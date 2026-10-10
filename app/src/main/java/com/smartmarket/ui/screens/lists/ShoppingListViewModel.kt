package com.smartmarket.ui.screens.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smartmarket.data.dao.ProductDao
import com.smartmarket.data.dao.ProductPriceDao
import com.smartmarket.data.dao.ShoppingItemWithProduct
import com.smartmarket.data.dao.ShoppingListDao
import com.smartmarket.data.dao.SupermarketDao
import com.smartmarket.data.entity.Product
import com.smartmarket.data.entity.ShoppingList
import com.smartmarket.data.entity.ShoppingListItem
import com.smartmarket.domain.comparator.ComparisonSummary
import com.smartmarket.domain.comparator.PriceComparator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class ShoppingListViewModel(
    private val listDao: ShoppingListDao,
    private val productDao: ProductDao,
    private val supermarketDao: SupermarketDao,
    private val priceDao: ProductPriceDao
) : ViewModel() {

    val allLists: StateFlow<List<ShoppingList>> = listDao.getAllLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allProducts: StateFlow<List<Product>> = productDao.getAllProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedListId = MutableStateFlow<Long?>(null)
    val selectedListId = _selectedListId.asStateFlow()

    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH) + 1)
    val selectedMonth = _selectedMonth.asStateFlow()

    private val _selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val selectedYear = _selectedYear.asStateFlow()

    private val _currentListItems = MutableStateFlow<List<ShoppingItemWithProduct>>(emptyList())
    val currentListItems = _currentListItems.asStateFlow()

    private val _comparison = MutableStateFlow<ComparisonSummary?>(null)
    val comparison = _comparison.asStateFlow()

    fun selectList(listId: Long) {
        _selectedListId.value = listId
        viewModelScope.launch {
            val list = listDao.getListById(listId)
            list?.let {
                _selectedMonth.value = it.referenceMonth
                _selectedYear.value = it.referenceYear
            }
            listDao.getItemsForList(listId).collect { items ->
                _currentListItems.value = items
                refreshComparison()
            }
        }
    }

    fun setMonthYear(month: Int, year: Int) {
        _selectedMonth.value = month
        _selectedYear.value = year
        refreshComparison()
    }

    fun refreshComparison() {
        val listId = _selectedListId.value ?: return
        viewModelScope.launch {
            val list = listDao.getListById(listId) ?: return@launch
            val items = _currentListItems.value
            val supermarkets = supermarketDao.getAllSupermarkets()
            val m = _selectedMonth.value
            val y = _selectedYear.value

            combine(
                supermarketDao.getAllSupermarkets(),
                priceDao.getPricesForMonth(m, y)
            ) { markets, prices ->
                PriceComparator.compare(
                    items = items,
                    supermarkets = markets,
                    prices = prices,
                    month = m,
                    year = y,
                    listName = list.name
                )
            }.collect { summary ->
                _comparison.value = summary
            }
        }
    }

    fun createList(name: String, month: Int, year: Int) {
        viewModelScope.launch {
            val id = listDao.insertList(
                ShoppingList(
                    name = name,
                    referenceMonth = month,
                    referenceYear = year
                )
            )
            selectList(id)
        }
    }

    fun addItemToList(productId: Long, quantity: Double) {
        val listId = _selectedListId.value ?: return
        viewModelScope.launch {
            listDao.insertItem(
                ShoppingListItem(
                    shoppingListId = listId,
                    productId = productId,
                    quantity = quantity
                )
            )
        }
    }

    fun toggleCheck(itemId: Long, currentCheck: Boolean) {
        viewModelScope.launch {
            listDao.toggleItemCheck(itemId, !currentCheck)
        }
    }

    fun removeItem(itemId: Long) {
        viewModelScope.launch {
            listDao.deleteItem(itemId)
        }
    }
}

class ShoppingListViewModelFactory(
    private val listDao: ShoppingListDao,
    private val productDao: ProductDao,
    private val supermarketDao: SupermarketDao,
    private val priceDao: ProductPriceDao
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ShoppingListViewModel(listDao, productDao, supermarketDao, priceDao) as T
    }
}
