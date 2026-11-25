package py.com.recargas.ussd.ussd

import py.com.recargas.ussd.catalog.PhoneFormatRule

data class PhoneValidationResult(
    val isValid: Boolean,
    val normalized: String,
    val errorMessage: String? = null
)

object PhoneFormatter {
    fun normalizeAndValidate(rawInput: String, rule: PhoneFormatRule): PhoneValidationResult {
        var digits = rawInput.filter { it.isDigit() }
        if (digits.isEmpty()) {
            return PhoneValidationResult(false, "", "Ingresa un número de teléfono.")
        }

        // Handle pasted Paraguay country prefix +595 or 595
        if (digits.startsWith("595") && digits.length >= 12) {
            digits = digits.removePrefix("595")
        }

        val normalized = if (rule.requireLeadingZero) {
            if (!digits.startsWith("0") && digits.length == rule.minDigits - 1) {
                "0$digits"
            } else {
                digits
            }
        } else {
            if (digits.startsWith("0") && digits.length == rule.maxDigits + 1) {
                digits.removePrefix("0")
            } else {
                digits
            }
        }

        if (rule.requireLeadingZero && !normalized.startsWith("0")) {
            return PhoneValidationResult(
                false,
                normalized,
                "Este operador requiere 0 inicial (ej. ${rule.example})."
            )
        }
        if (!rule.requireLeadingZero && normalized.startsWith("0")) {
            return PhoneValidationResult(
                false,
                normalized,
                "Este operador requiere el número sin 0 inicial (ej. ${rule.example})."
            )
        }
        if (normalized.length < rule.minDigits || normalized.length > rule.maxDigits) {
            return PhoneValidationResult(
                false,
                normalized,
                "Longitud inválida (${normalized.length} dígitos). Se esperan ${rule.minDigits} dígitos (ej. ${rule.example})."
            )
        }

        return PhoneValidationResult(true, normalized, null)
    }
}
