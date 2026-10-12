package com.smartmarket.domain.images

import com.smartmarket.data.dao.SupermarketDao
import com.smartmarket.data.prefs.SettingsRepository
import com.smartmarket.domain.ai.AiModelResolver
import com.smartmarket.domain.ai.GeminiClient
import com.smartmarket.domain.deals.OnlineMatch
import com.smartmarket.domain.online.MarketSourceRegistry
import kotlinx.coroutines.CancellationException

/**
 * Acha a foto de um produto: código de barras no Open Food Facts, busca nos sites dos mercados
 * (Atacadão, Spani) e busca no Open Food Facts. Só aceita foto do mesmo produto ([ImageMatch]);
 * com a IA ligada, ela confere a variante e o tamanho. O resultado fica no [ImageCache].
 */
class ProductImageFinder(
    private val settings: SettingsRepository,
    private val registry: MarketSourceRegistry,
    private val supermarketDao: SupermarketDao,
    private val off: OpenFoodFacts,
    private val gemini: GeminiClient,
    private val ai: AiModelResolver,
    val cache: ImageCache
) {
    /** Se a IA falhar (cota, rede), segue só com as regras até o fim desta execução. */
    @Volatile private var aiBroken = false

    suspend fun find(text: String, barcode: String? = null, force: Boolean = false): String? {
        if (text.isBlank() || !settings.current().photosEnabled) return null
        if (!force && cache.isKnown(text)) return cache.peek(text)

        val candidates = mutableListOf<ImageCandidate>()
        if (!barcode.isNullOrBlank() && barcode.count(Char::isDigit) >= 8) {
            candidates += safe { off.byBarcode(barcode) }
        }
        val term = OnlineMatch.searchTerm(text)
        val markets = safe { supermarketDao.getAllSupermarketsSync() }
        for (b in registry.bind(markets)) {
            candidates += safe {
                registry.search(b.source, term).mapNotNull { o ->
                    o.imageUrl?.let { ImageCandidate(listOfNotNull(o.name, o.brand).joinToString(" "), it, b.market.name) }
                }
            }
        }
        var ranked = ImageMatch.rank(text, candidates)
        if (ranked.isEmpty()) ranked = ImageMatch.rank(text, safe { off.search(term) })

        val chosen = choose(text, ranked)
        cache.put(text, chosen)
        return chosen
    }

    fun save() = cache.save()

    private suspend fun choose(text: String, ranked: List<ImageCandidate>): String? {
        if (ranked.isEmpty()) return null
        val s = settings.current()
        if (!s.photoAiReady || aiBroken) return ranked.first().imageUrl
        val top = ranked.take(MAX_FOR_AI)
        val ready = ai.resolve() ?: return ranked.first().imageUrl
        return try {
            gemini.pickImage(ready.apiKey, ready.model, text, top.map { it.name })?.let { top[it].imageUrl }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            aiBroken = true
            ranked.first().imageUrl
        }
    }

    private suspend fun <T> safe(block: suspend () -> List<T>): List<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        emptyList()
    }

    companion object {
        const val MAX_FOR_AI = 6
    }
}
