package com.smartmarket.ui.screens.pantry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.smartmarket.data.dao.PantryItemWithProduct
import com.smartmarket.data.entity.Product
import com.smartmarket.domain.matching.FuzzyMatcher
import com.smartmarket.domain.pantry.PantryPlanner
import com.smartmarket.ui.appViewModel
import com.smartmarket.ui.components.AppCard
import com.smartmarket.ui.components.AppTopBar
import com.smartmarket.ui.components.CategoryAvatar
import com.smartmarket.ui.components.EmptyState
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.IconBadge
import com.smartmarket.ui.components.LoadingBox
import com.smartmarket.ui.components.QuantityStepper
import com.smartmarket.ui.components.ScreenScaffold
import com.smartmarket.ui.components.SearchField
import com.smartmarket.ui.components.Tag
import com.smartmarket.ui.navigation.Routes
import com.smartmarket.ui.screens.product.DateField
import com.smartmarket.ui.screens.product.DecimalField
import com.smartmarket.ui.screens.product.FormSheet
import com.smartmarket.ui.theme.Spacing
import com.smartmarket.ui.theme.extended
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

@Composable
fun PantryScreen(navController: NavController) {
    val vm = appViewModel { c -> PantryViewModel(c.db) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is PantryEvent.OpenList -> navController.navigate(Routes.listDetail(e.listId))
                is PantryEvent.Message -> {
                    val result = snackbar.showSnackbar(
                        message = e.text,
                        actionLabel = if (e.undo != null) "Desfazer" else null,
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed && e.undo != null) vm.restore(e.undo)
                }
            }
        }
    }

    ScreenScaffold(
        topBar = {
            AppTopBar(
                title = "Despensa",
                subtitle = when {
                    state.total == 0 -> null
                    state.lowCount == 0 -> "${state.total} itens, tudo em dia"
                    else -> "${state.total} itens · ${state.lowCount} para repor"
                },
                onBack = { navController.popBackStack() }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Adicionar item") }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.total == 0 -> EmptyState(
                icon = Icons.Outlined.Inventory2,
                title = "Despensa vazia",
                message = "Adicione o que você tem em casa e defina um mínimo. O app avisa quando estiver acabando e monta a lista de reposição.",
                modifier = Modifier.padding(padding),
                actionLabel = "Adicionar item",
                onAction = { showAdd = true }
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                if (state.lowCount > 0) {
                    item(key = "restock") {
                        RestockCard(state.lowCount, onGenerate = { vm.generateRestockList() })
                    }
                }
                item(key = "hint") {
                    Text(
                        "Deslize para a direita para zerar e para a esquerda para remover.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.xs)
                    )
                }
                state.sections.forEach { section ->
                    item(key = "h-${section.title}-${section.low}") {
                        SectionTitle(section)
                    }
                    items(section.items, key = { it.productId }) { item ->
                        SwipeablePantryRow(
                            item = item,
                            low = section.low,
                            onQuantity = { vm.setQuantity(item, it) },
                            onZero = { vm.zero(item) },
                            onRemove = { vm.remove(item) },
                            onEdit = { editingId = item.productId }
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddPantryItemSheet(
            products = state.products,
            inPantry = state.inPantry,
            onDismiss = { showAdd = false },
            onPick = { vm.add(it) }
        )
    }

    val editing = editingId?.let { id -> state.sections.flatMap { it.items }.firstOrNull { it.productId == id } }
    if (editing != null) {
        EditPantryItemSheet(
            item = editing,
            onDismiss = { editingId = null },
            onSave = { min, expires -> vm.saveDetails(editing, min, expires) },
            onOpenProduct = {
                editingId = null
                navController.navigate(Routes.productDetail(editing.productId))
            }
        )
    }
}

// ---------------------------------------------------------------- Cabeçalhos

@Composable
private fun RestockCard(lowCount: Int, onGenerate: () -> Unit) {
    val ext = MaterialTheme.extended
    AppCard(Modifier.fillMaxWidth(), containerColor = ext.warningContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.WarningAmber, container = ext.warning, tint = MaterialTheme.colorScheme.surface)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    if (lowCount == 1) "1 item abaixo do mínimo" else "$lowCount itens abaixo do mínimo",
                    style = MaterialTheme.typography.titleSmall,
                    color = ext.onWarningContainer
                )
                Text(
                    "Monte a lista com as quantidades certas para o mês",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.onWarningContainer.copy(alpha = 0.85f)
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Filled.AddShoppingCart, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text("Gerar lista de reposição")
        }
    }
}

@Composable
private fun SectionTitle(section: PantrySection) {
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.md, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            section.title,
            style = MaterialTheme.typography.titleSmall,
            color = if (section.low) MaterialTheme.extended.warning else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            "${section.items.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------- Linha com swipe

@Composable
private fun SwipeablePantryRow(
    item: PantryItemWithProduct,
    low: Boolean,
    onQuantity: (Double) -> Unit,
    onZero: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit
) {
    // O estado do swipe é lembrado; as callbacks precisam ver o item mais recente
    val currentOnZero by rememberUpdatedState(onZero)
    val currentOnRemove by rememberUpdatedState(onRemove)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { currentOnZero(); false } // volta ao lugar
                SwipeToDismissBoxValue.EndToStart -> { currentOnRemove(); true }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val ext = MaterialTheme.extended
            val (color, onColor, icon, label, alignment) = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> SwipeBg(ext.warningContainer, ext.onWarningContainer, Icons.Filled.RemoveShoppingCart, "Zerar", Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart -> SwipeBg(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Icons.Filled.Delete, "Remover", Alignment.CenterEnd)
                SwipeToDismissBoxValue.Settled -> SwipeBg(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.onSurfaceVariant, null, "", Alignment.Center)
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color, MaterialTheme.shapes.medium)
                    .padding(horizontal = Spacing.xl),
                contentAlignment = alignment
            ) {
                if (icon != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, tint = onColor)
                        Spacer(Modifier.width(Spacing.sm))
                        Text(label, color = onColor, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    ) {
        PantryRow(item, low, onQuantity, onEdit)
    }
}

private data class SwipeBg(
    val color: androidx.compose.ui.graphics.Color,
    val onColor: androidx.compose.ui.graphics.Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector?,
    val label: String,
    val alignment: Alignment
)

@Composable
private fun PantryRow(item: PantryItemWithProduct, low: Boolean, onQuantity: (Double) -> Unit, onEdit: () -> Unit) {
    val ext = MaterialTheme.extended
    val step = if (PantryPlanner.isFractionalUnit(item.unit)) 0.5 else 1.0
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (low) ext.warning.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .clickable(role = Role.Button, onClickLabel = "Editar mínimo e validade", onClick = onEdit)
                .heightIn(min = 64.dp)
                .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryAvatar(item.category, size = 36.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(item.productName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "mín. ${Fmt.quantity(item.minQuantity)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (low) ext.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (low) FontWeight.SemiBold else FontWeight.Normal
                    )
                    ExpiryTag(item.expiresAt)
                }
            }
            QuantityStepper(value = item.quantity, onValueChange = onQuantity, step = step, unit = item.unit)
        }
    }
}

