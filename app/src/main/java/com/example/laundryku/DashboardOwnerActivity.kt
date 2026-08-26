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

class DashboardOwnerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = requireValidSession(4) ?: return
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_dashboard_owner)
        applySystemBarInsets()
        findViewById<TextView>(R.id.ownerGreetingText).text =
            getString(R.string.dashboard_greeting_format, session.getNama())
        findViewById<View>(R.id.ownerNavReports).setOnClickListener { openScreen(OwnerReportsActivity::class.java) }
        findViewById<View>(R.id.ownerNavStaff).setOnClickListener { openScreen(OwnerStaffActivity::class.java) }
        findViewById<View>(R.id.ownerNavProfile).setOnClickListener { openProfileForLevel(4) }
    }

    private fun applySystemBarInsets() {
        val header = findViewById<View>(R.id.ownerDashboardHeader)
        val navigation = findViewById<View>(R.id.ownerBottomNavigation)
        val headerTop = header.paddingTop
        val navigationBottom = navigation.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ownerDashboardRoot)) { _, insets ->
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
