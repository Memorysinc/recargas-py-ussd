package py.com.recargas.ussd.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import py.com.recargas.ussd.ussd.ExecutionMode

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "recargas_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class AccentPalette { BLUE, RED, EMERALD, PURPLE, TEAL }
enum class ButtonSizeOption { NORMAL, LARGE, XLARGE }

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentPalette: AccentPalette = AccentPalette.BLUE,
    val buttonSize: ButtonSizeOption = ButtonSizeOption.LARGE,
    val operatorOrder: List<String> = listOf("tigo", "claro"),
    val categoryOrder: List<String> = listOf("RECHARGE", "COMBOS", "INTERNET", "CALLS_SMS", "ROAMING", "PACKS", "QUERIES"),
    val hiddenCategories: Set<String> = emptySet(),
    val defaultOperatorId: String = "tigo",
    val defaultAmountGs: Int = 5000,
    val updateIntervalDays: Int = 7,
    val catalogUpdateUrl: String = "https://raw.githubusercontent.com/Memorysinc/recargas-py-ussd/main/catalog/catalog_v1.json",
    val showUpdateAlerts: Boolean = true,
    val autoPinTigo: Boolean = false,
    val autoPinClaro: Boolean = false,
    val defaultExecutionMode: ExecutionMode = ExecutionMode.ASSISTED,
    val deviceLockBypassAccepted: Boolean = false
) {
    fun isAutoPinEnabled(operatorId: String): Boolean = when (operatorId) {
        "tigo" -> autoPinTigo
        "claro" -> autoPinClaro
        else -> false
    }
}

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent_palette")
        val BUTTON_SIZE = stringPreferencesKey("button_size")
        val OP_ORDER = stringPreferencesKey("operator_order")
        val CAT_ORDER = stringPreferencesKey("category_order")
        val HIDDEN_CATS = stringPreferencesKey("hidden_categories")
        val DEFAULT_OP = stringPreferencesKey("default_operator")
        val DEFAULT_AMOUNT = intPreferencesKey("default_amount_gs")
        val UPDATE_DAYS = intPreferencesKey("update_interval_days")
        val CATALOG_URL = stringPreferencesKey("catalog_url")
        val UPDATE_ALERTS = booleanPreferencesKey("show_update_alerts")
        val AUTO_PIN_TIGO = booleanPreferencesKey("auto_pin_tigo")
        val AUTO_PIN_CLARO = booleanPreferencesKey("auto_pin_claro")
        val EXEC_MODE = stringPreferencesKey("default_exec_mode")
        val LOCK_BYPASS = booleanPreferencesKey("device_lock_bypass")
    }

    val preferences: Flow<AppPreferences> = context.dataStore.data.map { p ->
        val defaults = AppPreferences()
        AppPreferences(
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME] ?: defaults.themeMode.name) }.getOrDefault(defaults.themeMode),
            accentPalette = runCatching { AccentPalette.valueOf(p[Keys.ACCENT] ?: defaults.accentPalette.name) }.getOrDefault(defaults.accentPalette),
            buttonSize = runCatching { ButtonSizeOption.valueOf(p[Keys.BUTTON_SIZE] ?: defaults.buttonSize.name) }.getOrDefault(defaults.buttonSize),
            operatorOrder = p[Keys.OP_ORDER]?.split(",")?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() } ?: defaults.operatorOrder,
            categoryOrder = p[Keys.CAT_ORDER]?.split(",")?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() } ?: defaults.categoryOrder,
            hiddenCategories = p[Keys.HIDDEN_CATS]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
            defaultOperatorId = p[Keys.DEFAULT_OP] ?: defaults.defaultOperatorId,
            defaultAmountGs = p[Keys.DEFAULT_AMOUNT] ?: defaults.defaultAmountGs,
            updateIntervalDays = p[Keys.UPDATE_DAYS] ?: defaults.updateIntervalDays,
            catalogUpdateUrl = p[Keys.CATALOG_URL] ?: defaults.catalogUpdateUrl,
            showUpdateAlerts = p[Keys.UPDATE_ALERTS] ?: defaults.showUpdateAlerts,
            autoPinTigo = p[Keys.AUTO_PIN_TIGO] ?: defaults.autoPinTigo,
            autoPinClaro = p[Keys.AUTO_PIN_CLARO] ?: defaults.autoPinClaro,
            defaultExecutionMode = runCatching { ExecutionMode.valueOf(p[Keys.EXEC_MODE] ?: defaults.defaultExecutionMode.name) }.getOrDefault(defaults.defaultExecutionMode),
            deviceLockBypassAccepted = p[Keys.LOCK_BYPASS] ?: defaults.deviceLockBypassAccepted
        )
    }

    suspend fun update(block: (AppPreferences) -> AppPreferences) {
        context.dataStore.edit { p ->
            val current = AppPreferences(
                themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME] ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM),
                accentPalette = runCatching { AccentPalette.valueOf(p[Keys.ACCENT] ?: AccentPalette.BLUE.name) }.getOrDefault(AccentPalette.BLUE),
                buttonSize = runCatching { ButtonSizeOption.valueOf(p[Keys.BUTTON_SIZE] ?: ButtonSizeOption.LARGE.name) }.getOrDefault(ButtonSizeOption.LARGE),
                operatorOrder = p[Keys.OP_ORDER]?.split(",")?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() } ?: listOf("tigo", "claro"),
                categoryOrder = p[Keys.CAT_ORDER]?.split(",")?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() } ?: listOf("RECHARGE", "COMBOS", "INTERNET", "CALLS_SMS", "ROAMING", "PACKS", "QUERIES"),
                hiddenCategories = p[Keys.HIDDEN_CATS]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
                defaultOperatorId = p[Keys.DEFAULT_OP] ?: "tigo",
                defaultAmountGs = p[Keys.DEFAULT_AMOUNT] ?: 5000,
                updateIntervalDays = p[Keys.UPDATE_DAYS] ?: 7,
                catalogUpdateUrl = p[Keys.CATALOG_URL] ?: "https://raw.githubusercontent.com/Memorysinc/recargas-py-ussd/main/catalog/catalog_v1.json",
                showUpdateAlerts = p[Keys.UPDATE_ALERTS] ?: true,
                autoPinTigo = p[Keys.AUTO_PIN_TIGO] ?: false,
                autoPinClaro = p[Keys.AUTO_PIN_CLARO] ?: false,
                defaultExecutionMode = runCatching { ExecutionMode.valueOf(p[Keys.EXEC_MODE] ?: ExecutionMode.ASSISTED.name) }.getOrDefault(ExecutionMode.ASSISTED),
                deviceLockBypassAccepted = p[Keys.LOCK_BYPASS] ?: false
            )
            val next = block(current)
            p[Keys.THEME] = next.themeMode.name
            p[Keys.ACCENT] = next.accentPalette.name
            p[Keys.BUTTON_SIZE] = next.buttonSize.name
            p[Keys.OP_ORDER] = next.operatorOrder.joinToString(",")
            p[Keys.CAT_ORDER] = next.categoryOrder.joinToString(",")
            p[Keys.HIDDEN_CATS] = next.hiddenCategories.joinToString(",")
            p[Keys.DEFAULT_OP] = next.defaultOperatorId
            p[Keys.DEFAULT_AMOUNT] = next.defaultAmountGs
            p[Keys.UPDATE_DAYS] = next.updateIntervalDays
            p[Keys.CATALOG_URL] = next.catalogUpdateUrl
            p[Keys.UPDATE_ALERTS] = next.showUpdateAlerts
            p[Keys.AUTO_PIN_TIGO] = next.autoPinTigo
            p[Keys.AUTO_PIN_CLARO] = next.autoPinClaro
            p[Keys.EXEC_MODE] = next.defaultExecutionMode.name
            p[Keys.LOCK_BYPASS] = next.deviceLockBypassAccepted
        }
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { it.clear() }
    }
}
