package com.example.laundryku.model

import com.google.gson.annotations.SerializedName

data class ServiceData(
    @SerializedName("id_layanan") val id: Int,
    @SerializedName("nama_layanan") val name: String,
    val harga: Double,
    val satuan: String,
    @SerializedName("minimal_order") val minimumOrder: Double,
    @SerializedName("estimasi_hari") val estimateDays: Int,
    @SerializedName("perlu_setrika") val requiresIroning: Boolean
)
