package com.smartmarket.domain.pantry

import com.smartmarket.domain.pantry.PantryPlanner.Stock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryPlannerTest {

    @Test
    fun `usa o minimo menos o atual quando e o maior`() {
        assertEquals(4.0, PantryPlanner.restockQuantity(current = 1.0, minQuantity = 5.0, monthlyConsumption = 2.0, unit = "un"), 0.0)
    }

    @Test
    fun `usa o consumo mensal quando e o maior`() {
        assertEquals(6.0, PantryPlanner.restockQuantity(current = 0.0, minQuantity = 2.0, monthlyConsumption = 6.0, unit = "pct"), 0.0)
    }

    @Test
    fun `compra ao menos uma unidade`() {
        assertEquals(1.0, PantryPlanner.restockQuantity(current = 3.0, minQuantity = 3.0, monthlyConsumption = 2.0, unit = "un"), 0.0)
    }

    @Test
    fun `arredonda para cima em unidades inteiras`() {
        assertEquals(3.0, PantryPlanner.restockQuantity(current = 0.5, minQuantity = 2.6, monthlyConsumption = 0.0, unit = "un"), 0.0)
        assertEquals(2.0, PantryPlanner.restockQuantity(current = 1.0, minQuantity = 3.0, monthlyConsumption = 0.0, unit = "cx"), 0.0)
    }

    @Test
    fun `kg e litro aceitam fracao`() {
        assertEquals(1.5, PantryPlanner.restockQuantity(current = 0.5, minQuantity = 2.0, monthlyConsumption = 0.0, unit = "kg"), 1e-9)
        assertEquals(2.5, PantryPlanner.restockQuantity(current = 0.5, minQuantity = 1.0, monthlyConsumption = 3.0, unit = "L"), 1e-9)
        assertTrue(PantryPlanner.isFractionalUnit(" KG "))
        assertFalse(PantryPlanner.isFractionalUnit("g"))
    }

    @Test
    fun `plano inclui so itens abaixo do minimo em ordem alfabetica`() {
        val items = listOf(
            Stock(1, "Sabão", "un", quantity = 5.0, minQuantity = 2.0, monthlyConsumption = 1.0),
            Stock(2, "Café", "un", quantity = 0.0, minQuantity = 1.0, monthlyConsumption = 2.0),
            Stock(3, "Arroz", "kg", quantity = 1.0, minQuantity = 2.0, monthlyConsumption = 5.0)
        )
        val plan = PantryPlanner.plan(items)
        assertEquals(listOf(3L, 2L), plan.map { it.productId })
        assertEquals(4.0, plan[0].quantity, 1e-9)
        assertEquals(2.0, plan[1].quantity, 0.0)
    }

    @Test
    fun `estimativa de consumo usa historico quando existe`() {
        assertEquals(2.0, PantryPlanner.estimateMonthlyConsumption(defaultMonthly = 1.0, boughtInPeriod = 6.0), 1e-9)
        assertEquals(1.0, PantryPlanner.estimateMonthlyConsumption(defaultMonthly = 1.0, boughtInPeriod = 0.0), 1e-9)
    }
}
