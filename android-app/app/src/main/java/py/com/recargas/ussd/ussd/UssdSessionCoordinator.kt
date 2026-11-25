package py.com.recargas.ussd.ussd

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import py.com.recargas.ussd.automation.UssdAccessibilityService
import py.com.recargas.ussd.history.AppDatabase
import py.com.recargas.ussd.history.FrequentNumberEntity
import py.com.recargas.ussd.history.TransactionEntity
import py.com.recargas.ussd.history.TransactionStatus
import py.com.recargas.ussd.preferences.PreferencesRepository
import py.com.recargas.ussd.security.PinVault

sealed class UssdSessionState {
    data object Idle : UssdSessionState()
    data class Running(
        val plan: UssdExecutionPlan,
        val currentStepIndex: Int,
        val lastDialogMessage: String,
        val accessibilityEnabled: Boolean,
        val simInfoText: String
    ) : UssdSessionState()

    data class AwaitingManualPin(
        val plan: UssdExecutionPlan,
        val currentStepIndex: Int,
        val promptMessage: String,
        val reason: String
    ) : UssdSessionState()

    data class AwaitingUserConfirmation(
        val plan: UssdExecutionPlan,
        val confirmationText: String
    ) : UssdSessionState()

    data class Completed(
        val plan: UssdExecutionPlan,
        val resultMessage: String
    ) : UssdSessionState()

    data class Cancelled(
        val plan: UssdExecutionPlan,
        val reason: String
    ) : UssdSessionState()

    data class Failed(
        val plan: UssdExecutionPlan,
        val errorMessage: String
    ) : UssdSessionState()

    data class Unknown(
        val plan: UssdExecutionPlan,
        val detail: String
    ) : UssdSessionState()
}

