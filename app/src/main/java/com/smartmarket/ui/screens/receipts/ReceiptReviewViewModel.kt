package com.smartmarket.ui.screens.receipts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartmarket.data.dao.ProductDao
import com.smartmarket.data.dao.SupermarketDao
import com.smartmarket.data.entity.Product
import com.smartmarket.data.entity.Supermarket
import com.smartmarket.data.repo.DuplicateReceiptException
import com.smartmarket.data.repo.MarketSelection
import com.smartmarket.data.repo.PurchaseRepository
import com.smartmarket.data.repo.ReceiptLine
import com.smartmarket.domain.matching.FuzzyMatcher
import com.smartmarket.domain.matching.ProductMatcher
import com.smartmarket.domain.nfce.NfceErrorKind
import com.smartmarket.domain.nfce.NfceException
import com.smartmarket.domain.nfce.NfceItem
import com.smartmarket.domain.nfce.NfceKey
import com.smartmarket.domain.nfce.NfceReceipt
import com.smartmarket.domain.nfce.NfceService
import com.smartmarket.domain.nfce.NfceText
import com.smartmarket.domain.nfce.QrPayload
import com.smartmarket.domain.nfce.QrPayloadParser
import com.smartmarket.domain.nfce.SefazDirectory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LinkStatus { LINKED, SUGGESTED, NEW, IGNORED }

/** Item da nota na revisão, com o vínculo escolhido. */
data class ReviewItem(
    val index: Int,
    val item: NfceItem,
    val product: Product? = null,
    val newProduct: Product? = null,
    val viaAlias: Boolean = false,
    val suggestions: List<FuzzyMatcher.Ranked<Product>> = emptyList(),
    val ignored: Boolean = false
) {
    val status: LinkStatus
        get() = when {
            ignored -> LinkStatus.IGNORED
            product != null || newProduct != null -> LinkStatus.LINKED
            suggestions.isNotEmpty() -> LinkStatus.SUGGESTED
            else -> LinkStatus.NEW
        }

    val linkedName: String? get() = product?.name ?: newProduct?.name
}

/** Mercado da nota: existente (pelo CNPJ ou sugerido pelo nome) ou novo. */
sealed interface MarketChoice {
    data class Existing(val market: Supermarket, val byCnpj: Boolean) : MarketChoice
    data class New(val name: String) : MarketChoice
}

sealed interface ReviewState {
    data object Loading : ReviewState
    data class Failed(val kind: NfceErrorKind, val message: String, val payload: QrPayload?) : ReviewState
    data class Duplicate(val purchaseId: Long, val importedAt: Long) : ReviewState
    data class Ready(
        val receipt: NfceReceipt,
        val items: List<ReviewItem>,
        val market: MarketChoice,
        val addToPantry: Boolean = true
    ) : ReviewState
}

/** Pedido para abrir a página da SEFAZ no WebView. */
data class WebViewRequest(val url: String, val key: NfceKey?, val clipboardKey: String?)

