package com.example.laundryku.model

import com.google.gson.annotations.SerializedName

data class CreateOrderRequest(
    @SerializedName("id_user") val userId: Int,
    @SerializedName("id_layanan") val serviceId: Int,
    val qty: Double
)
