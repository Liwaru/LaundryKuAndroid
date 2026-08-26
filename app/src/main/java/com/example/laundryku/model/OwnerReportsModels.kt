package com.example.laundryku.model

import com.google.gson.annotations.SerializedName

data class OwnerReportsResponse(
    val success: Boolean,
    val message: String,
    val data: OwnerReportsData?
)

data class OwnerReportsData(
    val period: String,
    @SerializedName("period_label") val periodLabel: String,
    val summary: OwnerReportsSummary,
    @SerializedName("financial_report") val financialReport: List<OwnerFinancialReportItem>,
    @SerializedName("transaction_report") val transactionReport: List<OwnerTransactionReportItem>,
    @SerializedName("popular_services") val popularServices: List<OwnerPopularService>
)

data class OwnerReportsSummary(
    val pendapatan: Double,
    @SerializedName("total_transaksi") val totalTransactions: Int,
    val selesai: Int,
    val dibatalkan: Int
)

data class OwnerFinancialReportItem(
    @SerializedName("id_pembayaran") val paymentId: Int,
    @SerializedName("id_transaksi") val transactionId: Int,
    @SerializedName("kode_transaksi") val transactionCode: String,
    @SerializedName("nama_pelanggan") val customerName: String,
    val jumlah: Double,
    val metode: String,
    @SerializedName("payment_channel") val paymentChannel: String?,
    @SerializedName("tanggal_bayar") val paidAt: String
)

data class OwnerTransactionReportItem(
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
    @SerializedName("tanggal_masuk") val enteredAt: String,
    @SerializedName("tanggal_selesai") val completedAt: String?
)
