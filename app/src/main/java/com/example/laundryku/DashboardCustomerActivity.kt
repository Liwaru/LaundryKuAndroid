package com.example.laundryku

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    private val serviceIdsByCard = mutableMapOf<Int, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = requireValidSession(1) ?: return
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_dashboard_customer)
        applySystemBarInsets()
        findViewById<TextView>(R.id.customerGreetingText).text =
            getString(R.string.dashboard_greeting_format, session.getNama())
        bindActions()
        loadPopularServices()
    }

    private fun bindActions() {
        findViewById<View>(R.id.detailButton).setOnClickListener { showComingSoon(R.string.detail_order) }
        findViewById<View>(R.id.newOrderButton).setOnClickListener { openCreateOrder() }
        listOf(R.id.serviceDry, R.id.serviceIron, R.id.serviceExpress, R.id.serviceBedCover).forEach { cardId ->
            findViewById<View>(cardId).setOnClickListener { openCreateOrder(serviceIdsByCard[cardId]) }
        }
        findViewById<View>(R.id.navOrders).setOnClickListener { openScreen(CustomerOrdersActivity::class.java) }
        findViewById<View>(R.id.navHistory).setOnClickListener { openScreen(CustomerHistoryActivity::class.java) }
        findViewById<View>(R.id.navProfile).setOnClickListener { openProfileForLevel(1) }
    }

    private fun showComingSoon(label: Int) {
        Toast.makeText(this, getString(R.string.feature_coming_soon, getString(label)), Toast.LENGTH_SHORT).show()
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
        super.onDestroy()
    }

    private data class ServiceViews(val card: Int, val name: Int, val price: Int, val minimum: Int)
}
