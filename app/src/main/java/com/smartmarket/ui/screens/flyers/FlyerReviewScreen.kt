package com.smartmarket.ui.screens.flyers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.smartmarket.data.entity.OfferStatus
import com.smartmarket.domain.flyers.FlyerTitle
import com.smartmarket.ui.appViewModel
import com.smartmarket.ui.components.AppCard
import com.smartmarket.ui.components.AppTopBar
import com.smartmarket.ui.components.ConfirmDialog
import com.smartmarket.ui.components.EmptyState
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.LoadingBox
import com.smartmarket.ui.components.MoneyText
import com.smartmarket.ui.components.ScreenScaffold
import com.smartmarket.ui.components.SearchField
import com.smartmarket.ui.components.Tag
import com.smartmarket.ui.theme.Spacing
import com.smartmarket.ui.theme.extended

/** Revisão das ofertas de um encarte baixado: aceitar, rejeitar ou vincular produto. */
@Composable
fun FlyerReviewScreen(flyerId: Long, navController: NavController) {
    val vm = appViewModel(key = "flyer-$flyerId") { c ->
        FlyerReviewViewModel(
            flyerId = flyerId,
            flyerDao = c.db.flyerDao(),
            productDao = c.db.productDao(),
            shoppingListDao = c.db.shoppingListDao(),
            priceRepository = c.priceRepository,
            matcher = c.productMatcher
        )
    }
    val flyer by vm.flyer.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pickerFor by remember { mutableStateOf<OfferUi?>(null) }
    var confirmAll by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    val f = flyer
    val subtitle = f?.let { listOfNotNull(FlyerTitle.decode(it.sourceUrl), Fmt.date(it.fetchedAt)).joinToString(" · ") }

    ScreenScaffold(
        topBar = {
            AppTopBar(
                title = f?.supermarketName ?: "Ofertas do encarte",
                subtitle = subtitle,
                onBack = { navController.popBackStack() },
                actions = {
                    if (ui.pendingLinked > 0) {
                        IconButton(onClick = { confirmAll = true }) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Aceitar todas vinculadas")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item(key = "summary") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = Spacing.sm)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                        Metric("Ofertas", ui.total.toString())
                        Metric("Pendentes", ui.pending.toString())
                        Metric("Da sua lista", ui.myList.toString())
                    }
                    SearchField(
                        value = query,
                        onValueChange = vm::setQuery,
                        placeholder = "Procurar no encarte"
                    )
                    if (query.isBlank()) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            OfferFilter.values().forEachIndexed { i, opt ->
                                SegmentedButton(
                                    selected = filter == opt,
                                    onClick = { vm.setFilter(opt) },
                                    shape = SegmentedButtonDefaults.itemShape(i, OfferFilter.values().size)
                                ) { Text(opt.label, maxLines = 1) }
                            }
                        }
                    }
                    if (ui.pendingLinked > 0 && query.isBlank()) {
                        FilledTonalButton(onClick = { confirmAll = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Spacing.sm))
                            Text("Aceitar todas vinculadas (${ui.pendingLinked})")
                        }
                    }
                }
            }

            when {
                ui.loading -> item(key = "loading") { LoadingBox(Modifier.height(200.dp)) }
                ui.offers.isEmpty() -> item(key = "empty") {
                    if (query.isNotBlank()) {
                        EmptyState(Icons.Default.SearchOff, "Nada encontrado", "Nenhum texto do encarte contém \"$query\".")
                    } else when (filter) {
                        OfferFilter.PENDING -> EmptyState(Icons.Default.DoneAll, "Tudo revisado", "Não há ofertas pendentes neste encarte.")
                        OfferFilter.MY_LIST -> EmptyState(Icons.Default.SearchOff, "Nada da sua lista", "Nenhuma oferta deste encarte é de produto das suas listas atuais.")
                        OfferFilter.ALL -> EmptyState(Icons.Default.SearchOff, "Sem ofertas", "Não encontramos ofertas com preço neste encarte.")
                    }
                }
                else -> items(ui.offers, key = { it.offer.id }) { u ->
                    OfferCard(
                        u = u,
                        onAccept = { vm.accept(u) },
                        onReject = { vm.reject(u) },
                        onRestore = { vm.restore(u) },
                        onLink = { pickerFor = u }
                    )
                }
            }
        }
    }

    val picking = pickerFor
    if (picking != null) {
        val suggestions = remember(picking.offer.id, products) { vm.suggestionsFor(picking.description) }
        ProductPickerSheet(
            sourceText = picking.description,
            products = products,
            suggestions = suggestions,
            currentProductId = picking.offer.productId,
            onDismiss = { pickerFor = null },
            onPick = { vm.link(picking, it); pickerFor = null },
            onUnlink = { vm.link(picking, null); pickerFor = null },
            onCreate = { name, category -> vm.createAndLink(picking, name, category); pickerFor = null }
        )
    }

    if (confirmAll) {
        ConfirmDialog(
            title = "Aceitar ofertas vinculadas",
            text = "Salvar o preço de ${ui.pendingLinked} ofertas com produto vinculado? Confira antes os vínculos marcados com \"Confira\".",
            confirmLabel = "Aceitar todas",
            onConfirm = { vm.acceptAllLinked() },
            onDismiss = { confirmAll = false }
        )
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OfferCard(
    u: OfferUi,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onRestore: () -> Unit,
    onLink: () -> Unit
) {
    val ext = MaterialTheme.extended
    val o = u.offer
    val linked = o.productId != null
    AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    "\"${u.description}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                if (linked) {
                    Text(o.productName ?: "Produto", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else {
                    Text("Não vinculado", style = MaterialTheme.typography.titleSmall, color = ext.warning)
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            MoneyText(o.price, style = MaterialTheme.typography.titleLarge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(top = Spacing.xs)) {
            when (o.status) {
                OfferStatus.ACCEPTED -> Tag("Aceita", container = ext.positiveContainer, content = ext.onPositiveContainer)
                OfferStatus.REJECTED -> Tag("Rejeitada", container = ext.negativeContainer, content = ext.onNegativeContainer)
                else -> if (linked && o.confidence < 0.8) {
                    Tag("Confira", container = ext.warningContainer, content = ext.onWarningContainer)
                }
            }
            if (u.inMyList) Tag("Na sua lista")
            if (u.note.isNotBlank()) Tag(u.note)
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            when (o.status) {
                OfferStatus.PENDING -> {
                    TextButton(onClick = onLink) {
                        Icon(if (linked) Icons.Default.Edit else Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text(if (linked) "Trocar" else "Vincular")
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onReject) {
                        Icon(Icons.Default.Close, contentDescription = "Rejeitar oferta", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalButton(onClick = onAccept, enabled = linked) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Aceitar")
                    }
                }
                OfferStatus.REJECTED -> {
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = onRestore) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Restaurar")
                    }
                }
                else -> {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onLink) { Text("Trocar produto") }
                }
            }
        }
    }
}
