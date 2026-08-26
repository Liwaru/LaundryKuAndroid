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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import com.example.laundryku.model.CustomerOrderData
import com.example.laundryku.model.CustomerOrdersResponse
import com.example.laundryku.network.RetrofitClient
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class CustomerOrdersActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var ordersList: LinearLayout
    private lateinit var loading: View
    private lateinit var errorState: View
    private lateinit var emptyState: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyDescription: TextView
    private lateinit var ordersScroll: NestedScrollView

    private var ordersCall: Call<CustomerOrdersResponse>? = null
    private var allOrders: List<CustomerOrderData> = emptyList()
    private var activeFilter = CustomerOrderFilter.ALL

    private val createOrderLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) loadOrders()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = requireValidSession(1) ?: return
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_customer_orders)
        bindViews()
        applySystemBarInsets()
        bindActions()
        selectFilter(CustomerOrderFilter.ALL)
        loadOrders()
    }

    private fun bindViews() {
        ordersList = findViewById(R.id.customerOrdersList)
        loading = findViewById(R.id.customerOrdersLoading)
        errorState = findViewById(R.id.customerOrdersErrorState)
        emptyState = findViewById(R.id.customerOrdersEmptyState)
        emptyTitle = findViewById(R.id.customerOrdersEmptyTitle)
        emptyDescription = findViewById(R.id.customerOrdersEmptyDescription)
        ordersScroll = findViewById(R.id.customerOrdersScroll)
    }

    private fun bindActions() {
        findViewById<View>(R.id.customerOrdersNavHome).setOnClickListener {
            openScreen(DashboardCustomerActivity::class.java)
        }
        findViewById<View>(R.id.customerOrdersNavHistory).setOnClickListener {
            openScreen(CustomerHistoryActivity::class.java)
        }
        findViewById<View>(R.id.customerOrdersNavProfile).setOnClickListener { openProfileForLevel(1) }
        findViewById<View>(R.id.customerCreateOrderButton).setOnClickListener {
            createOrderLauncher.launch(Intent(this, CreateOrderActivity::class.java))
        }
        findViewById<View>(R.id.customerOrdersRetryButton).setOnClickListener { loadOrders() }
        findViewById<View>(R.id.customerOrdersFilterAll).setOnClickListener {
            selectFilter(CustomerOrderFilter.ALL)
        }
        findViewById<View>(R.id.customerOrdersFilterProcessing).setOnClickListener {
            selectFilter(CustomerOrderFilter.PROCESSING)
        }
        findViewById<View>(R.id.customerOrdersFilterReady).setOnClickListener {
            selectFilter(CustomerOrderFilter.READY)
        }
    }

    private fun loadOrders() {
        ordersCall?.cancel()
        loading.visibility = View.VISIBLE
        errorState.visibility = View.GONE
        emptyState.visibility = View.GONE
        ordersList.removeAllViews()

        ordersCall = RetrofitClient.apiService.getCustomerOrders().also { call ->
            call.enqueue(object : Callback<CustomerOrdersResponse> {
                override fun onResponse(
                    call: Call<CustomerOrdersResponse>,
                    response: Response<CustomerOrdersResponse>
                ) {
                    if (isFinishing || isDestroyed) return
                    loading.visibility = View.GONE
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true) {
                        allOrders = body.data.orEmpty()
                        renderOrders()
                    } else {
                        showLoadError(serverMessage(response))
                    }
                }

                override fun onFailure(call: Call<CustomerOrdersResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    loading.visibility = View.GONE
                    showLoadError(null)
                }
            })
        }
    }

    private fun showLoadError(message: String?) {
        allOrders = emptyList()
        ordersList.removeAllViews()
        emptyState.visibility = View.GONE
        errorState.visibility = View.VISIBLE
        if (!message.isNullOrBlank()) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun selectFilter(filter: CustomerOrderFilter) {
        activeFilter = filter
        listOf(
            R.id.customerOrdersFilterAll to CustomerOrderFilter.ALL,
            R.id.customerOrdersFilterProcessing to CustomerOrderFilter.PROCESSING,
            R.id.customerOrdersFilterReady to CustomerOrderFilter.READY
        ).forEach { (viewId, tabFilter) ->
            findViewById<TextView>(viewId).apply {
                val active = filter == tabFilter
                setBackgroundResource(if (active) R.drawable.bg_order_filter_active else android.R.color.transparent)
                setTextColor(getColor(if (active) R.color.laundry_on_primary else R.color.laundry_text_secondary))
            }
        }
        if (loading.visibility != View.VISIBLE && errorState.visibility != View.VISIBLE) {
            renderOrders()
            ordersScroll.post { ordersScroll.scrollTo(0, 0) }
        }
    }

    private fun renderOrders() {
        ordersList.removeAllViews()
        errorState.visibility = View.GONE
        val visibleOrders = CustomerOrderPresentation.filter(allOrders, activeFilter)
        if (visibleOrders.isEmpty()) {
            emptyTitle.setText(
                if (allOrders.isEmpty()) R.string.customer_orders_empty_title
                else R.string.customer_orders_filter_empty_title
            )
            emptyDescription.setText(
                if (allOrders.isEmpty()) R.string.customer_orders_empty_description
                else R.string.customer_orders_filter_empty_description
            )
            emptyState.visibility = View.VISIBLE
            return
        }

        emptyState.visibility = View.GONE
        visibleOrders.forEach { order -> ordersList.addView(createOrderCard(order)) }
    }

    private fun createOrderCard(order: CustomerOrderData): View {
        val card = LayoutInflater.from(this).inflate(R.layout.item_customer_order, ordersList, false)
        card.findViewById<TextView>(R.id.customerOrderCode).text = displayCode(order.transactionCode)
        card.findViewById<TextView>(R.id.customerOrderService).text = order.serviceName
        card.findViewById<TextView>(R.id.customerOrderQuantity).text =
            CustomerOrderPresentation.quantity(order.qty, order.satuan)
        card.findViewById<TextView>(R.id.customerOrderTotal).text = formatCurrency(order.totalPrice)
        card.findViewById<TextView>(R.id.customerOrderEstimate).text = formatEstimate(order.estimatedCompletion)

        card.findViewById<TextView>(R.id.customerOrderLaundryStatus).apply {
            statusStyle(order.laundryStatus).also { style ->
                setText(style.label)
                setBackgroundResource(style.background)
                setTextColor(getColor(style.textColor))
            }
        }
        card.findViewById<TextView>(R.id.customerOrderPaymentStatus).apply {
            val paid = order.paymentStatus == "sudah_dibayar"
            setText(if (paid) R.string.customer_order_paid else R.string.customer_order_unpaid)
            setBackgroundResource(if (paid) R.drawable.bg_status_done else R.drawable.bg_order_status_waiting)
            setTextColor(getColor(if (paid) R.color.dashboard_success else R.color.order_waiting))
        }
        card.findViewById<View>(R.id.customerOrderDetailButton).setOnClickListener {
            startActivity(Intent(this, CustomerOrderDetailActivity::class.java).apply {
                putExtra(CustomerOrderDetailActivity.EXTRA_TRANSACTION_ID, order.transactionId)
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

    private fun formatEstimate(value: String?): String {
        if (value.isNullOrBlank()) return getString(R.string.customer_order_estimate_unavailable)
        return runCatching {
            val parser = SimpleDateFormat(API_DATE_FORMAT, Locale.US).apply { isLenient = false }
            val formatter = SimpleDateFormat(DISPLAY_DATE_FORMAT, INDONESIAN_LOCALE)
            formatter.format(requireNotNull(parser.parse(value)))
        }.getOrElse { value }
    }

    private fun statusStyle(status: String): StatusStyle = when (status) {
        "menunggu" -> StatusStyle(R.string.staff_status_waiting, R.drawable.bg_order_status_waiting, R.color.order_waiting)
        "dicuci" -> StatusStyle(R.string.staff_status_washing, R.drawable.bg_order_status_washing, R.color.laundry_primary_dark)
        "dikeringkan" -> StatusStyle(R.string.staff_status_drying, R.drawable.bg_order_status_drying, R.color.order_drying)
        "disetrika" -> StatusStyle(R.string.staff_status_ironing, R.drawable.bg_order_status_ironing, R.color.order_ironing)
        "dipacking" -> StatusStyle(R.string.staff_status_packing, R.drawable.bg_order_status_packing, R.color.order_packing)
        "siap_diambil" -> StatusStyle(R.string.staff_status_ready, R.drawable.bg_status_done, R.color.dashboard_success)
        else -> StatusStyle(R.string.staff_status_waiting, R.drawable.bg_order_status_waiting, R.color.order_waiting)
    }

    private fun serverMessage(response: Response<CustomerOrdersResponse>): String? = runCatching {
        JSONObject(response.errorBody()?.string().orEmpty()).optString("message").takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun applySystemBarInsets() {
        val header = findViewById<View>(R.id.customerOrdersHeader)
        val navigation = findViewById<View>(R.id.customerOrdersBottomNavigation)
        val headerTop = header.paddingTop
        val navigationBottom = navigation.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.customerOrdersRoot)) { _, insets ->
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

    override fun onDestroy() {
        ordersCall?.cancel()
        super.onDestroy()
    }

    private data class StatusStyle(val label: Int, val background: Int, val textColor: Int)

    companion object {
        private val INDONESIAN_LOCALE = Locale.forLanguageTag("id-ID")
        private const val API_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"
        private const val DISPLAY_DATE_FORMAT = "d MMMM yyyy"
    }
}
