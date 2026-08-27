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
import com.example.laundryku.model.CashierTransactionData
import com.example.laundryku.model.CashierTransactionsResponse
import com.example.laundryku.model.ConfirmCashPaymentRequest
import com.example.laundryku.model.ConfirmCashPaymentResponse
import com.example.laundryku.model.CompleteTransactionRequest
import com.example.laundryku.model.CompleteTransactionResponse
import com.example.laundryku.network.RetrofitClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.NumberFormat
import java.util.Locale

class CashierTransactionActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var transactionList: LinearLayout
    private lateinit var loading: View
    private lateinit var errorState: View
    private lateinit var emptyState: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyDescription: TextView
    private lateinit var scroll: NestedScrollView

    private var transactionsCall: Call<CashierTransactionsResponse>? = null
    private var confirmCall: Call<ConfirmCashPaymentResponse>? = null
    private var completeCall: Call<CompleteTransactionResponse>? = null
    private var allTransactions: List<CashierTransactionData> = emptyList()
    private var activeFilter = CashierTransactionFilter.ALL
    private var searchQuery = ""
    private var confirmingTransactionId: Int? = null
    private var completingTransactionId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = requireValidSession(2) ?: return
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_cashier_transactions)
        bindViews()
        applySystemBarInsets()
        bindActions()
        selectFilter(CashierTransactionFilter.ALL)
        loadTransactions()
    }

    private fun bindViews() {
        transactionList = findViewById(R.id.cashierTransactionsList)
        loading = findViewById(R.id.cashierTransactionsLoading)
        errorState = findViewById(R.id.cashierTransactionsErrorState)
        emptyState = findViewById(R.id.cashierTransactionsEmptyState)
        emptyTitle = findViewById(R.id.cashierTransactionsEmptyTitle)
        emptyDescription = findViewById(R.id.cashierTransactionsEmptyDescription)
        scroll = findViewById(R.id.cashierTransactionsScroll)
    }

    private fun bindActions() {
        findViewById<View>(R.id.cashierTransactionsNavHome).setOnClickListener {
            openScreen(CashierDashboardActivity::class.java)
        }
        findViewById<View>(R.id.cashierTransactionsNavCustomers).setOnClickListener {
            openScreen(CashierCustomerActivity::class.java)
        }
        findViewById<View>(R.id.cashierTransactionsNavProfile).setOnClickListener { openProfileForLevel(2) }
        findViewById<View>(R.id.cashierTransactionsRetryButton).setOnClickListener { loadTransactions() }

        listOf(
            R.id.cashierTransactionsFilterAll to CashierTransactionFilter.ALL,
            R.id.cashierTransactionsFilterUnpaid to CashierTransactionFilter.UNPAID,
            R.id.cashierTransactionsFilterPaid to CashierTransactionFilter.PAID,
            R.id.cashierTransactionsFilterReady to CashierTransactionFilter.READY,
            R.id.cashierTransactionsFilterCompleted to CashierTransactionFilter.COMPLETED
        ).forEach { (viewId, filter) ->
            findViewById<View>(viewId).setOnClickListener { selectFilter(filter) }
        }

        findViewById<TextInputEditText>(R.id.cashierTransactionsSearchInput)
            .addTextChangedListener { value ->
                searchQuery = value?.toString().orEmpty()
                if (loading.visibility != View.VISIBLE && errorState.visibility != View.VISIBLE) {
                    renderTransactions()
                }
            }
    }

    private fun loadTransactions() {
        transactionsCall?.cancel()
        loading.visibility = View.VISIBLE
        errorState.visibility = View.GONE
        emptyState.visibility = View.GONE
        transactionList.removeAllViews()

        transactionsCall = RetrofitClient.apiService
            .getCashierTransactions()
            .also { call ->
                call.enqueue(object : Callback<CashierTransactionsResponse> {
                    override fun onResponse(
                        call: Call<CashierTransactionsResponse>,
                        response: Response<CashierTransactionsResponse>
                    ) {
                        if (isFinishing || isDestroyed) return
                        loading.visibility = View.GONE
                        val body = response.body()
                        if (response.isSuccessful && body?.success == true) {
                            allTransactions = body.data.orEmpty()
                            renderTransactions()
                        } else {
                            showLoadError(serverMessage(response))
                        }
                    }

                    override fun onFailure(call: Call<CashierTransactionsResponse>, throwable: Throwable) {
                        if (call.isCanceled || isFinishing || isDestroyed) return
                        loading.visibility = View.GONE
                        showLoadError(null)
                    }
                })
            }
    }

    private fun showLoadError(message: String?) {
        allTransactions = emptyList()
        transactionList.removeAllViews()
        emptyState.visibility = View.GONE
        errorState.visibility = View.VISIBLE
        if (!message.isNullOrBlank()) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun selectFilter(filter: CashierTransactionFilter) {
        activeFilter = filter
        listOf(
            R.id.cashierTransactionsFilterAll to CashierTransactionFilter.ALL,
            R.id.cashierTransactionsFilterUnpaid to CashierTransactionFilter.UNPAID,
            R.id.cashierTransactionsFilterPaid to CashierTransactionFilter.PAID,
            R.id.cashierTransactionsFilterReady to CashierTransactionFilter.READY,
            R.id.cashierTransactionsFilterCompleted to CashierTransactionFilter.COMPLETED
        ).forEach { (viewId, tabFilter) ->
            findViewById<TextView>(viewId).apply {
                val active = filter == tabFilter
                setBackgroundResource(if (active) R.drawable.bg_order_filter_active else android.R.color.transparent)
                setTextColor(getColor(if (active) R.color.laundry_on_primary else R.color.laundry_text_secondary))
            }
        }
        if (loading.visibility != View.VISIBLE && errorState.visibility != View.VISIBLE) {
            renderTransactions()
            scroll.post { scroll.scrollTo(0, 0) }
        }
    }

    private fun renderTransactions() {
        transactionList.removeAllViews()
        errorState.visibility = View.GONE
        val visibleTransactions = CashierTransactionPresentation.filter(
            allTransactions,
            activeFilter,
            searchQuery
        )
        if (visibleTransactions.isEmpty()) {
            val completelyEmpty = allTransactions.isEmpty()
            emptyTitle.setText(
                if (completelyEmpty) R.string.cashier_transactions_empty_title
                else R.string.cashier_transactions_filter_empty_title
            )
            emptyDescription.setText(
                if (completelyEmpty) R.string.cashier_transactions_empty_description
                else R.string.cashier_transactions_filter_empty_description
            )
            emptyState.visibility = View.VISIBLE
            return
        }

        emptyState.visibility = View.GONE
        visibleTransactions.forEach { transaction ->
            transactionList.addView(createTransactionCard(transaction))
        }
    }

    private fun createTransactionCard(transaction: CashierTransactionData): View {
        val card = LayoutInflater.from(this).inflate(R.layout.item_cashier_transaction, transactionList, false)
        card.findViewById<TextView>(R.id.cashierTransactionCode).text = displayCode(transaction.transactionCode)
        card.findViewById<TextView>(R.id.cashierTransactionCustomer).text = transaction.customerName
        card.findViewById<TextView>(R.id.cashierTransactionServiceQuantity).text = getString(
            R.string.cashier_transaction_service_quantity_format,
            transaction.serviceName,
            CashierTransactionPresentation.quantity(transaction.qty, transaction.satuan)
        )
        card.findViewById<TextView>(R.id.cashierTransactionTotal).text = formatCurrency(transaction.totalPrice)

        card.findViewById<TextView>(R.id.cashierTransactionLaundryStatus).apply {
            val style = laundryStatusStyle(transaction.laundryStatus)
            setText(style.label)
            setBackgroundResource(style.background)
            setTextColor(getColor(style.textColor))
        }
        card.findViewById<TextView>(R.id.cashierTransactionPaymentStatus).apply {
            val paid = transaction.paymentStatus == "sudah_dibayar"
            setText(if (paid) R.string.cashier_transaction_paid else R.string.cashier_transaction_unpaid)
            setBackgroundResource(if (paid) R.drawable.bg_status_done else R.drawable.bg_order_status_waiting)
            setTextColor(getColor(if (paid) R.color.dashboard_success else R.color.order_waiting))
        }
        card.findViewById<TextView>(R.id.cashierTransactionPaymentMethod).text = getString(
            R.string.cashier_transaction_method_format,
            paymentMethodLabel(transaction)
        )

        val canConfirm = CashierTransactionPresentation.canConfirmCash(transaction)
        card.findViewById<View>(R.id.cashierTransactionPaymentNote).visibility =
            if (canConfirm) View.VISIBLE else View.GONE
        card.findViewById<MaterialButton>(R.id.cashierTransactionConfirmPayment).apply {
            visibility = if (canConfirm) View.VISIBLE else View.GONE
            isEnabled = confirmingTransactionId == null && completingTransactionId == null
            if (canConfirm) setOnClickListener { showConfirmDialog(transaction) }
        }

        card.findViewById<View>(R.id.cashierTransactionCompletionNote).visibility =
            if (CashierTransactionPresentation.isWaitingForPaymentBeforeCompletion(transaction)) {
                View.VISIBLE
            } else {
                View.GONE
            }
        val canComplete = CashierTransactionPresentation.canComplete(transaction)
        card.findViewById<MaterialButton>(R.id.cashierTransactionCompleteButton).apply {
            visibility = if (canComplete) View.VISIBLE else View.GONE
            isEnabled = confirmingTransactionId == null && completingTransactionId == null
            if (canComplete) setOnClickListener { showCompleteDialog(transaction) }
        }
        return card
    }

    private fun showConfirmDialog(transaction: CashierTransactionData) {
        if (confirmingTransactionId != null || completingTransactionId != null) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.cashier_confirm_payment_title)
            .setMessage(getString(R.string.cashier_confirm_payment_message, formatCurrency(transaction.totalPrice)))
            .setNegativeButton(R.string.cashier_confirm_payment_cancel, null)
            .setPositiveButton(R.string.cashier_confirm_payment_received) { _, _ ->
                confirmCashPayment(transaction.transactionId)
            }
            .show()
    }

    private fun confirmCashPayment(transactionId: Int) {
        if (confirmingTransactionId != null || completingTransactionId != null) return
        confirmingTransactionId = transactionId
        renderTransactions()
        confirmCall = RetrofitClient.apiService.confirmCashPayment(
            ConfirmCashPaymentRequest(transactionId)
        ).also { call ->
            call.enqueue(object : Callback<ConfirmCashPaymentResponse> {
                override fun onResponse(
                    call: Call<ConfirmCashPaymentResponse>,
                    response: Response<ConfirmCashPaymentResponse>
                ) {
                    if (isFinishing || isDestroyed) return
                    confirmingTransactionId = null
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true) {
                        Toast.makeText(
                            this@CashierTransactionActivity,
                            R.string.cashier_confirm_payment_success,
                            Toast.LENGTH_SHORT
                        ).show()
                        loadTransactions()
                    } else {
                        renderTransactions()
                        Toast.makeText(
                            this@CashierTransactionActivity,
                            serverMessage(response) ?: getString(R.string.cashier_confirm_payment_error),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailure(call: Call<ConfirmCashPaymentResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    confirmingTransactionId = null
                    renderTransactions()
                    Toast.makeText(
                        this@CashierTransactionActivity,
                        R.string.cashier_confirm_payment_error,
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
        }
    }

    private fun showCompleteDialog(transaction: CashierTransactionData) {
        if (confirmingTransactionId != null || completingTransactionId != null) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.cashier_complete_title)
            .setMessage(R.string.cashier_complete_message)
            .setNegativeButton(R.string.cashier_complete_cancel, null)
            .setPositiveButton(R.string.cashier_complete_confirm) { _, _ ->
                completeTransaction(transaction.transactionId)
            }
            .show()
    }

    private fun completeTransaction(transactionId: Int) {
        if (confirmingTransactionId != null || completingTransactionId != null) return
        completingTransactionId = transactionId
        renderTransactions()
        completeCall = RetrofitClient.apiService.completeTransaction(
            CompleteTransactionRequest(transactionId)
        ).also { call ->
            call.enqueue(object : Callback<CompleteTransactionResponse> {
                override fun onResponse(
                    call: Call<CompleteTransactionResponse>,
                    response: Response<CompleteTransactionResponse>
                ) {
                    if (isFinishing || isDestroyed) return
                    completingTransactionId = null
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true) {
                        Toast.makeText(
                            this@CashierTransactionActivity,
                            R.string.cashier_complete_success,
                            Toast.LENGTH_SHORT
                        ).show()
                        loadTransactions()
                    } else {
                        renderTransactions()
                        Toast.makeText(
                            this@CashierTransactionActivity,
                            serverMessage(response) ?: getString(R.string.cashier_complete_error),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailure(call: Call<CompleteTransactionResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    completingTransactionId = null
                    renderTransactions()
                    Toast.makeText(
                        this@CashierTransactionActivity,
                        R.string.cashier_complete_error,
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
        }
    }

    private fun paymentMethodLabel(transaction: CashierTransactionData): String = when (transaction.paymentMethod) {
        "cash" -> "Cash"
        "qris" -> "QRIS"
        "e_wallet" -> PaymentPresentation.paymentMethodLabel("e_wallet", transaction.paymentChannel) ?: "E-Wallet"
        "paylater" -> "PayLater"
        else -> getString(R.string.cashier_payment_method_unselected)
    }

    private fun displayCode(code: String): String = if (code.startsWith('#')) code else "#$code"

    private fun formatCurrency(value: Double): String = NumberFormat
        .getCurrencyInstance(INDONESIAN_LOCALE)
        .apply {
            maximumFractionDigits = if (value % 1.0 == 0.0) 0 else 2
            minimumFractionDigits = 0
        }
        .format(value)
        .replace('\u00a0', ' ')

    private fun laundryStatusStyle(status: String): StatusStyle = when (status) {
        "menunggu" -> StatusStyle(R.string.staff_status_waiting, R.drawable.bg_order_status_waiting, R.color.order_waiting)
        "dicuci" -> StatusStyle(R.string.staff_status_washing, R.drawable.bg_order_status_washing, R.color.laundry_primary_dark)
        "dikeringkan" -> StatusStyle(R.string.staff_status_drying, R.drawable.bg_order_status_drying, R.color.order_drying)
        "disetrika" -> StatusStyle(R.string.staff_status_ironing, R.drawable.bg_order_status_ironing, R.color.order_ironing)
        "dipacking" -> StatusStyle(R.string.staff_status_packing, R.drawable.bg_order_status_packing, R.color.order_packing)
        "siap_diambil" -> StatusStyle(R.string.staff_status_ready, R.drawable.bg_status_done, R.color.dashboard_success)
        "selesai" -> StatusStyle(R.string.customer_history_completed, R.drawable.bg_status_done, R.color.dashboard_success)
        "dibatalkan" -> StatusStyle(R.string.customer_history_cancelled, R.drawable.bg_history_cancelled, R.color.history_cancelled)
        else -> StatusStyle(R.string.staff_status_waiting, R.drawable.bg_order_status_waiting, R.color.order_waiting)
    }

    private fun serverMessage(response: Response<*>): String? = runCatching {
        JSONObject(response.errorBody()?.string().orEmpty()).optString("message").takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun applySystemBarInsets() {
        val header = findViewById<View>(R.id.cashierTransactionsHeader)
        val navigation = findViewById<View>(R.id.cashierTransactionsBottomNavigation)
        val headerTop = header.paddingTop
        val navigationBottom = navigation.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.cashierTransactionsRoot)) { _, insets ->
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
        transactionsCall?.cancel()
        confirmCall?.cancel()
        completeCall?.cancel()
        super.onDestroy()
    }

    private data class StatusStyle(val label: Int, val background: Int, val textColor: Int)

    companion object {
        private val INDONESIAN_LOCALE = Locale.forLanguageTag("id-ID")
    }
}
