package com.example.laundryku

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class CashierCustomerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_cashier_customers)
        applySystemBarInsets()
        findViewById<View>(R.id.cashierCustomersNavHome).setOnClickListener { openScreen(CashierDashboardActivity::class.java) }
        findViewById<View>(R.id.cashierCustomersNavTransactions).setOnClickListener { openScreen(CashierTransactionActivity::class.java) }
        findViewById<View>(R.id.cashierCustomersNavProfile).setOnClickListener { openProfileForLevel(2) }
    }

    private fun applySystemBarInsets() {
        val header = findViewById<View>(R.id.cashierCustomersHeader)
        val navigation = findViewById<View>(R.id.cashierCustomersBottomNavigation)
        val headerTop = header.paddingTop
        val navigationBottom = navigation.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.cashierCustomersRoot)) { _, insets ->
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
