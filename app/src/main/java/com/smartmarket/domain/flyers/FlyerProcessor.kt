package com.smartmarket.domain.flyers

import com.smartmarket.data.db.AppDatabase
import com.smartmarket.data.entity.Flyer
import com.smartmarket.data.entity.FlyerKind
import com.smartmarket.data.entity.FlyerOffer
import com.smartmarket.data.entity.FlyerStatus
import com.smartmarket.data.entity.OfferStatus
import com.smartmarket.data.entity.Supermarket
import com.smartmarket.domain.matching.ProductMatcher
import com.smartmarket.domain.ocr.OcrPage
import com.smartmarket.domain.ocr.OcrProcessor
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Resumo do processamento de um encarte (ou da falha de um mercado). */
data class FlyerSyncResult(
    val supermarketId: Long,
    val marketName: String,
    val title: String,
    val flyerId: Long?,
    val offerCount: Int,
    /** Ofertas de produtos que estão nas listas de compras atuais. */
    val listMatches: Int,
    /** Encarte já processado antes (mesmo hash). */
    val skipped: Boolean,
    val error: String? = null
) {
    val isNew: Boolean get() = !skipped && error == null && flyerId != null
}

/**
 * Busca os encartes de cada mercado com `flyerUrl`, baixa os arquivos, pula os já vistos
 * (hash), lê com OCR, separa as ofertas e grava como pendentes de revisão.
 * A falha de um mercado não interrompe os outros.
 */
