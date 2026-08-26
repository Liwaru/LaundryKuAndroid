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
import com.example.laundryku.model.CashierCustomerData
import com.example.laundryku.model.CashierCustomersResponse
import com.example.laundryku.network.RetrofitClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class CashierCustomerActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var scroll: NestedScrollView
    private lateinit var loading: View
    private lateinit var errorState: View
    private lateinit var customerList: LinearLayout
    private lateinit var emptyState: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyDescription: TextView
    private lateinit var totalText: TextView
    private var customersCall: Call<CashierCustomersResponse>? = null
    private var allCustomers: List<CashierCustomerData> = emptyList()
    private var searchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = requireValidSession(2) ?: return
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_cashier_customers)
        bindViews()
        applySystemBarInsets()
        bindActions()
        clearData()
    }

    override fun onResume() {
        super.onResume()
        if (::session.isInitialized) loadCustomers()
    }

    private fun bindViews() {
        scroll = findViewById(R.id.cashierCustomersScroll)
        loading = findViewById(R.id.cashierCustomersLoading)
        errorState = findViewById(R.id.cashierCustomersErrorState)
        customerList = findViewById(R.id.cashierCustomersList)
        emptyState = findViewById(R.id.cashierCustomersEmptyState)
        emptyTitle = findViewById(R.id.cashierCustomersEmptyTitle)
        emptyDescription = findViewById(R.id.cashierCustomersEmptyDescription)
        totalText = findViewById(R.id.cashierCustomersTotalText)
    }

    private fun bindActions() {
        findViewById<View>(R.id.cashierCustomersNavHome).setOnClickListener {
            openScreen(CashierDashboardActivity::class.java)
        }
        findViewById<View>(R.id.cashierCustomersNavTransactions).setOnClickListener {
            openScreen(CashierTransactionActivity::class.java)
        }
        findViewById<View>(R.id.cashierCustomersNavProfile).setOnClickListener { openProfileForLevel(2) }
        findViewById<View>(R.id.cashierCustomersRetryButton).setOnClickListener { loadCustomers() }
        findViewById<TextInputEditText>(R.id.cashierCustomersSearchInput)
            .addTextChangedListener { value ->
                searchQuery = value?.toString().orEmpty()
                if (loading.visibility != View.VISIBLE && errorState.visibility != View.VISIBLE) {
                    renderCustomers()
                }
            }
    }

    private fun clearData() {
        totalText.text = ""
        customerList.removeAllViews()
        emptyState.visibility = View.GONE
        findViewById<TextView>(R.id.cashierCustomersErrorMessage)
            .setText(R.string.cashier_customers_load_error)
    }

    private fun loadCustomers() {
        customersCall?.cancel()
        clearData()
        scroll.visibility = View.INVISIBLE
        loading.visibility = View.VISIBLE
        errorState.visibility = View.GONE
        customersCall = RetrofitClient.apiService.getCashierCustomers(session.getUserId()).also { call ->
            call.enqueue(object : Callback<CashierCustomersResponse> {
                override fun onResponse(
                    call: Call<CashierCustomersResponse>,
                    response: Response<CashierCustomersResponse>
                ) {
                    if (isFinishing || isDestroyed) return
                    loading.visibility = View.GONE
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true && body.data != null) {
                        allCustomers = body.data.customers
                        totalText.text = getString(
                            R.string.cashier_customers_total_format,
                            body.data.totalCustomers
                        )
                        renderCustomers()
                        scroll.visibility = View.VISIBLE
                    } else {
                        showLoadError(serverMessage(response))
                    }
                }

                override fun onFailure(call: Call<CashierCustomersResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    loading.visibility = View.GONE
                    showLoadError(null)
                }
            })
        }
    }

    private fun renderCustomers() {
        customerList.removeAllViews()
        val visibleCustomers = CashierCustomerPresentation.filter(allCustomers, searchQuery)
        if (visibleCustomers.isEmpty()) {
            val noCustomers = allCustomers.isEmpty()
            emptyTitle.setText(
                if (noCustomers) R.string.cashier_customers_empty_title
                else R.string.cashier_customers_search_empty_title
            )
            emptyDescription.setText(
                if (noCustomers) R.string.cashier_customers_empty_description
                else R.string.cashier_customers_search_empty_description
            )
            emptyState.visibility = View.VISIBLE
            return
        }
        emptyState.visibility = View.GONE
        visibleCustomers.forEach { customer ->
            customerList.addView(
                LayoutInflater.from(this).inflate(
                    R.layout.item_cashier_customer,
                    customerList,
                    false
                ).apply {
                    findViewById<TextView>(R.id.cashierCustomerName).text = customer.nama
                    findViewById<TextView>(R.id.cashierCustomerUsername).text = customer.username
                    findViewById<TextView>(R.id.cashierCustomerPhone).text = customer.phone
                    findViewById<TextView>(R.id.cashierCustomerTransactions).text = getString(
                        R.string.cashier_customer_transactions_format,
                        customer.totalTransactions
                    )
                    findViewById<MaterialButton>(R.id.cashierCustomerDetailButton).setOnClickListener {
                        Toast.makeText(
                            this@CashierCustomerActivity,
                            R.string.cashier_customer_detail_unavailable,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }
    }

    private fun showLoadError(message: String?) {
        allCustomers = emptyList()
        customerList.removeAllViews()
        emptyState.visibility = View.GONE
        scroll.visibility = View.INVISIBLE
        errorState.visibility = View.VISIBLE
        if (!message.isNullOrBlank()) {
            findViewById<TextView>(R.id.cashierCustomersErrorMessage).text = message
        }
    }

    private fun serverMessage(response: Response<*>): String? = runCatching {
        JSONObject(response.errorBody()?.string().orEmpty()).optString("message").takeIf { it.isNotBlank() }
    }.getOrNull()

    override fun onDestroy() {
        customersCall?.cancel()
        super.onDestroy()
    }

    private fun applySystemBarInsets() {
        val header = findViewById<View>(R.id.cashierCustomersHeader)
        val navigation = findViewById<View>(R.id.cashierCustomersBottomNavigation)
        val headerTop = header.paddingTop
        val navigationBottom = navigation.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.cashierCustomersRoot)) { _, insets ->
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
