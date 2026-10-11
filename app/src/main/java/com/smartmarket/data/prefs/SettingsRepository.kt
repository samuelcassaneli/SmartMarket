package com.smartmarket.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
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
    val atacadaoRegionId: String = ""
) {
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
            atacadaoRegionId = p[Keys.ATACADAO_REGION] ?: ""
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
}
