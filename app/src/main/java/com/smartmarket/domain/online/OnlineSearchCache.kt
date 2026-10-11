package com.smartmarket.domain.online

import com.smartmarket.domain.matching.FuzzyMatcher

/**
 * Cache em memória das buscas online por (fonte, termo, página), com validade de 6h.
 * Evita repetir requisições aos sites dos mercados.
 */
class OnlineSearchCache(
    private val ttlMillis: Long = DEFAULT_TTL,
    private val maxEntries: Int = 120,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private data class Entry(val offers: List<OnlineOffer>, val storedAt: Long)

    private val entries = object : LinkedHashMap<String, Entry>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > maxEntries
    }

    fun key(sourceId: String, term: String, page: Int): String =
        "$sourceId|${FuzzyMatcher.normalize(term)}|$page"

    @Synchronized
    fun get(sourceId: String, term: String, page: Int): List<OnlineOffer>? {
        val k = key(sourceId, term, page)
        val e = entries[k] ?: return null
        if (clock() - e.storedAt > ttlMillis) {
            entries.remove(k)
            return null
        }
        return e.offers
    }

    @Synchronized
    fun put(sourceId: String, term: String, page: Int, offers: List<OnlineOffer>) {
        entries[key(sourceId, term, page)] = Entry(offers, clock())
    }

    @Synchronized
    fun invalidate(sourceId: String) {
        entries.keys.removeAll { it.startsWith("$sourceId|") }
    }

    @Synchronized
    fun clear() = entries.clear()

    companion object {
        const val DEFAULT_TTL = 6L * 60 * 60 * 1000
    }
}
