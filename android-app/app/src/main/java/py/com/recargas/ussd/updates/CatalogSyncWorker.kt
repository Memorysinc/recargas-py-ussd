package py.com.recargas.ussd.updates

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import py.com.recargas.ussd.RecargasApp
import java.util.concurrent.TimeUnit

class CatalogSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? RecargasApp ?: return Result.failure()
        val prefs = app.preferencesRepository.preferences.first()
        return when (app.catalogRepository.updateFromUrl(prefs.catalogUpdateUrl)) {
            is CatalogUpdateOutcome.Updated,
            is CatalogUpdateOutcome.NoChanges,
            is CatalogUpdateOutcome.Rejected -> Result.success()
            is CatalogUpdateOutcome.NetworkError -> Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "catalog_periodic_sync"

        fun schedule(context: Context, intervalDays: Int) {
            val days = intervalDays.coerceIn(1, 30).toLong()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val req = PeriodicWorkRequestBuilder<CatalogSyncWorker>(days, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }
    }
}
