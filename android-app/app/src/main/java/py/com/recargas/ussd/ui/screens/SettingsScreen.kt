package py.com.recargas.ussd.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import py.com.recargas.ussd.preferences.AccentPalette
import py.com.recargas.ussd.preferences.AppPreferences
import py.com.recargas.ussd.preferences.ButtonSizeOption
import py.com.recargas.ussd.preferences.ThemeMode
import py.com.recargas.ussd.security.DeviceAuthManager
import py.com.recargas.ussd.security.DeviceLockStatus
import py.com.recargas.ussd.updates.CatalogStateSnapshot
import py.com.recargas.ussd.ui.components.VerificationBadge
import py.com.recargas.ussd.ussd.ExecutionMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    catalogSnapshot: CatalogStateSnapshot,
    accessibilityEnabled: Boolean,
    deviceLockStatus: DeviceLockStatus,
    securityUnlocked: Boolean,
    pinStatusMap: Map<String, Boolean>,
    isUpdatingCatalog: Boolean,
    updateMessage: String?,
    onUnlockSecurity: (Boolean) -> Unit,
    onAcceptLockBypass: () -> Unit,
    onReadPin: (String) -> String,
    onSavePin: (String, String) -> Unit,
    onClearPin: (String) -> Unit,
    onToggleAutoPin: (String, Boolean) -> Unit,
    onSetExecutionMode: (ExecutionMode) -> Unit,
    onSetCatalogUrl: (String) -> Unit,
    onSetUpdateInterval: (Int) -> Unit,
    onSetShowUpdateAlerts: (Boolean) -> Unit,
    onUpdateCatalogNow: () -> Unit,
    onImportCatalogJson: (String) -> Unit,
    onRestoreBackupCatalog: () -> Unit,
    onClearUpdateMessage: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetAccentPalette: (AccentPalette) -> Unit,
    onSetButtonSize: (ButtonSizeOption) -> Unit,
    onSwapOperatorOrder: () -> Unit,
    onMoveCategoryUp: (String) -> Unit,
    onToggleCategoryVisibility: (String) -> Unit,
    onSetDefaultOperator: (String) -> Unit,
    onSetDefaultAmount: (Int) -> Unit,
    onResetPreferences: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var urlDraft by remember(preferences.catalogUpdateUrl) { mutableStateOf(preferences.catalogUpdateUrl) }
    var defaultAmtDraft by remember(preferences.defaultAmountGs) { mutableStateOf(preferences.defaultAmountGs.toString()) }
    var editingPinOp by remember { mutableStateOf<String?>(null) }
    var showNoLockPrompt by remember { mutableStateOf(false) }
    var showDiffDialog by remember { mutableStateOf(false) }

    val jsonPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (raw != null) onImportCatalogJson(raw)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Configuración", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        // Section 1: Automation and Security
        item {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Automatización y seguridad", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Asistente USSD (Accesibilidad)", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (accessibilityEnabled) "Activo: puede leer menús y asistir PIN" else "Inactivo: funcionará con guía manual",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) {
                            Text(if (accessibilityEnabled) "Revisar" else "Activar")
                        }
                    }

                    HorizontalDivider()

                    Text("Modo por defecto", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = preferences.defaultExecutionMode == ExecutionMode.ASSISTED,
                            onClick = { onSetExecutionMode(ExecutionMode.ASSISTED) },
                            label = { Text("Asistido") }
                        )
                        FilterChip(
                            selected = preferences.defaultExecutionMode == ExecutionMode.DIRECT,
                            onClick = { onSetExecutionMode(ExecutionMode.DIRECT) },
                            label = { Text("Directo (si verificado)") }
                        )
                    }

                    HorizontalDivider()

                    if (!securityUnlocked) {
                        Text(
                            "Los PIN se cifran con Android Keystore. Autentícate para ver o editar credenciales.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                if (deviceLockStatus == DeviceLockStatus.AVAILABLE && activity != null) {
                                    DeviceAuthManager.authenticate(
                                        activity = activity,
                                        onSuccess = { onUnlockSecurity(true) },
                                        onError = { err -> if (err == "NO_LOCK") showNoLockPrompt = true }
                                    )
                                } else if (preferences.deviceLockBypassAccepted) {
                                    onUnlockSecurity(true)
                                } else {
                                    showNoLockPrompt = true
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Desbloquear credenciales")
                        }
                    } else {
                        listOf("tigo" to "Tigo", "claro" to "Claro").forEach { (opId, label) ->
                            val hasPin = pinStatusMap[opId] == true
                            val autoOn = preferences.isAutoPinEnabled(opId)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("PIN $label: ${if (hasPin) "Guardado (cifrado)" else "No configurado"}", fontWeight = FontWeight.SemiBold)
                                    Text("Auto-ingreso de PIN", style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(checked = autoOn, onCheckedChange = { onToggleAutoPin(opId, it) })
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { editingPinOp = opId }) {
                                    Text(if (hasPin) "Cambiar PIN" else "Configurar PIN")
                                }
                                if (hasPin) {
                                    TextButton(onClick = { onClearPin(opId) }) { Text("Borrar PIN") }
                                }
                            }
                        }
                        TextButton(onClick = { onUnlockSecurity(false) }) { Text("Bloquear sección") }
                    }
                }
            }
        }

        // Section 2: Catalog & Smart Updates
        item {
            val cat = catalogSnapshot.activeCatalog
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Catálogo y actualizaciones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        VerificationBadge(status = cat.verificationLevel)
                    }
                    Text("Versión activa: v${cat.catalogVersion} (${cat.updatedAt})", style = MaterialTheme.typography.bodySmall)
                    Text("Última comprobación: ${catalogSnapshot.lastCheckedAt ?: "Pendiente"}", style = MaterialTheme.typography.bodySmall)

                    OutlinedTextField(
                        value = urlDraft,
                        onValueChange = { urlDraft = it },
                        label = { Text("URL del catálogo JSON externo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onSetCatalogUrl(urlDraft) }) { Text("Guardar URL") }
                        Button(
                            enabled = !isUpdatingCatalog,
                            onClick = {
                                onSetCatalogUrl(urlDraft)
                                onUpdateCatalogNow()
                            }
                        ) {
                            Icon(Icons.Rounded.Sync, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (isUpdatingCatalog) "Actualizando..." else "Actualizar ahora")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { jsonPicker.launch(arrayOf("application/json", "*/*")) }) {
                            Icon(Icons.Rounded.UploadFile, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Importar JSON")
                        }
                        if (catalogSnapshot.hasBackup) {
                            OutlinedButton(onClick = onRestoreBackupCatalog) {
                                Icon(Icons.Rounded.Restore, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Restaurar anterior")
                            }
                        }
                    }

                    if (catalogSnapshot.lastDiffReport != null) {
                        TextButton(onClick = { showDiffDialog = true }) {
                            Text("Ver comparación de cambios (${catalogSnapshot.lastDiffReport.changes.size})")
                        }
                    }

                    Text("Intervalo de actualización (días)", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 3, 7, 15, 30).forEach { days ->
                            FilterChip(
                                selected = preferences.updateIntervalDays == days,
                                onClick = { onSetUpdateInterval(days) },
                                label = { Text("$days d") }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Avisos de cambios en pantalla principal", modifier = Modifier.weight(1f))
                        Switch(checked = preferences.showUpdateAlerts, onCheckedChange = onSetShowUpdateAlerts)
                    }
                }
            }
        }

        // Section 3: Personalization & UI
        item {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Apariencia y personalización", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Text("Tema", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            val label = when (mode) {
                                ThemeMode.SYSTEM -> "Sistema"
                                ThemeMode.LIGHT -> "Claro"
                                ThemeMode.DARK -> "Oscuro"
                            }
                            FilterChip(selected = preferences.themeMode == mode, onClick = { onSetThemeMode(mode) }, label = { Text(label) })
                        }
                    }

                    Text("Color principal", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccentPalette.entries.forEach { pal ->
                            FilterChip(selected = preferences.accentPalette == pal, onClick = { onSetAccentPalette(pal) }, label = { Text(pal.name) })
                        }
                    }

                    Text("Tamaño de botones", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ButtonSizeOption.entries.forEach { sz ->
                            val label = when (sz) {
                                ButtonSizeOption.NORMAL -> "Normal"
                                ButtonSizeOption.LARGE -> "Grande"
                                ButtonSizeOption.XLARGE -> "Extra"
                            }
                            FilterChip(selected = preferences.buttonSize == sz, onClick = { onSetButtonSize(sz) }, label = { Text(label) })
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Orden operadores: ${preferences.operatorOrder.joinToString(" → ").uppercase()}")
                        OutlinedButton(onClick = onSwapOperatorOrder) {
                            Icon(Icons.Rounded.SwapVert, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Invertir")
                        }
                    }

                    Text("Orden y visibilidad de categorías", fontWeight = FontWeight.SemiBold)
                    preferences.categoryOrder.forEach { catId ->
                        val visible = catId !in preferences.hiddenCategories
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(catId, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { onMoveCategoryUp(catId) }) {
                                Icon(Icons.Rounded.ArrowUpward, contentDescription = "Subir")
                            }
                            Switch(checked = visible, onCheckedChange = { onToggleCategoryVisibility(catId) })
                        }
                    }
                }
            }
        }

        // Section 4: Defaults & Reset
        item {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Valores predeterminados y respaldo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Operador predeterminado", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = preferences.defaultOperatorId == "tigo", onClick = { onSetDefaultOperator("tigo") }, label = { Text("Tigo") })
                        FilterChip(selected = preferences.defaultOperatorId == "claro", onClick = { onSetDefaultOperator("claro") }, label = { Text("Claro") })
                    }
                    OutlinedTextField(
                        value = defaultAmtDraft,
                        onValueChange = { defaultAmtDraft = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Importe predeterminado (Gs.)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            defaultAmtDraft.toIntOrNull()?.takeIf { it > 0 }?.let(onSetDefaultAmount)
                        }) { Text("Guardar importe") }
                        OutlinedButton(onClick = onResetPreferences) {
                            Text("Restaurar configuración original")
                        }
                    }
                }
            }
        }
    }

    if (editingPinOp != null) {
        val opId = editingPinOp!!
        var pinInput by remember(opId) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { editingPinOp = null },
            title = { Text("Configurar PIN ${opId.uppercase()}") },
            text = {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it.filter { ch -> ch.isDigit() }.take(8) },
                    label = { Text("PIN numérico") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (pinInput.isNotBlank()) onSavePin(opId, pinInput)
                    editingPinOp = null
                }) { Text("Guardar cifrado") }
            },
            dismissButton = {
                TextButton(onClick = { editingPinOp = null }) { Text("Cancelar") }
            }
        )
    }

    if (showNoLockPrompt) {
        AlertDialog(
            onDismissRequest = { showNoLockPrompt = false },
            title = { Text("Sin bloqueo de pantalla configurado") },
            text = {
                Text("Este teléfono no tiene huella, patrón o PIN de pantalla activo. Se recomienda activarlo en Ajustes para proteger el acceso a tus credenciales.")
            },
            confirmButton = {
                Button(onClick = {
                    showNoLockPrompt = false
                    DeviceAuthManager.openSecuritySettings(context)
                }) { Text("Configurar bloqueo") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNoLockPrompt = false
                    onAcceptLockBypass()
                }) { Text("Continuar sin bloqueo") }
            }
        )
    }

    if (updateMessage != null) {
        AlertDialog(
            onDismissRequest = onClearUpdateMessage,
            title = { Text("Estado del catálogo") },
            text = { Text(updateMessage) },
            confirmButton = { Button(onClick = onClearUpdateMessage) { Text("Aceptar") } }
        )
    }

    if (showDiffDialog && catalogSnapshot.lastDiffReport != null) {
        val diff = catalogSnapshot.lastDiffReport
        AlertDialog(
            onDismissRequest = { showDiffDialog = false },
            title = { Text("Cambios v${diff.previousVersion} → v${diff.newVersion}") },
            text = { Text(diff.humanSummary()) },
            confirmButton = { TextButton(onClick = { showDiffDialog = false }) { Text("Cerrar") } }
        )
    }
}
