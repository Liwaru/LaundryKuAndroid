package com.example.laundryku

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.laundryku.model.CashierDashboardResponse
import com.example.laundryku.model.CashierDashboardTransaction
import com.example.laundryku.network.RetrofitClient
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class CashierDashboardActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var scroll: View
    private lateinit var loading: View
    private lateinit var errorState: View
    private lateinit var recentList: LinearLayout
    private lateinit var recentEmpty: View
    private lateinit var readyList: LinearLayout
    private var dashboardCall: Call<CashierDashboardResponse>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = requireValidSession(2) ?: return
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_cashier_dashboard)
        bindViews()
        applySystemBarInsets()
        findViewById<TextView>(R.id.cashierGreetingText).text =
            getString(R.string.dashboard_greeting_format, session.getNama())
        findViewById<View>(R.id.cashierNavTransactions).setOnClickListener { openScreen(CashierTransactionActivity::class.java) }
        findViewById<View>(R.id.cashierNavCustomers).setOnClickListener { openScreen(CashierCustomerActivity::class.java) }
        findViewById<View>(R.id.cashierNavProfile).setOnClickListener { openProfileForLevel(2) }
        findViewById<View>(R.id.cashierCustomerDataAction).setOnClickListener {
            openScreen(CashierCustomerActivity::class.java)
        }
        findViewById<View>(R.id.cashierPaymentAction).setOnClickListener {
            openScreen(CashierTransactionActivity::class.java)
        }
        findViewById<View>(R.id.cashierSeeAllTransactions).setOnClickListener { openScreen(CashierTransactionActivity::class.java) }
        findViewById<View>(R.id.cashierDashboardRetryButton).setOnClickListener { loadDashboard() }
        clearDashboard()
    }

    override fun onResume() {
        super.onResume()
        if (::session.isInitialized) {
            findViewById<TextView>(R.id.cashierGreetingText).text =
                getString(R.string.dashboard_greeting_format, session.getNama())
            loadDashboard()
        }
    }

    private fun bindViews() {
        scroll = findViewById(R.id.cashierDashboardScroll)
        loading = findViewById(R.id.cashierDashboardLoading)
        errorState = findViewById(R.id.cashierDashboardErrorState)
        recentList = findViewById(R.id.cashierRecentTransactionsList)
        recentEmpty = findViewById(R.id.cashierRecentTransactionsEmpty)
        readyList = findViewById(R.id.cashierReadyOrdersList)
    }

    private fun clearDashboard() {
        findViewById<TextView>(R.id.cashierDashboardErrorMessage)
            .setText(R.string.cashier_dashboard_load_error)
        listOf(
            R.id.cashierCountActive,
            R.id.cashierCountUnpaid,
            R.id.cashierCountReady,
            R.id.cashierIncomeToday
        ).forEach { findViewById<TextView>(it).text = "" }
        recentList.removeAllViews()
        readyList.removeAllViews()
        recentEmpty.visibility = View.GONE
        findViewById<View>(R.id.cashierReadyOrdersTitle).visibility = View.GONE
        findViewById<View>(R.id.cashierReadyOrdersCard).visibility = View.GONE
    }

    private fun loadDashboard() {
        dashboardCall?.cancel()
        clearDashboard()
        scroll.visibility = View.INVISIBLE
        errorState.visibility = View.GONE
        loading.visibility = View.VISIBLE
        dashboardCall = RetrofitClient.apiService.getCashierDashboard().also { call ->
            call.enqueue(object : Callback<CashierDashboardResponse> {
                override fun onResponse(
                    call: Call<CashierDashboardResponse>,
                    response: Response<CashierDashboardResponse>
                ) {
                    if (isFinishing || isDestroyed) return
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true && body.data != null) {
                        loading.visibility = View.GONE
                        val summary = body.data.summary
                        findViewById<TextView>(R.id.cashierCountActive).text = summary.activeOrders.toString()
                        findViewById<TextView>(R.id.cashierCountUnpaid).text = summary.unpaid.toString()
                        findViewById<TextView>(R.id.cashierCountReady).text = summary.ready.toString()
                        findViewById<TextView>(R.id.cashierIncomeToday).text =
                            CashierDashboardPresentation.currency(summary.incomeToday)
                        renderRecentTransactions(body.data.recentTransactions)
                        renderReadyTransactions(body.data.readyTransactions)
                        scroll.visibility = View.VISIBLE
                    } else {
                        showDashboardError(serverMessage(response))
                    }
                }

                override fun onFailure(call: Call<CashierDashboardResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    showDashboardError(null)
                }
            })
        }
    }

    private fun renderRecentTransactions(transactions: List<CashierDashboardTransaction>) {
        recentList.removeAllViews()
        recentEmpty.visibility = if (transactions.isEmpty()) View.VISIBLE else View.GONE
        transactions.forEachIndexed { index, transaction ->
            if (index > 0) recentList.addView(divider())
            recentList.addView(
                LayoutInflater.from(this).inflate(
                    R.layout.item_cashier_dashboard_transaction,
                    recentList,
                    false
                ).apply {
                    findViewById<TextView>(R.id.cashierDashboardTransactionCode).text =
                        CashierDashboardPresentation.displayCode(transaction.transactionCode)
                    findViewById<TextView>(R.id.cashierDashboardTransactionCustomer).text = transaction.customerName
                    findViewById<TextView>(R.id.cashierDashboardTransactionDetail).text =
                        CashierDashboardPresentation.serviceDetail(transaction)
                    findViewById<TextView>(R.id.cashierDashboardTransactionTotal).text =
                        CashierDashboardPresentation.currency(transaction.totalPrice)
                    findViewById<TextView>(R.id.cashierDashboardTransactionStatus).apply {
                        text = getString(
                            R.string.cashier_dashboard_status_format,
                            CashierDashboardPresentation.paymentLabel(transaction.paymentStatus),
                            CashierDashboardPresentation.statusLabel(transaction.laundryStatus)
                        )
                        setTextColor(getColor(statusColor(transaction.laundryStatus, transaction.paymentStatus)))
                    }
                }
            )
        }
    }

    private fun renderReadyTransactions(transactions: List<CashierDashboardTransaction>) {
        readyList.removeAllViews()
        val visible = transactions.isNotEmpty()
        findViewById<View>(R.id.cashierReadyOrdersTitle).visibility = if (visible) View.VISIBLE else View.GONE
        findViewById<View>(R.id.cashierReadyOrdersCard).visibility = if (visible) View.VISIBLE else View.GONE
        transactions.forEachIndexed { index, transaction ->
            if (index > 0) readyList.addView(divider())
            readyList.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(52)
                )
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(16), 0)
                text = getString(
                    R.string.cashier_dashboard_ready_format,
                    CashierDashboardPresentation.displayCode(transaction.transactionCode),
                    transaction.customerName
                )
                setTextColor(getColor(R.color.laundry_text_primary))
                textSize = 14f
            })
        }
    }

    private fun divider() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
            marginStart = dp(16)
            marginEnd = dp(16)
        }
        setBackgroundColor(getColor(R.color.dashboard_divider))
    }

    private fun statusColor(laundryStatus: String, paymentStatus: String): Int = when {
        laundryStatus == "dibatalkan" -> R.color.history_cancelled
        laundryStatus == "selesai" || laundryStatus == "siap_diambil" -> R.color.dashboard_success
        paymentStatus == "belum_dibayar" || laundryStatus == "menunggu" -> R.color.order_waiting
        else -> R.color.laundry_primary_dark
    }

    private fun showDashboardError(message: String?) {
        loading.visibility = View.GONE
        scroll.visibility = View.INVISIBLE
        errorState.visibility = View.VISIBLE
        if (!message.isNullOrBlank()) {
            findViewById<TextView>(R.id.cashierDashboardErrorMessage).text = message
        }
    }

    private fun serverMessage(response: Response<*>): String? = runCatching {
        JSONObject(response.errorBody()?.string().orEmpty()).optString("message").takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        dashboardCall?.cancel()
        super.onDestroy()
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
