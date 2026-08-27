package com.example.laundryku

import com.example.laundryku.model.CustomerDashboardOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerDashboardPresentationTest {
    @Test
    fun `service summary includes additional service count`() {
        assertEquals("Cuci Kering", CustomerDashboardPresentation.serviceSummary(order(serviceCount = 1)))
        assertEquals(
            "Cuci Kering + 2 layanan",
            CustomerDashboardPresentation.serviceSummary(order(serviceCount = 3))
        )
    }

    @Test
    fun `dashboard uses customer friendly workflow labels`() {
        assertEquals("Menunggu", CustomerDashboardPresentation.statusLabel("menunggu"))
        assertEquals("Dicuci", CustomerDashboardPresentation.statusLabel("dicuci"))
        assertEquals("Dikeringkan", CustomerDashboardPresentation.statusLabel("dikeringkan"))
        assertEquals("Disetrika", CustomerDashboardPresentation.statusLabel("disetrika"))
        assertEquals("Dipacking", CustomerDashboardPresentation.statusLabel("dipacking"))
        assertEquals("Siap Diambil", CustomerDashboardPresentation.statusLabel("siap_diambil"))
        assertEquals("Selesai", CustomerDashboardPresentation.statusLabel("selesai"))
        assertEquals("Dibatalkan", CustomerDashboardPresentation.statusLabel("dibatalkan"))
    }

    @Test
    fun `display code never duplicates hash prefix`() {
        assertEquals("#LDY-1", CustomerDashboardPresentation.displayCode("LDY-1"))
        assertEquals("#LDY-1", CustomerDashboardPresentation.displayCode("#LDY-1"))
    }

    private fun order(serviceCount: Int) = CustomerDashboardOrder(
        transactionId = 1,
        transactionCode = "LDY-1",
        serviceName = "Cuci Kering",
        serviceCount = serviceCount,
        totalPrice = 10_000.0,
        orderDate = "2026-08-27 10:00:00",
        estimatedCompletion = "2026-08-29 10:00:00",
        completionDate = null,
        laundryStatus = "dicuci",
        paymentStatus = "belum_dibayar"
    )
}
