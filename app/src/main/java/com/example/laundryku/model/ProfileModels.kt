package com.example.laundryku.model

import com.google.gson.annotations.SerializedName

data class UpdateProfileRequest(
    val nama: String,
    val username: String,
    @SerializedName("no_hp") val phone: String
)

data class UpdateProfileResponse(
    val success: Boolean,
    val message: String,
    val data: UpdatedProfileData?
)

data class UpdatedProfileData(
    val nama: String,
    val username: String,
    @SerializedName("no_hp") val phone: String
)

data class ChangePasswordRequest(
    @SerializedName("old_password") val oldPassword: String,
    @SerializedName("new_password") val newPassword: String,
    @SerializedName("confirm_password") val confirmation: String
)

data class ChangePasswordResponse(
    val success: Boolean,
    val message: String
)
