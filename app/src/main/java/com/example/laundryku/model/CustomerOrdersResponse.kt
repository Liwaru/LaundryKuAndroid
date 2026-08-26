package com.example.laundryku.model

data class CustomerOrdersResponse(
    val success: Boolean,
    val message: String,
    val data: List<CustomerOrderData>?
)
