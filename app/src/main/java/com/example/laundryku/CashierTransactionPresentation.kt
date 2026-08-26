package com.example.laundryku

import com.example.laundryku.model.CashierTransactionData
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

enum class CashierTransactionFilter {
    ALL,
    UNPAID,
    PAID,
    READY,
    COMPLETED
}

object CashierTransactionPresentation {
    fun filter(
        transactions: List<CashierTransactionData>,
        filter: CashierTransactionFilter,
        searchQuery: String
    ): List<CashierTransactionData> {
        val query = searchQuery.trim()
        return transactions.filter { transaction ->
            val matchesFilter = when (filter) {
                CashierTransactionFilter.ALL -> true
                CashierTransactionFilter.UNPAID -> transaction.paymentStatus == "belum_dibayar"
                CashierTransactionFilter.PAID -> transaction.paymentStatus == "sudah_dibayar"
                CashierTransactionFilter.READY -> transaction.laundryStatus == "siap_diambil"
                CashierTransactionFilter.COMPLETED -> transaction.laundryStatus == "selesai"
            }
            val matchesSearch = query.isBlank() ||
                transaction.transactionCode.contains(query, ignoreCase = true) ||
                transaction.customerName.contains(query, ignoreCase = true) ||
                transaction.phone.contains(query, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    fun canConfirmCash(transaction: CashierTransactionData): Boolean =
        transaction.paymentStatus == "belum_dibayar" &&
            transaction.laundryStatus != "dibatalkan" &&
            transaction.paymentMethod == "cash" &&
            transaction.paymentRecordStatus == "menunggu"

    fun canComplete(transaction: CashierTransactionData): Boolean =
        transaction.laundryStatus == "siap_diambil" &&
            transaction.paymentStatus == "sudah_dibayar"

    fun isWaitingForPaymentBeforeCompletion(transaction: CashierTransactionData): Boolean =
        transaction.laundryStatus == "siap_diambil" &&
            transaction.paymentStatus == "belum_dibayar"

    fun quantity(qty: Double, unit: String): String {
        val symbols = DecimalFormatSymbols(Locale.forLanguageTag("id-ID"))
        return "${DecimalFormat("0.##", symbols).format(qty)} $unit"
    }
}