class ReceiptReviewViewModel(
    initialPayload: String,
    private val service: NfceService,
    private val purchases: PurchaseRepository,
    private val matcher: ProductMatcher,
    productDao: ProductDao,
    private val supermarketDao: SupermarketDao
) : ViewModel() {

    private val _state = MutableStateFlow<ReviewState>(ReviewState.Loading)
    val state: StateFlow<ReviewState> = _state.asStateFlow()

    private val _webView = MutableStateFlow<WebViewRequest?>(null)
    val webView: StateFlow<WebViewRequest?> = _webView.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** Id da compra salva (a tela navega e limpa). */
    private val _savedId = MutableStateFlow<Long?>(null)
    val savedId: StateFlow<Long?> = _savedId.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val products: StateFlow<List<Product>> = productDao.getAllProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val markets: StateFlow<List<Supermarket>> = supermarketDao.getAllSupermarkets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var payload: QrPayload? = null
    private var currentPayloadText: String = initialPayload

    init {
        load(initialPayload)
    }

    fun retry() = load(currentPayloadText)

    fun load(payloadText: String) {
        currentPayloadText = payloadText
        _state.value = ReviewState.Loading
        viewModelScope.launch {
            val parsed = try {
                QrPayloadParser.parseOrThrow(payloadText)
            } catch (e: NfceException) {
                _state.value = ReviewState.Failed(e.kind, e.message ?: "Código inválido.", null)
                return@launch
            }
            payload = parsed

            purchases.findByAccessKey(parsed.key.digits)?.let {
                _state.value = ReviewState.Duplicate(it.id, it.createdAt)
                return@launch
            }

            if (parsed.consultUrl == null && SefazDirectory.directKeyUrl(parsed.key) == null) {
                // Só a chave: a consulta pública pede captcha, então abre a página do estado no WebView
                _state.value = ReviewState.Failed(
                    NfceErrorKind.CAPTCHA,
                    "Para consultar pela chave, a SEFAZ pede uma verificação. Abra a consulta, cole a chave (já copiada) e confirme.",
                    parsed
                )
                openKeyQuery()
                return@launch
            }

            try {
                onReceipt(service.load(parsed))
            } catch (e: NfceException) {
                _state.value = ReviewState.Failed(e.kind, e.message ?: "Não foi possível ler a nota.", parsed)
                if (e.kind.needsWebView) openInWebView()
            } catch (e: Exception) {
                _state.value = ReviewState.Failed(NfceErrorKind.NETWORK, "Não foi possível consultar a SEFAZ.", parsed)
            }
        }
    }

    /** Abre a URL do QR (ou a consulta por chave) no navegador interno. */
    fun openInWebView() {
        val p = payload ?: return
        val url = p.consultUrl
        if (url != null) {
            _webView.value = WebViewRequest(url, p.key, null)
        } else {
            openKeyQuery()
        }
    }

    private fun openKeyQuery() {
        val p = payload ?: return
        _webView.value = WebViewRequest(SefazDirectory.keyQueryUrl(p.key), p.key, p.key.digits)
    }

    fun closeWebView() {
        _webView.value = null
    }

    fun onExtracted(receipt: NfceReceipt) {
        _webView.value = null
        _state.value = ReviewState.Loading
        viewModelScope.launch { onReceipt(receipt) }
    }

    private suspend fun onReceipt(receipt: NfceReceipt) {
        purchases.findByAccessKey(receipt.accessKey)?.let {
            _state.value = ReviewState.Duplicate(it.id, it.createdAt)
            return
        }
        val outcomes = matcher.matchAll(receipt.items.map { it.description })
        val items = receipt.items.mapIndexed { i, item ->
            val outcome = outcomes[i]
            ReviewItem(
                index = i,
                item = item,
                product = outcome.product,
                viaAlias = outcome.viaAlias,
                suggestions = outcome.suggestions
            )
        }
        _state.value = ReviewState.Ready(receipt, items, resolveMarket(receipt))
    }

    private suspend fun resolveMarket(receipt: NfceReceipt): MarketChoice {
        if (receipt.cnpj.isNotBlank()) {
            supermarketDao.getSupermarketByCnpj(receipt.cnpj)?.let { return MarketChoice.Existing(it, byCnpj = true) }
        }
        val best = suggestedMarkets(receipt, supermarketDao.getAllSupermarketsSync()).firstOrNull()
        return if (best != null) MarketChoice.Existing(best, byCnpj = false)
        else MarketChoice.New(NfceText.cleanStoreName(receipt.storeName))
    }

    /** Mercados cadastrados parecidos com a razão social da nota (sem CNPJ ou com o mesmo). */
    fun suggestedMarkets(receipt: NfceReceipt, all: List<Supermarket>): List<Supermarket> =
        all.map { it to NfceText.storeSimilarity(receipt.storeName, it.name) }
            .filter { it.second >= 0.5 }
            .sortedByDescending { it.second }
            .map { it.first }

    // ------------------------------------------------------------------ edição

    private fun updateReady(transform: (ReviewState.Ready) -> ReviewState.Ready) {
        _state.update { s -> if (s is ReviewState.Ready) transform(s) else s }
    }

    private fun updateItem(index: Int, transform: (ReviewItem) -> ReviewItem) = updateReady { s ->
        s.copy(items = s.items.map { if (it.index == index) transform(it) else it })
    }

    fun linkItem(index: Int, product: Product) = updateItem(index) {
        it.copy(product = product, newProduct = null, ignored = false, viaAlias = false)
    }

    fun createProductFor(index: Int, name: String, category: String, unit: String) = updateItem(index) {
        it.copy(
            product = null,
            newProduct = Product(name = name.trim(), category = category.trim().ifBlank { "Geral" }, unit = unit.trim().ifBlank { "un" }),
            ignored = false
        )
    }

    fun ignoreItem(index: Int) = updateItem(index) { it.copy(product = null, newProduct = null, ignored = true) }

    fun unlinkItem(index: Int) = updateItem(index) { it.copy(product = null, newProduct = null, ignored = false) }

    /** Aceita a melhor sugestão de cada item ainda sem vínculo. */
    fun acceptAllSuggestions() = updateReady { s ->
        s.copy(items = s.items.map { item ->
            if (item.status == LinkStatus.SUGGESTED) item.copy(product = item.suggestions.first().item) else item
        })
    }

    /** Cria produto para cada item novo (sem sugestão), com nome limpo, categoria e unidade sugeridas. */
    fun createAllNew() = updateReady { s ->
        s.copy(items = s.items.map { item ->
            if (item.status == LinkStatus.NEW) item.copy(newProduct = draftFor(item.item)) else item
        })
    }

    fun draftFor(item: NfceItem): Product = Product(
        name = NfceText.cleanProductName(item.description),
        category = NfceText.suggestCategory(item.description),
        unit = NfceText.suggestUnit(item)
    )

    fun setMarket(choice: MarketChoice) = updateReady { it.copy(market = choice) }

    fun setAddToPantry(value: Boolean) = updateReady { it.copy(addToPantry = value) }

    fun consumeMessage() {
        _message.value = null
    }

    fun consumeSaved() {
        _savedId.value = null
    }

    fun save() {
        val s = _state.value as? ReviewState.Ready ?: return
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            try {
                val lines = s.items.map { ri ->
                    when {
                        ri.ignored -> ReceiptLine(ri.item)
                        ri.product != null -> ReceiptLine(ri.item, productId = ri.product.id)
                        ri.newProduct != null -> ReceiptLine(ri.item, newProduct = ri.newProduct)
                        else -> ReceiptLine(ri.item)
                    }
                }
                val market = when (val m = s.market) {
                    is MarketChoice.Existing -> MarketSelection.Existing(m.market.id)
                    is MarketChoice.New -> MarketSelection.New(m.name)
                }
                _savedId.value = purchases.saveReceipt(s.receipt, market, lines, s.addToPantry)
            } catch (e: DuplicateReceiptException) {
                _state.value = ReviewState.Duplicate(e.purchaseId, System.currentTimeMillis())
            } catch (e: Exception) {
                _message.value = "Não foi possível salvar a compra: ${e.message ?: "erro desconhecido"}"
            } finally {
                _saving.value = false
            }
        }
    }
}
