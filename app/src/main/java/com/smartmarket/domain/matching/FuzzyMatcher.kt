package com.smartmarket.domain.matching

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object FuzzyMatcher {

    // Stop words e ruídos típicos de encartes brasileiros de supermercado
    private val STOP_WORDS = setOf(
        "de", "do", "da", "dos", "das", "em", "para", "com", "sem", "por",
        "un", "und", "unidade", "unidades", "kg", "g", "gr", "gramas", "kilo", "quilo",
        "l", "lt", "litro", "litros", "ml", "pct", "pacote", "cx", "caixa",
        "cada", "leves", "pague", "oferta", "ofertas", "promocao", "super",
        "sabores", "tipo", "tradicional", "especial", "novo", "nova", "preço", "preco",
        "nesta", "embalagem", "gratis", "desconto", "clube", "cliente"
    )

    /**
     * Remove acentos, pontuações, caracteres especiais e converte para minúsculas.
     */
    fun normalize(text: String): String {
        val nfd = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return nfd.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    /**
     * Extrai tokens limpos ignorando stop words e tamanhos irrelevantes
     */
    fun extractKeywords(text: String): Set<String> {
        val norm = normalize(text)
        return norm.split(" ")
            .map { it.trim() }
            .filter { it.length >= 2 && !STOP_WORDS.contains(it) }
            .toSet()
    }

    /**
     * Distância de Levenshtein padrão
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1,
                    min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                )
            }
        }
        return dp[m][n]
    }

    /**
     * Calcula afinidade entre texto detectado no encarte e nome do produto cadastrado.
     * Dá peso crucial a tokens substantivos (ex: se o produto é 'Leite Integral' e o encarte diz 'Leite Condensado',
     * detecta conflito negativo).
     */
    fun calculateAffinity(detectedText: String, productName: String): Double {
        val normDetected = normalize(detectedText)
        val normProduct = normalize(productName)

        if (normDetected == normProduct) return 1.0

        val detKeywords = extractKeywords(detectedText)
        val prodKeywords = extractKeywords(productName)

        if (detKeywords.isEmpty() || prodKeywords.isEmpty()) return 0.0

        // Interseção de palavras-chave
        val commonKeywords = detKeywords.intersect(prodKeywords)
        if (commonKeywords.isEmpty()) return 0.0

        // Diferença que pode caracterizar produto totalmente diferente:
        // Ex: "leite condensado" x "leite integral" -> common: "leite", mas "condensado" x "integral" são antagônicos
        val hasAntagonism = (detKeywords.contains("condensado") && !prodKeywords.contains("condensado")) ||
                (detKeywords.contains("po") && !prodKeywords.contains("po") && detKeywords.contains("leite")) ||
                (detKeywords.contains("creme") && !prodKeywords.contains("creme")) ||
                (detKeywords.contains("molho") && !prodKeywords.contains("molho"))

        if (hasAntagonism) {
            return 0.15 // Penalidade severa para evitar falsos positivos grosseiros
        }

        // Similaridade Jaccard ponderada pelas keywords do produto
        // Se a maioria das palavras do produto está no encarte: pontuação alta
        val recall = commonKeywords.size.toDouble() / prodKeywords.size.toDouble()
        val precision = commonKeywords.size.toDouble() / detKeywords.size.toDouble()
        val f1 = if (precision + recall > 0) (2 * precision * recall) / (precision + recall) else 0.0

        // Levenshtein geral
        val maxLen = max(normDetected.length, normProduct.length)
        val levScore = 1.0 - (levenshteinDistance(normDetected, normProduct).toDouble() / maxLen.toDouble())

        val score = (f1 * 0.7) + (levScore * 0.3)
        return min(1.0, max(0.0, score))
    }
}
