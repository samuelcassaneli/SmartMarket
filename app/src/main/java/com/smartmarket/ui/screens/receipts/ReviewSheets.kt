package com.smartmarket.ui.screens.receipts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartmarket.data.entity.Product
import com.smartmarket.data.entity.Supermarket
import com.smartmarket.domain.matching.FuzzyMatcher
import com.smartmarket.domain.nfce.NfceReceipt
import com.smartmarket.domain.nfce.NfceText
import com.smartmarket.ui.components.CategoryAvatar
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.IconBadge
import com.smartmarket.ui.components.MarketAvatar
import com.smartmarket.ui.components.SearchField
import com.smartmarket.ui.components.Tag
import com.smartmarket.ui.theme.Spacing

/** Sheet para vincular um item da nota: sugestões, busca, criar produto ou ignorar. */
@Composable
fun ItemLinkSheet(
    item: ReviewItem,
    products: List<Product>,
    draft: Product,
    onDismiss: () -> Unit,
    onPick: (Product) -> Unit,
    onCreate: (name: String, category: String, unit: String) -> Unit,
    onIgnore: () -> Unit,
    onUnlink: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var creating by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = Spacing.screen).navigationBarsPadding()) {
            Text(item.item.description, style = MaterialTheme.typography.titleMedium)
            Text(
                "${Fmt.quantity(item.item.quantity)} ${item.item.unit.lowercase()} × ${Fmt.money(item.item.unitPrice)} = ${Fmt.money(item.item.totalPrice)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            item.linkedName?.let {
                Spacer(Modifier.height(Spacing.xs))
                Text("Vinculado a: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(Spacing.md))

            if (creating) {
                ProductForm(
                    initial = draft,
                    confirmLabel = "Criar e vincular",
                    onConfirm = onCreate,
                    onCancel = { creating = false }
                )
                Spacer(Modifier.height(Spacing.lg))
            } else {
                SearchField(value = query, onValueChange = { query = it }, placeholder = "Buscar produto")
                Spacer(Modifier.height(Spacing.sm))
                ProductPickerList(
                    products = products,
                    query = query,
                    suggestions = item.suggestions,
                    selectedId = item.product?.id,
                    onPick = onPick,
                    modifier = Modifier.heightIn(max = 340.dp)
                )
                Spacer(Modifier.height(Spacing.sm))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(Spacing.sm))
                Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Criar produto")
                }
                Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(onClick = onIgnore, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Icon(Icons.Default.Block, contentDescription = null)
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Ignorar item")
                    }
                    if (item.linkedName != null || item.ignored) {
                        OutlinedButton(onClick = onUnlink, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Icon(Icons.Default.LinkOff, contentDescription = null)
                            Spacer(Modifier.width(Spacing.xs))
                            Text("Desfazer")
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }
}

/** Lista de produtos: sugestões no topo (com %) e depois a busca. */
@Composable
internal fun ProductPickerList(
    products: List<Product>,
    query: String,
    suggestions: List<FuzzyMatcher.Ranked<Product>>,
    selectedId: Long?,
    onPick: (Product) -> Unit,
    modifier: Modifier = Modifier
) {
    val q = FuzzyMatcher.normalize(query)
    val suggestionIds = suggestions.map { it.item.id }.toSet()
    val filtered = remember(products, q) {
        if (q.isBlank()) products.take(40)
        else products.filter { FuzzyMatcher.normalize(it.name).contains(q) || FuzzyMatcher.normalize(it.category).contains(q) }.take(60)
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(vertical = Spacing.xs)) {
        if (q.isBlank() && suggestions.isNotEmpty()) {
            item { SheetLabel("Sugestões") }
            items(suggestions, key = { "s-${it.item.id}" }) { ranked ->
                ProductRowItem(
                    product = ranked.item,
                    trailing = "${(ranked.score * 100).toInt()}%",
                    selected = ranked.item.id == selectedId,
                    onClick = { onPick(ranked.item) }
                )
            }
            item { SheetLabel("Todos os produtos") }
        }
        val rest = if (q.isBlank()) filtered.filter { it.id !in suggestionIds } else filtered
        if (rest.isEmpty()) {
            item {
                Text(
                    if (q.isBlank()) "Nenhum produto cadastrado ainda." else "Nenhum produto encontrado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.md)
                )
            }
        }
        items(rest, key = { "p-${it.id}" }) { p ->
            ProductRowItem(product = p, trailing = null, selected = p.id == selectedId, onClick = { onPick(p) })
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs)
    )
}

@Composable
private fun ProductRowItem(product: Product, trailing: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryAvatar(product.category, size = 36.dp)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(product.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${product.category} · ${product.unit}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (trailing != null) Tag(trailing)
        if (selected) {
            Spacer(Modifier.width(Spacing.sm))
            Icon(Icons.Default.Check, contentDescription = "Selecionado", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private val UNITS = listOf("un", "kg", "g", "L", "ml", "pct", "cx")
private val CATEGORIES = listOf("Alimentos", "Hortifruti", "Carnes", "Laticínios", "Padaria", "Bebidas", "Matinais", "Limpeza", "Higiene", "Geral")

/** Formulário de produto novo (nome, categoria, unidade), já preenchido com a sugestão. */
@Composable
internal fun ProductForm(
    initial: Product,
    confirmLabel: String,
    onConfirm: (name: String, category: String, unit: String) -> Unit,
    onCancel: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var category by rememberSaveable { mutableStateOf(initial.category) }
    var unit by rememberSaveable { mutableStateOf(initial.unit) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Novo produto", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nome") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
        )
        OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("Categoria") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        ChipRow(options = CATEGORIES, selected = category, onSelect = { category = it })
        Text("Unidade", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChipRow(options = UNITS, selected = unit, onSelect = { unit = it })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Voltar") }
            Button(
                onClick = { onConfirm(name.trim(), category.trim(), unit) },
                enabled = name.isNotBlank(),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) { Text(confirmLabel) }
        }
    }
}

@Composable
private fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        options.forEach { opt ->
            FilterChip(selected = opt.equals(selected, ignoreCase = true), onClick = { onSelect(opt) }, label = { Text(opt) })
        }
    }
}

/** Sheet para escolher o mercado da nota: sugeridos, todos ou criar novo. */
@Composable
fun MarketPickerSheet(
    receipt: NfceReceipt,
    current: MarketChoice,
    markets: List<Supermarket>,
    suggested: List<Supermarket>,
    onDismiss: () -> Unit,
    onChoose: (MarketChoice) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newName by rememberSaveable { mutableStateOf(NfceText.cleanStoreName(receipt.storeName)) }
    val suggestedIds = suggested.map { it.id }.toSet()
    val ordered = suggested + markets.filter { it.id !in suggestedIds }
    val currentId = (current as? MarketChoice.Existing)?.market?.id

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = Spacing.screen).navigationBarsPadding()) {
            Text("Mercado da nota", style = MaterialTheme.typography.titleMedium)
            Text(
                "${receipt.storeName}${if (receipt.cnpj.isNotBlank()) " · " + NfceText.formatCnpj(receipt.cnpj) else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.md))
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(ordered, key = { it.id }) { m ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { onChoose(MarketChoice.Existing(m, byCnpj = m.cnpj.isNotBlank() && m.cnpj == receipt.cnpj)) }
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MarketAvatar(m.name, m.colorHex, size = 36.dp)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sub = listOfNotNull(
                                m.city.takeIf { it.isNotBlank() },
                                m.cnpj.takeIf { it.isNotBlank() }?.let { NfceText.formatCnpj(it) }
                            ).joinToString(" · ")
                            if (sub.isNotBlank()) {
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (m.id in suggestedIds) Tag("Sugerido")
                        if (m.id == currentId) {
                            Spacer(Modifier.width(Spacing.sm))
                            Icon(Icons.Default.Check, contentDescription = "Selecionado", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Default.AddBusiness, size = 36.dp)
                Spacer(Modifier.width(Spacing.md))
                Text("Cadastrar novo mercado", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Nome do mercado") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Button(onClick = { onChoose(MarketChoice.New(newName.trim())) }, enabled = newName.isNotBlank()) {
                    Text("Usar novo mercado")
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
