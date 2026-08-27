package com.example.laundryku

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.laundryku.model.ChangePasswordRequest
import com.example.laundryku.model.ChangePasswordResponse
import com.example.laundryku.network.RetrofitClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ChangePasswordActivity : AppCompatActivity() {
    private var changeCall: Call<ChangePasswordResponse>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireValidSession() ?: return
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_change_password)
        applyInsets()
        findViewById<View>(R.id.changePasswordBackButton).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.changePasswordSubmitButton).setOnClickListener { submit() }
    }

    private fun submit() {
        val oldLayout = findViewById<TextInputLayout>(R.id.changePasswordOldLayout)
        val newLayout = findViewById<TextInputLayout>(R.id.changePasswordNewLayout)
        val confirmationLayout = findViewById<TextInputLayout>(R.id.changePasswordConfirmationLayout)
        val old = findViewById<TextInputEditText>(R.id.changePasswordOldInput).text?.toString().orEmpty()
        val new = findViewById<TextInputEditText>(R.id.changePasswordNewInput).text?.toString().orEmpty()
        val confirmation = findViewById<TextInputEditText>(R.id.changePasswordConfirmationInput).text?.toString().orEmpty()
        listOf(oldLayout, newLayout, confirmationLayout).forEach { it.error = null }

        val errors = ProfileValidation.passwordErrors(old, new, confirmation)
        oldLayout.error = if (errors.old == InputError.REQUIRED) getString(R.string.auth_required) else null
        newLayout.error = when (errors.new) {
            InputError.REQUIRED -> getString(R.string.auth_required)
            InputError.TOO_SHORT -> getString(R.string.auth_password_too_short)
            InputError.TOO_LONG -> getString(R.string.auth_password_too_long)
            InputError.SAME_AS_OLD -> getString(R.string.change_password_same)
            else -> null
        }
        confirmationLayout.error = when (errors.confirmation) {
            InputError.REQUIRED -> getString(R.string.auth_required)
            InputError.MISMATCH -> getString(R.string.change_password_confirmation_mismatch)
            else -> null
        }
        if (!errors.isValid) return

        setLoading(true)
        changeCall = RetrofitClient.apiService.changePassword(
            ChangePasswordRequest(old, new, confirmation)
        ).also { call ->
            call.enqueue(object : Callback<ChangePasswordResponse> {
                override fun onResponse(call: Call<ChangePasswordResponse>, response: Response<ChangePasswordResponse>) {
                    if (isFinishing || isDestroyed) return
                    changeCall = null
                    setLoading(false)
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true) {
                        Toast.makeText(this@ChangePasswordActivity, body.message, Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(
                            this@ChangePasswordActivity,
                            serverMessage(response, body?.message) ?: getString(R.string.change_password_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailure(call: Call<ChangePasswordResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    changeCall = null
                    setLoading(false)
                    Toast.makeText(this@ChangePasswordActivity, R.string.auth_network_error, Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun setLoading(loading: Boolean) {
        findViewById<MaterialButton>(R.id.changePasswordSubmitButton).isEnabled = !loading
        findViewById<View>(R.id.changePasswordLoading).visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun serverMessage(response: Response<*>, fallback: String?): String? =
        fallback?.takeIf { it.isNotBlank() } ?: runCatching {
            JSONObject(response.errorBody()?.string().orEmpty()).optString("message").takeIf { it.isNotBlank() }
        }.getOrNull()

    private fun applyInsets() {
        val header = findViewById<View>(R.id.changePasswordHeader)
        val headerTop = header.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.changePasswordRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.setPadding(header.paddingLeft, headerTop + bars.top, header.paddingRight, header.paddingBottom)
            insets
        }
    }

    override fun onDestroy() {
        changeCall?.cancel()
        super.onDestroy()
    }
}
