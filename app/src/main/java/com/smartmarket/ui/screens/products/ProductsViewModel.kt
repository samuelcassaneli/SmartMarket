package com.smartmarket.ui.screens.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartmarket.data.dao.BestPrice
import com.smartmarket.data.dao.ProductDao
import com.smartmarket.data.dao.ProductPriceDao
import com.smartmarket.data.entity.Product
import com.smartmarket.domain.matching.FuzzyMatcher
import com.smartmarket.ui.components.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ProductSort { NAME, PRICE }

data class ProductRowUi(val product: Product, val best: BestPrice?)

data class ProductsUiState(
    val loading: Boolean = true,
    val rows: List<ProductRowUi> = emptyList(),
    val totalCount: Int = 0,
    val categories: List<String> = emptyList(),
    val query: String = "",
    val category: String? = null,
    val sort: ProductSort = ProductSort.NAME
)

private data class Filters(val query: String, val category: String?, val sort: ProductSort)

class ProductsViewModel(
    private val productDao: ProductDao,
    priceDao: ProductPriceDao
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<String?>(null)
    private val sort = MutableStateFlow(ProductSort.NAME)
    private val month = YearMonth.now()

    private val filters = combine(query, category, sort) { q, c, s -> Filters(q, c, s) }

    val state: StateFlow<ProductsUiState> = combine(
        productDao.getAllProducts(),
        productDao.getAllCategories(),
        priceDao.getBestPricesForMonth(month.month, month.year),
        filters
    ) { products, categories, best, f ->
        val bestById = best.associateBy { it.productId }
        val nq = FuzzyMatcher.normalize(f.query)
        val filtered = products
            .filter { f.category == null || it.category == f.category }
            .filter { nq.isEmpty() || FuzzyMatcher.normalize(it.name).contains(nq) || it.barcode == f.query.trim() }
            .map { ProductRowUi(it, bestById[it.id]) }
        val sorted = when (f.sort) {
            ProductSort.NAME -> filtered.sortedBy { it.product.name.lowercase() }
            ProductSort.PRICE -> filtered.sortedWith(
                compareBy<ProductRowUi> { it.best == null }.thenBy { it.best?.price ?: 0.0 }.thenBy { it.product.name.lowercase() }
            )
        }
        ProductsUiState(
            loading = false,
            rows = sorted,
            totalCount = products.size,
            categories = categories.filter { it.isNotBlank() },
            query = f.query,
            category = f.category,
            sort = f.sort
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProductsUiState())

    fun setQuery(q: String) { query.value = q }
    fun setCategory(c: String?) { category.value = c }
    fun setSort(s: ProductSort) { sort.value = s }

    fun create(product: Product, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = productDao.insertProduct(product)
            onCreated(id)
        }
    }
}
