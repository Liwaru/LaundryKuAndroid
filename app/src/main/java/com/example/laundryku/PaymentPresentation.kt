package com.example.laundryku

object PaymentPresentation {
    fun canPay(transactionPaymentStatus: String, laundryStatus: String): Boolean =
        transactionPaymentStatus == "belum_dibayar" && laundryStatus != "dibatalkan"

    fun isCashWaiting(method: String?, paymentStatus: String?): Boolean =
        method == "cash" && paymentStatus == "menunggu"
}
