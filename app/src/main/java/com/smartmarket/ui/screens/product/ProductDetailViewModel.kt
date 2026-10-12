package com.smartmarket.ui.screens.product

import com.smartmarket.domain.images.ImageMatch
import com.smartmarket.domain.images.ProductImageFinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.smartmarket.data.dao.ListSummary
import com.smartmarket.data.dao.PantryItemWithProduct
import com.smartmarket.data.dao.PriceObservationWithMarket
import com.smartmarket.data.dao.ProductPriceStats
import com.smartmarket.data.db.AppDatabase
import com.smartmarket.data.entity.PantryItem
import com.smartmarket.data.entity.PriceAlert
import com.smartmarket.data.entity.PriceSource
import com.smartmarket.data.entity.Product
import com.smartmarket.data.entity.ShoppingList
import com.smartmarket.data.entity.ShoppingListItem
import com.smartmarket.data.entity.Supermarket
import com.smartmarket.data.repo.PriceRepository
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Preço do mês de um mercado, para a seção "Preços deste mês". */
data class MarketPriceRow(
    val supermarketId: Long,
    val name: String,
    val colorHex: String,
    val price: Double,
    val source: String,
    val updatedAt: Long,
    val isBest: Boolean
)

/** Compra do produto vinda de uma nota. */
data class ProductPurchaseRow(
    val purchaseId: Long,
    val issuedAt: Long,
    val marketName: String,
    val colorHex: String?,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double,
    val totalPrice: Double
)

data class ProductDetailState(
    val loading: Boolean = true,
    val product: Product? = null,
    val stats: ProductPriceStats? = null,
    val observations: List<PriceObservationWithMarket> = emptyList(),
    val monthPrices: List<MarketPriceRow> = emptyList(),
    val alert: PriceAlert? = null,
    val pantry: PantryItemWithProduct? = null,
    val lists: List<ListSummary> = emptyList(),
    val markets: List<Supermarket> = emptyList(),
    val categories: List<String> = emptyList(),
    val purchases: List<ProductPurchaseRow> = emptyList()
)

private data class PartA(
    val product: Product?,
    val stats: ProductPriceStats,
    val observations: List<PriceObservationWithMarket>,
    val monthPrices: List<MarketPriceRow>,
    val alert: PriceAlert?
)

private data class PartB(
    val pantry: PantryItemWithProduct?,
    val lists: List<ListSummary>,
    val categories: List<String>,
    val purchases: List<ProductPurchaseRow>,
    val markets: List<Supermarket>
)

