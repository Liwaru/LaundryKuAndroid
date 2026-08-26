package com.example.laundryku

import com.example.laundryku.model.CashierDashboardTransaction
import org.junit.Assert.assertEquals
import org.junit.Test

class CashierDashboardPresentationTest {
    @Test
    fun statusAndPaymentLabelsAreComplete() {
        assertEquals("Menunggu", CashierDashboardPresentation.statusLabel("menunggu"))
        assertEquals("Dicuci", CashierDashboardPresentation.statusLabel("dicuci"))
        assertEquals("Dikeringkan", CashierDashboardPresentation.statusLabel("dikeringkan"))
        assertEquals("Disetrika", CashierDashboardPresentation.statusLabel("disetrika"))
        assertEquals("Dipacking", CashierDashboardPresentation.statusLabel("dipacking"))
        assertEquals("Siap Diambil", CashierDashboardPresentation.statusLabel("siap_diambil"))
        assertEquals("Selesai", CashierDashboardPresentation.statusLabel("selesai"))
        assertEquals("Dibatalkan", CashierDashboardPresentation.statusLabel("dibatalkan"))
        assertEquals("Sudah Dibayar", CashierDashboardPresentation.paymentLabel("sudah_dibayar"))
        assertEquals("Belum Dibayar", CashierDashboardPresentation.paymentLabel("belum_dibayar"))
    }

    @Test
    fun serviceSummaryAndCurrencyUseIndonesianPresentation() {
        assertEquals("Cuci Setrika • 4,5 kg", CashierDashboardPresentation.serviceDetail(transaction()))
        assertEquals(
            "Cuci Setrika + 1 layanan • 4,5 kg",
            CashierDashboardPresentation.serviceDetail(transaction(serviceCount = 2))
        )
        assertEquals("Rp45.000", CashierDashboardPresentation.currency(45000.0))
        assertEquals("#LDY001", CashierDashboardPresentation.displayCode("LDY001"))
    }

    private fun transaction(serviceCount: Int = 1) = CashierDashboardTransaction(
        transactionId = 1,
        transactionCode = "LDY001",
        customerName = "Hendrik",
        serviceName = "Cuci Setrika",
        qty = 4.5,
        satuan = "kg",
        serviceCount = serviceCount,
        totalPrice = 45000.0,
        laundryStatus = "dicuci",
        paymentStatus = "sudah_dibayar",
        paymentMethod = "cash",
        orderDate = "2026-08-26 10:00:00"
    )
}
