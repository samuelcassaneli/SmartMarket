@file:OptIn(ExperimentalMaterial3Api::class)

package com.smartmarket.ui.screens.more

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.smartmarket.data.dao.FlyerDao
import com.smartmarket.data.dao.PantryDao
import com.smartmarket.data.dao.ProductDao
import com.smartmarket.data.dao.PurchaseDao
import com.smartmarket.data.dao.SupermarketDao
import com.smartmarket.ui.appViewModel
import com.smartmarket.ui.components.AppCard
import com.smartmarket.ui.components.AppTopBar
import com.smartmarket.ui.components.IconBadge
import com.smartmarket.ui.components.ScreenScaffold
import com.smartmarket.ui.components.Tag
import com.smartmarket.ui.navigation.Routes
import com.smartmarket.ui.theme.Spacing
import com.smartmarket.ui.theme.extended
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MoreCounts(
    val products: Int = 0,
    val markets: Int = 0,
    val pantry: Int = 0,
    val lowStock: Int = 0,
    val receipts: Int = 0,
    val pendingOffers: Int = 0
)

class MoreViewModel(
    productDao: ProductDao,
    supermarketDao: SupermarketDao,
    pantryDao: PantryDao,
    purchaseDao: PurchaseDao,
    flyerDao: FlyerDao
) : ViewModel() {
    private val base = combine(
        productDao.getAllProducts(),
        supermarketDao.getAllSupermarkets(),
        pantryDao.getPantry()
    ) { p, m, pantry ->
        MoreCounts(
            products = p.size,
            markets = m.size,
            pantry = pantry.size,
            lowStock = pantry.count { it.quantity <= it.minQuantity }
        )
    }

    val counts: StateFlow<MoreCounts> = combine(
        base,
        purchaseDao.getAllPurchases(),
        flyerDao.observePendingCount()
    ) { b, purchases, pending -> b.copy(receipts = purchases.size, pendingOffers = pending) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MoreCounts())
}

private data class MoreEntry(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val count: String?,
    val route: String,
    val alert: String? = null
)

@Composable
fun MoreScreen(navController: NavController) {
    val vm = appViewModel { c ->
        MoreViewModel(c.db.productDao(), c.db.supermarketDao(), c.db.pantryDao(), c.db.purchaseDao(), c.db.flyerDao())
    }
    val counts by vm.counts.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val version = remember { appVersion(context) }

    val entries = listOf(
        MoreEntry(Icons.Default.Category, "Produtos", "Catálogo, categorias e consumo do mês", counts.products.toString(), Routes.PRODUCTS),
        MoreEntry(Icons.Default.Storefront, "Mercados", "Lojas, encartes e tabela de preços", counts.markets.toString(), Routes.MARKETS),
        MoreEntry(
            Icons.Default.Kitchen, "Despensa", "O que você tem em casa", counts.pantry.toString(), Routes.PANTRY,
            alert = if (counts.lowStock > 0) "${counts.lowStock} acabando" else null
        ),
        MoreEntry(Icons.AutoMirrored.Filled.ReceiptLong, "Notas fiscais", "Compras importadas pelo QR Code", counts.receipts.toString(), Routes.RECEIPTS),
        MoreEntry(
            Icons.Default.Newspaper, "Encartes", "Ofertas baixadas dos mercados", null, Routes.FLYERS,
            alert = if (counts.pendingOffers > 0) "${counts.pendingOffers} para revisar" else null
        ),
        MoreEntry(Icons.Default.DocumentScanner, "Ler encarte manualmente", "Foto ou PDF de um encarte de ofertas", null, Routes.FLYER_SCAN)
    )

    ScreenScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { AppTopBar(title = "Mais", large = true, scrollBehavior = scrollBehavior) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            items(entries.chunked(2), key = { row -> row.first().route }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    row.forEach { entry ->
                        MoreTile(entry, Modifier.weight(1f)) { navController.navigate(entry.route) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item(key = "settings") {
                AppCard(
                    onClick = { navController.navigate(Routes.SETTINGS) },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(
                            Icons.Default.Settings,
                            container = MaterialTheme.colorScheme.surfaceContainerHighest,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text("Configurações", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "CEP, orçamento, tema, notificações e backup",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item(key = "version") {
                Text(
                    "SmartMarket $version",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg)
                )
            }
        }
    }
}

@Composable
private fun MoreTile(entry: MoreEntry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    AppCard(onClick = onClick, modifier = modifier.heightIn(min = 148.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(entry.icon)
            Spacer(Modifier.weight(1f))
            if (entry.count != null) {
                Text(entry.count, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            entry.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (entry.alert != null) {
            Spacer(Modifier.height(Spacing.xs))
            Tag(entry.alert, container = MaterialTheme.extended.warningContainer, content = MaterialTheme.extended.onWarningContainer)
        }
    }
}

@Suppress("DEPRECATION")
private fun appVersion(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: ""
