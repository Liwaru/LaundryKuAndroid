package com.example.laundryku

import com.example.laundryku.model.CustomerHistoryData
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerHistoryPresentationTest {
    private val completedPaid = item(1, "selesai", "sudah_dibayar", 20_000.0)
    private val completedUnpaid = item(2, "selesai", "belum_dibayar", 45_000.0)
    private val cancelled = item(3, "dibatalkan", "belum_dibayar", 25_000.0)

    @Test
    fun filtersHistoryLocallyByFinalStatus() {
        val items = listOf(completedPaid, completedUnpaid, cancelled)

        assertEquals(3, CustomerHistoryPresentation.filter(items, CustomerHistoryFilter.ALL).size)
        assertEquals(2, CustomerHistoryPresentation.filter(items, CustomerHistoryFilter.COMPLETED).size)
        assertEquals(1, CustomerHistoryPresentation.filter(items, CustomerHistoryFilter.CANCELLED).size)
    }

    @Test
    fun summaryCountsCompletedAndOnlyPaidCompletedSpending() {
        val summary = CustomerHistoryPresentation.summary(listOf(completedPaid, completedUnpaid, cancelled))

        assertEquals(2, summary.completedCount)
        assertEquals(20_000.0, summary.totalSpending, 0.0)
    }

    @Test
    fun emptyHistoryHasZeroSummary() {
        assertEquals(CustomerHistorySummary(0, 0.0), CustomerHistoryPresentation.summary(emptyList()))
    }

    private fun item(id: Int, laundryStatus: String, paymentStatus: String, total: Double) =
        CustomerHistoryData(
            transactionId = id,
            transactionCode = "LDY$id",
            serviceName = "Layanan",
            qty = 2.0,
            satuan = "kg",
            totalPrice = total,
            orderDate = "2026-08-25 10:00:00",
            completionDate = null,
            laundryStatus = laundryStatus,
            paymentStatus = paymentStatus
        )
}
