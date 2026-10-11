package com.smartmarket.domain.nfce

/** Monta chaves válidas (com DV calculado) para os testes. */
object NfceTestKeys {
    fun build(
        cUF: Int = 33,
        aamm: String = "2610",
        cnpj: String = "12345678000199",
        model: Int = 65,
        series: Int = 1,
        number: Long = 12345,
        tpEmis: Int = 1,
        code: String = "12345678"
    ): String {
        val first43 = "%02d".format(cUF) + aamm + cnpj + "%02d".format(model) + "%03d".format(series) +
            "%09d".format(number) + tpEmis + code
        require(first43.length == 43) { "tamanho ${first43.length}" }
        return first43 + NfceKey.computeCheckDigit(first43)
    }

    /** Chaves reais impressas em DANFEs do RJ (fixtures). */
    const val RJ_HORTIFRUTI = "33190831487473000601650070001300741239378786"
    const val RJ_AMERICANAS = "33250500776574199483650030002280071855107701"
}
