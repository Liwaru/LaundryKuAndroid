package com.example.laundryku

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.laundryku.model.OwnerDashboardResponse
import com.example.laundryku.model.OwnerPopularService
import com.example.laundryku.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DashboardOwnerActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var scroll: View
    private lateinit var loading: View
    private lateinit var errorState: View
    private lateinit var popularServicesList: LinearLayout
    private var dashboardCall: Call<OwnerDashboardResponse>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = requireValidSession(4) ?: return
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_dashboard_owner)
        bindViews()
        applySystemBarInsets()
        findViewById<TextView>(R.id.ownerGreetingText).text =
            getString(R.string.dashboard_greeting_format, session.getNama())
        findViewById<View>(R.id.ownerNavReports).setOnClickListener { openScreen(OwnerReportsActivity::class.java) }
        findViewById<View>(R.id.ownerFinancialReportsMenu).setOnClickListener {
            openScreen(OwnerReportsActivity::class.java)
        }
        findViewById<View>(R.id.ownerTransactionReportsMenu).setOnClickListener {
            openScreen(OwnerReportsActivity::class.java)
        }
        findViewById<View>(R.id.ownerNavStaff).setOnClickListener { openScreen(OwnerStaffActivity::class.java) }
        findViewById<View>(R.id.ownerNavProfile).setOnClickListener { openProfileForLevel(4) }
        findViewById<View>(R.id.ownerDashboardRetryButton).setOnClickListener { loadDashboard() }
        clearDashboard()
    }

    override fun onResume() {
        super.onResume()
        if (::session.isInitialized) loadDashboard()
    }

    private fun bindViews() {
        scroll = findViewById(R.id.ownerDashboardScroll)
        loading = findViewById(R.id.ownerDashboardLoading)
        errorState = findViewById(R.id.ownerDashboardErrorState)
        popularServicesList = findViewById(R.id.ownerPopularServicesList)
    }

    private fun clearDashboard() {
        listOf(
            R.id.ownerRevenueToday,
            R.id.ownerTransactionsToday,
            R.id.ownerActiveOrders,
            R.id.ownerTotalCustomers,
            R.id.ownerWashingCount,
            R.id.ownerDryingCount,
            R.id.ownerIroningCount,
            R.id.ownerPackingCount,
            R.id.ownerReadyCount
        ).forEach { findViewById<TextView>(it).text = "" }
        popularServicesList.removeAllViews()
    }

    private fun loadDashboard() {
        dashboardCall?.cancel()
        clearDashboard()
        scroll.visibility = View.INVISIBLE
        errorState.visibility = View.GONE
        loading.visibility = View.VISIBLE
        dashboardCall = RetrofitClient.apiService.getOwnerDashboard().also { call ->
            call.enqueue(object : Callback<OwnerDashboardResponse> {
                override fun onResponse(
                    call: Call<OwnerDashboardResponse>,
                    response: Response<OwnerDashboardResponse>
                ) {
                    if (isFinishing || isDestroyed) return
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true && body.data != null) {
                        val data = body.data
                        val summary = data.summary
                        findViewById<TextView>(R.id.ownerRevenueToday).text =
                            OwnerDashboardPresentation.currency(summary.incomeToday)
                        findViewById<TextView>(R.id.ownerTransactionsToday).text =
                            summary.transactionsToday.toString()
                        findViewById<TextView>(R.id.ownerActiveOrders).text =
                            summary.activeOrders.toString()
                        findViewById<TextView>(R.id.ownerTotalCustomers).text =
                            summary.totalCustomers.toString()
                        renderPopularServices(data.popularServices)
                        val status = data.operationalStatus
                        val counts = OwnerDashboardPresentation.operationalCounts(
                            status.dicuci,
                            status.dikeringkan,
                            status.disetrika,
                            status.dipacking,
                            status.readyForPickup
                        )
                        listOf(
                            R.id.ownerWashingCount,
                            R.id.ownerDryingCount,
                            R.id.ownerIroningCount,
                            R.id.ownerPackingCount,
                            R.id.ownerReadyCount
                        ).forEachIndexed { index, viewId ->
                            findViewById<TextView>(viewId).text = counts[index].toString()
                        }
                        loading.visibility = View.GONE
                        scroll.visibility = View.VISIBLE
                    } else {
                        showDashboardError()
                    }
                }

                override fun onFailure(call: Call<OwnerDashboardResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    showDashboardError()
                }
            })
        }
    }

    private fun renderPopularServices(services: List<OwnerPopularService>) {
        popularServicesList.removeAllViews()
        if (services.isEmpty()) {
            popularServicesList.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(52)
                )
                gravity = android.view.Gravity.CENTER_VERTICAL
                text = getString(R.string.owner_dashboard_empty_services)
                setTextColor(getColor(R.color.laundry_text_secondary))
                textSize = 14f
            })
            return
        }
        services.forEachIndexed { index, service ->
            if (index > 0) popularServicesList.addView(divider())
            popularServicesList.addView(
                LayoutInflater.from(this).inflate(
                    R.layout.item_owner_popular_service,
                    popularServicesList,
                    false
                ).apply {
                    findViewById<TextView>(R.id.ownerPopularServiceRank).text = (index + 1).toString()
                    findViewById<TextView>(R.id.ownerPopularServiceName).text = service.serviceName
                    findViewById<TextView>(R.id.ownerPopularServiceCount).text =
                        OwnerDashboardPresentation.serviceOrderCount(service)
                }
            )
        }
    }

    private fun divider() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(1)
        )
        setBackgroundColor(getColor(R.color.dashboard_divider))
    }

    private fun showDashboardError() {
        clearDashboard()
        loading.visibility = View.GONE
        scroll.visibility = View.INVISIBLE
        errorState.visibility = View.VISIBLE
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        dashboardCall?.cancel()
        super.onDestroy()
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
