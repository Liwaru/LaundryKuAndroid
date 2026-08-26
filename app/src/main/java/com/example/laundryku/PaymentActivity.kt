package com.example.laundryku

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.laundryku.model.CashPaymentResponse
import com.example.laundryku.model.CustomerOrderDetailData
import com.example.laundryku.model.CustomerOrderDetailResponse
import com.example.laundryku.model.SelectCashPaymentRequest
import com.example.laundryku.network.RetrofitClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.NumberFormat
import java.util.Locale

class PaymentActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var loading: View
    private lateinit var errorState: View
    private lateinit var errorMessage: TextView
    private lateinit var content: View
    private lateinit var methodsContainer: android.widget.LinearLayout
    private lateinit var cashInformationCard: View
    private lateinit var cashInformationTitle: TextView
    private lateinit var cashInformationDescription: TextView
    private lateinit var cashPendingStatus: View
    private lateinit var cashSubmitButton: MaterialButton

    private var detailCall: Call<CustomerOrderDetailResponse>? = null
    private var cashCall: Call<CashPaymentResponse>? = null
    private var transactionId = INVALID_TRANSACTION_ID
    private var isSubmitting = false
    private var cashPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = requireValidSession(1) ?: return
        transactionId = intent.getIntExtra(EXTRA_TRANSACTION_ID, INVALID_TRANSACTION_ID)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_payment)
        bindViews()
        applySystemBarInsets()
        findViewById<View>(R.id.paymentBackButton).setOnClickListener { finish() }
        findViewById<View>(R.id.paymentRetryButton).setOnClickListener { loadPaymentData() }
        cashSubmitButton.setOnClickListener { submitCashPayment() }

        if (transactionId > 0) {
            loadPaymentData()
        } else {
            loading.visibility = View.GONE
            showError(getString(R.string.payment_invalid_transaction))
        }
    }

    private fun bindViews() {
        loading = findViewById(R.id.paymentLoading)
        errorState = findViewById(R.id.paymentErrorState)
        errorMessage = findViewById(R.id.paymentErrorMessage)
        content = findViewById(R.id.paymentScroll)
        methodsContainer = findViewById(R.id.paymentMethods)
        cashInformationCard = findViewById(R.id.paymentCashInformationCard)
        cashInformationTitle = findViewById(R.id.paymentCashInformationTitle)
        cashInformationDescription = findViewById(R.id.paymentCashInformationDescription)
        cashPendingStatus = findViewById(R.id.paymentCashPendingStatus)
        cashSubmitButton = findViewById(R.id.paymentCashSubmitButton)
    }

    private fun loadPaymentData() {
        detailCall?.cancel()
        loading.visibility = View.VISIBLE
        errorState.visibility = View.GONE
        content.visibility = View.GONE

        detailCall = RetrofitClient.apiService.getCustomerOrderDetail(
            session.getUserId(),
            transactionId
        ).also { call ->
            call.enqueue(object : Callback<CustomerOrderDetailResponse> {
                override fun onResponse(
                    call: Call<CustomerOrderDetailResponse>,
                    response: Response<CustomerOrderDetailResponse>
                ) {
                    if (isFinishing || isDestroyed) return
                    loading.visibility = View.GONE
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true && body.data != null) {
                        renderPaymentData(body.data)
                    } else {
                        showError(serverMessage(response))
                    }
                }

                override fun onFailure(call: Call<CustomerOrderDetailResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    loading.visibility = View.GONE
                    showError(null)
                }
            })
        }
    }

    private fun renderPaymentData(data: CustomerOrderDetailData) {
        when {
            data.laundryStatus == "dibatalkan" -> {
                showError(getString(R.string.payment_cancelled_order))
                return
            }
            data.paymentStatus == "sudah_dibayar" -> {
                showError(getString(R.string.payment_already_paid))
                return
            }
        }

        findViewById<TextView>(R.id.paymentTransactionCode).text = displayCode(data.transactionCode)
        findViewById<TextView>(R.id.paymentTotal).text = formatCurrency(data.totalPrice)
        val payment = data.payment
        cashPending = PaymentPresentation.isCashWaiting(payment?.metode, payment?.status)
        renderMethods(selectCash = cashPending)
        if (cashPending) showCashPending() else cashInformationCard.visibility = View.GONE
        errorState.visibility = View.GONE
        content.visibility = View.VISIBLE
    }

    private fun renderMethods(selectCash: Boolean) {
        methodsContainer.removeAllViews()
        PAYMENT_METHODS.forEach { method ->
            val item = LayoutInflater.from(this).inflate(R.layout.item_payment_method, methodsContainer, false)
            val card = item.findViewById<MaterialCardView>(R.id.paymentMethodCard)
            item.findViewById<TextView>(R.id.paymentMethodName).setText(method.label)
            item.findViewById<TextView>(R.id.paymentMethodAvailability).setText(
                if (method.available) R.string.payment_available else R.string.payment_coming_soon
            )
            item.findViewById<RadioButton>(R.id.paymentMethodRadio).apply {
                visibility = if (method.available) View.VISIBLE else View.GONE
                isChecked = method.key == CASH_KEY && selectCash
            }
            card.alpha = if (method.available) 1f else 0.7f
            card.strokeWidth = if (method.key == CASH_KEY && selectCash) dp(2) else dp(1)
            card.setStrokeColor(getColor(if (method.key == CASH_KEY && selectCash) R.color.laundry_primary else R.color.dashboard_divider))
            card.setOnClickListener {
                if (method.available) {
                    renderMethods(selectCash = true)
                    showCashSelection()
                } else {
                    Toast.makeText(this, R.string.payment_unavailable_message, Toast.LENGTH_SHORT).show()
                }
            }
            methodsContainer.addView(item)
        }
    }

    private fun showCashSelection() {
        if (cashPending) {
            showCashPending()
            return
        }
        cashInformationTitle.setText(R.string.payment_cash_information_title)
        cashInformationDescription.setText(R.string.payment_cash_information)
        cashPendingStatus.visibility = View.GONE
        cashSubmitButton.visibility = View.VISIBLE
        cashSubmitButton.isEnabled = !isSubmitting
        cashInformationCard.visibility = View.VISIBLE
    }

    private fun showCashPending() {
        cashInformationTitle.setText(R.string.payment_cash_selected_title)
        cashInformationDescription.setText(R.string.payment_cash_selected_description)
        cashPendingStatus.visibility = View.VISIBLE
        cashSubmitButton.visibility = View.GONE
        cashInformationCard.visibility = View.VISIBLE
    }

    private fun submitCashPayment() {
        if (isSubmitting || transactionId <= 0) return
        isSubmitting = true
        cashSubmitButton.isEnabled = false

        cashCall = RetrofitClient.apiService.selectCashPayment(
            SelectCashPaymentRequest(session.getUserId(), transactionId)
        ).also { call ->
            call.enqueue(object : Callback<CashPaymentResponse> {
                override fun onResponse(call: Call<CashPaymentResponse>, response: Response<CashPaymentResponse>) {
                    if (isFinishing || isDestroyed) return
                    isSubmitting = false
                    val body = response.body()
                    if (
                        response.isSuccessful && body?.success == true &&
                        body.data?.status == "menunggu" &&
                        body.data.transactionPaymentStatus == "belum_dibayar"
                    ) {
                        cashPending = true
                        setResult(RESULT_OK)
                        renderMethods(selectCash = true)
                        showCashPending()
                        Toast.makeText(this@PaymentActivity, body.message, Toast.LENGTH_SHORT).show()
                    } else {
                        cashSubmitButton.isEnabled = true
                        Toast.makeText(
                            this@PaymentActivity,
                            serverMessage(response) ?: getString(R.string.payment_load_error),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailure(call: Call<CashPaymentResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    isSubmitting = false
                    cashSubmitButton.isEnabled = true
                    Toast.makeText(this@PaymentActivity, R.string.payment_load_error, Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun showError(message: String?) {
        content.visibility = View.GONE
        errorMessage.text = message?.takeIf { it.isNotBlank() } ?: getString(R.string.payment_load_error)
        errorState.visibility = View.VISIBLE
    }

    private fun displayCode(code: String): String =
        if (code.startsWith('#')) code else getString(R.string.customer_order_code_format, code)

    private fun formatCurrency(value: Double): String = NumberFormat.getCurrencyInstance(INDONESIAN_LOCALE).apply {
        maximumFractionDigits = if (value % 1.0 == 0.0) 0 else 2
        minimumFractionDigits = 0
    }.format(value).replace('\u00a0', ' ')

    private fun serverMessage(response: Response<*>): String? = runCatching {
        JSONObject(response.errorBody()?.string().orEmpty()).optString("message").takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun applySystemBarInsets() {
        val header = findViewById<View>(R.id.paymentHeader)
        val scroll = findViewById<View>(R.id.paymentScroll)
        val headerTop = header.paddingTop
        val scrollBottom = scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.paymentRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.setPadding(header.paddingLeft, headerTop + bars.top, header.paddingRight, header.paddingBottom)
            scroll.setPadding(scroll.paddingLeft, scroll.paddingTop, scroll.paddingRight, scrollBottom + bars.bottom)
            insets
        }
    }

    override fun onDestroy() {
        detailCall?.cancel()
        cashCall?.cancel()
        super.onDestroy()
    }

    private data class PaymentMethod(val key: String, val label: Int, val available: Boolean)

    companion object {
        const val EXTRA_TRANSACTION_ID = "extra_transaction_id"
        private const val INVALID_TRANSACTION_ID = -1
        private const val CASH_KEY = "cash"
        private val INDONESIAN_LOCALE = Locale.forLanguageTag("id-ID")
        private val PAYMENT_METHODS = listOf(
            PaymentMethod(CASH_KEY, R.string.payment_cash, true),
            PaymentMethod("qris", R.string.payment_qris, false),
            PaymentMethod("gopay", R.string.payment_gopay, false),
            PaymentMethod("dana", R.string.payment_dana, false),
            PaymentMethod("ovo", R.string.payment_ovo, false),
            PaymentMethod("shopeepay", R.string.payment_shopeepay, false),
            PaymentMethod("paylater", R.string.payment_paylater, false)
        )
    }
}
