@file:OptIn(ExperimentalMaterial3Api::class)

package com.smartmarket.ui.screens.lists

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.smartmarket.data.dao.ShoppingItemWithProduct
import com.smartmarket.ui.appViewModel
import com.smartmarket.ui.components.AppCard
import com.smartmarket.ui.components.AppTopBar
import com.smartmarket.ui.components.ConfirmDialog
import com.smartmarket.ui.components.EmptyState
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.LoadingBox
import com.smartmarket.ui.components.MarketAvatar
import com.smartmarket.ui.components.ScreenScaffold
import com.smartmarket.ui.navigation.Routes
import com.smartmarket.ui.theme.Spacing
import com.smartmarket.ui.theme.extended
import kotlinx.coroutines.launch

@Composable
fun ListDetailScreen(listId: Long, navController: NavController) {
    val vm = appViewModel(key = "list-$listId") { c ->
        ListDetailViewModel(
            listId,
            c.db.shoppingListDao(),
            c.db.productDao(),
            c.db.supermarketDao(),
            c.db.productPriceDao()
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableStateOf(0) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showBudget by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    val list = state.list
    val back: () -> Unit = { navController.popBackStack() }

    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(state.notFound) {
        if (state.notFound && !leaving) {
            leaving = true
            navController.popBackStack()
        }
    }

    val onRemove: (ShoppingItemWithProduct) -> Unit = { item ->
        vm.removeItem(item)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = "\"${item.productName}\" removido",
                actionLabel = "Desfazer",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) vm.restoreItem(item)
        }
    }

    ScreenScaffold(
        topBar = {
            AppTopBar(
                title = list?.name ?: "Lista",
                subtitle = list?.let { Fmt.monthYear(it.referenceMonth, it.referenceYear) },
                onBack = back,
                actions = {
                    IconToggleButton(checked = state.marketMode, onCheckedChange = { vm.setMarketMode(it); if (it) tab = 0 }) {
                        Icon(
                            Icons.Default.ShoppingCartCheckout,
                            contentDescription = if (state.marketMode) "Sair do modo mercado" else "Modo mercado",
                            tint = if (state.marketMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        val text = vm.shareText(state)
                        if (text.isNotEmpty()) {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(send, "Compartilhar lista"))
                        }
                    }) { Icon(Icons.Default.Share, contentDescription = "Compartilhar") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Mais opções") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Renomear") },
                                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                                onClick = { menu = false; showRename = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Editar orçamento") },
                                leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, null) },
                                onClick = { menu = false; showBudget = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Excluir lista", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; showDelete = true }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (list != null && tab == 0 && !state.marketMode) {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Adicionar") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.marketMode && list != null) MarketModeBar(state)
        }
    ) { padding ->
        if (state.loading || list == null) {
            LoadingBox(Modifier.padding(padding))
        } else LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            if (state.marketMode) {
                item(key = "market-picker") { MarketModePicker(state, vm::setShoppingMarket, vm::setHideChecked) }
            } else {
                item(key = "header") { ListHeader(state, onMonthChange = vm::setRefMonth, onEditBudget = { showBudget = true }) }
                item(key = "tabs") {
                    val labels = listOf("Itens (${state.items.size})", "Comparar")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                        labels.forEachIndexed { index, label ->
                            SegmentedButton(
                                selected = tab == index,
                                onClick = { tab = index },
                                shape = SegmentedButtonDefaults.itemShape(index, labels.size)
                            ) { Text(label) }
                        }
                    }
                }
            }

            if (tab == 0 || state.marketMode) {
                itemsTab(
                    state = state,
                    onToggle = vm::toggleCheck,
                    onQuantity = vm::setQuantity,
                    onRemove = onRemove,
                    onOpenProduct = { navController.navigate(Routes.productDetail(it)) },
                    onAdd = { showAdd = true }
                )
            } else {
                compareTab(
                    state = state,
                    onSearch = { navController.navigate(Routes.search(it)) },
                    onOpenMarkets = { navController.navigate(Routes.MARKETS) }
                )
            }
        }
    }

    if (showAdd && list != null) {
        AddItemSheet(
            products = products,
            categories = categories,
            inList = state.items,
            onDismiss = { showAdd = false },
            onAdd = vm::addProduct,
            onCreate = vm::createProductAndAdd
        )
    }
    if (showRename && list != null) {
        TextInputDialog(
            title = "Renomear lista",
            label = "Nome",
            initial = list.name,
            onDismiss = { showRename = false },
            onConfirm = { vm.rename(it); showRename = false }
        )
    }
    if (showBudget && list != null) {
        TextInputDialog(
            title = "Orçamento da lista",
            label = "Valor (deixe vazio para remover)",
            initial = if (list.budgetTarget > 0) Fmt.decimal(list.budgetTarget) else "",
            keyboardType = KeyboardType.Decimal,
            prefix = "R$ ",
            onDismiss = { showBudget = false },
            onConfirm = { vm.setBudget(Fmt.parseMoney(it) ?: 0.0); showBudget = false }
        )
    }
    if (showDelete && list != null) {
        ConfirmDialog(
            title = "Excluir lista?",
            text = "\"${list.name}\" e todos os itens serão apagados. Não dá para desfazer.",
            confirmLabel = "Excluir",
            destructive = true,
            // Ao apagar, a lista some do banco e o LaunchedEffect(notFound) volta para a tela anterior
            onConfirm = { vm.deleteList {} },
            onDismiss = { showDelete = false }
        )
    }
}

