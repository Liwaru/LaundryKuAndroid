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
import com.example.laundryku.model.StaffJobData
import com.example.laundryku.model.StaffJobsResponse
import com.example.laundryku.model.UpdateLaundryStatusRequest
import com.example.laundryku.model.UpdateLaundryStatusResponse
import com.example.laundryku.network.RetrofitClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Locale

class StaffJobsActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var jobsList: LinearLayout
    private lateinit var loading: View
    private lateinit var errorState: View
    private lateinit var emptyState: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyDescription: TextView
    private lateinit var scroll: NestedScrollView

    private var jobsCall: Call<StaffJobsResponse>? = null
    private var updateCall: Call<UpdateLaundryStatusResponse>? = null
    private var allJobs: List<StaffJobData> = emptyList()
    private var activeFilter = StaffJobFilter.ALL
    private var searchQuery = ""
    private var updatingTransactionId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = requireValidSession(3) ?: return
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_staff_jobs)
        bindViews()
        applySystemBarInsets()
        bindActions()
        selectFilter(StaffJobFilter.ALL)
        loadJobs()
    }

    private fun bindViews() {
        jobsList = findViewById(R.id.staffJobsList)
        loading = findViewById(R.id.staffJobsLoading)
        errorState = findViewById(R.id.staffJobsErrorState)
        emptyState = findViewById(R.id.staffJobsEmptyState)
        emptyTitle = findViewById(R.id.staffJobsEmptyTitle)
        emptyDescription = findViewById(R.id.staffJobsEmptyDescription)
        scroll = findViewById(R.id.staffJobsScroll)
    }

    private fun bindActions() {
        findViewById<View>(R.id.staffJobsNavHome).setOnClickListener { openScreen(StaffDashboardActivity::class.java) }
        findViewById<View>(R.id.staffJobsNavHistory).setOnClickListener { openScreen(StaffHistoryActivity::class.java) }
        findViewById<View>(R.id.staffJobsNavProfile).setOnClickListener { openProfileForLevel(3) }
        findViewById<View>(R.id.staffJobsRetryButton).setOnClickListener { loadJobs() }

        filterViews().forEach { (id, filter) ->
            findViewById<View>(id).setOnClickListener { selectFilter(filter) }
        }
        findViewById<TextInputEditText>(R.id.staffJobsSearchInput).addTextChangedListener {
            searchQuery = it?.toString().orEmpty()
            if (loading.visibility != View.VISIBLE && errorState.visibility != View.VISIBLE) renderJobs()
        }
    }

    private fun loadJobs() {
        jobsCall?.cancel()
        loading.visibility = View.VISIBLE
        errorState.visibility = View.GONE
        emptyState.visibility = View.GONE
        jobsList.removeAllViews()
        jobsCall = RetrofitClient.apiService.getStaffJobs(session.getUserId()).also { call ->
            call.enqueue(object : Callback<StaffJobsResponse> {
                override fun onResponse(call: Call<StaffJobsResponse>, response: Response<StaffJobsResponse>) {
                    if (isFinishing || isDestroyed) return
                    loading.visibility = View.GONE
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true) {
                        allJobs = body.data.orEmpty()
                        renderJobs()
                    } else showLoadError(serverMessage(response))
                }

                override fun onFailure(call: Call<StaffJobsResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    loading.visibility = View.GONE
                    showLoadError(null)
                }
            })
        }
    }

    private fun showLoadError(message: String?) {
        allJobs = emptyList()
        jobsList.removeAllViews()
        emptyState.visibility = View.GONE
        errorState.visibility = View.VISIBLE
        if (!message.isNullOrBlank()) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun selectFilter(filter: StaffJobFilter) {
        activeFilter = filter
        filterViews().forEach { (id, value) ->
            findViewById<TextView>(id).apply {
                val active = filter == value
                setBackgroundResource(if (active) R.drawable.bg_order_filter_active else R.drawable.bg_staff_filter_inactive)
                setTextColor(getColor(if (active) R.color.laundry_on_primary else R.color.laundry_text_secondary))
            }
        }
        if (loading.visibility != View.VISIBLE && errorState.visibility != View.VISIBLE) {
            renderJobs()
            scroll.post { scroll.scrollTo(0, 0) }
        }
    }

    private fun renderJobs() {
        jobsList.removeAllViews()
        errorState.visibility = View.GONE
        val visibleJobs = StaffWorkflowPresentation.filterJobs(allJobs, activeFilter, searchQuery)
        if (visibleJobs.isEmpty()) {
            val empty = allJobs.isEmpty()
            emptyTitle.setText(if (empty) R.string.staff_jobs_empty_title else R.string.staff_jobs_filter_empty_title)
            emptyDescription.setText(if (empty) R.string.staff_jobs_empty_description else R.string.staff_jobs_filter_empty_description)
            emptyState.visibility = View.VISIBLE
            return
        }
        emptyState.visibility = View.GONE
        visibleJobs.forEach { jobsList.addView(createJobCard(it)) }
    }

    private fun createJobCard(job: StaffJobData): View {
        val card = LayoutInflater.from(this).inflate(R.layout.item_staff_job, jobsList, false)
        card.findViewById<TextView>(R.id.staffJobCode).text = displayCode(job.transactionCode)
        card.findViewById<TextView>(R.id.staffJobCustomer).text = job.customerName
        card.findViewById<TextView>(R.id.staffJobDetail).text = detailText(job)
        card.findViewById<TextView>(R.id.staffJobEstimate).text = formatDate(job.estimatedCompletion)
        card.findViewById<TextView>(R.id.staffJobStatus).applyStatus(job.laundryStatus)

        val canUpdate = StaffWorkflowPresentation.canUpdate(job)
        card.findViewById<MaterialButton>(R.id.staffJobUpdateButton).apply {
            visibility = if (canUpdate) View.VISIBLE else View.GONE
            isEnabled = updatingTransactionId == null
            if (canUpdate) setOnClickListener { showUpdateDialog(job) }
        }
        return card
    }

    private fun showUpdateDialog(job: StaffJobData) {
        val next = job.nextStatus ?: return
        if (updatingTransactionId != null) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.staff_update_dialog_title)
            .setMessage(getString(R.string.staff_update_dialog_message, statusLabel(job.laundryStatus), statusLabel(next)))
            .setNegativeButton(R.string.staff_update_dialog_cancel, null)
            .setPositiveButton(R.string.staff_update_dialog_confirm) { _, _ -> updateStatus(job) }
            .show()
    }

    private fun updateStatus(job: StaffJobData) {
        if (updatingTransactionId != null) return
        updatingTransactionId = job.transactionId
        renderJobs()
        updateCall = RetrofitClient.apiService.updateLaundryStatus(
            UpdateLaundryStatusRequest(session.getUserId(), job.transactionId, job.laundryStatus)
        ).also { call ->
            call.enqueue(object : Callback<UpdateLaundryStatusResponse> {
                override fun onResponse(call: Call<UpdateLaundryStatusResponse>, response: Response<UpdateLaundryStatusResponse>) {
                    if (isFinishing || isDestroyed) return
                    updatingTransactionId = null
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true) {
                        Toast.makeText(this@StaffJobsActivity, R.string.staff_update_success, Toast.LENGTH_SHORT).show()
                        loadJobs()
                    } else {
                        renderJobs()
                        Toast.makeText(this@StaffJobsActivity, serverMessage(response) ?: getString(R.string.staff_update_error), Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<UpdateLaundryStatusResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    updatingTransactionId = null
                    renderJobs()
                    Toast.makeText(this@StaffJobsActivity, R.string.staff_update_error, Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun detailText(job: StaffJobData): String {
        val details = job.details.ifEmpty { emptyList() }
        return if (details.isEmpty()) {
            getString(R.string.staff_job_detail_format, job.serviceName, StaffWorkflowPresentation.quantity(job.qty, job.satuan))
        } else details.joinToString("\n") {
            getString(R.string.staff_job_detail_format, it.serviceName, StaffWorkflowPresentation.quantity(it.qty, it.satuan))
        }
    }

    private fun TextView.applyStatus(status: String) {
        val style = statusStyle(status)
        text = getString(style.label)
        setBackgroundResource(style.background)
        setTextColor(getColor(style.textColor))
    }

    private fun formatDate(value: String?): String {
        if (value.isNullOrBlank()) return getString(R.string.staff_job_estimate_unavailable)
        return runCatching {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { isLenient = false }
            SimpleDateFormat("d MMMM yyyy", INDONESIAN_LOCALE).format(requireNotNull(parser.parse(value)))
        }.getOrElse { value }
    }

    private fun displayCode(code: String) = if (code.startsWith('#')) code else "#$code"
    private fun statusLabel(status: String) = getString(statusStyle(status).label)

    private fun statusStyle(status: String): StatusStyle = when (status) {
        "menunggu" -> StatusStyle(R.string.staff_status_waiting, R.drawable.bg_order_status_waiting, R.color.order_waiting)
        "dicuci" -> StatusStyle(R.string.staff_status_washing, R.drawable.bg_order_status_washing, R.color.laundry_primary_dark)
        "dikeringkan" -> StatusStyle(R.string.staff_status_drying, R.drawable.bg_order_status_drying, R.color.order_drying)
        "disetrika" -> StatusStyle(R.string.staff_status_ironing, R.drawable.bg_order_status_ironing, R.color.order_ironing)
        "dipacking" -> StatusStyle(R.string.staff_status_packing, R.drawable.bg_order_status_packing, R.color.order_packing)
        "siap_diambil" -> StatusStyle(R.string.staff_status_ready, R.drawable.bg_status_done, R.color.dashboard_success)
        else -> StatusStyle(R.string.staff_status_waiting, R.drawable.bg_order_status_waiting, R.color.order_waiting)
    }

    private fun filterViews() = listOf(
        R.id.staffJobsFilterAll to StaffJobFilter.ALL,
        R.id.staffJobsFilterWaiting to StaffJobFilter.WAITING,
        R.id.staffJobsFilterWashing to StaffJobFilter.WASHING,
        R.id.staffJobsFilterDrying to StaffJobFilter.DRYING,
        R.id.staffJobsFilterIroning to StaffJobFilter.IRONING,
        R.id.staffJobsFilterPacking to StaffJobFilter.PACKING,
        R.id.staffJobsFilterReady to StaffJobFilter.READY
    )

    private fun serverMessage(response: Response<*>): String? = runCatching {
        JSONObject(response.errorBody()?.string().orEmpty()).optString("message").takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun applySystemBarInsets() {
        val header = findViewById<View>(R.id.staffJobsHeader)
        val navigation = findViewById<View>(R.id.staffJobsBottomNavigation)
        val headerTop = header.paddingTop
        val navigationBottom = navigation.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.staffJobsRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.setPadding(header.paddingLeft, headerTop + bars.top, header.paddingRight, header.paddingBottom)
            navigation.setPadding(navigation.paddingLeft, navigation.paddingTop, navigation.paddingRight, navigationBottom + bars.bottom)
            insets
        }
    }

    override fun onDestroy() {
        jobsCall?.cancel()
        updateCall?.cancel()
        super.onDestroy()
    }

    private data class StatusStyle(val label: Int, val background: Int, val textColor: Int)
    companion object { private val INDONESIAN_LOCALE = Locale.forLanguageTag("id-ID") }
}
