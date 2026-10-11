package com.smartmarket.domain.nfce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class QrPayloadParserTest {

    private val key = NfceTestKeys.RJ_HORTIFRUTI

    @Test
    fun `qr v2 online`() {
        val text = "http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p=$key|2|1|1|0A1B2C3D4E5F60718293A4B5C6D7E8F901234567"
        val p = QrPayloadParser.parseOrThrow(text)
        assertEquals(key, p.key.digits)
        assertEquals(2, p.version)
        assertEquals(text, p.consultUrl)
    }

    @Test
    fun `qr v2 contingencia`() {
        val text = "http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p=$key|2|1|31|108.95|6C6F4A2B3C|1|ABCDEF0123456789ABCDEF0123456789ABCDEF01"
        val p = QrPayloadParser.parseOrThrow(text)
        assertEquals(key, p.key.digits)
        assertEquals(2, p.version)
        assertTrue(p.consultUrl!!.startsWith("http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p="))
    }

    @Test
    fun `qr v3`() {
        val text = "https://www.fazenda.rj.gov.br/nfce/qrcode?p=$key|3|1"
        val p = QrPayloadParser.parseOrThrow(text)
        assertEquals(3, p.version)
        assertEquals(key, p.key.digits)
    }

    @Test
    fun `qr v1 com chNFe`() {
        val text = "http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?chNFe=$key&nVersao=100&tpAmb=1&dhEmi=323031392d30382d33315431333a33343a33312d30333a3030&vNF=108.95&vICMS=0.00&digVal=6a&cIdToken=000001&cHashQRCode=ABC"
        val p = QrPayloadParser.parseOrThrow(text)
        assertEquals(1, p.version)
        assertEquals(key, p.key.digits)
        assertEquals(text, p.consultUrl)
    }

    @Test
    fun `url codificada e sem esquema`() {
        val text = "www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p=$key%7C2%7C1%7C1%7CABCDEF"
        val p = QrPayloadParser.parseOrThrow(text)
        assertEquals(key, p.key.digits)
        assertTrue(p.consultUrl!!.startsWith("http://www4.fazenda.rj.gov.br/"))
        assertTrue(p.consultUrl!!.contains("|2|1|1|ABCDEF"))
    }

    @Test
    fun `so o parametro p monta a url do estado`() {
        val p = QrPayloadParser.parseOrThrow("$key|2|1|1|ABCDEF")
        assertEquals("http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p=$key|2|1|1|ABCDEF", p.consultUrl)
    }

    @Test
    fun `url sem p com a chave`() {
        val p = QrPayloadParser.parseOrThrow("https://www.sefaz.exemplo.gov.br/nfce/consulta/$key")
        assertEquals(key, p.key.digits)
        assertEquals(0, p.version)
    }

    @Test
    fun `chave pura com e sem espacos`() {
        val pure = QrPayloadParser.parseOrThrow(key)
        assertEquals(key, pure.key.digits)
        assertNull(pure.consultUrl)
        val spaced = QrPayloadParser.parseOrThrow(NfceKey.format(NfceTestKeys.RJ_AMERICANAS))
        assertEquals(NfceTestKeys.RJ_AMERICANAS, spaced.key.digits)
    }

    @Test
    fun `rejeita nfe modelo 55`() {
        val nfe = NfceTestKeys.build(model = 55)
        assertError(NfceErrorKind.NOT_NFCE, "http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p=$nfe|2|1|1|ABC")
        assertError(NfceErrorKind.NOT_NFCE, nfe)
    }

    @Test
    fun `rejeita dv invalido e qr que nao e nota`() {
        val bad = key.substring(0, 43) + ((key.last() - '0' + 1) % 10)
        assertError(NfceErrorKind.INVALID_KEY, "http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p=$bad|2|1|1|ABC")
        assertError(NfceErrorKind.INVALID_PAYLOAD, "https://www.google.com/")
        assertError(NfceErrorKind.INVALID_PAYLOAD, "12345")
        assertError(NfceErrorKind.INVALID_PAYLOAD, "")
    }

    private fun assertError(kind: NfceErrorKind, text: String) {
        try {
            QrPayloadParser.parseOrThrow(text)
            fail("esperava erro $kind para '$text'")
        } catch (e: NfceException) {
            assertEquals(kind, e.kind)
        }
    }
}

class QrPayloadSecurityTest {

    private val key = NfceTestKeys.RJ_HORTIFRUTI
    private val rjBase = "http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p="

    @Test
    fun `url de host nao oficial e trocada pela url da SEFAZ da UF`() {
        val evil = QrPayloadParser.parseOrThrow("https://evil.example.com/phish?p=$key|2|1|1|ABC")
        assertEquals("$rjBase$key|2|1|1|ABC", evil.consultUrl)

        val lookalike = QrPayloadParser.parseOrThrow("http://fazenda.rj.gov.br.evil.com/QRCode?p=$key|2|1|1|ABC")
        assertEquals("$rjBase$key|2|1|1|ABC", lookalike.consultUrl)

        val userInfo = QrPayloadParser.parseOrThrow("http://www.fazenda.rj.gov.br@evil.com/?p=$key|2|1|1|ABC")
        assertEquals("$rjBase$key|2|1|1|ABC", userInfo.consultUrl)
    }

    @Test
    fun `v1 ou url sem p de host nao oficial fica sem url`() {
        assertNull(QrPayloadParser.parseOrThrow("http://evil.com/x?chNFe=$key&nVersao=100").consultUrl)
        assertNull(QrPayloadParser.parseOrThrow("https://evil.com/nota/$key").consultUrl)
    }

    @Test
    fun `hosts oficiais`() {
        assertTrue(SefazDirectory.isOfficialHost("www4.fazenda.rj.gov.br"))
        assertTrue(SefazDirectory.isOfficialHost("GOV.BR."))
        assertTrue(!SefazDirectory.isOfficialHost("evilgov.br"))
        assertTrue(!SefazDirectory.isOfficialHost("gov.br.evil.com"))
        assertTrue(!SefazDirectory.isOfficialUrl("javascript:alert(1)"))
        assertTrue(!SefazDirectory.isOfficialUrl("file:///sdcard/x.gov.br"))
    }
}
