package com.smartmarket.domain.online

/** Produto encontrado no site de um mercado. */
data class OnlineOffer(
    val sourceId: String,
    val name: String,
    val brand: String? = null,
    val price: Double,
    /** Preço "de" (antes do desconto), quando maior que [price]. */
    val listPrice: Double? = null,
    /** Quantidade vendida por unidade de venda (ex.: 0,5 kg), se a fonte informar. */
    val unitMultiplier: Double? = null,
    /** Unidade de venda informada pela fonte (un, kg, UND...). */
    val measurementUnit: String? = null,
    val imageUrl: String? = null,
    val productUrl: String? = null,
    val ean: String? = null,
    val available: Boolean = true,
    val sellerId: String? = null,
    val sellerName: String? = null
) {
    /** Chave estável dentro de uma busca (para listas e seleção). */
    val key: String get() = "$sourceId|${productUrl ?: ""}|${ean ?: ""}|$name"

    val hasDiscount: Boolean get() = listPrice != null && listPrice > price + 0.009
}

/** Fonte de busca de produtos no site de um mercado. */
interface MarketSearchSource {
    /** Igual à constante de [com.smartmarket.data.entity.OnlineSource]. */
    val id: String
    val displayName: String

    /**
     * Busca [term] no site. [page] começa em 0.
     * @throws SourceUnavailable quando a busca do site não pode ser usada.
     * @throws SourceError para falhas temporárias (rede, HTTP, resposta inesperada).
     */
    suspend fun search(term: String, page: Int = 0): List<OnlineOffer>
}

/** Erros tipados das fontes online. */
sealed class SourceException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A fonte não oferece busca utilizável (sem API pública, exige login, mudou...). Não adianta tentar de novo. */
class SourceUnavailable(message: String, cause: Throwable? = null) : SourceException(message, cause)

/** Falha temporária: sem rede, tempo esgotado, HTTP de erro ou resposta inesperada. Pode tentar de novo. */
class SourceError(message: String, cause: Throwable? = null) : SourceException(message, cause)
