package com.smartmarket.ui.screens.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartmarket.data.dao.ProductDao
import com.smartmarket.data.dao.ProductPriceDao
import com.smartmarket.data.dao.ShoppingItemWithProduct
import com.smartmarket.data.dao.ShoppingListDao
import com.smartmarket.data.dao.SupermarketDao
import com.smartmarket.data.entity.Product
import com.smartmarket.data.entity.ShoppingList
import com.smartmarket.data.entity.ShoppingListItem
import com.smartmarket.data.entity.Supermarket
import com.smartmarket.domain.comparator.IntelligentPriceComparator
import com.smartmarket.domain.comparator.MonthComparisonSummary
import com.smartmarket.domain.share.ListShareFormatter
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ListDetailUi(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val list: ShoppingList? = null,
    val items: List<ShoppingItemWithProduct> = emptyList(),
    val markets: List<Supermarket> = emptyList(),
    val refMonth: YearMonth = YearMonth.now(),
    val summary: MonthComparisonSummary? = null,
    /** mercado -> (produto -> preço unitário) no mês de referência */
    val priceTable: Map<Long, Map<Long, Double>> = emptyMap(),
    val marketMode: Boolean = false,
    val hideChecked: Boolean = true,
    val shoppingMarketId: Long? = null
) {
    val shoppingMarket: Supermarket? get() = markets.firstOrNull { it.id == shoppingMarketId }

    /** Preço do item no mercado onde o usuário está comprando. */
    fun priceAtShoppingMarket(productId: Long): Double? = shoppingMarketId?.let { priceTable[it]?.get(productId) }

    /** Total corrente dos itens marcados no mercado escolhido. */
    val cartTotal: Double
        get() = items.filter { it.isChecked }.sumOf { (priceAtShoppingMarket(it.productId) ?: 0.0) * it.quantity }

    val cartMissingPrices: Int
        get() = items.count { it.isChecked && priceAtShoppingMarket(it.productId) == null }

    val remainingTotal: Double
        get() = items.filter { !it.isChecked }.sumOf { (priceAtShoppingMarket(it.productId) ?: 0.0) * it.quantity }
}

private data class Flags(val marketMode: Boolean, val hideChecked: Boolean, val shoppingMarketId: Long?)

