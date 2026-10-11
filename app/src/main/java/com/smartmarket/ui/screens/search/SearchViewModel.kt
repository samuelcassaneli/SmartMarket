package com.smartmarket.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartmarket.data.dao.BestPrice
import com.smartmarket.data.dao.ProductDao
import com.smartmarket.data.dao.ProductPriceDao
import com.smartmarket.data.dao.PurchaseDao
import com.smartmarket.data.dao.ShoppingListDao
import com.smartmarket.data.dao.SupermarketDao
import com.smartmarket.data.entity.PriceSource
import com.smartmarket.data.entity.Product
import com.smartmarket.data.entity.ShoppingList
import com.smartmarket.data.entity.ShoppingListItem
import com.smartmarket.data.entity.Supermarket
import com.smartmarket.data.repo.PriceRepository
import com.smartmarket.domain.matching.FuzzyMatcher
import com.smartmarket.domain.matching.ProductMatcher
import com.smartmarket.domain.online.MarketBinding
import com.smartmarket.domain.online.MarketSourceRegistry
import com.smartmarket.domain.online.OnlineOffer
import com.smartmarket.domain.online.SourceException
import com.smartmarket.domain.online.SourceUnavailable
import com.smartmarket.domain.online.UnitPrice
import com.smartmarket.domain.online.UnitPriceCalculator
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.YearMonth
import com.smartmarket.ui.navigation.Routes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SearchFilter(val label: String) {
    ALL("Tudo"),
    LOCAL("Meus produtos"),
    ONLINE("Nos mercados")
}

data class LocalResult(val product: Product, val bestPrice: BestPrice?)

/** Oferta pronta para exibir: com o mercado e o preço por unidade já calculado. */
data class OfferUi(
    val offer: OnlineOffer,
    val market: Supermarket,
    val sourceName: String,
    val unitPrice: UnitPrice?
) {
    val key: String get() = "${market.id}|${offer.key}"
}

sealed interface SectionState {
    data object Idle : SectionState
    data object Loading : SectionState
    data class Success(val offers: List<OfferUi>) : SectionState
    data object Empty : SectionState
    data class Error(val message: String) : SectionState
    data class Unavailable(val message: String) : SectionState
}

data class MarketSectionUi(val binding: MarketBinding, val state: SectionState) {
    val marketId: Long get() = binding.market.id
}

enum class OfferAction { SAVE_PRICE, ADD_TO_LIST }

/** Estado do sheet de vínculo da oferta com um produto do app. */
data class LinkSheetState(
    val action: OfferAction,
    val offerUi: OfferUi,
    val suggested: Product?,
    val viaAlias: Boolean,
    val suggestions: List<Product>,
    val saving: Boolean = false
)

sealed interface ProductChoice {
    data class Existing(val product: Product) : ProductChoice
    data class CreateNew(val name: String) : ProductChoice
}

