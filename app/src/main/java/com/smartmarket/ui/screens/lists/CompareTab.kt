package com.smartmarket.ui.screens.lists

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartmarket.data.entity.Supermarket
import com.smartmarket.domain.comparator.MarketComparisonResult
import com.smartmarket.domain.comparator.SplitShoppingOptimizer
import com.smartmarket.domain.comparator.SplitSuggestion
import com.smartmarket.ui.components.AppCard
import com.smartmarket.ui.components.BestPriceTag
import com.smartmarket.ui.components.EmptyState
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.IconBadge
import com.smartmarket.ui.components.MarketAvatar
import com.smartmarket.ui.components.PriceChangeChip
import com.smartmarket.ui.components.SectionHeader
import com.smartmarket.ui.theme.Spacing
import com.smartmarket.ui.theme.extended

/** Conteúdo da aba Comparar. */
internal fun LazyListScope.compareTab(
    state: ListDetailUi,
    onSearch: (String) -> Unit,
    onOpenMarkets: () -> Unit
) {
    val summary = state.summary
    when {
        state.items.isEmpty() -> {
            item(key = "cmp-empty") {
                EmptyState(Icons.Default.Storefront, "Nada para comparar", "Adicione itens à lista para comparar os mercados.")
            }
            return
        }
        state.markets.isEmpty() -> {
            item(key = "cmp-nomarkets") {
                EmptyState(
                    Icons.Default.Storefront, "Nenhum mercado cadastrado",
                    "Cadastre os mercados onde você compra para comparar os preços.",
                    actionLabel = "Cadastrar mercados", onAction = onOpenMarkets
                )
            }
            return
        }
    }
    if (summary == null) return

    if (summary.itemsWithoutAnyPrice.isNotEmpty()) {
        item(key = "cmp-missing") { MissingPricesCard(summary.itemsWithoutAnyPrice.map { it.productName }, state.refMonth.toString(), onSearch) }
    }

    if (summary.bestSupermarketCurrent == null) {
        item(key = "cmp-noprices") {
            EmptyState(
                Icons.Default.Storefront,
                "Sem preços em ${state.refMonth}",
                "Registre preços na tabela do mercado, escaneie uma nota ou busque online. Você também pode trocar o mês de referência acima."
            )
        }
        return
    }

    summary.split?.let { split ->
        item(key = "cmp-split") { SplitCard(split) }
    }

    item(key = "cmp-ranking-h") {
        SectionHeader("Ranking de mercados", Modifier.padding(top = Spacing.sm))
    }
    val prevLabel = Fmt.monthShort(summary.previousMonth)
    val ranked = summary.marketResults.filter { it.hasAnyPrice }
    items(ranked, key = { "cmp-m-${it.supermarket.id}" }) { result ->
        MarketRankCard(position = ranked.indexOf(result) + 1, result = result, prevLabel = prevLabel)
    }
    val noPrices = summary.marketResults.filter { !it.hasAnyPrice }
    if (noPrices.isNotEmpty()) {
        item(key = "cmp-noprice-markets") {
            Text(
                "Sem preços em ${state.refMonth}: " + noPrices.joinToString { it.supermarket.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.sm)
            )
        }
    }
}

@Composable
private fun MissingPricesCard(names: List<String>, monthLabel: String, onSearch: (String) -> Unit) {
    val ext = MaterialTheme.extended
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded) names else names.take(4)
    AppCard(Modifier.fillMaxWidth(), containerColor = ext.warningContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = ext.onWarningContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(
                if (names.size == 1) "1 item sem preço em $monthLabel" else "${names.size} itens sem preço em $monthLabel",
                style = MaterialTheme.typography.titleSmall,
                color = ext.onWarningContainer
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        shown.forEach { name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ext.onWarningContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onSearch(name) }) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = ext.onWarningContainer)
                    Spacer(Modifier.width(4.dp))
                    Text("Buscar preços", color = ext.onWarningContainer)
                }
            }
        }
        if (names.size > 4) {
            TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                Text(if (expanded) "Mostrar menos" else "Mostrar mais ${names.size - 4}", color = ext.onWarningContainer)
            }
        }
    }
}