@Composable
private fun ExpiryTag(expiresAt: Long?) {
    if (expiresAt == null) return
    val ext = MaterialTheme.extended
    val days = ceil((expiresAt - System.currentTimeMillis()).toDouble() / TimeUnit.DAYS.toMillis(1)).toInt()
    Spacer(Modifier.width(Spacing.sm))
    when {
        days < 0 -> Tag("Vencido", container = ext.negativeContainer, content = ext.onNegativeContainer)
        days == 0 -> Tag("Vence hoje", container = ext.warningContainer, content = ext.onWarningContainer)
        days <= 7 -> Tag("Vence em $days d", container = ext.warningContainer, content = ext.onWarningContainer)
        else -> Text(
            "· val. ${Fmt.dateShort(expiresAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------- Sheets

@Composable
private fun AddPantryItemSheet(
    products: List<Product>,
    inPantry: Set<Long>,
    onDismiss: () -> Unit,
    onPick: (Product) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = FuzzyMatcher.normalize(query)
    val results = products
        .filter { it.id !in inPantry }
        .filter { q.isBlank() || FuzzyMatcher.normalize(it.name).contains(q) || FuzzyMatcher.normalize(it.category).contains(q) }
        .take(30)

    FormSheet("Adicionar à despensa", onDismiss) {
        SearchField(value = query, onValueChange = { query = it }, placeholder = "Buscar produto", autoFocus = true)
        if (results.isEmpty()) {
            Text(
                if (products.isEmpty()) "Cadastre produtos primeiro em Produtos." else "Nenhum produto encontrado.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.md)
            )
        }
        Column {
            results.forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onPick(p); onDismiss() }
                        .heightIn(min = 56.dp)
                        .padding(vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryAvatar(p.category, size = 36.dp)
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(p.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.Add, contentDescription = "Adicionar ${p.name}", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun EditPantryItemSheet(
    item: PantryItemWithProduct,
    onDismiss: () -> Unit,
    onSave: (min: Double, expiresAt: Long?) -> Unit,
    onOpenProduct: () -> Unit
) {
    var minText by rememberSaveable(item.productId) { mutableStateOf(Fmt.quantity(item.minQuantity)) }
    var expires by rememberSaveable(item.productId) { mutableStateOf(item.expiresAt) }
    val min = Fmt.parseMoney(minText)

    FormSheet(item.productName, onDismiss) {
        Text(
            "Em casa: ${Fmt.quantity(item.quantity)} ${item.unit}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DecimalField(
            value = minText,
            onValueChange = { minText = it },
            label = "Quantidade mínima (${item.unit})",
            supporting = "Abaixo disso o item entra na lista de reposição",
            isError = minText.isNotBlank() && min == null
        )
        DateField(label = "Validade", millis = expires, onChange = { expires = it }, endOfDay = true, allowClear = true)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(onClick = onOpenProduct, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text("Ver produto")
            }
            Button(
                onClick = { onSave(min ?: item.minQuantity, expires); onDismiss() },
                enabled = min != null || minText.isBlank(),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) { Text("Salvar") }
        }
    }
}
