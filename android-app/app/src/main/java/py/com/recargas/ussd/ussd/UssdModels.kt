package py.com.recargas.ussd.ussd

import py.com.recargas.ussd.catalog.CategoryId
import py.com.recargas.ussd.catalog.OperatorDefinition
import py.com.recargas.ussd.catalog.VerificationStatus

enum class ExecutionMode {
    ASSISTED,
    DIRECT
}

enum class StepKind {
    PIN,
    MENU_OPTION,
    DESTINATION,
    AMOUNT,
    DATE,
    STOP_AT_CONFIRMATION,
    READ_RESULT
}

data class UssdStep(
    val kind: StepKind,
    val value: String,
    val label: String,
    val isSensitive: Boolean = false
)

data class OperationSummary(
    val operatorId: String,
    val operatorName: String,
    val categoryId: CategoryId,
    val categoryTitle: String,
    val operationType: String,
    val productName: String,
    val destinationNumber: String?,
    val amountGs: Int?,
    val validity: String,
    val verificationStatus: VerificationStatus,
    val isEconomicOperation: Boolean,
    val note: String = ""
)

data class UssdExecutionPlan(
    val operator: OperatorDefinition,
    val summary: OperationSummary,
    val initialCode: String,
    val requestedMode: ExecutionMode,
    val effectiveMode: ExecutionMode,
    val modeFallbackReason: String? = null,
    val steps: List<UssdStep>
)
