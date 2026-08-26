package com.example.laundryku.model

data class LoginResponse(
    val success: Boolean,
    val message: String,
    val data: UserData?
)
