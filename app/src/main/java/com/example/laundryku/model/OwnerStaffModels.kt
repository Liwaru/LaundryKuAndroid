package com.example.laundryku.model

import com.google.gson.annotations.SerializedName

data class OwnerStaffResponse(
    val success: Boolean,
    val message: String,
    val data: OwnerStaffData?
)

data class OwnerStaffData(
    val summary: OwnerStaffSummary,
    val staff: List<OwnerStaffMember>
)

data class OwnerStaffSummary(
    @SerializedName("jumlah_kasir") val cashierCount: Int,
    @SerializedName("jumlah_staff_laundry") val laundryStaffCount: Int,
    @SerializedName("jumlah_staff_aktif") val activeStaffCount: Int
)

data class OwnerStaffMember(
    @SerializedName("id_user") val userId: Int,
    val nama: String,
    val username: String,
    @SerializedName("no_hp") val phone: String,
    val level: Int,
    @SerializedName("status_akun") val accountStatus: String,
    @SerializedName("created_at") val createdAt: String?
)

data class OwnerCreateStaffRequest(
    @SerializedName("id_user") val ownerId: Int,
    val nama: String,
    @SerializedName("no_hp") val phone: String,
    val username: String,
    val password: String,
    val level: Int
)

data class OwnerCreateStaffResponse(
    val success: Boolean,
    val message: String,
    val data: OwnerCreatedStaff?
)

data class OwnerCreatedStaff(
    @SerializedName("id_user") val userId: Int,
    val nama: String,
    val username: String,
    @SerializedName("no_hp") val phone: String,
    val level: Int,
    @SerializedName("status_akun") val accountStatus: String
)
