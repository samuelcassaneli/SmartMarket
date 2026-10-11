package com.smartmarket.ui.components

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val PT_BR = Locale("pt", "BR")

/** Formatação padrão pt-BR usada em todo o app. */
object Fmt {
    private val currency = NumberFormat.getCurrencyInstance(PT_BR)
    private val number = NumberFormat.getNumberInstance(PT_BR).apply { maximumFractionDigits = 3 }

    /** R$ 1.234,56 */
    fun money(value: Double): String = currency.format(value).replace(' ', ' ')

    /** 1.234,56 (sem símbolo) */
    fun decimal(value: Double, digits: Int = 2): String =
        NumberFormat.getNumberInstance(PT_BR).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }.format(value)

    /** Quantidade sem casas desnecessárias: 2, 1,5, 0,375 */
    fun quantity(value: Double): String = number.format(value)

    /** +12,3% / -4,0% */
    fun percent(value: Double, withSign: Boolean = true): String {
        val sign = when {
            !withSign -> ""
            value > 0.0001 -> "+"
            value < -0.0001 -> "-"
            else -> ""
        }
        return sign + decimal(abs(value), 1) + "%"
    }

    fun date(millis: Long): String = SimpleDateFormat("dd/MM/yyyy", PT_BR).format(Date(millis))
    fun dateShort(millis: Long): String = SimpleDateFormat("dd/MM", PT_BR).format(Date(millis))
    fun dateTime(millis: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", PT_BR).format(Date(millis))

    private val MONTHS = listOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    )

    /** month 1..12 */
    fun monthName(month: Int): String = MONTHS[(month - 1).coerceIn(0, 11)]
    fun monthShort(month: Int): String = monthName(month).take(3)
    fun monthYear(month: Int, year: Int): String = "${monthName(month)} $year"

    /** "AAAA-MM" -> "Out" */
    fun yearMonthShort(yearMonth: String): String =
        yearMonth.substringAfter('-').toIntOrNull()?.let { monthShort(it) } ?: yearMonth

    /** Aceita "5,99", "5.99", "R$ 1.234,56". Retorna null se inválido. */
    fun parseMoney(text: String): Double? {
        val clean = text.replace("R$", "").replace(" ", "").trim()
        if (clean.isEmpty()) return null
        val normalized = if (clean.contains(',')) clean.replace(".", "").replace(',', '.') else clean
        return normalized.toDoubleOrNull()
    }
}

/** Mês/ano atual (mês 1..12). */
data class YearMonth(val month: Int, val year: Int) {
    fun previous(): YearMonth = if (month == 1) YearMonth(12, year - 1) else YearMonth(month - 1, year)
    fun next(): YearMonth = if (month == 12) YearMonth(1, year + 1) else YearMonth(month + 1, year)

    /** Intervalo [início, fim) em millis. */
    fun range(): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            clear(); set(year, month - 1, 1, 0, 0, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            clear(); set(next().year, next().month - 1, 1, 0, 0, 0)
        }.timeInMillis
        return start to end
    }

    override fun toString(): String = Fmt.monthYear(month, year)

    companion object {
        fun now(): YearMonth {
            val c = Calendar.getInstance()
            return YearMonth(c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR))
        }
    }
}
