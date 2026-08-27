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
import com.example.laundryku.model.UpdateProfileRequest
import com.example.laundryku.model.UpdateProfileResponse
import com.example.laundryku.network.RetrofitClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class EditProfileActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private var updateCall: Call<UpdateProfileResponse>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = requireValidSession() ?: return
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_edit_profile)
        applyInsets()
        findViewById<View>(R.id.editProfileBackButton).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.editProfileSubmitButton).setOnClickListener { submit() }
        if (savedInstanceState == null) populateCurrentProfile()
    }

    private fun populateCurrentProfile() {
        findViewById<TextInputEditText>(R.id.editProfileNameInput).setText(session.getNama())
        findViewById<TextInputEditText>(R.id.editProfileUsernameInput).setText(session.getUsername())
        findViewById<TextInputEditText>(R.id.editProfilePhoneInput).setText(session.getNoHp())
    }

    private fun submit() {
        val nameLayout = findViewById<TextInputLayout>(R.id.editProfileNameLayout)
        val usernameLayout = findViewById<TextInputLayout>(R.id.editProfileUsernameLayout)
        val phoneLayout = findViewById<TextInputLayout>(R.id.editProfilePhoneLayout)
        val name = findViewById<TextInputEditText>(R.id.editProfileNameInput).text?.toString()?.trim().orEmpty()
        val username = findViewById<TextInputEditText>(R.id.editProfileUsernameInput).text?.toString()?.trim().orEmpty()
        val phone = findViewById<TextInputEditText>(R.id.editProfilePhoneInput).text?.toString()?.trim().orEmpty()
        listOf(nameLayout, usernameLayout, phoneLayout).forEach { it.error = null }

        val errors = ProfileValidation.editErrors(name, username, phone)
        nameLayout.error = when (errors.name) {
            InputError.REQUIRED -> getString(R.string.auth_required)
            InputError.TOO_LONG -> getString(R.string.edit_profile_name_too_long)
            else -> null
        }
        usernameLayout.error = when (errors.username) {
            InputError.REQUIRED -> getString(R.string.auth_required)
            InputError.TOO_LONG -> getString(R.string.auth_username_too_long)
            else -> null
        }
        phoneLayout.error = when (errors.phone) {
            InputError.REQUIRED -> getString(R.string.auth_required)
            InputError.INVALID -> getString(R.string.auth_phone_invalid)
            else -> null
        }
        if (!errors.isValid) return

        setLoading(true)
        updateCall = RetrofitClient.apiService.updateProfile(
            UpdateProfileRequest(name, username, phone)
        ).also { call ->
            call.enqueue(object : Callback<UpdateProfileResponse> {
                override fun onResponse(call: Call<UpdateProfileResponse>, response: Response<UpdateProfileResponse>) {
                    if (isFinishing || isDestroyed) return
                    updateCall = null
                    setLoading(false)
                    val body = response.body()
                    val profile = body?.data
                    if (response.isSuccessful && body?.success == true && profile != null) {
                        if (!session.updateProfile(profile.nama, profile.phone, profile.username)) {
                            Toast.makeText(this@EditProfileActivity, R.string.session_save_failed, Toast.LENGTH_LONG).show()
                            return
                        }
                        Toast.makeText(this@EditProfileActivity, body.message, Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(
                            this@EditProfileActivity,
                            serverMessage(response, body?.message) ?: getString(R.string.edit_profile_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailure(call: Call<UpdateProfileResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    updateCall = null
                    setLoading(false)
                    Toast.makeText(this@EditProfileActivity, R.string.auth_network_error, Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun setLoading(loading: Boolean) {
        findViewById<MaterialButton>(R.id.editProfileSubmitButton).isEnabled = !loading
        findViewById<View>(R.id.editProfileLoading).visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun serverMessage(response: Response<*>, fallback: String?): String? =
        fallback?.takeIf { it.isNotBlank() } ?: runCatching {
            JSONObject(response.errorBody()?.string().orEmpty()).optString("message").takeIf { it.isNotBlank() }
        }.getOrNull()

    private fun applyInsets() {
        val header = findViewById<View>(R.id.editProfileHeader)
        val headerTop = header.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.editProfileRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.setPadding(header.paddingLeft, headerTop + bars.top, header.paddingRight, header.paddingBottom)
            insets
        }
    }

    override fun onDestroy() {
        updateCall?.cancel()
        super.onDestroy()
    }
}
