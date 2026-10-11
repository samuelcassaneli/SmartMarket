package com.smartmarket.ui.screens.flyers

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartmarket.data.dao.ProductDao
import com.smartmarket.data.dao.SupermarketDao
import com.smartmarket.data.entity.PriceSource
import com.smartmarket.data.entity.Product
import com.smartmarket.data.entity.Supermarket
import com.smartmarket.data.repo.PriceEntry
import com.smartmarket.data.repo.PriceRepository
import com.smartmarket.domain.flyers.FlyerInterpreter
import com.smartmarket.domain.flyers.matchOffers
import com.smartmarket.domain.matching.ProductMatcher
import com.smartmarket.domain.ocr.EncarteParser
import com.smartmarket.domain.ocr.OcrPage
import com.smartmarket.domain.ocr.OcrProcessor
import com.smartmarket.ui.components.Fmt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Item da revisão da leitura manual. */
data class ScanItem(
    val id: Int,
    val original: String,
    val note: String,
    val product: Product?,
    val confidence: Double,
    val viaAlias: Boolean,
    val suggestions: List<Product>,
    val priceText: String,
    val include: Boolean
) {
    val price: Double? get() = Fmt.parseMoney(priceText)?.takeIf { it in EncarteParser.MIN_PRICE..EncarteParser.MAX_PRICE }
    val canSave: Boolean get() = include && product != null && price != null
}

sealed interface ScanPhase {
    data object Idle : ScanPhase
    data class Reading(val message: String, val current: Int, val total: Int) : ScanPhase
    data object Review : ScanPhase
    data class Error(val message: String) : ScanPhase
}

class FlyerScanViewModel(
    private val ocr: OcrProcessor,
    private val matcher: ProductMatcher,
    private val priceRepository: PriceRepository,
    private val productDao: ProductDao,
    supermarketDao: SupermarketDao,
    private val interpreter: FlyerInterpreter
) : ViewModel() {

    val markets: StateFlow<List<Supermarket>> = supermarketDao.getAllSupermarkets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<Product>> = productDao.getAllProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _marketId = MutableStateFlow<Long?>(null)
    val marketId = _marketId.asStateFlow()

    private val _phase = MutableStateFlow<ScanPhase>(ScanPhase.Idle)
    val phase = _phase.asStateFlow()

    private val _items = MutableStateFlow<List<ScanItem>>(emptyList())
    val items = _items.asStateFlow()

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Emite quando o salvamento terminou (a tela volta). */
    private val _saved = Channel<Int>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    private var job: Job? = null

    fun selectMarket(id: Long) { _marketId.value = id }

    fun readImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        startReading { onProgress ->
            uris.mapIndexed { i, uri ->
                onProgress(i + 1, uris.size)
                ocr.readImageUri(uri, i)
            }
        }
    }

    fun readPdf(uri: Uri) = startReading { onProgress -> ocr.readPdfUri(uri) { c, t -> onProgress(c, t) } }

    private fun startReading(read: suspend (onProgress: (Int, Int) -> Unit) -> List<OcrPage>) {
        job?.cancel()
        job = viewModelScope.launch {
            _phase.value = ScanPhase.Reading("Preparando…", 0, 0)
            try {
                val pages = read { c, t -> _phase.value = ScanPhase.Reading("Lendo página $c de $t…", c, t) }
                _phase.value = ScanPhase.Reading("Procurando ofertas…", 0, 0)
                val marketName = markets.value.firstOrNull { it.id == _marketId.value }?.name
                val result = interpreter.interpret(pages, marketName) { c, t, ai ->
                    _phase.value = ScanPhase.Reading(
                        if (ai) "IA lendo página $c de $t…" else "Separando ofertas da página $c de $t…", c, t
                    )
                }
                result.aiError?.let { _events.trySend("Leitura com IA falhou em algumas páginas ($it); usamos a leitura local.") }
                val parsed = result.offers
                val matched = matchOffers(parsed, matcher)
                val built = matched.mapIndexed { i, m ->
                    ScanItem(
                        id = i,
                        original = m.parsed.description,
                        note = m.parsed.note,
                        product = m.outcome.product,
                        confidence = m.outcome.confidence,
                        viaAlias = m.outcome.viaAlias,
                        suggestions = m.outcome.suggestions.map { it.item },
                        priceText = Fmt.decimal(m.parsed.price),
                        include = m.outcome.product != null
                    )
                }.sortedWith(compareBy<ScanItem> { it.product == null }.thenByDescending { it.confidence })
                _items.value = built
                _phase.value = if (built.isEmpty()) {
                    ScanPhase.Error("Não encontramos ofertas com preço nesta leitura. Tente uma foto mais nítida e de perto.")
                } else ScanPhase.Review
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                _phase.value = ScanPhase.Error("O arquivo é grande demais para ler de uma vez.")
            } catch (e: Exception) {
                _phase.value = ScanPhase.Error("Não foi possível ler o encarte: ${e.message ?: "erro desconhecido"}")
            }
        }
    }

    fun reset() {
        job?.cancel()
        _items.value = emptyList()
        _phase.value = ScanPhase.Idle
    }

    private fun updateItem(id: Int, f: (ScanItem) -> ScanItem) {
        _items.update { list -> list.map { if (it.id == id) f(it) else it } }
    }

    fun setPrice(id: Int, text: String) = updateItem(id) { it.copy(priceText = text) }

    fun setInclude(id: Int, include: Boolean) = updateItem(id) { it.copy(include = include && it.product != null) }

    fun setProduct(id: Int, product: Product?) =
        updateItem(id) { it.copy(product = product, include = product != null, confidence = if (product != null) 1.0 else 0.0, viaAlias = false) }

    fun createProduct(id: Int, name: String, category: String) {
        viewModelScope.launch {
            val product = Product(name = name, category = category)
            val newId = productDao.insertProduct(product)
            setProduct(id, product.copy(id = newId))
            _events.send("Produto \"$name\" criado")
        }
    }

    fun save() {
        val market = _marketId.value
        if (market == null) {
            viewModelScope.launch { _events.send("Escolha o mercado antes de salvar") }
            return
        }
        val toSave = _items.value.filter { it.canSave }
        if (toSave.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            priceRepository.recordPrices(toSave.map {
                PriceEntry(
                    productId = it.product!!.id,
                    supermarketId = market,
                    price = it.price!!,
                    source = PriceSource.OCR,
                    observedAt = now,
                    note = it.note
                )
            })
            toSave.forEach { matcher.learn(it.original, it.product!!.id, PriceSource.OCR) }
            _saved.send(toSave.size)
        }
    }
}
