package com.smartmarket.ui.screens.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.smartmarket.data.dao.PantryItemWithProduct
import com.smartmarket.data.db.AppDatabase
import com.smartmarket.data.entity.PantryItem
import com.smartmarket.data.entity.Product
import com.smartmarket.data.entity.ShoppingList
import com.smartmarket.data.entity.ShoppingListItem
import com.smartmarket.domain.pantry.PantryPlanner
import com.smartmarket.ui.components.YearMonth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class PantrySection(val title: String, val low: Boolean, val items: List<PantryItemWithProduct>)

data class PantryUiState(
    val loading: Boolean = true,
    val sections: List<PantrySection> = emptyList(),
    val total: Int = 0,
    val lowCount: Int = 0,
    val products: List<Product> = emptyList(),
    val inPantry: Set<Long> = emptySet()
)

sealed interface PantryEvent {
    data class Message(val text: String, val undo: PantryItem? = null) : PantryEvent
    data class OpenList(val listId: Long) : PantryEvent
}

class PantryViewModel(private val db: AppDatabase) : ViewModel() {

    private val pantryDao = db.pantryDao()
    private val _events = MutableSharedFlow<PantryEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PantryEvent> = _events

    val state: StateFlow<PantryUiState> = combine(
        pantryDao.getPantry(),
        db.productDao().getAllProducts()
    ) { items, products ->
        val low = items.filter { PantryPlanner.isLow(it.quantity, it.minQuantity) }
        val rest = items.filterNot { PantryPlanner.isLow(it.quantity, it.minQuantity) }
        val sections = buildList {
            if (low.isNotEmpty()) add(PantrySection("Abaixo do mínimo", true, low.sortedBy { it.productName.lowercase() }))
            rest.groupBy { it.category.ifBlank { "Geral" } }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                .forEach { (cat, list) -> add(PantrySection(cat, false, list.sortedBy { it.productName.lowercase() })) }
        }
        PantryUiState(
            loading = false,
            sections = sections,
            total = items.size,
            lowCount = low.size,
            products = products,
            inPantry = items.map { it.productId }.toSet()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PantryUiState())

    fun setQuantity(item: PantryItemWithProduct, quantity: Double) = viewModelScope.launch {
        pantryDao.upsert(item.toEntity().copy(quantity = quantity.coerceAtLeast(0.0), updatedAt = System.currentTimeMillis()))
    }

    fun zero(item: PantryItemWithProduct) = viewModelScope.launch {
        if (item.quantity <= 0.0) return@launch
        pantryDao.upsert(item.toEntity().copy(quantity = 0.0, updatedAt = System.currentTimeMillis()))
        _events.tryEmit(PantryEvent.Message("${item.productName} zerado", undo = item.toEntity()))
    }

    fun remove(item: PantryItemWithProduct) = viewModelScope.launch {
        if (pantryDao.get(item.productId) == null) return@launch
        pantryDao.delete(item.productId)
        _events.tryEmit(PantryEvent.Message("${item.productName} saiu da despensa", undo = item.toEntity()))
    }

    fun restore(item: PantryItem) = viewModelScope.launch { pantryDao.upsert(item) }

    fun saveDetails(item: PantryItemWithProduct, minQuantity: Double, expiresAt: Long?) = viewModelScope.launch {
        pantryDao.upsert(
            item.toEntity().copy(minQuantity = minQuantity.coerceAtLeast(0.0), expiresAt = expiresAt, updatedAt = System.currentTimeMillis())
        )
    }

    fun add(product: Product) = viewModelScope.launch {
        if (pantryDao.get(product.id) != null) {
            _events.tryEmit(PantryEvent.Message("${product.name} já está na despensa"))
            return@launch
        }
        pantryDao.upsert(PantryItem(productId = product.id, quantity = 1.0, minQuantity = 1.0))
        _events.tryEmit(PantryEvent.Message("${product.name} adicionado à despensa"))
    }

    /** Cria "Reposição dd/MM" com os itens abaixo do mínimo e abre a lista. */
    fun generateRestockList() = viewModelScope.launch {
        try {
            val items = pantryDao.getPantry().first()
            val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90)
            val purchaseDao = db.purchaseDao()
            val stocks = items.filter { PantryPlanner.isLow(it.quantity, it.minQuantity) }.map {
                PantryPlanner.Stock(
                    productId = it.productId,
                    productName = it.productName,
                    unit = it.unit,
                    quantity = it.quantity,
                    minQuantity = it.minQuantity,
                    monthlyConsumption = PantryPlanner.estimateMonthlyConsumption(
                        defaultMonthly = it.defaultMonthlyQuantity,
                        boughtInPeriod = purchaseDao.getQuantityBoughtSince(it.productId, since),
                        months = 3
                    )
                )
            }
            val lines = PantryPlanner.plan(stocks)
            if (lines.isEmpty()) {
                _events.tryEmit(PantryEvent.Message("Nada abaixo do mínimo agora"))
                return@launch
            }
            val now = YearMonth.now()
            val name = "Reposição " + SimpleDateFormat("dd/MM", Locale("pt", "BR")).format(Date())
            val listDao = db.shoppingListDao()
            val listId = db.withTransaction {
                val id = listDao.insertList(ShoppingList(name = name, referenceMonth = now.month, referenceYear = now.year))
                listDao.insertItems(lines.map { ShoppingListItem(shoppingListId = id, productId = it.productId, quantity = it.quantity) })
                id
            }
            _events.tryEmit(PantryEvent.OpenList(listId))
        } catch (e: Exception) {
            _events.tryEmit(PantryEvent.Message("Não foi possível gerar a lista"))
        }
    }
}

internal fun PantryItemWithProduct.toEntity() = PantryItem(
    productId = productId,
    quantity = quantity,
    minQuantity = minQuantity,
    expiresAt = expiresAt,
    updatedAt = updatedAt
)
