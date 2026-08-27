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
import com.example.laundryku.model.CustomerDashboardOrder
import com.example.laundryku.model.CustomerDashboardResponse
import com.example.laundryku.model.ServiceData
import com.example.laundryku.model.ServicesResponse
import com.example.laundryku.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

class DashboardCustomerActivity : AppCompatActivity() {
    private var servicesCall: Call<ServicesResponse>? = null
    private var dashboardCall: Call<CustomerDashboardResponse>? = null
    private val serviceIdsByCard = mutableMapOf<Int, Int>()
    private var dashboardReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = requireValidSession(1) ?: return
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_dashboard_customer)
        dashboardReady = true
        applySystemBarInsets()
        findViewById<TextView>(R.id.customerGreetingText).text =
            getString(R.string.dashboard_greeting_format, session.getNama())
        bindActions()
        loadPopularServices()
    }

    override fun onResume() {
        super.onResume()
        if (dashboardReady) {
            findViewById<TextView>(R.id.customerGreetingText).text = getString(
                R.string.dashboard_greeting_format,
                SessionManager(this).getNama()
            )
            loadDashboardOrders()
        }
    }

    private fun bindActions() {
        findViewById<View>(R.id.newOrderButton).setOnClickListener { openCreateOrder() }
        findViewById<View>(R.id.dashboardOrdersRetryButton).setOnClickListener { loadDashboardOrders() }
        findViewById<View>(R.id.dashboardSeeAllOrders).setOnClickListener {
            openScreen(CustomerOrdersActivity::class.java)
        }
        listOf(R.id.serviceDry, R.id.serviceIron, R.id.serviceExpress, R.id.serviceBedCover).forEach { cardId ->
            findViewById<View>(cardId).setOnClickListener { openCreateOrder(serviceIdsByCard[cardId]) }
        }
        findViewById<View>(R.id.navOrders).setOnClickListener { openScreen(CustomerOrdersActivity::class.java) }
        findViewById<View>(R.id.navHistory).setOnClickListener { openScreen(CustomerHistoryActivity::class.java) }
        findViewById<View>(R.id.navProfile).setOnClickListener { openProfileForLevel(1) }
    }

    private fun loadDashboardOrders() {
        dashboardCall?.cancel()
        showOrdersLoading()
        dashboardCall = RetrofitClient.apiService.getCustomerDashboard().also { call ->
            call.enqueue(object : Callback<CustomerDashboardResponse> {
                override fun onResponse(
                    call: Call<CustomerDashboardResponse>,
                    response: Response<CustomerDashboardResponse>
                ) {
                    if (isFinishing || isDestroyed) return
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true && body.data != null) {
                        renderActiveOrder(body.data.activeOrder)
                        renderRecentOrders(body.data.recentOrders.take(MAX_RECENT_ORDERS))
                    } else {
                        showOrdersError()
                    }
                }

                override fun onFailure(call: Call<CustomerDashboardResponse>, throwable: Throwable) {
                    if (!call.isCanceled && !isFinishing && !isDestroyed) showOrdersError()
                }
            })
        }
    }

    private fun showOrdersLoading() {
        findViewById<View>(R.id.dashboardOrdersLoading).visibility = View.VISIBLE
        findViewById<View>(R.id.dashboardOrdersErrorState).visibility = View.GONE
        findViewById<View>(R.id.dashboardActiveOrderCard).visibility = View.GONE
        findViewById<View>(R.id.dashboardActiveOrderEmpty).visibility = View.GONE
        findViewById<View>(R.id.dashboardRecentOrdersCard).visibility = View.GONE
        findViewById<View>(R.id.dashboardRecentOrdersEmpty).visibility = View.GONE
        findViewById<LinearLayout>(R.id.dashboardRecentOrdersList).removeAllViews()
    }

    private fun showOrdersError() {
        findViewById<View>(R.id.dashboardOrdersLoading).visibility = View.GONE
        findViewById<View>(R.id.dashboardActiveOrderCard).visibility = View.GONE
        findViewById<View>(R.id.dashboardActiveOrderEmpty).visibility = View.GONE
        findViewById<View>(R.id.dashboardRecentOrdersCard).visibility = View.GONE
        findViewById<View>(R.id.dashboardRecentOrdersEmpty).visibility = View.GONE
        findViewById<View>(R.id.dashboardOrdersErrorState).visibility = View.VISIBLE
    }

    private fun renderActiveOrder(order: CustomerDashboardOrder?) {
        findViewById<View>(R.id.dashboardOrdersLoading).visibility = View.GONE
        findViewById<View>(R.id.dashboardOrdersErrorState).visibility = View.GONE
        findViewById<View>(R.id.dashboardActiveOrderEmpty).visibility =
            if (order == null) View.VISIBLE else View.GONE
        findViewById<View>(R.id.dashboardActiveOrderCard).visibility =
            if (order == null) View.GONE else View.VISIBLE
        if (order == null) return

        findViewById<TextView>(R.id.dashboardActiveOrderCode).text =
            CustomerDashboardPresentation.displayCode(order.transactionCode)
        findViewById<TextView>(R.id.dashboardActiveOrderService).text =
            CustomerDashboardPresentation.serviceSummary(order)
        findViewById<TextView>(R.id.dashboardActiveOrderPayment).setText(
            if (order.paymentStatus == "sudah_dibayar") R.string.customer_order_paid
            else R.string.customer_order_unpaid
        )
        findViewById<TextView>(R.id.dashboardActiveOrderStatus).applyStatus(order.laundryStatus)
        findViewById<TextView>(R.id.dashboardActiveOrderEstimate).text =
            CustomerDashboardPresentation.date(order.estimatedCompletion)
        findViewById<View>(R.id.detailButton).setOnClickListener { openOrderDetail(order.transactionId) }
    }

    private fun renderRecentOrders(orders: List<CustomerDashboardOrder>) {
        val list = findViewById<LinearLayout>(R.id.dashboardRecentOrdersList)
        list.removeAllViews()
        findViewById<View>(R.id.dashboardRecentOrdersCard).visibility =
            if (orders.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.dashboardRecentOrdersEmpty).visibility =
            if (orders.isEmpty()) View.VISIBLE else View.GONE

        orders.forEachIndexed { index, order ->
            if (index > 0) list.addView(divider())
            val item = LayoutInflater.from(this).inflate(R.layout.item_dashboard_recent_order, list, false)
            item.findViewById<TextView>(R.id.dashboardRecentOrderCode).text =
                CustomerDashboardPresentation.displayCode(order.transactionCode)
            val relevantDate = when (order.laundryStatus) {
                "selesai", "dibatalkan" -> order.completionDate ?: order.orderDate
                else -> order.estimatedCompletion ?: order.orderDate
            }
            item.findViewById<TextView>(R.id.dashboardRecentOrderDetail).text = getString(
                R.string.dashboard_recent_detail_format,
                CustomerDashboardPresentation.serviceSummary(order),
                CustomerDashboardPresentation.date(relevantDate)
            )
            item.findViewById<TextView>(R.id.dashboardRecentOrderStatus).applyStatus(order.laundryStatus)
            item.setOnClickListener { openOrderDetail(order.transactionId) }
            list.addView(item)
        }
    }

    private fun TextView.applyStatus(status: String) {
        val style = statusStyle(status)
        text = CustomerDashboardPresentation.statusLabel(status)
        setBackgroundResource(style.background)
        setTextColor(getColor(style.textColor))
    }

    private fun statusStyle(status: String): StatusStyle = when (status) {
        "menunggu" -> StatusStyle(R.drawable.bg_status_active, R.color.dashboard_warning)
        "dicuci" -> StatusStyle(R.drawable.bg_order_status_washing, R.color.laundry_primary_dark)
        "dikeringkan" -> StatusStyle(R.drawable.bg_order_status_drying, R.color.order_drying)
        "disetrika" -> StatusStyle(R.drawable.bg_order_status_ironing, R.color.order_ironing)
        "dipacking" -> StatusStyle(R.drawable.bg_order_status_packing, R.color.order_packing)
        "siap_diambil", "selesai" -> StatusStyle(R.drawable.bg_status_done, R.color.dashboard_success)
        "dibatalkan" -> StatusStyle(R.drawable.bg_history_cancelled, R.color.history_cancelled)
        else -> StatusStyle(R.drawable.bg_status_active, R.color.dashboard_warning)
    }

    private fun openOrderDetail(transactionId: Int) {
        startActivity(Intent(this, CustomerOrderDetailActivity::class.java).apply {
            putExtra(CustomerOrderDetailActivity.EXTRA_TRANSACTION_ID, transactionId)
        })
    }

    private fun openCreateOrder(serviceId: Int? = null) {
        startActivity(Intent(this, CreateOrderActivity::class.java).apply {
            serviceId?.let { putExtra(CreateOrderActivity.EXTRA_SERVICE_ID, it) }
        })
    }

    private fun loadPopularServices() {
        servicesCall = RetrofitClient.apiService.getServices().also { call ->
            call.enqueue(object : Callback<ServicesResponse> {
                override fun onResponse(call: Call<ServicesResponse>, response: Response<ServicesResponse>) {
                    if (isFinishing || isDestroyed) return
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true) {
                        body.data.orEmpty().forEach(::showService)
                    } else {
                        showServicesLoadError()
                    }
                }

                override fun onFailure(call: Call<ServicesResponse>, throwable: Throwable) {
                    if (!call.isCanceled && !isFinishing && !isDestroyed) showServicesLoadError()
                }
            })
        }
    }

    private fun showService(service: ServiceData) {
        val views = when (service.name.trim().lowercase(Locale.ROOT)) {
            "cuci kering" -> ServiceViews(R.id.serviceDry, R.id.serviceDryName, R.id.serviceDryPrice, R.id.serviceDryMinimum)
            "cuci setrika" -> ServiceViews(R.id.serviceIron, R.id.serviceIronName, R.id.serviceIronPrice, R.id.serviceIronMinimum)
            "express" -> ServiceViews(R.id.serviceExpress, R.id.serviceExpressName, R.id.serviceExpressPrice, R.id.serviceExpressMinimum)
            "bed cover" -> ServiceViews(R.id.serviceBedCover, R.id.serviceBedCoverName, R.id.serviceBedCoverPrice, R.id.serviceBedCoverMinimum)
            else -> return
        }
        serviceIdsByCard[views.card] = service.id
        findViewById<TextView>(views.name).text = service.name
        findViewById<TextView>(views.price).text =
            getString(R.string.create_order_service_price_format, formatCurrency(service.harga), service.satuan)
        findViewById<TextView>(views.minimum).text = getString(
            R.string.create_order_minimum_format,
            formatDecimal(service.minimumOrder),
            service.satuan
        ).replace("Minimal", "Min.")
    }

    private fun showServicesLoadError() {
        Toast.makeText(this, R.string.dashboard_services_load_error, Toast.LENGTH_SHORT).show()
    }

    private fun formatCurrency(value: Double): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID")).apply {
        maximumFractionDigits = if (value % 1.0 == 0.0) 0 else 2
        minimumFractionDigits = 0
    }.format(value).replace('\u00a0', ' ')

    private fun formatDecimal(value: Double): String =
        BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.', ',')

    private fun divider() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
            marginStart = dp(16)
            marginEnd = dp(16)
        }
        setBackgroundColor(getColor(R.color.dashboard_divider))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun applySystemBarInsets() {
        val header = findViewById<View>(R.id.dashboardHeader)
        val bottomNavigation = findViewById<View>(R.id.bottomNavigation)
        val headerTop = header.paddingTop
        val bottomPadding = bottomNavigation.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.dashboardRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.setPadding(header.paddingLeft, headerTop + bars.top, header.paddingRight, header.paddingBottom)
            bottomNavigation.setPadding(
                bottomNavigation.paddingLeft,
                bottomNavigation.paddingTop,
                bottomNavigation.paddingRight,
                bottomPadding + bars.bottom
            )
            insets
        }
    }

    override fun onDestroy() {
        servicesCall?.cancel()
        dashboardCall?.cancel()
        super.onDestroy()
    }

    private data class ServiceViews(val card: Int, val name: Int, val price: Int, val minimum: Int)
    private data class StatusStyle(val background: Int, val textColor: Int)

    private companion object {
        const val MAX_RECENT_ORDERS = 2
    }
}
