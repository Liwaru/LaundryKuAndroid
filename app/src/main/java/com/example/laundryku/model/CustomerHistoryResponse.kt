package com.example.laundryku.model

data class CustomerHistoryResponse(
    val success: Boolean,
    val message: String,
    val data: List<CustomerHistoryData>?
)
