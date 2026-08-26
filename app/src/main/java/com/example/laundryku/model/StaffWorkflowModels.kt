package com.example.laundryku.model

import com.google.gson.annotations.SerializedName

data class StaffJobsResponse(
    val success: Boolean,
    val message: String,
    val data: List<StaffJobData>?
)

data class StaffJobData(
    @SerializedName("id_transaksi") val transactionId: Int,
    @SerializedName("kode_transaksi") val transactionCode: String,
    @SerializedName("nama_pelanggan") val customerName: String,
    @SerializedName("nama_layanan") val serviceName: String,
    val qty: Double,
    val satuan: String,
    @SerializedName("estimasi_selesai") val estimatedCompletion: String?,
    @SerializedName("status_laundry") val laundryStatus: String,
    @SerializedName("perlu_setrika") val requiresIron: Boolean = false,
    @SerializedName("next_status") val nextStatus: String? = null,
    val details: List<StaffJobDetail> = emptyList()
)

data class StaffJobDetail(
    @SerializedName("nama_layanan") val serviceName: String,
    val qty: Double,
    val satuan: String,
    @SerializedName("perlu_setrika") val requiresIron: Boolean = false
)

data class UpdateLaundryStatusRequest(
    @SerializedName("id_user") val staffId: Int,
    @SerializedName("id_transaksi") val transactionId: Int,
    @SerializedName("current_status") val currentStatus: String
)

data class UpdateLaundryStatusResponse(
    val success: Boolean,
    val message: String,
    val data: UpdatedLaundryStatusData?
)

data class UpdatedLaundryStatusData(
    @SerializedName("id_transaksi") val transactionId: Int,
    @SerializedName("status_laundry") val laundryStatus: String
)

data class StaffHistoryResponse(
    val success: Boolean,
    val message: String,
    val data: List<StaffHistoryData>?
)

data class StaffHistoryData(
    @SerializedName("id_transaksi") val transactionId: Int,
    @SerializedName("kode_transaksi") val transactionCode: String,
    @SerializedName("nama_pelanggan") val customerName: String,
    @SerializedName("nama_layanan") val serviceName: String,
    val qty: Double,
    val satuan: String,
    @SerializedName("status_laundry") val laundryStatus: String,
    @SerializedName("terakhir_diperbarui") val lastUpdated: String?,
    val details: List<StaffJobDetail> = emptyList()
)

data class StaffDashboardResponse(
    val success: Boolean,
    val message: String,
    val data: StaffDashboardData?
)

data class StaffDashboardData(
    val summary: StaffDashboardSummary,
    @SerializedName("next_job") val nextJob: StaffJobData?
)

data class StaffDashboardSummary(
    val menunggu: Int,
    val dicuci: Int,
    val dikeringkan: Int,
    val disetrika: Int,
    val dipacking: Int,
    @SerializedName("siap_diambil") val ready: Int
)
