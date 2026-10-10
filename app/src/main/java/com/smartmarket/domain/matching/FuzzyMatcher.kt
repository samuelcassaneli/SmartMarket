package com.smartmarket.domain.matching

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object FuzzyMatcher {

    /**
     * Remove acentos, pontuações e converte para minúsculas.
     */
    fun normalize(text: String): String {
        val nfd = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return nfd.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    /**
     * Distância de Levenshtein entre duas strings
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
                    dp[i - 1][j] + 1, // remoção
                    min(
                        dp[i][j - 1] + 1, // inserção
                        dp[i - 1][j - 1] + cost // substituição
                    )
                )
            }
        }
        return dp[m][n]
    }

    /**
     * Retorna a similaridade entre 0.0 e 1.0 (onde 1.0 é idêntico)
     */
    fun similarity(s1: String, s2: String): Double {
        val norm1 = normalize(s1)
        val norm2 = normalize(s2)

        if (norm1 == norm2) return 1.0
        if (norm1.isEmpty() || norm2.isEmpty()) return 0.0

        // Similaridade por tokens/palavras (Jaccard-like)
        val tokens1 = norm1.split(" ").filter { it.length > 2 }.toSet()
        val tokens2 = norm2.split(" ").filter { it.length > 2 }.toSet()

        var tokenScore = 0.0
        if (tokens1.isNotEmpty() && tokens2.isNotEmpty()) {
            val intersection = tokens1.intersect(tokens2).size
            val union = tokens1.union(tokens2).size
            tokenScore = intersection.toDouble() / union.toDouble()
        }

        // Levenshtein Score
        val maxLen = max(norm1.length, norm2.length)
        val distance = levenshteinDistance(norm1, norm2)
        val levenshteinScore = 1.0 - (distance.toDouble() / maxLen.toDouble())

        // Se uma string estiver contida inteiramente na outra
        val containsBonus = if (norm1.contains(norm2) || norm2.contains(norm1)) 0.25 else 0.0

        val finalScore = (tokenScore * 0.5) + (levenshteinScore * 0.5) + containsBonus
        return min(1.0, max(0.0, finalScore))
    }
}
