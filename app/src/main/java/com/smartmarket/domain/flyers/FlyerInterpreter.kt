package com.smartmarket.domain.flyers

import com.smartmarket.data.prefs.SettingsRepository
import com.smartmarket.domain.ai.AiException
import com.smartmarket.domain.ai.GeminiClient
import com.smartmarket.domain.ocr.EncarteParser
import com.smartmarket.domain.ocr.OcrPage
import com.smartmarket.domain.ocr.ParsedOffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Resultado da interpretação das páginas: ofertas e quantas páginas a IA leu. */
data class InterpretResult(val offers: List<ParsedOffer>, val aiPages: Int, val localPages: Int, val aiError: String? = null)

/**
 * Transforma as páginas lidas em ofertas. Se a leitura com IA estiver ligada (chave do usuário),
 * cada página vai para o Gemini; páginas que falharem (cota, rede) usam o parser local.
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
        if (!s.aiReady) {
            return InterpretResult(EncarteParser.parse(pages, marketNames), aiPages = 0, localPages = pages.size)
        }
        val model = s.geminiModel.ifBlank {
            runCatching { gemini.discoverModel(s.geminiApiKey) }
                .onSuccess { settings.setGemini(s.geminiApiKey, it) }
                .getOrNull()
        }
        if (model.isNullOrBlank()) {
            return InterpretResult(EncarteParser.parse(pages, marketNames), 0, pages.size, aiError = "Chave da IA inválida")
        }

        val offers = mutableListOf<ParsedOffer>()
        var aiPages = 0
        var lastError: String? = null
        var aiBroken = false
        pages.forEachIndexed { i, page ->
            onProgress(i + 1, pages.size, !aiBroken)
            val fromAi = if (aiBroken || (page.lines.isEmpty() && page.image == null)) null else try {
                if (aiPages > 0) delay(PACING_MS) // respeita o limite por minuto da camada gratuita
                gemini.readPage(s.geminiApiKey, model, page, marketName).also { aiPages++ }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e.message
                // chave inválida: não adianta tentar as outras páginas
                if (e is AiException && !e.retryable) aiBroken = true
                null
            }
            offers += fromAi ?: EncarteParser.parsePage(page.lines, page.index, marketNames)
        }
        val seen = HashSet<String>()
        val unique = offers.filter { seen.add(EncarteParser.normalize(it.description) + "|" + it.price) }
        return InterpretResult(unique, aiPages, pages.size - aiPages, lastError)
    }

    companion object {
        private const val PACING_MS = 4_000L
    }
}
