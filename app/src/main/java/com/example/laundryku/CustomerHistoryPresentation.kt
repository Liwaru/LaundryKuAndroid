package com.example.laundryku

import com.example.laundryku.model.CustomerHistoryData

enum class CustomerHistoryFilter {
    ALL,
    COMPLETED,
    CANCELLED
}

data class CustomerHistorySummary(
    val completedCount: Int,
    val totalSpending: Double
)

object CustomerHistoryPresentation {
    fun filter(
        history: List<CustomerHistoryData>,
        filter: CustomerHistoryFilter
    ): List<CustomerHistoryData> = when (filter) {
        CustomerHistoryFilter.ALL -> history
        CustomerHistoryFilter.COMPLETED -> history.filter { it.laundryStatus == "selesai" }
        CustomerHistoryFilter.CANCELLED -> history.filter { it.laundryStatus == "dibatalkan" }
    }

    fun summary(history: List<CustomerHistoryData>): CustomerHistorySummary = CustomerHistorySummary(
        completedCount = history.count { it.laundryStatus == "selesai" },
        totalSpending = history
            .filter { it.laundryStatus == "selesai" && it.paymentStatus == "sudah_dibayar" }
            .sumOf { it.totalPrice }
    )
}