class UssdSessionCoordinator(
    private val context: Context,
    private val database: AppDatabase,
    private val preferencesRepository: PreferencesRepository,
    private val pinVault: PinVault
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<UssdSessionState>(UssdSessionState.Idle)
    val state: StateFlow<UssdSessionState> = _state.asStateFlow()

    @Volatile
    var sessionId: Long = 0L
        private set

    @Volatile
    var activePlan: UssdExecutionPlan? = null
        private set

    @Volatile
    var currentStepIndex: Int = 0
        private set

    @Volatile
    private var autoPinEnabledForRun: Boolean = false

    @Volatile
    private var pinAttemptedInCurrentStep: Boolean = false

    @Volatile
    private var terminalRecorded: Boolean = false

    private var watchdogJob: Job? = null

    fun isAccessibilityEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabledServices.contains(UssdAccessibilityService::class.java.name) ||
            enabledServices.contains("${context.packageName}/.automation.UssdAccessibilityService")
    }

    fun startSession(plan: UssdExecutionPlan, detectedSim: DetectedSim?) {
        scope.launch {
            val prefs = preferencesRepository.preferences.first()
            autoPinEnabledForRun = prefs.isAutoPinEnabled(plan.operator.id)
            sessionId++
            activePlan = plan
            currentStepIndex = 0
            pinAttemptedInCurrentStep = false
            terminalRecorded = false

            val accEnabled = isAccessibilityEnabled()
            val launchResult = SimSelector.launchUssd(context, plan.operator, plan.initialCode, detectedSim)
            if (launchResult is DialLaunchResult.Error) {
                finishWithStatus(TransactionStatus.FALLIDA, launchResult.message)
                return@launch
            }

            val simText = when (launchResult) {
                is DialLaunchResult.LaunchedWithSim -> "SIM ${launchResult.simDisplayName} seleccionada automáticamente"
                DialLaunchResult.LaunchedWithSystemPicker -> "Selector de SIM del sistema"
                DialLaunchResult.OpenedDialerOnly -> "Permiso de llamada no otorgado: marcador abierto"
                is DialLaunchResult.Error -> ""
            }

            _state.value = UssdSessionState.Running(
                plan = plan,
                currentStepIndex = 0,
                lastDialogMessage = if (accEnabled) {
                    "Iniciando código ${plan.initialCode}..."
                } else {
                    "Asistente de Accesibilidad inactivo: sigue la guía paso a paso en la ventana USSD."
                },
                accessibilityEnabled = accEnabled,
                simInfoText = simText
            )

            // Save frequent number usage if destination is valid
            plan.summary.destinationNumber?.takeIf { it.isNotBlank() }?.let { dest ->
                launch(Dispatchers.IO) {
                    val existing = database.frequentNumberDao().findByNumber(dest)
                    database.frequentNumberDao().upsert(
                        FrequentNumberEntity(
                            phoneNumber = dest,
                            operatorId = plan.operator.id,
                            alias = existing?.alias.orEmpty(),
                            useCount = (existing?.useCount ?: 0) + 1,
                            lastUsedMillis = System.currentTimeMillis()
                        )
                    )
                }
            }

            resetWatchdog()
        }
    }

    fun getCurrentStep(): UssdStep? {
        val plan = activePlan ?: return null
        return plan.steps.getOrNull(currentStepIndex)
    }

    fun shouldAutoInjectPin(): String? {
        val plan = activePlan ?: return null
        if (!autoPinEnabledForRun) return null
        if (pinAttemptedInCurrentStep) return null
        val step = getCurrentStep() ?: return null
        if (step.kind != StepKind.PIN) return null
        return pinVault.getPin(plan.operator.id)
    }

    fun onPinInjected() {
        pinAttemptedInCurrentStep = true
        advanceStep("PIN ingresado de forma segura")
    }

    fun onManualPinRequired(dialogMessage: String, reason: String) {
        val plan = activePlan ?: return
        resetWatchdog()
        _state.value = UssdSessionState.AwaitingManualPin(
            plan = plan,
            currentStepIndex = currentStepIndex,
            promptMessage = dialogMessage,
            reason = reason
        )
    }

    fun advanceStep(dialogMessage: String) {
        val plan = activePlan ?: return
        pinAttemptedInCurrentStep = false
        if (currentStepIndex < plan.steps.lastIndex) {
            currentStepIndex++
        }
        resetWatchdog()
        _state.value = UssdSessionState.Running(
            plan = plan,
            currentStepIndex = currentStepIndex,
            lastDialogMessage = dialogMessage,
            accessibilityEnabled = isAccessibilityEnabled(),
            simInfoText = ""
        )
    }

    fun onDialogMessageObserved(message: String) {
        val plan = activePlan ?: return
        if (terminalRecorded) return
        val lower = message.lowercase()
        val rules = plan.operator.ussdRules

        // Cancel message observed
        if (rules.cancelResultKeywords.any { lower.contains(it.lowercase()) }) {
            finishWithStatus(TransactionStatus.CANCELADA, message)
            return
        }

        // Error or MMI or commercial block observed
        if (rules.errorKeywords.any { lower.contains(it.lowercase()) }) {
            finishWithStatus(TransactionStatus.FALLIDA, message)
            return
        }

        // Final confirmation screen reached
        if (rules.confirmKeywords.any { lower.contains(it.lowercase()) }) {
            watchdogJob?.cancel()
            _state.value = UssdSessionState.AwaitingUserConfirmation(plan, message)
            return
        }

        // Read-only query result (no input field, just final informational dialog)
        if (!plan.summary.isEconomicOperation && getCurrentStep()?.kind == StepKind.READ_RESULT) {
            finishWithStatus(TransactionStatus.COMPLETADA, message)
            return
        }

        // If user already passed AwaitingUserConfirmation and operator shows final response
        if (_state.value is UssdSessionState.AwaitingUserConfirmation) {
            val isSuccessMsg = lower.contains("exitos") || lower.contains("realizad") ||
                lower.contains("confirmad") || lower.contains("acredit")
            val status = if (isSuccessMsg) TransactionStatus.COMPLETADA else TransactionStatus.DESCONOCIDA
            finishWithStatus(status, message)
        }
    }

    fun onUserClickedCancelOnDialog() {
        if (activePlan != null && !terminalRecorded) {
            finishWithStatus(TransactionStatus.CANCELADA, "Cancelado por el usuario en el diálogo USSD.")
        }
    }

    fun markManualResult(status: TransactionStatus, note: String) {
        if (activePlan != null && !terminalRecorded) {
            finishWithStatus(status, note)
        }
    }

    fun dismissSession() {
        watchdogJob?.cancel()
        activePlan = null
        currentStepIndex = 0
        _state.value = UssdSessionState.Idle
    }

    private fun resetWatchdog() {
        watchdogJob?.cancel()
        val plan = activePlan ?: return
        watchdogJob = scope.launch {
            delay(25_000L)
            if (!terminalRecorded && activePlan == plan) {
                val current = _state.value
                if (current is UssdSessionState.Running || current is UssdSessionState.AwaitingManualPin) {
                    finishWithStatus(
                        TransactionStatus.FALLIDA,
                        "El diálogo USSD no respondió o expiró tras 25 segundos. Puedes reintentar."
                    )
                }
            }
        }
    }

    private fun finishWithStatus(status: TransactionStatus, detail: String) {
        val plan = activePlan ?: return
        if (terminalRecorded) return
        terminalRecorded = true
        watchdogJob?.cancel()

        _state.value = when (status) {
            TransactionStatus.COMPLETADA -> UssdSessionState.Completed(plan, detail)
            TransactionStatus.CANCELADA -> UssdSessionState.Cancelled(plan, detail)
            TransactionStatus.FALLIDA -> UssdSessionState.Failed(plan, detail)
            TransactionStatus.DESCONOCIDA -> UssdSessionState.Unknown(plan, detail)
        }

        scope.launch(Dispatchers.IO) {
            database.transactionDao().insert(
                TransactionEntity(
                    timestampMillis = System.currentTimeMillis(),
                    operatorId = plan.summary.operatorId,
                    operatorName = plan.summary.operatorName,
                    phoneNumber = plan.summary.destinationNumber,
                    amountGs = plan.summary.amountGs,
                    operationType = plan.summary.operationType,
                    productName = plan.summary.productName,
                    validity = plan.summary.validity,
                    executionMode = plan.effectiveMode.name,
                    status = status.name,
                    detailMessage = detail
                )
            )
        }
    }
}
