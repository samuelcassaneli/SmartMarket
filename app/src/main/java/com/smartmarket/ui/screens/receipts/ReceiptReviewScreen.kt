package com.smartmarket.ui.screens.receipts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.smartmarket.data.repo.PurchaseRepository
import com.smartmarket.domain.nfce.NfceErrorKind
import com.smartmarket.domain.nfce.NfceReceipt
import com.smartmarket.domain.nfce.NfceService
import com.smartmarket.domain.nfce.NfceText
import com.smartmarket.ui.appViewModel
import com.smartmarket.ui.components.AppCard
import com.smartmarket.ui.components.AppTopBar
import com.smartmarket.ui.components.EmptyState
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.IconBadge
import com.smartmarket.ui.components.LoadingBox
import com.smartmarket.ui.components.MarketAvatar
import com.smartmarket.ui.components.MoneyText
import com.smartmarket.ui.components.ScreenScaffold
import com.smartmarket.ui.components.SectionHeader
import com.smartmarket.ui.components.Tag
import com.smartmarket.ui.navigation.Routes
import com.smartmarket.ui.theme.Spacing
import com.smartmarket.ui.theme.extended

@Composable
fun ReceiptReviewScreen(payload: String, navController: NavController) {
    val vm = appViewModel(key = "receipt-review-$payload") { c ->
        ReceiptReviewViewModel(
            initialPayload = payload,
            service = NfceService(c.httpClient),
            purchases = PurchaseRepository(c.db, c.priceRepository, c.productMatcher),
            matcher = c.productMatcher,
            productDao = c.db.productDao(),
            supermarketDao = c.db.supermarketDao()
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val webView by vm.webView.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val savedId by vm.savedId.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val markets by vm.markets.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var showKeyDialog by rememberSaveable { mutableStateOf(false) }
    var sheetItemIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showMarketSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(savedId) {
        val id = savedId ?: return@LaunchedEffect
        vm.consumeSaved()
        navController.navigate(Routes.receiptDetail(id)) {
            popUpTo(Routes.RECEIPT_REVIEW) { inclusive = true }
        }
    }
    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        snackbar.showSnackbar(m)
        vm.consumeMessage()
    }

    webView?.let { req ->
        NfceWebViewExtractor(
            url = req.url,
            expectedKey = req.key,
            clipboardKey = req.clipboardKey,
            onExtracted = { vm.onExtracted(it) },
            onDismiss = { vm.closeWebView() }
        )
    }

    if (showKeyDialog) {
        ManualKeyDialog(
            onConfirm = { digits ->
                showKeyDialog = false
                vm.load(digits)
            },
            onDismiss = { showKeyDialog = false }
        )
    }

    val ready = state as? ReviewState.Ready

    ScreenScaffold(
        topBar = {
            AppTopBar(
                title = "Conferir nota",
                subtitle = ready?.let { NfceText.cleanStoreName(it.receipt.storeName) },
                onBack = { navController.popBackStack() }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (ready != null) {
                SaveBar(ready = ready, saving = saving, onSave = vm::save)
            }
        }
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when (val s = state) {
            ReviewState.Loading -> LoadingBox(modifier, "Consultando a SEFAZ…")
            is ReviewState.Failed -> FailedContent(
                state = s,
                modifier = modifier,
                onRetry = vm::retry,
                onOpenWeb = vm::openInWebView,
                onTypeKey = { showKeyDialog = true }
            )
            is ReviewState.Duplicate -> EmptyState(
                icon = Icons.Default.CheckCircle,
                title = "Nota já importada",
                message = "Esta nota já foi importada em ${Fmt.dateShort(s.importedAt)}.",
                modifier = modifier,
                actionLabel = "Abrir nota",
                onAction = {
                    navController.navigate(Routes.receiptDetail(s.purchaseId)) {
                        popUpTo(Routes.RECEIPT_REVIEW) { inclusive = true }
                    }
                }
            )
            is ReviewState.Ready -> ReviewContent(
                state = s,
                modifier = modifier,
                onItemClick = { sheetItemIndex = it },
                onMarketClick = { showMarketSheet = true },
                onPantryChange = vm::setAddToPantry,
                onAcceptSuggestions = vm::acceptAllSuggestions,
                onCreateAllNew = vm::createAllNew
            )
        }
    }

    val sheetItem = ready?.items?.firstOrNull { it.index == sheetItemIndex }
    if (sheetItem != null) {
        ItemLinkSheet(
            item = sheetItem,
            products = products,
            draft = vm.draftFor(sheetItem.item),
            onDismiss = { sheetItemIndex = null },
            onPick = { vm.linkItem(sheetItem.index, it); sheetItemIndex = null },
            onCreate = { name, category, unit ->
                vm.createProductFor(sheetItem.index, name, category, unit); sheetItemIndex = null
            },
            onIgnore = { vm.ignoreItem(sheetItem.index); sheetItemIndex = null },
            onUnlink = { vm.unlinkItem(sheetItem.index); sheetItemIndex = null }
        )
    }

    if (showMarketSheet && ready != null) {
        MarketPickerSheet(
            receipt = ready.receipt,
            current = ready.market,
            markets = markets,
            suggested = vm.suggestedMarkets(ready.receipt, markets),
            onDismiss = { showMarketSheet = false },
            onChoose = { vm.setMarket(it); showMarketSheet = false }
        )
    }
}

// ---------------------------------------------------------------- erro

@Composable
private fun FailedContent(
    state: ReviewState.Failed,
    modifier: Modifier,
    onRetry: () -> Unit,
    onOpenWeb: () -> Unit,
    onTypeKey: () -> Unit
) {
    val title = when (state.kind) {
        NfceErrorKind.CAPTCHA -> "Verificação da SEFAZ"
        NfceErrorKind.NOT_FOUND -> "Nota não encontrada"
        NfceErrorKind.NETWORK -> "Sem resposta da SEFAZ"
        NfceErrorKind.NOT_NFCE, NfceErrorKind.INVALID_KEY, NfceErrorKind.INVALID_PAYLOAD -> "Código não reconhecido"
        NfceErrorKind.JS_REQUIRED, NfceErrorKind.UNKNOWN_LAYOUT -> "Abra a nota no navegador"
    }
    Column(
        modifier = modifier.padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconBadge(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            size = 64.dp,
            container = MaterialTheme.colorScheme.errorContainer,
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(Modifier.height(Spacing.lg))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Spacing.xl))
        if (state.payload != null) {
            Button(onClick = onOpenWeb, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Abrir no navegador interno")
            }
            Spacer(Modifier.height(Spacing.sm))
            FilledTonalButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Tentar de novo")
            }
            Spacer(Modifier.height(Spacing.sm))
        }
        OutlinedButton(onClick = onTypeKey, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Keyboard, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text("Digitar chave")
        }
    }
}

