package com.example.laundryku

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ProfileActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = requireValidSession() ?: return
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_profile)
        applySystemBarInsets()
        val level = sessionManager.getLevel()
        configureProfileData(level)
        configureBottomNavigation(level)
        configureNavigationActions(level)
        findViewById<View>(R.id.profileBackButton).setOnClickListener { finish() }
        findViewById<View>(R.id.profileLogoutMenu).setOnClickListener { showLogoutConfirmation() }
    }

    private fun configureProfileData(level: Int) {
        val roleName = when (level) {
            1 -> getString(R.string.role_customer)
            2 -> getString(R.string.role_cashier_admin)
            3 -> getString(R.string.role_laundry_staff)
            4 -> getString(R.string.role_owner)
            else -> getString(R.string.role_unknown)
        }
        findViewById<TextView>(R.id.profileHeaderName).text = sessionManager.getNama()
        findViewById<TextView>(R.id.profileRoleBadge).text = roleName
        findViewById<TextView>(R.id.profileNameValue).text = sessionManager.getNama()
        findViewById<TextView>(R.id.profileUsernameValue).text = sessionManager.getUsername()
        findViewById<TextView>(R.id.profilePhoneValue).text = sessionManager.getNoHp()
        findViewById<TextView>(R.id.profileRoleValue).text = roleName
    }

    private fun showLogoutConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.logout_confirmation_title)
            .setNegativeButton(R.string.logout_cancel, null)
            .setPositiveButton(R.string.logout_confirm) { _, _ ->
                sessionManager.clearSession()
                openLoginAndClearTask()
            }
            .show()
    }

    private fun configureBottomNavigation(level: Int) {
        val navigationItems = when (level) {
            2 -> NavigationItems(
                R.string.cashier_nav_transactions,
                R.drawable.ic_orders,
                R.string.cashier_nav_customers,
                R.drawable.ic_owner_customer_data
            )
            3 -> NavigationItems(
                R.string.staff_nav_jobs,
                R.drawable.ic_orders,
                R.string.nav_history,
                R.drawable.ic_history
            )
            4 -> NavigationItems(
                R.string.owner_nav_reports,
                R.drawable.ic_owner_chart,
                R.string.owner_nav_staff,
                R.drawable.ic_owner_staff
            )
            else -> NavigationItems(
                R.string.nav_orders,
                R.drawable.ic_orders,
                R.string.nav_history,
                R.drawable.ic_history
            )
        }

        bindNavigationItem(
            R.id.profileNavSecondIcon,
            R.id.profileNavSecondLabel,
            navigationItems.secondLabel,
            navigationItems.secondIcon
        )
        bindNavigationItem(
            R.id.profileNavThirdIcon,
            R.id.profileNavThirdLabel,
            navigationItems.thirdLabel,
            navigationItems.thirdIcon
        )
    }

    private fun configureNavigationActions(level: Int) {
        val home = findViewById<View>(R.id.profileNavHome)
        val second = findViewById<View>(R.id.profileNavSecond)
        val third = findViewById<View>(R.id.profileNavThird)

        home.setOnClickListener {
            when (level) {
                2 -> navigateTo(CashierDashboardActivity::class.java)
                3 -> navigateTo(StaffDashboardActivity::class.java)
                4 -> navigateTo(DashboardOwnerActivity::class.java)
                else -> navigateTo(DashboardCustomerActivity::class.java)
            }
        }

        second.setOnClickListener(null)
        third.setOnClickListener(null)
        second.isClickable = false
        third.isClickable = false

        when (level) {
            1 -> {
                second.isClickable = true
                third.isClickable = true
                second.setOnClickListener { navigateTo(CustomerOrdersActivity::class.java) }
                third.setOnClickListener { navigateTo(CustomerHistoryActivity::class.java) }
            }
            2 -> {
                second.isClickable = true
                third.isClickable = true
                second.setOnClickListener { navigateTo(CashierTransactionActivity::class.java) }
                third.setOnClickListener { navigateTo(CashierCustomerActivity::class.java) }
            }
            3 -> {
                second.isClickable = true
                third.isClickable = true
                second.setOnClickListener { navigateTo(StaffJobsActivity::class.java) }
                third.setOnClickListener { navigateTo(StaffHistoryActivity::class.java) }
            }
            4 -> {
                second.isClickable = true
                third.isClickable = true
                second.setOnClickListener { navigateTo(OwnerReportsActivity::class.java) }
                third.setOnClickListener { navigateTo(OwnerStaffActivity::class.java) }
            }
        }
    }

    private fun navigateTo(activityClass: Class<out AppCompatActivity>) {
        openScreen(activityClass)
        finish()
    }

    private fun bindNavigationItem(iconViewId: Int, labelViewId: Int, label: Int, icon: Int) {
        findViewById<ImageView>(iconViewId).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(getColor(R.color.dashboard_nav_inactive))
            contentDescription = getString(label)
        }
        findViewById<TextView>(labelViewId).setText(label)
    }

    private fun applySystemBarInsets() {
        val header = findViewById<View>(R.id.profileHeader)
        val navigation = findViewById<View>(R.id.profileBottomNavigation)
        val headerTop = header.paddingTop
        val navigationBottom = navigation.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.profileRoot)) { _, insets ->
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

    private data class NavigationItems(
        val secondLabel: Int,
        val secondIcon: Int,
        val thirdLabel: Int,
        val thirdIcon: Int
    )

    companion object {
        const val EXTRA_LEVEL = "level"
    }

}
