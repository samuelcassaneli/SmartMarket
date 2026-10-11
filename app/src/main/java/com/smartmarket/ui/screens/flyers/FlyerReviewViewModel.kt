package com.smartmarket.ui.screens.flyers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartmarket.data.dao.FlyerDao
import com.smartmarket.data.dao.FlyerOfferWithProduct
import com.smartmarket.data.dao.FlyerWithMarket
import com.smartmarket.data.dao.ProductDao
import com.smartmarket.data.dao.ShoppingListDao
import com.smartmarket.data.entity.OfferStatus
import com.smartmarket.data.entity.PriceSource
import com.smartmarket.data.entity.Product
import com.smartmarket.data.repo.PriceEntry
import com.smartmarket.data.repo.PriceRepository
import com.smartmarket.domain.flyers.OfferText
import com.smartmarket.domain.matching.FuzzyMatcher
import com.smartmarket.domain.matching.ProductMatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class OfferFilter(val label: String) { PENDING("Pendentes"), MY_LIST("Da minha lista"), ALL("Todas") }

/** Oferta pronta para exibir: descrição e observação separadas. */
data class OfferUi(
    val offer: FlyerOfferWithProduct,
    val description: String,
    val note: String,
    val inMyList: Boolean
)

data class ReviewUi(
    val loading: Boolean = true,
    val offers: List<OfferUi> = emptyList(),
    val total: Int = 0,
    val pending: Int = 0,
    val pendingLinked: Int = 0,
    val myList: Int = 0
)

class FlyerReviewViewModel(
    private val flyerId: Long,
    private val flyerDao: FlyerDao,
    private val productDao: ProductDao,
    private val shoppingListDao: ShoppingListDao,
    private val priceRepository: PriceRepository,
    private val matcher: ProductMatcher
) : ViewModel() {

    val flyer: StateFlow<FlyerWithMarket?> = flyerDao.getFlyers()
        .map { list -> list.firstOrNull { it.id == flyerId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val products: StateFlow<List<Product>> = productDao.getAllProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _filter = MutableStateFlow(OfferFilter.PENDING)
    val filter = _filter.asStateFlow()

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val listProductIds = MutableStateFlow<Set<Long>>(emptySet())

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Todas as ofertas do encarte, sem filtro. */
    private val allOffers: StateFlow<List<OfferUi>?> = combine(
        flyerDao.getOffersForFlyer(flyerId), listProductIds
    ) { offers, mine ->
        offers.map { o ->
            val (desc, note) = OfferText.split(o.rawText)
            val pid = o.productId
            OfferUi(o, desc, note, pid != null && pid in mine)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val ui: StateFlow<ReviewUi> = combine(allOffers, _filter, _query) { loaded, filter, query ->
        if (loaded == null) return@combine ReviewUi(loading = true)
        val all: List<OfferUi> = loaded
        val q = FuzzyMatcher.normalize(query)
        val visible = all.filter { u ->
            val passFilter = when (filter) {
                OfferFilter.PENDING -> u.offer.status == OfferStatus.PENDING
                OfferFilter.MY_LIST -> u.inMyList
                OfferFilter.ALL -> true
            }
            // a busca procura em tudo (inclusive itens já revisados), para achar qualquer texto do encarte
            if (q.isNotEmpty()) {
                FuzzyMatcher.normalize(u.description).contains(q) ||
                    (u.offer.productName?.let { FuzzyMatcher.normalize(it).contains(q) } ?: false)
            } else passFilter
        }
        ReviewUi(
            loading = false,
            offers = visible,
            total = all.size,
            pending = all.count { it.offer.status == OfferStatus.PENDING },
            pendingLinked = all.count { it.offer.status == OfferStatus.PENDING && it.offer.productId != null },
            myList = all.count { it.inMyList }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReviewUi())

    init {
        viewModelScope.launch {
            val active = shoppingListDao.getAllListsSync().filter { !it.isArchived }.map { it.id }.toSet()
            listProductIds.value = shoppingListDao.getAllItemsSync()
                .filter { it.shoppingListId in active }
                .map { it.productId }
                .toSet()
        }
    }

    fun setFilter(f: OfferFilter) { _filter.value = f }
    fun setQuery(q: String) { _query.value = q }

    fun suggestionsFor(text: String): List<Product> =
        FuzzyMatcher.rank(text, products.value, { it.name }, limit = 5).map { it.item }

    fun accept(u: OfferUi, notify: Boolean = true) {
        val productId = u.offer.productId ?: return
        viewModelScope.launch {
            acceptInternal(u, productId)
            if (notify) _events.send("Preço de ${u.offer.productName ?: "produto"} salvo")
        }
    }

    private suspend fun acceptInternal(u: OfferUi, productId: Long) {
        val observedAt = flyer.value?.fetchedAt ?: System.currentTimeMillis()
        priceRepository.recordPrice(
            productId = productId,
            supermarketId = u.offer.supermarketId,
            price = u.offer.price,
            source = PriceSource.FLYER,
            observedAt = observedAt,
            note = u.note
        )
        matcher.learn(u.description, productId, PriceSource.FLYER)
        flyerDao.updateOffer(u.offer.id, OfferStatus.ACCEPTED, productId)
    }

    fun reject(u: OfferUi) {
        viewModelScope.launch { flyerDao.updateOffer(u.offer.id, OfferStatus.REJECTED, u.offer.productId) }
    }

    fun restore(u: OfferUi) {
        viewModelScope.launch { flyerDao.updateOffer(u.offer.id, OfferStatus.PENDING, u.offer.productId) }
    }

    /** Troca o produto vinculado (mantém o status; aceitar é uma ação separada). */
    fun link(u: OfferUi, product: Product?) {
        viewModelScope.launch {
            val status = if (u.offer.status == OfferStatus.ACCEPTED) OfferStatus.PENDING else u.offer.status
            flyerDao.updateOffer(u.offer.id, status, product?.id)
        }
    }

    fun createAndLink(u: OfferUi, name: String, category: String) {
        viewModelScope.launch {
            val id = productDao.insertProduct(Product(name = name, category = category))
            flyerDao.updateOffer(u.offer.id, OfferStatus.PENDING, id)
            _events.send("Produto \"$name\" criado e vinculado")
        }
    }

    fun acceptAllLinked() {
        viewModelScope.launch {
            // todas as pendentes vinculadas, mesmo as escondidas pelo filtro atual
            val targets = allOffers.value.orEmpty()
                .filter { it.offer.status == OfferStatus.PENDING && it.offer.productId != null }
            if (targets.isEmpty()) return@launch
            val observedAt = flyer.value?.fetchedAt ?: System.currentTimeMillis()
            priceRepository.recordPrices(targets.map {
                PriceEntry(it.offer.productId!!, it.offer.supermarketId, it.offer.price, PriceSource.FLYER, observedAt, it.note)
            })
            targets.forEach {
                matcher.learn(it.description, it.offer.productId!!, PriceSource.FLYER)
                flyerDao.updateOffer(it.offer.id, OfferStatus.ACCEPTED, it.offer.productId)
            }
            _events.send(if (targets.size == 1) "1 preço salvo" else "${targets.size} preços salvos")
        }
    }
}
