package com.example.laundryku

import com.example.laundryku.model.OwnerTransactionReportItem
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Locale

enum class OwnerReportsPeriod(val apiValue: String) {
    TODAY("today"),
    WEEK("week"),
    MONTH("month")
}

object OwnerReportsPresentation {
    private val indonesianLocale = Locale.forLanguageTag("id-ID")

    fun currency(value: Double): String = OwnerDashboardPresentation.currency(value)

    fun displayCode(code: String): String = if (code.startsWith('#')) code else "#$code"

    fun paymentMethod(method: String, channel: String?): String {
        val methodLabel = when (method) {
            "cash" -> "Cash"
            "qris" -> "QRIS"
            "e_wallet" -> "E-Wallet"
            "paylater" -> "PayLater"
            else -> method
        }
        val channelLabel = when (channel) {
            null, "" -> null
            "gopay" -> "GoPay"
            "dana" -> "DANA"
            "ovo" -> "OVO"
            "shopeepay" -> "ShopeePay"
            else -> channel
        }
        return if (channelLabel == null) methodLabel else "$methodLabel \u2022 $channelLabel"
    }

    fun serviceDetail(transaction: OwnerTransactionReportItem): String {
        val service = transaction.serviceName ?: "Layanan tidak tersedia"
        val services = if (transaction.serviceCount > 1) {
            "$service + ${transaction.serviceCount - 1} layanan"
        } else {
            service
        }
        val quantity = if (transaction.qty != null && transaction.satuan != null) {
            val formatted = BigDecimal.valueOf(transaction.qty).stripTrailingZeros().toPlainString()
                .replace('.', ',')
            " \u2022 $formatted ${transaction.satuan}"
        } else {
            ""
        }
        return services + quantity
    }

    fun laundryStatus(status: String): String = CashierDashboardPresentation.statusLabel(status)

    fun paymentStatus(status: String): String = CashierDashboardPresentation.paymentLabel(status)

    fun dateTime(value: String): String = formatDate(value, "d MMMM yyyy, HH:mm")

    fun date(value: String): String = formatDate(value, "d MMMM yyyy")

    fun activeTransactions(transactions: List<OwnerTransactionReportItem>): Int =
        transactions.count { it.laundryStatus in ACTIVE_STATUSES }

    private fun formatDate(value: String, outputPattern: String): String = runCatching {
        val source = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { isLenient = false }
        val parsed = requireNotNull(source.parse(value))
        SimpleDateFormat(outputPattern, indonesianLocale).format(parsed)
    }.getOrDefault(value)

    private val ACTIVE_STATUSES = setOf(
        "menunggu",
        "dicuci",
        "dikeringkan",
        "disetrika",
        "dipacking",
        "siap_diambil"
    )
}
