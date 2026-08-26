package com.example.laundryku

import com.example.laundryku.model.StaffHistoryData
import com.example.laundryku.model.StaffJobData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffWorkflowPresentationTest {
    @Test
    fun jobsFilterAndSearchUseLoadedData() {
        val jobs = listOf(job("menunggu", "LDY001", "Hendrik"), job("dicuci", "LDY002", "Kevin"))
        assertEquals(2, StaffWorkflowPresentation.filterJobs(jobs, StaffJobFilter.ALL, "").size)
        assertEquals(1, StaffWorkflowPresentation.filterJobs(jobs, StaffJobFilter.WASHING, "").size)
        assertEquals(1, StaffWorkflowPresentation.filterJobs(jobs, StaffJobFilter.ALL, "kevin").size)
        assertEquals(1, StaffWorkflowPresentation.filterJobs(jobs, StaffJobFilter.ALL, "LDY001").size)
    }

    @Test
    fun readyJobCannotBeUpdated() {
        assertTrue(StaffWorkflowPresentation.canUpdate(job("dicuci", next = "dikeringkan")))
        assertFalse(StaffWorkflowPresentation.canUpdate(job("siap_diambil", next = null)))
    }

    @Test
    fun historyFiltersFinalOperationalStatuses() {
        val values = listOf(history("siap_diambil"), history("selesai"), history("dibatalkan"))
        assertEquals(3, StaffWorkflowPresentation.filterHistory(values, StaffHistoryFilter.ALL, "").size)
        assertEquals(1, StaffWorkflowPresentation.filterHistory(values, StaffHistoryFilter.READY, "").size)
        assertEquals(1, StaffWorkflowPresentation.filterHistory(values, StaffHistoryFilter.COMPLETED, "").size)
        assertEquals(1, StaffWorkflowPresentation.filterHistory(values, StaffHistoryFilter.CANCELLED, "").size)
    }

    @Test
    fun quantityUsesIndonesianDecimal() {
        assertEquals("4,5 kg", StaffWorkflowPresentation.quantity(4.5, "kg"))
        assertEquals("1 pcs", StaffWorkflowPresentation.quantity(1.0, "pcs"))
    }

    private fun job(status: String, code: String = "LDY001", customer: String = "Hendrik", next: String? = "dicuci") =
        StaffJobData(1, code, customer, "Cuci Kering", 2.0, "kg", null, status, false, next)

    private fun history(status: String) =
        StaffHistoryData(1, "LDY001", "Hendrik", "Cuci Kering", 2.0, "kg", status, null)
}
