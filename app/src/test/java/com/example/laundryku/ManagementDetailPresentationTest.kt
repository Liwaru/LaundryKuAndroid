package com.example.laundryku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagementDetailPresentationTest {
    @Test
    fun formatsCodesDatesAndCurrencyForReadOnlyDetails() {
        assertEquals("#LDY-10", ManagementDetailPresentation.displayCode("LDY-10"))
        assertEquals("#LDY-10", ManagementDetailPresentation.displayCode("#LDY-10"))
        assertTrue(ManagementDetailPresentation.date("2026-08-27 10:30:00").contains("2026"))
        assertTrue(ManagementDetailPresentation.currency(15000.0).contains("15.000"))
    }
}
