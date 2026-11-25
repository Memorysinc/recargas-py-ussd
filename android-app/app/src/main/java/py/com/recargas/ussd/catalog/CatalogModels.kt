package py.com.recargas.ussd.catalog

import kotlinx.serialization.Serializable

@Serializable
enum class VerificationStatus {
    VERIFIED_USSD,
    PUBLISHED_UNVERIFIED,
    PENDING,
    BROKEN,
    UNAVAILABLE_UNVERIFIED
}

@Serializable
enum class AuthMode {
    SESSION_START,
    PER_OPERATION
}

@Serializable
enum class CategoryId {
    RECHARGE,
    COMBOS,
    INTERNET,
    CALLS_SMS,
    ROAMING,
    PACKS,
    QUERIES
}

@Serializable
enum class AmountInputMode {
    MENU_OPTION,
    FREE_STEP
}

@Serializable
data class OperatorCatalog(
    val schemaVersion: Int,
    val catalogVersion: String,
    val updatedAt: String,
    val sourceLabel: String = "",
    val verificationLevel: VerificationStatus = VerificationStatus.VERIFIED_USSD,
    val integritySha256: String? = null,
    val operators: List<OperatorDefinition> = emptyList()
)

@Serializable
data class OperatorDefinition(
    val id: String,
    val name: String,
    val shortName: String,
    val brandColorHex: String,
    val ussdCode: String,
    val simMatch: SimMatchRule,
    val authMode: AuthMode,
    val phoneFormat: PhoneFormatRule,
    val directCodes: List<DirectCodeEntry> = emptyList(),
    val ussdRules: UssdRules,
    val categories: List<CategoryDefinition> = emptyList()
)

@Serializable
data class SimMatchRule(
    val mcc: String = "744",
    val mnc: String,
    val carrierKeywords: List<String> = emptyList()
)

@Serializable
data class PhoneFormatRule(
    val requireLeadingZero: Boolean,
    val minDigits: Int,
    val maxDigits: Int,
    val example: String,
    val hint: String
)

@Serializable
data class DirectCodeEntry(
    val code: String,
    val operationType: String,
    val status: VerificationStatus,
    val note: String = ""
)

@Serializable
data class UssdRules(
    val pinPromptKeywords: List<String> = emptyList(),
    val pinExcludeKeywords: List<String> = emptyList(),
    val destinationPromptKeywords: List<String> = emptyList(),
    val amountPromptKeywords: List<String> = emptyList(),
    val confirmKeywords: List<String> = emptyList(),
    val confirmOption: String = "1",
    val cancelOption: String = "2",
    val cancelResultKeywords: List<String> = emptyList(),
    val errorKeywords: List<String> = emptyList()
)

@Serializable
data class CategoryDefinition(
    val id: CategoryId,
    val title: String,
    val ussdOption: String = "",
    val status: VerificationStatus = VerificationStatus.VERIFIED_USSD,
    val requiresDestination: Boolean = true,
    val amountInputMode: AmountInputMode? = null,
    val amounts: List<RechargeAmountOption> = emptyList(),
    val minAmountGs: Int? = null,
    val maxAmountGs: Int? = null,
    val stepGs: Int? = null,
    val suggestedAmountsGs: List<Int> = emptyList(),
    val packages: List<PackageOption> = emptyList(),
    val queries: List<QueryOption> = emptyList(),
    val note: String = ""
)

@Serializable
data class RechargeAmountOption(
    val amountGs: Int,
    val ussdOption: String,
    val status: VerificationStatus = VerificationStatus.VERIFIED_USSD
)

@Serializable
data class PackageOption(
    val id: String,
    val ussdOption: String,
    val name: String,
    val priceGs: Int? = null,
    val validity: String = "",
    val status: VerificationStatus = VerificationStatus.VERIFIED_USSD,
    val note: String = ""
)

@Serializable
data class QueryOption(
    val id: String,
    val name: String,
    val steps: List<String>,
    val requiresPin: Boolean = false,
    val requiresDestination: Boolean = false,
    val status: VerificationStatus = VerificationStatus.VERIFIED_USSD,
    val note: String = ""
)
