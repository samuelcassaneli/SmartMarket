package com.smartmarket.ui.screens.markets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartmarket.data.dao.ProductDao
import com.smartmarket.data.dao.ProductPriceDao
import com.smartmarket.data.dao.SupermarketDao
import com.smartmarket.data.entity.PriceSource
import com.smartmarket.data.entity.Product
import com.smartmarket.data.entity.Supermarket
import com.smartmarket.data.repo.PriceRepository
import com.smartmarket.domain.matching.FuzzyMatcher
import com.smartmarket.ui.components.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MarketsUiState(
    val loading: Boolean = true,
    val markets: List<Supermarket> = emptyList(),
    /** mercado -> quantidade de preços no mês atual */
    val priceCounts: Map<Long, Int> = emptyMap(),
    val month: YearMonth = YearMonth.now()
)

class MarketsViewModel(
    private val supermarketDao: SupermarketDao,
    private val priceDao: ProductPriceDao
) : ViewModel() {

    private val month = YearMonth.now()

    val state: StateFlow<MarketsUiState> = combine(
        supermarketDao.getAllSupermarkets(),
        priceDao.getPricesForMonth(month.month, month.year)
    ) { markets, prices ->
        MarketsUiState(
            loading = false,
            markets = markets,
            priceCounts = prices.groupingBy { it.supermarketId }.eachCount(),
            month = month
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MarketsUiState())

    fun save(market: Supermarket) {
        viewModelScope.launch {
            if (market.id == 0L) supermarketDao.insertSupermarket(market) else supermarketDao.updateSupermarket(market)
        }
    }

    fun toggleFavorite(market: Supermarket) {
        viewModelScope.launch { supermarketDao.updateSupermarket(market.copy(isFavorite = !market.isFavorite)) }
    }

    /** Exclui o mercado e os preços dele. */
    fun delete(market: Supermarket) {
        viewModelScope.launch {
            priceDao.deletePricesBySupermarket(market.id)
            supermarketDao.deleteSupermarket(market)
        }
    }
}

// ---------------------------------------------------------------- Tabela de preços

data class PriceRowUi(
    val product: Product,
    val current: Double?,
    val previous: Double?
) {
    val changePercent: Double?
        get() = if (current != null && previous != null && previous > 0) (current - previous) / previous * 100.0 else null
}

data class PriceTableUi(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val query: String = "",
    val rows: List<PriceRowUi> = emptyList(),
    val filledCount: Int = 0,
    val totalProducts: Int = 0,
    /** Produtos sem preço no mês mas com preço no mês anterior */
    val copyableCount: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class PriceTableViewModel(
    private val marketId: Long,
    productDao: ProductDao,
    private val priceDao: ProductPriceDao,
    private val priceRepository: PriceRepository
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val query = MutableStateFlow("")

    private val pricesFlow = month.flatMapLatest { ym ->
        val prev = ym.previous()
        combine(
            priceDao.getPricesForMonth(ym.month, ym.year),
            priceDao.getPricesForMonth(prev.month, prev.year)
        ) { cur, old ->
            Triple(
                ym,
                cur.filter { it.supermarketId == marketId }.associate { it.productId to it.price },
                old.filter { it.supermarketId == marketId }.associate { it.productId to it.price }
            )
        }
    }

    val state: StateFlow<PriceTableUi> = combine(productDao.getAllProducts(), pricesFlow, query) { products, prices, q ->
        val (ym, cur, old) = prices
        val nq = FuzzyMatcher.normalize(q)
        val all = products.map { PriceRowUi(it, cur[it.id], old[it.id]) }
        val rows = all
            .filter { nq.isEmpty() || FuzzyMatcher.normalize(it.product.name).contains(nq) }
            .sortedWith(compareBy<PriceRowUi> { it.product.category.lowercase() }.thenBy { it.product.name.lowercase() })
        PriceTableUi(
            loading = false,
            month = ym,
            query = q,
            rows = rows,
            filledCount = all.count { it.current != null },
            totalProducts = all.size,
            copyableCount = all.count { it.current == null && it.previous != null }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PriceTableUi())

    fun setMonth(ym: YearMonth) { month.value = ym }
    fun setQuery(q: String) { query.value = q }

    /** Grava (ou apaga, se [price] for null) o preço do produto no mês selecionado. */
    fun setPrice(productId: Long, price: Double?) {
        val ym = month.value
        viewModelScope.launch {
            if (price == null) {
                priceDao.getPrice(productId, marketId, ym.month, ym.year)?.let { priceDao.deletePriceById(it.id) }
            } else {
                priceRepository.setMonthlyPrice(productId, marketId, price, ym.month, ym.year, PriceSource.MANUAL)
            }
        }
    }

    /** Copia os preços do mês anterior para os produtos ainda sem preço. */
    fun copyPreviousMonth(onDone: (Int) -> Unit) {
        val ym = month.value
        // Vale para todos os produtos, inclusive os fora do filtro de busca
        viewModelScope.launch {
            val prev = ym.previous()
            val current = priceDao.getPricesForMonthSync(ym.month, ym.year)
                .filter { it.supermarketId == marketId }.map { it.productId }.toSet()
            val previous = priceDao.getPricesForMonthSync(prev.month, prev.year)
                .filter { it.supermarketId == marketId && it.productId !in current }
            previous.forEach {
                priceRepository.setMonthlyPrice(it.productId, marketId, it.price, ym.month, ym.year, PriceSource.MANUAL)
            }
            onDone(previous.size)
        }
    }
}
