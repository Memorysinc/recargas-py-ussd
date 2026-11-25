package py.com.recargas.ussd.ussd

import py.com.recargas.ussd.catalog.AmountInputMode
import py.com.recargas.ussd.catalog.AuthMode
import py.com.recargas.ussd.catalog.CategoryDefinition
import py.com.recargas.ussd.catalog.CategoryId
import py.com.recargas.ussd.catalog.OperatorDefinition
import py.com.recargas.ussd.catalog.PackageOption
import py.com.recargas.ussd.catalog.QueryOption
import py.com.recargas.ussd.catalog.VerificationStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object UssdPlanBuilder {

    fun buildRechargePlan(
        operator: OperatorDefinition,
        category: CategoryDefinition,
        destinationNumber: String,
        amountGs: Int,
        requestedMode: ExecutionMode
    ): Result<UssdExecutionPlan> {
        val steps = mutableListOf<UssdStep>()
        if (operator.authMode == AuthMode.SESSION_START) {
            steps += UssdStep(StepKind.PIN, "{PIN}", "Ingresar PIN de acceso al menú", isSensitive = true)
        }
        steps += UssdStep(StepKind.MENU_OPTION, category.ussdOption, "Seleccionar ${category.title} (${category.ussdOption})")
        steps += UssdStep(StepKind.DESTINATION, destinationNumber, "Ingresar número destinatario ($destinationNumber)")

        when (category.amountInputMode) {
            AmountInputMode.MENU_OPTION -> {
                val amtOption = category.amounts.firstOrNull { it.amountGs == amountGs }
                    ?: return Result.failure(IllegalArgumentException("Monto Gs. $amountGs no disponible en el menú de ${operator.shortName}."))
                steps += UssdStep(StepKind.AMOUNT, amtOption.ussdOption, "Seleccionar monto Gs. ${formatGs(amountGs)} (opción ${amtOption.ussdOption})")
            }
            AmountInputMode.FREE_STEP, null -> {
                val min = category.minAmountGs ?: 1000
                val max = category.maxAmountGs ?: 124000
                val step = category.stepGs ?: 1000
                if (amountGs < min || amountGs > max || amountGs % step != 0) {
                    return Result.failure(
                        IllegalArgumentException("El monto debe estar entre Gs. ${formatGs(min)} y Gs. ${formatGs(max)} en múltiplos de Gs. ${formatGs(step)}.")
                    )
                }
                steps += UssdStep(StepKind.AMOUNT, amountGs.toString(), "Ingresar monto Gs. ${formatGs(amountGs)}")
            }
        }

        if (operator.authMode == AuthMode.PER_OPERATION) {
            steps += UssdStep(StepKind.PIN, "{PIN}", "Ingresar PIN de autorización", isSensitive = true)
        }
        steps += UssdStep(StepKind.STOP_AT_CONFIRMATION, "", "Detenerse en la confirmación final para tu autorización")

        val direct = resolveDirectCode(operator, "RECHARGE", requestedMode)
        return Result.success(
            UssdExecutionPlan(
                operator = operator,
                summary = OperationSummary(
                    operatorId = operator.id,
                    operatorName = operator.shortName,
                    categoryId = category.id,
                    categoryTitle = category.title,
                    operationType = "Recarga de saldo",
                    productName = "Recarga Gs. ${formatGs(amountGs)}",
                    destinationNumber = destinationNumber,
                    amountGs = amountGs,
                    validity = "Según operador",
                    verificationStatus = category.status,
                    isEconomicOperation = true,
                    note = category.note
                ),
                initialCode = direct.first ?: operator.ussdCode,
                requestedMode = requestedMode,
                effectiveMode = direct.second,
                modeFallbackReason = direct.third,
                steps = steps
            )
        )
    }

    fun buildPackagePlan(
        operator: OperatorDefinition,
        category: CategoryDefinition,
        pkg: PackageOption,
        destinationNumber: String,
        requestedMode: ExecutionMode
    ): UssdExecutionPlan {
        val steps = mutableListOf<UssdStep>()
        if (operator.authMode == AuthMode.SESSION_START) {
            steps += UssdStep(StepKind.PIN, "{PIN}", "Ingresar PIN de acceso al menú", isSensitive = true)
        }
        category.ussdOption.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { opt ->
            steps += UssdStep(StepKind.MENU_OPTION, opt, "Navegar menú ${category.title} (opción $opt)")
        }

        // Claro Roaming asks country first, then number
        if (operator.id == "claro" && category.id == CategoryId.ROAMING) {
            steps += UssdStep(StepKind.MENU_OPTION, pkg.ussdOption, "Seleccionar país: ${pkg.name} (opción ${pkg.ussdOption})")
            steps += UssdStep(StepKind.DESTINATION, destinationNumber, "Ingresar número destino ($destinationNumber)")
        } else {
            steps += UssdStep(StepKind.DESTINATION, destinationNumber, "Ingresar número destinatario ($destinationNumber)")
            steps += UssdStep(StepKind.MENU_OPTION, pkg.ussdOption, "Seleccionar paquete ${pkg.name} (opción ${pkg.ussdOption})")
        }

        if (operator.authMode == AuthMode.PER_OPERATION) {
            steps += UssdStep(StepKind.PIN, "{PIN}", "Ingresar PIN si el operador lo solicita", isSensitive = true)
        }
        steps += UssdStep(StepKind.STOP_AT_CONFIRMATION, "", "Detenerse en la pantalla final para confirmar o cancelar")

        val direct = resolveDirectCode(operator, category.id.name, requestedMode)
        return UssdExecutionPlan(
            operator = operator,
            summary = OperationSummary(
                operatorId = operator.id,
                operatorName = operator.shortName,
                categoryId = category.id,
                categoryTitle = category.title,
                operationType = category.title,
                productName = pkg.name,
                destinationNumber = destinationNumber,
                amountGs = pkg.priceGs,
                validity = pkg.validity.ifBlank { "No especificada" },
                verificationStatus = pkg.status,
                isEconomicOperation = true,
                note = pkg.note.ifBlank { category.note }
            ),
            initialCode = direct.first ?: operator.ussdCode,
            requestedMode = requestedMode,
            effectiveMode = direct.second,
            modeFallbackReason = direct.third,
            steps = steps
        )
    }

    fun buildInteractiveCategoryPlan(
        operator: OperatorDefinition,
        category: CategoryDefinition,
        destinationNumber: String,
        requestedMode: ExecutionMode
    ): UssdExecutionPlan {
        val steps = mutableListOf<UssdStep>()
        if (operator.authMode == AuthMode.SESSION_START) {
            steps += UssdStep(StepKind.PIN, "{PIN}", "Ingresar PIN de acceso al menú", isSensitive = true)
        }
        category.ussdOption.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { opt ->
            steps += UssdStep(StepKind.MENU_OPTION, opt, "Seleccionar ${category.title} (opción $opt)")
        }
        if (category.requiresDestination && destinationNumber.isNotBlank()) {
            steps += UssdStep(StepKind.DESTINATION, destinationNumber, "Ingresar número destino ($destinationNumber)")
        }
        if (operator.authMode == AuthMode.PER_OPERATION) {
            steps += UssdStep(StepKind.PIN, "{PIN}", "Ingresar PIN para ver ofertas", isSensitive = true)
        }
        steps += UssdStep(StepKind.STOP_AT_CONFIRMATION, "", "Continuar interactivamente en el diálogo USSD")

        val direct = resolveDirectCode(operator, category.id.name, requestedMode)
        return UssdExecutionPlan(
            operator = operator,
            summary = OperationSummary(
                operatorId = operator.id,
                operatorName = operator.shortName,
                categoryId = category.id,
                categoryTitle = category.title,
                operationType = category.title,
                productName = "${category.title} (interactivo)",
                destinationNumber = destinationNumber.ifBlank { null },
                amountGs = null,
                validity = "Según menú",
                verificationStatus = category.status,
                isEconomicOperation = true,
                note = category.note
            ),
            initialCode = direct.first ?: operator.ussdCode,
            requestedMode = requestedMode,
            effectiveMode = direct.second,
            modeFallbackReason = direct.third,
            steps = steps
        )
    }

    fun buildQueryPlan(
        operator: OperatorDefinition,
        query: QueryOption,
        destinationNumber: String? = null
    ): UssdExecutionPlan {
        val steps = mutableListOf<UssdStep>()
        if (operator.authMode == AuthMode.SESSION_START) {
            steps += UssdStep(StepKind.PIN, "{PIN}", "Ingresar PIN de acceso al menú", isSensitive = true)
        }
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyyyy"))
        for (raw in query.steps) {
            when (raw) {
                "{PIN}" -> steps += UssdStep(StepKind.PIN, "{PIN}", "Ingresar PIN de consulta", isSensitive = true)
                "{DEST}" -> steps += UssdStep(StepKind.DESTINATION, destinationNumber.orEmpty(), "Ingresar número (${destinationNumber.orEmpty()})")
                "{TODAY}" -> steps += UssdStep(StepKind.DATE, today, "Ingresar fecha de hoy ($today)")
                else -> steps += UssdStep(StepKind.MENU_OPTION, raw, "Enviar opción $raw")
            }
        }
        steps += UssdStep(StepKind.READ_RESULT, "", "Leer resultado de la consulta")

        return UssdExecutionPlan(
            operator = operator,
            summary = OperationSummary(
                operatorId = operator.id,
                operatorName = operator.shortName,
                categoryId = CategoryId.QUERIES,
                categoryTitle = "Consultas",
                operationType = "Consulta",
                productName = query.name,
                destinationNumber = destinationNumber,
                amountGs = null,
                validity = "Informativo",
                verificationStatus = query.status,
                isEconomicOperation = false,
                note = query.note
            ),
            initialCode = operator.ussdCode,
            requestedMode = ExecutionMode.ASSISTED,
            effectiveMode = ExecutionMode.ASSISTED,
            modeFallbackReason = null,
            steps = steps
        )
    }

    private fun resolveDirectCode(
        operator: OperatorDefinition,
        operationType: String,
        requestedMode: ExecutionMode
    ): Triple<String?, ExecutionMode, String?> {
        if (requestedMode == ExecutionMode.ASSISTED) {
            return Triple(null, ExecutionMode.ASSISTED, null)
        }
        val usable = operator.directCodes.firstOrNull {
            it.operationType == operationType &&
                (it.status == VerificationStatus.VERIFIED_USSD || it.status == VerificationStatus.PUBLISHED_UNVERIFIED)
        }
        return if (usable != null) {
            Triple(usable.code, ExecutionMode.DIRECT, null)
        } else {
            Triple(
                null,
                ExecutionMode.ASSISTED,
                "No existe código directo verificado para ${operator.shortName}; se ejecuta en modo asistido seguro."
            )
        }
    }

    private fun formatGs(value: Int): String = "%,d".format(value).replace(',', '.')
}
