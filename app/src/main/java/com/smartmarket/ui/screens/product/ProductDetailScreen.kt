package com.smartmarket.ui.screens.product

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.smartmarket.data.entity.PriceAlert
import com.smartmarket.data.entity.PriceSource
import com.smartmarket.domain.pantry.PantryPlanner
import com.smartmarket.ui.appViewModel
import com.smartmarket.ui.charts.LineChart
import com.smartmarket.ui.charts.LinePoint
import com.smartmarket.ui.charts.LineSeries
import com.smartmarket.ui.components.AppCard
import com.smartmarket.ui.components.AppTopBar
import com.smartmarket.ui.components.BestPriceTag
import com.smartmarket.ui.components.CategoryAvatar
import com.smartmarket.ui.components.ConfirmDialog
import com.smartmarket.ui.components.EmptyState
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.LoadingBox
import com.smartmarket.ui.components.MarketAvatar
import com.smartmarket.ui.components.MoneyText
import com.smartmarket.ui.components.QuantityStepper
import com.smartmarket.ui.components.ScreenScaffold
import com.smartmarket.ui.components.StatCard
import com.smartmarket.ui.components.Tag
import com.smartmarket.ui.components.colorFromHex
import com.smartmarket.ui.navigation.Routes
import com.smartmarket.ui.theme.Spacing
import com.smartmarket.ui.theme.extended

@Composable
fun ProductDetailScreen(productId: Long, navController: NavController) {
    val vm = appViewModel(key = "produto-$productId") { c ->
        ProductDetailViewModel(productId, c.db, c.priceRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var showRecord by rememberSaveable { mutableStateOf(false) }
    var showAddToList by rememberSaveable { mutableStateOf(false) }
    var showEdit by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(deleted) { if (deleted) navController.popBackStack() }

    val product = state.product
    ScreenScaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AppTopBar(
                title = product?.name ?: "Produto",
                onBack = { navController.popBackStack() },
                actions = {
                    if (product != null) {
                        IconButton(onClick = vm::toggleFavorite) {
                            Icon(
                                if (product.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (product.isFavorite) "Remover dos favoritos" else "Marcar como favorito",
                                tint = if (product.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showEdit = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar produto")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Excluir produto") },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                    onClick = { menuOpen = false; confirmDelete = true }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            product == null -> EmptyState(
                icon = Icons.Outlined.Inventory2,
                title = "Produto não encontrado",
                message = "Ele pode ter sido excluído.",
                modifier = Modifier.padding(padding),
                actionLabel = "Voltar",
                onAction = { navController.popBackStack() }
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                item(key = "header") {
                    HeaderCard(state)
                }
                item(key = "actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Button(
                                onClick = { showRecord = true },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(Spacing.xs))
                                Text("Registrar preço")
                            }
                            FilledTonalButton(
                                onClick = { showAddToList = true },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Filled.AddShoppingCart, contentDescription = null)
                                Spacer(Modifier.width(Spacing.xs))
                                Text("À lista")
                            }
                        }
                        OutlinedButton(
                            onClick = { navController.navigate(Routes.search(product.name)) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null)
                            Spacer(Modifier.width(Spacing.xs))
                            Text("Buscar nos mercados")
                        }
                    }
                }
                item(key = "stats") { StatsGrid(state) }
                item(key = "history") { HistoryCard(state) }
                item(key = "month") { MonthPricesCard(state) }
                item(key = "alert") {
                    AlertCard(state.alert, onSave = vm::saveAlert)
                }
                item(key = "pantry") {
                    PantryCard(state, onChange = vm::setPantry, onRemove = vm::removeFromPantry)
                }
                if (state.purchases.isNotEmpty()) {
                    item(key = "purchases") {
                        PurchasesCard(state) { navController.navigate(Routes.receiptDetail(it)) }
                    }
                }
            }
        }
    }

    if (showRecord && product != null) {
        RecordPriceSheet(
            markets = state.markets,
            initialMarketId = state.monthPrices.firstOrNull()?.supermarketId,
            onDismiss = { showRecord = false },
            onSave = vm::recordPrice
        )
    }
    if (showAddToList && product != null) {
        AddToListSheet(
            lists = state.lists,
            onDismiss = { showAddToList = false },
            onPick = { vm.addToList(it.id, it.name) },
            onCreate = vm::createListAndAdd
        )
    }
    if (showEdit && product != null) {
        EditProductSheet(
            product = product,
            categories = state.categories,
            onDismiss = { showEdit = false },
            onSave = vm::updateProduct
        )
    }
    if (confirmDelete && product != null) {
        ConfirmDialog(
            title = "Excluir produto?",
            text = "\"${product.name}\" sai das listas, da despensa e do histórico de preços. Não dá para desfazer.",
            confirmLabel = "Excluir",
            destructive = true,
            onConfirm = vm::delete,
            onDismiss = { confirmDelete = false }
        )
    }
}