data class SearchEvent(val message: String, val actionLabel: String? = null, val route: String? = null)

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.ALL,
    val localLoading: Boolean = false,
    val localResults: List<LocalResult> = emptyList(),
    /** Termo da última busca online (null = ainda não buscou). */
    val onlineTerm: String? = null,
    val sections: List<MarketSectionUi> = emptyList(),
    val bestOfferKey: String? = null,
    val hasOnlineMarkets: Boolean = true,
    val history: List<String> = emptyList(),
    val suggestions: List<Product> = emptyList(),
    val sheet: LinkSheetState? = null
)

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val productDao: ProductDao,
    supermarketDao: SupermarketDao,
    productPriceDao: ProductPriceDao,
    private val shoppingListDao: ShoppingListDao,
    private val purchaseDao: PurchaseDao,
    private val priceRepository: PriceRepository,
    private val productMatcher: ProductMatcher,
    private val registry: MarketSourceRegistry,
    private val history: SearchHistoryStore
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState(history = runCatching { history.load() }.getOrDefault(emptyList())))
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _events = Channel<SearchEvent>(Channel.BUFFERED)
    val events: Flow<SearchEvent> = _events.receiveAsFlow()

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    /** Listas não arquivadas, mais recentes primeiro. */
    val openLists: StateFlow<List<ShoppingList>> = shoppingListDao.getAllLists()
        .map { lists -> lists.filter { !it.isArchived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val queryFlow = MutableStateFlow("")
    private var bindings: List<MarketBinding> = emptyList()
    private var bestPrices: Map<Long, BestPrice> = emptyMap()
    private val sectionJobs = mutableMapOf<Long, Job>()
    private var initialApplied = false

    init {
        viewModelScope.launch {
            supermarketDao.getAllSupermarkets().collect { markets ->
                bindings = registry.bind(markets)
                _state.update { s ->
                    val old = s.sections.associateBy { it.marketId }
                    s.copy(
                        hasOnlineMarkets = bindings.isNotEmpty(),
                        sections = bindings.map { b -> MarketSectionUi(b, old[b.market.id]?.state ?: SectionState.Idle) }
                    )
                }
            }
        }
        viewModelScope.launch {
            val ym = YearMonth.now()
            productPriceDao.getBestPricesForMonth(ym.month, ym.year).collect { list ->
                bestPrices = list.associateBy { it.productId }
                _state.update { s -> s.copy(localResults = s.localResults.map { it.copy(bestPrice = bestPrices[it.product.id]) }) }
            }
        }
        viewModelScope.launch {
            productDao.getAllProducts().collect { all ->
                _products.value = all
                runLocal(queryFlow.value)
                loadSuggestions(all)
            }
        }
        viewModelScope.launch {
            queryFlow.debounce(400).collectLatest { runLocal(it) }
        }
    }

    // ------------------------------------------------------------ Entrada

    fun applyInitialQuery(query: String) {
        if (initialApplied) return
        initialApplied = true
        if (query.isBlank()) return
        onQueryChange(query)
        searchOnline()
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query, localLoading = query.isNotBlank()) }
        queryFlow.value = query
    }

    fun setFilter(filter: SearchFilter) = _state.update { it.copy(filter = filter) }

    fun useHistory(term: String) {
        onQueryChange(term)
        searchOnline()
    }

    fun removeHistory(term: String) {
        val list = runCatching { history.remove(term) }.getOrDefault(_state.value.history)
        _state.update { it.copy(history = list) }
    }

    fun clearHistory() {
        val list = runCatching { history.clear() }.getOrDefault(emptyList())
        _state.update { it.copy(history = list) }
    }

    // ------------------------------------------------------------ Busca local

    private suspend fun runLocal(term: String) {
        if (term.isBlank()) {
            _state.update { it.copy(localResults = emptyList(), localLoading = false) }
            return
        }
        val all = _products.value
        val results = withContext(Dispatchers.Default) { searchLocal(term, all) }
        if (_state.value.query.trim() != term.trim() && _state.value.query.isNotBlank()) return // termo já mudou
        _state.update { s ->
            s.copy(localResults = results.map { LocalResult(it, bestPrices[it.id]) }, localLoading = false)
        }
    }

    private fun searchLocal(term: String, all: List<Product>): List<Product> {
        val nTerm = FuzzyMatcher.normalize(term)
        if (nTerm.isEmpty()) return emptyList()
        val tokens = nTerm.split(' ').filter { it.isNotBlank() }
        val contains = all.filter { p ->
            val n = FuzzyMatcher.normalize(p.normalizedName.ifBlank { p.name })
            n.contains(nTerm) || tokens.all { n.contains(it) }
        }
        val ranked = contains
            .map { it to FuzzyMatcher.calculateAffinity(term, it.name) }
            .sortedWith(compareByDescending<Pair<Product, Double>> { it.second }.thenBy { it.first.name.lowercase() })
            .map { it.first }
        if (ranked.size >= 3) return ranked
        // Poucos resultados por "contém": completa com o fuzzy (abreviações, erros de digitação)
        val extra = FuzzyMatcher.rank(term, all, { it.name }, limit = 5).map { it.item }
        return (ranked + extra).distinctBy { it.id }
    }

    private suspend fun loadSuggestions(all: List<Product>) {
        val byId = all.associateBy { it.id }
        val mostBought = runCatching {
            withContext(Dispatchers.IO) { purchaseDao.getAllItemsSync() }
                .mapNotNull { it.productId }
                .groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }
                .mapNotNull { byId[it.key] }
        }.getOrDefault(emptyList())
        val suggestions = (all.filter { it.isFavorite } + mostBought).distinctBy { it.id }.take(10)
        _state.update { it.copy(suggestions = suggestions) }
    }

    // ------------------------------------------------------------ Busca online

    /** Busca nos sites dos mercados (só sob ação do usuário: Enter ou botão). */
    fun searchOnline() {
        val term = _state.value.query.trim()
        if (term.isEmpty()) return
        val hist = runCatching { history.add(term) }.getOrDefault(SearchHistoryStore.merge(_state.value.history, term))
        _state.update { it.copy(onlineTerm = term, history = hist) }
        viewModelScope.launch { runLocal(term) }
        bindings.forEach { runSection(it, term, forceRefresh = false) }
    }

    fun retry(marketId: Long) {
        val term = _state.value.onlineTerm ?: return
        val binding = bindings.firstOrNull { it.market.id == marketId } ?: return
        runSection(binding, term, forceRefresh = true)
    }

    private fun runSection(binding: MarketBinding, term: String, forceRefresh: Boolean) {
        val marketId = binding.market.id
        sectionJobs[marketId]?.cancel()
        setSection(marketId, SectionState.Loading)
        sectionJobs[marketId] = viewModelScope.launch {
            val newState = try {
                val offers = registry.search(binding.source, term, 0, forceRefresh)
                if (offers.isEmpty()) SectionState.Empty
                else SectionState.Success(offers.map { o ->
                    OfferUi(
                        offer = o,
                        market = binding.market,
                        sourceName = binding.source.displayName,
                        unitPrice = UnitPriceCalculator.compute(o.name, o.price, o.unitMultiplier, o.measurementUnit)
                    )
                })
            } catch (e: CancellationException) {
                throw e
            } catch (e: SourceUnavailable) {
                SectionState.Unavailable(e.message ?: "Busca online indisponível")
            } catch (e: SourceException) {
                SectionState.Error(e.message ?: "Não foi possível buscar no site.")
            } catch (e: Exception) {
                SectionState.Error("Não foi possível buscar no site.")
            }
            setSection(marketId, newState)
        }
    }

    private fun setSection(marketId: Long, sectionState: SectionState) {
        _state.update { s ->
            val sections = s.sections.map { if (it.marketId == marketId) it.copy(state = sectionState) else it }
            s.copy(sections = sections, bestOfferKey = computeBest(sections))
        }
    }

    /** Oferta disponível de menor preço normalizado entre todos os mercados. */
    private fun computeBest(sections: List<MarketSectionUi>): String? {
        val offers = sections.flatMap { (it.state as? SectionState.Success)?.offers.orEmpty() }
            .filter { it.offer.available }
        val idx = UnitPriceCalculator.cheapestIndex(offers.map { it.offer.price to it.unitPrice }) ?: return null
        return offers[idx].key
    }

    // ------------------------------------------------------------ Salvar preço / adicionar à lista

    fun openSheet(offerUi: OfferUi, action: OfferAction) {
        viewModelScope.launch {
            val outcome = runCatching { productMatcher.match(offerUi.offer.name) }.getOrNull()
            _state.update {
                it.copy(
                    sheet = LinkSheetState(
                        action = action,
                        offerUi = offerUi,
                        suggested = outcome?.product,
                        viaAlias = outcome?.viaAlias == true,
                        suggestions = outcome?.suggestions?.map { r -> r.item }.orEmpty()
                    )
                )
            }
        }
    }

    fun dismissSheet() = _state.update { it.copy(sheet = null) }

    /** [listId] null cria a lista "Compras de <Mês>". */
    fun confirm(choice: ProductChoice, listId: Long?) {
        val sheet = _state.value.sheet ?: return
        if (sheet.saving) return
        _state.update { it.copy(sheet = sheet.copy(saving = true)) }
        val offer = sheet.offerUi.offer
        val market = sheet.offerUi.market
        viewModelScope.launch {
            try {
                val productId = resolveProduct(choice, offer)
                priceRepository.recordPrice(
                    productId = productId,
                    supermarketId = market.id,
                    price = offer.price,
                    source = PriceSource.ONLINE,
                    note = "Site: ${sheet.offerUi.sourceName}"
                )
                productMatcher.learn(offer.name, productId, PriceSource.ONLINE)

                val event = if (sheet.action == OfferAction.ADD_TO_LIST) {
                    val list = resolveList(listId)
                    val existing = shoppingListDao.findItem(list.id, productId)
                    if (existing != null) {
                        shoppingListDao.updateItemQuantity(existing.id, existing.quantity + 1.0)
                    } else {
                        shoppingListDao.insertItem(ShoppingListItem(shoppingListId = list.id, productId = productId, quantity = 1.0))
                    }
                    SearchEvent("Adicionado à lista “${list.name}”", actionLabel = "Ver", route = Routes.listDetail(list.id))
                } else {
                    SearchEvent("Preço salvo", actionLabel = "Ver produto", route = Routes.productDetail(productId))
                }
                _state.update { it.copy(sheet = null) }
                _events.send(event)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { s -> s.copy(sheet = s.sheet?.copy(saving = false)) }
                _events.send(SearchEvent("Não foi possível salvar. Tente de novo."))
            }
        }
    }

    private suspend fun resolveProduct(choice: ProductChoice, offer: OnlineOffer): Long = when (choice) {
        is ProductChoice.Existing -> {
            val p = choice.product
            // Aproveita foto e código de barras do site se o produto ainda não tem
            val enriched = p.copy(
                imageUrl = p.imageUrl.ifBlank { offer.imageUrl.orEmpty() },
                barcode = p.barcode.ifBlank { offer.ean.orEmpty() }
            )
            if (enriched != p) productDao.updateProduct(enriched)
            p.id
        }
        is ProductChoice.CreateNew -> {
            val name = choice.name.trim().ifBlank { offer.name }
            productDao.insertProduct(
                Product(
                    name = name,
                    unit = guessUnit(offer),
                    barcode = offer.ean.orEmpty(),
                    imageUrl = offer.imageUrl.orEmpty()
                )
            )
        }
    }

    private fun guessUnit(offer: OnlineOffer): String = when (offer.measurementUnit?.trim()?.lowercase()) {
        "kg" -> "kg"
        "g" -> "g"
        "l", "lt" -> "L"
        "ml" -> "ml"
        else -> "un"
    }

    private suspend fun resolveList(listId: Long?): ShoppingList {
        if (listId != null) shoppingListDao.getListById(listId)?.let { return it }
        val ym = YearMonth.now()
        val list = ShoppingList(name = newListName(), referenceMonth = ym.month, referenceYear = ym.year)
        val id = shoppingListDao.insertList(list)
        return list.copy(id = id)
    }

    companion object {
        fun newListName(): String = "Compras de ${Fmt.monthName(YearMonth.now().month)}"
    }
}
