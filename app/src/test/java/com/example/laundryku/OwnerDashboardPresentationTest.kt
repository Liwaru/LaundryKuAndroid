package com.example.laundryku

import com.example.laundryku.model.OwnerPopularService
import org.junit.Assert.assertEquals
import org.junit.Test

class OwnerDashboardPresentationTest {
    @Test
    fun currencyUsesIndonesianRupiahWithoutDecimals() {
        assertEquals("Rp150.000", OwnerDashboardPresentation.currency(150000.0))
        assertEquals("Rp0", OwnerDashboardPresentation.currency(0.0))
    }

    @Test
    fun popularServiceAndOperationalCountsMapForTheExistingLayout() {
        val service = OwnerPopularService(2, "Cuci Setrika", 3)
        assertEquals("3 Pesanan", OwnerDashboardPresentation.serviceOrderCount(service))
        assertEquals(
            listOf(2, 1, 0, 4, 3),
            OwnerDashboardPresentation.operationalCounts(2, 1, 0, 4, 3)
        )
    }
}
