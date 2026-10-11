package com.smartmarket.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartmarket.data.entity.Product
import com.smartmarket.data.entity.ShoppingList
import com.smartmarket.domain.matching.FuzzyMatcher
import com.smartmarket.ui.components.AppCard
import com.smartmarket.ui.components.CategoryAvatar
import com.smartmarket.ui.components.IconBadge
import com.smartmarket.ui.components.MarketAvatar
import com.smartmarket.ui.components.MoneyText
import com.smartmarket.ui.components.SearchField
import com.smartmarket.ui.components.Tag
import com.smartmarket.ui.theme.Spacing

/**
 * Confirma com qual produto do app a oferta do site será vinculada (sugestão, troca ou novo produto)
 * e, para "Adicionar à lista", em qual lista.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkOfferSheet(
    sheet: LinkSheetState,
    allProducts: List<Product>,
    lists: List<ShoppingList>,
    onDismiss: () -> Unit,
    onConfirm: (ProductChoice, Long?) -> Unit
) {
    val offer = sheet.offerUi.offer
    val market = sheet.offerUi.market
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var choice by remember(offer.key) {
        mutableStateOf<ProductChoice>(
            sheet.suggested?.let { ProductChoice.Existing(it) } ?: ProductChoice.CreateNew(offer.name)
        )
    }
    var picking by remember(offer.key) { mutableStateOf(sheet.suggested == null) }
    var filter by remember(offer.key) { mutableStateOf("") }
    var listId by remember(offer.key) { mutableStateOf<Long?>(null) }
    var listTouched by remember(offer.key) { mutableStateOf(false) }

    // Padrão: lista não arquivada mais recente (ou nova lista do mês, se não houver)
    LaunchedEffect(lists) {
        if (!listTouched) listId = lists.firstOrNull()?.id
    }

    val candidates = remember(filter, allProducts, sheet.suggestions) {
        val f = FuzzyMatcher.normalize(filter)
        if (f.isBlank()) {
            sheet.suggestions.ifEmpty { allProducts.take(20) }
        } else {
            allProducts.filter { FuzzyMatcher.normalize(it.name).contains(f) }.take(30)
        }
    }

    val title = if (sheet.action == OfferAction.ADD_TO_LIST) "Adicionar à lista" else "Salvar preço"

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            item {
              Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.size(Spacing.md))
                AppCard(contentPadding = PaddingValues(Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MarketAvatar(market.name, market.colorHex, size = 36.dp)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(offer.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                market.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        MoneyText(offer.price)
                    }
                }
              }
            }

            item {
                Text(
                    "Produto no app",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm)
                )
            }

            if (!picking) {
                item {
                    SelectedProductCard(
                        choice = choice,
                        viaAlias = sheet.viaAlias && (choice as? ProductChoice.Existing)?.product?.id == sheet.suggested?.id,
                        isSuggestion = (choice as? ProductChoice.Existing)?.product?.id == sheet.suggested?.id,
                        onChange = { picking = true }
                    )
                }
            } else {
                item {
                    SearchField(
                        value = filter,
                        onValueChange = { filter = it },
                        placeholder = "Buscar nos meus produtos"
                    )
                }
                items(candidates, key = { "p-${it.id}" }) { p ->
                    val selected = (choice as? ProductChoice.Existing)?.product?.id == p.id
                    PickRow(
                        title = p.name,
                        subtitle = p.category,
                        selected = selected,
                        leading = { CategoryAvatar(p.category, size = 36.dp) },
                        onClick = {
                            choice = ProductChoice.Existing(p)
                            picking = false
                        }
                    )
                }
                if (candidates.isEmpty()) {
                    item {
                        Text(
                            "Nenhum produto encontrado.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    PickRow(
                        title = "Criar produto “${offer.name}”",
                        subtitle = "Novo produto com o nome do site",
                        selected = choice is ProductChoice.CreateNew,
                        leading = { IconBadge(Icons.Default.AddCircleOutline, size = 36.dp) },
                        onClick = {
                            choice = ProductChoice.CreateNew(offer.name)
                            picking = false
                        }
                    )
                }
            }

            if (sheet.action == OfferAction.ADD_TO_LIST) {
                item {
                  Column {
                    Text(
                        "Lista",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm)
                    )
                    Spacer(Modifier.size(Spacing.xs))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(lists, key = { "l-${it.id}" }) { l ->
                            FilterChip(
                                selected = listId == l.id,
                                onClick = { listId = l.id; listTouched = true },
                                label = { Text(l.name, maxLines = 1) },
                                leadingIcon = if (listId == l.id) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null
                            )
                        }
                        item(key = "new-list") {
                            FilterChip(
                                selected = listId == null,
                                onClick = { listId = null; listTouched = true },
                                label = { Text("Nova: ${SearchViewModel.newListName()}", maxLines = 1) },
                                leadingIcon = if (listId == null) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null
                            )
                        }
                    }
                  }
                }
            }

            item {
              Column {
                Spacer(Modifier.size(Spacing.sm))
                Button(
                    onClick = { onConfirm(choice, listId) },
                    enabled = !sheet.saving && !picking,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    if (sheet.saving) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(Spacing.sm))
                    }
                    Text(title)
                }
                if (picking && sheet.suggested != null) {
                    TextButton(onClick = { picking = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Voltar à sugestão")
                    }
                }
              }
            }
        }
    }
}

@Composable
private fun SelectedProductCard(
    choice: ProductChoice,
    viaAlias: Boolean,
    isSuggestion: Boolean,
    onChange: () -> Unit
) {
    AppCard(contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (choice) {
                is ProductChoice.Existing -> CategoryAvatar(choice.product.category, size = 36.dp)
                is ProductChoice.CreateNew -> IconBadge(Icons.Default.AddCircleOutline, size = 36.dp)
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    when (choice) {
                        is ProductChoice.Existing -> choice.product.name
                        is ProductChoice.CreateNew -> choice.name
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.size(2.dp))
                when {
                    choice is ProductChoice.CreateNew -> Tag("Novo produto")
                    viaAlias -> Tag("Vínculo já confirmado")
                    isSuggestion -> Tag("Sugestão automática")
                }
            }
            TextButton(onClick = onChange) { Text("Trocar") }
        }
    }
}

@Composable
private fun PickRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    leading: @Composable () -> Unit,
    onClick: () -> Unit
) {
    AppCard(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 40.dp)) {
            leading()
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = "Selecionado", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
