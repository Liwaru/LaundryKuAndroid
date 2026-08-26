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
import androidx.core.widget.NestedScrollView
import com.example.laundryku.model.OwnerFinancialReportItem
import com.example.laundryku.model.OwnerPopularService
import com.example.laundryku.model.OwnerReportsResponse
import com.example.laundryku.model.OwnerTransactionReportItem
import com.example.laundryku.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class OwnerReportsActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var scroll: NestedScrollView
    private lateinit var loading: View
    private lateinit var errorState: View
    private lateinit var financialList: LinearLayout
    private lateinit var transactionList: LinearLayout
    private lateinit var popularServicesList: LinearLayout
    private var reportsCall: Call<OwnerReportsResponse>? = null
    private var selectedPeriod = OwnerReportsPeriod.TODAY
    private var requestGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = requireValidSession(4) ?: return
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_owner_reports)
        bindViews()
        applySystemBarInsets()
        bindActions()
        updatePeriodTabs()
        clearReport()
    }

    override fun onResume() {
        super.onResume()
        if (::session.isInitialized) loadReports()
    }

    private fun bindViews() {
        scroll = findViewById(R.id.ownerReportsScroll)
        loading = findViewById(R.id.ownerReportsLoading)
        errorState = findViewById(R.id.ownerReportsErrorState)
        financialList = findViewById(R.id.ownerReportsFinancialList)
        transactionList = findViewById(R.id.ownerReportsTransactionList)
        popularServicesList = findViewById(R.id.ownerReportsPopularServicesList)
    }

    private fun bindActions() {
        findViewById<View>(R.id.ownerReportsNavHome).setOnClickListener {
            openScreen(DashboardOwnerActivity::class.java)
        }
        findViewById<View>(R.id.ownerReportsNavStaff).setOnClickListener {
            openScreen(OwnerStaffActivity::class.java)
        }
        findViewById<View>(R.id.ownerReportsNavProfile).setOnClickListener { openProfileForLevel(4) }
        findViewById<View>(R.id.ownerReportsRetryButton).setOnClickListener { loadReports() }
        findViewById<View>(R.id.ownerReportsPeriodToday).setOnClickListener {
            selectPeriod(OwnerReportsPeriod.TODAY)
        }
        findViewById<View>(R.id.ownerReportsPeriodWeek).setOnClickListener {
            selectPeriod(OwnerReportsPeriod.WEEK)
        }
        findViewById<View>(R.id.ownerReportsPeriodMonth).setOnClickListener {
            selectPeriod(OwnerReportsPeriod.MONTH)
        }
    }

    private fun selectPeriod(period: OwnerReportsPeriod) {
        if (selectedPeriod == period) return
        selectedPeriod = period
        updatePeriodTabs()
        loadReports()
    }

    private fun updatePeriodTabs() {
        listOf(
            R.id.ownerReportsPeriodToday to OwnerReportsPeriod.TODAY,
            R.id.ownerReportsPeriodWeek to OwnerReportsPeriod.WEEK,
            R.id.ownerReportsPeriodMonth to OwnerReportsPeriod.MONTH
        ).forEach { (viewId, period) ->
            findViewById<TextView>(viewId).apply {
                val active = selectedPeriod == period
                setBackgroundResource(if (active) R.drawable.bg_order_filter_active else android.R.color.transparent)
                setTextColor(getColor(if (active) R.color.laundry_on_primary else R.color.laundry_text_secondary))
            }
        }
    }

    private fun setPeriodTabsEnabled(enabled: Boolean) {
        listOf(
            R.id.ownerReportsPeriodToday,
            R.id.ownerReportsPeriodWeek,
            R.id.ownerReportsPeriodMonth
        ).forEach { findViewById<View>(it).isEnabled = enabled }
    }

    private fun clearReport() {
        listOf(
            R.id.ownerReportsRevenue,
            R.id.ownerReportsTotalTransactions,
            R.id.ownerReportsCompleted,
            R.id.ownerReportsCancelled,
            R.id.ownerReportsFinancialIncome,
            R.id.ownerReportsSuccessfulPayments,
            R.id.ownerReportsTransactionTotal,
            R.id.ownerReportsTransactionCompleted,
            R.id.ownerReportsTransactionActive,
            R.id.ownerReportsTransactionCancelled
        ).forEach { findViewById<TextView>(it).text = "" }
        financialList.removeAllViews()
        transactionList.removeAllViews()
        popularServicesList.removeAllViews()
        findViewById<View>(R.id.ownerReportsFinancialEmpty).visibility = View.GONE
        findViewById<View>(R.id.ownerReportsTransactionEmpty).visibility = View.GONE
    }

    private fun loadReports() {
        reportsCall?.cancel()
        requestGeneration += 1
        val generation = requestGeneration
        val requestedPeriod = selectedPeriod
        clearReport()
        scroll.visibility = View.INVISIBLE
        errorState.visibility = View.GONE
        loading.visibility = View.VISIBLE
        setPeriodTabsEnabled(false)
        reportsCall = RetrofitClient.apiService.getOwnerReports(
            requestedPeriod.apiValue
        ).also { call ->
            call.enqueue(object : Callback<OwnerReportsResponse> {
                override fun onResponse(
                    call: Call<OwnerReportsResponse>,
                    response: Response<OwnerReportsResponse>
                ) {
                    if (isFinishing || isDestroyed || generation != requestGeneration || requestedPeriod != selectedPeriod) {
                        return
                    }
                    loading.visibility = View.GONE
                    setPeriodTabsEnabled(true)
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true && body.data != null) {
                        val data = body.data
                        val summary = data.summary
                        findViewById<TextView>(R.id.ownerReportsRevenue).text =
                            OwnerReportsPresentation.currency(summary.pendapatan)
                        findViewById<TextView>(R.id.ownerReportsTotalTransactions).text =
                            summary.totalTransactions.toString()
                        findViewById<TextView>(R.id.ownerReportsCompleted).text = summary.selesai.toString()
                        findViewById<TextView>(R.id.ownerReportsCancelled).text = summary.dibatalkan.toString()
                        renderFinancialReport(data.financialReport, summary.pendapatan)
                        renderTransactionReport(data.transactionReport)
                        renderPopularServices(data.popularServices)
                        scroll.visibility = View.VISIBLE
                    } else {
                        showLoadError()
                    }
                }

                override fun onFailure(call: Call<OwnerReportsResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed || generation != requestGeneration) return
                    loading.visibility = View.GONE
                    setPeriodTabsEnabled(true)
                    showLoadError()
                }
            })
        }
    }

    private fun renderFinancialReport(items: List<OwnerFinancialReportItem>, income: Double) {
        findViewById<TextView>(R.id.ownerReportsFinancialIncome).text =
            OwnerReportsPresentation.currency(income)
        findViewById<TextView>(R.id.ownerReportsSuccessfulPayments).text = getString(
            R.string.owner_reports_successful_payments_format,
            items.size
        )
        findViewById<View>(R.id.ownerReportsFinancialEmpty).visibility =
            if (items.isEmpty()) View.VISIBLE else View.GONE
        items.forEachIndexed { index, item ->
            if (index > 0) financialList.addView(divider())
            financialList.addView(
                LayoutInflater.from(this).inflate(
                    R.layout.item_owner_financial_report,
                    financialList,
                    false
                ).apply {
                    findViewById<TextView>(R.id.ownerFinancialCode).text =
                        OwnerReportsPresentation.displayCode(item.transactionCode)
                    findViewById<TextView>(R.id.ownerFinancialCustomer).text = item.customerName
                    findViewById<TextView>(R.id.ownerFinancialAmount).text =
                        OwnerReportsPresentation.currency(item.jumlah)
                    findViewById<TextView>(R.id.ownerFinancialMethod).text =
                        OwnerReportsPresentation.paymentMethod(item.metode, item.paymentChannel)
                    findViewById<TextView>(R.id.ownerFinancialDate).text =
                        OwnerReportsPresentation.dateTime(item.paidAt)
                }
            )
        }
    }

    private fun renderTransactionReport(items: List<OwnerTransactionReportItem>) {
        findViewById<TextView>(R.id.ownerReportsTransactionTotal).text = getString(
            R.string.owner_reports_transaction_total_format,
            items.size
        )
        findViewById<TextView>(R.id.ownerReportsTransactionCompleted).text = getString(
            R.string.owner_reports_transaction_completed_format,
            items.count { it.laundryStatus == "selesai" }
        )
        findViewById<TextView>(R.id.ownerReportsTransactionActive).text = getString(
            R.string.owner_reports_transaction_active_format,
            OwnerReportsPresentation.activeTransactions(items)
        )
        findViewById<TextView>(R.id.ownerReportsTransactionCancelled).text = getString(
            R.string.owner_reports_transaction_cancelled_format,
            items.count { it.laundryStatus == "dibatalkan" }
        )
        findViewById<View>(R.id.ownerReportsTransactionEmpty).visibility =
            if (items.isEmpty()) View.VISIBLE else View.GONE
        items.forEachIndexed { index, item ->
            if (index > 0) transactionList.addView(divider())
            transactionList.addView(
                LayoutInflater.from(this).inflate(
                    R.layout.item_owner_transaction_report,
                    transactionList,
                    false
                ).apply {
                    findViewById<TextView>(R.id.ownerTransactionCode).text =
                        OwnerReportsPresentation.displayCode(item.transactionCode)
                    findViewById<TextView>(R.id.ownerTransactionCustomer).text = item.customerName
                    findViewById<TextView>(R.id.ownerTransactionService).text =
                        OwnerReportsPresentation.serviceDetail(item)
                    findViewById<TextView>(R.id.ownerTransactionTotalPrice).text =
                        OwnerReportsPresentation.currency(item.totalPrice)
                    findViewById<TextView>(R.id.ownerTransactionDate).text =
                        OwnerReportsPresentation.date(item.enteredAt)
                    findViewById<TextView>(R.id.ownerTransactionLaundryStatus).apply {
                        text = OwnerReportsPresentation.laundryStatus(item.laundryStatus)
                        setBackgroundResource(laundryStatusBackground(item.laundryStatus))
                        setTextColor(getColor(laundryStatusColor(item.laundryStatus)))
                    }
                    findViewById<TextView>(R.id.ownerTransactionPaymentStatus).apply {
                        val paid = item.paymentStatus == "sudah_dibayar"
                        text = OwnerReportsPresentation.paymentStatus(item.paymentStatus)
                        setBackgroundResource(if (paid) R.drawable.bg_status_done else R.drawable.bg_order_status_waiting)
                        setTextColor(getColor(if (paid) R.color.dashboard_success else R.color.order_waiting))
                    }
                }
            )
        }
    }

    private fun renderPopularServices(services: List<OwnerPopularService>) {
        if (services.isEmpty()) {
            popularServicesList.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(52)
                )
                gravity = android.view.Gravity.CENTER_VERTICAL
                text = getString(R.string.owner_reports_services_empty)
                setTextColor(getColor(R.color.laundry_text_secondary))
                textSize = 13f
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

    private fun laundryStatusBackground(status: String): Int = when (status) {
        "selesai" -> R.drawable.bg_status_done
        "dibatalkan" -> R.drawable.bg_history_cancelled
        "menunggu" -> R.drawable.bg_order_status_waiting
        "dipacking" -> R.drawable.bg_order_status_packing
        else -> R.drawable.bg_order_status_washing
    }

    private fun laundryStatusColor(status: String): Int = when (status) {
        "selesai" -> R.color.dashboard_success
        "dibatalkan" -> R.color.history_cancelled
        "menunggu" -> R.color.order_waiting
        "dipacking" -> R.color.order_packing
        else -> R.color.laundry_primary_dark
    }

    private fun divider() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        setBackgroundColor(getColor(R.color.dashboard_divider))
    }

    private fun showLoadError() {
        clearReport()
        scroll.visibility = View.INVISIBLE
        errorState.visibility = View.VISIBLE
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        requestGeneration += 1
        reportsCall?.cancel()
        super.onDestroy()
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
