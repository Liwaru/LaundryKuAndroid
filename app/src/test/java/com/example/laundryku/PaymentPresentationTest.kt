package com.example.laundryku

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentPresentationTest {
    @Test
    fun unpaidNonCancelledOrderCanBePaid() {
        assertTrue(PaymentPresentation.canPay("belum_dibayar", "dicuci"))
        assertTrue(PaymentPresentation.canPay("belum_dibayar", "siap_diambil"))
    }

    @Test
    fun paidOrCancelledOrderCannotBePaid() {
        assertFalse(PaymentPresentation.canPay("sudah_dibayar", "selesai"))
        assertFalse(PaymentPresentation.canPay("belum_dibayar", "dibatalkan"))
    }

    @Test
    fun cashInstructionIsOnlyPendingForWaitingCashRecord() {
        assertTrue(PaymentPresentation.isCashWaiting("cash", "menunggu"))
        assertFalse(PaymentPresentation.isCashWaiting("cash", "berhasil"))
        assertFalse(PaymentPresentation.isCashWaiting("qris", "menunggu"))
        assertFalse(PaymentPresentation.isCashWaiting(null, null))
    }

    @Test
    fun manualQrisCheckRestartsPollingAfterAutomaticLimit() {
        assertEquals(0, PaymentPresentation.pollCountForManualQrisCheck(24, 24))
        assertEquals(7, PaymentPresentation.pollCountForManualQrisCheck(7, 24))
    }

    @Test
    fun eWalletLabelsAreExplicitlyPresentedAsEWallet() {
        assertEquals("E-Wallet • GoPay", PaymentPresentation.paymentMethodLabel("e_wallet", "gopay"))
        assertEquals("E-Wallet • DANA", PaymentPresentation.paymentMethodLabel("e_wallet", "dana"))
        assertEquals("E-Wallet • OVO", PaymentPresentation.paymentMethodLabel("e_wallet", "ovo"))
        assertEquals("E-Wallet • ShopeePay", PaymentPresentation.paymentMethodLabel("e_wallet", "shopeepay"))
        assertEquals(null, PaymentPresentation.channelLabel("paylater"))
    }
}
