package com.example.laundryku.model

import com.google.gson.annotations.SerializedName

data class CreateQrisPaymentRequest(
    @SerializedName("id_transaksi") val transactionId: Int
)
data class QrisPaymentResponse(
    val success: Boolean,
    val message: String,
    val data: QrisPaymentData?
)

data class QrisPaymentData(
    @SerializedName("id_transaksi") val transactionId: Int,
    @SerializedName("gateway_order_id") val gatewayOrderId: String?,
    val total: Double?,
    @SerializedName("qr_url") val qrUrl: String?,
    val status: String?,
    @SerializedName("expiry_time") val expiryTime: String?
)

data class PaymentStatusResponse(
    val success: Boolean,
    val message: String,
    val data: PaymentStatusData?
)

data class PaymentStatusData(
    @SerializedName("id_transaksi") val transactionId: Int,
    @SerializedName("status_pembayaran") val transactionPaymentStatus: String,
    @SerializedName("payment_status") val paymentStatus: String?,
    val metode: String?,
    val total: Double?,
    @SerializedName("gateway_order_id") val gatewayOrderId: String?,
    @SerializedName("qr_url") val qrUrl: String?,
    @SerializedName("expiry_time") val expiryTime: String?
)
