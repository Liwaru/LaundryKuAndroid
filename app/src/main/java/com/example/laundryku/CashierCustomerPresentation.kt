package com.example.laundryku

import com.example.laundryku.model.CashierCustomerData

object CashierCustomerPresentation {
    fun filter(customers: List<CashierCustomerData>, searchQuery: String): List<CashierCustomerData> {
        val query = searchQuery.trim()
        if (query.isBlank()) return customers
        return customers.filter { customer ->
            customer.nama.contains(query, ignoreCase = true) ||
                customer.username.contains(query, ignoreCase = true) ||
                customer.phone.contains(query)
        }
    }
}
