package com.example.laundryku

import com.example.laundryku.model.OwnerPopularService
import java.text.NumberFormat
import java.util.Locale

object OwnerDashboardPresentation {
    private val indonesianLocale = Locale.forLanguageTag("id-ID")

    fun currency(value: Double): String = NumberFormat
        .getCurrencyInstance(indonesianLocale)
        .apply {
            maximumFractionDigits = if (value % 1.0 == 0.0) 0 else 2
            minimumFractionDigits = 0
        }
        .format(value)
        .replace('\u00a0', ' ')

    fun serviceOrderCount(service: OwnerPopularService): String =
        "${service.orderCount} Pesanan"

    fun operationalCounts(
        dicuci: Int,
        dikeringkan: Int,
        disetrika: Int,
        dipacking: Int,
        siapDiambil: Int
    ): List<Int> = listOf(dicuci, dikeringkan, disetrika, dipacking, siapDiambil)
}
