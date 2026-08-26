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
    @SerializedName("total_transaksi") val totalTransactions: Int
)
