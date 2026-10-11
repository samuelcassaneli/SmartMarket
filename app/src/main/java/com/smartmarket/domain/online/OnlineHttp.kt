package com.smartmarket.domain.online

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Resposta HTTP já lida (corpo como texto). */
internal data class HttpResult(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299 // inclui o 206 da VTEX
}

/** Cliente derivado com tempo total limitado: busca online não pode travar a tela. */
internal fun OkHttpClient.forOnlineSearch(): OkHttpClient = newBuilder()
    .callTimeout(20, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

/** Executa a chamada de forma cancelável e devolve código e corpo. */
internal suspend fun OkHttpClient.fetch(request: Request): HttpResult =
    suspendCancellableCoroutine { cont ->
        val call = newCall(request)
        cont.invokeOnCancellation { runCatching { call.cancel() } }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use { HttpResult(it.code, it.body?.string().orEmpty()) }
                } catch (e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                    return
                }
                if (cont.isActive) cont.resume(result)
            }
        })
    }

internal fun networkError(e: IOException): SourceError =
    SourceError("Não foi possível falar com o site. Verifique a internet.", e)
