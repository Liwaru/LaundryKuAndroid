package com.example.laundryku.model

import com.google.gson.annotations.SerializedName

data class CashierDashboardResponse(
    val success: Boolean,
    val message: String,
    val data: CashierDashboardData?
)

data class CashierDashboardData(
    val summary: CashierDashboardSummary,
    @SerializedName("recent_transactions") val recentTransactions: List<CashierDashboardTransaction>,
    @SerializedName("ready_transactions") val readyTransactions: List<CashierDashboardTransaction>
)

data class CashierDashboardSummary(
    @SerializedName("pesanan_aktif") val activeOrders: Int,
    @SerializedName("belum_dibayar") val unpaid: Int,
    @SerializedName("siap_diambil") val ready: Int,
    @SerializedName("transaksi_hari_ini") val transactionsToday: Int,
    @SerializedName("pendapatan_hari_ini") val incomeToday: Double
)

data class CashierDashboardTransaction(
    @SerializedName("id_transaksi") val transactionId: Int,
    @SerializedName("kode_transaksi") val transactionCode: String,
    @SerializedName("nama_pelanggan") val customerName: String,
    @SerializedName("nama_layanan") val serviceName: String?,
    val qty: Double?,
    val satuan: String?,
    @SerializedName("jumlah_layanan") val serviceCount: Int,
    @SerializedName("total_harga") val totalPrice: Double,
    @SerializedName("status_laundry") val laundryStatus: String,
    @SerializedName("status_pembayaran") val paymentStatus: String,
    @SerializedName("metode_pembayaran") val paymentMethod: String?,
    @SerializedName("tanggal_masuk") val orderDate: String
)
