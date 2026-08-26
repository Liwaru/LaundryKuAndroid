package com.example.laundryku

import android.graphics.Color
import android.os.Bundle
import android.text.method.DigitsKeyListener
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.example.laundryku.model.CreateOrderRequest
import com.example.laundryku.model.CreateOrderResponse
import com.example.laundryku.model.ServiceData
import com.example.laundryku.model.ServicesResponse
import com.example.laundryku.network.RetrofitClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

class CreateOrderActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var serviceOptions: LinearLayout
    private lateinit var servicesProgress: View
    private lateinit var servicesError: View
    private lateinit var quantityLayout: TextInputLayout
    private lateinit var quantityInput: EditText
    private lateinit var minimumInfo: TextView
    private lateinit var summaryCard: View
    private lateinit var submitButton: MaterialButton

    private var servicesCall: Call<ServicesResponse>? = null
    private var orderCall: Call<CreateOrderResponse>? = null
    private var selectedService: ServiceData? = null
    private var isSubmitting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = requireValidSession(1) ?: return
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_create_order)
        bindViews()
        applySystemBarInsets()
        bindActions()
        loadServices()
    }

    private fun bindViews() {
        serviceOptions = findViewById(R.id.createOrderServiceOptions)
        servicesProgress = findViewById(R.id.createOrderServicesProgress)
        servicesError = findViewById(R.id.createOrderServicesError)
        quantityLayout = findViewById(R.id.createOrderQuantityLayout)
        quantityInput = findViewById(R.id.createOrderQuantityInput)
        minimumInfo = findViewById(R.id.createOrderMinimumInfo)
        summaryCard = findViewById(R.id.createOrderSummaryCard)
        submitButton = findViewById(R.id.createOrderSubmitButton)
    }

    private fun bindActions() {
        findViewById<View>(R.id.createOrderBackButton).setOnClickListener { finish() }
        findViewById<View>(R.id.createOrderRetryButton).setOnClickListener { loadServices() }
        quantityInput.doAfterTextChanged {
            quantityLayout.error = null
            updateSummary()
        }
        submitButton.setOnClickListener { submitOrder() }
    }

    private fun loadServices() {
        servicesCall?.cancel()
        servicesProgress.visibility = View.VISIBLE
        servicesError.visibility = View.GONE
        serviceOptions.removeAllViews()
        selectedService = null
        setQuantityEnabled(false)

        servicesCall = RetrofitClient.apiService.getServices().also { call ->
            call.enqueue(object : Callback<ServicesResponse> {
                override fun onResponse(call: Call<ServicesResponse>, response: Response<ServicesResponse>) {
                    if (isFinishing || isDestroyed) return
                    servicesProgress.visibility = View.GONE
                    val body = response.body()
                    val services = body?.data.orEmpty()
                    if (response.isSuccessful && body?.success == true && services.isNotEmpty()) {
                        renderServices(services)
                    } else {
                        servicesError.visibility = View.VISIBLE
                    }
                }

                override fun onFailure(call: Call<ServicesResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    servicesProgress.visibility = View.GONE
                    servicesError.visibility = View.VISIBLE
                }
            })
        }
    }

    private fun renderServices(services: List<ServiceData>) {
        val requestedServiceId = intent.getIntExtra(EXTRA_SERVICE_ID, -1)
        services.forEach { service ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.item_create_order_service, serviceOptions, false) as MaterialCardView
            card.tag = service
            card.findViewById<TextView>(R.id.createOrderServiceName).text = service.name
            card.findViewById<TextView>(R.id.createOrderServicePrice).text =
                getString(R.string.create_order_service_price_format, formatCurrency(service.harga), service.satuan)
            card.findViewById<TextView>(R.id.createOrderServiceMinimum).text =
                getString(
                    R.string.create_order_minimum_format,
                    formatDecimal(BigDecimal.valueOf(service.minimumOrder)),
                    service.satuan
                )
            card.findViewById<TextView>(R.id.createOrderServiceEstimate).text =
                getString(R.string.create_order_estimate_format, service.estimateDays)
            card.contentDescription = listOf(
                service.name,
                formatCurrency(service.harga) + "/" + service.satuan,
                getString(R.string.create_order_estimate_format, service.estimateDays)
            ).joinToString(", ")
            card.setOnClickListener { selectService(service, card) }
            serviceOptions.addView(card)
        }

        if (requestedServiceId > 0) {
            (0 until serviceOptions.childCount)
                .map { serviceOptions.getChildAt(it) as MaterialCardView }
                .firstOrNull { (it.tag as ServiceData).id == requestedServiceId }
                ?.let { selectService(it.tag as ServiceData, it) }
        }
    }

    private fun selectService(service: ServiceData, selectedCard: MaterialCardView) {
        selectedService = service
        for (index in 0 until serviceOptions.childCount) {
            val card = serviceOptions.getChildAt(index) as MaterialCardView
            val selected = card === selectedCard
            card.strokeColor = getColor(if (selected) R.color.laundry_primary else R.color.dashboard_divider)
            card.strokeWidth = resources.getDimensionPixelSize(if (selected) R.dimen.create_order_selected_stroke else R.dimen.create_order_default_stroke)
            card.findViewById<RadioButton>(R.id.createOrderServiceRadio).isChecked = selected
        }

        quantityInput.text?.clear()
        quantityLayout.error = null
        quantityLayout.hint = getString(
            if (service.satuan == "pcs") R.string.create_order_quantity_pcs else R.string.create_order_quantity_kg
        )
        quantityInput.hint = getString(
            if (service.satuan == "pcs") R.string.create_order_quantity_hint_pcs else R.string.create_order_quantity_hint_kg
        )
        quantityInput.keyListener = DigitsKeyListener.getInstance(
            if (service.satuan == "pcs") "0123456789" else "0123456789.,"
        )
        minimumInfo.text = getString(
            R.string.create_order_minimum_format,
            formatDecimal(BigDecimal.valueOf(service.minimumOrder)),
            service.satuan
        )
        minimumInfo.visibility = View.VISIBLE
        setQuantityEnabled(true)
        quantityInput.requestFocus()
    }

    private fun setQuantityEnabled(enabled: Boolean) {
        quantityLayout.visibility = if (enabled) View.VISIBLE else View.GONE
        quantityLayout.isEnabled = enabled
        minimumInfo.visibility = if (enabled) View.VISIBLE else View.GONE
        if (!enabled) quantityInput.text?.clear()
        summaryCard.visibility = View.GONE
        submitButton.isEnabled = false
    }

    private fun updateSummary() {
        val service = selectedService ?: return
        val quantity = OrderPriceCalculator.parseQuantity(quantityInput.text?.toString().orEmpty())
        if (quantity == null || !OrderPriceCalculator.isValidQuantity(quantity, service.satuan)) {
            summaryCard.visibility = View.GONE
            submitButton.isEnabled = false
            return
        }

        val preview = OrderPriceCalculator.calculate(
            quantity,
            BigDecimal.valueOf(service.minimumOrder),
            BigDecimal.valueOf(service.harga)
        )
        findViewById<TextView>(R.id.createOrderSummaryService).text = service.name
        findViewById<TextView>(R.id.createOrderSummaryQuantity).text =
            getString(
                R.string.create_order_quantity_value_format,
                formatDecimal(preview.actualQuantity),
                service.satuan
            )
        findViewById<TextView>(R.id.createOrderSummaryPrice).text =
            getString(R.string.create_order_service_price_format, formatCurrency(service.harga), service.satuan)
        findViewById<TextView>(R.id.createOrderSummaryEstimate).text =
            getString(R.string.create_order_estimate_format, service.estimateDays)
        findViewById<TextView>(R.id.createOrderSummaryTotal).text = formatCurrency(preview.total.toDouble())
        summaryCard.visibility = View.VISIBLE
        submitButton.isEnabled = !isSubmitting
    }

    private fun submitOrder() {
        if (isSubmitting) return
        val service = selectedService
        if (service == null) {
            Toast.makeText(this, R.string.create_order_select_service_error, Toast.LENGTH_SHORT).show()
            return
        }
        val raw = quantityInput.text?.toString().orEmpty()
        val quantity = OrderPriceCalculator.parseQuantity(raw)
        when {
            raw.isBlank() || quantity == null -> quantityLayout.error = getString(R.string.create_order_quantity_required)
            quantity <= BigDecimal.ZERO -> quantityLayout.error = getString(R.string.create_order_quantity_positive)
            !OrderPriceCalculator.isValidQuantity(quantity, service.satuan) ->
                quantityLayout.error = getString(R.string.create_order_quantity_integer)
            else -> sendOrder(service, quantity)
        }
    }

    private fun sendOrder(service: ServiceData, quantity: BigDecimal) {
        setSubmitting(true)
        orderCall = RetrofitClient.apiService.createOrder(
            CreateOrderRequest(service.id, quantity.toDouble())
        ).also { call ->
            call.enqueue(object : Callback<CreateOrderResponse> {
                override fun onResponse(call: Call<CreateOrderResponse>, response: Response<CreateOrderResponse>) {
                    if (isFinishing || isDestroyed) return
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true) {
                        Toast.makeText(
                            this@CreateOrderActivity,
                            body.message.ifBlank { getString(R.string.create_order_success) },
                            Toast.LENGTH_SHORT
                        ).show()
                        setResult(RESULT_OK)
                        finish()
                        return
                    }
                    setSubmitting(false)
                    Toast.makeText(
                        this@CreateOrderActivity,
                        serverMessage(response) ?: getString(R.string.auth_invalid_response),
                        Toast.LENGTH_LONG
                    ).show()
                }

                override fun onFailure(call: Call<CreateOrderResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    setSubmitting(false)
                    Toast.makeText(
                        this@CreateOrderActivity,
                        R.string.create_order_network_error,
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
        }
    }

    private fun setSubmitting(submitting: Boolean) {
        isSubmitting = submitting
        serviceOptions.isEnabled = !submitting
        for (index in 0 until serviceOptions.childCount) {
            serviceOptions.getChildAt(index).isEnabled = !submitting
        }
        quantityLayout.isEnabled = !submitting
        submitButton.isEnabled = !submitting
        submitButton.text = getString(
            if (submitting) R.string.create_order_processing else R.string.create_order_submit
        )
        if (!submitting) updateSummary()
    }

    private fun serverMessage(response: Response<CreateOrderResponse>): String? = runCatching {
        JSONObject(response.errorBody()?.string().orEmpty()).optString("message").takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun formatCurrency(value: Double): String = NumberFormat.getCurrencyInstance(INDONESIAN_LOCALE).apply {
        maximumFractionDigits = if (value % 1.0 == 0.0) 0 else 2
        minimumFractionDigits = 0
    }.format(value).replace("Rp", "Rp").replace('\u00a0', ' ')

    private fun formatDecimal(value: BigDecimal): String =
        value.stripTrailingZeros().toPlainString().replace('.', ',')

    private fun applySystemBarInsets() {
        val header = findViewById<View>(R.id.createOrderHeader)
        val scroll = findViewById<View>(R.id.createOrderScroll)
        val headerTop = header.paddingTop
        val scrollBottom = scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.createOrderRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.setPadding(header.paddingLeft, headerTop + bars.top, header.paddingRight, header.paddingBottom)
            scroll.setPadding(scroll.paddingLeft, scroll.paddingTop, scroll.paddingRight, scrollBottom + bars.bottom)
            insets
        }
    }

    override fun onDestroy() {
        servicesCall?.cancel()
        orderCall?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SERVICE_ID = "extra_service_id"
        private val INDONESIAN_LOCALE = Locale.forLanguageTag("id-ID")
    }
}
