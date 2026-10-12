package com.smartmarket.domain.ai

import android.content.Context
import com.smartmarket.data.prefs.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Dicas da IA guardadas (uma geração por dia, ou quando os dados mudam). */
@Serializable
data class Insights(val tips: List<String> = emptyList(), val generatedAt: Long = 0, val dataHash: Int = 0)

data class InsightsUi(val insights: Insights? = null, val loading: Boolean = false, val error: String? = null)

/**
 * Insights da IA na tela inicial: o app monta um resumo dos dados (gastos, orçamento, categorias,
 * mercados, promoções, despensa) e o Gemini do usuário devolve dicas curtas. Fica guardado no celular.
 */
class AiInsights(
    context: Context,
    private val settings: SettingsRepository,
    private val gemini: GeminiClient,
    private val ai: AiModelResolver
) {
    private val prefs = context.getSharedPreferences("ai_insights", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(InsightsUi(load()))
    val state: StateFlow<InsightsUi> = _state.asStateFlow()

    private fun load(): Insights? = runCatching {
        prefs.getString(KEY, null)?.let { json.decodeFromString(Insights.serializer(), it) }
    }.getOrNull()

    /** Gera se ligado e se as dicas guardadas são de outro dia ou de outros dados (ou se [force]). */
    suspend fun refresh(summary: String, force: Boolean = false) {
        if (!settings.current().insightsReady || summary.isBlank()) return
        val current = _state.value
        if (current.loading) return
        val hash = summary.hashCode()
        val saved = current.insights
        val fresh = saved != null && System.currentTimeMillis() - saved.generatedAt < DAY && saved.dataHash == hash
        if (fresh && !force) return
        _state.value = current.copy(loading = true, error = null)
        try {
            val ready = ai.resolve() ?: throw IllegalStateException("Chave da IA inválida")
            val tips = gemini.insights(ready.apiKey, ready.model, summary)
            val ins = Insights(tips, System.currentTimeMillis(), hash)
            prefs.edit().putString(KEY, json.encodeToString(Insights.serializer(), ins)).apply()
            _state.value = InsightsUi(ins)
        } catch (e: CancellationException) {
            _state.value = current.copy(loading = false)
            throw e
        } catch (e: Throwable) {
            _state.value = current.copy(loading = false, error = e.message ?: "Falha ao gerar as dicas")
        }
    }

    companion object {
        private const val KEY = "insights"
        private const val DAY = 24L * 60 * 60 * 1000
    }
}