// ---------------------------------------------------------------- revisão

@Composable
private fun ReviewContent(
    state: ReviewState.Ready,
    modifier: Modifier,
    onItemClick: (Int) -> Unit,
    onMarketClick: () -> Unit,
    onPantryChange: (Boolean) -> Unit,
    onAcceptSuggestions: () -> Unit,
    onCreateAllNew: () -> Unit
) {
    val r = state.receipt
    val linked = state.items.count { it.status == LinkStatus.LINKED }
    val suggested = state.items.count { it.status == LinkStatus.SUGGESTED }
    val news = state.items.count { it.status == LinkStatus.NEW }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item { ReceiptHeaderCard(r) }
        item { MarketCard(state.market, onMarketClick) }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Default.Inventory2, size = 36.dp)
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text("Adicionar à despensa", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Soma as quantidades dos itens vinculados ao estoque de casa",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = state.addToPantry, onCheckedChange = onPantryChange)
                }
            }
        }
        item {
            SectionHeader(title = "Itens (${state.items.size})")
            Text(
                "$linked vinculados · $suggested com sugestão · $news novos",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (suggested > 0 || news > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.xs)) {
                    if (suggested > 0) TextButton(onClick = onAcceptSuggestions) { Text("Aceitar sugestões") }
                    if (news > 0) TextButton(onClick = onCreateAllNew) { Text("Criar produtos novos") }
                }
            }
        }
        items(state.items, key = { it.index }) { item ->
            ReviewItemRow(item, onClick = { onItemClick(item.index) })
        }
    }
}

