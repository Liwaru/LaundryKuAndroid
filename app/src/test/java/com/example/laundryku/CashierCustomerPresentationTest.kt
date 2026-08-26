package com.example.laundryku

import com.example.laundryku.model.CashierCustomerData
import org.junit.Assert.assertEquals
import org.junit.Test

class CashierCustomerPresentationTest {
    private val customers = listOf(
        CashierCustomerData(10, "Alpha", "customer_a", "081234567890", "aktif", 3),
        CashierCustomerData(11, "Beta", "second_user", "089876543210", "nonaktif", 0)
    )

    @Test
    fun searchMatchesNameCaseInsensitively() {
        assertEquals(listOf(10), CashierCustomerPresentation.filter(customers, "aLpH").map { it.userId })
    }

    @Test
    fun searchMatchesUsernameCaseInsensitively() {
        assertEquals(listOf(11), CashierCustomerPresentation.filter(customers, "SECOND").map { it.userId })
    }

    @Test
    fun searchMatchesPhoneAsText() {
        assertEquals(listOf(10), CashierCustomerPresentation.filter(customers, "456789").map { it.userId })
    }

    @Test
    fun blankSearchReturnsOriginalOrder() {
        assertEquals(customers, CashierCustomerPresentation.filter(customers, "  "))
    }

    @Test
    fun unknownSearchReturnsEmptyList() {
        assertEquals(emptyList<CashierCustomerData>(), CashierCustomerPresentation.filter(customers, "missing"))
    }
}
