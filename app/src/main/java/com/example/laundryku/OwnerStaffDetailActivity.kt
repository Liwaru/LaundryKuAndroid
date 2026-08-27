package com.example.laundryku

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.laundryku.model.OwnerStaffDetailResponse
import com.example.laundryku.network.RetrofitClient
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class OwnerStaffDetailActivity : AppCompatActivity() {
    private var detailCall: Call<OwnerStaffDetailResponse>? = null
    private var staffId = INVALID_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireValidSession(4) ?: return
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_owner_staff_detail)
        applyInsets()
        findViewById<View>(R.id.ownerStaffDetailBack).setOnClickListener { finish() }
        findViewById<View>(R.id.ownerStaffDetailRetry).setOnClickListener { loadDetail() }
        staffId = intent.getIntExtra(EXTRA_STAFF_ID, INVALID_ID)
        if (staffId > 0) loadDetail() else showError(getString(R.string.management_invalid_target))
    }

    private fun loadDetail() {
        if (staffId <= 0) {
            showError(getString(R.string.management_invalid_target))
            return
        }
        detailCall?.cancel()
        findViewById<View>(R.id.ownerStaffDetailContent).visibility = View.GONE
        findViewById<View>(R.id.ownerStaffDetailError).visibility = View.GONE
        findViewById<View>(R.id.ownerStaffDetailLoading).visibility = View.VISIBLE
        detailCall = RetrofitClient.apiService.getOwnerStaffDetail(staffId).also { call ->
            call.enqueue(object : Callback<OwnerStaffDetailResponse> {
                override fun onResponse(call: Call<OwnerStaffDetailResponse>, response: Response<OwnerStaffDetailResponse>) {
                    if (isFinishing || isDestroyed) return
                    findViewById<View>(R.id.ownerStaffDetailLoading).visibility = View.GONE
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true && body.data != null) {
                        val staff = body.data.staff
                        findViewById<TextView>(R.id.ownerStaffDetailName).text = staff.nama
                        findViewById<TextView>(R.id.ownerStaffDetailUsername).text = staff.username
                        findViewById<TextView>(R.id.ownerStaffDetailPhone).text = staff.phone
                        findViewById<TextView>(R.id.ownerStaffDetailRole).text =
                            OwnerStaffPresentation.roleLabel(staff.level)
                        findViewById<TextView>(R.id.ownerStaffDetailStatus).text =
                            OwnerStaffPresentation.statusLabel(staff.accountStatus)
                        findViewById<TextView>(R.id.ownerStaffDetailCreated).text =
                            ManagementDetailPresentation.date(staff.createdAt)
                        findViewById<View>(R.id.ownerStaffDetailContent).visibility = View.VISIBLE
                    } else {
                        showError(serverMessage(response) ?: getString(R.string.owner_staff_detail_error))
                    }
                }

                override fun onFailure(call: Call<OwnerStaffDetailResponse>, throwable: Throwable) {
                    if (!call.isCanceled && !isFinishing && !isDestroyed) {
                        showError(getString(R.string.owner_staff_detail_error))
                    }
                }
            })
        }
    }

    private fun showError(message: String) {
        findViewById<View>(R.id.ownerStaffDetailLoading).visibility = View.GONE
        findViewById<View>(R.id.ownerStaffDetailContent).visibility = View.GONE
        findViewById<View>(R.id.ownerStaffDetailError).visibility = View.VISIBLE
        findViewById<TextView>(R.id.ownerStaffDetailErrorMessage).text = message
    }

    private fun serverMessage(response: Response<*>): String? = runCatching {
        JSONObject(response.errorBody()?.string().orEmpty()).optString("message").takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun applyInsets() {
        val header = findViewById<View>(R.id.ownerStaffDetailHeader)
        val top = header.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ownerStaffDetailRoot)) { _, insets ->
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
        const val EXTRA_STAFF_ID = "id_staff"
        private const val INVALID_ID = -1
    }
}
