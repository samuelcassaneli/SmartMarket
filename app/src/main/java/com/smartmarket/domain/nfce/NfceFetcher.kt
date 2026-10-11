package com.smartmarket.domain.nfce

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.charset.Charset

/** Página baixada da SEFAZ. [finalUrl] é a URL depois dos redirects. */
data class FetchedPage(val html: String, val finalUrl: String)

/**
 * Baixa a página de consulta da NFC-e com o cliente HTTP do app (user-agent mobile, redirects ligados).
 * Detecta charset (cabeçalho, meta tag ou heurística) porque várias SEFAZ servem ISO-8859-1.
 */
class NfceFetcher(private val client: OkHttpClient) {

    /** Cliente sem redirects automáticos: seguimos à mão para validar o host de cada salto. */
    private val noRedirectClient: OkHttpClient by lazy {
        client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    }

    suspend fun fetch(url: String): FetchedPage = withContext(Dispatchers.IO) {
        var current = url.toHttpUrlOrNull()
            ?: throw NfceException(NfceErrorKind.INVALID_PAYLOAD, "Endereço de consulta inválido.")
        try {
            repeat(MAX_REDIRECTS + 1) {
                if (!SefazDirectory.isOfficialHost(current.host)) {
                    throw NfceException(NfceErrorKind.INVALID_PAYLOAD, "Endereço fora dos sites oficiais da SEFAZ (${current.host}).")
                }
                val request = Request.Builder()
                    .url(current)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .get()
                    .build()
                noRedirectClient.newCall(request).execute().use { response ->
                    if (response.isRedirect) {
                        val location = response.header("Location")
                            ?: throw NfceException(NfceErrorKind.NETWORK, "A SEFAZ redirecionou sem destino.")
                        current = response.request.url.resolve(location)
                            ?: throw NfceException(NfceErrorKind.NETWORK, "Redirecionamento inválido da SEFAZ.")
                        return@use
                    }
                    if (!response.isSuccessful) {
                        throw NfceException(
                            NfceErrorKind.NETWORK,
                            "A SEFAZ respondeu com erro ${response.code}. Tente de novo em instantes."
                        )
                    }
                    val body = response.body ?: throw NfceException(NfceErrorKind.NETWORK, "A SEFAZ não devolveu conteúdo.")
                    val bytes = body.bytes()
                    val headerCharset = body.contentType()?.charset()
                    return@withContext FetchedPage(decode(bytes, headerCharset), response.request.url.toString())
                }
            }
            throw NfceException(NfceErrorKind.NETWORK, "A SEFAZ redirecionou vezes demais.")
        } catch (e: NfceException) {
            throw e
        } catch (e: IOException) {
            throw NfceException(NfceErrorKind.NETWORK, "Sem conexão com a SEFAZ. Verifique a internet e tente de novo.", e)
        }
    }

    companion object {
        private const val MAX_REDIRECTS = 8
        private val META_CHARSET = Regex("""<meta[^>]+charset\s*=\s*["']?([A-Za-z0-9_\-]+)""", RegexOption.IGNORE_CASE)

        /** Decodifica pelo charset do cabeçalho, depois o da meta tag; sem nenhum, tenta UTF-8 e cai em ISO-8859-1. */
        fun decode(bytes: ByteArray, headerCharset: Charset?): String {
            if (headerCharset != null) return String(bytes, headerCharset)
            val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
            val metaName = META_CHARSET.find(head)?.groupValues?.get(1)
            val meta = metaName?.let { runCatching { Charset.forName(it) }.getOrNull() }
            if (meta != null) return String(bytes, meta)
            val utf8 = String(bytes, Charsets.UTF_8)
            return if (utf8.contains('�')) String(bytes, Charsets.ISO_8859_1) else utf8
        }
    }
}

/** Orquestra QR -> download -> parser. */
class NfceService(private val fetcher: NfceFetcher) {

    constructor(client: OkHttpClient) : this(NfceFetcher(client))

    /** Consulta a nota do QR. Lança [NfceException] com o tipo de falha. */
    suspend fun load(payload: QrPayload): NfceReceipt {
        val url = payload.consultUrl
            ?: SefazDirectory.directKeyUrl(payload.key)
            ?: throw NfceException(
                NfceErrorKind.CAPTCHA,
                "A consulta pela chave exige verificação na página da SEFAZ."
            )
        val page = fetcher.fetch(url)
        return parseHtml(page.html, url, payload.key)
    }

    companion object {
        /** Usa o parser e completa a chave com a do QR se a página não trouxer. */
        fun parseHtml(html: String, sourceUrl: String, expectedKey: NfceKey?): NfceReceipt {
            val receipt = NfceHtmlParser.parse(html, sourceUrl)
            return if (receipt.accessKey.isBlank() && expectedKey != null) receipt.copy(accessKey = expectedKey.digits) else receipt
        }
    }
}
