package com.smartmarket.domain.nfce

import java.net.URLDecoder

/** Resultado da leitura do QR (ou da chave digitada). */
data class QrPayload(
    val key: NfceKey,
    /** URL de consulta da SEFAZ; null quando só temos a chave (consulta manual no WebView). */
    val consultUrl: String?,
    /** Versão do QR (1, 2, 3) ou 0 para chave pura. */
    val version: Int,
    val raw: String
)

/**
 * Interpreta o texto lido do QR Code da NFC-e.
 *
 * Formatos aceitos:
 * - v2 online: `...?p=CHAVE|2|tpAmb|cIdToken|cHashQRCode`
 * - v2 contingência: `p=CHAVE|2|tpAmb|dia|valor|digVal|cIdToken|hash`
 * - v3 (NT 2025.001): `p=CHAVE|3|tpAmb...`
 * - v1: `?chNFe=CHAVE&nVersao=100&...`
 * - URL sem `p=` contendo a chave, ou a chave pura (com ou sem espaços)
 */
object QrPayloadParser {

    private val PIPE_P = Regex("""(?:^|[?&])p=([^&#\s]+)""", RegexOption.IGNORE_CASE)
    private val CH_NFE = Regex("""(?:^|[?&])chNFe=(\d{44})""", RegexOption.IGNORE_CASE)
    private val ANY_44 = Regex("""(?<!\d)\d{44}(?!\d)""")

    fun parse(text: String): Result<QrPayload> = runCatching { parseOrThrow(text) }

    fun parseOrThrow(text: String): QrPayload {
        val raw = text.trim()
        if (raw.isEmpty()) throw NfceException(NfceErrorKind.INVALID_PAYLOAD, "QR Code vazio.")

        val decoded = decode(raw)

        // Chave pura (com ou sem espaços/pontos)
        if (decoded.none { it.isLetter() } && !decoded.contains('|')) {
            val digits = decoded.filter { it.isDigit() }
            if (digits.length == NfceKey.LENGTH) {
                val key = validated(digits)
                return QrPayload(key, null, 0, raw)
            }
            throw NfceException(NfceErrorKind.INVALID_PAYLOAD, "Este código não é de uma nota fiscal.")
        }

        val url = urlPart(decoded)

        // p=CHAVE|versão|...
        val pValue = PIPE_P.find(decoded)?.groupValues?.get(1)
            ?: decoded.takeIf { url == null && it.contains('|') } // só o conteúdo do p
        if (pValue != null) {
            val fields = pValue.split('|')
            val digits = fields.first().filter { it.isDigit() }
            if (digits.length != NfceKey.LENGTH) {
                throw NfceException(NfceErrorKind.INVALID_PAYLOAD, "QR Code de nota com chave incompleta.")
            }
            val key = validated(digits)
            val version = fields.getOrNull(1)?.trim()?.toIntOrNull() ?: 2
            // URL do QR só é usada se for de host oficial (.gov.br); senão, montamos pela UF da chave
            val consultUrl = officialUrl(url) ?: SefazDirectory.qrUrl(key, pValue)
            return QrPayload(key, consultUrl, version, raw)
        }

        // v1: chNFe=...&nVersao=100
        CH_NFE.find(decoded)?.let { m ->
            val key = validated(m.groupValues[1])
            return QrPayload(key, officialUrl(url), 1, raw)
        }

        // URL qualquer com a chave em algum lugar
        ANY_44.find(decoded)?.let { m ->
            val key = validated(m.value)
            return QrPayload(key, officialUrl(url), 0, raw)
        }

        throw NfceException(NfceErrorKind.INVALID_PAYLOAD, "Este QR Code não é de uma nota fiscal de consumidor.")
    }

    private fun validated(digits: String): NfceKey {
        if (!NfceKey.isValid(digits)) {
            throw NfceException(NfceErrorKind.INVALID_KEY, "A chave de acesso é inválida (dígito verificador não confere).")
        }
        val key = NfceKey(digits)
        if (key.isNfe) {
            throw NfceException(
                NfceErrorKind.NOT_NFCE,
                "Esta é uma NF-e (modelo 55), não uma nota de consumidor. Só a NFC-e (modelo 65) pode ser consultada pelo QR Code."
            )
        }
        if (!key.isNfce) {
            throw NfceException(NfceErrorKind.NOT_NFCE, "Modelo de documento ${key.model} não suportado. Use o QR Code da NFC-e.")
        }
        return key
    }

    /** Alguns leitores devolvem o QR com %7C no lugar de |. */
    private fun decode(raw: String): String =
        if (raw.contains("%7C", ignoreCase = true) || raw.contains("%3D", ignoreCase = true)) {
            runCatching { URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") }.getOrDefault(raw)
        } else raw

    /** Retorna a URL do texto (com ou sem esquema) ou null se não parecer uma URL. */
    private fun urlPart(text: String): String? {
        val t = text.trim()
        return when {
            t.startsWith("http://", true) || t.startsWith("https://", true) -> t
            Regex("""^[a-z0-9.-]+\.[a-z]{2,}(/|\?)""", RegexOption.IGNORE_CASE).containsMatchIn(t) -> t
            else -> null
        }
    }

    /** URL com esquema, se for http/https de host oficial; null caso contrário. */
    private fun officialUrl(url: String?): String? =
        url?.let { ensureScheme(it) }?.takeIf { SefazDirectory.isOfficialUrl(it) }

    private fun ensureScheme(url: String): String =
        if (url.startsWith("http://", true) || url.startsWith("https://", true)) url else "http://$url"
}
