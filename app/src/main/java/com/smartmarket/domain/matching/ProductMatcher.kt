package com.smartmarket.domain.matching

import com.smartmarket.data.dao.ProductDao
import com.smartmarket.data.entity.Product
import com.smartmarket.data.entity.ProductAlias

/** Resultado do casamento de um texto com o catálogo. */
data class MatchOutcome(
    val text: String,
    val product: Product?,          // vínculo automático (alias ou score alto)
    val confidence: Double,
    val viaAlias: Boolean,
    val suggestions: List<FuzzyMatcher.Ranked<Product>>
)

/**
 * Casa textos (nota, encarte, site) com produtos: primeiro pelos aliases que o usuário já
 * confirmou, depois pelo [FuzzyMatcher]. Confirmações viram aliases via [learn].
 */
class ProductMatcher(private val productDao: ProductDao) {

    /** Casa vários textos de uma vez, carregando catálogo e aliases uma única vez. */
    suspend fun matchAll(texts: List<String>): List<MatchOutcome> {
        val products = productDao.getAllProductsSync()
        val byId = products.associateBy { it.id }
        val aliases = productDao.getAllAliases().associateBy { it.normalizedAlias }
        return texts.map { match(it, products, byId, aliases) }
    }

    suspend fun match(text: String): MatchOutcome = matchAll(listOf(text)).first()

    fun match(
        text: String,
        products: List<Product>,
        byId: Map<Long, Product>,
        aliases: Map<String, ProductAlias>
    ): MatchOutcome {
        val key = aliasKey(text)
        val alias = aliases[key]?.let { byId[it.productId] }
        val ranked = FuzzyMatcher.rank(text, products, { it.name })
        if (alias != null) {
            return MatchOutcome(text, alias, 1.0, viaAlias = true, suggestions = ranked)
        }
        val best = ranked.firstOrNull()
        val auto = best?.takeIf { it.score >= FuzzyMatcher.AUTO_MATCH_THRESHOLD }
        return MatchOutcome(
            text = text,
            product = auto?.item,
            confidence = best?.score ?: 0.0,
            viaAlias = false,
            suggestions = ranked
        )
    }

    /** Registra que [text] corresponde a [productId]. */
    suspend fun learn(text: String, productId: Long, source: String) {
        val key = aliasKey(text)
        if (key.length < 3) return
        val existing = productDao.findAlias(key)
        productDao.upsertAlias(
            ProductAlias(id = existing?.id ?: 0, productId = productId, normalizedAlias = key, source = source)
        )
    }

    companion object {
        /** Chave do alias: texto normalizado sem números soltos de preço/código. */
        fun aliasKey(text: String): String = FuzzyMatcher.normalize(text)
            .replace("[,.]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}
