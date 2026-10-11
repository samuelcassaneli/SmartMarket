package com.smartmarket.domain.online

import com.smartmarket.domain.matching.FuzzyMatcher

/** Preço normalizado por unidade de medida: por kg, por litro ou por unidade. */
data class UnitPrice(val value: Double, val unit: String) {
    companion object {
        const val KG = "kg"
        const val LITER = "L"
        const val UNIT = "un"
    }
}

/** Cálculo de preço por kg/L/un a partir do nome do produto ("Arroz 5kg", "Papel c/ 12"...). */
object UnitPriceCalculator {

    fun compute(
        name: String,
        price: Double,
        unitMultiplier: Double? = null,
        measurementUnit: String? = null
    ): UnitPrice? {
        if (price <= 0.0) return null
        val pack = FuzzyMatcher.extractPackCount(name) ?: 1
        val size = FuzzyMatcher.extractSize(name)
        if (size != null && size.amount > 0.0) {
            val total = size.amount * pack // gramas ou mililitros
            return when (size.dimension) {
                'm' -> UnitPrice(price / (total / 1000.0), UnitPrice.KG)
                else -> UnitPrice(price / (total / 1000.0), UnitPrice.LITER)
            }
        }
        // Vendido a granel: a fonte informa a fração (ex.: 0,5 kg por unidade de venda)
        val unit = measurementUnit?.trim()?.lowercase()
        val mult = unitMultiplier?.takeIf { it > 0.0 }
        if (mult != null && unit != null) {
            when (unit) {
                "kg" -> return UnitPrice(price / mult, UnitPrice.KG)
                "g" -> return UnitPrice(price / (mult / 1000.0), UnitPrice.KG)
                "l", "lt" -> return UnitPrice(price / mult, UnitPrice.LITER)
                "ml" -> return UnitPrice(price / (mult / 1000.0), UnitPrice.LITER)
            }
        }
        if (pack > 1) return UnitPrice(price / pack, UnitPrice.UNIT)
        return null
    }

    /**
     * Escolhe a oferta mais barata comparando o preço normalizado.
     * Usa a unidade (kg/L/un) mais comum entre as ofertas; sem preço por unidade, compara o preço cheio.
     * Retorna o índice da melhor, ou null se houver menos de 2 candidatas.
     */
    fun cheapestIndex(prices: List<Pair<Double, UnitPrice?>>): Int? {
        val candidates = prices.withIndex().filter { it.value.first > 0.0 }
        if (candidates.size < 2) return null
        val dominant = candidates.mapNotNull { it.value.second?.unit }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }
        return if (dominant != null && dominant.value >= 2) {
            candidates.filter { it.value.second?.unit == dominant.key }
                .minByOrNull { it.value.second!!.value }?.index
        } else {
            candidates.minByOrNull { it.value.first }?.index
        }
    }
}
