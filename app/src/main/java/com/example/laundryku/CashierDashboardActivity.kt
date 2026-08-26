package com.example.laundryku

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class CashierDashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = requireValidSession(2) ?: return
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_cashier_dashboard)
        applySystemBarInsets()
        findViewById<TextView>(R.id.cashierGreetingText).text =
            getString(R.string.dashboard_greeting_format, session.getNama())
        findViewById<View>(R.id.cashierNavTransactions).setOnClickListener { openScreen(CashierTransactionActivity::class.java) }
        findViewById<View>(R.id.cashierNavCustomers).setOnClickListener { openScreen(CashierCustomerActivity::class.java) }
        findViewById<View>(R.id.cashierNavProfile).setOnClickListener { openProfileForLevel(2) }
    }

    private fun applySystemBarInsets() {
        val header = findViewById<View>(R.id.cashierDashboardHeader)
        val navigation = findViewById<View>(R.id.cashierBottomNavigation)
        val headerTop = header.paddingTop
        val navigationBottom = navigation.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.cashierDashboardRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.setPadding(header.paddingLeft, headerTop + bars.top, header.paddingRight, header.paddingBottom)
            navigation.setPadding(
                navigation.paddingLeft,
                navigation.paddingTop,
                navigation.paddingRight,
                navigationBottom + bars.bottom
            )
            insets
        }
    }
}
