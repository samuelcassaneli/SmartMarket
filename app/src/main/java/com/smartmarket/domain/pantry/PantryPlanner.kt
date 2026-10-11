package com.smartmarket.domain.pantry

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.round

/** Lógica pura da despensa: o que está acabando e quanto repor. */
object PantryPlanner {

    data class Stock(
        val productId: Long,
        val productName: String,
        val unit: String,
        val quantity: Double,
        val minQuantity: Double,
        /** Consumo mensal estimado (cadastro do produto ou histórico de compras). */
        val monthlyConsumption: Double
    )

    data class RestockLine(val productId: Long, val productName: String, val quantity: Double)

    private val FRACTIONAL_UNITS = setOf("kg", "l", "lt", "litro", "litros", "quilo", "quilos")

    /** kg e L aceitam quantidades fracionadas; o resto é contado em unidades inteiras. */
    fun isFractionalUnit(unit: String): Boolean = unit.trim().lowercase(Locale.ROOT) in FRACTIONAL_UNITS

    /** Abaixo (ou no) mínimo. Com mínimo zero, só conta quando acabou. */
    fun isLow(quantity: Double, minQuantity: Double): Boolean = quantity <= minQuantity

    /**
     * Quantidade a comprar: max(mínimo − atual, consumo mensal − atual, 1),
     * arredondada para cima em unidades inteiras quando a unidade não for kg ou L.
     */
    fun restockQuantity(current: Double, minQuantity: Double, monthlyConsumption: Double, unit: String): Double {
        val need = maxOf(minQuantity - current, monthlyConsumption - current, 1.0)
        return if (isFractionalUnit(unit)) {
            round(need * 1000.0) / 1000.0
        } else {
            // Tolerância para erros de ponto flutuante (2.0000001 não vira 3)
            ceil(need - 1e-9)
        }
    }

    /**
     * Consumo mensal estimado: média dos últimos [months] meses de compras, quando houver histórico;
     * senão, a quantidade mensal cadastrada no produto.
     */
    fun estimateMonthlyConsumption(defaultMonthly: Double, boughtInPeriod: Double, months: Int = 3): Double =
        if (boughtInPeriod > 0.0 && months > 0) boughtInPeriod / months else defaultMonthly.coerceAtLeast(0.0)

    /** Linhas da lista de reposição: só os itens abaixo do mínimo, em ordem alfabética. */
    fun plan(items: List<Stock>): List<RestockLine> =
        items.filter { isLow(it.quantity, it.minQuantity) }
            .sortedBy { it.productName.lowercase(Locale.ROOT) }
            .map {
                RestockLine(
                    productId = it.productId,
                    productName = it.productName,
                    quantity = restockQuantity(it.quantity, it.minQuantity, it.monthlyConsumption, it.unit)
                )
            }
}
