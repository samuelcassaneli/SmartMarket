package com.smartmarket.domain.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaniVipParserTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("online/$name")) { "fixture $name" }.readText()

    @Test
    fun `dominio real do Spani Online`() {
        val d = SpaniVipParser.parseDomain(fixture("spani_domain.json"))!!
        assertEquals(108L, d.vipFilialId)
        assertEquals(67L, d.orgId)
        assertEquals("spanionline.com.br", d.domainKey)
    }

    @Test
    fun `token anonimo em data`() {
        assertEquals("abc.def", SpaniVipParser.parseToken("""{"success":true,"data":"abc.def"}"""))
        assertNull(SpaniVipParser.parseToken("""{"success":false,"error":"Token not provided."}"""))
    }

    /** Fixture montada com os campos que o bundle JS do site lê (não é resposta capturada). */
    @Test
    fun `busca com oferta preco texto e imagem relativa`() {
        val offers = SpaniVipParser.parseSearch(fixture("spani_search_inferido.json"), "SPANI_ONLINE", "https://img.base")
        assertEquals(2, offers.size)

        val leite = offers[0]
        assertEquals("LEITE CONDENSADO ITALAC 395G", leite.name)
        assertEquals(6.79, leite.price, 0.001)
        assertEquals(7.49, leite.listPrice!!, 0.001)
        assertEquals("https://img.base/produtos/7898080642646.png", leite.imageUrl)
        assertEquals("7898080642646", leite.ean)
        assertEquals("https://www.spanionline.com.br/produtos/detalhe/501/leite-condensado-italac-395g", leite.productUrl)
        assertTrue(leite.available)

        val banana = offers[1]
        assertEquals(2.99, banana.price, 0.001)
        assertNull(banana.listPrice)
        assertEquals(0.5, banana.unitMultiplier!!, 0.0)
        assertEquals("KG", banana.measurementUnit)
        assertEquals("https://cdn.exemplo.com/banana.png", banana.imageUrl)
        assertFalse(banana.available)
    }

    @Test
    fun `escolhe a loja de Resende entre os CDs`() {
        val body = """{"success":true,"data":[
            {"id":3,"nome":"Spani Taubaté","endereco":{"cidade":"Taubaté"}},
            {"id":7,"nome_site":"Loja Resende","endereco":{"logradouro":"Av. Pres. Kennedy","cidade":"Resende"}}
        ]}"""
        val cds = SpaniVipParser.parseCds(body)
        assertEquals(2, cds.size)
        assertEquals(7L, SpaniVipParser.chooseCd(cds, defaultCdId = 3)?.id)
        assertEquals(3L, SpaniVipParser.chooseCd(cds.take(1), defaultCdId = 3)?.id)
    }
}
