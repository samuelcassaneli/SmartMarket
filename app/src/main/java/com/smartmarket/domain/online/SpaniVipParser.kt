package com.smartmarket.domain.online

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Parser puro (JVM) da API VipCommerce usada pelo Spani Online (spanionline.com.br).
 * O esquema foi levantado no bundle JS do site; a leitura é tolerante (campos opcionais,
 * números como texto) para não quebrar com pequenas mudanças.
 */
object SpaniVipParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class DomainInfo(val vipFilialId: Long, val orgId: Long, val domainKey: String)
    data class StoreInfo(val filialId: Long, val productImageBase: String?, val defaultCdId: Long?)
    data class Cd(val id: Long, val name: String, val address: String)

    fun root(body: String): JsonObject = json.parseToJsonElement(body) as? JsonObject
        ?: throw IllegalArgumentException("Resposta não é um objeto JSON")

    /** `GET /organizacoes/filiais/dominio/{dominio}` */
    fun parseDomain(body: String): DomainInfo? {
        val data = root(body).obj("data") ?: return null
        val org = data.obj("organizacao") ?: return null
        val vipId = data.long("id") ?: return null
        val orgId = org.long("id") ?: return null
        return DomainInfo(vipId, orgId, org.str("enderecoServidor") ?: "")
    }

    /** `POST /org/{org}/auth/loja/login` → `data` é o JWT anônimo da loja. */
    fun parseToken(body: String): String? {
        val data = root(body)["data"] ?: return null
        return when (data) {
            is JsonPrimitive -> data.contentOrNull
            is JsonObject -> data.str("token") ?: data.str("access_token")
            else -> null
        }?.takeIf { it.isNotBlank() }
    }

    /** `GET /org/{org}/loja/omnichannel/{vipFilialId}` → filial da loja e bucket das imagens. */
    fun parseStore(body: String): StoreInfo? {
        val data = root(body).obj("data") ?: return null
        val filial = data.obj("filial") ?: return null
        val filialId = filial.long("id") ?: return null
        val buckets = data["localizacaoArquivos"] as? JsonArray
        val produto = buckets?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.str("model")?.equals("produto", ignoreCase = true) == true }
            ?.str("localizacao")
        val defaultCd = filial.long("centro_distribuicao_padrao_id")
            ?: data.long("centro_distribuicao_padrao_id")
        return StoreInfo(filialId, produto?.trimEnd('/'), defaultCd)
    }

    /** `GET /org/{org}/filial/{f}/loja/centros_distribuicoes/retiradas?cep=` */
    fun parseCds(body: String): List<Cd> {
        val data = root(body)["data"]
        val arr = when (data) {
            is JsonArray -> data
            is JsonObject -> (data["centros_distribuicao"] ?: data["centros_distribuicoes"] ?: data["cds"]) as? JsonArray
            else -> null
        } ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.long("id") ?: return@mapNotNull null
            val name = o.str("nome_site") ?: o.str("nome") ?: o.str("descricao") ?: ""
            val end = o.obj("endereco")
            val address = listOfNotNull(
                end?.str("logradouro"), end?.str("bairro"), end?.str("cidade"),
                o.str("cidade"), o.str("endereco_completo")
            ).joinToString(" ")
            Cd(id, name, address)
        }
    }

    /** Escolhe o CD (loja) de Resende; senão o padrão; senão o primeiro. */
    fun chooseCd(cds: List<Cd>, defaultCdId: Long?, cityHint: String = "resende"): Cd? =
        cds.firstOrNull { (it.name + " " + it.address).lowercase().contains(cityHint) }
            ?: cds.firstOrNull { it.id == defaultCdId }
            ?: cds.firstOrNull()

    /** `GET .../loja/buscas/produtos/termo/{termo}?page=N` → `data.produtos[]`. */
    fun parseSearch(body: String, sourceId: String, imageBase: String?): List<OnlineOffer> {
        val data = root(body)["data"]
        val produtos = when (data) {
            is JsonObject -> data["produtos"] as? JsonArray
            is JsonArray -> data
            else -> null
        } ?: return emptyList()
        return produtos.mapNotNull { el -> (el as? JsonObject)?.let { toOffer(it, sourceId, imageBase) } }
    }

    fun toOffer(p: JsonObject, sourceId: String, imageBase: String?): OnlineOffer? {
        val name = p.str("descricao")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val regular = p.num("preco") ?: p.num("preco_original") ?: p.num("preco_venda") ?: return null
        val oferta = p.obj("oferta")
        val onSale = p.bool("em_oferta") == true && oferta != null
        val salePrice = if (onSale) oferta?.num("preco_oferta") else null
        val price = salePrice?.takeIf { it > 0.0 } ?: regular
        if (price <= 0.0) return null
        val before = if (onSale) (oferta?.num("preco_antigo") ?: oferta?.num("preco_original") ?: regular) else null

        val id = p.long("produto_id") ?: p.long("id")
        val slug = p.str("link")
        val image = p.str("imagem")?.takeIf { it.isNotBlank() }?.let { img ->
            when {
                img.startsWith("http") -> img
                imageBase != null -> "$imageBase/${img.trimStart('/')}"
                else -> null
            }
        }
        val differentUnit = p.bool("possui_unidade_diferente") == true
        return OnlineOffer(
            sourceId = sourceId,
            name = name,
            brand = p.str("marca") ?: p.obj("marca")?.str("nome"),
            price = price,
            listPrice = before?.takeIf { it > price + 0.009 },
            unitMultiplier = if (differentUnit) p.num("quantidade_unidade_diferente") else null,
            measurementUnit = p.str("unidade_sigla"),
            imageUrl = image,
            productUrl = id?.let { pid ->
                if (slug.isNullOrBlank()) "$SITE/produtos/detalhe/$pid" else "$SITE/produtos/detalhe/$pid/$slug"
            },
            ean = p.str("codigo_barras")?.filter(Char::isDigit)?.takeIf { it.length >= 8 },
            available = p.bool("disponivel") ?: true
        )
    }

    const val SITE = "https://www.spanionline.com.br"

    // ------------------------------------------------------------ Helpers tolerantes

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.prim(key: String): JsonPrimitive? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }

    private fun JsonObject.str(key: String): String? = prim(key)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.long(key: String): Long? = prim(key)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

    private fun JsonObject.bool(key: String): Boolean? = prim(key)?.let {
        it.booleanOrNull ?: when (it.contentOrNull) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }

    private fun JsonObject.num(key: String): Double? = prim(key)?.let {
        it.doubleOrNull ?: it.contentOrNull?.replace(",", ".")?.toDoubleOrNull()
    }
}
