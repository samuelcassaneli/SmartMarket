package com.smartmarket.ui.screens.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartmarket.data.dao.ListSummary
import com.smartmarket.data.dao.ProductDao
import com.smartmarket.data.dao.ProductPriceDao
import com.smartmarket.data.dao.ShoppingListDao
import com.smartmarket.data.dao.SupermarketDao
import com.smartmarket.data.entity.ShoppingList
import com.smartmarket.data.entity.ShoppingListItem
import com.smartmarket.domain.comparator.IntelligentPriceComparator
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Cartão de lista com o total estimado no mercado mais barato do mês da lista. */
data class ListCardUi(
    val summary: ListSummary,
    val estimatedTotal: Double?,
    val cheapestMarketName: String?,
    val pricedItems: Int
)

data class ListsUiState(
    val loading: Boolean = true,
    val active: List<ListCardUi> = emptyList(),
    val archived: List<ListCardUi> = emptyList()
) {
    val all: List<ListCardUi> get() = active + archived
}

enum class ListStart { EMPTY, COPY, MONTHLY }

@OptIn(ExperimentalCoroutinesApi::class)
class ListsViewModel(
    private val listDao: ShoppingListDao,
    private val productDao: ProductDao,
    private val supermarketDao: SupermarketDao,
    private val priceDao: ProductPriceDao
) : ViewModel() {

    val state: StateFlow<ListsUiState> = combine(
        listDao.getListSummaries(),
        supermarketDao.getAllSupermarkets(),
        // Gatilho: preços do mês atual mudaram (as outras consultas são feitas sob demanda)
        priceDao.getPricesForMonth(YearMonth.now().month, YearMonth.now().year)
    ) { summaries, markets, _ -> summaries to markets }
        .mapLatest { (summaries, markets) ->
            val marketNames = markets.associate { it.id to it.name }
            val itemsByList = listDao.getAllItemsSync().groupBy { it.shoppingListId }
            val pricesByMonth = summaries.map { it.referenceMonth to it.referenceYear }.distinct()
                .associateWith { (m, y) -> priceDao.getPricesForMonthSync(m, y).filter { it.supermarketId in marketNames } }
            val cards = summaries.map { s ->
                val quantities = itemsByList[s.id].orEmpty()
                    .groupBy { it.productId }
                    .mapValues { (_, list) -> list.sumOf { it.quantity } }
                val best = IntelligentPriceComparator.bestMarketTotal(
                    quantities, pricesByMonth[s.referenceMonth to s.referenceYear].orEmpty()
                )
                ListCardUi(
                    summary = s,
                    estimatedTotal = best?.total,
                    cheapestMarketName = best?.let { marketNames[it.supermarketId] },
                    pricedItems = best?.covered ?: 0
                )
            }
            ListsUiState(
                loading = false,
                active = cards.filter { !it.summary.isArchived },
                archived = cards.filter { it.summary.isArchived }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListsUiState())

    fun createList(
        name: String,
        month: YearMonth,
        budget: Double,
        start: ListStart,
        copyFromListId: Long?,
        onCreated: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val id = listDao.insertList(
                ShoppingList(
                    name = name.trim().ifBlank { "Compras de ${Fmt.monthName(month.month)}" },
                    referenceMonth = month.month,
                    referenceYear = month.year,
                    budgetTarget = budget
                )
            )
            val items = when (start) {
                ListStart.EMPTY -> emptyList()
                ListStart.COPY -> copyFromListId?.let { src ->
                    listDao.getItemsForListSync(src).map {
                        ShoppingListItem(shoppingListId = id, productId = it.productId, quantity = it.quantity)
                    }
                }.orEmpty()
                ListStart.MONTHLY -> productDao.getAllProductsSync()
                    .filter { it.defaultMonthlyQuantity > 0 }
                    .map { ShoppingListItem(shoppingListId = id, productId = it.id, quantity = it.defaultMonthlyQuantity) }
            }
            if (items.isNotEmpty()) listDao.insertItems(items)
            onCreated(id)
        }
    }

    fun rename(listId: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val list = listDao.getListById(listId) ?: return@launch
            listDao.updateList(list.copy(name = name.trim()))
        }
    }

    fun setArchived(listId: Long, archived: Boolean) {
        viewModelScope.launch {
            val list = listDao.getListById(listId) ?: return@launch
            listDao.updateList(list.copy(isArchived = archived))
        }
    }

    /** Duplica a lista para o mês seguinte (itens desmarcados). */
    fun duplicateToNextMonth(listId: Long, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val list = listDao.getListById(listId) ?: return@launch
            val next = YearMonth(list.referenceMonth, list.referenceYear).next()
            val oldMonthName = Fmt.monthName(list.referenceMonth)
            val newName = if (list.name.contains(oldMonthName, ignoreCase = true)) {
                list.name.replace(oldMonthName, Fmt.monthName(next.month), ignoreCase = true)
            } else list.name
            val newId = listDao.insertList(
                ShoppingList(
                    name = newName,
                    referenceMonth = next.month,
                    referenceYear = next.year,
                    budgetTarget = list.budgetTarget,
                    notes = list.notes
                )
            )
            val items = listDao.getItemsForListSync(listId).map {
                ShoppingListItem(shoppingListId = newId, productId = it.productId, quantity = it.quantity)
            }
            if (items.isNotEmpty()) listDao.insertItems(items)
            onCreated(newId)
        }
    }

    fun delete(listId: Long) {
        viewModelScope.launch {
            val list = listDao.getListById(listId) ?: return@launch
            listDao.deleteList(list)
        }
    }
}
