package com.smartmarket.ui.screens.search

import android.content.Context

/** Buscas recentes (até [MAX]), em SharedPreferences próprias da tela de busca. */
class SearchHistoryStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): List<String> =
        prefs.getString(KEY, null)?.split(SEP)?.filter { it.isNotBlank() }.orEmpty()

    fun add(term: String): List<String> = save(merge(load(), term))

    fun remove(term: String): List<String> = save(load().filterNot { it.equals(term, ignoreCase = true) })

    fun clear(): List<String> = save(emptyList())

    private fun save(list: List<String>): List<String> {
        prefs.edit().putString(KEY, list.joinToString(SEP)).apply()
        return list
    }

    companion object {
        const val MAX = 8
        private const val PREFS = "search_history"
        private const val KEY = "recent"
        private const val SEP = "\u001F"

        /** Termo mais recente primeiro, sem repetição (ignora maiúsculas) e limitado a [max]. */
        fun merge(current: List<String>, term: String, max: Int = MAX): List<String> {
            val clean = term.trim().replace(SEP, " ")
            if (clean.isEmpty()) return current.take(max)
            return (listOf(clean) + current.filterNot { it.equals(clean, ignoreCase = true) }).take(max)
        }
    }
}
