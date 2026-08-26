package com.example.laundryku

import java.math.BigDecimal

data class OrderPricePreview(
    val actualQuantity: BigDecimal,
    val billedQuantity: BigDecimal,
    val total: BigDecimal
)

object OrderPriceCalculator {
    fun parseQuantity(value: String): BigDecimal? = runCatching {
        value.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toBigDecimal()
    }.getOrNull()

    fun isValidQuantity(quantity: BigDecimal, unit: String): Boolean {
        if (quantity <= BigDecimal.ZERO) return false
        return unit != "pcs" || quantity.stripTrailingZeros().scale() <= 0
    }

    fun calculate(
        quantity: BigDecimal,
        minimumOrder: BigDecimal,
        unitPrice: BigDecimal
    ): OrderPricePreview {
        require(quantity > BigDecimal.ZERO)
        val billedQuantity = quantity.max(minimumOrder)
        return OrderPricePreview(
            actualQuantity = quantity,
            billedQuantity = billedQuantity,
            total = billedQuantity.multiply(unitPrice)
        )
    }
}
