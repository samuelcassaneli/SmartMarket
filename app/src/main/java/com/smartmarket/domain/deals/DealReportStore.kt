package com.smartmarket.domain.deals

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/** Guarda o último relatório de promoções (para a tela inicial, o pop-up e a tela Promoções). */
class DealReportStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("deal_report", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _report = MutableStateFlow(load())
    val report: StateFlow<DealReport?> = _report.asStateFlow()

    private fun load(): DealReport? = prefs.getString(KEY, null)?.let {
        runCatching { json.decodeFromString(DealReport.serializer(), it) }.getOrNull()
    }

    fun save(report: DealReport) {
        prefs.edit().putString(KEY, json.encodeToString(DealReport.serializer(), report)).apply()
        _report.value = report
    }

    fun markSeen() {
        val r = _report.value ?: return
        if (!r.seen) save(r.copy(seen = true))
    }

    companion object {
        private const val KEY = "last"
    }
}
