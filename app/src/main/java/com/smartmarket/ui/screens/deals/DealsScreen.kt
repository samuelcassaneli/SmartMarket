package com.smartmarket.ui.screens.deals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.smartmarket.domain.deals.Deal
import com.smartmarket.ui.LocalAppContainer
import com.smartmarket.ui.components.AppCard
import com.smartmarket.ui.components.AppTopBar
import com.smartmarket.ui.components.EmptyState
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.MarketAvatar
import com.smartmarket.ui.components.ScreenScaffold
import com.smartmarket.ui.components.Tag
import com.smartmarket.ui.navigation.Routes
import com.smartmarket.ui.theme.Spacing
import com.smartmarket.ui.theme.extended
import com.smartmarket.work.FlyerSyncWorker

/** Produtos que ficaram mais baratos na última verificação automática. */
@Composable
fun DealsScreen(navController: NavController) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val report by container.dealReports.report.collectAsStateWithLifecycle()
    LaunchedEffect(report?.checkedAt) { container.dealReports.markSeen() }

    ScreenScaffold(
        topBar = {
            AppTopBar(
                title = "Promoções",
                subtitle = report?.let { "Verificado em ${Fmt.dateTime(it.checkedAt)}" },
                onBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { FlyerSyncWorker.runNow(context) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Verificar agora")
                    }
                }
            )
        }
    ) { padding ->
        val r = report
        if (r == null || r.deals.isEmpty()) {
            EmptyState(
                icon = Icons.Default.LocalOffer,
                title = if (r == null) "Nenhuma verificação ainda" else "Nada mais barato desta vez",
                message = if (r == null) "A verificação roda sozinha na frequência escolhida em Configurações. Toque em atualizar para verificar agora."
                else "Comparamos ${r.pricesChecked} preços com a sua última compra e nenhum ficou mais barato.",
                modifier = Modifier.padding(padding),
                actionLabel = "Verificar agora",
                onAction = { FlyerSyncWorker.runNow(context) }
            )
            return@ScreenScaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Text(
                    "${r.deals.size} produto(s) mais barato(s) que antes",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(r.deals, key = { "${it.productId}-${it.supermarketId}" }) { d ->
                DealCard(d) { navController.navigate(Routes.productDetail(d.productId)) }
            }
        }
    }
}

@Composable
fun DealCard(d: Deal, onClick: () -> Unit) {
    val ext = MaterialTheme.extended
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarketAvatar(d.marketName, null)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(d.productName, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${d.marketName} · ${d.origin}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Tag(
                "-" + Fmt.decimal(d.savingsPercent, 0) + "%",
                container = ext.positiveContainer,
                content = ext.onPositiveContainer
            )
        }
        Spacer(Modifier.padding(top = Spacing.sm))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.money(d.newPrice), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ext.positive)
            Spacer(Modifier.width(Spacing.sm))
            Text(
                Fmt.money(d.oldPrice),
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "Comparado com a ${d.comparedWith}" + (d.oldDate?.let { " (${Fmt.date(it)})" } ?: "") +
                ". Economia de ${Fmt.money(d.savings)} por unidade." + (if (d.note.isNotBlank()) " ${d.note}." else ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
