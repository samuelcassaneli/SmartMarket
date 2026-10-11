package com.smartmarket.ui.screens.product

import java.util.Calendar
import java.util.TimeZone

/**
 * O DatePicker do Material 3 trabalha com meia-noite em UTC.
 * Estas funções convertem de/para instantes no fuso local.
 */
internal object PickerDates {
    private val UTC = TimeZone.getTimeZone("UTC")

    /** Instante local -> meia-noite UTC do mesmo dia do calendário. */
    fun toPicker(localMillis: Long): Long {
        val local = Calendar.getInstance().apply { timeInMillis = localMillis }
        return Calendar.getInstance(UTC).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }.timeInMillis
    }

    /**
     * Data escolhida (UTC) -> instante local. Se for hoje, usa a hora atual;
     * senão, meio-dia daquele dia.
     */
    fun fromPicker(utcMillis: Long, nowMillis: Long = System.currentTimeMillis()): Long {
        val utc = Calendar.getInstance(UTC).apply { timeInMillis = utcMillis }
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val y = utc.get(Calendar.YEAR); val m = utc.get(Calendar.MONTH); val d = utc.get(Calendar.DAY_OF_MONTH)
        if (now.get(Calendar.YEAR) == y && now.get(Calendar.MONTH) == m && now.get(Calendar.DAY_OF_MONTH) == d) return nowMillis
        return Calendar.getInstance().apply {
            clear()
            set(y, m, d, 12, 0, 0)
        }.timeInMillis
    }

    /** Fim do dia local (23:59:59) da data escolhida; usado para validade. */
    fun endOfDayFromPicker(utcMillis: Long): Long {
        val utc = Calendar.getInstance(UTC).apply { timeInMillis = utcMillis }
        return Calendar.getInstance().apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 23, 59, 59)
        }.timeInMillis
    }
}