// ---------------------------------------------------------------- Cabeçalho e estatísticas

@Composable
private fun HeaderCard(state: ProductDetailState) {
    val p = state.product ?: return
    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryAvatar(p.category, size = 56.dp)
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${p.category} · ${p.unit}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (p.barcode.isNotBlank() || p.isFavorite || p.defaultMonthlyQuantity > 0) {
            Spacer(Modifier.height(Spacing.md))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                if (p.isFavorite) Tag("Favorito", icon = Icons.Filled.Star)
                if (p.barcode.isNotBlank()) Tag("Código ${p.barcode}")
                if (p.defaultMonthlyQuantity > 0) Tag("${Fmt.quantity(p.defaultMonthlyQuantity)} ${p.unit}/mês")
            }
        }
    }
}

@Composable
private fun StatsGrid(state: ProductDetailState) {
    val s = state.stats
    val count = s?.observationCount ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatCard(
                label = "Menor preço",
                value = s?.minPrice?.let(Fmt::money) ?: "—",
                supporting = "já pago ou visto",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Preço médio",
                value = s?.avgPrice?.let(Fmt::money) ?: "—",
                supporting = s?.maxPrice?.let { "máximo ${Fmt.money(it)}" },
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatCard(
                label = "Último preço",
                value = s?.lastPrice?.let(Fmt::money) ?: "—",
                supporting = s?.lastObservedAt?.let { "em ${Fmt.date(it)}" },
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Registros",
                value = count.toString(),
                supporting = if (count == 1) "preço anotado" else "preços anotados",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ---------------------------------------------------------------- Histórico

@Composable
private fun HistoryCard(state: ProductDetailState) {
    AppCard(Modifier.fillMaxWidth()) {
        Text("Histórico de preço", style = MaterialTheme.typography.titleMedium)
        Text(
            "Toque num ponto para ver o valor",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.md))
        if (state.observations.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.ShowChart,
                title = "Ainda sem histórico",
                message = "Registre um preço ou escaneie uma nota para começar o gráfico."
            )
        } else {
            val series = state.observations
                .groupBy { it.supermarketId }
                .values
                .map { obs ->
                    val first = obs.first()
                    LineSeries(
                        name = first.supermarketName,
                        color = colorFromHex(first.colorHex),
                        points = obs.map { LinePoint(it.observedAt, it.price) }
                    )
                }
            LineChart(series = series)
        }
    }
}

@Composable
private fun MonthPricesCard(state: ProductDetailState) {
    AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.lg)) {
        Text(
            "Preços deste mês",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Spacing.lg)
        )
        Spacer(Modifier.height(Spacing.sm))
        if (state.monthPrices.isEmpty()) {
            Text(
                "Nenhum preço registrado neste mês.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
        } else {
            state.monthPrices.forEach { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MarketAvatar(row.name, row.colorHex, size = 36.dp)
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(row.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${PriceSource.label(row.source)} · ${Fmt.dateShort(row.updatedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        MoneyText(
                            row.price,
                            color = if (row.isBest) MaterialTheme.extended.positive else MaterialTheme.colorScheme.onSurface
                        )
                        if (row.isBest) BestPriceTag()
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Alerta

@Composable
private fun AlertCard(alert: PriceAlert?, onSave: (active: Boolean, target: Double?, anyDrop: Boolean) -> Unit) {
    val active = alert?.active == true
    val anyDrop = alert?.notifyOnAnyDrop ?: true
    var targetText by rememberSaveable(alert?.id, alert?.targetPrice) {
        mutableStateOf(alert?.targetPrice?.let { Fmt.decimal(it) } ?: "")
    }
    val parsed = Fmt.parseMoney(targetText)
    val targetChanged = parsed != alert?.targetPrice && !(targetText.isBlank() && alert?.targetPrice == null)

    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .padding(end = Spacing.md)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small)
                    .padding(Spacing.sm)
            ) {
                Icon(
                    Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(Modifier.weight(1f)) {
                Text("Alerta de preço", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (active) "Você será avisado quando ficar mais barato" else "Desligado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = active,
                onCheckedChange = { on -> onSave(on, parsed, anyDrop) }
            )
        }
        if (active) {
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DecimalField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = "Preço-alvo (opcional)",
                    prefix = "R$ ",
                    modifier = Modifier.weight(1f)
                )
                if (targetChanged) {
                    Spacer(Modifier.width(Spacing.sm))
                    TextButton(onClick = { onSave(true, parsed, anyDrop) }) { Text("Salvar") }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Switch) { onSave(true, alert?.targetPrice, !anyDrop) }
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Avisar em qualquer queda", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = anyDrop, onCheckedChange = { onSave(true, alert?.targetPrice, it) })
            }
        }
    }
}

// ---------------------------------------------------------------- Despensa

@Composable
private fun PantryCard(state: ProductDetailState, onChange: (Double, Double) -> Unit, onRemove: () -> Unit) {
    val p = state.product ?: return
    val pantry = state.pantry
    val qty = pantry?.quantity ?: 0.0
    val min = pantry?.minQuantity ?: 0.0
    val step = if (PantryPlanner.isFractionalUnit(p.unit)) 0.5 else 1.0
    val low = pantry != null && PantryPlanner.isLow(qty, min)

    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Despensa", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (low) {
                Tag("Repor", container = MaterialTheme.extended.warningContainer, content = MaterialTheme.extended.onWarningContainer)
            }
            if (pantry != null) {
                TextButton(onClick = onRemove) { Text("Remover") }
            }
        }
        if (pantry == null) {
            Text(
                "Este produto não está na despensa.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.sm))
            FilledTonalButton(onClick = { onChange(1.0, step) }) { Text("Adicionar à despensa") }
        } else {
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Em casa", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                QuantityStepper(value = qty, onValueChange = { onChange(it, min) }, step = step, unit = p.unit)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mínimo", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                QuantityStepper(value = min, onValueChange = { onChange(qty, it) }, step = step, unit = p.unit)
            }
        }
    }
}

// ---------------------------------------------------------------- Compras

@Composable
private fun PurchasesCard(state: ProductDetailState, onOpen: (Long) -> Unit) {
    AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.lg)) {
        Text(
            "Últimas compras",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Spacing.lg)
        )
        Spacer(Modifier.height(Spacing.xs))
        state.purchases.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onOpen(row.purchaseId) }
                    .heightIn(min = 56.dp)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MarketAvatar(row.marketName, row.colorHex, size = 36.dp)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(row.marketName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${Fmt.date(row.issuedAt)} · ${Fmt.quantity(row.quantity)} ${row.unit.lowercase()} × ${Fmt.money(row.unitPrice)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(Fmt.money(row.totalPrice), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
