package com.smartmarket.domain.matching

import java.util.Locale

/**
 * Marca da oferta, deduzida pela diferença entre o texto do encarte/site e o nome do produto cadastrado:
 * produto "Óleo de Soja 500ml" + encarte "Óleo de Soja Soya 500ml" = "Soya".
 */
object BrandGuess {

    private val GENERIC = setOf(
        "tipo", "tipo1", "pacote", "garrafa", "pet", "lata", "caixa", "sache", "refil", "pote", "bandeja", "fardo",
        "oferta", "leve", "pague", "cada", "atacado", "varejo", "app", "cartao", "clube", "rs", "preco", "promocao",
        "original", "embalagem", "economica", "familia", "gratis", "diversos", "varios", "sortidos", "cores",
        "aromas", "fragrancias", "nacional", "importado", "granel", "kilo", "quilo", "un", "und", "unidade",
        "rolos", "rolo", "folhas", "metros", "pacotes", "lt", "ml", "kg", "gr", "g", "l", "x"
    )
    private val SIZE = Regex("""\d+(?:[.,]\d+)?\s*(?:kg|g|gr|grs|mg|ml|l|lt|lts|litros?|un|und|m)\b""", RegexOption.IGNORE_CASE)

    fun extract(offerText: String, productName: String?): String? {
        if (productName.isNullOrBlank()) return null
        val product = FuzzyMatcher.extractKeywords(productName)
        val words = SIZE.replace(offerText, " ")
            .split(Regex("""[\s/,.;:()\-+*]+"""))
            .filter { it.isNotBlank() }
        val brand = words.filter { w ->
            val n = FuzzyMatcher.normalize(w)
            n.length >= 2 && n.any(Char::isLetter) && !n.any(Char::isDigit) &&
                n !in GENERIC && !FuzzyMatcher.isStopWord(n) && !FuzzyMatcher.isQualifier(n) &&
                product.none { FuzzyMatcher.tokenSimilarity(n, it) >= 0.75 } &&
                FuzzyMatcher.extractKeywords(n).let { kw -> kw.isNotEmpty() && kw.none { k -> product.any { FuzzyMatcher.tokenSimilarity(k, it) >= 0.75 } } }
        }.distinctBy { FuzzyMatcher.normalize(it) }.take(2)
        if (brand.isEmpty()) return null
        return brand.joinToString(" ") { w ->
            val lower = w.lowercase(Locale("pt", "BR"))
            lower.replaceFirstChar { it.titlecase(Locale("pt", "BR")) }
        }
    }
}
