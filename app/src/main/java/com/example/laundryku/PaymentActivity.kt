package com.example.laundryku

import android.graphics.Color
import android.content.Intent
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
import com.example.laundryku.model.CreateQrisPaymentRequest
import com.example.laundryku.model.QrisPaymentResponse
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
    private lateinit var cashPendingStatus: TextView
    private lateinit var cashSubmitButton: MaterialButton

    private var detailCall: Call<CustomerOrderDetailResponse>? = null
    private var cashCall: Call<CashPaymentResponse>? = null
    private var qrisCall: Call<QrisPaymentResponse>? = null
    private var transactionId = INVALID_TRANSACTION_ID
    private var isSubmitting = false
    private var cashPending = false
    private var qrisPending = false
    private var selectedMethod: String? = null
    private var transactionCode = ""
    private var transactionTotal = 0.0

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
        transactionCode = data.transactionCode
        transactionTotal = data.totalPrice
        val payment = data.payment
        cashPending = PaymentPresentation.isCashWaiting(payment?.metode, payment?.status)
        qrisPending = PaymentPresentation.isQrisWaiting(payment?.metode, payment?.status)
        selectedMethod = when {
            cashPending -> CASH_KEY
            payment?.metode == QRIS_KEY -> QRIS_KEY
            else -> null
        }
        renderMethods()
        when {
            cashPending -> showCashPending()
            qrisPending -> showQrisSelection(existing = true)
            payment?.metode == QRIS_KEY && payment.status == "gagal" -> showQrisSelection(existing = false)
            else -> cashInformationCard.visibility = View.GONE
        }
        errorState.visibility = View.GONE
        content.visibility = View.VISIBLE
    }

    private fun renderMethods() {
        methodsContainer.removeAllViews()
        PAYMENT_METHODS.forEach { method ->
            val item = LayoutInflater.from(this).inflate(R.layout.item_payment_method, methodsContainer, false)
            val card = item.findViewById<MaterialCardView>(R.id.paymentMethodCard)
            item.findViewById<TextView>(R.id.paymentMethodName).setText(method.label)
            item.findViewById<TextView>(R.id.paymentMethodAvailability).setText(R.string.payment_available)
            item.findViewById<RadioButton>(R.id.paymentMethodRadio).apply {
                isChecked = method.key == selectedMethod
            }
            card.strokeWidth = if (method.key == selectedMethod) dp(2) else dp(1)
            card.setStrokeColor(getColor(if (method.key == selectedMethod) R.color.laundry_primary else R.color.dashboard_divider))
            card.setOnClickListener {
                if (method.key == CASH_KEY && qrisPending) {
                    Toast.makeText(this, R.string.payment_qris_switch_blocked, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                selectedMethod = method.key
                renderMethods()
                when (method.key) {
                    QRIS_KEY -> showQrisSelection(existing = qrisPending)
                    CASH_KEY -> showCashSelection()
                    else -> openEWalletPayment(method.key)
                }
            }
            methodsContainer.addView(item)
        }
    }

    private fun openEWalletPayment(channel: String) {
        cashInformationCard.visibility = View.GONE
        startActivity(Intent(this, EWalletPaymentActivity::class.java).apply {
            putExtra(EWalletPaymentActivity.EXTRA_TRANSACTION_ID, transactionId)
            putExtra(EWalletPaymentActivity.EXTRA_PAYMENT_CHANNEL, channel)
        })
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
        cashSubmitButton.setText(R.string.payment_cash_submit)
        cashSubmitButton.setOnClickListener { submitCashPayment() }
        cashSubmitButton.isEnabled = !isSubmitting
        cashInformationCard.visibility = View.VISIBLE
    }

    private fun showQrisSelection(existing: Boolean) {
        cashInformationTitle.setText(R.string.payment_qris_information_title)
        cashInformationDescription.setText(
            if (existing) R.string.payment_qris_pending_description else R.string.payment_qris_information
        )
        cashPendingStatus.visibility = if (existing) View.VISIBLE else View.GONE
        if (existing) cashPendingStatus.setText(R.string.payment_qris_pending_status)
        cashSubmitButton.visibility = View.VISIBLE
        cashSubmitButton.setText(if (existing) R.string.payment_qris_show else R.string.payment_qris_create)
        cashSubmitButton.isEnabled = !isSubmitting
        cashSubmitButton.setOnClickListener { submitQrisPayment() }
        cashInformationCard.visibility = View.VISIBLE
    }

    private fun showCashPending() {
        cashInformationTitle.setText(R.string.payment_cash_selected_title)
        cashInformationDescription.setText(R.string.payment_cash_selected_description)
        cashPendingStatus.setText(R.string.payment_cash_pending_status)
        cashPendingStatus.visibility = View.VISIBLE
        cashSubmitButton.visibility = View.GONE
        cashInformationCard.visibility = View.VISIBLE
    }

    private fun submitCashPayment() {
        if (isSubmitting || transactionId <= 0) return
        isSubmitting = true
        cashSubmitButton.setOnClickListener { submitCashPayment() }
        cashSubmitButton.setText(R.string.payment_cash_submit)
        cashSubmitButton.isEnabled = false

        cashCall = RetrofitClient.apiService.selectCashPayment(
            SelectCashPaymentRequest(transactionId)
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
                        selectedMethod = CASH_KEY
                        renderMethods()
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

    private fun submitQrisPayment() {
        if (isSubmitting || transactionId <= 0) return
        isSubmitting = true
        cashSubmitButton.isEnabled = false
        qrisCall = RetrofitClient.apiService.createQrisPayment(
            CreateQrisPaymentRequest(transactionId)
        ).also { call ->
            call.enqueue(object : Callback<QrisPaymentResponse> {
                override fun onResponse(call: Call<QrisPaymentResponse>, response: Response<QrisPaymentResponse>) {
                    if (isFinishing || isDestroyed) return
                    isSubmitting = false
                    cashSubmitButton.isEnabled = true
                    val body = response.body()
                    val data = body?.data
                    if (response.isSuccessful && body?.success == true && data != null &&
                        data.status == "menunggu" && !data.gatewayOrderId.isNullOrBlank() &&
                        !data.qrUrl.isNullOrBlank()) {
                        qrisPending = true
                        setResult(RESULT_OK)
                        startActivity(Intent(this@PaymentActivity, QrisPaymentActivity::class.java).apply {
                            putExtra(QrisPaymentActivity.EXTRA_TRANSACTION_ID, transactionId)
                            putExtra(QrisPaymentActivity.EXTRA_TRANSACTION_CODE, transactionCode)
                            putExtra(QrisPaymentActivity.EXTRA_TOTAL, data.total ?: transactionTotal)
                            putExtra(QrisPaymentActivity.EXTRA_QR_URL, data.qrUrl)
                            putExtra(QrisPaymentActivity.EXTRA_EXPIRY_TIME, data.expiryTime)
                        })
                    } else {
                        Toast.makeText(
                            this@PaymentActivity,
                            serverMessage(response) ?: getString(R.string.payment_qris_create_error),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailure(call: Call<QrisPaymentResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    isSubmitting = false
                    cashSubmitButton.isEnabled = true
                    Toast.makeText(this@PaymentActivity, R.string.payment_qris_create_error, Toast.LENGTH_LONG).show()
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
        qrisCall?.cancel()
        super.onDestroy()
    }

    override fun onRestart() {
        super.onRestart()
        if (!isSubmitting && transactionId > 0) loadPaymentData()
    }

    private data class PaymentMethod(val key: String, val label: Int)

    companion object {
        const val EXTRA_TRANSACTION_ID = "extra_transaction_id"
        private const val INVALID_TRANSACTION_ID = -1
        private const val CASH_KEY = "cash"
        private const val QRIS_KEY = "qris"
        private val INDONESIAN_LOCALE = Locale.forLanguageTag("id-ID")
        private val PAYMENT_METHODS = listOf(
            PaymentMethod(CASH_KEY, R.string.payment_cash),
            PaymentMethod(QRIS_KEY, R.string.payment_qris),
            PaymentMethod("gopay", R.string.payment_gopay),
            PaymentMethod("dana", R.string.payment_dana),
            PaymentMethod("ovo", R.string.payment_ovo),
            PaymentMethod("shopeepay", R.string.payment_shopeepay)
        )
    }
}
