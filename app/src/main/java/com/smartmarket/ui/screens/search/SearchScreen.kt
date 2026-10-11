package com.smartmarket.ui.screens.search

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.smartmarket.domain.online.MarketSourceRegistry
import com.smartmarket.ui.appViewModel
import com.smartmarket.ui.components.AppTopBar
import com.smartmarket.ui.components.EmptyState
import com.smartmarket.ui.components.ScreenScaffold
import com.smartmarket.ui.components.SearchField
import com.smartmarket.ui.components.SectionHeader
import com.smartmarket.ui.navigation.Routes
import com.smartmarket.ui.theme.Spacing

private const val LOCAL_PREVIEW = 4
private const val OFFERS_PREVIEW = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(initialQuery: String, navController: NavController) {
    val vm = appViewModel { c ->
        SearchViewModel(
            productDao = c.db.productDao(),
            supermarketDao = c.db.supermarketDao(),
            productPriceDao = c.db.productPriceDao(),
            shoppingListDao = c.db.shoppingListDao(),
            purchaseDao = c.db.purchaseDao(),
            priceRepository = c.priceRepository,
            productMatcher = c.productMatcher,
            registry = MarketSourceRegistry.get(c.httpClient, c.settings),
            history = SearchHistoryStore(c.context)
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val lists by vm.openLists.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val focusManager = LocalFocusManager.current
    var expandedMarkets by remember { mutableStateOf(listOf<Long>()) }

    LaunchedEffect(initialQuery) { vm.applyInitialQuery(initialQuery) }
    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            val result = snackbar.showSnackbar(
                message = ev.message,
                actionLabel = ev.actionLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed && ev.route != null) navController.navigate(ev.route)
        }
    }

    val searchOnline = {
        focusManager.clearFocus()
        expandedMarkets = emptyList()
        vm.searchOnline()
    }

    ScreenScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { AppTopBar(title = "Buscar", large = true, scrollBehavior = scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // ---------------------------------------------------------- Campo e filtros (fixos)
            Column(Modifier.padding(horizontal = Spacing.screen)) {
                SearchField(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    placeholder = "Buscar qualquer produto",
                    onSearch = searchOnline
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    SearchFilter.entries.forEach { f ->
                        val selected = state.filter == f
                        FilterChip(
                            selected = selected,
                            onClick = { vm.setFilter(f) },
                            label = { Text(f.label) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null
                        )
                    }
                }
                if (state.query.isNotBlank() && state.filter != SearchFilter.LOCAL && state.hasOnlineMarkets) {
                    FilledTonalButton(
                        onClick = searchOnline,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Buscar nos mercados")
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
            }

            // ---------------------------------------------------------- Resultados
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                if (state.query.isBlank()) {
                    emptyQueryContent(
                        history = state.history,
                        suggestions = state.suggestions,
                        onHistory = { term -> focusManager.clearFocus(); expandedMarkets = emptyList(); vm.useHistory(term) },
                        onRemoveHistory = vm::removeHistory,
                        onClearHistory = vm::clearHistory,
                        onSuggestion = { name -> vm.onQueryChange(name) }
                    )
                } else {
                    // ------------------------------------------------ Meus produtos
                    if (state.filter != SearchFilter.ONLINE) {
                        val showAll = state.filter == SearchFilter.LOCAL
                        val visible = if (showAll) state.localResults else state.localResults.take(LOCAL_PREVIEW)
                        item(key = "local-header") {
                            SectionHeader(
                                title = "Meus produtos",
                                actionLabel = if (!showAll && state.localResults.size > LOCAL_PREVIEW) "Ver todos (${state.localResults.size})" else null,
                                onAction = { vm.setFilter(SearchFilter.LOCAL) }
                            )
                        }
                        if (visible.isEmpty()) {
                            item(key = "local-empty") {
                                Text(
                                    if (state.localLoading) "Procurando…" else "Nenhum produto seu com esse nome.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(visible, key = { "local-${it.product.id}" }) { r ->
                            LocalProductRow(r, onClick = { navController.navigate(Routes.productDetail(r.product.id)) })
                        }
                    }

                    // ------------------------------------------------ Nos mercados
                    if (state.filter != SearchFilter.LOCAL) {
                        item(key = "online-header") { SectionHeader(title = "Nos mercados") }
                        val term = state.query.trim()
                        when {
                            !state.hasOnlineMarkets -> item(key = "online-none") {
                                SectionUnavailableMessage(
                                    "Nenhum mercado com busca online. Cadastre o Atacadão ou o Spani em Mercados."
                                )
                            }
                            state.onlineTerm != term -> item(key = "online-prompt") {
                                PromptCard(
                                    title = "Preços nos sites dos mercados",
                                    message = "Busque “$term” no Atacadão e no Spani para comparar com os seus preços.",
                                    actionLabel = "Buscar nos mercados",
                                    onAction = searchOnline
                                )
                            }
                            else -> state.sections.forEach { section ->
                                val marketId = section.marketId
                                item(key = "sec-$marketId") { MarketSectionHeader(section) }
                                when (val s = section.state) {
                                    SectionState.Idle, SectionState.Loading -> {
                                        items(2, key = { "shimmer-$marketId-$it" }) { ShimmerOfferCard() }
                                    }
                                    is SectionState.Error -> item(key = "err-$marketId") {
                                        SectionErrorMessage(s.message) { vm.retry(marketId) }
                                    }
                                    is SectionState.Unavailable -> item(key = "unav-$marketId") {
                                        SectionUnavailableMessage(s.message)
                                    }
                                    SectionState.Empty -> item(key = "empty-$marketId") {
                                        SectionEmptyMessage(term)
                                    }
                                    is SectionState.Success -> {
                                        val expanded = marketId in expandedMarkets
                                        val shown = if (expanded) s.offers else s.offers.take(OFFERS_PREVIEW)
                                        itemsIndexed(shown, key = { i, _ -> "offer-$marketId-$i" }) { _, offerUi ->
                                            OfferCard(
                                                item = offerUi,
                                                isBest = offerUi.key == state.bestOfferKey,
                                                onSavePrice = { vm.openSheet(offerUi, OfferAction.SAVE_PRICE) },
                                                onAddToList = { vm.openSheet(offerUi, OfferAction.ADD_TO_LIST) }
                                            )
                                        }
                                        if (!expanded && s.offers.size > OFFERS_PREVIEW) {
                                            item(key = "more-$marketId") {
                                                TextButton(
                                                    onClick = { expandedMarkets = expandedMarkets + marketId },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("Mostrar mais ${s.offers.size - OFFERS_PREVIEW} do ${section.binding.market.name}")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.sheet?.let { sheet ->
        LinkOfferSheet(
            sheet = sheet,
            allProducts = products,
            lists = lists,
            onDismiss = vm::dismissSheet,
            onConfirm = { choice, listId -> vm.confirm(choice, listId) }
        )
    }
}

/** Campo vazio: buscas recentes e sugestões (favoritos e mais comprados). */
private fun androidx.compose.foundation.lazy.LazyListScope.emptyQueryContent(
    history: List<String>,
    suggestions: List<com.smartmarket.data.entity.Product>,
    onHistory: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onSuggestion: (String) -> Unit
) {
    if (history.isEmpty() && suggestions.isEmpty()) {
        item(key = "intro") {
            EmptyState(
                icon = Icons.Default.Search,
                title = "Pesquise qualquer produto",
                message = "Veja os seus preços e busque nos sites do Atacadão e do Spani, mesmo o que não está no encarte."
            )
        }
        return
    }
    if (history.isNotEmpty()) {
        item(key = "history-header") {
            SectionHeader(title = "Buscas recentes", actionLabel = "Limpar", onAction = onClearHistory)
        }
        items(history, key = { "h-$it" }) { term ->
            HistoryRow(term = term, onClick = { onHistory(term) }, onRemove = { onRemoveHistory(term) })
        }
    }
    if (suggestions.isNotEmpty()) {
        item(key = "sugg-header") { SectionHeader(title = "Sugestões") }
        item(key = "sugg-row") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(suggestions, key = { "s-${it.id}" }) { p ->
                    SuggestionChip(
                        onClick = { onSuggestion(p.name) },
                        label = { Text(p.name, maxLines = 1) },
                        icon = if (p.isFavorite) {
                            { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                }
            }
        }
    }
}
