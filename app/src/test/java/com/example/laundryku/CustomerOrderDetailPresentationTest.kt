package com.example.laundryku

import com.example.laundryku.model.CustomerOrderDetailData
import com.example.laundryku.model.CustomerOrderLineData
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerOrderDetailPresentationTest {
    @Test
    fun mapsEveryLaundryStatus() {
        assertEquals("Menunggu", CustomerOrderDetailPresentation.statusLabel("menunggu"))
        assertEquals("Dicuci", CustomerOrderDetailPresentation.statusLabel("dicuci"))
        assertEquals("Dikeringkan", CustomerOrderDetailPresentation.statusLabel("dikeringkan"))
        assertEquals("Disetrika", CustomerOrderDetailPresentation.statusLabel("disetrika"))
        assertEquals("Dipacking", CustomerOrderDetailPresentation.statusLabel("dipacking"))
        assertEquals("Siap Diambil", CustomerOrderDetailPresentation.statusLabel("siap_diambil"))
        assertEquals("Selesai", CustomerOrderDetailPresentation.statusLabel("selesai"))
        assertEquals("Dibatalkan", CustomerOrderDetailPresentation.statusLabel("dibatalkan"))
    }

    @Test
    fun keepsAllDatabaseDetailLines() {
        val first = line(1, "Cuci Kering")
        val second = line(2, "Bed Cover")
        assertEquals(listOf(first, second), CustomerOrderDetailPresentation.lines(data(listOf(first, second))))
    }

    @Test
    fun fallsBackToTopLevelFieldsForCompatibleResponse() {
        val line = CustomerOrderDetailPresentation.lines(data(emptyList())).single()
        assertEquals("Cuci Setrika", line.serviceName)
        assertEquals(4.5, line.qty, 0.0)
        assertEquals(10_000.0, line.unitPrice, 0.0)
        assertEquals(45_000.0, line.subtotal, 0.0)
    }

    private fun line(id: Int, service: String) = CustomerOrderLineData(
        detailId = id,
        serviceId = id,
        serviceName = service,
        qty = 2.0,
        satuan = "kg",
        unitPrice = 7_000.0,
        subtotal = 14_000.0
    )

    private fun data(lines: List<CustomerOrderLineData>) = CustomerOrderDetailData(
        transactionId = 1,
        transactionCode = "LDY1",
        serviceName = "Cuci Setrika",
        qty = 4.5,
        satuan = "kg",
        unitPrice = 10_000.0,
        subtotal = 45_000.0,
        totalPrice = 45_000.0,
        orderDate = "2026-08-25 10:00:00",
        estimatedCompletion = null,
        completionDate = null,
        laundryStatus = "dicuci",
        paymentStatus = "belum_dibayar",
        details = lines,
        timeline = emptyList()
    )
}
