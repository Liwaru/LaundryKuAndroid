package com.example.laundryku

import com.example.laundryku.model.CustomerOrderData
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerOrderPresentationTest {
    @Test
    fun filtersSeparateProcessingAndReadyOrders() {
        val orders = listOf(
            order("menunggu"),
            order("dicuci"),
            order("siap_diambil"),
            order("selesai"),
            order("dibatalkan")
        )

        assertEquals(5, CustomerOrderPresentation.filter(orders, CustomerOrderFilter.ALL).size)
        assertEquals(
            listOf("menunggu", "dicuci"),
            CustomerOrderPresentation.filter(orders, CustomerOrderFilter.PROCESSING).map { it.laundryStatus }
        )
        assertEquals(
            listOf("siap_diambil"),
            CustomerOrderPresentation.filter(orders, CustomerOrderFilter.READY).map { it.laundryStatus }
        )
    }

    @Test
    fun quantityUsesIndonesianDecimalFormatting() {
        assertEquals("4,5 kg", CustomerOrderPresentation.quantity(4.5, "kg"))
        assertEquals("2 kg", CustomerOrderPresentation.quantity(2.0, "kg"))
        assertEquals("1 pcs", CustomerOrderPresentation.quantity(1.0, "pcs"))
    }

    private fun order(status: String) = CustomerOrderData(
        transactionId = 1,
        transactionCode = "LDY1",
        customerId = 1,
        serviceName = "Cuci Kering",
        qty = 2.0,
        satuan = "kg",
        unitPrice = 7000.0,
        subtotal = 14000.0,
        totalPrice = 14000.0,
        orderDate = "2026-08-25 10:00:00",
        estimatedCompletion = "2026-08-27 10:00:00",
        laundryStatus = status,
        paymentStatus = "belum_dibayar"
    )
}
