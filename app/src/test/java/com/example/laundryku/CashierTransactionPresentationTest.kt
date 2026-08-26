package com.example.laundryku

import com.example.laundryku.model.CashierTransactionData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CashierTransactionPresentationTest {
    @Test
    fun filtersFollowCashierDefinitions() {
        val transactions = listOf(
            transaction(1, "belum_dibayar", "dicuci"),
            transaction(2, "sudah_dibayar", "selesai"),
            transaction(3, "belum_dibayar", "siap_diambil")
        )

        assertEquals(3, CashierTransactionPresentation.filter(transactions, CashierTransactionFilter.ALL, "").size)
        assertEquals(2, CashierTransactionPresentation.filter(transactions, CashierTransactionFilter.UNPAID, "").size)
        assertEquals(1, CashierTransactionPresentation.filter(transactions, CashierTransactionFilter.PAID, "").size)
        assertEquals(1, CashierTransactionPresentation.filter(transactions, CashierTransactionFilter.READY, "").size)
        assertEquals(1, CashierTransactionPresentation.filter(transactions, CashierTransactionFilter.COMPLETED, "").size)
    }

    @Test
    fun searchMatchesCodeCustomerAndPhone() {
        val transaction = transaction(1, "belum_dibayar", "dicuci")
        assertEquals(1, CashierTransactionPresentation.filter(listOf(transaction), CashierTransactionFilter.ALL, "LDY001").size)
        assertEquals(1, CashierTransactionPresentation.filter(listOf(transaction), CashierTransactionFilter.ALL, "hendrik").size)
        assertEquals(1, CashierTransactionPresentation.filter(listOf(transaction), CashierTransactionFilter.ALL, "0812").size)
        assertEquals(0, CashierTransactionPresentation.filter(listOf(transaction), CashierTransactionFilter.ALL, "Kevin").size)
    }

    @Test
    fun onlyWaitingCashCanBeConfirmed() {
        assertTrue(CashierTransactionPresentation.canConfirmCash(transaction(1, "belum_dibayar", "dicuci")))
        assertFalse(CashierTransactionPresentation.canConfirmCash(transaction(1, "sudah_dibayar", "dicuci")))
        assertFalse(CashierTransactionPresentation.canConfirmCash(transaction(1, "belum_dibayar", "dibatalkan")))
        assertFalse(CashierTransactionPresentation.canConfirmCash(transaction(1, "belum_dibayar", "dicuci", method = null)))
        assertFalse(CashierTransactionPresentation.canConfirmCash(transaction(1, "belum_dibayar", "dicuci", status = "berhasil")))
    }

    @Test
    fun quantityUsesIndonesianFormat() {
        assertEquals("4,5 kg", CashierTransactionPresentation.quantity(4.5, "kg"))
        assertEquals("2 kg", CashierTransactionPresentation.quantity(2.0, "kg"))
    }

    private fun transaction(
        id: Int,
        payment: String,
        laundry: String,
        method: String? = "cash",
        status: String? = "menunggu"
    ) = CashierTransactionData(
        transactionId = id,
        transactionCode = "LDY001",
        customerId = 1,
        customerName = "Hendrik",
        phone = "081234567890",
        serviceName = "Cuci Setrika",
        qty = 4.5,
        satuan = "kg",
        totalPrice = 45000.0,
        laundryStatus = laundry,
        paymentStatus = payment,
        paymentMethod = method,
        paymentChannel = null,
        paymentRecordStatus = status,
        orderDate = "2026-08-25 10:00:00",
        estimatedCompletion = "2026-08-28 10:00:00"
    )
}
