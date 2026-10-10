package com.smartmarket.domain.ocr

import android.graphics.Rect
import com.smartmarket.data.entity.Product
import com.smartmarket.domain.matching.FuzzyMatcher
import java.util.regex.Pattern
import kotlin.math.abs

data class ExtractedOcrItem(
    val detectedText: String,
    val detectedPrice: Double,
    val matchedProduct: Product?,
    val matchConfidence: Double,
    val boundingBox: Rect? = null,
    val isConfirmed: Boolean = true
)

object EncarteParser {

    // Regex para detectar valores monetários típicos em encartes brasileiros:
    // Ex: "R$ 19,90", "19,90", "R$ 4.89", "RS 9,99", "14,99 un", "8,50 kg"
    private val PRICE_REGEX = Pattern.compile(
        """(?:r\$|rs|\$)?\s*(\d{1,4}[,\.]\d{2})""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Extrai possíveis preços de um texto de bloco ou linha.
     */
    fun extractPrices(text: String): List<Double> {
        val matcher = PRICE_REGEX.matcher(text)
        val prices = mutableListOf<Double>()
        while (matcher.find()) {
            val rawPrice = matcher.group(1)?.replace(",", ".")
            rawPrice?.toDoubleOrNull()?.let {
                // Filtro para ignorar números absurdos ou irrelevantes como anos (ex: 2026)
                if (it > 0.10 && it < 10000.0) {
                    prices.add(it)
                }
            }
        }
        return prices
    }

    data class RawTextElement(
        val text: String,
        val boundingBox: Rect?
    )

    /**
     * Processa linhas detectadas pelo ML Kit, correlacionando textos de produtos
     * com preços próximos espacialmente ou na mesma linha.
     */
    fun processDetectedElements(
        elements: List<RawTextElement>,
        registeredProducts: List<Product>
    ): List<ExtractedOcrItem> {
        val result = mutableListOf<ExtractedOcrItem>()
        val usedPriceElements = mutableSetOf<Int>()

        // Primeiro passo: identificar elementos que são puramente preços e os que têm descrições
        for (i in elements.indices) {
            val elem = elements[i]
            val pricesInElem = extractPrices(elem.text)

            // Se o próprio elemento já tem texto + preço (ex: "Leite Integral Piracanjuba R$ 4,89")
            if (pricesInElem.isNotEmpty() && elem.text.length > 8) {
                val cleanedText = elem.text.replace(PRICE_REGEX.toRegex(), "").trim()
                if (cleanedText.length >= 3) {
                    val (bestProduct, score) = findBestMatch(cleanedText, registeredProducts)
                    result.add(
                        ExtractedOcrItem(
                            detectedText = cleanedText,
                            detectedPrice = pricesInElem.first(),
                            matchedProduct = bestProduct,
                            matchConfidence = score,
                            boundingBox = elem.boundingBox
                        )
                    )
                    usedPriceElements.add(i)
                    continue
                }
            }
        }

        // Segundo passo: para textos sem preço, buscar preço espacialmente mais próximo
        for (i in elements.indices) {
            if (i in usedPriceElements) continue
            val textElem = elements[i]
            val prices = extractPrices(textElem.text)
            if (prices.isNotEmpty()) continue // É apenas preço

            val cleaned = textElem.text.trim()
            if (cleaned.length < 3) continue

            val (bestProduct, score) = findBestMatch(cleaned, registeredProducts)
            if (score >= 0.40) { // Candidato a produto cadastrado
                // Procurar preço mais próximo (abaixo ou ao lado)
                var closestPrice: Double? = null
                var minDistance = Double.MAX_VALUE
                var closestIndex = -1

                for (j in elements.indices) {
                    if (j == i) continue
                    val otherElem = elements[j]
                    val otherPrices = extractPrices(otherElem.text)
                    if (otherPrices.isNotEmpty()) {
                        val dist = calculateDistance(textElem.boundingBox, otherElem.boundingBox)
                        if (dist < minDistance && dist < 400.0) { // Distância razoável em pixels
                            minDistance = dist
                            closestPrice = otherPrices.first()
                            closestIndex = j
                        }
                    }
                }

                if (closestPrice != null) {
                    result.add(
                        ExtractedOcrItem(
                            detectedText = cleaned,
                            detectedPrice = closestPrice,
                            matchedProduct = bestProduct,
                            matchConfidence = score,
                            boundingBox = textElem.boundingBox
                        )
                    )
                    if (closestIndex != -1) usedPriceElements.add(closestIndex)
                }
            }
        }

        return result
    }

    private fun findBestMatch(
        text: String,
        registeredProducts: List<Product>
    ): Pair<Product?, Double> {
        var bestProduct: Product? = null
        var bestScore = 0.0

        for (product in registeredProducts) {
            val score = FuzzyMatcher.similarity(text, product.name)
            if (score > bestScore) {
                bestScore = score
                bestProduct = product
            }
        }

        return Pair(if (bestScore >= 0.35) bestProduct else null, bestScore)
    }

    private fun calculateDistance(r1: Rect?, r2: Rect?): Double {
        if (r1 == null || r2 == null) return Double.MAX_VALUE
        // Distância entre o centro dos retângulos
        val cx1 = r1.centerX()
        val cy1 = r1.centerY()
        val cx2 = r2.centerX()
        val cy2 = r2.centerY()

        // Ponderar: em encartes, o preço frequentemente fica ligeiramente abaixo do texto
        val dx = abs(cx1 - cx2)
        val dy = cy2 - cy1 // positivo se r2 estiver abaixo de r1
        val verticalPenalty = if (dy < -50) 2.0 else 1.0 // penaliza se o preço estiver muito acima

        return kotlin.math.sqrt((dx * dx + (dy * verticalPenalty) * (dy * verticalPenalty)).toDouble())
    }
}
