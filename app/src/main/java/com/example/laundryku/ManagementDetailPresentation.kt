package com.example.laundryku

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

object ManagementDetailPresentation {
    private val locale = Locale.forLanguageTag("id-ID")

    fun displayCode(code: String): String = if (code.startsWith('#')) code else "#$code"

    fun date(value: String?): String {
        if (value.isNullOrBlank()) return "Belum tersedia"
        return runCatching {
            val parsed = requireNotNull(
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { isLenient = false }.parse(value)
            )
            SimpleDateFormat("d MMMM yyyy", locale).format(parsed)
        }.getOrDefault(value)
    }

    fun currency(value: Double): String = NumberFormat.getCurrencyInstance(locale).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }.format(value).replace('\u00a0', ' ')
}