@Composable
private fun ReceiptHeaderCard(r: NfceReceipt) {
    AppCard {
        Text(
            r.storeName.ifBlank { "Loja" },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (r.cnpj.isNotBlank()) {
            Text(
                "CNPJ ${NfceText.formatCnpj(r.cnpj)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            Fmt.dateTime(r.issuedAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.md))
        Text("Total pago", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(r.total, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Spacing.sm))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(Spacing.sm))
        InfoLine("Itens", "${r.itemCount}")
        if (r.discount > 0.0) {
            InfoLine("Desconto", "- ${Fmt.money(r.discount)}", valueColor = MaterialTheme.extended.positive)
        }
        if (r.paymentMethod.isNotBlank()) InfoLine("Pagamento", r.paymentMethod)
    }
}

@Composable
internal fun InfoLine(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, textAlign = TextAlign.End, maxLines = 2)
    }
}

@Composable
private fun MarketCard(choice: MarketChoice, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (choice) {
                is MarketChoice.Existing -> MarketAvatar(choice.market.name, choice.market.colorHex)
                is MarketChoice.New -> IconBadge(Icons.Default.AddBusiness)
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text("Mercado", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    when (choice) {
                        is MarketChoice.Existing -> choice.market.name
                        is MarketChoice.New -> "Novo: ${choice.name}"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    when (choice) {
                        is MarketChoice.Existing -> if (choice.byCnpj) "Reconhecido pelo CNPJ" else "Sugerido pelo nome · toque para trocar"
                        is MarketChoice.New -> "Será cadastrado ao salvar · toque para trocar"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.Storefront, contentDescription = "Trocar mercado", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReviewItemRow(item: ReviewItem, onClick: () -> Unit) {
    val ext = MaterialTheme.extended
    val i = item.item
    AppCard(onClick = onClick, contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(i.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Fmt.quantity(i.quantity)} ${i.unit.lowercase()} × ${Fmt.money(i.unitPrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            MoneyText(i.totalPrice, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (item.status) {
                LinkStatus.LINKED -> Tag(
                    if (item.newProduct != null) "Novo produto" else if (item.viaAlias) "Vinculado" else "Vinculado (auto)",
                    container = ext.positiveContainer,
                    content = ext.onPositiveContainer
                )
                LinkStatus.SUGGESTED -> Tag("Sugestão", container = ext.warningContainer, content = ext.onWarningContainer)
                LinkStatus.NEW -> Tag("Novo")
                LinkStatus.IGNORED -> Tag(
                    "Ignorado",
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            val detail = when (item.status) {
                LinkStatus.LINKED -> item.linkedName
                LinkStatus.SUGGESTED -> item.suggestions.firstOrNull()?.item?.name?.let { "$it?" }
                LinkStatus.NEW -> "Toque para vincular ou criar"
                LinkStatus.IGNORED -> null
            }
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SaveBar(ready: ReviewState.Ready, saving: Boolean, onSave: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("${ready.items.size} itens", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyText(ready.receipt.total, style = MaterialTheme.typography.titleLarge)
            }
            Button(onClick = onSave, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp)) {
                if (saving) {
                    CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(Spacing.sm))
                }
                Text("Salvar compra")
            }
        }
    }
}
