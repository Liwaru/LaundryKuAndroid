package com.example.laundryku

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.laundryku.model.CustomerOrderDetailResponse
import com.example.laundryku.model.SimulateEWalletPaymentRequest
import com.example.laundryku.model.SimulateEWalletPaymentResponse
import com.example.laundryku.network.RetrofitClient
import com.google.android.material.button.MaterialButton
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/** Customer-facing confirmation for the local E-Wallet Simulation (Demo Payment). */
class EWalletPaymentActivity : AppCompatActivity() {
    private lateinit var loading: View
    private lateinit var errorState: View
    private lateinit var errorMessage: TextView
    private lateinit var content: View
    private lateinit var title: TextView
    private lateinit var transactionCode: TextView
    private lateinit var total: TextView
    private lateinit var method: TextView
    private lateinit var description: TextView
    private lateinit var submitButton: MaterialButton

    private var detailCall: Call<CustomerOrderDetailResponse>? = null
    private var paymentCall: Call<SimulateEWalletPaymentResponse>? = null
    private var transactionId = INVALID_TRANSACTION_ID
    private var channel = ""
    private var amount = 0.0
    private var submitting = false
    private var succeeded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireValidSession(1) ?: return
        transactionId = intent.getIntExtra(EXTRA_TRANSACTION_ID, INVALID_TRANSACTION_ID)
        channel = intent.getStringExtra(EXTRA_PAYMENT_CHANNEL).orEmpty().lowercase()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_ewallet_payment)
        bindViews()
        applySystemBarInsets()
        findViewById<View>(R.id.ewalletBackButton).setOnClickListener { finish() }
        findViewById<View>(R.id.ewalletRetryButton).setOnClickListener { loadTransaction() }
        submitButton.setOnClickListener { processPayment() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!succeeded) finish()
            }
        })

        if (transactionId > 0 && PaymentPresentation.channelLabel(channel) != null) {
            loadTransaction()
        } else {
            loading.visibility = View.GONE
            showError(getString(R.string.payment_invalid_transaction))
        }
    }

    private fun bindViews() {
        loading = findViewById(R.id.ewalletLoading)
        errorState = findViewById(R.id.ewalletErrorState)
        errorMessage = findViewById(R.id.ewalletErrorMessage)
        content = findViewById(R.id.ewalletContent)
        title = findViewById(R.id.ewalletTitle)
        transactionCode = findViewById(R.id.ewalletTransactionCode)
        total = findViewById(R.id.ewalletTotal)
        method = findViewById(R.id.ewalletMethod)
        description = findViewById(R.id.ewalletDescription)
        submitButton = findViewById(R.id.ewalletSubmitButton)
    }

    private fun loadTransaction() {
        detailCall?.cancel()
        loading.visibility = View.VISIBLE
        errorState.visibility = View.GONE
        content.visibility = View.GONE
        detailCall = RetrofitClient.apiService.getCustomerOrderDetail(transactionId).also { call ->
            call.enqueue(object : Callback<CustomerOrderDetailResponse> {
                override fun onResponse(call: Call<CustomerOrderDetailResponse>, response: Response<CustomerOrderDetailResponse>) {
                    if (isFinishing || isDestroyed) return
                    loading.visibility = View.GONE
                    val body = response.body()
                    val data = body?.data
                    if (response.isSuccessful && body?.success == true && data != null &&
                        PaymentPresentation.canPay(data.paymentStatus, data.laundryStatus)) {
                        amount = data.totalPrice
                        renderConfirmation(data.transactionCode)
                    } else {
                        showError(serverMessage(response) ?: getString(R.string.payment_already_paid))
                    }
                }

                override fun onFailure(call: Call<CustomerOrderDetailResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    loading.visibility = View.GONE
                    showError(getString(R.string.payment_load_error))
                }
            })
        }
    }

    private fun renderConfirmation(code: String) {
        val channelLabel = requireNotNull(PaymentPresentation.channelLabel(channel))
        title.text = getString(R.string.payment_ewallet_title_format, channelLabel)
        transactionCode.text = if (code.startsWith('#')) code else "#$code"
        total.text = PaymentPresentation.formatRupiah(amount)
        method.text = channelLabel
        description.setText(R.string.payment_ewallet_demo_description)
        submitButton.setText(R.string.payment_ewallet_process)
        submitButton.isEnabled = true
        content.visibility = View.VISIBLE
    }

    private fun processPayment() {
        if (submitting || succeeded || transactionId <= 0) return
        submitting = true
        submitButton.isEnabled = false
        paymentCall = RetrofitClient.apiService.simulateEWalletPayment(
            SimulateEWalletPaymentRequest(transactionId, channel)
        ).also { call ->
            call.enqueue(object : Callback<SimulateEWalletPaymentResponse> {
                override fun onResponse(call: Call<SimulateEWalletPaymentResponse>, response: Response<SimulateEWalletPaymentResponse>) {
                    if (isFinishing || isDestroyed) return
                    submitting = false
                    val body = response.body()
                    val data = body?.data
                    if (response.isSuccessful && body?.success == true && data != null &&
                        data.metode == "e_wallet" && data.paymentChannel == channel &&
                        data.status == "berhasil" && data.transactionPaymentStatus == "sudah_dibayar") {
                        amount = data.jumlah
                        renderSuccess()
                    } else {
                        submitButton.isEnabled = true
                        Toast.makeText(
                            this@EWalletPaymentActivity,
                            serverMessage(response) ?: getString(R.string.payment_ewallet_error),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailure(call: Call<SimulateEWalletPaymentResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    submitting = false
                    submitButton.isEnabled = true
                    Toast.makeText(this@EWalletPaymentActivity, R.string.payment_ewallet_error, Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun renderSuccess() {
        succeeded = true
        findViewById<View>(R.id.ewalletBackButton).visibility = View.INVISIBLE
        title.setText(R.string.payment_ewallet_success_title)
        transactionCode.visibility = View.GONE
        total.text = PaymentPresentation.formatRupiah(amount)
        method.text = requireNotNull(PaymentPresentation.channelLabel(channel))
        description.setText(R.string.payment_ewallet_success_description)
        submitButton.setText(R.string.payment_ewallet_return_dashboard)
        submitButton.isEnabled = true
        submitButton.setOnClickListener { returnToDashboard() }
        setResult(RESULT_OK)
    }

    private fun returnToDashboard() {
        startActivity(Intent(this, DashboardCustomerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun showError(message: String) {
        content.visibility = View.GONE
        errorMessage.text = message
        errorState.visibility = View.VISIBLE
    }

    private fun serverMessage(response: Response<*>): String? = runCatching {
        JSONObject(response.errorBody()?.string().orEmpty()).optString("message").takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun applySystemBarInsets() {
        val header = findViewById<View>(R.id.ewalletHeader)
        val body = findViewById<View>(R.id.ewalletBody)
        val headerTop = header.paddingTop
        val bodyBottom = body.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ewalletRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.setPadding(header.paddingLeft, headerTop + bars.top, header.paddingRight, header.paddingBottom)
            body.setPadding(body.paddingLeft, body.paddingTop, body.paddingRight, bodyBottom + bars.bottom)
            insets
        }
    }

    override fun onDestroy() {
        detailCall?.cancel()
        paymentCall?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TRANSACTION_ID = "extra_transaction_id"
        const val EXTRA_PAYMENT_CHANNEL = "extra_payment_channel"
        private const val INVALID_TRANSACTION_ID = -1
    }
}
