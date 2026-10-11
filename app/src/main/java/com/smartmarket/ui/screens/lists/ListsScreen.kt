@file:OptIn(ExperimentalMaterial3Api::class)

package com.smartmarket.ui.screens.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.smartmarket.ui.LocalAppContainer
import com.smartmarket.ui.appViewModel
import com.smartmarket.ui.components.AppCard
import com.smartmarket.ui.components.AppTopBar
import com.smartmarket.ui.components.ConfirmDialog
import com.smartmarket.ui.components.EmptyState
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.LoadingBox
import com.smartmarket.ui.components.ScreenScaffold
import com.smartmarket.ui.components.SectionHeader
import com.smartmarket.ui.components.Tag
import com.smartmarket.ui.components.YearMonth
import com.smartmarket.ui.navigation.Routes
import com.smartmarket.ui.theme.Spacing
import com.smartmarket.ui.theme.extended

@Composable
fun ListsScreen(navController: NavController) {
    val vm = appViewModel { c ->
        ListsViewModel(c.db.shoppingListDao(), c.db.productDao(), c.db.supermarketDao(), c.db.productPriceDao())
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var showNewSheet by rememberSaveable { mutableStateOf(false) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ListCardUi?>(null) }
    var deleteTarget by remember { mutableStateOf<ListCardUi?>(null) }

    ScreenScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { AppTopBar(title = "Listas", large = true, scrollBehavior = scrollBehavior) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nova lista") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.all.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.ShoppingCart,
                    title = "Nenhuma lista ainda",
                    message = "Crie sua lista do mês e o app mostra onde ela sai mais barata.",
                    actionLabel = "Criar lista",
                    onAction = { showNewSheet = true }
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                if (state.active.isNotEmpty()) {
                    item(key = "h-active") { SectionHeader("Ativas") }
                    items(state.active, key = { it.summary.id }) { card ->
                        ListCard(
                            card = card,
                            onClick = { navController.navigate(Routes.listDetail(card.summary.id)) },
                            onRename = { renameTarget = card },
                            onDuplicate = { vm.duplicateToNextMonth(card.summary.id) { id -> navController.navigate(Routes.listDetail(id)) } },
                            onArchiveToggle = { vm.setArchived(card.summary.id, !card.summary.isArchived) },
                            onDelete = { deleteTarget = card }
                        )
                    }
                }
                if (state.archived.isNotEmpty()) {
                    item(key = "h-archived") {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionHeader("Arquivadas (${state.archived.size})", Modifier.weight(1f))
                            IconButton(onClick = { showArchived = !showArchived }) {
                                Icon(
                                    if (showArchived) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (showArchived) "Recolher arquivadas" else "Mostrar arquivadas"
                                )
                            }
                        }
                    }
                    if (showArchived) {
                        items(state.archived, key = { it.summary.id }) { card ->
                            ListCard(
                                card = card,
                                onClick = { navController.navigate(Routes.listDetail(card.summary.id)) },
                                onRename = { renameTarget = card },
                                onDuplicate = { vm.duplicateToNextMonth(card.summary.id) { id -> navController.navigate(Routes.listDetail(id)) } },
                                onArchiveToggle = { vm.setArchived(card.summary.id, !card.summary.isArchived) },
                                onDelete = { deleteTarget = card }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewSheet) {
        NewListSheet(
            existing = state.all,
            onDismiss = { showNewSheet = false },
            onCreate = { name, month, budget, start, copyFrom ->
                showNewSheet = false
                vm.createList(name, month, budget, start, copyFrom) { id -> navController.navigate(Routes.listDetail(id)) }
            }
        )
    }

    renameTarget?.let { target ->
        TextInputDialog(
            title = "Renomear lista",
            label = "Nome",
            initial = target.summary.name,
            onDismiss = { renameTarget = null },
            onConfirm = { vm.rename(target.summary.id, it); renameTarget = null }
        )
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Excluir lista?",
            text = "\"${target.summary.name}\" e seus ${target.summary.itemCount} itens serão apagados. Não dá para desfazer.",
            confirmLabel = "Excluir",
            destructive = true,
            onConfirm = { vm.delete(target.summary.id) },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun ListCard(
    card: ListCardUi,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val s = card.summary
    val muted = s.isArchived
    var menu by remember { mutableStateOf(false) }
    AppCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = Spacing.lg, top = Spacing.md, bottom = Spacing.lg, end = Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    s.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    Fmt.monthYear(s.referenceMonth, s.referenceYear),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opções da lista")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Renomear") },
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                        onClick = { menu = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicar para o próximo mês") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = { menu = false; onDuplicate() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (s.isArchived) "Desarquivar" else "Arquivar") },
                        leadingIcon = { Icon(if (s.isArchived) Icons.Default.Unarchive else Icons.Default.Archive, null) },
                        onClick = { menu = false; onArchiveToggle() }
                    )
                    DropdownMenuItem(
                        text = { Text("Excluir", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Column(Modifier.padding(end = Spacing.md)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Estimado", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        card.estimatedTotal?.let { Fmt.money(it) } ?: "—",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    if (card.cheapestMarketName != null) {
                        Text(
                            "no ${card.cheapestMarketName}" +
                                if (card.pricedItems < s.itemCount) " · ${s.itemCount - card.pricedItems} sem preço" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (s.itemCount > 0) {
                        Text("Sem preços neste mês", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (s.budgetTarget > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Orçamento", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Fmt.money(s.budgetTarget), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        val est = card.estimatedTotal
                        if (est != null && est > s.budgetTarget) {
                            Spacer(Modifier.height(2.dp))
                            Tag(
                                "Acima",
                                container = MaterialTheme.extended.negativeContainer,
                                content = MaterialTheme.extended.onNegativeContainer
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
            val progress = if (s.itemCount > 0) s.checkedCount.toFloat() / s.itemCount else 0f
            ThinProgress(progress, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                if (s.itemCount == 0) "Lista vazia" else "${s.checkedCount} de ${s.itemCount} itens no carrinho",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NewListSheet(
    existing: List<ListCardUi>,
    onDismiss: () -> Unit,
    onCreate: (name: String, month: YearMonth, budget: Double, start: ListStart, copyFrom: Long?) -> Unit
) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var name by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(ListStart.EMPTY) }
    var copyFrom by remember { mutableStateOf(existing.firstOrNull()?.summary) }
    var copyMenu by remember { mutableStateOf(false) }

    FormSheet(title = "Nova lista", onDismiss = onDismiss) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nome") },
            placeholder = { Text("Compras de ${Fmt.monthName(month.month)}") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mês", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            MonthSelector(month, { month = it })
        }
        OutlinedTextField(
            value = budget,
            onValueChange = { budget = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
            label = { Text("Orçamento (opcional)") },
            prefix = { Text("R$ ") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Text("Começar com", style = MaterialTheme.typography.labelLarge)
        val options = listOf(ListStart.EMPTY to "Vazia", ListStart.COPY to "Copiar lista", ListStart.MONTHLY to "Itens do mês")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = start == value,
                    onClick = { start = value },
                    enabled = value != ListStart.COPY || existing.isNotEmpty(),
                    shape = SegmentedButtonDefaults.itemShape(index, options.size)
                ) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
            }
        }
        when (start) {
            ListStart.COPY -> ExposedDropdownMenuBox(expanded = copyMenu, onExpandedChange = { copyMenu = it }) {
                OutlinedTextField(
                    value = copyFrom?.let { "${it.name} · ${Fmt.monthYear(it.referenceMonth, it.referenceYear)}" } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Copiar itens de") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = copyMenu) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = copyMenu, onDismissRequest = { copyMenu = false }) {
                    existing.forEach { card ->
                        DropdownMenuItem(
                            text = { Text("${card.summary.name} · ${Fmt.monthYear(card.summary.referenceMonth, card.summary.referenceYear)} (${card.summary.itemCount})") },
                            onClick = { copyFrom = card.summary; copyMenu = false }
                        )
                    }
                }
            }
            ListStart.MONTHLY -> Text(
                "Adiciona os produtos com consumo mensal definido, já na quantidade de costume.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ListStart.EMPTY -> Unit
        }
        Button(
            onClick = {
                onCreate(name, month, Fmt.parseMoney(budget) ?: 0.0, start, copyFrom?.id)
            },
            enabled = start != ListStart.COPY || copyFrom != null,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Criar lista") }
    }
}

/** Diálogo com um campo de texto (renomear, orçamento...). */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    prefix: String? = null
) {
    var text by remember { mutableStateOf(initial) }
    val prefixContent: (@Composable () -> Unit)? = prefix?.let { p -> @Composable { Text(p) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                prefix = prefixContent,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
