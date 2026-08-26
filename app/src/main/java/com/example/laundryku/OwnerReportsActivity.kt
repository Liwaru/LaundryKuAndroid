package com.example.laundryku

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class OwnerReportsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_owner_reports)
        applySystemBarInsets()
        findViewById<View>(R.id.ownerReportsNavHome).setOnClickListener { openScreen(DashboardOwnerActivity::class.java) }
        findViewById<View>(R.id.ownerReportsNavStaff).setOnClickListener { openScreen(OwnerStaffActivity::class.java) }
        findViewById<View>(R.id.ownerReportsNavProfile).setOnClickListener { openProfileForLevel(4) }
    }

    private fun applySystemBarInsets() {
        val header = findViewById<View>(R.id.ownerReportsHeader)
        val navigation = findViewById<View>(R.id.ownerReportsBottomNavigation)
        val headerTop = header.paddingTop
        val navigationBottom = navigation.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ownerReportsRoot)) { _, insets ->
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
