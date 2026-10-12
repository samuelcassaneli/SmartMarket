package com.smartmarket.domain.images

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Open Food Facts: base aberta e gratuita de produtos (com fotos), boa para itens com código de barras. */
class OpenFoodFacts(private val client: OkHttpClient) {

    suspend fun byBarcode(ean: String): List<ImageCandidate> = get(
        "$BASE/api/v2/product/${ean.filter(Char::isDigit)}.json".toHttpUrl().newBuilder()
            .addQueryParameter("fields", FIELDS).build().toString()
    )?.let { body -> parseProduct(body) }.orEmpty()

    suspend fun search(term: String): List<ImageCandidate> = get(
        "$BASE/cgi/search.pl".toHttpUrl().newBuilder()
            .addQueryParameter("search_terms", term)
            .addQueryParameter("search_simple", "1")
            .addQueryParameter("action", "process")
            .addQueryParameter("json", "1")
            .addQueryParameter("page_size", "12")
            .addQueryParameter("fields", FIELDS)
            .build().toString()
    )?.let { parseSearch(it) }.orEmpty()

    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).header("User-Agent", "SmartMarket-Android/2.0 (uso pessoal)").build())
                .execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull()
    }

    companion object {
        private const val BASE = "https://world.openfoodfacts.org"
        private const val FIELDS = "product_name,product_name_pt,brands,quantity,image_front_url,image_url"
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parseSearch(body: String): List<ImageCandidate> {
            val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return emptyList()
            return (root["products"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::toCandidate) }
        }

        fun parseProduct(body: String): List<ImageCandidate> {
            val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return emptyList()
            return listOfNotNull((root["product"] as? JsonObject)?.let(::toCandidate))
        }

        private fun toCandidate(o: JsonObject): ImageCandidate? {
            fun str(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            val url = str("image_front_url") ?: str("image_url") ?: return null
            val name = str("product_name_pt") ?: str("product_name") ?: return null
            val full = listOfNotNull(name, str("brands")?.split(',')?.firstOrNull()?.trim(), str("quantity")).joinToString(" ")
            return ImageCandidate(full, url, "Open Food Facts")
        }
    }
}
