package com.smartmarket.ui.screens.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.smartmarket.data.dao.PantryItemWithProduct
import com.smartmarket.data.dao.PurchaseWithMarket
import com.smartmarket.ui.appViewModel
import com.smartmarket.ui.charts.BarChart
import com.smartmarket.ui.charts.BarEntry
import com.smartmarket.ui.charts.DonutWithLegend
import com.smartmarket.ui.charts.ShareBars
import com.smartmarket.ui.charts.ShareSlice
import com.smartmarket.ui.charts.topSlices
import com.smartmarket.ui.components.AppCard
import com.smartmarket.ui.components.AppTopBar
import com.smartmarket.ui.components.CategoryAvatar
import com.smartmarket.ui.components.EmptyState
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.IconBadge
import com.smartmarket.ui.components.LoadingBox
import com.smartmarket.ui.components.MarketAvatar
import com.smartmarket.ui.components.MoneyText
import com.smartmarket.ui.components.PriceChangeChip
import com.smartmarket.ui.components.ScreenScaffold
import com.smartmarket.ui.components.SectionHeader
import com.smartmarket.ui.components.Tag
import com.smartmarket.ui.components.colorFromHex
import com.smartmarket.ui.navigation.Routes
import com.smartmarket.ui.theme.Spacing
import com.smartmarket.ui.theme.extended
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(navController: NavController) {
    val vm = appViewModel { c ->
        HomeViewModel(c.db.purchaseDao(), c.db.pantryDao(), c.db.flyerDao(), c.db.shoppingListDao(), c.settings)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val greeting = remember { greeting() }
    val today = remember { todayLabel() }

    ScreenScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = greeting,
                subtitle = today,
                large = true,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Configurações")
                    }
                }
            )
        }
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@ScreenScaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item(key = "hero") {
                HeroSpendCard(
                    state = state,
                    onOpenReceipts = { navController.navigate(Routes.RECEIPTS) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }
            item(key = "actions") {
                QuickActions(
                    onScan = { navController.navigate(Routes.scanQr()) },
                    onNewList = { navController.navigate(Routes.LISTS) },
                    onSearch = { navController.navigate(Routes.search()) }
                )
            }
            if (state.pendingOffers > 0) {
                item(key = "offers") {
                    PendingOffersBanner(state.pendingOffers) { navController.navigate(Routes.FLYERS) }
                }
            }

            if (!state.hasAnyPurchase) {
                item(key = "empty") {
                    AppCard {
                        EmptyState(
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            title = "Seu painel começa pela primeira nota",
                            message = "Escaneie o QR Code de uma nota fiscal e o app monta seus gastos, preços e despensa sozinho.",
                            actionLabel = "Escanear primeira nota",
                            onAction = { navController.navigate(Routes.scanQr()) }
                        )
                    }
                }
            } else {
                item(key = "months") { MonthsCard(state) }
                if (state.byCategory.isNotEmpty()) {
                    item(key = "category") {
                        AppCard(Modifier.fillMaxWidth()) {
                            CardTitle("Por categoria", "Neste mês")
                            Spacer(Modifier.height(Spacing.md))
                            DonutWithLegend(
                                slices = topSlices(state.byCategory.map { it.label to it.total }),
                                centerLabel = "Total"
                            )
                        }
                    }
                }
                if (state.byMarket.isNotEmpty()) {
                    item(key = "market") {
                        AppCard(Modifier.fillMaxWidth()) {
                            CardTitle("Por mercado", "Neste mês")
                            Spacer(Modifier.height(Spacing.md))
                            val slices = state.byMarket.map { ShareSlice(it.label, it.total, colorFromHex(it.colorHex)) }
                            ShareBars(slices)
                        }
                    }
                }
                item(key = "recent-header") {
                    SectionHeader("Últimas compras", actionLabel = "Ver todas", onAction = { navController.navigate(Routes.RECEIPTS) })
                }
                item(key = "recent") {
                    AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                        state.recentPurchases.forEachIndexed { i, p ->
                            PurchaseRow(p) { navController.navigate(Routes.receiptDetail(p.id)) }
                            if (i < state.recentPurchases.lastIndex) RowDivider()
                        }
                    }
                }
            }

            if (state.lowStock.isNotEmpty()) {
                item(key = "pantry-header") {
                    SectionHeader("Acabando na despensa", actionLabel = "Ver despensa", onAction = { navController.navigate(Routes.PANTRY) })
                }
                item(key = "pantry") {
                    val shown = state.lowStock.take(4)
                    AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                        shown.forEachIndexed { i, item ->
                            LowStockRow(item) { navController.navigate(Routes.PANTRY) }
                            if (i < shown.lastIndex) RowDivider()
                        }
                        if (state.lowStock.size > shown.size) {
                            Text(
                                "e mais ${state.lowStock.size - shown.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Cartão herói

@Composable
private fun HeroSpendCard(state: HomeUiState, onOpenReceipts: () -> Unit, onOpenSettings: () -> Unit) {
    val ext = MaterialTheme.extended
    val scheme = MaterialTheme.colorScheme
    AppCard(Modifier.fillMaxWidth(), onClick = onOpenReceipts, contentPadding = PaddingValues(Spacing.xl)) {
        Text(
            "Gastos de ${Fmt.monthName(state.month.month).lowercase(Locale("pt", "BR"))}",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.xs))
        MoneyText(state.spentThisMonth, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val change = state.changeVsLastMonth
            if (change != null) {
                PriceChangeChip(change)
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    "vs. ${Fmt.monthName(state.month.previous().month).lowercase(Locale("pt", "BR"))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            } else {
                Text(
                    when (state.purchasesThisMonth) {
                        0 -> "Nenhuma compra registrada neste mês"
                        1 -> "1 compra neste mês"
                        else -> "${state.purchasesThisMonth} compras neste mês"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        if (state.budget > 0) {
            val ratio = state.budgetRatio
            val color = when {
                ratio > 1f -> ext.negative
                ratio > 0.8f -> ext.warning
                else -> scheme.primary
            }
            BudgetBar(ratio, color)
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (state.budgetFromLists) "Orçamento das listas: " else "Orçamento: ") + Fmt.money(state.budget),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                val diff = state.budget - state.spentThisMonth
                Text(
                    if (diff >= 0) "Restam ${Fmt.money(diff)}" else "${Fmt.money(-diff)} acima",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (diff >= 0) scheme.onSurface else ext.negative
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Sem orçamento definido",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onOpenSettings) { Text("Definir") }
            }
        }
    }
}

@Composable
private fun BudgetBar(ratio: Float, color: Color) {
    val animated by animateFloatAsState(
        targetValue = ratio.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "budget"
    )
    val percent = (ratio * 100).toInt()
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics { contentDescription = "$percent% do orçamento usado" }
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color)
        )
    }
}

// ---------------------------------------------------------------- Ações rápidas

@Composable
private fun QuickActions(onScan: () -> Unit, onNewList: () -> Unit, onSearch: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        QuickAction(Icons.Filled.QrCodeScanner, "Escanear nota", onScan, Modifier.weight(1f))
        QuickAction(Icons.Filled.AddShoppingCart, "Nova lista", onNewList, Modifier.weight(1f))
        QuickAction(Icons.Filled.Search, "Buscar preço", onSearch, Modifier.weight(1f))
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier.heightIn(min = 88.dp), onClick = onClick, contentPadding = PaddingValues(Spacing.md)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(icon, size = 36.dp)
            Spacer(Modifier.height(Spacing.sm))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun PendingOffersBanner(count: Int, onClick: () -> Unit) {
    AppCard(
        Modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                Icons.Filled.LocalOffer,
                container = MaterialTheme.colorScheme.primary,
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    if (count == 1) "1 oferta de encarte para revisar" else "$count ofertas de encarte para revisar",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Confirme os preços para comparar suas listas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// ---------------------------------------------------------------- Gráficos

@Composable
private fun MonthsCard(state: HomeUiState) {
    AppCard(Modifier.fillMaxWidth()) {
        val avg = state.lastSixMonths.filter { it.total > 0 }.map { it.total }.average().takeIf { !it.isNaN() } ?: 0.0
        CardTitle("Últimos 6 meses", "Média ${Fmt.money(avg)} por mês")
        Spacer(Modifier.height(Spacing.md))
        BarChart(
            entries = state.lastSixMonths.map {
                BarEntry(Fmt.monthShort(it.ym.month), it.total, highlighted = it.ym == state.month)
            },
            contentDescription = "Gastos dos últimos 6 meses: " + state.lastSixMonths.joinToString("; ") {
                "${Fmt.monthName(it.ym.month)}: ${Fmt.money(it.total)}"
            }
        )
    }
}

@Composable
private fun CardTitle(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (subtitle != null) {
        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- Linhas

@Composable
private fun PurchaseRow(p: PurchaseWithMarket, onClick: () -> Unit) {
    val name = p.supermarketName ?: p.storeName.ifBlank { "Compra" }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MarketAvatar(name, p.colorHex)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${Fmt.date(p.issuedAt)} · ${if (p.itemCount == 1) "1 item" else "${p.itemCount} itens"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        MoneyText(p.totalAmount, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun LowStockRow(item: PantryItemWithProduct, onClick: () -> Unit) {
    val ext = MaterialTheme.extended
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryAvatar(item.category, size = 36.dp)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(item.productName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "Em casa: ${Fmt.quantity(item.quantity)} ${item.unit} · mínimo ${Fmt.quantity(item.minQuantity)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Tag(
            if (item.quantity <= 0.0) "Acabou" else "Repor",
            container = ext.warningContainer,
            content = ext.onWarningContainer,
            icon = Icons.Filled.Inventory2
        )
    }
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .padding(start = 68.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

// ---------------------------------------------------------------- Textos

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Bom dia"
    in 12..17 -> "Boa tarde"
    else -> "Boa noite"
}

private fun todayLabel(): String {
    val ptBr = Locale("pt", "BR")
    val text = SimpleDateFormat("EEEE, d 'de' MMMM", ptBr).format(Date())
    return text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(ptBr) else it.toString() }
}
