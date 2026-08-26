package com.example.laundryku

import com.example.laundryku.model.OwnerStaffMember
import java.util.Locale

enum class OwnerStaffFilter(val level: Int?) {
    ALL(null),
    CASHIER(2),
    LAUNDRY(3)
}

object OwnerStaffPresentation {
    fun filter(
        staff: List<OwnerStaffMember>,
        query: String,
        filter: OwnerStaffFilter
    ): List<OwnerStaffMember> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        return staff.filter { member ->
            val matchesRole = filter.level == null || member.level == filter.level
            val matchesQuery = normalizedQuery.isEmpty() ||
                member.nama.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                member.username.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                member.phone.contains(normalizedQuery)
            matchesRole && matchesQuery
        }
    }

    fun roleLabel(level: Int): String = when (level) {
        2 -> "Kasir/Admin"
        3 -> "Staff Laundry"
        else -> "-"
    }

    fun statusLabel(status: String): String = when (status) {
        "aktif" -> "Aktif"
        "nonaktif" -> "Nonaktif"
        else -> status
    }
}
