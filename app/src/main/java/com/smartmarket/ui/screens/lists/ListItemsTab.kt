@file:OptIn(ExperimentalMaterial3Api::class)

package com.smartmarket.ui.screens.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartmarket.data.dao.ShoppingItemWithProduct
import com.smartmarket.ui.components.CategoryAvatar
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.QuantityStepper
import com.smartmarket.ui.theme.Spacing

/** Conteúdo da aba Itens (também usado no modo mercado). */
internal fun LazyListScope.itemsTab(
    state: ListDetailUi,
    onToggle: (ShoppingItemWithProduct) -> Unit,
    onQuantity: (ShoppingItemWithProduct, Double) -> Unit,
    onRemove: (ShoppingItemWithProduct) -> Unit,
    onOpenProduct: (Long) -> Unit,
    onAdd: () -> Unit
) {
    if (state.items.isEmpty()) {
        item(key = "empty") { EmptyItems(onAdd) }
        return
    }
    val pending = state.items.filter { !it.isChecked }
    val done = state.items.filter { it.isChecked }

    pending.groupBy { it.productCategory.trim().ifBlank { "Outros" } }
        .entries.sortedBy { it.key.lowercase() }
        .forEach { (category, list) ->
            item(key = "cat-$category") { CategoryHeader(category, list.size) }
            items(list.sortedBy { it.productName.lowercase() }, key = { "i-${it.itemId}" }) { item ->
                SwipeRow(item, onRemove) {
                    ItemRow(item, state, onToggle, onQuantity, onOpenProduct)
                }
            }
        }

    if (pending.isEmpty() && done.isNotEmpty()) {
        item(key = "all-done") {
            Text(
                "Tudo no carrinho!",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = Spacing.md)
            )
        }
    }

    if (done.isNotEmpty() && !(state.marketMode && state.hideChecked)) {
        item(key = "done-header") {
            Text(
                "No carrinho (${done.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs)
            )
        }
        items(done.sortedBy { it.productName.lowercase() }, key = { "i-${it.itemId}" }) { item ->
            SwipeRow(item, onRemove) {
                ItemRow(item, state, onToggle, onQuantity, onOpenProduct)
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.md, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryAvatar(category, size = 28.dp)
        Spacer(Modifier.width(Spacing.sm))
        Text(category, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwipeRow(
    item: ShoppingItemWithProduct,
    onRemove: (ShoppingItemWithProduct) -> Unit,
    content: @Composable () -> Unit
) {
    val currentItem by rememberUpdatedState(item)
    val currentOnRemove by rememberUpdatedState(onRemove)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                currentOnRemove(currentItem)
                true
            } else false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = Spacing.lg),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Remover", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    ) { content() }
}

@Composable
private fun ItemRow(
    item: ShoppingItemWithProduct,
    state: ListDetailUi,
    onToggle: (ShoppingItemWithProduct) -> Unit,
    onQuantity: (ShoppingItemWithProduct, Double) -> Unit,
    onOpenProduct: (Long) -> Unit
) {
    val marketMode = state.marketMode
    val checked = item.isChecked
    val cheapest = state.summary?.cheapestByProduct?.get(item.productId)
    val priceLine: String
    val priceHighlight: Boolean
    if (marketMode) {
        val here = state.priceAtShoppingMarket(item.productId)
        priceHighlight = here != null && cheapest != null && here <= cheapest.unitPrice + 0.001
        priceLine = when {
            here != null && item.quantity != 1.0 -> "${Fmt.money(here)} · total ${Fmt.money(here * item.quantity)}"
            here != null -> Fmt.money(here)
            cheapest != null -> "Sem preço aqui · ${Fmt.money(cheapest.unitPrice)} no ${cheapest.supermarketName}"
            else -> "Sem preço no mês"
        }
    } else {
        priceHighlight = false
        priceLine = cheapest?.let { "${Fmt.money(it.unitPrice)} · ${it.supermarketName}" } ?: "Sem preço no mês"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (marketMode) 64.dp else 56.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(if (marketMode) Modifier.clickable { onToggle(item) } else Modifier)
            .alpha(if (checked) 0.55f else 1f)
            .padding(end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle(item) },
            modifier = if (marketMode) Modifier.padding(horizontal = Spacing.xs).scale(1.3f) else Modifier
        )
        Column(
            Modifier
                .weight(1f)
                .then(if (!marketMode) Modifier.clip(MaterialTheme.shapes.small).clickable { onOpenProduct(item.productId) } else Modifier)
                .padding(vertical = Spacing.xs)
        ) {
            Text(
                item.productName,
                style = if (marketMode) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                priceLine,
                style = MaterialTheme.typography.bodySmall,
                color = if (priceHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (marketMode) {
            Text(
                "${Fmt.quantity(item.quantity)} ${item.productUnit}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = Spacing.sm, end = Spacing.sm)
            )
        } else {
            val step = quantityStep(item.productUnit)
            QuantityStepper(
                value = item.quantity,
                onValueChange = { onQuantity(item, it) },
                step = step,
                min = step,
                unit = item.productUnit.takeIf { it != "un" }
            )
        }
    }
}
