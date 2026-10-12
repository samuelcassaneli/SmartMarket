package com.smartmarket.domain.images

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache de "qual é a foto deste produto", pela especificação ([ImageMatch.key]): o mesmo produto não é
 * procurado de novo. Quando não acha, lembra disso por [MISS_TTL] para não repetir a busca toda hora.
 * A imagem em si fica no cache de disco do Coil.
 */
class ImageCache(context: Context) {

    @Serializable
    data class Entry(val url: String? = null, val at: Long = 0)

    private val file = File(context.filesDir, "image_cache.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val map = ConcurrentHashMap<String, Entry>()
    @Volatile private var loaded = false

    private val _version = MutableStateFlow(0)
    /** Muda quando o cache ganha fotos novas (para as telas atualizarem). */
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                if (file.exists()) map.putAll(json.decodeFromString(MAP_SERIALIZER, file.readText()))
            }
            loaded = true
        }
    }

    /** Foto conhecida para o texto (ou null). */
    fun peek(text: String): String? {
        ensureLoaded()
        return map[ImageMatch.key(text)]?.url
    }

    /** true = já tem resposta válida (foto, ou "não achou" recente). */
    fun isKnown(text: String, now: Long = System.currentTimeMillis()): Boolean {
        ensureLoaded()
        val e = map[ImageMatch.key(text)] ?: return false
        return e.url != null || now - e.at < MISS_TTL
    }

    fun put(text: String, url: String?) {
        ensureLoaded()
        map[ImageMatch.key(text)] = Entry(url, System.currentTimeMillis())
        if (url != null) _version.value++
    }

    fun save() {
        ensureLoaded()
        runCatching { file.writeText(json.encodeToString(MAP_SERIALIZER, HashMap(map))) }
    }

    fun clear() {
        map.clear()
        runCatching { file.delete() }
        _version.value++
    }

    val size: Int get() { ensureLoaded(); return map.values.count { it.url != null } }

    companion object {
        const val MISS_TTL = 7L * 24 * 60 * 60 * 1000
        private val MAP_SERIALIZER = MapSerializer(String.serializer(), Entry.serializer())
    }
}
