package com.example.laundryku

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.laundryku.model.PaymentStatusResponse
import com.example.laundryku.network.RetrofitClient
import com.google.android.material.button.MaterialButton
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

class QrisPaymentActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val imageExecutor = Executors.newSingleThreadExecutor()
    private lateinit var qrImage: ImageView
    private lateinit var qrLoading: ProgressBar
    private lateinit var qrError: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusDescription: TextView
    private lateinit var checkStatusButton: MaterialButton
    private var statusCall: Call<PaymentStatusResponse>? = null
    private var transactionId = -1
    private var pollCount = 0
    private var terminal = false
    private var pageActive = false

    private val pollRunnable = Runnable { refreshStatus(showError = false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireValidSession(1) ?: return
        transactionId = intent.getIntExtra(EXTRA_TRANSACTION_ID, -1)
        val qrUrl = intent.getStringExtra(EXTRA_QR_URL).orEmpty()
        if (transactionId <= 0 || !isTrustedQrisUrl(qrUrl)) {
            finish()
            return
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_qris_payment)
        bindViews()
        applyInsets()

        findViewById<View>(R.id.qrisBackButton).setOnClickListener { finish() }
        findViewById<TextView>(R.id.qrisTransactionCode).text = displayCode(
            intent.getStringExtra(EXTRA_TRANSACTION_CODE).orEmpty()
        )
        findViewById<TextView>(R.id.qrisTotal).text = PaymentPresentation.formatRupiah(
            intent.getDoubleExtra(EXTRA_TOTAL, 0.0)
        )
        intent.getStringExtra(EXTRA_EXPIRY_TIME)?.takeIf { it.isNotBlank() }?.let {
            findViewById<TextView>(R.id.qrisExpiry).apply {
                text = getString(R.string.payment_qris_expiry_format, it)
                visibility = View.VISIBLE
            }
        }
        checkStatusButton.setOnClickListener { refreshStatus(showError = true) }
        qrError.setOnClickListener { loadQrImage(qrUrl) }
        renderState(QrisUiState.PENDING)
        loadQrImage(qrUrl)
    }

    private fun bindViews() {
        qrImage = findViewById(R.id.qrisImage)
        qrLoading = findViewById(R.id.qrisImageLoading)
        qrError = findViewById(R.id.qrisImageError)
        statusTitle = findViewById(R.id.qrisStatusTitle)
        statusDescription = findViewById(R.id.qrisStatusDescription)
        checkStatusButton = findViewById(R.id.qrisCheckStatusButton)
    }

    private fun loadQrImage(url: String) {
        qrLoading.visibility = View.VISIBLE
        qrError.visibility = View.GONE
        qrImage.visibility = View.INVISIBLE
        imageExecutor.execute {
            val bitmap = runCatching { downloadBitmap(url) }.getOrNull()
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                qrLoading.visibility = View.GONE
                if (bitmap != null) {
                    qrImage.setImageBitmap(bitmap)
                    qrImage.visibility = View.VISIBLE
                } else {
                    qrError.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun downloadBitmap(url: String): Bitmap? {
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "image/png,image/*")
        return try {
            connection.connect()
            if (connection.responseCode != HttpsURLConnection.HTTP_OK) null
            else connection.inputStream.use(BitmapFactory::decodeStream)
        } finally {
            connection.disconnect()
        }
    }

    private fun refreshStatus(showError: Boolean) {
        if (!PaymentPresentation.shouldPollQris(pageActive, terminal, pollCount, MAX_POLLS) || statusCall != null) return
        mainHandler.removeCallbacks(pollRunnable)
        pollCount++
        checkStatusButton.isEnabled = false
        statusCall = RetrofitClient.apiService.getPaymentStatus(transactionId).also { call ->
            call.enqueue(object : Callback<PaymentStatusResponse> {
                override fun onResponse(call: Call<PaymentStatusResponse>, response: Response<PaymentStatusResponse>) {
                    statusCall = null
                    if (isFinishing || isDestroyed) return
                    checkStatusButton.isEnabled = true
                    val body = response.body()
                    val data = body?.data
                    if (response.isSuccessful && body?.success == true && data != null) {
                        renderState(PaymentPresentation.qrisState(data.transactionPaymentStatus, data.paymentStatus))
                    } else {
                        if (showError) Toast.makeText(
                            this@QrisPaymentActivity,
                            serverMessage(response) ?: getString(R.string.payment_status_error),
                            Toast.LENGTH_LONG
                        ).show()
                        scheduleNextPoll()
                    }
                }

                override fun onFailure(call: Call<PaymentStatusResponse>, throwable: Throwable) {
                    statusCall = null
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    checkStatusButton.isEnabled = true
                    if (showError) Toast.makeText(
                        this@QrisPaymentActivity,
                        R.string.payment_status_error,
                        Toast.LENGTH_LONG
                    ).show()
                    scheduleNextPoll()
                }
            })
        }
    }

    private fun renderState(state: QrisUiState) {
        terminal = state != QrisUiState.PENDING
        when (state) {
            QrisUiState.PENDING -> {
                statusTitle.setText(R.string.payment_qris_pending_status)
                statusDescription.setText(R.string.payment_qris_scan_instruction)
                checkStatusButton.setText(R.string.payment_check_status)
                checkStatusButton.setOnClickListener { refreshStatus(showError = true) }
                scheduleNextPoll()
            }
            QrisUiState.SUCCESS -> {
                setResult(RESULT_OK)
                statusTitle.setText(R.string.payment_qris_success_title)
                statusDescription.setText(R.string.payment_qris_success_description)
                qrImage.visibility = View.GONE
                qrLoading.visibility = View.GONE
                qrError.visibility = View.GONE
                checkStatusButton.setText(R.string.payment_return_to_detail)
                checkStatusButton.setOnClickListener { finish() }
            }
            QrisUiState.FAILURE -> {
                statusTitle.setText(R.string.payment_qris_failure_title)
                statusDescription.setText(R.string.payment_qris_failure_description)
                qrImage.visibility = View.GONE
                qrLoading.visibility = View.GONE
                qrError.visibility = View.GONE
                checkStatusButton.setText(R.string.payment_qris_retry_return)
                checkStatusButton.setOnClickListener { finish() }
            }
        }
    }

    private fun scheduleNextPoll() {
        mainHandler.removeCallbacks(pollRunnable)
        if (PaymentPresentation.shouldPollQris(pageActive, terminal, pollCount, MAX_POLLS)) {
            mainHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
        }
    }

    override fun onStart() {
        super.onStart()
        pageActive = true
        pollCount = 0
        refreshStatus(showError = false)
    }

    override fun onStop() {
        pageActive = false
        mainHandler.removeCallbacks(pollRunnable)
        statusCall?.cancel()
        statusCall = null
        super.onStop()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        imageExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun displayCode(code: String): String = if (code.startsWith('#')) code else "#$code"

    private fun isTrustedQrisUrl(value: String): Boolean = runCatching {
        val url = URL(value)
        url.protocol == "https" && url.host == "api.sandbox.midtrans.com"
    }.getOrDefault(false)

    private fun serverMessage(response: Response<*>): String? = runCatching {
        JSONObject(response.errorBody()?.string().orEmpty()).optString("message").takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun applyInsets() {
        val header = findViewById<View>(R.id.qrisHeader)
        val scroll = findViewById<View>(R.id.qrisScroll)
        val top = header.paddingTop
        val bottom = scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.qrisRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.setPadding(header.paddingLeft, top + bars.top, header.paddingRight, header.paddingBottom)
            scroll.setPadding(scroll.paddingLeft, scroll.paddingTop, scroll.paddingRight, bottom + bars.bottom)
            insets
        }
    }

    companion object {
        const val EXTRA_TRANSACTION_ID = "qris_transaction_id"
        const val EXTRA_TRANSACTION_CODE = "qris_transaction_code"
        const val EXTRA_TOTAL = "qris_total"
        const val EXTRA_QR_URL = "qris_qr_url"
        const val EXTRA_EXPIRY_TIME = "qris_expiry_time"
        private const val POLL_INTERVAL_MS = 5_000L
        private const val MAX_POLLS = 24
    }
}
