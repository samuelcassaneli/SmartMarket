package com.smartmarket.ui.screens.receipts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.smartmarket.data.dao.PurchaseDao
import com.smartmarket.data.dao.PurchaseWithMarket
import com.smartmarket.ui.appViewModel
import com.smartmarket.ui.components.AppCard
import com.smartmarket.ui.components.AppTopBar
import com.smartmarket.ui.components.EmptyState
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.LoadingBox
import com.smartmarket.ui.components.MarketAvatar
import com.smartmarket.ui.components.MoneyText
import com.smartmarket.ui.components.ScreenScaffold
import com.smartmarket.ui.navigation.Routes
import com.smartmarket.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

/** Notas de um mês, com o total. */
data class ReceiptMonth(val month: Int, val year: Int, val total: Double, val purchases: List<PurchaseWithMarket>)

class ReceiptsViewModel(purchaseDao: PurchaseDao) : ViewModel() {
    /** null = carregando. */
    val months: StateFlow<List<ReceiptMonth>?> = purchaseDao.getAllPurchases()
        .map { list -> groupByMonth(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun groupByMonth(list: List<PurchaseWithMarket>): List<ReceiptMonth> {
        val cal = Calendar.getInstance()
        return list.groupBy {
            cal.timeInMillis = it.issuedAt
            cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.MONTH) + 1
        }.entries
            .sortedByDescending { it.key }
            .map { (ym, purchases) ->
                ReceiptMonth(ym % 100, ym / 100, purchases.sumOf { it.totalAmount }, purchases.sortedByDescending { it.issuedAt })
            }
    }
}

@Composable
fun ReceiptsScreen(navController: NavController) {
    val vm = appViewModel { c -> ReceiptsViewModel(c.db.purchaseDao()) }
    val months by vm.months.collectAsStateWithLifecycle()

    ScreenScaffold(
        topBar = { AppTopBar(title = "Notas fiscais", onBack = { navController.popBackStack() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Routes.scanQr()) },
                icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                text = { Text("Escanear nota") }
            )
        }
    ) { padding ->
        val list = months
        when {
            list == null -> LoadingBox(Modifier.padding(padding))
            list.isEmpty() -> EmptyState(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = "Nenhuma nota ainda",
                message = "Escaneie o QR Code da nota fiscal do mercado para registrar itens, preços e gastos automaticamente.",
                modifier = Modifier.fillMaxSize().padding(padding),
                actionLabel = "Escanear nota",
                onAction = { navController.navigate(Routes.scanQr()) }
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                list.forEach { month ->
                    item(key = "h-${month.year}-${month.month}") {
                        Row(
                            Modifier.fillMaxWidth().padding(top = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                Fmt.monthYear(month.month, month.year),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            MoneyText(month.total, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(month.purchases, key = { it.id }) { p ->
                        PurchaseCard(p, onClick = { navController.navigate(Routes.receiptDetail(p.id)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PurchaseCard(p: PurchaseWithMarket, onClick: () -> Unit) {
    val name = p.supermarketName ?: p.storeName.ifBlank { "Compra" }
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarketAvatar(name, p.colorHex)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Fmt.dateTime(p.issuedAt)} · ${p.itemCount} ${if (p.itemCount == 1) "item" else "itens"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            MoneyText(p.totalAmount, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
