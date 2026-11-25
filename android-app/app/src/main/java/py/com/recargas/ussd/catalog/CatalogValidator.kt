package py.com.recargas.ussd.catalog

import kotlinx.serialization.json.Json
import java.security.MessageDigest

data class ValidationResult(
    val isValid: Boolean,
    val catalog: OperatorCatalog? = null,
    val errors: List<String> = emptyList()
)

object CatalogValidator {
    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val forbiddenPinKeys = Regex(""""pin"\s*:\s*"[0-9]{3,8}"""", RegexOption.IGNORE_CASE)

    fun parseAndValidate(rawJson: String): ValidationResult {
        if (rawJson.isBlank()) {
            return ValidationResult(false, errors = listOf("El archivo JSON del catálogo está vacío."))
        }
        if (forbiddenPinKeys.containsMatchIn(rawJson)) {
            return ValidationResult(false, errors = listOf("Seguridad: el catálogo contiene un PIN en texto plano y fue rechazado."))
        }
        val parsed = try {
            json.decodeFromString(OperatorCatalog.serializer(), rawJson)
        } catch (e: Exception) {
            return ValidationResult(false, errors = listOf("Error de formato JSON: ${e.message ?: "esquema inválido"}"))
        }

        val errors = mutableListOf<String>()
        if (parsed.schemaVersion < 1) errors += "schemaVersion inválido (${parsed.schemaVersion})."
        if (parsed.catalogVersion.isBlank()) errors += "catalogVersion vacío."
        if (parsed.updatedAt.isBlank()) errors += "updatedAt vacío."
        if (parsed.operators.isEmpty()) errors += "El catálogo no define ningún operador."

        parsed.integritySha256?.takeIf { it.isNotBlank() }?.let { expected ->
            val withoutHash = rawJson.replace(
                Regex(""""integritySha256"\s*:\s*"[^"]*""""),
                """"integritySha256": null"""
            )
            val actual = sha256Hex(withoutHash)
            if (!actual.equals(expected, ignoreCase = true)) {
                errors += "Fallo de integridad SHA-256."
            }
        }

        val opIds = mutableSetOf<String>()
        for (op in parsed.operators) {
            if (op.id.isBlank()) errors += "Operador con id vacío."
            if (!opIds.add(op.id)) errors += "Operador duplicado: ${op.id}."
            if (!op.ussdCode.startsWith("*") || !op.ussdCode.endsWith("#")) {
                errors += "Código USSD inicial inválido en ${op.id}: ${op.ussdCode}."
            }
            if (op.phoneFormat.minDigits <= 0 || op.phoneFormat.maxDigits < op.phoneFormat.minDigits) {
                errors += "Formato telefónico inválido en ${op.id}."
            }
            if (op.categories.isEmpty()) {
                errors += "Operador ${op.id} sin categorías."
            }
            for (cat in op.categories) {
                for (amt in cat.amounts) {
                    if (amt.amountGs <= 0) errors += "Monto inválido en ${op.id}/${cat.id}: ${amt.amountGs}."
                    if (amt.ussdOption.isBlank()) errors += "Opción USSD vacía en monto ${amt.amountGs} de ${op.id}."
                }
                if (cat.amountInputMode == AmountInputMode.FREE_STEP) {
                    val min = cat.minAmountGs ?: 0
                    val max = cat.maxAmountGs ?: 0
                    val step = cat.stepGs ?: 0
                    if (min <= 0 || max < min || step <= 0) {
                        errors += "Rango libre inválido en ${op.id}/${cat.id}."
                    }
                }
                for (pkg in cat.packages) {
                    if (pkg.name.isBlank()) errors += "Paquete sin nombre en ${op.id}/${cat.id}."
                    if (pkg.priceGs != null && pkg.priceGs <= 0) {
                        errors += "Precio de paquete inválido (${pkg.name}): ${pkg.priceGs}."
                    }
                }
            }
        }

        return if (errors.isEmpty()) {
            ValidationResult(true, parsed, emptyList())
        } else {
            ValidationResult(false, null, errors)
        }
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
