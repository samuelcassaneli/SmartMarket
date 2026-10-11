package com.smartmarket.domain.nfce

/**
 * Chave de acesso de 44 dígitos de NF-e/NFC-e.
 *
 * Layout: cUF(2) AAMM(4) CNPJ(14) modelo(2) série(3) número(9) tpEmis(1) código(8) DV(1).
 */
data class NfceKey(val digits: String) {

    init {
        require(digits.length == LENGTH && digits.all { it.isDigit() }) { "A chave precisa ter 44 dígitos" }
    }

    val cUF: Int get() = digits.substring(0, 2).toInt()
    val yearMonth: String get() = digits.substring(2, 6) // AAMM
    val year: Int get() = 2000 + digits.substring(2, 4).toInt()
    val month: Int get() = digits.substring(4, 6).toInt()
    val cnpj: String get() = digits.substring(6, 20)
    val model: Int get() = digits.substring(20, 22).toInt()
    val series: Int get() = digits.substring(22, 25).toInt()
    val number: Long get() = digits.substring(25, 34).toLong()
    val emissionType: Int get() = digits.substring(34, 35).toInt()
    val numericCode: String get() = digits.substring(35, 43)
    val checkDigit: Int get() = digits.substring(43).toInt()

    val isNfce: Boolean get() = model == MODEL_NFCE
    val isNfe: Boolean get() = model == MODEL_NFE
    val isValid: Boolean get() = isValid(digits)
    val uf: String? get() = SefazDirectory.ufOf(cUF)

    /** "3325 0500 ..." */
    fun formatted(): String = format(digits)

    override fun toString(): String = digits

    companion object {
        const val LENGTH = 44
        const val MODEL_NFCE = 65
        const val MODEL_NFE = 55

        /** Dígito verificador módulo 11 dos 43 primeiros dígitos (pesos 2..9 da direita para a esquerda). */
        fun computeCheckDigit(first43: String): Int {
            require(first43.length == 43 && first43.all { it.isDigit() })
            var sum = 0
            var weight = 2
            for (i in first43.indices.reversed()) {
                sum += (first43[i] - '0') * weight
                weight = if (weight == 9) 2 else weight + 1
            }
            val rest = sum % 11
            return if (rest < 2) 0 else 11 - rest
        }

        fun isValid(digits: String): Boolean =
            digits.length == LENGTH && digits.all { it.isDigit() } &&
                computeCheckDigit(digits.substring(0, 43)) == digits[43] - '0'

        /** Aceita espaços, pontos e traços. Retorna null se não for uma chave válida. */
        fun parse(text: String): NfceKey? {
            val digits = text.filter { it.isDigit() }
            return if (isValid(digits)) NfceKey(digits) else null
        }

        fun format(digits: String): String = digits.chunked(4).joinToString(" ")

        private val CANDIDATE = Regex("""\d(?:[ .\-]?\d){43}""")

        /**
         * Procura uma chave válida em texto livre (OCR de foto da nota), aceitando grupos de 4
         * separados por espaço. Confere o DV para descartar números quaisquer.
         */
        fun findInText(text: String): NfceKey? {
            // 1) sequências com separadores simples
            for (m in CANDIDATE.findAll(text)) {
                parse(m.value)?.takeIf { it.model == MODEL_NFCE || it.model == MODEL_NFE }?.let { return it }
            }
            // 2) junta tudo por linha e varre janelas de 44 dígitos (OCR quebra os grupos)
            val lines = text.lines()
            for (start in lines.indices) {
                val joined = StringBuilder()
                for (j in start until minOf(lines.size, start + 4)) {
                    joined.append(lines[j].filter { it.isDigit() })
                    val digits = joined.toString()
                    if (digits.length >= LENGTH) {
                        for (k in 0..digits.length - LENGTH) {
                            val window = digits.substring(k, k + LENGTH)
                            if (isValid(window) && window.substring(20, 22).toInt() in setOf(MODEL_NFCE, MODEL_NFE)) {
                                return NfceKey(window)
                            }
                        }
                    }
                }
            }
            return null
        }
    }
}
