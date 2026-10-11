package com.smartmarket.domain.online

import com.smartmarket.data.entity.OnlineSource
import com.smartmarket.data.entity.Supermarket
import com.smartmarket.data.prefs.SettingsRepository
import okhttp3.OkHttpClient

/** Mercado cadastrado com a fonte de busca do site dele. */
data class MarketBinding(val market: Supermarket, val source: MarketSearchSource)

/**
 * Associa cada [Supermarket] com `onlineSource != NONE` à sua fonte e guarda as buscas
 * em cache por 6h. O Dom Atacadista não tem catálogo online (só encarte), então não tem fonte.
 */
class MarketSourceRegistry(
    private val sources: Map<String, MarketSearchSource>,
    val cache: OnlineSearchCache = OnlineSearchCache()
) {

    fun sourceFor(market: Supermarket): MarketSearchSource? =
        if (market.onlineSource == OnlineSource.NONE) null else sources[market.onlineSource]

    fun bind(markets: List<Supermarket>): List<MarketBinding> =
        markets.mapNotNull { m -> sourceFor(m)?.let { MarketBinding(m, it) } }

    /** Busca usando o cache; [forceRefresh] ignora o cache (botão "tentar de novo" depois de erro não precisa). */
    suspend fun search(source: MarketSearchSource, term: String, page: Int = 0, forceRefresh: Boolean = false): List<OnlineOffer> {
        if (!forceRefresh) cache.get(source.id, term, page)?.let { return it }
        val offers = source.search(term, page)
        cache.put(source.id, term, page, offers)
        return offers
    }

    companion object {
        @Volatile private var instance: MarketSourceRegistry? = null

        /**
         * Instância única do app, para o cache sobreviver entre telas.
         * (Pode ir para o AppContainer no futuro; aqui evita mexer nele.)
         */
        fun get(httpClient: OkHttpClient, settings: SettingsRepository): MarketSourceRegistry =
            instance ?: synchronized(this) {
                instance ?: MarketSourceRegistry(
                    mapOf(
                        OnlineSource.VTEX_ATACADAO to AtacadaoVtexSource(httpClient, settings),
                        OnlineSource.SPANI_ONLINE to SpaniOnlineSource(httpClient)
                    )
                ).also { instance = it }
            }
    }
}