@OptIn(ExperimentalCoroutinesApi::class)
class ListDetailViewModel(
    private val listId: Long,
    private val listDao: ShoppingListDao,
    private val productDao: ProductDao,
    supermarketDao: SupermarketDao,
    priceDao: ProductPriceDao
) : ViewModel() {

    private val refOverride = MutableStateFlow<YearMonth?>(null)
    private val marketMode = MutableStateFlow(false)
    private val hideChecked = MutableStateFlow(true)
    private val shoppingMarketId = MutableStateFlow<Long?>(null)

    private val listFlow = listDao.observeList(listId)

    private val refMonthFlow = combine(listFlow, refOverride) { list, override ->
        override ?: list?.let { YearMonth(it.referenceMonth, it.referenceYear) } ?: YearMonth.now()
    }.distinctUntilChanged()

    private val pricesFlow = refMonthFlow.flatMapLatest { ym ->
        val prev = ym.previous()
        combine(
            priceDao.getPricesForMonth(ym.month, ym.year),
            priceDao.getPricesForMonth(prev.month, prev.year)
        ) { cur, old -> Triple(ym, cur, old) }
    }

    private val flags = combine(marketMode, hideChecked, shoppingMarketId) { m, h, s -> Flags(m, h, s) }

    val products: StateFlow<List<Product>> = productDao.getAllProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<String>> = productDao.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val state: StateFlow<ListDetailUi> = combine(
        listFlow,
        listDao.getItemsForList(listId),
        supermarketDao.getAllSupermarkets(),
        pricesFlow,
        flags
    ) { list, items, markets, prices, f ->
        if (list == null) {
            return@combine ListDetailUi(loading = false, notFound = true)
        }
        val (ym, cur, old) = prices
        val prev = ym.previous()
        val summary = IntelligentPriceComparator.compareWithPreviousMonth(
            items = items,
            supermarkets = markets,
            currentPrices = cur,
            previousPrices = old,
            currentMonth = ym.month,
            currentYear = ym.year,
            previousMonth = prev.month,
            previousYear = prev.year,
            listName = list.name
        )
        val table = IntelligentPriceComparator.buildPriceTable(cur, markets.map { it.id }.toSet())
        // Mercado padrão do modo mercado: o mais barato; senão o favorito/primeiro.
        val shopping = f.shoppingMarketId?.takeIf { id -> markets.any { it.id == id } }
            ?: summary.bestSupermarketCurrent?.id
            ?: markets.firstOrNull()?.id
        ListDetailUi(
            loading = false,
            list = list,
            items = items,
            markets = markets,
            refMonth = ym,
            summary = summary,
            priceTable = table,
            marketMode = f.marketMode,
            hideChecked = f.hideChecked,
            shoppingMarketId = shopping
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListDetailUi())

    // ---------------------------------------------------------------- Ações

    fun setRefMonth(ym: YearMonth) { refOverride.value = ym }
    fun setMarketMode(enabled: Boolean) { marketMode.value = enabled }
    fun setHideChecked(hide: Boolean) { hideChecked.value = hide }
    fun setShoppingMarket(id: Long) { shoppingMarketId.value = id }

    fun toggleCheck(item: ShoppingItemWithProduct) {
        viewModelScope.launch { listDao.toggleItemCheck(item.itemId, !item.isChecked) }
    }

    fun setQuantity(item: ShoppingItemWithProduct, quantity: Double) {
        if (quantity <= 0.0) return
        viewModelScope.launch { listDao.updateItemQuantity(item.itemId, quantity) }
    }

    fun removeItem(item: ShoppingItemWithProduct) {
        viewModelScope.launch { listDao.deleteItem(item.itemId) }
    }

    fun restoreItem(item: ShoppingItemWithProduct) {
        viewModelScope.launch {
            listDao.insertItem(
                ShoppingListItem(
                    id = item.itemId,
                    shoppingListId = item.shoppingListId,
                    productId = item.productId,
                    quantity = item.quantity,
                    isChecked = item.isChecked
                )
            )
        }
    }

    /** Adiciona um produto; se já estiver na lista, soma a quantidade. */
    fun addProduct(productId: Long, quantity: Double) {
        viewModelScope.launch { addInternal(productId, quantity) }
    }

    private suspend fun addInternal(productId: Long, quantity: Double) {
        val existing = listDao.findItem(listId, productId)
        if (existing != null) {
            listDao.updateItem(existing.copy(quantity = existing.quantity + quantity, isChecked = false))
        } else {
            listDao.insertItem(ShoppingListItem(shoppingListId = listId, productId = productId, quantity = quantity))
        }
    }

    fun createProductAndAdd(product: Product, quantity: Double) {
        viewModelScope.launch {
            val id = productDao.insertProduct(product)
            addInternal(id, quantity)
        }
    }

    fun rename(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val list = listDao.getListById(listId) ?: return@launch
            listDao.updateList(list.copy(name = name.trim()))
        }
    }

    fun setBudget(budget: Double) {
        viewModelScope.launch {
            val list = listDao.getListById(listId) ?: return@launch
            listDao.updateList(list.copy(budgetTarget = budget.coerceAtLeast(0.0)))
        }
    }

    fun deleteList(onDone: () -> Unit) {
        viewModelScope.launch {
            val list = listDao.getListById(listId) ?: return@launch
            listDao.deleteList(list)
            onDone()
        }
    }

    /** Texto formatado para WhatsApp. */
    fun shareText(ui: ListDetailUi): String {
        val list = ui.list ?: return ""
        val summary = ui.summary
        return ListShareFormatter.format(
            title = list.name,
            subtitle = Fmt.monthYear(list.referenceMonth, list.referenceYear),
            lines = ui.items.map {
                ListShareFormatter.Line(it.productName, it.productCategory, it.quantity, it.productUnit, it.isChecked)
            },
            estimatedTotal = summary?.bestTotal?.takeIf { it > 0 },
            marketName = summary?.bestSupermarketCurrent?.name
        )
    }
}
