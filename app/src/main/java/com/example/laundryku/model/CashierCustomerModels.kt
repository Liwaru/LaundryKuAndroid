package com.example.laundryku.model

import com.google.gson.annotations.SerializedName

data class CashierCustomersResponse(
    val success: Boolean,
    val message: String,
    val data: CashierCustomersData?
)

data class CashierCustomersData(
    @SerializedName("total_pelanggan") val totalCustomers: Int,
    val customers: List<CashierCustomerData>
)

data class CashierCustomerData(
    @SerializedName("id_user") val userId: Int,
    val nama: String,
    val username: String,
    @SerializedName("no_hp") val phone: String,
    @SerializedName("status_akun") val accountStatus: String,
    @SerializedName("total_transaksi") val totalTransactions: Int,
    @SerializedName("total_pengeluaran") val totalSpending: Double = 0.0,
    @SerializedName("created_at") val createdAt: String? = null
)

data class CashierCustomerDetailResponse(
    val success: Boolean,
    val message: String,
    val data: CashierCustomerDetailData?
)

data class CashierCustomerDetailData(
    val customer: CashierCustomerData,
    @SerializedName("recent_transactions") val recentTransactions: List<CashierCustomerRecentTransaction>
)

data class CashierCustomerRecentTransaction(
    @SerializedName("id_transaksi") val transactionId: Int,
    @SerializedName("kode_transaksi") val transactionCode: String,
    @SerializedName("tanggal_masuk") val enteredAt: String,
    @SerializedName("total_harga") val totalPrice: Double,
    @SerializedName("status_laundry") val laundryStatus: String,
    @SerializedName("status_pembayaran") val paymentStatus: String
)
