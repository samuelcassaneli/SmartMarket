package com.smartmarket.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object ThemeMode {
    const val SYSTEM = "SYSTEM"
    const val LIGHT = "LIGHT"
    const val DARK = "DARK"
}

data class AppSettings(
    val postalCode: String = DEFAULT_POSTAL_CODE,
    val monthlyBudget: Double = 0.0,
    val themeMode: String = ThemeMode.SYSTEM,
    val flyerAutoSync: Boolean = true,
    val priceAlertsEnabled: Boolean = true,
    val autoBackupEnabled: Boolean = false,
    val autoBackupTreeUri: String = "",
    val atacadaoRegionId: String = "",
    /** Leitura de encartes com IA (Gemini, chave do próprio usuário). */
    val aiEnabled: Boolean = false,
    val geminiApiKey: String = "",
    /** Modelo escolhido automaticamente na validação da chave (ex.: "gemini-2.5-flash"). */
    val geminiModel: String = "",
    /** Intervalo da verificação automática de encartes e preços, em dias (0 = desligada). */
    val checkIntervalDays: Int = 7
) {
    val aiReady: Boolean get() = aiEnabled && geminiApiKey.isNotBlank()

    companion object {
        const val DEFAULT_POSTAL_CODE = "27511000" // Resende - RJ (região atendida pela loja Atacadão Resende)
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val POSTAL_CODE = stringPreferencesKey("postal_code")
        val MONTHLY_BUDGET = doublePreferencesKey("monthly_budget")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FLYER_AUTO_SYNC = booleanPreferencesKey("flyer_auto_sync")
        val PRICE_ALERTS = booleanPreferencesKey("price_alerts")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        val AUTO_BACKUP_URI = stringPreferencesKey("auto_backup_uri")
        val ATACADAO_REGION = stringPreferencesKey("atacadao_region")
        val AI_ENABLED = booleanPreferencesKey("ai_enabled")
        val GEMINI_KEY = stringPreferencesKey("gemini_key")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val CHECK_INTERVAL = intPreferencesKey("check_interval_days")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            postalCode = p[Keys.POSTAL_CODE] ?: AppSettings.DEFAULT_POSTAL_CODE,
            monthlyBudget = p[Keys.MONTHLY_BUDGET] ?: 0.0,
            themeMode = p[Keys.THEME_MODE] ?: ThemeMode.SYSTEM,
            flyerAutoSync = p[Keys.FLYER_AUTO_SYNC] ?: true,
            priceAlertsEnabled = p[Keys.PRICE_ALERTS] ?: true,
            autoBackupEnabled = p[Keys.AUTO_BACKUP] ?: false,
            autoBackupTreeUri = p[Keys.AUTO_BACKUP_URI] ?: "",
            atacadaoRegionId = p[Keys.ATACADAO_REGION] ?: "",
            aiEnabled = p[Keys.AI_ENABLED] ?: false,
            geminiApiKey = p[Keys.GEMINI_KEY] ?: "",
            geminiModel = p[Keys.GEMINI_MODEL] ?: "",
            // quem desligou a busca automática de encartes na versão anterior continua desligado
            checkIntervalDays = p[Keys.CHECK_INTERVAL] ?: if (p[Keys.FLYER_AUTO_SYNC] == false) 0 else 7
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setPostalCode(cep: String) = context.dataStore.edit {
        it[Keys.POSTAL_CODE] = cep.filter(Char::isDigit)
        it.remove(Keys.ATACADAO_REGION) // região depende do CEP
    }

    suspend fun setMonthlyBudget(value: Double) = context.dataStore.edit { it[Keys.MONTHLY_BUDGET] = value }
    suspend fun setThemeMode(mode: String) = context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    suspend fun setFlyerAutoSync(enabled: Boolean) = context.dataStore.edit { it[Keys.FLYER_AUTO_SYNC] = enabled }
    suspend fun setPriceAlertsEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.PRICE_ALERTS] = enabled }
    suspend fun setAutoBackup(enabled: Boolean, treeUri: String) = context.dataStore.edit {
        it[Keys.AUTO_BACKUP] = enabled
        it[Keys.AUTO_BACKUP_URI] = treeUri
    }
    suspend fun setAtacadaoRegionId(regionId: String) = context.dataStore.edit { it[Keys.ATACADAO_REGION] = regionId }
    suspend fun setAiEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.AI_ENABLED] = enabled }
    suspend fun setGemini(key: String, model: String) = context.dataStore.edit {
        it[Keys.GEMINI_KEY] = key.trim()
        it[Keys.GEMINI_MODEL] = model
    }
    suspend fun setCheckIntervalDays(days: Int) = context.dataStore.edit {
        it[Keys.CHECK_INTERVAL] = days
        it[Keys.FLYER_AUTO_SYNC] = days > 0
    }
}
