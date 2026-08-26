package com.example.laundryku

import android.app.Application
import com.example.laundryku.network.RetrofitClient

class LaundryKuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RetrofitClient.initialize(this)
    }
}
