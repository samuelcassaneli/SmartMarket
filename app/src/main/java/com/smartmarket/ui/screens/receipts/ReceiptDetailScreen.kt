package com.smartmarket.ui.screens.receipts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.smartmarket.data.dao.PurchaseDao
import com.smartmarket.data.dao.PurchaseItemWithProduct
import com.smartmarket.data.dao.PurchaseWithMarket
import com.smartmarket.data.entity.Purchase
import com.smartmarket.data.repo.PurchaseRepository
import com.smartmarket.domain.nfce.NfceKey
import com.smartmarket.domain.nfce.NfceText
import com.smartmarket.ui.appViewModel
import com.smartmarket.ui.components.AppCard
import com.smartmarket.ui.components.AppTopBar
import com.smartmarket.ui.components.ConfirmDialog
import com.smartmarket.ui.components.EmptyState
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.LoadingBox
import com.smartmarket.ui.components.MarketAvatar
import com.smartmarket.ui.components.MoneyText
import com.smartmarket.ui.components.ScreenScaffold
import com.smartmarket.ui.components.SectionHeader
import com.smartmarket.ui.components.Tag
import com.smartmarket.ui.navigation.Routes
import com.smartmarket.ui.theme.Spacing
import com.smartmarket.ui.theme.extended
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReceiptDetailViewModel(
    private val purchaseId: Long,
    purchaseDao: PurchaseDao,
    private val repository: PurchaseRepository
) : ViewModel() {

    /** Loaded = false enquanto carrega. */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val summary: StateFlow<PurchaseWithMarket?> = purchaseDao.observePurchase(purchaseId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val items: StateFlow<List<PurchaseItemWithProduct>> = purchaseDao.getItemsForPurchase(purchaseId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _purchase = MutableStateFlow<Purchase?>(null)
    val purchase: StateFlow<Purchase?> = _purchase.asStateFlow()

    init {
        viewModelScope.launch {
            _purchase.value = purchaseDao.getById(purchaseId)
            _loaded.value = true
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deletePurchase(purchaseId)
            onDone()
        }
    }
}

@Composable
fun ReceiptDetailScreen(purchaseId: Long, navController: NavController) {
    val vm = appViewModel(key = "receipt-$purchaseId") { c ->
        ReceiptDetailViewModel(purchaseId, c.db.purchaseDao(), PurchaseRepository(c.db, c.priceRepository, c.productMatcher))
    }
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val purchase by vm.purchase.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Excluir compra?",
            text = "A nota e os itens serão removidos. Os preços registrados continuam no histórico.",
            confirmLabel = "Excluir",
            destructive = true,
            onConfirm = { vm.delete { navController.popBackStack() } },
            onDismiss = { confirmDelete = false }
        )
    }

    val p = purchase
    ScreenScaffold(
        topBar = {
            AppTopBar(
                title = summary?.supermarketName ?: p?.storeName?.ifBlank { null } ?: "Nota fiscal",
                subtitle = p?.let { Fmt.dateTime(it.issuedAt) },
                onBack = { navController.popBackStack() },
                actions = {
                    if (p != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Excluir compra")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            !loaded -> LoadingBox(Modifier.padding(padding))
            p == null -> EmptyState(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = "Nota não encontrada",
                message = "Esta compra pode ter sido excluída.",
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                item {
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val name = summary?.supermarketName ?: p.storeName.ifBlank { "Compra" }
                            MarketAvatar(name, summary?.colorHex)
                            Spacer(Modifier.width(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (p.storeName.isNotBlank() && p.storeName != name) {
                                    Text(p.storeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        Spacer(Modifier.height(Spacing.md))
                        Text("Total pago", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(p.totalAmount, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(Spacing.sm))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(Spacing.sm))
                        InfoLine("Data", Fmt.dateTime(p.issuedAt))
                        InfoLine("Itens", "${p.itemCount}")
                        if (p.discount > 0.0) InfoLine("Desconto", "- ${Fmt.money(p.discount)}", valueColor = MaterialTheme.extended.positive)
                        if (p.paymentMethod.isNotBlank()) InfoLine("Pagamento", p.paymentMethod)
                        if (p.cnpj.isNotBlank()) InfoLine("CNPJ", NfceText.formatCnpj(p.cnpj))
                        if (p.storeAddress.isNotBlank()) InfoLine("Endereço", p.storeAddress)
                        p.accessKey?.let { key ->
                            Spacer(Modifier.height(Spacing.sm))
                            Text("Chave de acesso", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(NfceKey.format(key), style = MaterialTheme.typography.bodySmall)
                        }
                        if (p.sourceUrl.isNotBlank()) {
                            Spacer(Modifier.height(Spacing.md))
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.sourceUrl)))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                                Spacer(Modifier.width(Spacing.sm))
                                Text("Ver na SEFAZ")
                            }
                        }
                    }
                }
                item { SectionHeader("Itens (${items.size})") }
                items(items, key = { it.id }) { item ->
                    PurchaseItemRow(item, onClick = item.productId?.let { id -> { navController.navigate(Routes.productDetail(id)) } })
                }
            }
        }
    }
}

@Composable
private fun PurchaseItemRow(item: PurchaseItemWithProduct, onClick: (() -> Unit)?) {
    AppCard(onClick = onClick, contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.productName ?: item.rawDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.productName != null) {
                    Text(item.rawDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "${Fmt.quantity(item.quantity)} ${item.unit.lowercase()} × ${Fmt.money(item.unitPrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(item.totalPrice, style = MaterialTheme.typography.titleSmall)
                if (item.productId == null) {
                    Spacer(Modifier.height(Spacing.xs))
                    Tag("Sem vínculo")
                }
            }
        }
    }
}
