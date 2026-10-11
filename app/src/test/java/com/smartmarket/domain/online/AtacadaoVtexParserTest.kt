package com.smartmarket.domain.online

import com.smartmarket.domain.online.AtacadaoVtexParser.VtexOffer
import com.smartmarket.domain.online.AtacadaoVtexParser.VtexSeller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class AtacadaoVtexParserTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("online/$name")) { "fixture $name" }.readText()

    @Test
    fun `parse da resposta real da VTEX com regiao de Resende`() {
        val offers = AtacadaoVtexParser.parseOffers(fixture("atacadao_search.json"), "VTEX_ATACADAO")

        // A fixture tem 3 produtos; só o leite condensado tem preço na loja de Resende
        assertEquals(1, offers.size)
        val o = offers.first()
        assertEquals("Leite Condensado Italac Integral 395g", o.name)
        assertEquals("Italac", o.brand)
        assertEquals(6.89, o.price, 0.001)
        assertNull("ListPrice igual ao preço não vira preço \"de\"", o.listPrice)
        assertEquals("1", o.sellerId)
        assertEquals("7898080642646", o.ean)
        assertTrue(o.imageUrl!!.startsWith("https://atacadaobr.vteximg.com.br/arquivos/ids/"))
        assertEquals("https://www.atacadao.com.br/leite-condensado-italac-integral-16923/p", o.productUrl)
        assertEquals(1.0, o.unitMultiplier!!, 0.0)
        assertTrue(o.available)
        assertEquals("VTEX_ATACADAO", o.sourceId)
    }

    @Test
    fun `parse das regioes`() {
        val body = """[{"id":"v2.D5FD0A6CC90C6C7DA6EAC99790728321","sellers":[{"id":"atacadaobr207","name":"Resende","logo":""}]}]"""
        val r = AtacadaoVtexParser.parseRegions(body).single()
        assertEquals("v2.D5FD0A6CC90C6C7DA6EAC99790728321", r.id)
        assertEquals("atacadaobr207", r.sellers.single().id)

        val semSeller = AtacadaoVtexParser.parseRegions("""[{"id":"v2.1BB1","sellers":[]}]""").single()
        assertTrue(semSeller.sellers.isEmpty())
    }

    private fun seller(id: String, price: Double, qty: Long = 10) =
        VtexSeller(sellerId = id, sellerName = id, commertialOffer = VtexOffer(price = price, listPrice = price, availableQuantity = qty))

    @Test
    fun `seller da regiao tem preferencia mesmo mais caro`() {
        val sellers = listOf(seller("1", 5.0), seller("atacadaobr207", 6.0), seller("atacadaobr136", 4.0))
        assertEquals("atacadaobr207", AtacadaoVtexParser.chooseSeller(sellers, setOf("atacadaobr207"))?.sellerId)
    }

    @Test
    fun `sem regiao escolhe o menor preco com estoque`() {
        val sellers = listOf(seller("1", 5.0), seller("a", 3.0, qty = 0), seller("b", 4.5))
        assertEquals("b", AtacadaoVtexParser.chooseSeller(sellers, emptySet())?.sellerId)
    }

    @Test
    fun `seller da regiao sem estoque cai para o menor preco disponivel`() {
        val sellers = listOf(seller("atacadaobr207", 3.0, qty = 0), seller("1", 5.0))
        assertEquals("1", AtacadaoVtexParser.chooseSeller(sellers, setOf("atacadaobr207"))?.sellerId)
    }

    @Test
    fun `nada em estoque vira oferta indisponivel`() {
        val p = AtacadaoVtexParser.VtexProduct(
            productName = "Arroz 5kg",
            items = listOf(AtacadaoVtexParser.VtexItem(sellers = listOf(seller("1", 25.0, qty = 0))))
        )
        val o = AtacadaoVtexParser.toOffers(listOf(p), "VTEX_ATACADAO").single()
        assertFalse(o.available)
        assertEquals(25.0, o.price, 0.0)
    }

    @Test
    fun `preco de vira listPrice quando maior`() {
        val p = AtacadaoVtexParser.VtexProduct(
            productName = "Café 500g",
            items = listOf(
                AtacadaoVtexParser.VtexItem(
                    sellers = listOf(VtexSeller("1", "x", true, VtexOffer(price = 15.9, listPrice = 19.9, availableQuantity = 5)))
                )
            )
        )
        val o = AtacadaoVtexParser.toOffers(listOf(p), "VTEX_ATACADAO").single()
        assertEquals(19.9, o.listPrice!!, 0.001)
        assertTrue(o.hasDiscount)
    }

    @Test
    fun `cookie de segmento leva o regionId`() {
        val cookie = AtacadaoVtexParser.segmentCookie("v2.ABC")
        val decoded = String(Base64.getDecoder().decode(cookie), Charsets.UTF_8)
        assertNotNull(decoded)
        assertTrue(decoded.contains("\"regionId\":\"v2.ABC\""))
        assertTrue(decoded.contains("\"channel\":\"1\""))
    }
}
