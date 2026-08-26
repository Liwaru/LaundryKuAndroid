package com.example.laundryku

import com.example.laundryku.model.CashierTransactionData
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

    @Test
    fun qrisPresentation_mapsPendingSuccessFailureAndExpiry() {
        assertEquals("Cash", PaymentPresentation.paymentMethodLabel("cash"))
        assertEquals("QRIS", PaymentPresentation.paymentMethodLabel("qris"))
        assertNull(PaymentPresentation.paymentMethodLabel("gopay"))
        assertTrue(PaymentPresentation.isQrisWaiting("qris", "menunggu"))
        assertEquals(QrisUiState.PENDING, PaymentPresentation.qrisState("belum_dibayar", "menunggu"))
        assertEquals(QrisUiState.SUCCESS, PaymentPresentation.qrisState("sudah_dibayar", "berhasil"))
        assertEquals(QrisUiState.FAILURE, PaymentPresentation.qrisState("belum_dibayar", "gagal"))
        assertEquals("Rp45.000", PaymentPresentation.formatRupiah(45_000.0))
        assertTrue(PaymentPresentation.shouldPollQris(true, false, 23, 24))
        assertFalse(PaymentPresentation.shouldPollQris(true, false, 24, 24))
        assertFalse(PaymentPresentation.shouldPollQris(false, false, 0, 24))
        assertFalse(PaymentPresentation.shouldPollQris(true, true, 0, 24))
    }

    @Test
    fun cashierQrisPayment_neverShowsCashConfirmationButCanCompleteWhenPaid() {
        val pending = cashierTransaction("belum_dibayar", "menunggu", "dicuci")
        assertFalse(CashierTransactionPresentation.canConfirmCash(pending))
        assertFalse(CashierTransactionPresentation.canComplete(pending))

        val paidReady = cashierTransaction("sudah_dibayar", "berhasil", "siap_diambil")
        assertFalse(CashierTransactionPresentation.canConfirmCash(paidReady))
        assertTrue(CashierTransactionPresentation.canComplete(paidReady))
    }

    private fun cashierTransaction(
        transactionPaymentStatus: String,
        paymentRecordStatus: String,
        laundryStatus: String
    ) = CashierTransactionData(
        transactionId = 10,
        transactionCode = "LDY010",
        customerId = 1,
        customerName = "Customer",
        phone = "081234567890",
        serviceName = "Cuci Setrika",
        qty = 4.5,
        satuan = "kg",
        totalPrice = 45_000.0,
        laundryStatus = laundryStatus,
        paymentStatus = transactionPaymentStatus,
        paymentMethod = "qris",
        paymentChannel = "qris",
        paymentRecordStatus = paymentRecordStatus,
        orderDate = "2026-08-26 10:00:00",
        estimatedCompletion = null
    )
}
