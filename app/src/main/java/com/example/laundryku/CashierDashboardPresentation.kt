package com.example.laundryku

import com.example.laundryku.model.CashierDashboardTransaction
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

object CashierDashboardPresentation {
    private val indonesianLocale = Locale.forLanguageTag("id-ID")

    fun displayCode(code: String): String = if (code.startsWith('#')) code else "#$code"

    fun serviceDetail(transaction: CashierDashboardTransaction): String {
        val service = transaction.serviceName ?: "Layanan tidak tersedia"
        val serviceSummary = if (transaction.serviceCount > 1) {
            "$service + ${transaction.serviceCount - 1} layanan"
        } else {
            service
        }
        val quantity = if (transaction.qty != null && transaction.satuan != null) {
            val symbols = DecimalFormatSymbols(indonesianLocale)
            " • ${DecimalFormat("0.##", symbols).format(transaction.qty)} ${transaction.satuan}"
        } else {
            ""
        }
        return serviceSummary + quantity
    }

    fun currency(value: Double): String = NumberFormat
        .getCurrencyInstance(indonesianLocale)
        .apply {
            maximumFractionDigits = if (value % 1.0 == 0.0) 0 else 2
            minimumFractionDigits = 0
        }
        .format(value)
        .replace('\u00a0', ' ')

    fun statusLabel(status: String): String = LaundryStatusPresentation.label(status)

    fun paymentLabel(status: String): String = when (status) {
        "sudah_dibayar" -> "Sudah Dibayar"
        "belum_dibayar" -> "Belum Dibayar"
        else -> status
    }
}
