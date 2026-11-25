package py.com.recargas.ussd

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import py.com.recargas.ussd.ui.MainViewModel
import py.com.recargas.ussd.ui.screens.FavoritesScreen
import py.com.recargas.ussd.ui.screens.HistoryScreen
import py.com.recargas.ussd.ui.screens.OperationFlowScreen
import py.com.recargas.ussd.ui.screens.OperateScreen
import py.com.recargas.ussd.ui.screens.PreConfirmationDialog
import py.com.recargas.ussd.ui.screens.SettingsScreen
import py.com.recargas.ussd.ui.screens.UssdLiveMonitorDialog
import py.com.recargas.ussd.ui.theme.RecargasTheme

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshEnvironmentState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val perms = buildList {
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        permissionLauncher.launch(perms)

        setContent {
            val prefs by viewModel.preferences.collectAsStateWithLifecycle()
            val catalogSnapshot by viewModel.catalogSnapshot.collectAsStateWithLifecycle()
            val selectedOpId by viewModel.selectedOperatorId.collectAsStateWithLifecycle()
            val detectedSims by viewModel.detectedSims.collectAsStateWithLifecycle()
            val favorites by viewModel.favorites.collectAsStateWithLifecycle()
            val frequentNumbers by viewModel.frequentNumbers.collectAsStateWithLifecycle()
            val transactions by viewModel.transactions.collectAsStateWithLifecycle()
            val accessibilityEnabled by viewModel.accessibilityEnabled.collectAsStateWithLifecycle()
            val deviceLockStatus by viewModel.deviceLockStatus.collectAsStateWithLifecycle()
            val securityUnlocked by viewModel.securityUnlocked.collectAsStateWithLifecycle()
            val pinStatusMap by viewModel.pinStatusMap.collectAsStateWithLifecycle()
            val isUpdatingCatalog by viewModel.isUpdatingCatalog.collectAsStateWithLifecycle()
            val updateMessage by viewModel.updateMessage.collectAsStateWithLifecycle()
            val activeDraft by viewModel.activeDraft.collectAsStateWithLifecycle()
            val pendingPlan by viewModel.pendingPreConfirmPlan.collectAsStateWithLifecycle()
            val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()

            var currentTab by rememberSaveable { mutableIntStateOf(0) }

            LaunchedEffect(Unit) {
                viewModel.refreshEnvironmentState()
            }

            RecargasTheme(themeMode = prefs.themeMode, accentPalette = prefs.accentPalette) {
                if (activeDraft != null) {
                    val draft = activeDraft!!
                    OperationFlowScreen(
                        draft = draft,
                        preferences = prefs,
                        frequentNumbers = frequentNumbers,
                        onBack = { viewModel.closeOperation() },
                        onPrepareRecharge = { phone, amt -> viewModel.prepareRechargePlan(draft.operator, draft.category, phone, amt) },
                        onPreparePackage = { pkg, phone -> viewModel.preparePackagePlan(draft.operator, draft.category, pkg, phone) },
                        onPrepareInteractive = { phone -> viewModel.prepareInteractiveCategoryPlan(draft.operator, draft.category, phone) },
                        onPrepareQuery = { q, phone -> viewModel.prepareQueryPlan(draft.operator, q, phone) },
                        onSaveFavorite = { label, prodId, prodName, amt, valStr, phone ->
                            viewModel.saveFavorite(
                                label = label,
                                operatorId = draft.operator.id,
                                categoryId = draft.category.id.name,
                                productId = prodId,
                                productName = prodName,
                                amountGs = amt,
                                validity = valStr,
                                phone = phone
                            )
                        }
                    )
                } else {
                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentTab == 0,
                                    onClick = { currentTab = 0 },
                                    icon = { Icon(Icons.Rounded.Bolt, contentDescription = null) },
                                    label = { Text("Operar") }
                                )
                                NavigationBarItem(
                                    selected = currentTab == 1,
                                    onClick = { currentTab = 1 },
                                    icon = { Icon(Icons.Rounded.Star, contentDescription = null) },
                                    label = { Text("Favoritos") }
                                )
                                NavigationBarItem(
                                    selected = currentTab == 2,
                                    onClick = { currentTab = 2 },
                                    icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                                    label = { Text("Historial") }
                                )
                                NavigationBarItem(
                                    selected = currentTab == 3,
                                    onClick = { currentTab = 3 },
                                    icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                                    label = { Text("Ajustes") }
                                )
                            }
                        }
                    ) { padding ->
                        Box(modifier = Modifier.padding(padding)) {
                            when (currentTab) {
                                0 -> OperateScreen(
                                    catalogSnapshot = catalogSnapshot,
                                    preferences = prefs,
                                    selectedOperatorId = selectedOpId,
                                    detectedSims = detectedSims,
                                    favorites = favorites,
                                    accessibilityEnabled = accessibilityEnabled,
                                    onSelectOperator = viewModel::selectOperator,
                                    onOpenCategory = { op, cat -> viewModel.openOperation(op, cat) },
                                    onOpenFavorite = viewModel::openFavorite,
                                    onDismissDiffAlert = viewModel::dismissDiffAlert,
                                    onOpenSettings = { currentTab = 3 }
                                )
                                1 -> FavoritesScreen(
                                    favorites = favorites,
                                    frequentNumbers = frequentNumbers,
                                    onRunFavorite = viewModel::openFavorite,
                                    onDeleteFavorite = viewModel::deleteFavorite,
                                    onSaveFrequentNumber = viewModel::saveFrequentNumber,
                                    onDeleteFrequentNumber = viewModel::deleteFrequentNumber
                                )
                                2 -> HistoryScreen(
                                    transactions = transactions,
                                    onRepeat = viewModel::repeatTransaction,
                                    onClearAll = viewModel::clearHistory
                                )
                                3 -> SettingsScreen(
                                    preferences = prefs,
                                    catalogSnapshot = catalogSnapshot,
                                    accessibilityEnabled = accessibilityEnabled,
                                    deviceLockStatus = deviceLockStatus,
                                    securityUnlocked = securityUnlocked,
                                    pinStatusMap = pinStatusMap,
                                    isUpdatingCatalog = isUpdatingCatalog,
                                    updateMessage = updateMessage,
                                    onUnlockSecurity = viewModel::setSecurityUnlocked,
                                    onAcceptLockBypass = viewModel::acceptLockBypass,
                                    onReadPin = viewModel::readOperatorPinForEdit,
                                    onSavePin = viewModel::saveOperatorPin,
                                    onClearPin = viewModel::clearOperatorPin,
                                    onToggleAutoPin = viewModel::setAutoPin,
                                    onSetExecutionMode = viewModel::setExecutionMode,
                                    onSetCatalogUrl = viewModel::setCatalogUrl,
                                    onSetUpdateInterval = viewModel::setUpdateIntervalDays,
                                    onSetShowUpdateAlerts = viewModel::setShowUpdateAlerts,
                                    onUpdateCatalogNow = viewModel::updateCatalogNow,
                                    onImportCatalogJson = viewModel::importCatalogJson,
                                    onRestoreBackupCatalog = viewModel::restoreBackupCatalog,
                                    onClearUpdateMessage = viewModel::clearUpdateMessage,
                                    onSetThemeMode = viewModel::setThemeMode,
                                    onSetAccentPalette = viewModel::setAccentPalette,
                                    onSetButtonSize = viewModel::setButtonSize,
                                    onSwapOperatorOrder = viewModel::swapOperatorOrder,
                                    onMoveCategoryUp = viewModel::moveCategoryUp,
                                    onToggleCategoryVisibility = viewModel::toggleCategoryVisibility,
                                    onSetDefaultOperator = viewModel::setDefaultOperator,
                                    onSetDefaultAmount = viewModel::setDefaultAmount,
                                    onResetPreferences = viewModel::resetAllPreferences
                                )
                            }
                        }
                    }
                }

                if (pendingPlan != null) {
                    val plan = pendingPlan!!
                    PreConfirmationDialog(
                        plan = plan,
                        autoPinEnabled = prefs.isAutoPinEnabled(plan.operator.id),
                        hasStoredPin = pinStatusMap[plan.operator.id] == true,
                        accessibilityEnabled = accessibilityEnabled,
                        onConfirmLaunch = viewModel::executePendingPlan,
                        onCancel = viewModel::cancelPreConfirm
                    )
                }

                UssdLiveMonitorDialog(
                    state = sessionState,
                    onDismiss = viewModel::dismissSession,
                    onRetry = viewModel::retryLastSession,
                    onMarkOutcome = viewModel::markManualOutcome
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshEnvironmentState()
    }
}
