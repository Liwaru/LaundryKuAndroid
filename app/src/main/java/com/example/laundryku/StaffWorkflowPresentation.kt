package com.example.laundryku

import com.example.laundryku.model.StaffHistoryData
import com.example.laundryku.model.StaffJobData
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

enum class StaffJobFilter(val databaseStatus: String?) {
    ALL(null),
    WAITING("menunggu"),
    WASHING("dicuci"),
    DRYING("dikeringkan"),
    IRONING("disetrika"),
    PACKING("dipacking"),
    READY("siap_diambil")
}

enum class StaffHistoryFilter(val databaseStatus: String?) {
    ALL(null),
    READY("siap_diambil"),
    COMPLETED("selesai"),
    CANCELLED("dibatalkan")
}

object StaffWorkflowPresentation {
    fun filterJobs(jobs: List<StaffJobData>, filter: StaffJobFilter, query: String): List<StaffJobData> =
        jobs.filter { job ->
            (filter.databaseStatus == null || job.laundryStatus == filter.databaseStatus) &&
                matchesSearch(job.transactionCode, job.customerName, query)
        }

    fun filterHistory(
        history: List<StaffHistoryData>,
        filter: StaffHistoryFilter,
        query: String
    ): List<StaffHistoryData> = history.filter { item ->
        (filter.databaseStatus == null || item.laundryStatus == filter.databaseStatus) &&
            matchesSearch(item.transactionCode, item.customerName, query)
    }

    fun canUpdate(job: StaffJobData): Boolean =
        job.nextStatus != null && job.laundryStatus !in setOf("siap_diambil", "selesai", "dibatalkan")

    fun quantity(qty: Double, unit: String): String {
        val symbols = DecimalFormatSymbols(Locale.forLanguageTag("id-ID"))
        return "${DecimalFormat("0.##", symbols).format(qty)} $unit"
    }

    private fun matchesSearch(code: String, customer: String, query: String): Boolean {
        val trimmed = query.trim()
        return trimmed.isBlank() || code.contains(trimmed, ignoreCase = true) ||
            customer.contains(trimmed, ignoreCase = true)
    }
}
