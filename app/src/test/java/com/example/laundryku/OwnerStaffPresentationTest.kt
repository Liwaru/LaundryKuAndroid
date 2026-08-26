package com.example.laundryku

import com.example.laundryku.model.OwnerStaffMember
import org.junit.Assert.assertEquals
import org.junit.Test

class OwnerStaffPresentationTest {
    private val staff = listOf(
        OwnerStaffMember(2, "Kasir", "adminKasir", "082388486206", 2, "aktif", null),
        OwnerStaffMember(3, "Laundry", "staffCuci", "082388486207", 3, "nonaktif", null)
    )

    @Test
    fun searchMatchesNameUsernameAndPhoneCaseInsensitively() {
        assertEquals(listOf(2), OwnerStaffPresentation.filter(staff, "KASIR", OwnerStaffFilter.ALL).map { it.userId })
        assertEquals(listOf(3), OwnerStaffPresentation.filter(staff, "STAFFC", OwnerStaffFilter.ALL).map { it.userId })
        assertEquals(listOf(2), OwnerStaffPresentation.filter(staff, "6206", OwnerStaffFilter.ALL).map { it.userId })
    }

    @Test
    fun roleFilterAndLabelsFollowLevelAndAccountStatus() {
        assertEquals(listOf(2), OwnerStaffPresentation.filter(staff, "", OwnerStaffFilter.CASHIER).map { it.userId })
        assertEquals(listOf(3), OwnerStaffPresentation.filter(staff, "", OwnerStaffFilter.LAUNDRY).map { it.userId })
        assertEquals("Kasir/Admin", OwnerStaffPresentation.roleLabel(2))
        assertEquals("Staff Laundry", OwnerStaffPresentation.roleLabel(3))
        assertEquals("Aktif", OwnerStaffPresentation.statusLabel("aktif"))
        assertEquals("Nonaktif", OwnerStaffPresentation.statusLabel("nonaktif"))
    }
}
