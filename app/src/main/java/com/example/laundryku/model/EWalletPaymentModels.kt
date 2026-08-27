package com.example.laundryku.model

import com.google.gson.annotations.SerializedName

data class SimulateEWalletPaymentRequest(
    @SerializedName("id_transaksi") val transactionId: Int,
    @SerializedName("payment_channel") val paymentChannel: String
)

data class SimulateEWalletPaymentResponse(
    val success: Boolean,
    val message: String,
    val data: SimulatedEWalletPaymentData?
)

data class SimulatedEWalletPaymentData(
    @SerializedName("id_transaksi") val transactionId: Int,
    @SerializedName("kode_transaksi") val transactionCode: String,
    val metode: String,
    @SerializedName("payment_channel") val paymentChannel: String,
    val jumlah: Double,
    val status: String,
    @SerializedName("status_pembayaran") val transactionPaymentStatus: String
)
