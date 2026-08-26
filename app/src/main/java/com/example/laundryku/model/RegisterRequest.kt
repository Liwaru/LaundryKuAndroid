package com.example.laundryku.model

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    val nama: String,
    @SerializedName("no_hp") val noHp: String,
    val username: String,
    val password: String,
    @SerializedName("konfirmasi_password") val confirmationPassword: String
)
