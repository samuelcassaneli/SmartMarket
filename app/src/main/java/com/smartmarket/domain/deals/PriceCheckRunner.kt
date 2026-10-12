package com.smartmarket.domain.deals

import com.smartmarket.AppContainer
import com.smartmarket.data.entity.PriceSource
import com.smartmarket.data.repo.PriceEntry
import com.smartmarket.domain.flyers.FlyerSyncResult
import com.smartmarket.domain.flyers.OfferText
import com.smartmarket.domain.matching.FuzzyMatcher
import com.smartmarket.domain.online.MarketSourceRegistry
import com.smartmarket.domain.online.OnlineOffer
import com.smartmarket.domain.matching.SaleForm
import com.smartmarket.domain.matching.BrandGuess
import com.smartmarket.domain.ai.GeminiModels
import com.smartmarket.data.dao.PaidItem
import com.smartmarket.data.entity.Product
import kotlinx.coroutines.CancellationException

/**
 * Verificação periódica: junta os preços dos encartes recém-lidos e dos sites dos mercados para os
 * produtos que o usuário compra, compara com a última compra (ou a última verificação) e monta o
 * relatório de promoções.
 */
class PriceCheckRunner(private val c: AppContainer) {

    fun interface Progress { fun update(message: String) }

    suspend fun run(flyerResults: List<FlyerSyncResult>, progress: Progress = Progress { }): DealReport {
        val startedAt = System.currentTimeMillis()
        val db = c.db
        val products = db.productDao().getAllProductsSync().associateBy { it.id }
        val markets = db.supermarketDao().getAllSupermarketsSync()
        val marketById = markets.associateBy { it.id }

        // 1) referências (antes de gravar qualquer preço novo)
        val baselines = buildBaselines(startedAt)

        // 2) produtos acompanhados: comprados nos últimos 120 dias, nas listas ativas, com alerta ou favoritos
        val tracked = trackedProducts(startedAt).filter { it in products }

        val found = mutableListOf<FoundPrice>()

        // 3) ofertas dos encartes novos já vinculadas a produtos
        for (r in flyerResults.filter { it.isNew }) {
            val flyerId = r.flyerId ?: continue
            for (o in db.flyerDao().getOffersForFlyerSync(flyerId)) {
                val pid = o.productId ?: continue
                val p = products[pid] ?: continue
                val (desc, cond) = OfferText.split(o.rawText)
                val note = listOfNotNull(
                    BrandGuess.extract(desc, p.name)?.let { "marca $it" },
                    cond.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                found += FoundPrice(pid, p.name, o.supermarketId, marketById[o.supermarketId]?.name ?: r.marketName,
                    o.price, origin = "Encarte", note = note)
            }
        }

        // 4) busca nos sites dos mercados (Atacadão, Spani) para os produtos acompanhados.
        //    Só vale o MESMO produto: mesma forma de venda (granel x pacote) e mesmo tamanho; com IA ligada, ela confere.
        val registry = MarketSourceRegistry.get(c.httpClient, c.settings)
        val bindings = registry.bind(markets)
        var checked = 0
        val online = mutableListOf<PriceEntry>()
        if (bindings.isNotEmpty()) {
            val paidItems = db.purchaseDao().getPaidItems().groupBy { it.productId }.mapValues { it.value.first() }
            val ai = AiChecker().also { it.init() }
            for (pid in tracked.take(MAX_ONLINE_PRODUCTS)) {
                val p = products[pid] ?: continue
                progress.update("Consultando preços: ${p.name}")
                val paid = paidItems[pid]
                val form = SaleForm.ofProduct(p.name, p.unit, paid?.description, paid?.unit)
                val results = mutableListOf<Triple<Long, String, OnlineOffer>>()
                for (b in bindings) {
                    try {
                        registry.search(b.source, OnlineMatch.searchTerm(p.name)).forEach { results += Triple(b.market.id, b.market.name, it) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        // site fora do ar ou indisponível: segue com os outros
                    }
                }
                val picked = if (ai.ready) {
                    val cands = OnlineMatch.candidates(p.name, form, results, FuzzyMatcher.SUGGESTION_THRESHOLD)
                        .take(OnlineMatch.MAX_FOR_AI)
                    val approved = ai.approve(p, form, paid, cands)
                    if (approved != null) OnlineMatch.cheapestApproved(cands, approved)
                    else OnlineMatch.bestByAffinity(cands)
                } else {
                    OnlineMatch.bestByAffinity(OnlineMatch.candidates(p.name, form, results, FuzzyMatcher.AUTO_MATCH_THRESHOLD))
                }
                for (best in picked) {
                    checked++
                    val note = best.offer.name + if (form == SaleForm.Bulk) " (preço por kg)" else ""
                    found += FoundPrice(pid, p.name, best.marketId, best.marketName, best.price, origin = "Site do mercado", note = note)
                    online += PriceEntry(pid, best.marketId, best.price, PriceSource.ONLINE, startedAt, note = note)
                }
            }
        }
        if (online.isNotEmpty()) c.priceRepository.recordPrices(online)

        // 5) promoções
        val deals = DealDetector.detect(found, baselines)
        return DealReport(
            checkedAt = startedAt,
            deals = deals,
            flyersRead = flyerResults.count { it.isNew },
            pricesChecked = checked + found.count { it.origin == "Encarte" }
        )
    }

    /** Conferência pela IA (Gemini do usuário). Se falhar uma vez, segue sem ela nesta verificação. */
    private inner class AiChecker {
        private var model: String? = null
        var ready = false
            private set
        private var key = ""

        suspend fun init() {
            val s = c.settings.current()
            if (!s.aiMatchReady) return
            key = s.geminiApiKey
            model = s.geminiModel.ifBlank {
                runCatching { c.gemini.discoverModel(key, s.aiFastMode) }
                    .onSuccess { c.settings.setGemini(key, it) }
                    .getOrNull().orEmpty()
            }.ifBlank { null }
            ready = model != null
        }

        suspend fun approve(p: Product, form: SaleForm, paid: PaidItem?, cands: List<OnlineCandidate>): Set<Int>? {
            if (cands.isEmpty()) return emptySet()
            val m = model ?: return null
            val product = GeminiModels.MatchProduct(
                name = p.name,
                saleInfo = form.describe(),
                lastPaidDescription = paid?.description,
                lastPaidUnit = paid?.unit,
                lastPaidPrice = paid?.price?.takeIf { it > 0 }
            )
            val list = cands.map {
                GeminiModels.MatchCandidate(it.marketName, it.offer.name, it.offer.price, describeOffer(it))
            }
            return try {
                c.gemini.sameProduct(key, m, product, list)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ready = false
                null
            }
        }

        private fun describeOffer(cand: OnlineCandidate): String {
            val unit = cand.offer.measurementUnit?.takeIf { it.isNotBlank() }
            val mult = cand.offer.unitMultiplier?.takeIf { it > 0 && it != 1.0 }
            return buildString {
                append(cand.form.describe())
                if (unit != null) append("; unidade de venda do site: ").append(unit)
                if (mult != null) append(" (").append(mult).append(" por unidade)")
            }
        }
    }

    private suspend fun buildBaselines(now: Long): Map<Long, Baseline> {
        val paid = c.db.purchaseDao().getPaidPrices().groupBy { it.productId }.mapValues { it.value.first() }
        val seen = c.db.productPriceDao().getObservationsBetween(now - 75L * DAY, now)
            .groupBy { it.productId }.mapValues { it.value.first() }
        return (paid.keys + seen.keys).associateWith { pid ->
            Baseline(
                lastPaid = paid[pid]?.price,
                lastPaidAt = paid[pid]?.at,
                lastSeen = seen[pid]?.price,
                lastSeenAt = seen[pid]?.observedAt
            )
        }
    }

    private suspend fun trackedProducts(now: Long): List<Long> {
        val db = c.db
        val bought = db.purchaseDao().getProductsBoughtSince(now - 120L * DAY)
        val listDao = db.shoppingListDao()
        val activeLists = listDao.getAllListsSync().filter { !it.isArchived }.map { it.id }.toSet()
        val inLists = listDao.getAllItemsSync().filter { it.shoppingListId in activeLists }.map { it.productId }
        val alerts = db.priceAlertDao().getAllSync().filter { it.active }.map { it.productId }
        val favorites = db.productDao().getAllProductsSync().filter { it.isFavorite }.map { it.id }
        // ordem de prioridade: alertas, comprados, listas, favoritos
        return (alerts + bought + inLists + favorites).distinct()
    }

    companion object {
        private const val DAY = 24L * 60 * 60 * 1000
        /** Limite de consultas por verificação, para não sobrecarregar os sites. */
        const val MAX_ONLINE_PRODUCTS = 30
    }
}
