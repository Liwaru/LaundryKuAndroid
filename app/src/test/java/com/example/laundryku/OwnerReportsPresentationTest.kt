package com.example.laundryku

import com.example.laundryku.model.OwnerTransactionReportItem
import org.junit.Assert.assertEquals
import org.junit.Test

class OwnerReportsPresentationTest {
    @Test
    fun periodsAndCurrencyUseExpectedApiAndRupiahValues() {
        assertEquals("today", OwnerReportsPeriod.TODAY.apiValue)
        assertEquals("week", OwnerReportsPeriod.WEEK.apiValue)
        assertEquals("month", OwnerReportsPeriod.MONTH.apiValue)
        assertEquals("Rp1.500.000", OwnerReportsPresentation.currency(1500000.0))
        assertEquals("Rp0", OwnerReportsPresentation.currency(0.0))
    }

    @Test
    fun paymentMethodIsFutureReadyWithoutNullChannel() {
        assertEquals("Cash", OwnerReportsPresentation.paymentMethod("cash", null))
        assertEquals("QRIS", OwnerReportsPresentation.paymentMethod("qris", null))
        assertEquals("E-Wallet • GoPay", OwnerReportsPresentation.paymentMethod("e_wallet", "gopay"))
        assertEquals("E-Wallet • DANA", OwnerReportsPresentation.paymentMethod("e_wallet", "dana"))
        assertEquals("E-Wallet • OVO", OwnerReportsPresentation.paymentMethod("e_wallet", "ovo"))
        assertEquals("E-Wallet • ShopeePay", OwnerReportsPresentation.paymentMethod("e_wallet", "shopeepay"))
        assertEquals("PayLater", OwnerReportsPresentation.paymentMethod("paylater", null))
    }

    @Test
    fun statusAndMultiDetailDisplayMatchOtherTransactionScreens() {
        assertEquals("Selesai", OwnerReportsPresentation.laundryStatus("selesai"))
        assertEquals("Dibatalkan", OwnerReportsPresentation.laundryStatus("dibatalkan"))
        assertEquals("Sudah Dibayar", OwnerReportsPresentation.paymentStatus("sudah_dibayar"))
        assertEquals("Belum Dibayar", OwnerReportsPresentation.paymentStatus("belum_dibayar"))
        assertEquals("Cuci Setrika + 1 layanan • 4,5 kg", OwnerReportsPresentation.serviceDetail(transaction()))
        assertEquals(1, OwnerReportsPresentation.activeTransactions(listOf(transaction(), transaction("selesai"))))
    }

    private fun transaction(status: String = "dicuci") = OwnerTransactionReportItem(
        transactionId = 1,
        transactionCode = "LDY001",
        customerName = "Pelanggan",
        serviceName = "Cuci Setrika",
        qty = 4.5,
        satuan = "kg",
        serviceCount = 2,
        totalPrice = 45000.0,
        laundryStatus = status,
        paymentStatus = "sudah_dibayar",
        enteredAt = "2026-08-26 10:00:00",
        completedAt = null
    )
}
