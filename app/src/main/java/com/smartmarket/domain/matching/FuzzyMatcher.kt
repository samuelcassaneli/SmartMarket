package com.smartmarket.domain.matching

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Casamento aproximado entre textos de encarte/nota fiscal/site e nomes de produtos cadastrados.
 *
 * Princípios:
 * - Palavra principal do produto (ex.: "leite", "papel") precisa aparecer no texto detectado.
 * - Qualificadores que mudam o produto ("condensado" x "integral", "zero" x normal) geram conflito.
 * - Tamanhos diferentes (395g x 1L, c/12 x c/4) penalizam.
 * - Abreviações de nota fiscal ("COND", "AMAC", "PAP HIG") casam por prefixo ou dicionário.
 */
object FuzzyMatcher {

    /** Acima disso o vínculo é automático; abaixo, vira sugestão. */
    const val AUTO_MATCH_THRESHOLD = 0.62
    const val SUGGESTION_THRESHOLD = 0.40

    private val STOP_WORDS = setOf(
        "de", "do", "da", "dos", "das", "em", "para", "com", "sem", "por", "e", "a", "o",
        "un", "und", "unid", "unidade", "unidades", "kg", "g", "gr", "gramas", "kilo", "quilo",
        "l", "lt", "litro", "litros", "ml", "pct", "pc", "pacote", "cx", "caixa", "fr", "frasco",
        "cada", "leve", "leves", "pague", "oferta", "ofertas", "promocao", "super",
        "sabores", "sabor", "tipo", "tradicional", "especial", "novo", "nova", "preco",
        "nesta", "embalagem", "gratis", "desconto", "clube", "cliente", "c", "cj", "emb"
    )

    /**
     * Palavras que definem a variante do produto. Se cada lado tiver uma diferente, é outro produto.
     */
    private val QUALIFIERS = setOf(
        "condensado", "integral", "desnatado", "semidesnatado", "zero", "light", "diet",
        "po", "liquido", "creme", "molho", "extrato", "polpa", "suco", "refrigerante", "achocolatado",
        "parboilizado", "arboreo", "branco", "preto", "carioca", "fradinho", "vermelho",
        "refinado", "cristal", "demerara", "mascavo", "organico",
        "moido", "soluvel", "grao", "capsula",
        "fatiado", "ralado", "inteiro", "picado",
        "frango", "bovino", "suino", "peixe", "peito", "coxa", "sobrecoxa", "asa", "file",
        "lava", "loucas", "roupas", "concentrado",
        "fermentado", "uht", "lactose"
    )

    /** Pares de qualificadores que NÃO conflitam entre si (sinônimos ou compatíveis). */
    private val COMPATIBLE_QUALIFIERS = setOf(
        setOf("integral", "uht"),
        setOf("desnatado", "uht"),
        setOf("semidesnatado", "uht"),
        setOf("liquido", "concentrado"),
        setOf("lava", "loucas"),
        setOf("lava", "roupas")
    )

    /** Abreviações comuns em notas fiscais que não são prefixo da palavra completa. */
    private val ABBREVIATIONS = mapOf(
        "fd" to listOf("folha", "dupla"),
        "fs" to listOf("folha", "simples"),
        "t1" to listOf("tipo1"),
        "tp1" to listOf("tipo1"),
        "lv" to listOf("leve"),
        "refri" to listOf("refrigerante"),
        "refrig" to listOf("refrigerante"),
        "achoc" to listOf("achocolatado"),
        "desn" to listOf("desnatado"),
        "semid" to listOf("semidesnatado"),
        "sdesn" to listOf("semidesnatado"),
        "integ" to listOf("integral"),
        "int" to listOf("integral"),
        "hig" to listOf("higienico"),
        "pap" to listOf("papel"),
        "lt" to listOf("leite"),
        "mac" to listOf("macarrao"),
        "esp" to listOf("espaguete"),
        "bisc" to listOf("biscoito"),
        "req" to listOf("requeijao"),
        "mus" to listOf("mussarela"),
        "muss" to listOf("mussarela"),
        "marg" to listOf("margarina"),
        "maion" to listOf("maionese"),
        "extr" to listOf("extrato"),
        "tom" to listOf("tomate"),
        "frang" to listOf("frango"),
        "cerv" to listOf("cerveja"),
        "sab" to listOf("sabao"),
        "det" to listOf("detergente"),
        "amac" to listOf("amaciante"),
        "cond" to listOf("condensado"),
        "conc" to listOf("concentrado"),
        "liq" to listOf("liquido"),
        "ref" to listOf("refinado"),
        "car" to listOf("carioca"),
        "parb" to listOf("parboilizado"),
        "choc" to listOf("chocolate"),
        "beb" to listOf("bebida"),
        "oleo" to listOf("oleo"),
        "acuc" to listOf("acucar")
    )

