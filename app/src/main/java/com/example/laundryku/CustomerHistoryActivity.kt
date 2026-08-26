package com.example.laundryku

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import com.example.laundryku.model.CustomerHistoryData
import com.example.laundryku.model.CustomerHistoryResponse
import com.example.laundryku.network.RetrofitClient
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class CustomerHistoryActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var historyList: LinearLayout
    private lateinit var loading: View
    private lateinit var errorState: View
    private lateinit var emptyState: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyDescription: TextView
    private lateinit var historyScroll: NestedScrollView
    private lateinit var totalLaundry: TextView
    private lateinit var totalSpending: TextView

    private var historyCall: Call<CustomerHistoryResponse>? = null
    private var allHistory: List<CustomerHistoryData> = emptyList()
    private var activeFilter = CustomerHistoryFilter.ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = requireValidSession(1) ?: return
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_customer_history)
        bindViews()
        applySystemBarInsets()
        bindActions()
        selectFilter(CustomerHistoryFilter.ALL)
        loadHistory()
    }

    private fun bindViews() {
        historyList = findViewById(R.id.customerHistoryList)
        loading = findViewById(R.id.customerHistoryLoading)
        errorState = findViewById(R.id.customerHistoryErrorState)
        emptyState = findViewById(R.id.customerHistoryEmptyState)
        emptyTitle = findViewById(R.id.customerHistoryEmptyTitle)
        emptyDescription = findViewById(R.id.customerHistoryEmptyDescription)
        historyScroll = findViewById(R.id.customerHistoryScroll)
        totalLaundry = findViewById(R.id.customerHistoryTotalLaundryValue)
        totalSpending = findViewById(R.id.customerHistoryTotalSpendingValue)
    }

    private fun bindActions() {
        findViewById<View>(R.id.customerHistoryNavHome).setOnClickListener {
            openScreen(DashboardCustomerActivity::class.java)
        }
        findViewById<View>(R.id.customerHistoryNavOrders).setOnClickListener {
            openScreen(CustomerOrdersActivity::class.java)
        }
        findViewById<View>(R.id.customerHistoryNavProfile).setOnClickListener { openProfileForLevel(1) }
        findViewById<View>(R.id.customerHistoryRetryButton).setOnClickListener { loadHistory() }
        findViewById<View>(R.id.customerHistoryFilterAll).setOnClickListener {
            selectFilter(CustomerHistoryFilter.ALL)
        }
        findViewById<View>(R.id.customerHistoryFilterCompleted).setOnClickListener {
            selectFilter(CustomerHistoryFilter.COMPLETED)
        }
        findViewById<View>(R.id.customerHistoryFilterCancelled).setOnClickListener {
            selectFilter(CustomerHistoryFilter.CANCELLED)
        }
    }

    private fun loadHistory() {
        historyCall?.cancel()
        loading.visibility = View.VISIBLE
        errorState.visibility = View.GONE
        emptyState.visibility = View.GONE
        historyList.removeAllViews()
        totalLaundry.setText(R.string.customer_history_summary_loading)
        totalSpending.setText(R.string.customer_history_summary_loading)

        historyCall = RetrofitClient.apiService.getCustomerHistory().also { call ->
            call.enqueue(object : Callback<CustomerHistoryResponse> {
                override fun onResponse(call: Call<CustomerHistoryResponse>, response: Response<CustomerHistoryResponse>) {
                    if (isFinishing || isDestroyed) return
                    loading.visibility = View.GONE
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true) {
                        allHistory = body.data.orEmpty().filter {
                            it.laundryStatus == "selesai" || it.laundryStatus == "dibatalkan"
                        }
                        updateSummary()
                        renderHistory()
                    } else {
                        showLoadError(serverMessage(response))
                    }
                }

                override fun onFailure(call: Call<CustomerHistoryResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    loading.visibility = View.GONE
                    showLoadError(null)
                }
            })
        }
    }

    private fun updateSummary() {
        val summary = CustomerHistoryPresentation.summary(allHistory)
        totalLaundry.text = getString(R.string.customer_history_total_laundry_format, summary.completedCount)
        totalSpending.text = formatCurrency(summary.totalSpending)
    }

    private fun showLoadError(message: String?) {
        allHistory = emptyList()
        historyList.removeAllViews()
        emptyState.visibility = View.GONE
        errorState.visibility = View.VISIBLE
        totalLaundry.setText(R.string.customer_history_summary_unavailable)
        totalSpending.setText(R.string.customer_history_summary_unavailable)
        if (!message.isNullOrBlank()) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun selectFilter(filter: CustomerHistoryFilter) {
        activeFilter = filter
        listOf(
            R.id.customerHistoryFilterAll to CustomerHistoryFilter.ALL,
            R.id.customerHistoryFilterCompleted to CustomerHistoryFilter.COMPLETED,
            R.id.customerHistoryFilterCancelled to CustomerHistoryFilter.CANCELLED
        ).forEach { (viewId, tabFilter) ->
            findViewById<TextView>(viewId).apply {
                val active = filter == tabFilter
                setBackgroundResource(if (active) R.drawable.bg_order_filter_active else android.R.color.transparent)
                setTextColor(getColor(if (active) R.color.laundry_on_primary else R.color.laundry_text_secondary))
            }
        }
        if (loading.visibility != View.VISIBLE && errorState.visibility != View.VISIBLE) {
            renderHistory()
            historyScroll.post { historyScroll.scrollTo(0, 0) }
        }
    }

    private fun renderHistory() {
        historyList.removeAllViews()
        errorState.visibility = View.GONE
        val visibleHistory = CustomerHistoryPresentation.filter(allHistory, activeFilter)
        if (visibleHistory.isEmpty()) {
            val noHistoryAtAll = allHistory.isEmpty()
            emptyTitle.setText(if (noHistoryAtAll) R.string.customer_history_empty_title else R.string.customer_history_filter_empty_title)
            emptyDescription.setText(if (noHistoryAtAll) R.string.customer_history_empty_description else R.string.customer_history_filter_empty_description)
            emptyState.visibility = View.VISIBLE
            return
        }

        emptyState.visibility = View.GONE
        visibleHistory.forEach { historyList.addView(createHistoryCard(it)) }
    }

    private fun createHistoryCard(item: CustomerHistoryData): View {
        val card = LayoutInflater.from(this).inflate(R.layout.item_customer_history, historyList, false)
        card.findViewById<TextView>(R.id.customerHistoryCode).text = displayCode(item.transactionCode)
        card.findViewById<TextView>(R.id.customerHistoryDate).text = formatDate(item.completionDate ?: item.orderDate)
        card.findViewById<TextView>(R.id.customerHistoryService).text = item.serviceName
        card.findViewById<TextView>(R.id.customerHistoryQuantity).text = CustomerOrderPresentation.quantity(item.qty, item.satuan)
        card.findViewById<TextView>(R.id.customerHistoryTotal).text = formatCurrency(item.totalPrice)

        card.findViewById<TextView>(R.id.customerHistoryStatus).apply {
            val completed = item.laundryStatus == "selesai"
            setText(if (completed) R.string.customer_history_completed else R.string.customer_history_cancelled)
            setBackgroundResource(if (completed) R.drawable.bg_status_done else R.drawable.bg_history_cancelled)
            setTextColor(getColor(if (completed) R.color.dashboard_success else R.color.history_cancelled))
        }
        card.findViewById<TextView>(R.id.customerHistoryPaymentStatus).apply {
            val paid = item.paymentStatus == "sudah_dibayar"
            setText(if (paid) R.string.customer_order_paid else R.string.customer_order_unpaid)
            setTextColor(getColor(if (paid) R.color.dashboard_success else R.color.order_waiting))
        }
        card.findViewById<View>(R.id.customerHistoryDetailButton).setOnClickListener {
            startActivity(Intent(this, CustomerOrderDetailActivity::class.java).apply {
                putExtra(CustomerOrderDetailActivity.EXTRA_TRANSACTION_ID, item.transactionId)
            })
        }
        return card
    }

    private fun displayCode(code: String): String =
        if (code.startsWith('#')) code else getString(R.string.customer_order_code_format, code)

    private fun formatCurrency(value: Double): String = NumberFormat.getCurrencyInstance(INDONESIAN_LOCALE).apply {
        maximumFractionDigits = if (value % 1.0 == 0.0) 0 else 2
        minimumFractionDigits = 0
    }.format(value).replace('\u00a0', ' ')

    private fun formatDate(value: String): String = runCatching {
        val parser = SimpleDateFormat(API_DATE_FORMAT, Locale.US).apply { isLenient = false }
        val formatter = SimpleDateFormat(DISPLAY_DATE_FORMAT, INDONESIAN_LOCALE)
        formatter.format(requireNotNull(parser.parse(value)))
    }.getOrElse { value }

    private fun serverMessage(response: Response<CustomerHistoryResponse>): String? = runCatching {
        JSONObject(response.errorBody()?.string().orEmpty()).optString("message").takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun applySystemBarInsets() {
        val header = findViewById<View>(R.id.customerHistoryHeader)
        val navigation = findViewById<View>(R.id.customerHistoryBottomNavigation)
        val headerTop = header.paddingTop
        val navigationBottom = navigation.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.customerHistoryRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.setPadding(header.paddingLeft, headerTop + bars.top, header.paddingRight, header.paddingBottom)
            navigation.setPadding(navigation.paddingLeft, navigation.paddingTop, navigation.paddingRight, navigationBottom + bars.bottom)
            insets
        }
    }

    override fun onDestroy() {
        historyCall?.cancel()
        super.onDestroy()
    }

    companion object {
        private val INDONESIAN_LOCALE = Locale.forLanguageTag("id-ID")
        private const val API_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"
        private const val DISPLAY_DATE_FORMAT = "d MMMM yyyy"
    }
}
