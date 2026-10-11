@file:OptIn(ExperimentalMaterial3Api::class)

package com.smartmarket.ui.screens.markets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartmarket.data.entity.Supermarket
import com.smartmarket.ui.appViewModel
import com.smartmarket.ui.components.AppTopBar
import com.smartmarket.ui.components.EmptyState
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.LoadingBox
import com.smartmarket.ui.components.MarketAvatar
import com.smartmarket.ui.components.PriceChangeChip
import com.smartmarket.ui.components.ScreenScaffold
import com.smartmarket.ui.components.SearchField
import com.smartmarket.ui.screens.lists.MonthSelector
import com.smartmarket.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Tabela de preços de um mercado: todos os produtos com preço editável no mês. */
@Composable
fun PriceTableView(market: Supermarket, onBack: () -> Unit) {
    val vm = appViewModel(key = "price-table-${market.id}") { c ->
        PriceTableViewModel(market.id, c.db.productDao(), c.db.productPriceDao(), c.priceRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ScreenScaffold(
        topBar = {
            AppTopBar(
                title = market.name,
                subtitle = "Tabela de preços",
                onBack = onBack,
                actions = {
                    MarketAvatar(market.name, market.colorHex, size = 32.dp, modifier = Modifier.padding(end = Spacing.md))
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${state.filledCount} de ${state.totalProducts}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("produtos com preço", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MonthSelector(state.month, vm::setMonth)
                }
                SearchField(value = state.query, onValueChange = vm::setQuery, placeholder = "Buscar produto")
                if (state.copyableCount > 0) {
                    FilledTonalButton(
                        onClick = {
                            vm.copyPreviousMonth { n ->
                                scope.launch { snackbar.showSnackbar("$n ${if (n == 1) "preço copiado" else "preços copiados"} de ${state.month.previous()}") }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Copiar ${state.copyableCount} preços do mês anterior")
                    }
                }
            }
            when {
                state.loading -> LoadingBox()
                state.totalProducts == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(Icons.Default.PriceCheck, "Nenhum produto", "Cadastre produtos para registrar os preços deste mercado.")
                }
                state.rows.isEmpty() -> EmptyState(Icons.Default.PriceCheck, "Nada encontrado", "Tente outro nome.")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().imePadding(),
                    contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 48.dp)
                ) {
                    items(state.rows, key = { it.product.id }) { row ->
                        PriceRow(row, prevLabel = Fmt.monthShort(state.month.previous().month).lowercase()) { price ->
                            vm.setPrice(row.product.id, price)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceRow(row: PriceRowUi, prevLabel: String, onCommit: (Double?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.product.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.previous?.let { "$prevLabel: ${Fmt.money(it)}" } ?: "${row.product.category} · ${row.product.unit}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                row.changePercent?.let {
                    Spacer(Modifier.width(Spacing.xs))
                    PriceChangeChip(it)
                }
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        PriceField(row.current, onCommit)
    }
}

/** Campo de preço inline: grava ao confirmar (Concluir) ou ao perder o foco. */
@Composable
private fun PriceField(current: Double?, onCommit: (Double?) -> Unit) {
    var text by remember(current) { mutableStateOf(current?.let { Fmt.decimal(it) } ?: "") }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun commit() {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            if (current != null) onCommit(null)
            return
        }
        val parsed = Fmt.parseMoney(trimmed)
        if (parsed == null || parsed <= 0.0) {
            text = current?.let { Fmt.decimal(it) } ?: ""
            return
        }
        if (current == null || abs(parsed - current) > 0.0001) onCommit(parsed)
        text = Fmt.decimal(parsed)
    }

    OutlinedTextField(
        value = text,
        onValueChange = { v -> text = v.filter { it.isDigit() || it == ',' || it == '.' }.take(10) },
        modifier = Modifier
            .width(128.dp)
            .onFocusChanged {
                if (focused && !it.isFocused) commit()
                focused = it.isFocused
            },
        singleLine = true,
        prefix = { Text("R$ ") },
        placeholder = { Text("0,00") },
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        shape = MaterialTheme.shapes.small
    )
}
