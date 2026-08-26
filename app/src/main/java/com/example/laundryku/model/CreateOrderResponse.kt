package com.example.laundryku.model

import com.google.gson.annotations.SerializedName

data class CreateOrderResponse(
    val success: Boolean,
    val message: String,
    val data: CreatedOrderData?
)

data class CreatedOrderData(
    @SerializedName("id_transaksi") val transactionId: Int,
    @SerializedName("kode_transaksi") val transactionCode: String,
    @SerializedName("total_harga") val totalPrice: Double,
    @SerializedName("status_laundry") val laundryStatus: String,
    @SerializedName("status_pembayaran") val paymentStatus: String,
    @SerializedName("estimasi_selesai") val estimatedCompletion: String
)
