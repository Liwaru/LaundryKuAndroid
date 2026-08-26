package com.example.laundryku

import com.example.laundryku.model.CustomerOrderData
import java.math.BigDecimal

enum class CustomerOrderFilter {
    ALL,
    PROCESSING,
    READY
}

object CustomerOrderPresentation {
    private val processingStatuses = setOf(
        "menunggu",
        "dicuci",
        "dikeringkan",
        "disetrika",
        "dipacking"
    )

    fun filter(orders: List<CustomerOrderData>, filter: CustomerOrderFilter): List<CustomerOrderData> =
        when (filter) {
            CustomerOrderFilter.ALL -> orders
            CustomerOrderFilter.PROCESSING -> orders.filter { it.laundryStatus in processingStatuses }
            CustomerOrderFilter.READY -> orders.filter { it.laundryStatus == "siap_diambil" }
        }

    fun quantity(value: Double, unit: String): String {
        val formatted = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.', ',')
        return "$formatted $unit"
    }

}
