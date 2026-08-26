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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.laundryku.model.CustomerOrderDetailData
import com.example.laundryku.model.CustomerOrderDetailResponse
import com.example.laundryku.model.CustomerOrderTimelineData
import com.example.laundryku.network.RetrofitClient
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class CustomerOrderDetailActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var loading: View
    private lateinit var errorState: View
    private lateinit var errorMessage: TextView
    private lateinit var content: View
    private lateinit var detailsContainer: LinearLayout
    private lateinit var timelineContainer: LinearLayout

    private var detailCall: Call<CustomerOrderDetailResponse>? = null
    private var transactionId: Int = INVALID_TRANSACTION_ID

    private val paymentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) loadDetail()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = requireValidSession(1) ?: return
        transactionId = intent.getIntExtra(EXTRA_TRANSACTION_ID, INVALID_TRANSACTION_ID)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_customer_order_detail)
        bindViews()
        applySystemBarInsets()
        findViewById<View>(R.id.orderDetailBackButton).setOnClickListener { finish() }
        findViewById<View>(R.id.orderDetailRetryButton).setOnClickListener { loadDetail() }
        findViewById<View>(R.id.orderDetailPayButton).setOnClickListener {
            paymentLauncher.launch(Intent(this, PaymentActivity::class.java).apply {
                putExtra(PaymentActivity.EXTRA_TRANSACTION_ID, transactionId)
            })
        }

        if (transactionId > 0) {
            loadDetail()
        } else {
            showError(getString(R.string.order_detail_load_error))
        }
    }

    private fun bindViews() {
        loading = findViewById(R.id.orderDetailLoading)
        errorState = findViewById(R.id.orderDetailErrorState)
        errorMessage = findViewById(R.id.orderDetailErrorMessage)
        content = findViewById(R.id.orderDetailScroll)
        detailsContainer = findViewById(R.id.orderDetailLines)
        timelineContainer = findViewById(R.id.orderDetailTimeline)
    }

    private fun loadDetail() {
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
                        renderDetail(body.data)
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

    private fun renderDetail(data: CustomerOrderDetailData) {
        findViewById<TextView>(R.id.orderDetailHeaderCode).text = displayCode(data.transactionCode)
        findViewById<TextView>(R.id.orderDetailTotal).text = formatCurrency(data.totalPrice)
        renderLines(data)
        renderStatuses(data)
        renderDates(data)
        renderTimeline(data.timeline)
        errorState.visibility = View.GONE
        content.visibility = View.VISIBLE
    }

    private fun renderLines(data: CustomerOrderDetailData) {
        detailsContainer.removeAllViews()
        val lines = CustomerOrderDetailPresentation.lines(data)
        lines.forEach { line ->
            val item = LayoutInflater.from(this).inflate(
                R.layout.item_customer_order_detail_line,
                detailsContainer,
                false
            )
            item.findViewById<TextView>(R.id.orderDetailLineService).text = line.serviceName
            item.findViewById<TextView>(R.id.orderDetailLineQuantityLabel).setText(
                if (line.satuan == "pcs") R.string.order_detail_quantity else R.string.order_detail_weight
            )
            item.findViewById<TextView>(R.id.orderDetailLineQuantity).text =
                CustomerOrderPresentation.quantity(line.qty, line.satuan)
            item.findViewById<TextView>(R.id.orderDetailLinePrice).text = getString(
                R.string.order_detail_price_per_unit,
                formatCurrency(line.unitPrice),
                line.satuan
            )
            item.findViewById<TextView>(R.id.orderDetailLineSubtotal).text = formatCurrency(line.subtotal)
            detailsContainer.addView(item)
        }
    }

    private fun renderStatuses(data: CustomerOrderDetailData) {
        findViewById<TextView>(R.id.orderDetailLaundryStatus).apply {
            val style = statusStyle(data.laundryStatus)
            text = style.label
            setBackgroundResource(style.background)
            setTextColor(getColor(style.textColor))
        }
        findViewById<TextView>(R.id.orderDetailPaymentStatus).apply {
            val paid = data.paymentStatus == "sudah_dibayar"
            setText(if (paid) R.string.customer_order_paid else R.string.customer_order_unpaid)
            setBackgroundResource(if (paid) R.drawable.bg_status_done else R.drawable.bg_order_status_waiting)
            setTextColor(getColor(if (paid) R.color.dashboard_success else R.color.order_waiting))
        }
        val cashPayment = data.payment?.metode == "cash"
        val cashWaiting = PaymentPresentation.isCashWaiting(data.payment?.metode, data.payment?.status)
        findViewById<TextView>(R.id.orderDetailPaymentInformation).apply {
            visibility = if (cashPayment) View.VISIBLE else View.GONE
            if (cashPayment) {
                setText(if (cashWaiting) R.string.order_detail_cash_pending else R.string.order_detail_cash_method)
                setBackgroundResource(if (cashWaiting) R.drawable.bg_order_status_waiting else R.drawable.bg_status_done)
                setTextColor(getColor(if (cashWaiting) R.color.order_waiting else R.color.dashboard_success))
            }
        }
        findViewById<View>(R.id.orderDetailPayButton).visibility =
            if (PaymentPresentation.canPay(data.paymentStatus, data.laundryStatus)) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun renderDates(data: CustomerOrderDetailData) {
        findViewById<TextView>(R.id.orderDetailOrderDate).text = formatDate(data.orderDate, FULL_DATE_FORMAT)
        findViewById<TextView>(R.id.orderDetailEstimatedDate).text = data.estimatedCompletion
            ?.takeIf { it.isNotBlank() }
            ?.let { formatDate(it, FULL_DATE_FORMAT) }
            ?: getString(R.string.order_detail_date_unavailable)

        findViewById<View>(R.id.orderDetailCompletionRow).apply {
            visibility = if (data.completionDate.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        if (!data.completionDate.isNullOrBlank()) {
            findViewById<TextView>(R.id.orderDetailCompletionDate).text =
                formatDate(data.completionDate, FULL_DATE_FORMAT)
        }
    }

    private fun renderTimeline(timeline: List<CustomerOrderTimelineData>) {
        timelineContainer.removeAllViews()
        findViewById<View>(R.id.orderDetailTimelineEmpty).visibility =
            if (timeline.isEmpty()) View.VISIBLE else View.GONE

        timeline.forEach { entry ->
            val item = LayoutInflater.from(this).inflate(
                R.layout.item_customer_order_timeline,
                timelineContainer,
                false
            )
            item.findViewById<TextView>(R.id.orderTimelineStatus).text =
                CustomerOrderDetailPresentation.statusLabel(entry.laundryStatus)
            item.findViewById<TextView>(R.id.orderTimelineDate).text = formatDate(entry.waktu, TIMELINE_DATE_FORMAT)
            item.findViewById<TextView>(R.id.orderTimelineNote).apply {
                val note = entry.catatan?.trim().orEmpty()
                text = note
                visibility = if (note.isBlank()) View.GONE else View.VISIBLE
            }
            timelineContainer.addView(item)
        }
    }

    private fun showError(message: String?) {
        content.visibility = View.GONE
        errorMessage.text = message?.takeIf { it.isNotBlank() } ?: getString(R.string.order_detail_load_error)
        errorState.visibility = View.VISIBLE
    }

    private fun displayCode(code: String): String =
        if (code.startsWith('#')) code else getString(R.string.customer_order_code_format, code)

    private fun formatCurrency(value: Double): String = NumberFormat.getCurrencyInstance(INDONESIAN_LOCALE).apply {
        maximumFractionDigits = if (value % 1.0 == 0.0) 0 else 2
        minimumFractionDigits = 0
    }.format(value).replace('\u00a0', ' ')

    private fun formatDate(value: String, outputPattern: String): String = runCatching {
        val parser = SimpleDateFormat(API_DATE_FORMAT, Locale.US).apply { isLenient = false }
        SimpleDateFormat(outputPattern, INDONESIAN_LOCALE).format(requireNotNull(parser.parse(value)))
    }.getOrElse { value }

    private fun statusStyle(status: String): StatusStyle = when (status) {
        "menunggu" -> StatusStyle(CustomerOrderDetailPresentation.statusLabel(status), R.drawable.bg_order_status_waiting, R.color.order_waiting)
        "dicuci" -> StatusStyle(CustomerOrderDetailPresentation.statusLabel(status), R.drawable.bg_order_status_washing, R.color.laundry_primary_dark)
        "dikeringkan" -> StatusStyle(CustomerOrderDetailPresentation.statusLabel(status), R.drawable.bg_order_status_drying, R.color.order_drying)
        "disetrika" -> StatusStyle(CustomerOrderDetailPresentation.statusLabel(status), R.drawable.bg_order_status_ironing, R.color.order_ironing)
        "dipacking" -> StatusStyle(CustomerOrderDetailPresentation.statusLabel(status), R.drawable.bg_order_status_packing, R.color.order_packing)
        "siap_diambil", "selesai" -> StatusStyle(CustomerOrderDetailPresentation.statusLabel(status), R.drawable.bg_status_done, R.color.dashboard_success)
        "dibatalkan" -> StatusStyle(CustomerOrderDetailPresentation.statusLabel(status), R.drawable.bg_history_cancelled, R.color.history_cancelled)
        else -> StatusStyle(CustomerOrderDetailPresentation.statusLabel(status), R.drawable.bg_order_status_waiting, R.color.order_waiting)
    }

    private fun serverMessage(response: Response<CustomerOrderDetailResponse>): String? = runCatching {
        JSONObject(response.errorBody()?.string().orEmpty()).optString("message").takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun applySystemBarInsets() {
        val header = findViewById<View>(R.id.orderDetailHeader)
        val scroll = findViewById<View>(R.id.orderDetailScroll)
        val headerTop = header.paddingTop
        val scrollBottom = scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.orderDetailRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.setPadding(header.paddingLeft, headerTop + bars.top, header.paddingRight, header.paddingBottom)
            scroll.setPadding(scroll.paddingLeft, scroll.paddingTop, scroll.paddingRight, scrollBottom + bars.bottom)
            insets
        }
    }

    override fun onDestroy() {
        detailCall?.cancel()
        super.onDestroy()
    }

    private data class StatusStyle(val label: String, val background: Int, val textColor: Int)

    companion object {
        const val EXTRA_TRANSACTION_ID = "extra_transaction_id"
        private const val INVALID_TRANSACTION_ID = -1
        private val INDONESIAN_LOCALE = Locale.forLanguageTag("id-ID")
        private const val API_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"
        private const val FULL_DATE_FORMAT = "d MMMM yyyy"
        private const val TIMELINE_DATE_FORMAT = "d MMM yyyy, HH:mm"
    }
}
