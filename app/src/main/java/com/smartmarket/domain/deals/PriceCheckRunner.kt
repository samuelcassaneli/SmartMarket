package com.smartmarket.domain.deals

import com.smartmarket.AppContainer
import com.smartmarket.data.entity.PriceSource
import com.smartmarket.data.repo.PriceEntry
import com.smartmarket.domain.flyers.FlyerSyncResult
import com.smartmarket.domain.flyers.OfferText
import com.smartmarket.domain.matching.FuzzyMatcher
import com.smartmarket.domain.online.MarketSourceRegistry
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
                val (_, note) = OfferText.split(o.rawText)
                found += FoundPrice(pid, p.name, o.supermarketId, marketById[o.supermarketId]?.name ?: r.marketName,
                    o.price, origin = "Encarte", note = note)
            }
        }

        // 4) busca nos sites dos mercados (Atacadão, Spani) para os produtos acompanhados
        val registry = MarketSourceRegistry.get(c.httpClient, c.settings)
        val bindings = registry.bind(markets)
        var checked = 0
        val online = mutableListOf<PriceEntry>()
        if (bindings.isNotEmpty()) {
            for (pid in tracked.take(MAX_ONLINE_PRODUCTS)) {
                val p = products[pid] ?: continue
                progress.update("Consultando preços: ${p.name}")
                for (b in bindings) {
                    val offers = try {
                        registry.search(b.source, p.name)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        continue // site fora do ar ou indisponível: segue com os outros
                    }
                    val best = offers.filter { it.available && it.price > 0 }
                        .map { it to FuzzyMatcher.calculateAffinity(it.name, p.name) }
                        .filter { it.second >= FuzzyMatcher.AUTO_MATCH_THRESHOLD }
                        .maxWithOrNull(compareBy<Pair<com.smartmarket.domain.online.OnlineOffer, Double>> { it.second }.thenBy { -it.first.price })
                        ?.first ?: continue
                    checked++
                    found += FoundPrice(pid, p.name, b.market.id, b.market.name, best.price, origin = "Site do mercado")
                    online += PriceEntry(pid, b.market.id, best.price, PriceSource.ONLINE, startedAt, note = best.name)
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
