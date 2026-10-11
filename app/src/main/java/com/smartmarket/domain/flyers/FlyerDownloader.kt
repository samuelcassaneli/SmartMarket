package com.smartmarket.domain.flyers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Baixa páginas e arquivos de encarte com o OkHttp do app. Os arquivos ficam em
 * `cacheDir/flyers/` e são apagados depois de processados.
 */
class FlyerDownloader(private val client: OkHttpClient, cacheDir: File) {

    val dir: File = File(cacheDir, "flyers").apply { mkdirs() }

    /** HTML da página da loja. */
    suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("O site respondeu ${resp.code}")
            resp.body?.string() ?: throw IOException("Página vazia")
        }
    }

    /** Baixa [url] para um arquivo no cache. */
    suspend fun download(url: String, name: String): File = withContext(Dispatchers.IO) {
        val target = File(dir, safeName(name))
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Falha ao baixar o encarte (${resp.code})")
            val body = resp.body ?: throw IOException("Arquivo vazio")
            target.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
        }
        if (target.length() == 0L) throw IOException("Arquivo vazio")
        target
    }

    fun clear(files: Collection<File>) {
        files.forEach { runCatching { it.delete() } }
    }

    companion object {
        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        fun sha256(text: String): String = sha256(text.toByteArray(Charsets.UTF_8))

        /** Hash do conteúdo de um arquivo, lido em blocos. */
        fun sha256(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        /** Hash de um encarte em imagens: a lista ordenada de URLs (os nomes já são UUIDs únicos). */
        fun hashOfUrls(urls: List<String>): String = sha256(urls.joinToString("\n"))

        private fun safeName(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
    }
}