class ProductDetailViewModel(
    private val productId: Long,
    private val db: AppDatabase,
    private val priceRepository: PriceRepository,
    private val imageFinder: ProductImageFinder? = null
) : ViewModel() {

    private val productDao = db.productDao()
    private val priceDao = db.productPriceDao()
    private val alertDao = db.priceAlertDao()
    private val pantryDao = db.pantryDao()
    private val listDao = db.shoppingListDao()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val markets: Flow<List<Supermarket>> = db.supermarketDao().getAllSupermarkets()
    private val observations = priceDao.getObservationsForProduct(productId)

    /** Recalcula os preços do mês sempre que o histórico ou os mercados mudam. */
    private val monthPrices: Flow<List<MarketPriceRow>> = combine(observations, markets) { _, ms ->
        val now = YearMonth.now()
        val byId = ms.associateBy { it.id }
        val prices = priceDao.getPricesForProductInMonth(productId, now.month, now.year)
        val min = prices.minOfOrNull { it.price }
        prices.mapNotNull { p ->
            val m = byId[p.supermarketId] ?: return@mapNotNull null
            MarketPriceRow(
                supermarketId = m.id, name = m.name, colorHex = m.colorHex, price = p.price, source = p.source,
                updatedAt = p.updatedAt, isBest = prices.size > 1 && p.price == min
            )
        }.sortedBy { it.price }
    }

    /** Últimas compras do produto (consulta própria; reavaliada quando as notas mudam). */
    private val purchases: Flow<List<ProductPurchaseRow>> = db.purchaseDao().getAllPurchases()
        .map { queryPurchases() }
        .flowOn(Dispatchers.IO)

    private val partA = combine(
        productDao.observeProduct(productId),
        priceDao.getStatsForProduct(productId),
        observations,
        monthPrices,
        alertDao.observeForProduct(productId)
    ) { product, stats, obs, month, alert -> PartA(product, stats, obs, month, alert) }

    private val partB = combine(
        pantryDao.getPantry().map { all -> all.firstOrNull { it.productId == productId } },
        listDao.getListSummaries().map { all -> all.filter { !it.isArchived } },
        productDao.getAllCategories(),
        purchases,
        markets
    ) { pantry, lists, categories, purchases, markets -> PartB(pantry, lists, categories, purchases, markets) }

    val state: StateFlow<ProductDetailState> = combine(partA, partB) { a, b ->
        ProductDetailState(
            loading = false,
            product = a.product,
            stats = a.stats,
            observations = a.observations,
            monthPrices = a.monthPrices,
            alert = a.alert,
            pantry = b.pantry,
            lists = b.lists,
            markets = b.markets,
            categories = b.categories.filter { it.isNotBlank() },
            purchases = b.purchases
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductDetailState())

    // ---------------------------------------------------------------- Ações

    fun toggleFavorite() = launchSafe {
        val p = productDao.getProductById(productId) ?: return@launchSafe
        productDao.updateProduct(p.copy(isFavorite = !p.isFavorite))
    }

    fun recordPrice(supermarketId: Long, price: Double, observedAt: Long) = launchSafe {
        priceRepository.recordPrice(productId, supermarketId, price, PriceSource.MANUAL, observedAt)
        _messages.tryEmit("Preço de ${Fmt.money(price)} registrado")
    }

    fun saveAlert(active: Boolean, targetPrice: Double?, notifyOnAnyDrop: Boolean) = launchSafe {
        val existing = alertDao.getForProduct(productId)
        if (existing == null && !active) return@launchSafe
        val changedRule = existing != null &&
            (existing.targetPrice != targetPrice || existing.notifyOnAnyDrop != notifyOnAnyDrop)
        alertDao.upsert(
            PriceAlert(
                id = existing?.id ?: 0,
                productId = productId,
                targetPrice = targetPrice?.takeIf { it > 0 },
                notifyOnAnyDrop = notifyOnAnyDrop,
                active = active,
                // Regra nova: pode avisar de novo o mesmo preço
                lastNotifiedPrice = if (changedRule) null else existing?.lastNotifiedPrice,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
        )
    }

    fun setPantry(quantity: Double, minQuantity: Double) = launchSafe {
        val existing = pantryDao.get(productId)
        pantryDao.upsert(
            PantryItem(
                productId = productId,
                quantity = quantity.coerceAtLeast(0.0),
                minQuantity = minQuantity.coerceAtLeast(0.0),
                expiresAt = existing?.expiresAt,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun removeFromPantry() = launchSafe { pantryDao.delete(productId) }

    fun addToList(listId: Long, listName: String) = launchSafe {
        val item = listDao.findItem(listId, productId)
        if (item != null) {
            listDao.updateItemQuantity(item.id, item.quantity + 1.0)
            _messages.tryEmit("Mais 1 em $listName (agora ${Fmt.quantity(item.quantity + 1.0)})")
        } else {
            listDao.insertItem(ShoppingListItem(shoppingListId = listId, productId = productId, quantity = 1.0))
            _messages.tryEmit("Adicionado a $listName")
        }
    }

    /** Cria a lista do mês e já adiciona o produto. */
    fun createListAndAdd() = launchSafe {
        val now = YearMonth.now()
        val name = "Compras de ${Fmt.monthName(now.month)}"
        db.withTransaction {
            val listId = listDao.insertList(ShoppingList(name = name, referenceMonth = now.month, referenceYear = now.year))
            listDao.insertItem(ShoppingListItem(shoppingListId = listId, productId = productId, quantity = 1.0))
        }
        _messages.tryEmit("Lista \"$name\" criada com o produto")
    }

    fun updateProduct(name: String, category: String, unit: String, monthly: Double, barcode: String) = launchSafe {
        val p = productDao.getProductById(productId) ?: return@launchSafe
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            _messages.tryEmit("O nome não pode ficar vazio")
            return@launchSafe
        }
        // nome ou código mudou (ex.: virou "zero"): a foto antiga pode ser de outro produto
        val specChanged = ImageMatch.key(cleanName) != ImageMatch.key(p.name) || barcode.filter { it.isDigit() } != p.barcode
        productDao.updateProduct(
            p.copy(
                imageUrl = if (specChanged) "" else p.imageUrl,
                name = cleanName,
                normalizedName = cleanName.lowercase().trim(),
                category = category.trim().ifEmpty { "Geral" },
                unit = unit.trim().ifEmpty { "un" },
                defaultMonthlyQuantity = monthly.coerceAtLeast(0.0),
                barcode = barcode.filter { it.isDigit() }
            )
        )
        _messages.tryEmit("Produto atualizado")
        if (specChanged) findPhoto(force = false)
    }

    // ---- Foto

    private val _photoBusy = MutableStateFlow(false)
    val photoBusy: StateFlow<Boolean> = _photoBusy.asStateFlow()

    init {
        // produto sem foto: procura sozinho (usa o cache; só vai à internet na primeira vez)
        viewModelScope.launch { runCatching { findPhoto(force = false) } }
    }

    private suspend fun findPhoto(force: Boolean): Boolean {
        val finder = imageFinder ?: return false
        val p = productDao.getProductById(productId) ?: return false
        if (!force && p.imageUrl.isNotBlank()) return true
        _photoBusy.value = true
        try {
            val url = finder.find(p.name, p.barcode, force) ?: return false
            productDao.setImageUrl(productId, url)
            return true
        } finally {
            finder.save()
            _photoBusy.value = false
        }
    }

    /** Procura de novo, ignorando o cache. */
    fun searchPhoto() = launchSafe {
        if (imageFinder == null) return@launchSafe
        val ok = findPhoto(force = true)
        _messages.tryEmit(if (ok) "Foto atualizada" else "Nenhuma foto exata deste produto (marca, variante e tamanho)")
    }

    fun removePhoto() = launchSafe {
        productDao.setImageUrl(productId, "")
        _messages.tryEmit("Foto removida")
    }

    fun delete() = launchSafe {
        val p = productDao.getProductById(productId) ?: return@launchSafe
        productDao.deleteProduct(p)
        _deleted.value = true
    }

    private fun launchSafe(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _messages.tryEmit("Não foi possível concluir: ${e.message ?: "erro desconhecido"}")
            }
        }
    }

    private suspend fun queryPurchases(): List<ProductPurchaseRow> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT pi.purchaseId AS purchaseId, pu.issuedAt AS issuedAt,
                   COALESCE(s.name, pu.storeName) AS marketName, s.colorHex AS colorHex,
                   pi.quantity AS quantity, pi.unit AS unit, pi.unitPrice AS unitPrice, pi.totalPrice AS totalPrice
            FROM purchase_items pi
            INNER JOIN purchases pu ON pu.id = pi.purchaseId
            LEFT JOIN supermarkets s ON s.id = pu.supermarketId
            WHERE pi.productId = ?
            ORDER BY pu.issuedAt DESC
            LIMIT 5
        """.trimIndent()
        val rows = mutableListOf<ProductPurchaseRow>()
        db.query(SimpleSQLiteQuery(sql, arrayOf<Any?>(productId))).use { c ->
            val iPurchase = c.getColumnIndexOrThrow("purchaseId")
            val iIssued = c.getColumnIndexOrThrow("issuedAt")
            val iMarket = c.getColumnIndexOrThrow("marketName")
            val iColor = c.getColumnIndexOrThrow("colorHex")
            val iQty = c.getColumnIndexOrThrow("quantity")
            val iUnit = c.getColumnIndexOrThrow("unit")
            val iUnitPrice = c.getColumnIndexOrThrow("unitPrice")
            val iTotal = c.getColumnIndexOrThrow("totalPrice")
            while (c.moveToNext()) {
                rows += ProductPurchaseRow(
                    purchaseId = c.getLong(iPurchase),
                    issuedAt = c.getLong(iIssued),
                    marketName = if (c.isNull(iMarket)) "Compra" else c.getString(iMarket),
                    colorHex = if (c.isNull(iColor)) null else c.getString(iColor),
                    quantity = c.getDouble(iQty),
                    unit = if (c.isNull(iUnit)) "" else c.getString(iUnit),
                    unitPrice = c.getDouble(iUnitPrice),
                    totalPrice = c.getDouble(iTotal)
                )
            }
        }
        rows
    }
}
