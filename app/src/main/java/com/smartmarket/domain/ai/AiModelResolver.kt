package com.smartmarket.domain.ai

import com.smartmarket.data.prefs.SettingsRepository

/** Chave e modelo do Gemini prontos para uso (descobre o modelo na primeira vez). */
class AiModelResolver(private val settings: SettingsRepository, private val gemini: GeminiClient) {

    data class Ready(val apiKey: String, val model: String)

    suspend fun resolve(): Ready? {
        val s = settings.current()
        if (!s.aiReady) return null
        val model = s.geminiModel.ifBlank {
            runCatching { gemini.discoverModel(s.geminiApiKey, s.aiFastMode) }
                .onSuccess { settings.setGemini(s.geminiApiKey, it) }
                .getOrNull().orEmpty()
        }
        return model.takeIf { it.isNotBlank() }?.let { Ready(s.geminiApiKey, it) }
    }
}
