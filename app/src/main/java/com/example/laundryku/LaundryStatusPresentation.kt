package com.example.laundryku

object LaundryStatusPresentation {
    fun label(status: String): String = when (status) {
        "menunggu" -> "Menunggu"
        "dicuci" -> "Dicuci"
        "dikeringkan" -> "Dikeringkan"
        "disetrika" -> "Disetrika"
        "dipacking" -> "Dipacking"
        "siap_diambil" -> "Siap Diambil"
        "selesai" -> "Selesai"
        "dibatalkan" -> "Dibatalkan"
        else -> status.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}
