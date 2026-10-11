package com.smartmarket.domain.nfce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

/**
 * Fixtures reais de consultas NFC-e do RJ (CPF do consumidor anonimizado):
 * - rj_hortifruti_2019.html: www4.fazenda.rj.gov.br/consultaNFCe (QR), 20 itens, com desconto;
 * - rj_americanas_2025.html: consultadfe.fazenda.rj.gov.br (layout 2025, data com fuso);
 * - rj_bot_challenge.html: resposta anti-robô (F5/TSPD) que a SEFAZ-RJ devolve a clientes "suspeitos".
 */
class NfceHtmlParserTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/nfce/$name")) { "fixture $name não encontrada" }.readText(Charsets.UTF_8)

    private val eps = 0.001

    @Test
    fun `hortifruti RJ - cabecalho e totais`() {
        val r = NfceHtmlParser.parse(fixture("rj_hortifruti_2019.html"), "http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p=x")
        assertEquals("HORTIGIL HORTIFRUTI S/A", r.storeName)
        assertEquals("31487473000601", r.cnpj)
        assertTrue(r.address, r.address.startsWith("Rua Vereador Manoel Braga, 83"))
        assertEquals("macae", r.city)
        assertEquals(20, r.items.size)
        assertEquals(20, r.declaredItemCount)
        assertEquals(109.05, r.grossTotal, eps)
        assertEquals(0.10, r.discount, eps)
        assertEquals(108.95, r.total, eps)
        assertEquals("Cartão de Débito", r.paymentMethod)
        assertEquals(NfceTestKeys.RJ_HORTIFRUTI, r.accessKey)
        assertEquals("130074", r.number)
        assertEquals("7", r.series)
        // 31/08/2019 13:34:31 em America/Sao_Paulo (UTC-3)
        assertEquals(Instant.parse("2019-08-31T16:34:31Z").toEpochMilli(), r.issuedAt)
    }

    @Test
    fun `hortifruti RJ - itens com virgula e kg fracionado`() {
        val r = NfceHtmlParser.parse(fixture("rj_hortifruti_2019.html"))
        val banana = r.items[0]
        assertEquals("BANANA PRATA", banana.description)
        assertEquals("100065", banana.code)
        assertEquals(0.38, banana.quantity, eps)
        assertEquals("KG", banana.unit)
        assertTrue(banana.isWeighed)
        assertEquals(5.49, banana.unitPrice, eps)
        assertEquals(2.09, banana.totalPrice, eps)

        val alho = r.items[1]
        assertEquals(21.9, alho.unitPrice, eps) // "21,9" sem segunda casa

        val batata = r.items[5]
        assertEquals("BATATA BAROA AMARELA", batata.description)
        assertEquals(0.535, batata.quantity, eps)
        assertEquals(7.48, batata.totalPrice, eps)

        val bolo = r.items.first { it.description == "MINI BOLO C CREME AV" }
        assertEquals(2.0, bolo.quantity, eps)
        assertEquals("UN", bolo.unit)
        assertEquals(4.58, bolo.totalPrice, eps)

        assertEquals(109.05, r.items.sumOf { it.totalPrice }, eps)
    }

    @Test
    fun `americanas RJ 2025 - data com fuso e pagamento com codigo`() {
        val r = NfceHtmlParser.parse(fixture("rj_americanas_2025.html"), "https://consultadfe.fazenda.rj.gov.br/consultaDFe/paginas/resultadoNfce.faces")
        assertEquals("AMERICANAS S/A", r.storeName)
        assertEquals("00776574199483", r.cnpj)
        assertEquals("Niteroi", r.city)
        assertEquals(4, r.items.size)
        assertEquals(24.69, r.total, eps)
        assertEquals(24.69, r.grossTotal, eps)
        assertEquals(0.0, r.discount, eps)
        assertEquals("Transferência bancária, Carteira Digital", r.paymentMethod)
        assertEquals(NfceTestKeys.RJ_AMERICANAS, r.accessKey)
        assertEquals(Instant.parse("2025-05-18T16:09:52Z").toEpochMilli(), r.issuedAt)
        val banana = r.items[0]
        assertEquals("BANANA PRATA ORG KG", banana.description)
        assertEquals(0.835, banana.quantity, eps)
        assertEquals("KG", banana.unit)
        assertEquals(9.99, banana.unitPrice, eps)
        assertEquals(8.34, banana.totalPrice, eps)
    }

    @Test
    fun `pagina anti-robo do RJ vira erro de captcha`() {
        try {
            NfceHtmlParser.parse(fixture("rj_bot_challenge.html"), "http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p=x")
            fail("esperava NfceException")
        } catch (e: NfceException) {
            assertEquals(NfceErrorKind.CAPTCHA, e.kind)
            assertTrue(e.kind.needsWebView)
        }
    }

    @Test
    fun `nota nao encontrada`() {
        val html = "<html><body><div id='conteudo'><p>NFC-e não encontrada na base de dados da SEFAZ. Verifique a chave de acesso informada e tente novamente mais tarde, pois a nota pode ainda não ter sido transmitida.</p></div></body></html>"
        try {
            NfceHtmlParser.parse(html)
            fail("esperava NfceException")
        } catch (e: NfceException) {
            assertEquals(NfceErrorKind.NOT_FOUND, e.kind)
        }
    }

    @Test
    fun `pagina feita por javascript`() {
        val html = "<html><head><script src='app.js'></script></head><body><div id=\"root\"></div><noscript>Ative o JavaScript</noscript></body></html>"
        try {
            NfceHtmlParser.parse(html)
            fail("esperava NfceException")
        } catch (e: NfceException) {
            assertEquals(NfceErrorKind.JS_REQUIRED, e.kind)
        }
    }

    @Test
    fun `parser generico le layout padrao de outro estado`() {
        val key = NfceTestKeys.build(cUF = 43)
        val html = """
            <html><body>
            <div class="txtCenter"><div id="u20" class="txtTopo">MERCADO TESTE LTDA</div>
            <div class="text">CNPJ: 12.345.678/0001-99</div>
            <div class="text">RUA A, 10, CENTRO, PORTO ALEGRE, RS</div></div>
            <table id="tabResult">
              <tr id="Item + 1"><td><span class="txtTit">ARROZ T1 5KG</span><span class="RCod">(Código: 789 )</span><br>
              <span class="Rqtd"><strong>Qtde.:</strong>2</span><span class="RUN"><strong>UN: </strong>UN</span>
              <span class="RvlUnit"><strong>Vl. Unit.:</strong>&nbsp;24,90</span></td>
              <td class="txtTit noWrap">Vl. Total<br><span class="valor">49,80</span></td></tr>
              <tr id="Item + 2"><td><span class="txtTit">QUEIJO MUSSARELA</span><span class="RCod">(Código: 123 )</span><br>
              <span class="Rqtd"><strong>Qtde.:</strong>0,412</span><span class="RUN"><strong>UN: </strong>KG</span>
              <span class="RvlUnit"><strong>Vl. Unit.:</strong>&nbsp;39,90</span></td>
              <td class="txtTit noWrap">Vl. Total<br><span class="valor">16,44</span></td></tr>
            </table>
            <div id="totalNota">
              <div id="linhaTotal"><label>Qtd. total de itens:</label><span class="totalNumb">2</span></div>
              <div id="linhaTotal"><label>Valor total R$:</label><span class="totalNumb">66,24</span></div>
              <div id="linhaTotal"><label>Descontos R$:</label><span class="totalNumb">1,24</span></div>
              <div id="linhaTotal" class="linhaShade"><label>Valor a pagar R$:</label><span class="totalNumb txtMax">65,00</span></div>
              <div id="linhaForma"><label>Forma de pagamento:</label><span class="totalNumb txtTitR">Valor pago R$:</span></div>
              <div id="linhaTotal"><label class="tx">03 - Cartão de Crédito</label><span class="totalNumb">65,00</span></div>
              <div id="linhaTotal"><label class="tx">Troco </label><span class="totalNumb">0,00</span></div>
            </div>
            <div id="infos"><li><strong>Número: </strong>55<strong> Série: </strong>1<strong> Emissão: </strong>05/10/2026 18:20:00 - Via Consumidor</li>
            <span class="chave">${NfceKey.format(key)}</span></div>
            </body></html>
        """.trimIndent()
        val r = NfceHtmlParser.parse(html, "https://www.sefaz.rs.gov.br/NFCE/NFCE-COM.aspx?p=x")
        assertEquals(2, r.items.size)
        assertEquals("789", r.items[0].code)
        assertEquals(0.412, r.items[1].quantity, eps)
        assertEquals(65.00, r.total, eps)
        assertEquals(1.24, r.discount, eps)
        assertEquals("Cartão de Crédito", r.paymentMethod)
        assertEquals(key, r.accessKey)
        assertEquals("12345678000199", r.cnpj)
        assertEquals(Instant.parse("2026-10-05T21:20:00Z").toEpochMilli(), r.issuedAt)
    }
}
