package com.example.laundryku

import android.content.Intent
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
import androidx.core.widget.addTextChangedListener
import com.example.laundryku.model.OwnerStaffMember
import com.example.laundryku.model.OwnerStaffResponse
import com.example.laundryku.network.RetrofitClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class OwnerStaffActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var scroll: NestedScrollView
    private lateinit var loading: View
    private lateinit var errorState: View
    private lateinit var staffList: LinearLayout
    private lateinit var emptyState: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyDescription: TextView
    private var staffCall: Call<OwnerStaffResponse>? = null
    private var allStaff: List<OwnerStaffMember> = emptyList()
    private var searchQuery = ""
    private var selectedFilter = OwnerStaffFilter.ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = requireValidSession(4) ?: return
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_owner_staff)
        bindViews()
        applySystemBarInsets()
        bindActions()
        clearData()
    }

    override fun onResume() {
        super.onResume()
        if (::session.isInitialized) loadStaff()
    }

    private fun bindViews() {
        scroll = findViewById(R.id.ownerStaffScroll)
        loading = findViewById(R.id.ownerStaffLoading)
        errorState = findViewById(R.id.ownerStaffErrorState)
        staffList = findViewById(R.id.ownerStaffList)
        emptyState = findViewById(R.id.ownerStaffEmptyState)
        emptyTitle = findViewById(R.id.ownerStaffEmptyTitle)
        emptyDescription = findViewById(R.id.ownerStaffEmptyDescription)
    }

    private fun bindActions() {
        findViewById<View>(R.id.ownerStaffNavHome).setOnClickListener {
            openScreen(DashboardOwnerActivity::class.java)
        }
        findViewById<View>(R.id.ownerStaffNavReports).setOnClickListener {
            openScreen(OwnerReportsActivity::class.java)
        }
        findViewById<View>(R.id.ownerStaffNavProfile).setOnClickListener { openProfileForLevel(4) }
        findViewById<View>(R.id.ownerStaffAddButton).setOnClickListener {
            openScreen(AddStaffActivity::class.java)
        }
        findViewById<View>(R.id.ownerStaffRetryButton).setOnClickListener { loadStaff() }
        findViewById<TextInputEditText>(R.id.ownerStaffSearchInput)
            .addTextChangedListener { value ->
                searchQuery = value?.toString().orEmpty()
                if (loading.visibility != View.VISIBLE && errorState.visibility != View.VISIBLE) {
                    renderStaff()
                }
            }
        findViewById<View>(R.id.ownerStaffFilterAll).setOnClickListener {
            selectFilter(OwnerStaffFilter.ALL)
        }
        findViewById<View>(R.id.ownerStaffFilterCashier).setOnClickListener {
            selectFilter(OwnerStaffFilter.CASHIER)
        }
        findViewById<View>(R.id.ownerStaffFilterLaundry).setOnClickListener {
            selectFilter(OwnerStaffFilter.LAUNDRY)
        }
        updateFilterTabs()
    }

    private fun clearData() {
        listOf(
            R.id.ownerStaffCashierCount,
            R.id.ownerStaffLaundryCount,
            R.id.ownerStaffActiveCount
        ).forEach { findViewById<TextView>(it).text = "" }
        staffList.removeAllViews()
        emptyState.visibility = View.GONE
    }

    private fun loadStaff() {
        staffCall?.cancel()
        clearData()
        scroll.visibility = View.INVISIBLE
        loading.visibility = View.VISIBLE
        errorState.visibility = View.GONE
        staffCall = RetrofitClient.apiService.getOwnerStaff().also { call ->
            call.enqueue(object : Callback<OwnerStaffResponse> {
                override fun onResponse(
                    call: Call<OwnerStaffResponse>,
                    response: Response<OwnerStaffResponse>
                ) {
                    if (isFinishing || isDestroyed) return
                    loading.visibility = View.GONE
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true && body.data != null) {
                        val data = body.data
                        allStaff = data.staff
                        findViewById<TextView>(R.id.ownerStaffCashierCount).text =
                            data.summary.cashierCount.toString()
                        findViewById<TextView>(R.id.ownerStaffLaundryCount).text =
                            data.summary.laundryStaffCount.toString()
                        findViewById<TextView>(R.id.ownerStaffActiveCount).text =
                            data.summary.activeStaffCount.toString()
                        renderStaff()
                        scroll.visibility = View.VISIBLE
                    } else {
                        showLoadError()
                    }
                }

                override fun onFailure(call: Call<OwnerStaffResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    loading.visibility = View.GONE
                    showLoadError()
                }
            })
        }
    }

    private fun selectFilter(filter: OwnerStaffFilter) {
        if (selectedFilter == filter) return
        selectedFilter = filter
        updateFilterTabs()
        if (loading.visibility != View.VISIBLE && errorState.visibility != View.VISIBLE) {
            renderStaff()
        }
    }

    private fun updateFilterTabs() {
        listOf(
            R.id.ownerStaffFilterAll to OwnerStaffFilter.ALL,
            R.id.ownerStaffFilterCashier to OwnerStaffFilter.CASHIER,
            R.id.ownerStaffFilterLaundry to OwnerStaffFilter.LAUNDRY
        ).forEach { (viewId, filter) ->
            findViewById<TextView>(viewId).apply {
                val active = selectedFilter == filter
                setBackgroundResource(if (active) R.drawable.bg_order_filter_active else android.R.color.transparent)
                setTextColor(getColor(if (active) R.color.laundry_on_primary else R.color.laundry_text_secondary))
            }
        }
    }

    private fun renderStaff() {
        staffList.removeAllViews()
        val visibleStaff = OwnerStaffPresentation.filter(allStaff, searchQuery, selectedFilter)
        if (visibleStaff.isEmpty()) {
            val databaseEmpty = allStaff.isEmpty()
            emptyTitle.setText(
                if (databaseEmpty) R.string.owner_staff_empty_title
                else R.string.owner_staff_search_empty_title
            )
            emptyDescription.setText(
                if (databaseEmpty) R.string.owner_staff_empty_description
                else R.string.owner_staff_search_empty_description
            )
            emptyState.visibility = View.VISIBLE
            return
        }
        emptyState.visibility = View.GONE
        visibleStaff.forEach { member ->
            staffList.addView(
                LayoutInflater.from(this).inflate(R.layout.item_owner_staff, staffList, false).apply {
                    findViewById<TextView>(R.id.ownerStaffMemberName).text = member.nama
                    findViewById<TextView>(R.id.ownerStaffMemberUsername).text = member.username
                    findViewById<TextView>(R.id.ownerStaffMemberPhone).text = member.phone
                    findViewById<TextView>(R.id.ownerStaffMemberRole).apply {
                        text = OwnerStaffPresentation.roleLabel(member.level)
                        setBackgroundResource(
                            if (member.level == 2) R.drawable.bg_order_status_washing
                            else R.drawable.bg_order_status_packing
                        )
                        setTextColor(
                            getColor(if (member.level == 2) R.color.laundry_primary_dark else R.color.order_packing)
                        )
                    }
                    findViewById<TextView>(R.id.ownerStaffMemberStatus).apply {
                        val active = member.accountStatus == "aktif"
                        text = OwnerStaffPresentation.statusLabel(member.accountStatus)
                        setBackgroundResource(
                            if (active) R.drawable.bg_status_done else R.drawable.bg_history_cancelled
                        )
                        setTextColor(getColor(if (active) R.color.dashboard_success else R.color.history_cancelled))
                    }
                    findViewById<MaterialButton>(R.id.ownerStaffMemberDetailButton).setOnClickListener {
                        startActivity(Intent(this@OwnerStaffActivity, OwnerStaffDetailActivity::class.java).apply {
                            putExtra(OwnerStaffDetailActivity.EXTRA_STAFF_ID, member.userId)
                        })
                    }
                }
            )
        }
    }

    private fun showLoadError() {
        allStaff = emptyList()
        clearData()
        scroll.visibility = View.INVISIBLE
        errorState.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        staffCall?.cancel()
        super.onDestroy()
    }

    private fun applySystemBarInsets() {
        val header = findViewById<View>(R.id.ownerStaffHeader)
        val navigation = findViewById<View>(R.id.ownerStaffBottomNavigation)
        val headerTop = header.paddingTop
        val navigationBottom = navigation.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ownerStaffRoot)) { _, insets ->
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
