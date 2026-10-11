package com.smartmarket.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartmarket.data.db.AppDatabase
import com.smartmarket.data.prefs.AppSettings
import com.smartmarket.data.prefs.SettingsRepository
import com.smartmarket.domain.backup.BackupFormatException
import com.smartmarket.domain.backup.BackupManager
import com.smartmarket.work.AutoBackupWorker
import com.smartmarket.domain.ai.GeminiClient
import com.smartmarket.work.FlyerSyncWorker
import com.smartmarket.work.PriceAlertWorker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Configurações. Cada toggle grava a preferência; o [com.smartmarket.BackgroundJobs] observa as
 * preferências e reagenda tudo. Mesmo assim, aqui também chamamos o `schedule` de cada worker
 * (todos são idempotentes) para o efeito ser imediato:
 * - encartes: [FlyerSyncWorker.schedule];
 * - alertas de preço: [PriceAlertWorker.schedule];
 * - backup automático: [AutoBackupWorker.schedule].
 */
class SettingsViewModel(
    private val appContext: Context,
    private val settings: SettingsRepository,
    db: AppDatabase,
    private val gemini: GeminiClient
) : ViewModel() {

    private val backup = BackupManager(appContext, db)

    val state: StateFlow<AppSettings?> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun setPostalCode(digits: String) = viewModelScope.launch {
        settings.setPostalCode(digits)
        _messages.tryEmit("CEP salvo")
    }

    fun setMonthlyBudget(value: Double) = viewModelScope.launch {
        settings.setMonthlyBudget(value.coerceAtLeast(0.0))
        _messages.tryEmit(if (value > 0) "Orçamento salvo" else "Orçamento removido")
    }

    fun setTheme(mode: String) = viewModelScope.launch { settings.setThemeMode(mode) }

    /** Frequência da verificação de encartes e preços (0 = desligada). */
    fun setCheckInterval(days: Int) = viewModelScope.launch {
        settings.setCheckIntervalDays(days)
        FlyerSyncWorker.schedule(appContext, days)
        _messages.tryEmit(if (days > 0) "Verificação agendada" else "Verificação automática desligada")
    }

    fun checkNow() {
        FlyerSyncWorker.runNow(appContext)
        _messages.tryEmit("Verificando encartes e preços em segundo plano…")
    }

    // ---- Leitura com IA

    private val _aiChecking = MutableStateFlow(false)
    val aiChecking: StateFlow<Boolean> = _aiChecking.asStateFlow()

    fun setAiEnabled(enabled: Boolean) = viewModelScope.launch { settings.setAiEnabled(enabled) }

    /** Valida a chave listando os modelos e guarda o melhor "flash" disponível. */
    fun saveGeminiKey(key: String) = viewModelScope.launch {
        val k = key.trim()
        if (k.isEmpty()) {
            settings.setGemini("", "")
            settings.setAiEnabled(false)
            _messages.tryEmit("Chave removida")
            return@launch
        }
        _aiChecking.value = true
        try {
            val model = gemini.discoverModel(k)
            settings.setGemini(k, model)
            settings.setAiEnabled(true)
            _messages.tryEmit("Chave válida. Modelo: $model")
        } catch (e: Exception) {
            _messages.tryEmit(e.message ?: "Não foi possível validar a chave")
        } finally {
            _aiChecking.value = false
        }
    }

    fun setPriceAlerts(enabled: Boolean) = viewModelScope.launch {
        settings.setPriceAlertsEnabled(enabled)
        PriceAlertWorker.schedule(appContext, enabled)
    }

    /** Chamado depois que o usuário escolheu a pasta (OpenDocumentTree). */
    fun enableAutoBackup(treeUri: Uri) = viewModelScope.launch {
        try {
            appContext.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            _messages.tryEmit("Não foi possível acessar essa pasta. Escolha outra.")
            return@launch
        }
        settings.setAutoBackup(true, treeUri.toString())
        AutoBackupWorker.schedule(appContext, true)
        AutoBackupWorker.runNow(appContext)
        _messages.tryEmit("Backup semanal ligado. O primeiro está sendo feito agora.")
    }

    fun disableAutoBackup() = viewModelScope.launch {
        val current = settings.current()
        settings.setAutoBackup(false, current.autoBackupTreeUri)
        AutoBackupWorker.schedule(appContext, false)
    }

    fun backupNow() {
        AutoBackupWorker.runNow(appContext)
        _messages.tryEmit("Backup em andamento na pasta escolhida")
    }

    fun exportBackup(uri: Uri) = runBusy {
        val summary = backup.exportTo(uri)
        _messages.tryEmit("Backup salvo: ${summary.describe()}")
    }

    fun importBackup(uri: Uri) = runBusy {
        val summary = backup.importFrom(uri)
        _messages.tryEmit("Backup restaurado: ${summary.describe()}")
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } catch (e: BackupFormatException) {
                _messages.tryEmit(e.message ?: "Arquivo inválido")
            } catch (e: Exception) {
                _messages.tryEmit("Falhou: ${e.message ?: "erro desconhecido"}")
            } finally {
                _busy.value = false
            }
        }
    }
}
