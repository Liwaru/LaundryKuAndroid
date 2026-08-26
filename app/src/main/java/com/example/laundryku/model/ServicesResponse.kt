package com.example.laundryku.model

data class ServicesResponse(
    val success: Boolean,
    val message: String,
    val data: List<ServiceData>?
)
