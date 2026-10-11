package com.smartmarket.domain.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineSearchCacheTest {

    private val offer = OnlineOffer(sourceId = "S", name = "Leite", price = 5.0)

    @Test
    fun `entrada expira depois de 6h`() {
        var now = 0L
        val cache = OnlineSearchCache(clock = { now })
        cache.put("S", "leite", 0, listOf(offer))
        now = OnlineSearchCache.DEFAULT_TTL - 1
        assertNotNull(cache.get("S", "leite", 0))
        now = OnlineSearchCache.DEFAULT_TTL + 1
        assertNull(cache.get("S", "leite", 0))
    }

    @Test
    fun `chave ignora maiusculas acentos e espacos`() {
        val cache = OnlineSearchCache(clock = { 0L })
        cache.put("S", "Feijão  Carioca", 0, listOf(offer))
        assertEquals(1, cache.get("S", "feijao carioca", 0)?.size)
        assertNull(cache.get("S", "feijao carioca", 1))
        assertNull(cache.get("OUTRA", "feijao carioca", 0))
    }

    @Test
    fun `invalidate limpa so a fonte`() {
        val cache = OnlineSearchCache(clock = { 0L })
        cache.put("A", "x", 0, listOf(offer))
        cache.put("B", "x", 0, listOf(offer))
        cache.invalidate("A")
        assertNull(cache.get("A", "x", 0))
        assertNotNull(cache.get("B", "x", 0))
    }
}
