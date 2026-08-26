package com.example.laundryku.model

import com.google.gson.annotations.SerializedName

data class CustomerHistoryData(
    @SerializedName("id_transaksi") val transactionId: Int,
    @SerializedName("kode_transaksi") val transactionCode: String,
    @SerializedName("nama_layanan") val serviceName: String,
    val qty: Double,
    val satuan: String,
    @SerializedName("total_harga") val totalPrice: Double,
    @SerializedName("tanggal_masuk") val orderDate: String,
    @SerializedName("tanggal_selesai") val completionDate: String?,
    @SerializedName("status_laundry") val laundryStatus: String,
    @SerializedName("status_pembayaran") val paymentStatus: String
)
