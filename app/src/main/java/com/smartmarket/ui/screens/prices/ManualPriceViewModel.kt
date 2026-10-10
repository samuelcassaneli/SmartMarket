package com.smartmarket.ui.screens.prices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smartmarket.data.dao.ProductDao
import com.smartmarket.data.dao.ProductPriceDao
import com.smartmarket.data.dao.SupermarketDao
import com.smartmarket.data.entity.Product
import com.smartmarket.data.entity.ProductPrice
import com.smartmarket.data.entity.Supermarket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class ProductPriceRowItem(
    val product: Product,
    val currentPrice: Double?,
    val lastUpdated: Long?
)

class ManualPriceViewModel(
    private val productDao: ProductDao,
    private val supermarketDao: SupermarketDao,
    private val priceDao: ProductPriceDao
) : ViewModel() {

    val supermarkets: StateFlow<List<Supermarket>> = supermarketDao.getAllSupermarkets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<Product>> = productDao.getAllProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSupermarketId = MutableStateFlow<Long?>(null)
    val selectedSupermarketId = _selectedSupermarketId.asStateFlow()

    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH) + 1)
    val selectedMonth = _selectedMonth.asStateFlow()

    private val _selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val selectedYear = _selectedYear.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Todos")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _monthPrices = MutableStateFlow<List<ProductPrice>>(emptyList())
    val monthPrices = _monthPrices.asStateFlow()

    fun selectSupermarket(marketId: Long) {
        _selectedSupermarketId.value = marketId
        refreshPrices()
    }

    fun setMonthYear(month: Int, year: Int) {
        _selectedMonth.value = month
        _selectedYear.value = year
        refreshPrices()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(cat: String) {
        _selectedCategory.value = cat
    }

    fun refreshPrices() {
        viewModelScope.launch {
            val m = _selectedMonth.value
            val y = _selectedYear.value
            priceDao.getPricesForMonth(m, y).collect { list ->
                _monthPrices.value = list
            }
        }
    }

    fun updatePrice(productId: Long, price: Double) {
        val marketId = _selectedSupermarketId.value ?: return
        val m = _selectedMonth.value
        val y = _selectedYear.value
        viewModelScope.launch {
            priceDao.insertPrice(
                ProductPrice(
                    productId = productId,
                    supermarketId = marketId,
                    price = price,
                    referenceMonth = m,
                    referenceYear = y,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
}

class ManualPriceViewModelFactory(
    private val productDao: ProductDao,
    private val supermarketDao: SupermarketDao,
    private val priceDao: ProductPriceDao
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ManualPriceViewModel(productDao, supermarketDao, priceDao) as T
    }
}
