package py.com.recargas.ussd

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import py.com.recargas.ussd.history.AppDatabase
import py.com.recargas.ussd.preferences.PreferencesRepository
import py.com.recargas.ussd.security.PinVault
import py.com.recargas.ussd.updates.CatalogRepository
import py.com.recargas.ussd.updates.CatalogSyncWorker
import py.com.recargas.ussd.ussd.UssdSessionCoordinator

class RecargasApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: AppDatabase
        private set
    lateinit var preferencesRepository: PreferencesRepository
        private set
    lateinit var pinVault: PinVault
        private set
    lateinit var catalogRepository: CatalogRepository
        private set
    lateinit var sessionCoordinator: UssdSessionCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        preferencesRepository = PreferencesRepository(this)
        pinVault = PinVault(this)
        catalogRepository = CatalogRepository(this)
        sessionCoordinator = UssdSessionCoordinator(
            context = this,
            database = database,
            preferencesRepository = preferencesRepository,
            pinVault = pinVault
        )

        appScope.launch {
            AppDatabase.seedDefaultFavoritesIfEmpty(database)
            val prefs = preferencesRepository.preferences.first()
            CatalogSyncWorker.schedule(this@RecargasApp, prefs.updateIntervalDays)
            // Silent startup update check; stays offline-safe if unreachable
            catalogRepository.updateFromUrl(prefs.catalogUpdateUrl)
        }
    }
}
