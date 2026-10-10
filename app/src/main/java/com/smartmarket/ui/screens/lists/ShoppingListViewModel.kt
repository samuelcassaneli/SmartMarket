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
import com.smartmarket.domain.comparator.IntelligentPriceComparator
import com.smartmarket.domain.comparator.MonthComparisonSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class ShoppingListViewModel(
    private val listDao: ShoppingListDao,
    private val productDao: ProductDao,
    private val supermarketDao: SupermarketDao,
    private val priceDao: ProductPriceDao
) : ViewModel() {

    // Todas as listas registradas no app
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

    // Mês anterior de comparação (configurável para comparar com qualquer mês do histórico)
    private val _compareMonth = MutableStateFlow(
        if (Calendar.getInstance().get(Calendar.MONTH) == 0) 12 else Calendar.getInstance().get(Calendar.MONTH)
    )
    val compareMonth = _compareMonth.asStateFlow()

    private val _compareYear = MutableStateFlow(
        if (Calendar.getInstance().get(Calendar.MONTH) == 0) Calendar.getInstance().get(Calendar.YEAR) - 1 else Calendar.getInstance().get(Calendar.YEAR)
    )
    val compareYear = _compareYear.asStateFlow()

    private val _currentListItems = MutableStateFlow<List<ShoppingItemWithProduct>>(emptyList())
    val currentListItems = _currentListItems.asStateFlow()

    private val _comparison = MutableStateFlow<MonthComparisonSummary?>(null)
    val comparison = _comparison.asStateFlow()

    fun selectList(listId: Long) {
        _selectedListId.value = listId
        viewModelScope.launch {
            val list = listDao.getListById(listId)
            list?.let {
                _selectedMonth.value = it.referenceMonth
                _selectedYear.value = it.referenceYear
                // Atualiza mês anterior de referência
                val prevM = if (it.referenceMonth == 1) 12 else it.referenceMonth - 1
                val prevY = if (it.referenceMonth == 1) it.referenceYear - 1 else it.referenceYear
                _compareMonth.value = prevM
                _compareYear.value = prevY
            }
            listDao.getItemsForList(listId).collect { items ->
                _currentListItems.value = items
                refreshComparison()
            }
        }
    }

    fun setCompareMonthYear(month: Int, year: Int) {
        _compareMonth.value = month
        _compareYear.value = year
        refreshComparison()
    }

    fun refreshComparison() {
        val listId = _selectedListId.value ?: return
        viewModelScope.launch {
            val list = listDao.getListById(listId) ?: return@launch
            val items = _currentListItems.value
            val supermarkets = supermarketDao.getSupermarketById(1) // apenas para trigger
            val allMarkets = supermarketDao.getAllSupermarkets()
            val m = _selectedMonth.value
            val y = _selectedYear.value
            val prevM = _compareMonth.value
            val prevY = _compareYear.value

            allMarkets.collect { markets ->
                val currentPrices = priceDao.getPricesForMonthSync(m, y)
                val prevPrices = priceDao.getPricesForMonthSync(prevM, prevY)

                val summary = IntelligentPriceComparator.compareWithPreviousMonth(
                    items = items,
                    supermarkets = markets,
                    currentPrices = currentPrices,
                    previousPrices = prevPrices,
                    currentMonth = m,
                    currentYear = y,
                    previousMonth = prevM,
                    previousYear = prevY,
                    listName = list.name
                )
                _comparison.value = summary
            }
        }
    }

    fun createList(name: String, month: Int, year: Int, budget: Double, copyDefaultMonthlyProducts: Boolean) {
        viewModelScope.launch {
            val id = listDao.insertList(
                ShoppingList(
                    name = name,
                    referenceMonth = month,
                    referenceYear = year,
                    budgetTarget = budget
                )
            )

            // Se solicitado, popula automaticamente com a quantidade média mensal dos produtos cadastrados
            if (copyDefaultMonthlyProducts) {
                val prods = productDao.searchProducts("")
                val itemsToInsert = prods.filter { it.defaultMonthlyQuantity > 0 }.map { p ->
                    ShoppingListItem(
                        shoppingListId = id,
                        productId = p.id,
                        quantity = p.defaultMonthlyQuantity
                    )
                }
                listDao.insertItems(itemsToInsert)
            }

            selectList(id)
        }
    }

    fun duplicateListToMonth(sourceListId: Long, newName: String, targetMonth: Int, targetYear: Int) {
        viewModelScope.launch {
            val sourceItems = listDao.getItemsForListSync(sourceListId)
            val newId = listDao.insertList(
                ShoppingList(
                    name = newName,
                    referenceMonth = targetMonth,
                    referenceYear = targetYear
                )
            )
            val clonedItems = sourceItems.map {
                ShoppingListItem(
                    shoppingListId = newId,
                    productId = it.productId,
                    quantity = it.quantity,
                    isChecked = false
                )
            }
            listDao.insertItems(clonedItems)
            selectList(newId)
        }
    }

    fun deleteList(list: ShoppingList) {
        viewModelScope.launch {
            listDao.deleteList(list)
            if (_selectedListId.value == list.id) {
                _selectedListId.value = null
                _currentListItems.value = emptyList()
                _comparison.value = null
            }
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

    fun updateItemQuantity(itemId: Long, newQuantity: Double) {
        val listId = _selectedListId.value ?: return
        viewModelScope.launch {
            val item = _currentListItems.value.find { it.itemId == itemId } ?: return@launch
            listDao.updateItem(
                ShoppingListItem(
                    id = itemId,
                    shoppingListId = listId,
                    productId = item.productId,
                    quantity = newQuantity,
                    isChecked = item.isChecked
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
