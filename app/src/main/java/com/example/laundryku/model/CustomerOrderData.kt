package com.example.laundryku.model

import com.google.gson.annotations.SerializedName

data class CustomerOrderData(
    @SerializedName("id_transaksi") val transactionId: Int,
    @SerializedName("kode_transaksi") val transactionCode: String,
    @SerializedName("id_pelanggan") val customerId: Int,
    @SerializedName("nama_layanan") val serviceName: String,
    val qty: Double,
    val satuan: String,
    @SerializedName("harga_satuan") val unitPrice: Double,
    val subtotal: Double,
    @SerializedName("total_harga") val totalPrice: Double,
    @SerializedName("tanggal_masuk") val orderDate: String,
    @SerializedName("estimasi_selesai") val estimatedCompletion: String?,
    @SerializedName("status_laundry") val laundryStatus: String,
    @SerializedName("status_pembayaran") val paymentStatus: String
)
