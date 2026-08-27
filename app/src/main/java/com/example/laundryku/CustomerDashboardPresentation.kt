package com.example.laundryku

import com.example.laundryku.model.CustomerDashboardOrder
import java.text.SimpleDateFormat
import java.util.Locale

object CustomerDashboardPresentation {
    private val indonesianLocale = Locale.forLanguageTag("id-ID")

    fun displayCode(code: String): String = if (code.startsWith('#')) code else "#$code"

    fun serviceSummary(order: CustomerDashboardOrder): String {
        val first = order.serviceName?.takeIf { it.isNotBlank() } ?: "Layanan tidak tersedia"
        return if (order.serviceCount > 1) "$first + ${order.serviceCount - 1} layanan" else first
    }

    fun statusLabel(status: String): String = CustomerOrderDetailPresentation.statusLabel(status)

    fun date(value: String?): String {
        if (value.isNullOrBlank()) return "Belum tersedia"
        return runCatching {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { isLenient = false }
            val parsed = requireNotNull(parser.parse(value))
            SimpleDateFormat("d MMMM yyyy", indonesianLocale).format(parsed)
        }.getOrDefault(value)
    }
}
