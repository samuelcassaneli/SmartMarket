package com.smartmarket.domain.flyers

import com.smartmarket.data.prefs.SettingsRepository
import com.smartmarket.domain.ai.AiException
import com.smartmarket.domain.ai.GeminiClient
import com.smartmarket.domain.ocr.EncarteParser
import com.smartmarket.domain.ocr.OcrPage
import com.smartmarket.domain.ocr.ParsedOffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Resultado da interpretação das páginas: ofertas e quantas páginas a IA leu. */
data class InterpretResult(val offers: List<ParsedOffer>, val aiPages: Int, val localPages: Int, val aiError: String? = null)

/**
 * Transforma as páginas lidas em ofertas. Com a leitura por IA ligada (chave do usuário), as páginas
 * vão para o Gemini em lotes (texto de PDF junto num pedido só, imagens uma por pedido), com até
 * [PARALLEL] pedidos ao mesmo tempo. Lote que falhar usa o parser local.
 */
class FlyerInterpreter(
    private val settings: SettingsRepository,
    private val gemini: GeminiClient
) {
    suspend fun interpret(
        pages: List<OcrPage>,
        marketName: String?,
        onProgress: (current: Int, total: Int, usingAi: Boolean) -> Unit = { _, _, _ -> }
    ): InterpretResult {
        val s = settings.current()
        val marketNames = listOfNotNull(marketName)
        if (!s.aiReady || pages.isEmpty()) {
            return InterpretResult(EncarteParser.parse(pages, marketNames), aiPages = 0, localPages = pages.size)
        }
        val model = currentModel() ?: return InterpretResult(
            EncarteParser.parse(pages, marketNames), 0, pages.size, aiError = "Chave da IA inválida"
        )

        val batches = batch(pages)
        val done = AtomicInteger(0)
        val aiPages = AtomicInteger(0)
        val broken = AtomicBoolean(false)
        var lastError: String? = null
        val gate = Semaphore(PARALLEL)
        onProgress(0, pages.size, true)

        val results: List<List<ParsedOffer>> = coroutineScope {
            batches.map { b ->
                async {
                    gate.withPermit {
                        val fromAi = if (broken.get()) null else try {
                            val r = if (b.isText) gemini.readTextPages(s.geminiApiKey, model, b.pages, marketName)
                            else gemini.readImagePage(s.geminiApiKey, model, b.pages.single(), marketName)
                            // resposta vazia para página com bastante texto: provável falha, usa o local
                            r.takeIf { it.isNotEmpty() || b.pages.sumOf { p -> p.lines.size } < 15 }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            lastError = e.message
                            if (e is AiException && !e.retryable) broken.set(true) // chave inválida: para de tentar
                            null
                        }
                        if (fromAi != null) aiPages.addAndGet(b.pages.size)
                        val local = b.pages.flatMap { EncarteParser.parsePage(it.lines, it.index, marketNames) }
                        // a IA às vezes pega o preço do card vizinho: confere com a leitura pela posição
                        val out = fromAi?.let { AiPriceCheck.reconcile(it, local) } ?: local
                        onProgress(done.addAndGet(b.pages.size), pages.size, !broken.get())
                        out
                    }
                }
            }.awaitAll()
        }

        val seen = HashSet<String>()
        val unique = results.flatten().filter { seen.add(EncarteParser.normalize(it.description) + "|" + it.price) }
        return InterpretResult(unique, aiPages.get(), pages.size - aiPages.get(), lastError)
    }

    /**
     * Pede à IA, entre os links de uma página desconhecida, quais são encartes.
     * Só funciona com a IA ligada; sem ela devolve vazio.
     */
    suspend fun findFlyerSources(pageUrl: String, html: String): List<FlyerSource> {
        val s = settings.current()
        if (!s.aiReady) return emptyList()
        val model = currentModel() ?: return emptyList()
        val (pdfs, images) = gemini.findFlyerLinks(s.geminiApiKey, model, pageUrl, FlyerLinkExtractor.linkCandidates(html, pageUrl))
        if (pdfs.isNotEmpty()) return pdfs.take(5).map { FlyerSource(key = it, title = "Encarte", urls = listOf(it), isPdf = true) }
        if (images.isNotEmpty()) return listOf(FlyerSource(key = "ia-" + images.first().hashCode(), title = "Encarte", urls = images.take(40), isPdf = false))
        return emptyList()
    }

    private suspend fun currentModel(): String? {
        val s = settings.current()
        if (s.geminiModel.isNotBlank()) return s.geminiModel
        return runCatching { gemini.discoverModel(s.geminiApiKey, s.aiFastMode) }
            .onSuccess { settings.setGemini(s.geminiApiKey, it) }
            .getOrNull()
    }

    private class Batch(val pages: List<OcrPage>, val isText: Boolean)

    /** Páginas de texto vizinhas juntas (até ~[MAX_BATCH_CHARS] caracteres); imagens sozinhas. */
    private fun batch(pages: List<OcrPage>): List<Batch> {
        val out = mutableListOf<Batch>()
        var current = mutableListOf<OcrPage>()
        var chars = 0
        fun flush() {
            if (current.isNotEmpty()) out += Batch(current, isText = true)
            current = mutableListOf(); chars = 0
        }
        for (p in pages) {
            val isText = p.fromPdfText || p.image == null
            if (!isText) {
                flush()
                out += Batch(listOf(p), isText = false)
                continue
            }
            val size = p.lines.sumOf { it.text.length + 12 }
            if (current.isNotEmpty() && (chars + size > MAX_BATCH_CHARS || current.size >= MAX_BATCH_PAGES)) flush()
            current += p
            chars += size
        }
        flush()
        return out
    }

    companion object {
        const val PARALLEL = 2
        private const val MAX_BATCH_CHARS = 14_000
        private const val MAX_BATCH_PAGES = 2
    }
}
