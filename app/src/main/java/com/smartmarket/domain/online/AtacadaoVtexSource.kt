package com.smartmarket.domain.online

import com.smartmarket.data.entity.OnlineSource
import com.smartmarket.data.prefs.SettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Busca no site do Atacadão (VTEX).
 *
 * - Catálogo: `GET /api/catalog_system/pub/products/search?ft=...` (redireciona para `/io/...` e responde 206).
 * - Região: `GET /api/checkout/pub/regions?country=BRA&postalCode=CEP` devolve o `regionId` e o seller da loja.
 *   A loja de Resende é o seller `atacadaobr207` (Av. Francisco Fortes Filho, Jardim Aliança).
 *   O CEP genérico 27500-000 não tem seller; por isso caímos para [FALLBACK_POSTAL_CODE].
 * - O preço regional só é aplicado com o cookie `vtex_segment` (o parâmetro `regionId` na URL é ignorado).
 */
class AtacadaoVtexSource(
    httpClient: OkHttpClient,
    private val settings: SettingsRepository
) : MarketSearchSource {

    override val id: String = OnlineSource.VTEX_ATACADAO
    override val displayName: String = "Atacadão"

    private val http = httpClient.forOnlineSearch()
    private val regionLock = Mutex()

    @Volatile private var region: ResolvedRegion? = null

    private data class ResolvedRegion(val id: String, val sellerIds: Set<String>, val postalCode: String)

    override suspend fun search(term: String, page: Int): List<OnlineOffer> {
        val clean = term.trim()
        if (clean.isEmpty()) return emptyList()
        val r = runCatching { resolveRegion() }.getOrNull() // sem região, busca nacional mesmo

        val from = page * PAGE_SIZE
        val url = "$BASE/api/catalog_system/pub/products/search".toHttpUrl().newBuilder()
            .addQueryParameter("ft", clean)
            .addQueryParameter("_from", from.toString())
            .addQueryParameter("_to", (from + PAGE_SIZE - 1).toString())
            .apply { if (r != null) addQueryParameter("regionId", r.id) }
            .build()
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .apply { if (r != null) header("Cookie", "vtex_segment=${AtacadaoVtexParser.segmentCookie(r.id)}") }
            .build()

        val res = try {
            http.fetch(request)
        } catch (e: IOException) {
            throw networkError(e)
        }
        // Fora do intervalo de páginas a VTEX responde 400/416: tratamos como "sem mais resultados"
        if (page > 0 && (res.code == 400 || res.code == 416)) return emptyList()
        if (!res.isSuccessful) throw SourceError("O site do Atacadão respondeu com erro (${res.code}).")
        return try {
            AtacadaoVtexParser.parseOffers(res.body, id, r?.sellerIds.orEmpty())
        } catch (e: SerializationException) {
            throw SourceError("Resposta inesperada do site do Atacadão.", e)
        } catch (e: IllegalArgumentException) {
            throw SourceError("Resposta inesperada do site do Atacadão.", e)
        }
    }

    /** Região da loja pelo CEP das configurações; guarda o id em [SettingsRepository.setAtacadaoRegionId]. */
    private suspend fun resolveRegion(): ResolvedRegion? = regionLock.withLock {
        val s = settings.current()
        val cep = s.postalCode.filter(Char::isDigit)
        region?.let { if (it.postalCode == cep && (s.atacadaoRegionId.isBlank() || it.id == s.atacadaoRegionId)) return it }

        // Id já salvo: usa direto (os sellers só servem de preferência, não são obrigatórios)
        if (s.atacadaoRegionId.isNotBlank()) {
            return ResolvedRegion(s.atacadaoRegionId, emptySet(), cep).also { region = it }
        }

        val found = fetchRegion(cep)?.takeIf { it.sellerIds.isNotEmpty() }
            ?: if (cep != FALLBACK_POSTAL_CODE) fetchRegion(FALLBACK_POSTAL_CODE)?.copy(postalCode = cep) else null
        if (found != null && found.sellerIds.isNotEmpty()) {
            runCatching { settings.setAtacadaoRegionId(found.id) }
            region = found
            return found
        }
        null
    }

    private suspend fun fetchRegion(cep: String): ResolvedRegion? {
        if (cep.length != 8) return null
        val url = "$BASE/api/checkout/pub/regions".toHttpUrl().newBuilder()
            .addQueryParameter("country", "BRA")
            .addQueryParameter("postalCode", cep)
            .build()
        val res = http.fetch(Request.Builder().url(url).header("Accept", "application/json").build())
        if (!res.isSuccessful) return null
        val first = runCatching { AtacadaoVtexParser.parseRegions(res.body) }.getOrNull()?.firstOrNull() ?: return null
        if (first.id.isBlank()) return null
        return ResolvedRegion(first.id, first.sellers.map { it.id }.toSet(), cep)
    }

    companion object {
        const val BASE = "https://www.atacadao.com.br"
        const val PAGE_SIZE = 20

        /** CEP atendido pela loja de Resende (seller atacadaobr207). */
        const val FALLBACK_POSTAL_CODE = "27511000"
        const val RESENDE_SELLER_ID = "atacadaobr207"
    }
}
