package com.example.laundryku.model

import com.google.gson.annotations.SerializedName

data class SelectCashPaymentRequest(
    @SerializedName("id_transaksi") val transactionId: Int
)

data class CashPaymentResponse(
    val success: Boolean,
    val message: String,
    val data: CashPaymentData?
)

data class CashPaymentData(
    @SerializedName("id_pembayaran") val paymentId: Int,
    val metode: String,
    @SerializedName("payment_channel") val paymentChannel: String?,
    val jumlah: Double,
    val status: String,
    @SerializedName("status_pembayaran") val transactionPaymentStatus: String
)
