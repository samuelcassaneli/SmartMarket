package com.smartmarket.domain.nfce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NfceKeyTest {

    @Test
    fun `chaves reais do RJ sao validas`() {
        assertTrue(NfceKey.isValid(NfceTestKeys.RJ_HORTIFRUTI))
        assertTrue(NfceKey.isValid(NfceTestKeys.RJ_AMERICANAS))
    }

    @Test
    fun `extrai os campos da chave`() {
        val key = NfceKey.parse(NfceTestKeys.RJ_HORTIFRUTI)!!
        assertEquals(33, key.cUF)
        assertEquals("RJ", key.uf)
        assertEquals("1908", key.yearMonth)
        assertEquals(2019, key.year)
        assertEquals(8, key.month)
        assertEquals("31487473000601", key.cnpj)
        assertEquals(65, key.model)
        assertTrue(key.isNfce)
        assertEquals(7, key.series)
        assertEquals(130074L, key.number)
        assertEquals(1, key.emissionType)
        assertEquals(6, key.checkDigit)
    }

    @Test
    fun `dv calculado bate com o construtor de teste`() {
        val key = NfceTestKeys.build(series = 3, number = 228007)
        assertTrue(NfceKey.isValid(key))
        val parsed = NfceKey.parse(key)!!
        assertEquals(3, parsed.series)
        assertEquals(228007L, parsed.number)
    }

    @Test
    fun `resto 0 ou 1 gera dv 0`() {
        // procura códigos até achar um com resto < 2 e confere que o DV é 0
        var found = 0
        for (i in 0 until 500) {
            val first43 = NfceTestKeys.build(code = "%08d".format(i)).substring(0, 43)
            var sum = 0
            var w = 2
            for (c in first43.reversed()) { sum += (c - '0') * w; w = if (w == 9) 2 else w + 1 }
            if (sum % 11 < 2) {
                assertEquals(0, NfceKey.computeCheckDigit(first43))
                found++
            }
        }
        assertTrue(found > 0)
    }

    @Test
    fun `dv errado ou tamanho errado e invalido`() {
        val valid = NfceTestKeys.RJ_AMERICANAS
        val wrongDv = valid.substring(0, 43) + ((valid.last() - '0' + 1) % 10)
        assertFalse(NfceKey.isValid(wrongDv))
        assertFalse(NfceKey.isValid(valid.substring(1)))
        assertFalse(NfceKey.isValid(valid + "0"))
        assertFalse(NfceKey.isValid(valid.replaceFirst('3', 'A')))
        // troca de dois dígitos vizinhos também é detectada
        val swapped = valid.substring(0, 10) + valid[11] + valid[10] + valid.substring(12)
        if (swapped != valid) assertFalse(NfceKey.isValid(swapped))
    }

    @Test
    fun `aceita espacos e formata em grupos de 4`() {
        val spaced = "3325 0500 7765 7419 9483 6500 3000 2280 0718 5510 7701"
        val key = NfceKey.parse(spaced)
        assertNotNull(key)
        assertEquals(NfceTestKeys.RJ_AMERICANAS, key!!.digits)
        assertEquals(spaced, key.formatted())
    }

    @Test
    fun `nfe modelo 55 e reconhecida`() {
        val key = NfceKey.parse(NfceTestKeys.build(model = 55))!!
        assertTrue(key.isNfe)
        assertFalse(key.isNfce)
    }

    @Test
    fun `acha a chave em texto de OCR`() {
        val ocr = """
            SUPERMERCADO EXEMPLO LTDA
            Consulte pela Chave de Acesso em
            www.fazenda.rj.gov.br/nfce/consulta
            3325 0500 7765 7419 9483
            6500 3000 2280 0718 5510 7701
            CONSUMIDOR NAO IDENTIFICADO
        """.trimIndent()
        assertEquals(NfceTestKeys.RJ_AMERICANAS, NfceKey.findInText(ocr)?.digits)
        assertEquals(NfceTestKeys.RJ_HORTIFRUTI, NfceKey.findInText("chave: ${NfceTestKeys.RJ_HORTIFRUTI} fim")?.digits)
        assertNull(NfceKey.findInText("TOTAL R$ 12,50 CPF 123.456.789-00"))
    }
}
