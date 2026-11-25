package py.com.recargas.ussd.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import py.com.recargas.ussd.RecargasApp
import py.com.recargas.ussd.catalog.CategoryDefinition
import py.com.recargas.ussd.catalog.CategoryId
import py.com.recargas.ussd.catalog.OperatorCatalog
import py.com.recargas.ussd.catalog.OperatorDefinition
import py.com.recargas.ussd.catalog.PackageOption
import py.com.recargas.ussd.catalog.QueryOption
import py.com.recargas.ussd.history.FavoriteEntity
import py.com.recargas.ussd.history.FrequentNumberEntity
import py.com.recargas.ussd.history.TransactionEntity
import py.com.recargas.ussd.history.TransactionStatus
import py.com.recargas.ussd.preferences.AccentPalette
import py.com.recargas.ussd.preferences.AppPreferences
import py.com.recargas.ussd.preferences.ButtonSizeOption
import py.com.recargas.ussd.preferences.ThemeMode
import py.com.recargas.ussd.security.DeviceAuthManager
import py.com.recargas.ussd.security.DeviceLockStatus
import py.com.recargas.ussd.updates.CatalogStateSnapshot
import py.com.recargas.ussd.updates.CatalogSyncWorker
import py.com.recargas.ussd.updates.CatalogUpdateOutcome
import py.com.recargas.ussd.ussd.DetectedSim
import py.com.recargas.ussd.ussd.ExecutionMode
import py.com.recargas.ussd.ussd.PhoneFormatter
import py.com.recargas.ussd.ussd.SimSelector
import py.com.recargas.ussd.ussd.UssdExecutionPlan
import py.com.recargas.ussd.ussd.UssdPlanBuilder
import py.com.recargas.ussd.ussd.UssdSessionState