class FlyerProcessor(
    private val db: AppDatabase,
    private val downloader: FlyerDownloader,
    private val ocr: OcrProcessor,
    private val matcher: ProductMatcher,
    private val interpreter: FlyerInterpreter
) {
    private val flyerDao = db.flyerDao()

    /** Mensagens de progresso, ex.: "Spani: lendo página 3 de 12". */
    fun interface Progress { fun update(message: String) }

    suspend fun syncAll(progress: Progress = Progress { }): List<FlyerSyncResult> {
        val markets = db.supermarketDao().getAllSupermarketsSync()
            .filter { it.flyerUrl.isNotBlank() && it.flyerKind != FlyerKind.NONE }
        val listProducts = currentListProductIds()
        val results = mutableListOf<FlyerSyncResult>()
        for (m in markets) {
            results += try {
                syncMarket(m, listProducts, progress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                listOf(recordFailure(m, "pagina-" + today(), m.flyerUrl, null, e))
            }
        }
        return results
    }

    suspend fun syncMarket(market: Supermarket, listProducts: Set<Long>, progress: Progress = Progress { }): List<FlyerSyncResult> {
        val name = shortName(market.name)
        progress.update("$name: abrindo o site")
        val html = downloader.fetchHtml(market.flyerUrl)
        val sources = when (market.flyerKind) {
            FlyerKind.PDF_LINK -> {
                val city = market.city.ifBlank { "resende" }
                FlyerLinkExtractor.spaniSources(html, city.lowercase(Locale.ROOT)).ifEmpty {
                    FlyerLinkExtractor.extractPdfLinks(html).map { resolve(market.flyerUrl, it) }
                        .map { FlyerSource(key = it, title = "Encarte", urls = listOf(it), isPdf = true) }
                }
            }
            FlyerKind.IMAGE_PAGES -> FlyerLinkExtractor.domSources(html)
            FlyerKind.ATACADAO_STORE -> FlyerLinkExtractor.atacadaoSources(
                html, SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
            )
            else -> emptyList()
        }
        if (sources.isEmpty()) {
            return listOf(recordFailure(market, "sem-encarte-" + today(), market.flyerUrl, null,
                IllegalStateException("Nenhum encarte encontrado no site")))
        }
        return sources.map { src ->
            try {
                processSource(market, src, listProducts, progress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                recordFailure(market, "erro-" + FlyerDownloader.hashOfUrls(src.urls).take(16) + "-" + today(),
                    sourceUrlOf(market, src), src.title, e)
            }
        }
    }

    private suspend fun processSource(
        market: Supermarket,
        src: FlyerSource,
        listProducts: Set<Long>,
        progress: Progress
    ): FlyerSyncResult {
        val name = shortName(market.name)
        val files = mutableListOf<File>()
        try {
            val hash: String
            val pages = mutableListOf<OcrPage>()
            if (src.isPdf) {
                progress.update("$name: baixando o encarte")
                val file = downloader.download(src.urls.first(), "m${market.id}_${System.currentTimeMillis()}.pdf")
                files += file
                hash = FlyerDownloader.sha256(file)
                if (alreadyProcessed(market.id, hash)) return skipped(market, src)
                pages += ocr.readPdfFile(file) { cur, total -> progress.update("$name: lendo página $cur de $total") }
            } else {
                hash = FlyerDownloader.hashOfUrls(src.urls)
                if (alreadyProcessed(market.id, hash)) return skipped(market, src)
                src.urls.forEachIndexed { i, url ->
                    progress.update("$name: lendo página ${i + 1} de ${src.urls.size}")
                    val file = downloader.download(url, "m${market.id}_p${i}_${System.currentTimeMillis()}.jpg")
                    try {
                        pages += ocr.readImageFile(file, i)
                    } finally {
                        file.delete()
                    }
                }
            }

            val parsed = interpreter.interpret(pages, market.name) { cur, total, ai ->
                progress.update("$name: " + (if (ai) "IA lendo" else "separando ofertas da") + " página $cur de $total")
            }.offers
            val matched = matchOffers(parsed, matcher)
            val existing = flyerDao.findByHash(market.id, hash)
            val now = System.currentTimeMillis()
            val flyerId = flyerDao.insertFlyer(
                Flyer(
                    id = existing?.id ?: 0,
                    supermarketId = market.id,
                    sourceUrl = sourceUrlOf(market, src),
                    contentHash = hash,
                    fetchedAt = now,
                    pageCount = pages.size,
                    offerCount = matched.size,
                    status = FlyerStatus.PROCESSED
                )
            )
            flyerDao.insertOffers(matched.map { m ->
                FlyerOffer(
                    flyerId = flyerId,
                    supermarketId = market.id,
                    productId = m.outcome.product?.id,
                    rawText = OfferText.compose(m.parsed.description, m.parsed.note),
                    price = m.parsed.price,
                    confidence = m.outcome.confidence,
                    status = OfferStatus.PENDING
                )
            })
            val fromList = matched.count { m -> m.outcome.product?.id?.let { it in listProducts } == true }
            return FlyerSyncResult(market.id, name, src.title, flyerId, matched.size, fromList, skipped = false)
        } finally {
            downloader.clear(files)
        }
    }

    private suspend fun alreadyProcessed(marketId: Long, hash: String): Boolean {
        val f = flyerDao.findByHash(marketId, hash) ?: return false
        return f.status != FlyerStatus.FAILED
    }

    private fun skipped(market: Supermarket, src: FlyerSource) =
        FlyerSyncResult(market.id, shortName(market.name), src.title, null, 0, 0, skipped = true)

    private suspend fun recordFailure(
        market: Supermarket,
        hash: String,
        sourceUrl: String,
        title: String?,
        e: Throwable
    ): FlyerSyncResult {
        val message = friendlyError(e)
        val id = runCatching {
            val existing = flyerDao.findByHash(market.id, hash)
            flyerDao.insertFlyer(
                Flyer(
                    id = existing?.id ?: 0,
                    supermarketId = market.id,
                    sourceUrl = sourceUrl,
                    contentHash = hash,
                    fetchedAt = System.currentTimeMillis(),
                    status = FlyerStatus.FAILED,
                    errorMessage = message
                )
            )
        }.getOrNull()
        return FlyerSyncResult(market.id, shortName(market.name), title ?: "Encarte", id, 0, 0, skipped = false, error = message)
    }

    /** Produtos das listas de compras não arquivadas. */
    suspend fun currentListProductIds(): Set<Long> {
        val dao = db.shoppingListDao()
        val active = dao.getAllListsSync().filter { !it.isArchived }.map { it.id }.toSet()
        if (active.isEmpty()) return emptySet()
        return dao.getAllItemsSync().filter { it.shoppingListId in active }.map { it.productId }.toSet()
    }

    private fun sourceUrlOf(market: Supermarket, src: FlyerSource): String =
        if (src.isPdf && market.flyerKind != FlyerKind.ATACADAO_STORE) src.urls.first()
        else FlyerTitle.encode(if (src.isPdf) src.urls.first() else market.flyerUrl, src.title)

    private fun resolve(base: String, link: String): String =
        if (link.startsWith("http")) link else base.toHttpUrlOrNull()?.resolve(link)?.toString() ?: link

    private fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())

    companion object {
        /** "Spani Atacadista Resende" -> "Spani"; "Dom Atacadista Resende" -> "Dom Atacadista". */
        fun shortName(name: String): String {
            val n = name.replace(Regex("""\s+(Resende|RJ|-.*)$""", RegexOption.IGNORE_CASE), "").trim()
            return when {
                n.startsWith("Spani", ignoreCase = true) -> "Spani"
                else -> n.ifBlank { name }
            }
        }

        fun friendlyError(e: Throwable): String = when (e) {
            is java.net.UnknownHostException -> "Sem conexão com a internet"
            is java.net.SocketTimeoutException -> "O site demorou demais para responder"
            is java.io.IOException -> e.message ?: "Falha de rede"
            is OutOfMemoryError -> "Memória insuficiente para ler o encarte"
            else -> e.message ?: e.javaClass.simpleName
        }
    }
}
