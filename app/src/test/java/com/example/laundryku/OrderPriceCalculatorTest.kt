package com.example.laundryku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class OrderPriceCalculatorTest {
    @Test
    fun cuciKering_quantityBelowMinimum_billsTwoKilograms() {
        assertTotal("1", "2", "7000", "14000")
    }

    @Test
    fun cuciKering_decimalQuantity_usesActualQuantity() {
        assertTotal("4.5", "2", "7000", "31500")
    }

    @Test
    fun cuciSetrika_twoKilograms_costsTwentyThousand() {
        assertTotal("2", "2", "10000", "20000")
    }

    @Test
    fun express_threeKilograms_costsFortyFiveThousand() {
        assertTotal("3", "2", "15000", "45000")
    }

    @Test
    fun bedCover_oneAndTwoPieces_haveExpectedTotals() {
        assertTotal("1", "1", "25000", "25000")
        assertTotal("2", "1", "25000", "50000")
    }

    @Test
    fun quantityValidation_rejectsZeroNegativeAndDecimalPieces() {
        assertFalse(OrderPriceCalculator.isValidQuantity(BigDecimal.ZERO, "kg"))
        assertFalse(OrderPriceCalculator.isValidQuantity(BigDecimal("-1"), "kg"))
        assertFalse(OrderPriceCalculator.isValidQuantity(BigDecimal("1.5"), "pcs"))
        assertTrue(OrderPriceCalculator.isValidQuantity(BigDecimal("2"), "pcs"))
        assertEquals(null, OrderPriceCalculator.parseQuantity(""))
    }

    @Test
    fun quantityParser_acceptsIndonesianDecimalComma() {
        assertEquals(0, BigDecimal("4.5").compareTo(OrderPriceCalculator.parseQuantity("4,5")))
    }

    private fun assertTotal(quantity: String, minimum: String, price: String, expected: String) {
        val preview = OrderPriceCalculator.calculate(
            BigDecimal(quantity),
            BigDecimal(minimum),
            BigDecimal(price)
        )
        assertEquals(0, BigDecimal(expected).compareTo(preview.total))
    }
}