/** Cabeçalho: total estimado, orçamento, economia e mês de referência dos preços. */
@Composable
private fun ListHeader(state: ListDetailUi, onMonthChange: (com.smartmarket.ui.components.YearMonth) -> Unit, onEditBudget: () -> Unit) {
    val list = state.list ?: return
    val summary = state.summary
    val best = summary?.bestSupermarketCurrent
    val total = summary?.bestTotal ?: 0.0
    val ext = MaterialTheme.extended
    AppCard(Modifier.fillMaxWidth()) {
        Text("Total estimado", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (best != null) Fmt.money(total) else "—",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        val bestResult = summary?.marketResults?.firstOrNull { it.isBestPrice }
        Text(
            when {
                state.items.isEmpty() -> "Adicione itens para estimar o total"
                best == null -> "Sem preços em ${state.refMonth}"
                bestResult != null && !bestResult.isComplete ->
                    "no ${best.name} · ${bestResult.itemsAvailableCount} de ${bestResult.totalItemsCount} itens com preço"
                else -> "no ${best.name}, o mais barato"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (list.budgetTarget > 0) {
            Spacer(Modifier.height(Spacing.md))
            val color = budgetColor(total, list.budgetTarget)
            ThinProgress((total / list.budgetTarget).toFloat(), color)
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Orçamento ${Fmt.money(list.budgetTarget)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                val diff = list.budgetTarget - total
                Text(
                    if (diff >= 0) "Sobram ${Fmt.money(diff)}" else "Passou ${Fmt.money(-diff)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (diff >= 0) MaterialTheme.colorScheme.onSurfaceVariant else ext.negative
                )
            }
        } else if (state.items.isNotEmpty()) {
            androidx.compose.material3.TextButton(onClick = onEditBudget, contentPadding = PaddingValues(0.dp)) {
                Text("Definir orçamento")
            }
        }

        val savings = summary?.currentPotentialSavings ?: 0.0
        if (best != null && savings >= 0.01) {
            Spacer(Modifier.height(Spacing.md))
            Surface(color = ext.positiveContainer, contentColor = ext.onPositiveContainer, shape = MaterialTheme.shapes.small) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Savings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        "Economia de ${Fmt.money(savings)} comprando no ${best.name}",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Preços de",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            MonthSelector(state.refMonth, onMonthChange)
        }
    }
}

/** Topo do modo mercado: onde estou comprando e se escondo os itens marcados. */
@Composable
private fun MarketModePicker(state: ListDetailUi, onPick: (Long) -> Unit, onHideChecked: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Comprando em", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.markets.isEmpty()) {
            Text("Cadastre um mercado para ver os preços aqui.", style = MaterialTheme.typography.bodySmall)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(state.markets, key = { it.id }) { market ->
                FilterChip(
                    selected = market.id == state.shoppingMarketId,
                    onClick = { onPick(market.id) },
                    label = { Text(market.name) },
                    leadingIcon = { MarketAvatar(market.name, market.colorHex, size = 20.dp) }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Esconder itens no carrinho", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = state.hideChecked, onCheckedChange = onHideChecked)
        }
    }
}

/** Barra fixa do modo mercado: total do carrinho contra o orçamento. */
@Composable
private fun MarketModeBar(state: ListDetailUi) {
    val list = state.list ?: return
    val total = state.cartTotal
    val budget = list.budgetTarget
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.md)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "No carrinho" + (state.shoppingMarket?.let { " · ${it.name}" } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(Fmt.money(total), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (budget > 0) {
                        val left = budget - total
                        Text(
                            if (left >= 0) "Restam ${Fmt.money(left)}" else "Passou ${Fmt.money(-left)}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (left >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.extended.negative
                        )
                    }
                    Text(
                        "Falta pegar ${Fmt.money(state.remainingTotal)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (budget > 0) {
                Spacer(Modifier.height(Spacing.sm))
                ThinProgress((total / budget).toFloat(), budgetColor(total, budget))
            }
            if (state.cartMissingPrices > 0) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "${state.cartMissingPrices} ${if (state.cartMissingPrices == 1) "item marcado sem preço" else "itens marcados sem preço"} neste mercado",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.extended.warning
                )
            }
        }
    }
}

@Composable
internal fun EmptyItems(onAdd: () -> Unit) {
    EmptyState(
        icon = Icons.Default.Add,
        title = "Lista vazia",
        message = "Adicione os produtos que você precisa comprar.",
        actionLabel = "Adicionar itens",
        onAction = onAdd
    )
}
