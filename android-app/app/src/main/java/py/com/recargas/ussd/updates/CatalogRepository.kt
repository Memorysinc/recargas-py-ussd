package py.com.recargas.ussd.updates

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import py.com.recargas.ussd.catalog.CatalogDiffEngine
import py.com.recargas.ussd.catalog.CatalogDiffReport
import py.com.recargas.ussd.catalog.CatalogValidator
import py.com.recargas.ussd.catalog.OperatorCatalog
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

sealed class CatalogUpdateOutcome {
    data class Updated(val catalog: OperatorCatalog, val diff: CatalogDiffReport) : CatalogUpdateOutcome()
    data class NoChanges(val catalog: OperatorCatalog) : CatalogUpdateOutcome()
    data class Rejected(val errors: List<String>) : CatalogUpdateOutcome()
    data class NetworkError(val message: String) : CatalogUpdateOutcome()
}

data class CatalogStateSnapshot(
    val activeCatalog: OperatorCatalog,
    val lastCheckedAt: String?,
    val lastDiffReport: CatalogDiffReport?,
    val hasBackup: Boolean,
    val isOfflineFallback: Boolean = false
)

class CatalogRepository(private val context: Context) {

    private val catalogDir = File(context.filesDir, "catalog").apply { mkdirs() }
    private val activeFile = File(catalogDir, "active_catalog.json")
    private val backupFile = File(catalogDir, "backup_catalog.json")
    private val diffFile = File(catalogDir, "last_diff.json")
    private val metaPrefs = context.getSharedPreferences("catalog_meta", Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _snapshot = MutableStateFlow(loadInitialSnapshot())
    val snapshot: StateFlow<CatalogStateSnapshot> = _snapshot.asStateFlow()

    private fun loadInitialSnapshot(): CatalogStateSnapshot {
        val activeCatalog = loadBestValidCatalog()
        val lastDiff = loadLastDiff()
        return CatalogStateSnapshot(
            activeCatalog = activeCatalog,
            lastCheckedAt = metaPrefs.getString("last_checked_at", null),
            lastDiffReport = lastDiff,
            hasBackup = backupFile.exists()
        )
    }

    private fun loadBestValidCatalog(): OperatorCatalog {
        if (activeFile.exists()) {
            val res = CatalogValidator.parseAndValidate(activeFile.readText(Charsets.UTF_8))
            if (res.isValid && res.catalog != null) return res.catalog
        }
        if (backupFile.exists()) {
            val res = CatalogValidator.parseAndValidate(backupFile.readText(Charsets.UTF_8))
            if (res.isValid && res.catalog != null) {
                activeFile.writeText(backupFile.readText(Charsets.UTF_8), Charsets.UTF_8)
                return res.catalog
            }
        }
        val bundledRaw = context.assets.open("catalog_default.json").bufferedReader().use { it.readText() }
        val bundled = CatalogValidator.parseAndValidate(bundledRaw).catalog
            ?: error("El catálogo base embebido es inválido.")
        activeFile.writeText(bundledRaw, Charsets.UTF_8)
        return bundled
    }

    private fun loadLastDiff(): CatalogDiffReport? {
        if (!diffFile.exists()) return null
        return try {
            CatalogValidator.json.decodeFromString(CatalogDiffReport.serializer(), diffFile.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    suspend fun updateFromUrl(url: String): CatalogUpdateOutcome = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            return@withContext CatalogUpdateOutcome.NetworkError("URL de actualización vacía.")
        }
        val raw = try {
            val req = Request.Builder().url(trimmed).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext CatalogUpdateOutcome.NetworkError("HTTP ${resp.code}: ${resp.message}")
                }
                resp.body?.string().orEmpty()
            }
        } catch (e: Exception) {
            return@withContext CatalogUpdateOutcome.NetworkError(
                "Sin conexión o servidor inaccesible (${e.javaClass.simpleName}): usando último catálogo válido."
            )
        }
        applyDownloadedJson(raw, markChecked = true)
    }

    suspend fun importFromRawJson(rawJson: String): CatalogUpdateOutcome = withContext(Dispatchers.IO) {
        applyDownloadedJson(rawJson, markChecked = true)
    }

    private fun applyDownloadedJson(rawJson: String, markChecked: Boolean): CatalogUpdateOutcome {
        val nowIso = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        if (markChecked) {
            metaPrefs.edit().putString("last_checked_at", nowIso).apply()
        }

        val validation = CatalogValidator.parseAndValidate(rawJson)
        if (!validation.isValid || validation.catalog == null) {
            refreshSnapshot(offlineFallback = false)
            return CatalogUpdateOutcome.Rejected(validation.errors)
        }

        val current = _snapshot.value.activeCatalog
        val incoming = validation.catalog
        val diff = CatalogDiffEngine.compare(current, incoming, nowIso)

        if (!diff.hasChanges && current.catalogVersion == incoming.catalogVersion) {
            refreshSnapshot(offlineFallback = false)
            return CatalogUpdateOutcome.NoChanges(current)
        }

        // Backup previous valid catalog before replacing
        if (activeFile.exists()) {
            activeFile.copyTo(backupFile, overwrite = true)
        }
        activeFile.writeText(rawJson, Charsets.UTF_8)
        diffFile.writeText(CatalogValidator.json.encodeToString(CatalogDiffReport.serializer(), diff), Charsets.UTF_8)
        refreshSnapshot(offlineFallback = false)
        return CatalogUpdateOutcome.Updated(incoming, diff)
    }

    suspend fun restoreBackup(): Result<OperatorCatalog> = withContext(Dispatchers.IO) {
        if (!backupFile.exists()) {
            return@withContext Result.failure(IllegalStateException("No hay copia de seguridad previa disponible."))
        }
        val raw = backupFile.readText(Charsets.UTF_8)
        val validation = CatalogValidator.parseAndValidate(raw)
        if (!validation.isValid || validation.catalog == null) {
            return@withContext Result.failure(IllegalStateException("La copia de seguridad no pasó validación."))
        }
        activeFile.writeText(raw, Charsets.UTF_8)
        diffFile.delete()
        refreshSnapshot(offlineFallback = false)
        Result.success(validation.catalog)
    }

    suspend fun resetToBundledCatalog(): OperatorCatalog = withContext(Dispatchers.IO) {
        val bundledRaw = context.assets.open("catalog_default.json").bufferedReader().use { it.readText() }
        if (activeFile.exists()) activeFile.copyTo(backupFile, overwrite = true)
        activeFile.writeText(bundledRaw, Charsets.UTF_8)
        diffFile.delete()
        refreshSnapshot(offlineFallback = false)
        _snapshot.value.activeCatalog
    }

    fun dismissDiffAlert() {
        diffFile.delete()
        refreshSnapshot(offlineFallback = false)
    }

    private fun refreshSnapshot(offlineFallback: Boolean) {
        _snapshot.value = CatalogStateSnapshot(
            activeCatalog = loadBestValidCatalog(),
            lastCheckedAt = metaPrefs.getString("last_checked_at", null),
            lastDiffReport = loadLastDiff(),
            hasBackup = backupFile.exists(),
            isOfflineFallback = offlineFallback
        )
    }
}
