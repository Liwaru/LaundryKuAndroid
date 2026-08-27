package com.example.laundryku.model

import com.google.gson.annotations.SerializedName

data class CustomerDashboardResponse(
    val success: Boolean,
    val message: String,
    val data: CustomerDashboardData?
)

data class CustomerDashboardData(
    @SerializedName("active_order") val activeOrder: CustomerDashboardOrder?,
    @SerializedName("recent_orders") val recentOrders: List<CustomerDashboardOrder>
)

data class CustomerDashboardOrder(
    @SerializedName("id_transaksi") val transactionId: Int,
    @SerializedName("kode_transaksi") val transactionCode: String,
    @SerializedName("nama_layanan") val serviceName: String?,
    @SerializedName("jumlah_layanan") val serviceCount: Int,
    @SerializedName("total_harga") val totalPrice: Double,
    @SerializedName("tanggal_masuk") val orderDate: String,
    @SerializedName("estimasi_selesai") val estimatedCompletion: String?,
    @SerializedName("tanggal_selesai") val completionDate: String?,
    @SerializedName("status_laundry") val laundryStatus: String,
    @SerializedName("status_pembayaran") val paymentStatus: String
)
