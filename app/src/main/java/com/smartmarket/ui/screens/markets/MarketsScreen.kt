@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.smartmarket.ui.screens.markets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.smartmarket.data.entity.OnlineSource
import com.smartmarket.data.entity.Supermarket
import com.smartmarket.ui.appViewModel
import com.smartmarket.ui.components.AppCard
import com.smartmarket.ui.components.AppTopBar
import com.smartmarket.ui.components.ConfirmDialog
import com.smartmarket.ui.components.EmptyState
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.LoadingBox
import com.smartmarket.ui.components.MarketAvatar
import com.smartmarket.ui.components.ScreenScaffold
import com.smartmarket.ui.components.Tag
import com.smartmarket.ui.theme.Spacing

@Composable
fun MarketsScreen(navController: NavController) {
    val vm = appViewModel { c -> MarketsViewModel(c.db.supermarketDao(), c.db.productPriceDao()) }
    val state by vm.state.collectAsStateWithLifecycle()

    var openTableFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<Supermarket?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Supermarket?>(null) }

    val tableMarket = openTableFor?.let { id -> state.markets.firstOrNull { it.id == id } }
    if (openTableFor != null && tableMarket != null) {
        BackHandler { openTableFor = null }
        PriceTableView(market = tableMarket, onBack = { openTableFor = null })
    } else {
        ScreenScaffold(
            topBar = {
                AppTopBar(
                    title = "Mercados",
                    subtitle = "Toque em um mercado para ver a tabela de preços",
                    onBack = { navController.popBackStack() }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Novo mercado") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        ) { padding ->
            when {
                state.loading -> LoadingBox(Modifier.padding(padding))
                state.markets.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    EmptyState(
                        Icons.Default.Storefront,
                        "Nenhum mercado",
                        "Cadastre os mercados onde você compra para comparar os preços.",
                        actionLabel = "Cadastrar mercado",
                        onAction = { creating = true }
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    items(state.markets, key = { it.id }) { market ->
                        MarketCard(
                            market = market,
                            priceCount = state.priceCounts[market.id] ?: 0,
                            monthLabel = Fmt.monthShort(state.month.month).lowercase(),
                            onOpen = { openTableFor = market.id },
                            onEdit = { editing = market },
                            onFavorite = { vm.toggleFavorite(market) }
                        )
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        MarketEditSheet(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { vm.save(it); creating = false; editing = null },
            onDelete = { deleteTarget = it; creating = false; editing = null }
        )
    }

    deleteTarget?.let { market ->
        ConfirmDialog(
            title = "Excluir ${market.name}?",
            text = "Todos os preços registrados neste mercado também serão apagados. Não dá para desfazer.",
            confirmLabel = "Excluir",
            destructive = true,
            onConfirm = { vm.delete(market) },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun MarketCard(
    market: Supermarket,
    priceCount: Int,
    monthLabel: String,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onFavorite: () -> Unit
) {
    AppCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = Spacing.lg, top = Spacing.md, bottom = Spacing.md, end = Spacing.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarketAvatar(market.name, market.colorHex, size = 48.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    market.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val place = listOf(market.address, market.city).filter { it.isNotBlank() }.joinToString(" · ")
                if (place.isNotEmpty()) {
                    Text(
                        place,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    if (priceCount == 0) "Sem preços em $monthLabel" else "$priceCount ${if (priceCount == 1) "preço" else "preços"} em $monthLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    if (market.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (market.isFavorite) "Remover dos favoritos" else "Marcar como favorito",
                    tint = if (market.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Editar ${market.name}")
            }
        }
        val online = market.onlineSource != OnlineSource.NONE
        val flyer = market.flyerUrl.isNotBlank()
        if (online || flyer) {
            Spacer(Modifier.height(Spacing.sm))
            FlowRow(
                modifier = Modifier.padding(start = 48.dp + Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                if (online) Tag("Busca online", icon = Icons.Default.Language)
                if (flyer) Tag("Encarte automático", icon = Icons.Default.Newspaper)
            }
        }
    }
}
