package com.example.laundryku

import java.text.NumberFormat
import java.util.Locale

object PaymentPresentation {
    fun canPay(transactionPaymentStatus: String, laundryStatus: String): Boolean =
        transactionPaymentStatus == "belum_dibayar" && laundryStatus != "dibatalkan"

    fun isCashWaiting(method: String?, paymentStatus: String?): Boolean =
        method == "cash" && paymentStatus == "menunggu"

    fun isQrisWaiting(method: String?, paymentStatus: String?): Boolean =
        method == "qris" && paymentStatus == "menunggu"

    fun paymentMethodLabel(method: String?, channel: String? = null): String? = when (method) {
        "cash" -> "Cash"
        "qris" -> "QRIS"
        "e_wallet" -> "E-Wallet${channelLabel(channel)?.let { " \u2022 $it" }.orEmpty()}"
        else -> null
    }

    fun channelLabel(channel: String?): String? = when (channel?.lowercase(Locale.ROOT)) {
        "gopay" -> "GoPay"
        "dana" -> "DANA"
        "ovo" -> "OVO"
        "shopeepay" -> "ShopeePay"
        else -> null
    }

    fun qrisState(transactionPaymentStatus: String, paymentStatus: String?): QrisUiState = when {
        transactionPaymentStatus == "sudah_dibayar" && paymentStatus == "berhasil" -> QrisUiState.SUCCESS
        paymentStatus == "gagal" -> QrisUiState.FAILURE
        else -> QrisUiState.PENDING
    }

    fun shouldPollQris(pageActive: Boolean, terminal: Boolean, pollCount: Int, maxPolls: Int): Boolean =
        pageActive && !terminal && pollCount < maxPolls

    fun pollCountForManualQrisCheck(pollCount: Int, maxPolls: Int): Int =
        if (pollCount >= maxPolls) 0 else pollCount

    fun formatRupiah(value: Double): String = NumberFormat.getCurrencyInstance(
        Locale.forLanguageTag("id-ID")
    ).apply {
        maximumFractionDigits = if (value % 1.0 == 0.0) 0 else 2
        minimumFractionDigits = 0
    }.format(value).replace('\u00a0', ' ')
}

enum class QrisUiState { PENDING, SUCCESS, FAILURE }
