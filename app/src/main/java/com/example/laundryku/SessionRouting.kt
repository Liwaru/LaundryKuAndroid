package com.example.laundryku

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

fun dashboardActivityForLevel(level: Int): Class<out AppCompatActivity>? = when (level) {
    1 -> DashboardCustomerActivity::class.java
    2 -> CashierDashboardActivity::class.java
    3 -> StaffDashboardActivity::class.java
    4 -> DashboardOwnerActivity::class.java
    else -> null
}

fun AppCompatActivity.requireValidSession(expectedLevel: Int? = null): SessionManager? {
    val session = SessionManager(this)
    if (!session.isLoggedIn()) {
        openLoginAndClearTask()
        return null
    }

    val level = session.getLevel()
    if (expectedLevel != null && level != expectedLevel) {
        val destination = dashboardActivityForLevel(level)
        if (destination == null) {
            session.clearSession()
            openLoginAndClearTask()
        } else {
            startActivity(Intent(this, destination).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            finish()
        }
        return null
    }
    return session
}

fun AppCompatActivity.openLoginAndClearTask() {
    startActivity(Intent(this, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    })
    finish()
}
