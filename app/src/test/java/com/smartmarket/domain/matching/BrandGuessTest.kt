package com.smartmarket.domain.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrandGuessTest {
    @Test
    fun brandFromFlyerText() {
        assertEquals("Soya", BrandGuess.extract("ÓLEO DE SOJA SOYA 500ML", "Oleo de Soja 500ml"))
        assertEquals("Galo", BrandGuess.extract("Óleo de Soja 500ml Galo", "Oleo de Soja 500ml"))
        assertEquals("União", BrandGuess.extract("Açúcar Refinado União 1kg", "Açúcar 1kg"))
        assertEquals("Ypê", BrandGuess.extract("Detergente Líquido Ypê 500ml", "Detergente 500ml"))
    }

    @Test
    fun noBrandWhenNothingExtra() {
        assertNull(BrandGuess.extract("Óleo de Soja 500ml", "Oleo de Soja 500ml"))
        assertNull(BrandGuess.extract("Alho kg", "Alho kg"))
        assertNull(BrandGuess.extract("Óleo de Soja Soya", null))
    }
}
