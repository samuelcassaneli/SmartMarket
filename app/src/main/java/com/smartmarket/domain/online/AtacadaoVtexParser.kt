package com.smartmarket.domain.online

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/** Parser puro (JVM) das respostas da API VTEX do Atacadão. */
object AtacadaoVtexParser {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // ------------------------------------------------------------ Modelos VTEX (somente o que usamos)

    @Serializable
    data class VtexProduct(
        val productId: String = "",
        val productName: String = "",
        val brand: String? = null,
        val link: String? = null,
        val linkText: String? = null,
        val items: List<VtexItem> = emptyList()
    )

    @Serializable
    data class VtexItem(
        val itemId: String = "",
        val name: String = "",
        val nameComplete: String? = null,
        val ean: String? = null,
        val measurementUnit: String? = null,
        val unitMultiplier: Double? = null,
        val images: List<VtexImage> = emptyList(),
        val sellers: List<VtexSeller> = emptyList()
    )

    @Serializable
    data class VtexImage(val imageUrl: String = "")

    @Serializable
    data class VtexSeller(
        val sellerId: String = "",
        val sellerName: String = "",
        val sellerDefault: Boolean = false,
        val commertialOffer: VtexOffer = VtexOffer()
    )

    @Serializable
    data class VtexOffer(
        @SerialName("Price") val price: Double = 0.0,
        @SerialName("ListPrice") val listPrice: Double = 0.0,
        @SerialName("AvailableQuantity") val availableQuantity: Long = 0,
        @SerialName("IsAvailable") val isAvailable: Boolean? = null
    ) {
        val hasStock: Boolean get() = price > 0.0 && availableQuantity > 0 && isAvailable != false
    }

    @Serializable
    data class VtexRegion(
        val id: String = "",
        val sellers: List<VtexRegionSeller> = emptyList()
    )

    @Serializable
    data class VtexRegionSeller(val id: String = "", val name: String = "")

    // ------------------------------------------------------------ Parse

    fun parseProducts(body: String): List<VtexProduct> = json.decodeFromString(body)

    fun parseRegions(body: String): List<VtexRegion> = json.decodeFromString(body)

    /**
     * Converte a resposta da busca em ofertas, escolhendo um seller por item:
     * o da região (se presente) ou o de menor preço com estoque.
     * Itens sem nenhum preço são descartados.
     */
    fun parseOffers(
        body: String,
        sourceId: String,
        preferredSellerIds: Set<String> = emptySet()
    ): List<OnlineOffer> = toOffers(parseProducts(body), sourceId, preferredSellerIds)

    fun toOffers(
        products: List<VtexProduct>,
        sourceId: String,
        preferredSellerIds: Set<String> = emptySet()
    ): List<OnlineOffer> = products.flatMap { p ->
        p.items.mapNotNull { item ->
            val seller = chooseSeller(item.sellers, preferredSellerIds) ?: return@mapNotNull null
            val offer = seller.commertialOffer
            if (offer.price <= 0.0) return@mapNotNull null
            val name = if (p.items.size > 1) item.nameComplete?.takeIf { it.isNotBlank() } ?: p.productName else p.productName
            OnlineOffer(
                sourceId = sourceId,
                name = name.trim(),
                brand = p.brand?.takeIf { it.isNotBlank() },
                price = offer.price,
                listPrice = offer.listPrice.takeIf { it > offer.price + 0.009 },
                unitMultiplier = item.unitMultiplier,
                measurementUnit = item.measurementUnit,
                imageUrl = item.images.firstOrNull()?.imageUrl?.takeIf { it.isNotBlank() },
                productUrl = productUrl(p),
                ean = item.ean?.takeIf { it.isNotBlank() },
                available = offer.hasStock,
                sellerId = seller.sellerId,
                sellerName = seller.sellerName
            )
        }
    }

    /**
     * Seller do item: primeiro um da região com estoque; senão o menor preço com estoque;
     * senão (nada em estoque) o de menor preço positivo, marcado como indisponível pelo chamador.
     */
    fun chooseSeller(sellers: List<VtexSeller>, preferredSellerIds: Set<String>): VtexSeller? {
        if (sellers.isEmpty()) return null
        val inStock = sellers.filter { it.commertialOffer.hasStock }
        if (preferredSellerIds.isNotEmpty()) {
            inStock.firstOrNull { it.sellerId in preferredSellerIds }?.let { return it }
        }
        inStock.minByOrNull { it.commertialOffer.price }?.let { return it }
        return sellers.filter { it.commertialOffer.price > 0.0 }.minByOrNull { it.commertialOffer.price }
            ?: sellers.firstOrNull { it.sellerDefault }
            ?: sellers.first()
    }

    private fun productUrl(p: VtexProduct): String? {
        val link = p.link?.takeIf { it.isNotBlank() }
            ?: p.linkText?.takeIf { it.isNotBlank() }?.let { "https://www.atacadao.com.br/$it/p" }
        // A API devolve o host "secure."; o "www." abre a mesma página sem exigir login
        return link?.replace("://secure.atacadao.com.br", "://www.atacadao.com.br")
    }

    /**
     * Cookie `vtex_segment` com a região. A API de catálogo do Atacadão ignora o parâmetro
     * `regionId` na URL, mas aplica preço e estoque da loja quando a região vem neste cookie.
     */
    fun segmentCookie(regionId: String): String {
        val segment = """{"regionId":"$regionId","channel":"1","currencyCode":"BRL","currencySymbol":"R$","countryCode":"BRA","cultureInfo":"pt-BR","channelPrivacy":"public"}"""
        return Base64.getEncoder().encodeToString(segment.toByteArray(Charsets.UTF_8))
    }
}
