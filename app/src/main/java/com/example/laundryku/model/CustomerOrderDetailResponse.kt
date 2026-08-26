package com.example.laundryku.model

import com.google.gson.annotations.SerializedName

data class CustomerOrderDetailResponse(
    val success: Boolean,
    val message: String,
    val data: CustomerOrderDetailData?
)

data class CustomerOrderDetailData(
    @SerializedName("id_transaksi") val transactionId: Int,
    @SerializedName("kode_transaksi") val transactionCode: String,
    @SerializedName("nama_layanan") val serviceName: String,
    val qty: Double,
    val satuan: String,
    @SerializedName("harga_satuan") val unitPrice: Double,
    val subtotal: Double,
    @SerializedName("total_harga") val totalPrice: Double,
    @SerializedName("tanggal_masuk") val orderDate: String,
    @SerializedName("estimasi_selesai") val estimatedCompletion: String?,
    @SerializedName("tanggal_selesai") val completionDate: String?,
    @SerializedName("status_laundry") val laundryStatus: String,
    @SerializedName("status_pembayaran") val paymentStatus: String,
    val details: List<CustomerOrderLineData>,
    val timeline: List<CustomerOrderTimelineData>,
    val payment: CustomerPaymentData? = null
)

data class CustomerOrderLineData(
    @SerializedName("id_detail") val detailId: Int,
    @SerializedName("id_layanan") val serviceId: Int,
    @SerializedName("nama_layanan") val serviceName: String,
    val qty: Double,
    val satuan: String,
    @SerializedName("harga_satuan") val unitPrice: Double,
    val subtotal: Double
)

data class CustomerOrderTimelineData(
    @SerializedName("status_laundry") val laundryStatus: String,
    val waktu: String,
    val catatan: String?
)

data class CustomerPaymentData(
    @SerializedName("id_pembayaran") val paymentId: Int,
    val metode: String,
    @SerializedName("payment_channel") val paymentChannel: String?,
    val jumlah: Double,
    val status: String,
    @SerializedName("tanggal_bayar") val paymentDate: String?
)
