package com.smartmarket.domain.nfce

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Uma forma de ler a página de consulta de um estado. */
interface NfceParserStrategy {
    val name: String
    fun canParse(doc: Document, sourceUrl: String): Boolean
    /** Retorna null se a página não tiver itens neste layout. */
    fun parse(doc: Document, sourceUrl: String): NfceReceipt?
}

/**
 * Lê a página de consulta da NFC-e (layout padrão do Portal NFC-e: RJ, SVRS e muitos estados).
 * Puro (só Jsoup), para rodar em teste JVM. Tenta as estratégias na ordem e cai no genérico.
 */
object NfceHtmlParser {

    val strategies: List<NfceParserStrategy> = listOf(RjParser, StandardPortalParser)

    fun parse(html: String, sourceUrl: String = ""): NfceReceipt {
        val doc = Jsoup.parse(html, sourceUrl.ifBlank { "http://localhost/" })
        for (strategy in strategies) {
            if (!strategy.canParse(doc, sourceUrl)) continue
            val receipt = runCatching { strategy.parse(doc, sourceUrl) }.getOrNull()
            if (receipt != null && receipt.items.isNotEmpty()) return receipt
        }
        throw classifyFailure(doc, html)
    }

    /** Página sem itens: descobre o motivo para a UI decidir se abre o WebView. */
    fun classifyFailure(doc: Document, html: String): NfceException {
        val text = NfceText.collapseSpaces(doc.text()).lowercase()
        val lowerHtml = html.lowercase()
        return when {
            "captcha" in lowerHtml || "recaptcha" in lowerHtml || "hcaptcha" in lowerHtml ||
                "tspd" in lowerHtml || "bobcmn" in lowerHtml || "support id" in text ->
                NfceException(NfceErrorKind.CAPTCHA, "A SEFAZ pediu uma verificação de segurança. Abra a consulta e confirme que você não é um robô.")
            "não encontrad" in text || "nao encontrad" in text || "inexistente" in text ||
                "não foi localizad" in text || "nao foi localizad" in text || "não consta" in text ->
                NfceException(NfceErrorKind.NOT_FOUND, "A SEFAZ não encontrou esta nota. Notas recém-emitidas podem levar alguns minutos para aparecer.")
            "rejeição" in text || "rejeicao" in text || "erro" in text && text.length < 600 ->
                NfceException(NfceErrorKind.NOT_FOUND, "A SEFAZ respondeu com erro: " + text.take(160))
            text.length < 400 || "<noscript" in lowerHtml || "ng-app" in lowerHtml || "id=\"root\"" in lowerHtml ->
                NfceException(NfceErrorKind.JS_REQUIRED, "A página da SEFAZ precisa ser aberta no navegador para mostrar os itens.")
            else ->
                NfceException(NfceErrorKind.UNKNOWN_LAYOUT, "Não reconhecemos o formato desta página da SEFAZ.")
        }
    }
}

/**
 * Layout padrão do Portal NFC-e (`#tabResult`, `.txtTit`, `.Rqtd`, `#totalNota`, `#infos`).
 * Aberto para que variantes por estado sobrescrevam só o que muda.
 */
open class StandardPortalParserBase : NfceParserStrategy {
    override val name: String = "Portal NFC-e padrão"

    override fun canParse(doc: Document, sourceUrl: String): Boolean = true

    override fun parse(doc: Document, sourceUrl: String): NfceReceipt? {
        val items = parseItems(doc)
        if (items.isEmpty()) return null

        val header = parseHeader(doc)
        val totals = parseTotals(doc)
        val infosText = NfceText.collapseSpaces((doc.selectFirst("#infos") ?: doc.body()).text())
        val key = parseKey(doc, infosText)
        val itemsSum = items.sumOf { it.totalPrice }

        val gross = totals.gross ?: (totals.toPay?.let { it + (totals.discount ?: 0.0) }) ?: itemsSum
        val toPay = totals.toPay ?: (gross - (totals.discount ?: 0.0))

        return NfceReceipt(
            storeName = header.name,
            cnpj = header.cnpj.ifBlank { key?.cnpj.orEmpty() },
            address = header.address,
            issuedAt = parseIssuedAt(infosText) ?: key?.let { fallbackDateFromKey(it) } ?: System.currentTimeMillis(),
            items = items,
            total = round2(toPay),
            grossTotal = round2(gross),
            discount = round2(totals.discount ?: (gross - toPay).coerceAtLeast(0.0)),
            paymentMethod = totals.payment,
            accessKey = key?.digits.orEmpty(),
            sourceUrl = sourceUrl,
            number = NUMBER.find(infosText)?.groupValues?.get(1).orEmpty(),
            series = SERIES.find(infosText)?.groupValues?.get(1).orEmpty(),
            declaredItemCount = totals.count
        )
    }

