package com.example.laundryku

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.laundryku.model.StaffDashboardResponse
import com.example.laundryku.model.StaffJobData
import com.example.laundryku.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class StaffDashboardActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private var dashboardCall: Call<StaffDashboardResponse>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = requireValidSession(3) ?: return
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_staff_dashboard)
        applySystemBarInsets()
        findViewById<TextView>(R.id.staffGreetingText).text =
            getString(R.string.dashboard_greeting_format, session.getNama())
        findViewById<View>(R.id.staffNavJobs).setOnClickListener { openScreen(StaffJobsActivity::class.java) }
        findViewById<View>(R.id.staffNavHistory).setOnClickListener { openScreen(StaffHistoryActivity::class.java) }
        findViewById<View>(R.id.staffNavProfile).setOnClickListener { openProfileForLevel(3) }
        findViewById<View>(R.id.staffUpdateStatusButton).setOnClickListener { openScreen(StaffJobsActivity::class.java) }
        findViewById<View>(R.id.staffSeeAllJobs).setOnClickListener { openScreen(StaffJobsActivity::class.java) }
        clearDashboard()
        loadDashboard()
    }

    override fun onResume() {
        super.onResume()
        if (::session.isInitialized) {
            findViewById<TextView>(R.id.staffGreetingText).text =
                getString(R.string.dashboard_greeting_format, session.getNama())
        }
    }

    private fun clearDashboard() {
        listOf(
            R.id.staffCountWaiting,
            R.id.staffCountWashing,
            R.id.staffCountDrying,
            R.id.staffCountIroning,
            R.id.staffCountPacking,
            R.id.staffCountReady
        ).forEach { findViewById<TextView>(it).text = "–" }
        findViewById<View>(R.id.staffNextJobCard).visibility = View.GONE
    }

    private fun loadDashboard() {
        dashboardCall?.cancel()
        dashboardCall = RetrofitClient.apiService.getStaffDashboard().also { call ->
            call.enqueue(object : Callback<StaffDashboardResponse> {
                override fun onResponse(call: Call<StaffDashboardResponse>, response: Response<StaffDashboardResponse>) {
                    if (isFinishing || isDestroyed) return
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true && body.data != null) {
                        val summary = body.data.summary
                        findViewById<TextView>(R.id.staffCountWaiting).text = summary.menunggu.toString()
                        findViewById<TextView>(R.id.staffCountWashing).text = summary.dicuci.toString()
                        findViewById<TextView>(R.id.staffCountDrying).text = summary.dikeringkan.toString()
                        findViewById<TextView>(R.id.staffCountIroning).text = summary.disetrika.toString()
                        findViewById<TextView>(R.id.staffCountPacking).text = summary.dipacking.toString()
                        findViewById<TextView>(R.id.staffCountReady).text = summary.ready.toString()
                        renderNextJob(body.data.nextJob)
                    } else showDashboardError()
                }

                override fun onFailure(call: Call<StaffDashboardResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    showDashboardError()
                }
            })
        }
    }

    private fun renderNextJob(job: StaffJobData?) {
        findViewById<View>(R.id.staffNextJobCard).visibility = if (job == null) View.GONE else View.VISIBLE
        if (job == null) return
        findViewById<TextView>(R.id.staffNextJobCode).text = if (job.transactionCode.startsWith('#')) job.transactionCode else "#${job.transactionCode}"
        findViewById<TextView>(R.id.staffNextJobCustomer).text = job.customerName
        findViewById<TextView>(R.id.staffNextJobDetail).text = if (job.details.isEmpty()) {
            getString(R.string.staff_job_detail_format, job.serviceName, StaffWorkflowPresentation.quantity(job.qty, job.satuan))
        } else job.details.joinToString("\n") {
            getString(R.string.staff_job_detail_format, it.serviceName, StaffWorkflowPresentation.quantity(it.qty, it.satuan))
        }
        findViewById<TextView>(R.id.staffNextJobStatus).apply {
            setText(statusLabel(job.laundryStatus))
            val waiting = job.laundryStatus == "menunggu"
            setBackgroundResource(if (waiting) R.drawable.bg_order_status_waiting else R.drawable.bg_order_status_washing)
            setTextColor(getColor(if (waiting) R.color.order_waiting else R.color.laundry_primary_dark))
        }
    }

    private fun statusLabel(status: String): Int = when (status) {
        "menunggu" -> R.string.staff_status_waiting
        "dicuci" -> R.string.staff_status_washing
        "dikeringkan" -> R.string.staff_status_drying
        "disetrika" -> R.string.staff_status_ironing
        "dipacking" -> R.string.staff_status_packing
        else -> R.string.staff_status_ready
    }

    private fun showDashboardError() {
        clearDashboard()
        Toast.makeText(this, R.string.staff_dashboard_load_error, Toast.LENGTH_LONG).show()
    }

    private fun applySystemBarInsets() {
        val header = findViewById<View>(R.id.staffDashboardHeader)
        val bottomNavigation = findViewById<View>(R.id.staffBottomNavigation)
        val headerTop = header.paddingTop
        val navigationBottom = bottomNavigation.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.staffDashboardRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.setPadding(header.paddingLeft, headerTop + bars.top, header.paddingRight, header.paddingBottom)
            bottomNavigation.setPadding(bottomNavigation.paddingLeft, bottomNavigation.paddingTop, bottomNavigation.paddingRight, navigationBottom + bars.bottom)
            insets
        }
    }

    override fun onDestroy() {
        dashboardCall?.cancel()
        super.onDestroy()
    }
}
