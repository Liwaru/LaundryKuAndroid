package com.example.laundryku

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
import androidx.core.widget.addTextChangedListener
import com.example.laundryku.model.StaffHistoryData
import com.example.laundryku.model.StaffHistoryResponse
import com.example.laundryku.network.RetrofitClient
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Locale

class StaffHistoryActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var historyList: LinearLayout
    private lateinit var loading: View
    private lateinit var errorState: View
    private lateinit var emptyState: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyDescription: TextView
    private lateinit var scroll: NestedScrollView
    private var historyCall: Call<StaffHistoryResponse>? = null
    private var allHistory: List<StaffHistoryData> = emptyList()
    private var activeFilter = StaffHistoryFilter.ALL
    private var searchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = requireValidSession(3) ?: return
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_staff_history)
        bindViews()
        applySystemBarInsets()
        bindActions()
        selectFilter(StaffHistoryFilter.ALL)
        loadHistory()
    }

    private fun bindViews() {
        historyList = findViewById(R.id.staffHistoryList)
        loading = findViewById(R.id.staffHistoryLoading)
        errorState = findViewById(R.id.staffHistoryErrorState)
        emptyState = findViewById(R.id.staffHistoryEmptyState)
        emptyTitle = findViewById(R.id.staffHistoryEmptyTitle)
        emptyDescription = findViewById(R.id.staffHistoryEmptyDescription)
        scroll = findViewById(R.id.staffHistoryScroll)
    }

    private fun bindActions() {
        findViewById<View>(R.id.staffHistoryNavHome).setOnClickListener { openScreen(StaffDashboardActivity::class.java) }
        findViewById<View>(R.id.staffHistoryNavJobs).setOnClickListener { openScreen(StaffJobsActivity::class.java) }
        findViewById<View>(R.id.staffHistoryNavProfile).setOnClickListener { openProfileForLevel(3) }
        findViewById<View>(R.id.staffHistoryRetryButton).setOnClickListener { loadHistory() }
        filterViews().forEach { (id, filter) -> findViewById<View>(id).setOnClickListener { selectFilter(filter) } }
        findViewById<TextInputEditText>(R.id.staffHistorySearchInput).addTextChangedListener {
            searchQuery = it?.toString().orEmpty()
            if (loading.visibility != View.VISIBLE && errorState.visibility != View.VISIBLE) renderHistory()
        }
    }

    private fun loadHistory() {
        historyCall?.cancel()
        loading.visibility = View.VISIBLE
        errorState.visibility = View.GONE
        emptyState.visibility = View.GONE
        historyList.removeAllViews()
        historyCall = RetrofitClient.apiService.getStaffHistory(session.getUserId()).also { call ->
            call.enqueue(object : Callback<StaffHistoryResponse> {
                override fun onResponse(call: Call<StaffHistoryResponse>, response: Response<StaffHistoryResponse>) {
                    if (isFinishing || isDestroyed) return
                    loading.visibility = View.GONE
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true) {
                        allHistory = body.data.orEmpty()
                        renderHistory()
                    } else showLoadError(serverMessage(response))
                }

                override fun onFailure(call: Call<StaffHistoryResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    loading.visibility = View.GONE
                    showLoadError(null)
                }
            })
        }
    }

    private fun showLoadError(message: String?) {
        allHistory = emptyList()
        historyList.removeAllViews()
        emptyState.visibility = View.GONE
        errorState.visibility = View.VISIBLE
        if (!message.isNullOrBlank()) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun selectFilter(filter: StaffHistoryFilter) {
        activeFilter = filter
        filterViews().forEach { (id, value) ->
            findViewById<TextView>(id).apply {
                val active = filter == value
                setBackgroundResource(if (active) R.drawable.bg_order_filter_active else android.R.color.transparent)
                setTextColor(getColor(if (active) R.color.laundry_on_primary else R.color.laundry_text_secondary))
            }
        }
        if (loading.visibility != View.VISIBLE && errorState.visibility != View.VISIBLE) {
            renderHistory()
            scroll.post { scroll.scrollTo(0, 0) }
        }
    }

    private fun renderHistory() {
        historyList.removeAllViews()
        errorState.visibility = View.GONE
        val visible = StaffWorkflowPresentation.filterHistory(allHistory, activeFilter, searchQuery)
        if (visible.isEmpty()) {
            val empty = allHistory.isEmpty()
            emptyTitle.setText(if (empty) R.string.staff_history_empty_title else R.string.staff_history_filter_empty_title)
            emptyDescription.setText(if (empty) R.string.staff_history_empty_description else R.string.staff_history_filter_empty_description)
            emptyState.visibility = View.VISIBLE
            return
        }
        emptyState.visibility = View.GONE
        visible.forEach { historyList.addView(createHistoryCard(it)) }
    }

    private fun createHistoryCard(item: StaffHistoryData): View {
        val card = LayoutInflater.from(this).inflate(R.layout.item_staff_history, historyList, false)
        card.findViewById<TextView>(R.id.staffHistoryCode).text = if (item.transactionCode.startsWith('#')) item.transactionCode else "#${item.transactionCode}"
        card.findViewById<TextView>(R.id.staffHistoryCustomer).text = item.customerName
        card.findViewById<TextView>(R.id.staffHistoryDetail).text = detailText(item)
        card.findViewById<TextView>(R.id.staffHistoryDate).text = formatDate(item.lastUpdated)
        card.findViewById<TextView>(R.id.staffHistoryStatus).apply {
            val completed = item.laundryStatus != "dibatalkan"
            setText(
                when (item.laundryStatus) {
                    "siap_diambil" -> R.string.staff_status_ready
                    "selesai" -> R.string.customer_history_completed
                    else -> R.string.customer_history_cancelled
                }
            )
            setBackgroundResource(if (completed) R.drawable.bg_status_done else R.drawable.bg_history_cancelled)
            setTextColor(getColor(if (completed) R.color.dashboard_success else R.color.history_cancelled))
        }
        return card
    }

    private fun detailText(item: StaffHistoryData): String {
        if (item.details.isEmpty()) {
            return getString(R.string.staff_job_detail_format, item.serviceName, StaffWorkflowPresentation.quantity(item.qty, item.satuan))
        }
        return item.details.joinToString("\n") {
            getString(R.string.staff_job_detail_format, it.serviceName, StaffWorkflowPresentation.quantity(it.qty, it.satuan))
        }
    }

    private fun formatDate(value: String?): String {
        if (value.isNullOrBlank()) return getString(R.string.staff_job_estimate_unavailable)
        return runCatching {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { isLenient = false }
            SimpleDateFormat("d MMMM yyyy, HH:mm", INDONESIAN_LOCALE).format(requireNotNull(parser.parse(value)))
        }.getOrElse { value }
    }

    private fun filterViews() = listOf(
        R.id.staffHistoryFilterAll to StaffHistoryFilter.ALL,
        R.id.staffHistoryFilterReady to StaffHistoryFilter.READY,
        R.id.staffHistoryFilterCompleted to StaffHistoryFilter.COMPLETED,
        R.id.staffHistoryFilterCancelled to StaffHistoryFilter.CANCELLED
    )

    private fun serverMessage(response: Response<*>): String? = runCatching {
        JSONObject(response.errorBody()?.string().orEmpty()).optString("message").takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun applySystemBarInsets() {
        val header = findViewById<View>(R.id.staffHistoryHeader)
        val navigation = findViewById<View>(R.id.staffHistoryBottomNavigation)
        val headerTop = header.paddingTop
        val navigationBottom = navigation.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.staffHistoryRoot)) { _, insets ->
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

    companion object { private val INDONESIAN_LOCALE = Locale.forLanguageTag("id-ID") }
}
