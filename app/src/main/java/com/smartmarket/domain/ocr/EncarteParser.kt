package com.smartmarket.domain.ocr

import android.graphics.Rect
import com.smartmarket.data.entity.Product
import com.smartmarket.domain.matching.FuzzyMatcher
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.min

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
    // Ex: "R$ 19,90", "19,90", "R$ 4.89", "RS 9,99", "R$ 0,94"
    private val PRICE_PATTERN = Pattern.compile(
        """(?:r\$|rs|\$)?\s*(\d{1,4}[,\.]\d{2})""",
        Pattern.CASE_INSENSITIVE
    )

    // Palavras que indicam texto descritivo e descartam textos puramente operacionais
    private val JUNK_TEXTS = setOf(
        "unidade", "aunidade", "un", "und", "kg", "cada", "foto ilustrativa",
        "o ministerio da saude adverte", "ofertas validas", "cartao", "spani", "atacado", "varejo"
    )

    data class RawTextElement(
        val text: String,
        val boundingBox: Rect?
    )

    data class DetectedPriceBox(
        val price: Double,
        val box: Rect?
    )

    /**
     * Extrai todos os preços válidos com seus bounding boxes
     */
    fun extractPricesWithBoxes(elements: List<RawTextElement>): List<DetectedPriceBox> {
        val prices = mutableListOf<DetectedPriceBox>()
        for (elem in elements) {
            val matcher = PRICE_PATTERN.matcher(elem.text)
            while (matcher.find()) {
                val raw = matcher.group(1)?.replace(",", ".")
                val p = raw?.toDoubleOrNull()
                // Descartar números fora da faixa de mercado diário (ex: datas como 2026, 2024, números irrelevantes)
                if (p != null && p >= 0.50 && p < 1500.0) {
                    prices.add(DetectedPriceBox(price = p, box = elem.boundingBox))
                }
            }
        }
        return prices
    }

    /**
     * Processa elementos do encarte agrupando blocos coerentes de texto de produto
     * e correlacionando com o preço de oferta mais próximo e abaixo/ao lado do produto.
     */
    fun processDetectedElements(
        elements: List<RawTextElement>,
        registeredProducts: List<Product>
    ): List<ExtractedOcrItem> {
        val result = mutableListOf<ExtractedOcrItem>()
        if (registeredProducts.isEmpty() || elements.isEmpty()) return result

        val priceBoxes = extractPricesWithBoxes(elements)
        val usedPriceBoxes = mutableSetOf<Int>()

        // 1. Filtrar elementos de texto com substância (mais de 4 caracteres, que não sejam apenas preços ou ruídos)
        val validProductCandidates = elements.filter { elem ->
            val clean = elem.text.trim().lowercase()
            val isOnlyPrice = PRICE_PATTERN.matcher(clean).matches()
            val isJunk = JUNK_TEXTS.contains(clean) || clean.length < 4
            !isOnlyPrice && !isJunk
        }

        // Agrupamos candidatos próximos verticalmente (ex: linhas contíguas do mesmo produto)
        val groupedProductTexts = mutableListOf<RawTextElement>()
        for (candidate in validProductCandidates) {
            var merged = false
            for (i in groupedProductTexts.indices) {
                val existing = groupedProductTexts[i]
                if (existing.boundingBox != null && candidate.boundingBox != null) {
                    val vertDist = abs(candidate.boundingBox.top - existing.boundingBox.bottom)
                    val horizDist = abs(candidate.boundingBox.centerX() - existing.boundingBox.centerX())
                    if (vertDist < 50 && horizDist < 120) {
                        // Mesclar textos da mesma etiqueta de produto
                        groupedProductTexts[i] = RawTextElement(
                            text = "${existing.text} ${candidate.text}".trim(),
                            boundingBox = Rect(
                                min(existing.boundingBox.left, candidate.boundingBox.left),
                                min(existing.boundingBox.top, candidate.boundingBox.top),
                                maxOf(existing.boundingBox.right, candidate.boundingBox.right),
                                maxOf(existing.boundingBox.bottom, candidate.boundingBox.bottom)
                            )
                        )
                        merged = true
                        break
                    }
                }
            }
            if (!merged) {
                groupedProductTexts.add(candidate)
            }
        }

        // 2. Para cada bloco de produto identificado, calcular a melhor afinidade contra a lista de produtos cadastrados
        for (prodText in groupedProductTexts) {
            val (bestProduct, score) = findBestAffinityMatch(prodText.text, registeredProducts)

            // Só processamos como item se tiver correspondência razoável (> 0.52) OU se for explicitamente um produto
            if (bestProduct != null && score >= 0.50) {
                // Procurar o preço mais próximo deste produto no encarte
                var bestPrice: Double? = null
                var minDistance = Double.MAX_VALUE
                var bestPriceIndex = -1

                for (pIdx in priceBoxes.indices) {
                    val pBox = priceBoxes[pIdx]
                    val dist = calculateDistanceToPrice(prodText.boundingBox, pBox.box)
                    if (dist < minDistance && dist < 450.0) {
                        minDistance = dist
                        bestPrice = pBox.price
                        bestPriceIndex = pIdx
                    }
                }

                if (bestPrice != null) {
                    result.add(
                        ExtractedOcrItem(
                            detectedText = prodText.text,
                            detectedPrice = bestPrice,
                            matchedProduct = bestProduct,
                            matchConfidence = score,
                            boundingBox = prodText.boundingBox,
                            isConfirmed = true
                        )
                    )
                    if (bestPriceIndex != -1) usedPriceBoxes.add(bestPriceIndex)
                }
            }
        }

        // Eliminar duplicatas (se o mesmo produto cadastrado foi pego 2 vezes pelo mesmo encarte, manter o de maior confiança)
        val deduplicated = result
            .groupBy { it.matchedProduct?.id }
            .mapValues { (_, items) -> items.maxByOrNull { it.matchConfidence }!! }
            .values
            .toList()

        return deduplicated
    }

    private fun findBestAffinityMatch(
        text: String,
        registeredProducts: List<Product>
    ): Pair<Product?, Double> {
        var bestProd: Product? = null
        var maxScore = 0.0

        for (p in registeredProducts) {
            val score = FuzzyMatcher.calculateAffinity(text, p.name)
            if (score > maxScore) {
                maxScore = score
                bestProd = p
            }
        }

        return Pair(if (maxScore >= 0.50) bestProd else null, maxScore)
    }

    private fun calculateDistanceToPrice(itemBox: Rect?, priceBox: Rect?): Double {
        if (itemBox == null || priceBox == null) return Double.MAX_VALUE

        val cx1 = itemBox.centerX()
        val cy1 = itemBox.centerY()
        val cx2 = priceBox.centerX()
        val cy2 = priceBox.centerY()

        val dx = abs(cx1 - cx2)
        val dy = cy2 - cy1 // Positivo se o preço estiver abaixo do nome do produto

        // Em folhetos/encartes, o preço fica tipicamente ABAIXO ou à direita do nome do produto
        val verticalFactor = when {
            dy in 0..250 -> 0.8 // Perfeito: logo abaixo do título
            dy > 250 -> 1.8     // Longe demais para baixo
            dy in -80..0 -> 1.2  // Ao lado ou ligeiramente acima
            else -> 3.5          // Bem acima do produto (improvável)
        }

        return kotlin.math.sqrt((dx * dx + (dy * verticalFactor) * (dy * verticalFactor)).toDouble())
    }
}