    private val SIZE_REGEX = Regex("""(\d+(?:[.,]\d+)?)\s*(kg|g|gr|grs|mg|ml|l|lt|lts|litros?)\b""")
    private val PACK_REGEX = Regex("""\b(?:c\s*/?\s*|com\s+|cx\s*|pct\s*|x\s*)(\d{1,3})\s*(?:un|und|unid|unidades|rolos?)?\b|\b(\d{1,3})\s*(?:un|und|unid|unidades|rolos)\b""")

    /** Remove acentos, pontuação e converte para minúsculas. */
    fun normalize(text: String): String {
        val nfd = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return nfd.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace("[^a-z0-9/ ,.]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    /** Tamanho da embalagem normalizado (gramas ou mililitros), se houver. */
    data class PackageSize(val amount: Double, val dimension: Char) // 'm' massa (g), 'v' volume (ml)

    fun extractSize(text: String): PackageSize? {
        val match = SIZE_REGEX.find(normalize(text)) ?: return null
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        return when (match.groupValues[2]) {
            "kg" -> PackageSize(value * 1000, 'm')
            "g", "gr", "grs" -> PackageSize(value, 'm')
            "mg" -> PackageSize(value / 1000, 'm')
            "ml" -> PackageSize(value, 'v')
            else -> PackageSize(value * 1000, 'v') // l, lt, lts, litro(s)
        }
    }

    /** Quantidade de unidades no pacote ("c/ 12", "12 rolos"), se houver. */
    fun extractPackCount(text: String): Int? {
        val match = PACK_REGEX.find(normalize(text)) ?: return null
        val raw = match.groupValues[1].ifEmpty { match.groupValues[2] }
        return raw.toIntOrNull()?.takeIf { it in 2..500 }
    }

    /** Palavras-chave sem stop words, números de tamanho e com abreviações expandidas. */
    fun extractKeywords(text: String): List<String> {
        val norm = normalize(text)
            .replace(SIZE_REGEX, " ")
            .replace("[/,.]".toRegex(), " ")
            .replace("\\btipo\\s*1\\b".toRegex(), " tipo1 ")
        val out = mutableListOf<String>()
        for (token in norm.split(" ")) {
            if (token.isBlank()) continue
            val expanded = ABBREVIATIONS[token] ?: listOf(token)
            for (t in expanded) {
                if (t.length < 2 && t != "po") continue
                if (STOP_WORDS.contains(t)) continue
                if (t.all { it.isDigit() }) continue
                if (!out.contains(t)) out.add(t)
            }
        }
        return out
    }

    fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(dp[i - 1][j] + 1, min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost))
            }
        }
        return dp[m][n]
    }

    /** Similaridade entre duas palavras: exata, abreviação por prefixo ou erro de OCR. */
    fun tokenSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val (short, long) = if (a.length <= b.length) a to b else b to a
        if (short.length >= 3 && long.startsWith(short)) return 0.85
        if (short.length >= 4) {
            val dist = levenshteinDistance(a, b)
            val sim = 1.0 - dist.toDouble() / max(a.length, b.length)
            if (sim >= 0.75) return sim * 0.9
        }
        return 0.0
    }

    private fun bestMatch(token: String, candidates: List<String>): Double =
        candidates.maxOfOrNull { tokenSimilarity(token, it) } ?: 0.0

    /** Palavra que descreve a variante (refinado, integral...), não a marca. */
    fun isQualifier(word: String): Boolean = normalize(word) in QUALIFIERS

    /** Palavra sem significado próprio (de, kg, unidade, oferta...). */
    fun isStopWord(word: String): Boolean = normalize(word) in STOP_WORDS

    private fun qualifiersOf(tokens: List<String>): Set<String> = tokens.filter { it in QUALIFIERS }.toSet()

    private fun isCompatible(a: String, b: String) = COMPATIBLE_QUALIFIERS.any { it.contains(a) && it.contains(b) }

    /**
     * Afinidade entre 0 e 1 entre o texto detectado e o nome de um produto cadastrado.
     */
    fun calculateAffinity(detectedText: String, productName: String): Double {
        val normDetected = normalize(detectedText)
        val normProduct = normalize(productName)
        if (normDetected.isEmpty() || normProduct.isEmpty()) return 0.0
        if (normDetected == normProduct) return 1.0

        val det = extractKeywords(detectedText)
        val prod = extractKeywords(productName)
        if (det.isEmpty() || prod.isEmpty()) return 0.0

        // Palavra principal (primeira do nome do produto) precisa aparecer
        val headScore = bestMatch(prod.first(), det)

        val recall = prod.sumOf { bestMatch(it, det) } / prod.size
        val precision = det.sumOf { bestMatch(it, prod) } / det.size
        if (recall == 0.0) return 0.0

        var score = recall * 0.75 + precision * 0.25
        if (headScore < 0.8) score *= 0.4

        // Qualificadores: variantes diferentes do mesmo item
        val qDet = qualifiersOf(det.map { t -> QUALIFIERS.firstOrNull { q -> tokenSimilarity(t, q) >= 0.85 } ?: t })
        val qProd = qualifiersOf(prod)
        val onlyDet = qDet.filter { d -> qProd.none { it == d || isCompatible(it, d) } }
        val onlyProd = qProd.filter { p -> qDet.none { it == p || isCompatible(it, p) } }
        when {
            onlyDet.isNotEmpty() && onlyProd.isNotEmpty() -> score = min(score, 0.15)
            onlyDet.isNotEmpty() -> score *= 0.6   // texto é uma variante que o produto não tem
            onlyProd.isNotEmpty() -> score *= 0.85 // texto genérico demais
        }

        // Tamanho da embalagem
        val sDet = extractSize(detectedText)
        val sProd = extractSize(productName)
        if (sDet != null && sProd != null) {
            if (sDet.dimension != sProd.dimension) {
                score *= 0.5
            } else {
                val ratio = abs(sDet.amount - sProd.amount) / max(sDet.amount, sProd.amount)
                if (ratio > 0.15) score *= 0.6
            }
        }

        // Granel x pacote ("Alho kg" x "Alho 60g") é outro produto para efeito de preço
        val fDet = SaleForm.ofText(detectedText)
        val fProd = SaleForm.ofText(productName)
        if ((fDet == SaleForm.Bulk) != (fProd == SaleForm.Bulk) && fDet.isKnown && fProd.isKnown &&
            SaleForm.comparablePrice(fProd, fDet, 1.0) == null
        ) score *= 0.5

        val pDet = extractPackCount(detectedText)
        val pProd = extractPackCount(productName)
        if (pDet != null && pProd != null && pDet != pProd) score *= 0.7

        return min(1.0, max(0.0, score))
    }

    data class Ranked<T>(val item: T, val score: Double)

    /** Ordena os candidatos pela afinidade com o texto, descartando os muito fracos. */
    fun <T> rank(text: String, candidates: List<T>, nameOf: (T) -> String, limit: Int = 5): List<Ranked<T>> =
        candidates.asSequence()
            .map { Ranked(it, calculateAffinity(text, nameOf(it))) }
            .filter { it.score >= SUGGESTION_THRESHOLD }
            .sortedByDescending { it.score }
            .take(limit)
            .toList()
}
