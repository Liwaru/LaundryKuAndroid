package com.example.laundryku.model

import com.google.gson.annotations.SerializedName

data class CashierTransactionsResponse(
    val success: Boolean,
    val message: String,
    val data: List<CashierTransactionData>?
)

data class CashierTransactionData(
    @SerializedName("id_transaksi") val transactionId: Int,
    @SerializedName("kode_transaksi") val transactionCode: String,
    @SerializedName("id_pelanggan") val customerId: Int,
    @SerializedName("nama_pelanggan") val customerName: String,
    @SerializedName("no_hp") val phone: String,
    @SerializedName("nama_layanan") val serviceName: String,
    val qty: Double,
    val satuan: String,
    @SerializedName("total_harga") val totalPrice: Double,
    @SerializedName("status_laundry") val laundryStatus: String,
    @SerializedName("status_pembayaran") val paymentStatus: String,
    @SerializedName("metode_pembayaran") val paymentMethod: String?,
    @SerializedName("payment_channel") val paymentChannel: String?,
    @SerializedName("status_payment_record") val paymentRecordStatus: String?,
    @SerializedName("tanggal_masuk") val orderDate: String,
    @SerializedName("estimasi_selesai") val estimatedCompletion: String?
)

data class ConfirmCashPaymentRequest(
    @SerializedName("id_user") val cashierId: Int,
    @SerializedName("id_transaksi") val transactionId: Int
)

data class ConfirmCashPaymentResponse(
    val success: Boolean,
    val message: String,
    val data: ConfirmCashPaymentData?
)

data class ConfirmCashPaymentData(
    @SerializedName("id_transaksi") val transactionId: Int,
    @SerializedName("status_pembayaran") val transactionPaymentStatus: String,
    @SerializedName("payment_status") val paymentStatus: String,
    val metode: String,
    val jumlah: Double
)

data class CompleteTransactionRequest(
    @SerializedName("id_user") val cashierId: Int,
    @SerializedName("id_transaksi") val transactionId: Int
)

data class CompleteTransactionResponse(
    val success: Boolean,
    val message: String,
    val data: CompleteTransactionData?
)

data class CompleteTransactionData(
    @SerializedName("id_transaksi") val transactionId: Int,
    @SerializedName("status_laundry") val laundryStatus: String,
    @SerializedName("tanggal_selesai") val completionDate: String?
)
