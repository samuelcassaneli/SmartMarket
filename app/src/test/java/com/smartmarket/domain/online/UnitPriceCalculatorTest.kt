package com.smartmarket.domain.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitPriceCalculatorTest {

    @Test
    fun `preco por kg a partir de gramas`() {
        val u = UnitPriceCalculator.compute("Leite Condensado Italac Integral 395g", 6.89)!!
        assertEquals(UnitPrice.KG, u.unit)
        assertEquals(17.44, u.value, 0.01)
    }

    @Test
    fun `preco por litro a partir de ml e litros`() {
        val oleo = UnitPriceCalculator.compute("Óleo de Soja 900ml", 7.20)!!
        assertEquals(UnitPrice.LITER, oleo.unit)
        assertEquals(8.0, oleo.value, 0.001)

        val amaciante = UnitPriceCalculator.compute("Amaciante Concentrado 1,5L", 15.0)!!
        assertEquals(UnitPrice.LITER, amaciante.unit)
        assertEquals(10.0, amaciante.value, 0.001)
    }

    @Test
    fun `pacote com varias unidades de tamanho multiplica`() {
        val u = UnitPriceCalculator.compute("Cerveja Lata 350ml c/12", 42.0)!!
        assertEquals(UnitPrice.LITER, u.unit)
        assertEquals(10.0, u.value, 0.001) // 42 / 4,2 L
    }

    @Test
    fun `pacote sem tamanho vira preco por unidade`() {
        val u = UnitPriceCalculator.compute("Papel Higiênico Folha Dupla c/ 12", 24.0)!!
        assertEquals(UnitPrice.UNIT, u.unit)
        assertEquals(2.0, u.value, 0.001)
    }

    @Test
    fun `granel usa o multiplicador da fonte`() {
        val u = UnitPriceCalculator.compute("Banana Prata", 3.5, unitMultiplier = 0.5, measurementUnit = "kg")!!
        assertEquals(UnitPrice.KG, u.unit)
        assertEquals(7.0, u.value, 0.001)
    }

    @Test
    fun `sem informacao de tamanho nao calcula`() {
        assertNull(UnitPriceCalculator.compute("Pão de queijo", 12.0, 1.0, "un"))
        assertNull(UnitPriceCalculator.compute("Arroz 5kg", 0.0))
    }

    @Test
    fun `mais barato compara o preco normalizado`() {
        val offers = listOf(
            8.5 to UnitPriceCalculator.compute("Leite Condensado 395g", 8.5),   // 21,52/kg
            15.0 to UnitPriceCalculator.compute("Leite Condensado 1kg", 15.0),  // 15,00/kg
            6.0 to UnitPriceCalculator.compute("Creme de leite 200g", 6.0)      // 30,00/kg
        )
        assertEquals(1, UnitPriceCalculator.cheapestIndex(offers))
    }

    @Test
    fun `sem preco por unidade compara o preco cheio`() {
        val offers = listOf(10.0 to null, 7.5 to null, 9.0 to null)
        assertEquals(1, UnitPriceCalculator.cheapestIndex(offers))
        assertNull(UnitPriceCalculator.cheapestIndex(listOf(5.0 to null)))
    }
}
