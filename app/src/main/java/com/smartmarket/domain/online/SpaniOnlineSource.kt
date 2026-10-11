package com.smartmarket.domain.online

import com.smartmarket.data.entity.OnlineSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Busca no Spani Online (spanionline.com.br), loja VipCommerce ("Supermercados Online").
 *
 * Fluxo levantado no bundle JS do site (o mesmo que o navegador faz para um visitante anônimo):
 * 1. `GET {api}/organizacoes/filiais/dominio/spanionline.com.br` → org 67, filial VipCommerce 108 (público, verificado).
 * 2. `POST {api}/org/{org}/auth/loja/login` com a chave pública da loja (está no JS do site) → JWT anônimo.
 * 3. `GET {api}/org/{org}/loja/omnichannel/{filialVip}` → id da filial e bucket das imagens.
 * 4. `GET {api}/org/{org}/filial/{f}/loja/centros_distribuicoes/retiradas?cep=` → loja (CD) de Resende.
 * 5. `GET {api}/org/{org}/filial/{f}/centro_distribuicao/{cd}/loja/buscas/produtos/termo/{termo}?page=N`.
 *
 * Sem o token a API responde 401 ("Token not provided"). Qualquer falha estrutural vira
 * [SourceUnavailable], e o app segue usando o encarte do Spani.
 */
class SpaniOnlineSource(httpClient: OkHttpClient) : MarketSearchSource {

    override val id: String = OnlineSource.SPANI_ONLINE
    override val displayName: String = "Spani"

    private val http = httpClient.forOnlineSearch()
    private val lock = Mutex()

    @Volatile private var session: Session? = null

    private data class Session(
        val orgId: Long,
        val domainKey: String,
        val token: String,
        val filialId: Long,
        val cdId: Long,
        val imageBase: String?
    )

    override suspend fun search(term: String, page: Int): List<OnlineOffer> {
        val clean = term.trim()
        if (clean.isEmpty()) return emptyList()
        try {
            var s = ensureSession()
            var res = http.fetch(searchRequest(s, clean, page))
            if (res.code == 401 || res.code == 403) {
                // Token expirado: renova uma vez
                session = null
                s = ensureSession()
                res = http.fetch(searchRequest(s, clean, page))
            }
            if (res.code == 401 || res.code == 403 || res.code == 404) throw unavailable()
            if (!res.isSuccessful) throw SourceError("O site do Spani respondeu com erro (${res.code}).")
            return try {
                SpaniVipParser.parseSearch(res.body, id, s.imageBase)
            } catch (e: Exception) {
                throw unavailable(e)
            }
        } catch (e: IOException) {
            throw networkError(e)
        }
    }

    private fun searchRequest(s: Session, term: String, page: Int): Request {
        val termo = term.replace(Regex("""[\\/\s%?=]+"""), "+")
        val url = orgUrl(s.orgId).newBuilder()
            .addPathSegment("filial").addPathSegment(s.filialId.toString())
            .addPathSegment("centro_distribuicao").addPathSegment(s.cdId.toString())
            .addPathSegments("loja/buscas/produtos/termo")
            .addPathSegment(termo)
            .addQueryParameter("page", (page + 1).toString())
            .build()
        return authed(Request.Builder().url(url), s.orgId, s.domainKey, s.token).get().build()
    }

    private suspend fun ensureSession(): Session = lock.withLock {
        session?.let { return it }
        // 1. Organização pelo domínio (público)
        val domainRes = http.fetch(
            Request.Builder().url("$API/organizacoes/filiais/dominio/$DOMAIN").header("Accept", "application/json").build()
        )
        if (!domainRes.isSuccessful) throw unavailable()
        val domain = runCatching { SpaniVipParser.parseDomain(domainRes.body) }.getOrNull() ?: throw unavailable()
        val domainKey = domain.domainKey.ifBlank { DOMAIN }

        // 2. Token anônimo da loja
        val loginBody = """{"domain":"$domainKey","username":"$STORE_USER","key":"$STORE_PUBLIC_KEY"}"""
        val loginRes = http.fetch(
            authed(Request.Builder().url(orgUrl(domain.orgId).newBuilder().addPathSegments("auth/loja/login").build()), domain.orgId, domainKey, null)
                .post(loginBody.toRequestBody(JSON))
                .build()
        )
        if (!loginRes.isSuccessful) throw unavailable()
        val token = runCatching { SpaniVipParser.parseToken(loginRes.body) }.getOrNull() ?: throw unavailable()

        // 3. Filial e bucket de imagens
        val storeRes = http.fetch(
            authed(
                Request.Builder().url(orgUrl(domain.orgId).newBuilder().addPathSegments("loja/omnichannel").addPathSegment(domain.vipFilialId.toString()).build()),
                domain.orgId, domainKey, token
            ).get().build()
        )
        if (!storeRes.isSuccessful) throw unavailable()
        val store = runCatching { SpaniVipParser.parseStore(storeRes.body) }.getOrNull() ?: throw unavailable()

        // 4. Loja (CD) de Resende
        val cdsRes = http.fetch(
            authed(
                Request.Builder().url(
                    orgUrl(domain.orgId).newBuilder()
                        .addPathSegment("filial").addPathSegment(store.filialId.toString())
                        .addPathSegments("loja/centros_distribuicoes/retiradas")
                        .addQueryParameter("cep", RESENDE_POSTAL_CODE)
                        .build()
                ),
                domain.orgId, domainKey, token
            ).get().build()
        )
        val cds = if (cdsRes.isSuccessful) runCatching { SpaniVipParser.parseCds(cdsRes.body) }.getOrDefault(emptyList()) else emptyList()
        val cdId = SpaniVipParser.chooseCd(cds, store.defaultCdId)?.id ?: store.defaultCdId ?: throw unavailable()

        Session(domain.orgId, domainKey, token, store.filialId, cdId, store.productImageBase).also { session = it }
    }

    private fun orgUrl(orgId: Long): HttpUrl = "$API/org/$orgId".toHttpUrl()

    private fun authed(builder: Request.Builder, orgId: Long, domainKey: String, token: String?): Request.Builder =
        builder
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("DomainKey", domainKey)
            .header("OrganizationId", orgId.toString())
            .apply { if (token != null) header("Authorization", "Bearer $token") }

    private fun unavailable(cause: Throwable? = null) = SourceUnavailable(UNAVAILABLE_MESSAGE, cause)

    companion object {
        const val UNAVAILABLE_MESSAGE = "Busca online do Spani indisponível — use o encarte"
        const val API = "https://services-beta.vipcommerce.com.br/api-admin/v1"
        const val DOMAIN = "spanionline.com.br"
        const val RESENDE_POSTAL_CODE = "27511000"

        /** Usuário e chave públicos que o próprio site usa para visitantes anônimos (environment do bundle JS). */
        private const val STORE_USER = "loja"
        private const val STORE_PUBLIC_KEY = "df072f85df9bf7dd71b6811c34bdbaa4f219d98775b56cff9dfa5f8ca1bf8469"

        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
