package com.example.laundryku.model

import com.google.gson.annotations.SerializedName

data class UserData(
    @SerializedName("id_user") val idUser: Int,
    val nama: String,
    @SerializedName("no_hp") val noHp: String,
    val username: String,
    val level: Int
)
