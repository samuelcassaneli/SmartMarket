package com.smartmarket.domain.images

import com.smartmarket.domain.matching.FuzzyMatcher
import kotlin.math.abs
import kotlin.math.max

/** Foto candidata: nome do produto na fonte e endereço da imagem. */
data class ImageCandidate(val name: String, val imageUrl: String, val source: String)

/**
 * Regras para a foto ser do MESMO produto: todas as palavras do produto (tipo, marca), a mesma variante
 * ("Ketchup Heinz Zero 600g" não usa a foto do "Ketchup Heinz 600g") e o mesmo tamanho.
 */
object ImageMatch {

    /** Palavras que mudam o produto (além dos qualificadores do [FuzzyMatcher]). */
    private val VARIANTS = setOf(
        "zero", "light", "diet", "picante", "apimentado", "apimentada", "defumado", "defumada", "lactose",
        "integral", "desnatado", "semidesnatado", "sal", "gluten", "organico", "vegano", "vegana",
        "infantil", "kids", "premium", "gourmet", "concentrado", "refil", "sache", "lata", "vidro",
        "baunilha", "morango", "chocolate", "limao", "laranja", "uva", "maracuja", "coco",
        "menta", "lavanda", "forte", "suave", "neutro", "branco",
        "preto", "tinto", "rose", "frango", "carne", "bacon", "queijo", "cebola", "alho", "tomate"
    )

    /** Chave do cache: palavras do produto em ordem alfabética + tamanho + quantidade. */
    fun key(text: String): String {
        val words = FuzzyMatcher.extractKeywords(text).sorted()
        val size = FuzzyMatcher.extractSize(text)?.let { "${it.dimension}${Math.round(it.amount)}" } ?: "-"
        val pack = FuzzyMatcher.extractPackCount(text) ?: 1
        return words.joinToString(" ") + "|" + size + "|" + pack
    }

    /** Palavras que não mudam a foto ("UHT" é todo leite de caixinha; "zero açúcar" = "zero"). */
    private val NEUTRAL = setOf("uht", "acucar", "pet")

    private fun isVariant(word: String) = word !in NEUTRAL && (word in VARIANTS || FuzzyMatcher.isQualifier(word))

    /** Nota de 0 a 1 (quanto menos palavras sobrando, melhor) ou null se não for o mesmo produto. */
    fun score(product: String, candidate: String): Double? {
        val p = FuzzyMatcher.extractKeywords(product)
        val c = FuzzyMatcher.extractKeywords(candidate)
        if (p.isEmpty() || c.isEmpty()) return null
        fun has(list: List<String>, w: String) = list.any { FuzzyMatcher.tokenSimilarity(w, it) >= 0.85 }
        // tudo que o produto diz (tipo, marca, variante) precisa estar na foto
        if (p.any { !has(c, it) }) return null
        // a foto não pode ser de outra variante
        if (c.any { isVariant(it) && !has(p, it) }) return null

        val ps = FuzzyMatcher.extractSize(product)
        val cs = FuzzyMatcher.extractSize(candidate)
        if (ps != null) {
            if (cs == null || cs.dimension != ps.dimension) return null
            if (abs(cs.amount - ps.amount) / max(cs.amount, ps.amount) > 0.10) return null
        }
        val pp = FuzzyMatcher.extractPackCount(product) ?: 1
        val cp = FuzzyMatcher.extractPackCount(candidate) ?: 1
        if (pp != cp) return null

        val extra = c.count { !has(p, it) }
        return 1.0 / (1 + extra) + if (ps != null && cs != null) 0.1 else 0.0
    }

    /** Candidatas aceitas, melhor primeiro. */
    fun rank(product: String, candidates: List<ImageCandidate>): List<ImageCandidate> =
        candidates.filter { it.imageUrl.startsWith("http") }
            .mapNotNull { c -> score(product, c.name)?.let { c to it } }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.imageUrl }
}
