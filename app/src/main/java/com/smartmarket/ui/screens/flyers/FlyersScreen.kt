package com.smartmarket.ui.screens.flyers

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.smartmarket.data.dao.FlyerDao
import com.smartmarket.data.dao.FlyerWithMarket
import com.smartmarket.data.dao.SupermarketDao
import com.smartmarket.data.entity.FlyerStatus
import com.smartmarket.data.entity.Supermarket
import com.smartmarket.domain.flyers.FlyerTitle
import com.smartmarket.ui.appViewModel
import com.smartmarket.ui.components.AppCard
import com.smartmarket.ui.components.AppTopBar
import com.smartmarket.ui.components.EmptyState
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.LoadingBox
import com.smartmarket.ui.components.MarketAvatar
import com.smartmarket.ui.components.ScreenScaffold
import com.smartmarket.ui.components.SectionHeader
import com.smartmarket.ui.components.Tag
import com.smartmarket.ui.navigation.Routes
import com.smartmarket.ui.theme.Spacing
import com.smartmarket.ui.theme.extended
import com.smartmarket.work.FlyerSyncWorker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

data class SyncUi(val running: Boolean = false, val message: String? = null)

class FlyersViewModel(
    private val appContext: Context,
    flyerDao: FlyerDao,
    supermarketDao: SupermarketDao
) : ViewModel() {

    /** null enquanto carrega. */
    val flyers: StateFlow<List<FlyerWithMarket>?> = flyerDao.getFlyers()
        .map<List<FlyerWithMarket>, List<FlyerWithMarket>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val markets: StateFlow<Map<Long, Supermarket>> = supermarketDao.getAllSupermarkets()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _sync = MutableStateFlow(SyncUi())
    val sync = _sync.asStateFlow()

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val workManager = WorkManager.getInstance(appContext)
    private val liveData = workManager.getWorkInfosForUniqueWorkLiveData(FlyerSyncWorker.UNIQUE_NOW)
    private var sawRunning = false

    private val observer = Observer<List<WorkInfo>> { infos ->
        val info = infos?.lastOrNull()
        if (info == null) {
            _sync.value = SyncUi(false)
            return@Observer
        }
        when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                sawRunning = true
                _sync.value = SyncUi(true, "Aguardando conexão…")
            }
            WorkInfo.State.RUNNING -> {
                sawRunning = true
                _sync.value = SyncUi(true, info.progress.getString(FlyerSyncWorker.KEY_PROGRESS) ?: "Procurando encartes…")
            }
            WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                _sync.value = SyncUi(false)
                if (sawRunning) {
                    sawRunning = false
                    val summary = info.outputData.getString(FlyerSyncWorker.KEY_SUMMARY)
                    _events.trySend(
                        when (info.state) {
                            WorkInfo.State.SUCCEEDED -> summary ?: "Busca concluída"
                            WorkInfo.State.CANCELLED -> "Busca cancelada"
                            else -> summary?.let { "Falha na busca: $it" } ?: "Não foi possível buscar os encartes"
                        }
                    )
                }
            }
            else -> _sync.value = SyncUi(false)
        }
    }

    init {
        liveData.observeForever(observer)
    }

    fun syncNow() {
        sawRunning = true
        _sync.value = SyncUi(true, "Procurando encartes…")
        FlyerSyncWorker.runNow(appContext)
    }

    override fun onCleared() {
        liveData.removeObserver(observer)
    }
}

/** Encartes baixados automaticamente, com busca manual e atalho para leitura de foto/PDF. */
@Composable
fun FlyersScreen(navController: NavController) {
    val context = LocalContext.current
    val vm = appViewModel { c -> FlyersViewModel(c.context.applicationContext, c.db.flyerDao(), c.db.supermarketDao()) }
    val flyers by vm.flyers.collectAsStateWithLifecycle()
    val markets by vm.markets.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    ScreenScaffold(
        topBar = { AppTopBar(title = "Encartes", onBack = { navController.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val list = flyers
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item(key = "actions") {
                SyncCard(
                    sync = sync,
                    marketsWithFlyer = markets.values.count { it.flyerUrl.isNotBlank() },
                    onSync = { vm.syncNow() },
                    onManual = { navController.navigate(Routes.FLYER_SCAN) }
                )
            }
            when {
                list == null -> item(key = "loading") { LoadingBox(Modifier.height(200.dp)) }
                list.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        icon = Icons.Default.Newspaper,
                        title = "Nenhum encarte ainda",
                        message = "Toque em \"Buscar encartes agora\" para baixar as ofertas do Spani e do Dom. O app também busca sozinho uma vez por dia."
                    )
                }
                else -> {
                    item(key = "header") { SectionHeader("Baixados") }
                    items(list, key = { it.id }) { f ->
                        FlyerRow(
                            flyer = f,
                            colorHex = markets[f.supermarketId]?.colorHex,
                            onClick = { navController.navigate(Routes.flyerReview(f.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncCard(sync: SyncUi, marketsWithFlyer: Int, onSync: () -> Unit, onManual: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Text("Ofertas dos mercados", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        Text(
            if (marketsWithFlyer > 0) "$marketsWithFlyer mercados com encarte no site. A leitura é automática e você revisa antes de salvar."
            else "Nenhum mercado tem endereço de encarte configurado.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.md))
        if (sync.running) {
            Text(sync.message ?: "Procurando encartes…", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(Spacing.sm))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.md))
        }
        Button(onClick = onSync, enabled = !sync.running && marketsWithFlyer > 0, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(if (sync.running) "Buscando…" else "Buscar encartes agora")
        }
        Spacer(Modifier.height(Spacing.sm))
        OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text("Ler encarte manualmente")
        }
    }
}

@Composable
private fun FlyerRow(flyer: FlyerWithMarket, colorHex: String?, onClick: () -> Unit) {
    val failed = flyer.status == FlyerStatus.FAILED
    val ext = MaterialTheme.extended
    val title = FlyerTitle.decode(flyer.sourceUrl)
    AppCard(onClick = if (failed) null else onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarketAvatar(flyer.supermarketName, colorHex)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(flyer.supermarketName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(title, Fmt.dateTime(flyer.fetchedAt)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!failed) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Abrir ofertas", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        if (failed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ext.negative, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    "Falhou: " + flyer.errorMessage.ifBlank { "erro desconhecido" },
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.negative
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                Column {
                    Text("Ofertas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${flyer.offerCount}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Pendentes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${flyer.pendingCount}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Páginas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${flyer.pageCount}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
            if (flyer.pendingCount == 0 && flyer.offerCount > 0) {
                Spacer(Modifier.height(Spacing.sm))
                Tag("Revisado", container = ext.positiveContainer, content = ext.onPositiveContainer)
            }
        }
    }
}
