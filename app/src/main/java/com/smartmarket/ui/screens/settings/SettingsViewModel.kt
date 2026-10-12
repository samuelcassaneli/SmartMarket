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
import com.smartmarket.domain.backup.ImportPreview
import com.smartmarket.work.AutoBackupWorker
import com.smartmarket.domain.ai.GeminiClient
import com.smartmarket.data.entity.FlyerKind
import com.smartmarket.data.entity.Supermarket
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
private val MARKET_COLORS = listOf("#E30613", "#F28C00", "#0057A8", "#2E7D32", "#7B1FA2", "#00838F", "#C2185B", "#5D4037")

class SettingsViewModel(
    private val appContext: Context,
    private val settings: SettingsRepository,
    db: AppDatabase,
    private val gemini: GeminiClient
) : ViewModel() {

    private val supermarketDao = db.supermarketDao()

    private val backup = BackupManager(appContext, db, settings)

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

    /** Modo rápido usa o Flash-Lite; o modelo é escolhido de novo com a chave atual. */
    fun setAiFast(fast: Boolean) = viewModelScope.launch {
        settings.setAiFastMode(fast)
        val key = settings.current().geminiApiKey
        if (key.isNotBlank()) {
            runCatching { gemini.discoverModel(key, fast) }
                .onSuccess { settings.setGemini(key, it); _messages.tryEmit("Modelo: $it") }
        }
    }

    // ---- Mercados e links de encarte

    val markets: StateFlow<List<Supermarket>> = supermarketDao.getAllSupermarkets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Salva o link de encartes do mercado; o tipo de leitura é detectado pelo endereço. */
    fun saveFlyerLink(market: Supermarket, url: String) = viewModelScope.launch {
        val clean = normalizeLink(url)
        supermarketDao.updateSupermarket(market.copy(flyerUrl = clean, flyerKind = FlyerKind.detect(clean)))
        _messages.tryEmit(if (clean.isBlank()) "Link removido de ${market.name}" else "Link salvo para ${market.name}")
    }

    /** Cria um mercado a partir do nome e do link (para quem mora em outra cidade). */
    fun addMarketWithLink(name: String, url: String, city: String) = viewModelScope.launch {
        val clean = normalizeLink(url)
        if (name.isBlank()) return@launch
        supermarketDao.insertSupermarket(
            Supermarket(
                name = name.trim(),
                city = city.trim(),
                flyerUrl = clean,
                flyerKind = FlyerKind.detect(clean),
                colorHex = MARKET_COLORS[(markets.value.size) % MARKET_COLORS.size]
            )
        )
        _messages.tryEmit("${name.trim()} adicionado")
    }

    private fun normalizeLink(url: String): String {
        val u = url.trim()
        if (u.isBlank()) return ""
        return if (u.startsWith("http://") || u.startsWith("https://")) u else "https://$u"
    }

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
            val model = gemini.discoverModel(k, settings.current().aiFastMode)
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

    private val _importPreview = MutableStateFlow<ImportPreview?>(null)
    /** Arquivo escolhido e conferido, esperando o usuário confirmar a restauração. */
    val importPreview: StateFlow<ImportPreview?> = _importPreview.asStateFlow()

    private val _canUndoImport = MutableStateFlow(backup.hasSnapshot())
    val canUndoImport: StateFlow<Boolean> = _canUndoImport.asStateFlow()

    /** Lê o arquivo e mostra o que tem nele antes de apagar qualquer coisa. */
    fun previewImport(uri: Uri) = runBusy {
        _importPreview.value = backup.preview(uri)
    }

    fun cancelImport() { _importPreview.value = null }

    fun confirmImport() {
        val preview = _importPreview.value ?: return
        _importPreview.value = null
        runBusy {
            val summary = backup.importFrom(preview.uri)
            _canUndoImport.value = backup.hasSnapshot()
            _messages.tryEmit("Backup restaurado: ${summary.describe()}")
        }
    }

    fun undoImport() = runBusy {
        val summary = backup.undoImport()
        _canUndoImport.value = backup.hasSnapshot()
        _messages.tryEmit("Dados de antes da importação de volta: ${summary.describe()}")
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