    // ------------------------------------------------------------------ itens

    protected open fun itemRows(doc: Document): List<Element> {
        val rows = doc.select("table#tabResult tr")
        return if (rows.isNotEmpty()) rows else doc.select("#tabResult tr, tr[id^=Item]")
    }

    protected open fun parseItems(doc: Document): List<NfceItem> =
        itemRows(doc).mapNotNull { parseItem(it) }

    protected open fun parseItem(row: Element): NfceItem? {
        val description = row.selectFirst("span.txtTit")?.text()?.let { NfceText.collapseSpaces(it) }
            ?.takeIf { it.isNotEmpty() } ?: return null
        val code = row.selectFirst("span.RCod")?.text()?.let { CODE.find(it)?.groupValues?.get(1)?.trim() }.orEmpty()
        val quantity = row.selectFirst("span.Rqtd")?.text()?.let { NfceText.parseNumber(stripLabel(it)) } ?: 1.0
        val unit = row.selectFirst("span.RUN")?.text()?.let { stripLabel(it) }?.trim()?.uppercase().orEmpty().ifBlank { "UN" }
        val total = row.selectFirst("span.valor")?.text()?.let { NfceText.parseNumber(it) }
        val unitPrice = row.selectFirst("span.RvlUnit")?.text()?.let { NfceText.parseNumber(stripLabel(it)) }
        if (total == null && unitPrice == null) return null
        val resolvedTotal = total ?: round2(quantity * (unitPrice ?: 0.0))
        val resolvedUnit = unitPrice ?: if (quantity > 0) resolvedTotal / quantity else resolvedTotal
        return NfceItem(description, code, quantity, unit, resolvedUnit, resolvedTotal)
    }

    /** Remove "Qtde.:", "UN:", "Vl. Unit.:" do início. */
    protected fun stripLabel(text: String): String =
        NfceText.collapseSpaces(text).substringAfter(':', NfceText.collapseSpaces(text)).trim()

    // ------------------------------------------------------------------ cabeçalho

    data class Header(val name: String, val cnpj: String, val address: String)

    protected open fun parseHeader(doc: Document): Header {
        val nameEl = doc.selectFirst("#u20") ?: doc.selectFirst(".txtTopo")
        val name = nameEl?.text()?.let { NfceText.collapseSpaces(it) }.orEmpty()
        val texts = (nameEl?.parent()?.select("div.text") ?: doc.select("div.text")).map { NfceText.collapseSpaces(it.text()) }
        val cnpjText = texts.firstOrNull { it.contains("CNPJ", ignoreCase = true) }
        val cnpj = cnpjText?.let { CNPJ.find(it)?.value }?.let { NfceText.digitsOnly(it) }.orEmpty()
        val address = texts.firstOrNull { !it.contains("CNPJ", ignoreCase = true) && !it.contains("Inscri", ignoreCase = true) }
            ?.let { cleanAddress(it) }.orEmpty()
        return Header(name, cnpj, address)
    }

    protected fun cleanAddress(text: String): String =
        text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            .map { part -> if (part.all { it.isDigit() }) part.trimStart('0').ifEmpty { "0" } else part }
            .joinToString(", ")

    // ------------------------------------------------------------------ totais

    data class Totals(
        val count: Int?,
        val gross: Double?,
        val discount: Double?,
        val toPay: Double?,
        val payment: String
    )

