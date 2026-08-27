package com.example.laundryku

import com.example.laundryku.model.CustomerOrderDetailData
import com.example.laundryku.model.CustomerOrderLineData

object CustomerOrderDetailPresentation {
    fun lines(data: CustomerOrderDetailData): List<CustomerOrderLineData> = data.details.ifEmpty {
        listOf(
            CustomerOrderLineData(
                detailId = 0,
                serviceId = 0,
                serviceName = data.serviceName,
                qty = data.qty,
                satuan = data.satuan,
                unitPrice = data.unitPrice,
                subtotal = data.subtotal
            )
        )
    }

    fun statusLabel(status: String): String = when (status) {
        "menunggu" -> "Menunggu"
        "dicuci" -> "Sedang Dicuci"
        "dikeringkan" -> "Sedang Dikeringkan"
        "disetrika" -> "Sedang Disetrika"
        "dipacking" -> "Sedang Dipacking"
        "siap_diambil" -> "Siap Diambil"
        "selesai" -> "Selesai"
        "dibatalkan" -> "Dibatalkan"
        else -> status.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}