@Composable
private fun SplitCard(split: SplitSuggestion) {
    val ext = MaterialTheme.extended
    val plan = split.plan
    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.AutoMirrored.Filled.CallSplit, container = ext.positiveContainer, tint = ext.onPositiveContainer)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text("Dividir a compra compensa", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Economia de ${Fmt.money(plan.savings)} (${Fmt.percent(plan.savingsPercent, withSign = false)}) " +
                        "contra comprar tudo no ${split.bestSingle.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.positive
                )
            }
        }
        if (plan.extraItemsCovered > 0) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "E ainda encontra ${plan.extraItemsCovered} ${if (plan.extraItemsCovered == 1) "item" else "itens"} que faltam no ${split.bestSingle.name}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Spacing.md))
        SplitShare(split.first, plan.first)
        HorizontalDivider(Modifier.padding(vertical = Spacing.sm), color = MaterialTheme.colorScheme.outlineVariant)
        SplitShare(split.second, plan.second)
        Spacer(Modifier.height(Spacing.sm))
        Row {
            Text("Total dividido", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(Fmt.money(plan.total), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SplitShare(market: Supermarket, share: SplitShoppingOptimizer.MarketShare) {
    var expanded by rememberSaveable(market.id) { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarketAvatar(market.name, market.colorHex, size = 32.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(market.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${share.items.size} ${if (share.items.size == 1) "item" else "itens"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(Fmt.money(share.subtotal), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            androidx.compose.material3.IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Esconder itens" else "Ver itens"
                )
            }
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 44.dp, end = Spacing.sm), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                share.items.sortedBy { it.name.lowercase() }.forEach { item ->
                    Row {
                        Text(
                            "${Fmt.quantity(item.quantity)}× ${item.name}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(Fmt.money(item.total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketRankCard(position: Int, result: MarketComparisonResult, prevLabel: String) {
    val ext = MaterialTheme.extended
    var showMissing by rememberSaveable(result.supermarket.id) { mutableStateOf(false) }
    val market = result.supermarket
    AppCard(
        Modifier.fillMaxWidth(),
        containerColor = if (result.isBestPrice) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$position",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(20.dp)
            )
            MarketAvatar(market.name, market.colorHex)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(market.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${result.itemsAvailableCount} de ${result.totalItemsCount} itens",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (result.isComplete) MaterialTheme.colorScheme.onSurfaceVariant else ext.warning
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.money(result.totalPrice), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (result.isBestPrice) {
                    BestPriceTag()
                } else if (result.differenceFromBest > 0.004) {
                    Text(
                        "+${Fmt.money(result.differenceFromBest)} (${Fmt.percent(result.percentageMoreExpensive)})",
                        style = MaterialTheme.typography.labelMedium,
                        color = ext.negative
                    )
                } else if (result.differenceFromBest < -0.004) {
                    // Mais barato que o melhor, mas com menos itens cotados
                    Text(
                        "${Fmt.money(result.differenceFromBest)} com menos itens",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        val inflation = result.inflationPercent
        if (inflation != null || result.missingItems.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (inflation != null) {
                    Text(
                        "vs $prevLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    PriceChangeChip(inflation)
                }
                Spacer(Modifier.weight(1f))
                if (result.missingItems.isNotEmpty()) {
                    TextButton(onClick = { showMissing = !showMissing }, contentPadding = PaddingValues(horizontal = Spacing.sm)) {
                        Text(
                            "Faltam ${result.missingItems.size}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Icon(
                            if (showMissing) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showMissing) "Esconder faltantes" else "Ver faltantes",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        AnimatedVisibility(showMissing) {
            Text(
                result.missingItems.joinToString(" · ") { it.productName },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs)
            )
        }
    }
}