    protected open fun parseTotals(doc: Document): Totals {
        var count: Int? = null
        var gross: Double? = null
        var discount: Double? = null
        var toPay: Double? = null
        val payments = mutableListOf<String>()
        val lines = doc.select("#totalNota > div").ifEmpty { doc.select("#totalNota div") }
        for (line in lines) {
            val labelEl = line.selectFirst("label") ?: continue
            val label = NfceText.collapseSpaces(labelEl.text())
            val value = line.selectFirst("span.totalNumb")?.text()
            val lower = label.lowercase()
            when {
                labelEl.hasClass("tx") -> {
                    if (!lower.startsWith("troco")) payments += cleanPayment(label)
                }
                lower.startsWith("qtd") -> count = NfceText.parseNumber(value)?.toInt()
                lower.startsWith("valor total") -> gross = NfceText.parseNumber(value)
                lower.startsWith("desconto") -> discount = NfceText.parseNumber(value)
                lower.startsWith("valor a pagar") -> toPay = NfceText.parseNumber(value)
            }
        }
        return Totals(count, gross, discount, toPay, payments.filter { it.isNotBlank() }.distinct().joinToString(" + "))
    }

    /** "18 - Transferência bancária, Carteira Digital" -> "Transferência bancária, Carteira Digital". */
    protected fun cleanPayment(label: String): String =
        label.replace(Regex("""^\d{1,2}\s*-\s*"""), "").trim()

    // ------------------------------------------------------------------ data e chave

    protected open fun parseIssuedAt(infosText: String): Long? {
        val m = EMISSION.find(infosText) ?: return null
        val (date, time, offset) = m.destructured
        val local = LocalDateTime.parse("$date $time", DATE_TIME)
        return if (offset.isNotBlank()) {
            OffsetDateTime.of(local, ZoneOffset.of(offset)).toInstant().toEpochMilli()
        } else {
            local.atZone(SAO_PAULO).toInstant().toEpochMilli()
        }
    }

    protected open fun parseKey(doc: Document, infosText: String): NfceKey? {
        doc.selectFirst("span.chave")?.text()?.let { NfceKey.parse(it) }?.let { return it }
        return NfceKey.findInText(infosText)
    }

    private fun fallbackDateFromKey(key: NfceKey): Long? = runCatching {
        LocalDateTime.of(key.year, key.month, 1, 12, 0).atZone(SAO_PAULO).toInstant().toEpochMilli()
    }.getOrNull()

    protected fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0

    companion object {
        val SAO_PAULO: ZoneId = ZoneId.of("America/Sao_Paulo")
        private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
        private val EMISSION = Regex("""Emiss[ãa]o:\s*(\d{2}/\d{2}/\d{4})\s+(\d{2}:\d{2}:\d{2})\s*([+-]\d{2}:\d{2})?""", RegexOption.IGNORE_CASE)
        private val CODE = Regex("""C[óo]digo:\s*([^)]+)""", RegexOption.IGNORE_CASE)
        private val CNPJ = Regex("""\d{2}\.?\d{3}\.?\d{3}/?\d{4}-?\d{2}""")
        private val NUMBER = Regex("""N[úu]mero:\s*(\d+)""", RegexOption.IGNORE_CASE)
        private val SERIES = Regex("""S[ée]rie:\s*(\d+)""", RegexOption.IGNORE_CASE)
    }
}

/** Genérico: qualquer estado que use o layout padrão do Portal NFC-e. */
object StandardPortalParser : StandardPortalParserBase() {
    override val name: String = "Genérico"
}

/**
 * Rio de Janeiro (www4.fazenda.rj.gov.br/consultaNFCe e consultadfe.fazenda.rj.gov.br).
 * Mesmo layout do portal padrão; a data de emissão às vezes vem com fuso ("13:09:52-03:00")
 * e o endereço traz o número com zeros à esquerda ("000043"), ambos tratados na base.
 */
object RjParser : StandardPortalParserBase() {
    override val name: String = "RJ"

    override fun canParse(doc: Document, sourceUrl: String): Boolean {
        if (sourceUrl.contains("fazenda.rj.gov.br", ignoreCase = true)) return true
        val key = doc.selectFirst("span.chave")?.text()?.let { NfceKey.parse(it) }
        return key?.cUF == 33
    }
}
