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
import com.example.laundryku.model.CashierCustomerDetailResponse
import com.example.laundryku.network.RetrofitClient
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class CashierCustomerDetailActivity : AppCompatActivity() {
    private var detailCall: Call<CashierCustomerDetailResponse>? = null
    private var customerId = INVALID_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireValidSession(2) ?: return
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_cashier_customer_detail)
        applyInsets()
        findViewById<View>(R.id.cashierCustomerDetailBack).setOnClickListener { finish() }
        findViewById<View>(R.id.cashierCustomerDetailRetry).setOnClickListener { loadDetail() }
        customerId = intent.getIntExtra(EXTRA_CUSTOMER_ID, INVALID_ID)
        if (customerId > 0) loadDetail() else showError(getString(R.string.management_invalid_target))
    }

    private fun loadDetail() {
        if (customerId <= 0) {
            showError(getString(R.string.management_invalid_target))
            return
        }
        detailCall?.cancel()
        findViewById<View>(R.id.cashierCustomerDetailContent).visibility = View.GONE
        findViewById<View>(R.id.cashierCustomerDetailError).visibility = View.GONE
        findViewById<View>(R.id.cashierCustomerDetailLoading).visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.cashierCustomerRecentList).removeAllViews()
        detailCall = RetrofitClient.apiService.getCashierCustomerDetail(customerId).also { call ->
            call.enqueue(object : Callback<CashierCustomerDetailResponse> {
                override fun onResponse(
                    call: Call<CashierCustomerDetailResponse>,
                    response: Response<CashierCustomerDetailResponse>
                ) {
                    if (isFinishing || isDestroyed) return
                    findViewById<View>(R.id.cashierCustomerDetailLoading).visibility = View.GONE
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true && body.data != null) {
                        val data = body.data
                        val customer = data.customer
                        findViewById<TextView>(R.id.cashierCustomerDetailName).text = customer.nama
                        findViewById<TextView>(R.id.cashierCustomerDetailUsername).text = customer.username
                        findViewById<TextView>(R.id.cashierCustomerDetailPhone).text = customer.phone
                        findViewById<TextView>(R.id.cashierCustomerDetailStatus).text =
                            OwnerStaffPresentation.statusLabel(customer.accountStatus)
                        findViewById<TextView>(R.id.cashierCustomerDetailTotal).text =
                            customer.totalTransactions.toString()
                        findViewById<TextView>(R.id.cashierCustomerDetailSpending).text =
                            ManagementDetailPresentation.currency(customer.totalSpending)
                        findViewById<TextView>(R.id.cashierCustomerDetailCreated).text =
                            ManagementDetailPresentation.date(customer.createdAt)
                        renderRecent(data.recentTransactions)
                        findViewById<View>(R.id.cashierCustomerDetailContent).visibility = View.VISIBLE
                    } else {
                        showError(serverMessage(response) ?: getString(R.string.cashier_customer_detail_error))
                    }
                }

                override fun onFailure(call: Call<CashierCustomerDetailResponse>, throwable: Throwable) {
                    if (!call.isCanceled && !isFinishing && !isDestroyed) {
                        showError(getString(R.string.cashier_customer_detail_error))
                    }
                }
            })
        }
    }

    private fun renderRecent(transactions: List<com.example.laundryku.model.CashierCustomerRecentTransaction>) {
        val list = findViewById<LinearLayout>(R.id.cashierCustomerRecentList)
        list.removeAllViews()
        findViewById<View>(R.id.cashierCustomerRecentCard).visibility =
            if (transactions.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.cashierCustomerRecentEmpty).visibility =
            if (transactions.isEmpty()) View.VISIBLE else View.GONE
        transactions.forEach { transaction ->
            list.addView(LayoutInflater.from(this).inflate(R.layout.item_cashier_customer_recent_transaction, list, false).apply {
                findViewById<TextView>(R.id.cashierCustomerRecentCode).text =
                    ManagementDetailPresentation.displayCode(transaction.transactionCode)
                findViewById<TextView>(R.id.cashierCustomerRecentStatus).text =
                    CustomerOrderDetailPresentation.statusLabel(transaction.laundryStatus)
                findViewById<TextView>(R.id.cashierCustomerRecentMeta).text = getString(
                    R.string.cashier_customer_recent_meta,
                    ManagementDetailPresentation.date(transaction.enteredAt),
                    ManagementDetailPresentation.currency(transaction.totalPrice)
                )
            })
        }
    }

    private fun showError(message: String) {
        findViewById<View>(R.id.cashierCustomerDetailLoading).visibility = View.GONE
        findViewById<View>(R.id.cashierCustomerDetailContent).visibility = View.GONE
        findViewById<View>(R.id.cashierCustomerDetailError).visibility = View.VISIBLE
        findViewById<TextView>(R.id.cashierCustomerDetailErrorMessage).text = message
    }

    private fun serverMessage(response: Response<*>): String? = runCatching {
        JSONObject(response.errorBody()?.string().orEmpty()).optString("message").takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun applyInsets() {
        val header = findViewById<View>(R.id.cashierCustomerDetailHeader)
        val top = header.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.cashierCustomerDetailRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.setPadding(header.paddingLeft, top + bars.top, header.paddingRight, header.paddingBottom)
            insets
        }
    }

    override fun onDestroy() {
        detailCall?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CUSTOMER_ID = "id_pelanggan"
        private const val INVALID_ID = -1
    }
}
