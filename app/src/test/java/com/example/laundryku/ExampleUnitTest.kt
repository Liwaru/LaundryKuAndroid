package com.example.laundryku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun validSessionData_acceptsAllSupportedRoles() {
        (1..4).forEach { level ->
            assertTrue(
                SessionManager.isValidSessionData(
                    token = "a".repeat(64),
                    userId = level,
                    name = "User",
                    phone = "081234567890",
                    username = "user$level",
                    level = level
                )
            )
        }
    }

    @Test
    fun validSessionData_rejectsCorruptValues() {
        val token = "a".repeat(64)
        assertFalse(SessionManager.isValidSessionData("", 1, "User", "0812", "user", 1))
        assertFalse(SessionManager.isValidSessionData("not-a-token", 1, "User", "0812", "user", 1))
        assertFalse(SessionManager.isValidSessionData(token, -1, "User", "0812", "user", 1))
        assertFalse(SessionManager.isValidSessionData(token, 1, "", "0812", "user", 1))
        assertFalse(SessionManager.isValidSessionData(token, 1, "User", "", "user", 1))
        assertFalse(SessionManager.isValidSessionData(token, 1, "User", "0812", "", 1))
        assertFalse(SessionManager.isValidSessionData(token, 1, "User", "0812", "user", 5))
    }

    @Test
    fun dashboardRouting_matchesRoleLevels() {
        assertEquals(DashboardCustomerActivity::class.java, dashboardActivityForLevel(1))
        assertEquals(CashierDashboardActivity::class.java, dashboardActivityForLevel(2))
        assertEquals(StaffDashboardActivity::class.java, dashboardActivityForLevel(3))
        assertEquals(DashboardOwnerActivity::class.java, dashboardActivityForLevel(4))
        assertNull(dashboardActivityForLevel(0))
        assertNull(dashboardActivityForLevel(5))
    }
}