data class ActiveOperationDraft(
    val operator: OperatorDefinition,
    val category: CategoryDefinition,
    val initialAmountGs: Int? = null,
    val initialPackageId: String? = null,
    val initialPhone: String = ""
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as RecargasApp

    val preferences: StateFlow<AppPreferences> = app.preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())

    val catalogSnapshot: StateFlow<CatalogStateSnapshot> = app.catalogRepository.snapshot
    val sessionState: StateFlow<UssdSessionState> = app.sessionCoordinator.state

    val favorites: StateFlow<List<FavoriteEntity>> = app.database.favoriteDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val transactions: StateFlow<List<TransactionEntity>> = app.database.transactionDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val frequentNumbers: StateFlow<List<FrequentNumberEntity>> = app.database.frequentNumberDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedOperatorId = MutableStateFlow<String?>(null)
    val selectedOperatorId: StateFlow<String> = combine(_selectedOperatorId, preferences, catalogSnapshot) { sel, prefs, snap ->
        val validIds = snap.activeCatalog.operators.map { it.id }
        when {
            sel != null && sel in validIds -> sel
            prefs.defaultOperatorId in validIds -> prefs.defaultOperatorId
            else -> validIds.firstOrNull() ?: "tigo"
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "tigo")

    private val _detectedSims = MutableStateFlow<Map<String, DetectedSim>>(emptyMap())
    val detectedSims: StateFlow<Map<String, DetectedSim>> = _detectedSims.asStateFlow()

    private val _accessibilityEnabled = MutableStateFlow(app.sessionCoordinator.isAccessibilityEnabled())
    val accessibilityEnabled: StateFlow<Boolean> = _accessibilityEnabled.asStateFlow()

    private val _deviceLockStatus = MutableStateFlow(DeviceAuthManager.checkLockStatus(app))
    val deviceLockStatus: StateFlow<DeviceLockStatus> = _deviceLockStatus.asStateFlow()

    private val _activeDraft = MutableStateFlow<ActiveOperationDraft?>(null)
    val activeDraft: StateFlow<ActiveOperationDraft?> = _activeDraft.asStateFlow()

    private val _pendingPreConfirmPlan = MutableStateFlow<UssdExecutionPlan?>(null)
    val pendingPreConfirmPlan: StateFlow<UssdExecutionPlan?> = _pendingPreConfirmPlan.asStateFlow()

    private val _updateMessage = MutableStateFlow<String?>(null)
    val updateMessage: StateFlow<String?> = _updateMessage.asStateFlow()

    private val _isUpdatingCatalog = MutableStateFlow(false)
    val isUpdatingCatalog: StateFlow<Boolean> = _isUpdatingCatalog.asStateFlow()

    private val _securityUnlocked = MutableStateFlow(false)
    val securityUnlocked: StateFlow<Boolean> = _securityUnlocked.asStateFlow()

    private val _pinStatusMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val pinStatusMap: StateFlow<Map<String, Boolean>> = _pinStatusMap.asStateFlow()

    init {
        refreshEnvironmentState()
    }

    fun refreshEnvironmentState() {
        val ops = catalogSnapshot.value.activeCatalog.operators
        _detectedSims.value = SimSelector.detectSims(app, ops)
        _accessibilityEnabled.value = app.sessionCoordinator.isAccessibilityEnabled()
        _deviceLockStatus.value = DeviceAuthManager.checkLockStatus(app)
        refreshPinStatuses()
    }

    fun refreshPinStatuses() {
        val ops = catalogSnapshot.value.activeCatalog.operators
        _pinStatusMap.value = ops.associate { it.id to app.pinVault.hasPin(it.id) }
    }

    fun selectOperator(operatorId: String) {
        _selectedOperatorId.value = operatorId
    }

    fun openOperation(
        operator: OperatorDefinition,
        category: CategoryDefinition,
        initialAmountGs: Int? = null,
        initialPackageId: String? = null,
        initialPhone: String = ""
    ) {
        val defaultAmt = initialAmountGs ?: if (category.id == CategoryId.RECHARGE) preferences.value.defaultAmountGs else null
        _activeDraft.value = ActiveOperationDraft(
            operator = operator,
            category = category,
            initialAmountGs = defaultAmt,
            initialPackageId = initialPackageId,
            initialPhone = initialPhone
        )
    }

    fun closeOperation() {
        _activeDraft.value = null
    }

    fun openFavorite(fav: FavoriteEntity) {
        val catalog = catalogSnapshot.value.activeCatalog
        val op = catalog.operators.firstOrNull { it.id == fav.operatorId } ?: return
        val cat = op.categories.firstOrNull { it.id.name == fav.categoryId } ?: return
        _selectedOperatorId.value = op.id
        openOperation(
            operator = op,
            category = cat,
            initialAmountGs = fav.amountGs,
            initialPackageId = fav.productId,
            initialPhone = fav.defaultPhoneNumber.orEmpty()
        )
    }

    fun repeatTransaction(tx: TransactionEntity) {
        val catalog = catalogSnapshot.value.activeCatalog
        val op = catalog.operators.firstOrNull { it.id == tx.operatorId } ?: return
        val cat = op.categories.firstOrNull {
            it.title == tx.operationType ||
                (tx.operationType == "Recarga de saldo" && it.id == CategoryId.RECHARGE)
        } ?: op.categories.firstOrNull() ?: return
        _selectedOperatorId.value = op.id
        openOperation(
            operator = op,
            category = cat,
            initialAmountGs = tx.amountGs,
            initialPackageId = cat.packages.firstOrNull { it.name == tx.productName }?.id,
            initialPhone = tx.phoneNumber.orEmpty()
        )
    }

    fun prepareRechargePlan(
        operator: OperatorDefinition,
        category: CategoryDefinition,
        rawPhone: String,
        amountGs: Int
    ): String? {
        val phoneResult = PhoneFormatter.normalizeAndValidate(rawPhone, operator.phoneFormat)
        if (!phoneResult.isValid) return phoneResult.errorMessage
        val planResult = UssdPlanBuilder.buildRechargePlan(
            operator = operator,
            category = category,
            destinationNumber = phoneResult.normalized,
            amountGs = amountGs,
            requestedMode = preferences.value.defaultExecutionMode
        )
        return planResult.fold(
            onSuccess = {
                _pendingPreConfirmPlan.value = it
                null
            },
            onFailure = { it.message ?: "Error al preparar recarga" }
        )
    }

    fun preparePackagePlan(
        operator: OperatorDefinition,
        category: CategoryDefinition,
        pkg: PackageOption,
        rawPhone: String
    ): String? {
        val dest = if (operator.id == "claro" && category.id == CategoryId.ROAMING) {
            rawPhone.filter { it.isDigit() }.ifBlank { return "Ingresa el número extranjero a recargar." }
        } else {
            val phoneResult = PhoneFormatter.normalizeAndValidate(rawPhone, operator.phoneFormat)
            if (!phoneResult.isValid) return phoneResult.errorMessage
            phoneResult.normalized
        }
        _pendingPreConfirmPlan.value = UssdPlanBuilder.buildPackagePlan(
            operator = operator,
            category = category,
            pkg = pkg,
            destinationNumber = dest,
            requestedMode = preferences.value.defaultExecutionMode
        )
        return null
    }

    fun prepareInteractiveCategoryPlan(
        operator: OperatorDefinition,
        category: CategoryDefinition,
        rawPhone: String
    ): String? {
        val phoneResult = PhoneFormatter.normalizeAndValidate(rawPhone, operator.phoneFormat)
        if (!phoneResult.isValid) return phoneResult.errorMessage
        _pendingPreConfirmPlan.value = UssdPlanBuilder.buildInteractiveCategoryPlan(
            operator = operator,
            category = category,
            destinationNumber = phoneResult.normalized,
            requestedMode = preferences.value.defaultExecutionMode
        )
        return null
    }

    fun prepareQueryPlan(
        operator: OperatorDefinition,
        query: QueryOption,
        rawPhone: String
    ): String? {
        var dest: String? = null
        if (query.requiresDestination) {
            val res = PhoneFormatter.normalizeAndValidate(rawPhone, operator.phoneFormat)
            if (!res.isValid) return res.errorMessage
            dest = res.normalized
        }
        _pendingPreConfirmPlan.value = UssdPlanBuilder.buildQueryPlan(operator, query, dest)
        return null
    }

    fun cancelPreConfirm() {
        _pendingPreConfirmPlan.value = null
    }

    fun executePendingPlan() {
        val plan = _pendingPreConfirmPlan.value ?: return
        _pendingPreConfirmPlan.value = null
        val sim = _detectedSims.value[plan.operator.id]
        app.sessionCoordinator.startSession(plan, sim)
    }

    fun retryLastSession(plan: UssdExecutionPlan) {
        val sim = _detectedSims.value[plan.operator.id]
        app.sessionCoordinator.startSession(plan, sim)
    }

    fun dismissSession() = app.sessionCoordinator.dismissSession()

    fun markManualOutcome(status: TransactionStatus, note: String) {
        app.sessionCoordinator.markManualResult(status, note)
    }

    fun saveFavorite(
        label: String,
        operatorId: String,
        categoryId: String,
        productId: String?,
        productName: String,
        amountGs: Int?,
        validity: String,
        phone: String?
    ) {
        viewModelScope.launch {
            app.database.favoriteDao().insert(
                FavoriteEntity(
                    label = label,
                    operatorId = operatorId,
                    categoryId = categoryId,
                    productId = productId,
                    productName = productName,
                    amountGs = amountGs,
                    validity = validity,
                    defaultPhoneNumber = phone?.takeIf { it.isNotBlank() },
                    sortOrder = 50
                )
            )
        }
    }

    fun deleteFavorite(id: Long) {
        viewModelScope.launch { app.database.favoriteDao().deleteById(id) }
    }

    fun saveFrequentNumber(phone: String, operatorId: String, alias: String) {
        viewModelScope.launch {
            val clean = phone.filter { it.isDigit() }
            if (clean.isNotEmpty()) {
                val prev = app.database.frequentNumberDao().findByNumber(clean)
                app.database.frequentNumberDao().upsert(
                    FrequentNumberEntity(
                        phoneNumber = clean,
                        operatorId = operatorId,
                        alias = alias.trim(),
                        useCount = (prev?.useCount ?: 0) + 1,
                        lastUsedMillis = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun deleteFrequentNumber(phone: String) {
        viewModelScope.launch { app.database.frequentNumberDao().deleteByNumber(phone) }
    }

    fun clearHistory() {
        viewModelScope.launch { app.database.transactionDao().clearAll() }
    }

    // Security & PINs
    fun setSecurityUnlocked(unlocked: Boolean) {
        _securityUnlocked.value = unlocked
        if (unlocked) refreshPinStatuses()
    }

    fun saveOperatorPin(operatorId: String, pin: String) {
        if (pin.isNotBlank()) {
            app.pinVault.savePin(operatorId, pin)
            refreshPinStatuses()
        }
    }

    fun clearOperatorPin(operatorId: String) {
        app.pinVault.clearPin(operatorId)
        refreshPinStatuses()
    }

    fun readOperatorPinForEdit(operatorId: String): String {
        if (!_securityUnlocked.value) return ""
        return app.pinVault.getPin(operatorId).orEmpty()
    }

    fun setAutoPin(operatorId: String, enabled: Boolean) {
        viewModelScope.launch {
            app.preferencesRepository.update { prefs ->
                when (operatorId) {
                    "tigo" -> prefs.copy(autoPinTigo = enabled)
                    "claro" -> prefs.copy(autoPinClaro = enabled)
                    else -> prefs
                }
            }
        }
    }

    fun acceptLockBypass() {
        viewModelScope.launch {
            app.preferencesRepository.update { it.copy(deviceLockBypassAccepted = true) }
            _securityUnlocked.value = true
        }
    }

    // Catalog updates
    fun updateCatalogNow() {
        viewModelScope.launch {
            _isUpdatingCatalog.value = true
            val url = preferences.value.catalogUpdateUrl
            val msg = when (val res = app.catalogRepository.updateFromUrl(url)) {
                is CatalogUpdateOutcome.Updated -> res.diff.humanSummary()
                is CatalogUpdateOutcome.NoChanges -> "Catálogo ya actualizado (v${res.catalog.catalogVersion})."
                is CatalogUpdateOutcome.Rejected -> "Actualización rechazada:\n" + res.errors.joinToString("\n")
                is CatalogUpdateOutcome.NetworkError -> res.message
            }
            _updateMessage.value = msg
            _isUpdatingCatalog.value = false
        }
    }

    fun importCatalogJson(rawJson: String) {
        viewModelScope.launch {
            _isUpdatingCatalog.value = true
            val msg = when (val res = app.catalogRepository.importFromRawJson(rawJson)) {
                is CatalogUpdateOutcome.Updated -> res.diff.humanSummary()
                is CatalogUpdateOutcome.NoChanges -> "El archivo coincide con la versión actual (v${res.catalog.catalogVersion})."
                is CatalogUpdateOutcome.Rejected -> "Archivo inválido y rechazado:\n" + res.errors.joinToString("\n")
                is CatalogUpdateOutcome.NetworkError -> res.message
            }
            _updateMessage.value = msg
            _isUpdatingCatalog.value = false
        }
    }

    fun restoreBackupCatalog() {
        viewModelScope.launch {
            val res = app.catalogRepository.restoreBackup()
            _updateMessage.value = res.fold(
                onSuccess = { "Se restauró el respaldo anterior (v${it.catalogVersion})." },
                onFailure = { it.message ?: "No se pudo restaurar el respaldo." }
            )
        }
    }

    fun clearUpdateMessage() {
        _updateMessage.value = null
    }

    fun dismissDiffAlert() = app.catalogRepository.dismissDiffAlert()

    // Preferences updates
    fun setThemeMode(mode: ThemeMode) = updatePrefs { it.copy(themeMode = mode) }
    fun setAccentPalette(palette: AccentPalette) = updatePrefs { it.copy(accentPalette = palette) }
    fun setButtonSize(size: ButtonSizeOption) = updatePrefs { it.copy(buttonSize = size) }
    fun setDefaultOperator(opId: String) = updatePrefs { it.copy(defaultOperatorId = opId) }
    fun setDefaultAmount(amount: Int) = updatePrefs { it.copy(defaultAmountGs = amount) }
    fun setExecutionMode(mode: ExecutionMode) = updatePrefs { it.copy(defaultExecutionMode = mode) }
    fun setCatalogUrl(url: String) = updatePrefs { it.copy(catalogUpdateUrl = url.trim()) }
    fun setShowUpdateAlerts(enabled: Boolean) = updatePrefs { it.copy(showUpdateAlerts = enabled) }

    fun setUpdateIntervalDays(days: Int) {
        viewModelScope.launch {
            app.preferencesRepository.update { it.copy(updateIntervalDays = days) }
            CatalogSyncWorker.schedule(app, days)
        }
    }

    fun swapOperatorOrder() = updatePrefs { prefs ->
        val reversed = prefs.operatorOrder.reversed()
        prefs.copy(operatorOrder = reversed)
    }

    fun toggleCategoryVisibility(categoryId: String) = updatePrefs { prefs ->
        val next = prefs.hiddenCategories.toMutableSet()
        if (!next.add(categoryId)) next.remove(categoryId)
        prefs.copy(hiddenCategories = next)
    }

    fun moveCategoryUp(categoryId: String) = updatePrefs { prefs ->
        val list = prefs.categoryOrder.toMutableList()
        val idx = list.indexOf(categoryId)
        if (idx > 0) {
            list.removeAt(idx)
            list.add(idx - 1, categoryId)
        }
        prefs.copy(categoryOrder = list)
    }

    fun resetAllPreferences() {
        viewModelScope.launch {
            app.preferencesRepository.resetToDefaults()
            CatalogSyncWorker.schedule(app, 7)
            _updateMessage.value = "Configuración restaurada a los valores originales."
        }
    }

    private fun updatePrefs(block: (AppPreferences) -> AppPreferences) {
        viewModelScope.launch { app.preferencesRepository.update(block) }
    }
}
