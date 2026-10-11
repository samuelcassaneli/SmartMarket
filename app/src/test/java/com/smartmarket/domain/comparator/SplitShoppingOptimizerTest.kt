package com.smartmarket.domain.comparator

import com.smartmarket.domain.comparator.SplitShoppingOptimizer.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitShoppingOptimizerTest {

    private val arroz = Line(1, "Arroz 5kg", 1.0)
    private val feijao = Line(2, "Feijão 1kg", 2.0)
    private val oleo = Line(3, "Óleo 900ml", 3.0)
    private val cafe = Line(4, "Café 500g", 2.0)

    // Mercado 10: barato em arroz/feijão; 20: barato em óleo/café; 30: caro em tudo
    private val prices = mapOf(
        10L to mapOf(1L to 25.0, 2L to 8.0, 3L to 9.0, 4L to 20.0),
        20L to mapOf(1L to 32.0, 2L to 10.0, 3L to 6.0, 4L to 14.0),
        30L to mapOf(1L to 30.0, 2L to 9.5, 3L to 8.5, 4L to 19.0)
    )

    @Test
    fun `sugere dividir entre os dois mercados complementares`() {
        val lines = listOf(arroz, feijao, oleo, cafe)
        // Únicos: 10 = 25+16+27+40 = 108; 20 = 32+20+18+28 = 98; 30 = 30+19+25,5+38 = 112,5
        val plan = SplitShoppingOptimizer.optimize(lines, prices)
        assertNotNull(plan)
        plan!!
        assertEquals(20L, plan.bestSingleMarketId)
        assertEquals(98.0, plan.bestSingleTotal, 0.001)
        // Divisão 10+20: arroz 25 + feijão 16 (10) ; óleo 18 + café 28 (20) = 87
        assertEquals(87.0, plan.total, 0.001)
        assertEquals(11.0, plan.savings, 0.001)
        assertEquals(setOf(10L, 20L), plan.shares.map { it.marketId }.toSet())
        val m10 = plan.shares.first { it.marketId == 10L }
        assertEquals(listOf(1L, 2L), m10.items.map { it.productId })
    }

    @Test
    fun `nao sugere quando a economia nao passa do minimo`() {
        val small = mapOf(
            10L to mapOf(1L to 10.0, 2L to 10.0),
            20L to mapOf(1L to 10.5, 2L to 9.0)
        )
        // Único: 20 = 10,5 + 18 = 28,5 ; divisão = 10 + 18 = 28 -> economia 0,50 < R$ 10
        assertNull(SplitShoppingOptimizer.optimize(listOf(arroz, feijao), small))
    }

    @Test
    fun `respeita o limite percentual em compras grandes`() {
        val big = mapOf(
            10L to mapOf(1L to 500.0, 2L to 500.0),
            20L to mapOf(1L to 512.0, 2L to 490.0)
        )
        // Único 10 = 500 + 1000 = 1500 ; 20 = 512 + 980 = 1492 (melhor)
        // Divisão = 500 + 980 = 1480 -> economia 12 > R$ 10, mas < 3% de 1492 (44,76)
        assertNull(SplitShoppingOptimizer.optimize(listOf(arroz, feijao), big))
    }

    @Test
    fun `itens faltantes - mercado unico com mais cobertura vence e divisao precisa cobri-lo`() {
        val withMissing = mapOf(
            // 10 não tem café
            10L to mapOf(1L to 20.0, 2L to 7.0, 3L to 5.0),
            // 20 tem tudo, mais caro
            20L to mapOf(1L to 35.0, 2L to 12.0, 3L to 9.0, 4L to 15.0),
            // 30 só tem café, baratíssimo
            30L to mapOf(4L to 10.0)
        )
        val lines = listOf(arroz, feijao, oleo, cafe)
        val single = SplitShoppingOptimizer.bestSingle(lines, withMissing)!!
        assertEquals(20L, single.marketId) // única com 4 itens
        assertEquals(4, single.covered)

        val plan = SplitShoppingOptimizer.optimize(lines, withMissing)
        assertNotNull(plan)
        plan!!
        // Único 20 = 35 + 24 + 27 + 30 = 116
        // 10+20: 20 + 14 + 15 + 30 = 79 (economia 37)
        // 10+30: 20 + 14 + 15 + 20 = 69 (economia 47) -> melhor
        assertEquals(setOf(10L, 30L), plan.shares.map { it.marketId }.toSet())
        assertEquals(47.0, plan.savings, 0.001)
        assertEquals(0, plan.extraItemsCovered)
        assertTrue(plan.first.subtotal >= plan.second.subtotal)
    }

    @Test
    fun `par que nao cobre os itens do mercado unico e ignorado`() {
        val p = mapOf(
            10L to mapOf(1L to 30.0, 2L to 10.0, 3L to 10.0),
            20L to mapOf(1L to 5.0),
            30L to mapOf(2L to 1.0)
        )
        // 20+30 não têm óleo; 10+20 e 10+30 são válidos
        val plan = SplitShoppingOptimizer.optimize(listOf(arroz, feijao, oleo), p)
        assertNotNull(plan)
        assertTrue(plan!!.shares.any { it.marketId == 10L })
    }

    @Test
    fun `menos de dois mercados ou itens retorna null`() {
        assertNull(SplitShoppingOptimizer.optimize(listOf(arroz, feijao), mapOf(10L to mapOf(1L to 1.0, 2L to 1.0))))
        assertNull(SplitShoppingOptimizer.optimize(listOf(arroz), prices))
    }
}
