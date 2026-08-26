package com.example.laundryku.model

import com.google.gson.annotations.SerializedName

data class OwnerDashboardResponse(
    val success: Boolean,
    val message: String,
    val data: OwnerDashboardData?
)

data class OwnerDashboardData(
    val summary: OwnerDashboardSummary,
    @SerializedName("popular_services") val popularServices: List<OwnerPopularService>,
    @SerializedName("operational_status") val operationalStatus: OwnerOperationalStatus
)

data class OwnerDashboardSummary(
    @SerializedName("pendapatan_hari_ini") val incomeToday: Double,
    @SerializedName("transaksi_hari_ini") val transactionsToday: Int,
    @SerializedName("pesanan_aktif") val activeOrders: Int,
    @SerializedName("total_pelanggan") val totalCustomers: Int
)

data class OwnerPopularService(
    @SerializedName("id_layanan") val serviceId: Int,
    @SerializedName("nama_layanan") val serviceName: String,
    @SerializedName("jumlah_pesanan") val orderCount: Int
)

data class OwnerOperationalStatus(
    val menunggu: Int,
    val dicuci: Int,
    val dikeringkan: Int,
    val disetrika: Int,
    val dipacking: Int,
    @SerializedName("siap_diambil") val readyForPickup: Int
)
