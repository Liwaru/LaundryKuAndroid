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
import com.example.laundryku.model.OwnerCreateStaffRequest
import com.example.laundryku.model.OwnerCreateStaffResponse
import com.example.laundryku.network.RetrofitClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AddStaffActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private var createCall: Call<OwnerCreateStaffResponse>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = requireValidSession(4) ?: return
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(R.layout.activity_add_staff)
        applySystemBarInsets()
        findViewById<View>(R.id.addStaffBackButton).setOnClickListener { finish() }
        findViewById<View>(R.id.addStaffSubmitButton).setOnClickListener { submitStaff() }
        findViewById<MaterialButtonToggleGroup>(R.id.addStaffRoleGroup)
            .addOnButtonCheckedListener { _, _, isChecked ->
                if (isChecked) findViewById<View>(R.id.addStaffRoleError).visibility = View.GONE
            }
    }

    private fun submitStaff() {
        val nameLayout = findViewById<TextInputLayout>(R.id.addStaffNameLayout)
        val phoneLayout = findViewById<TextInputLayout>(R.id.addStaffPhoneLayout)
        val usernameLayout = findViewById<TextInputLayout>(R.id.addStaffUsernameLayout)
        val passwordLayout = findViewById<TextInputLayout>(R.id.addStaffPasswordLayout)
        val name = findViewById<TextInputEditText>(R.id.addStaffNameInput).text?.toString()?.trim().orEmpty()
        val phone = findViewById<TextInputEditText>(R.id.addStaffPhoneInput).text?.toString()?.trim().orEmpty()
        val username = findViewById<TextInputEditText>(R.id.addStaffUsernameInput).text?.toString()?.trim().orEmpty()
        val password = findViewById<TextInputEditText>(R.id.addStaffPasswordInput).text?.toString().orEmpty()
        val roleGroup = findViewById<MaterialButtonToggleGroup>(R.id.addStaffRoleGroup)
        val level = when (roleGroup.checkedButtonId) {
            R.id.addStaffRoleCashier -> 2
            R.id.addStaffRoleLaundry -> 3
            else -> null
        }

        listOf(nameLayout, phoneLayout, usernameLayout, passwordLayout).forEach { it.error = null }
        findViewById<View>(R.id.addStaffRoleError).visibility = View.GONE

        var valid = true
        if (name.isEmpty()) {
            nameLayout.error = getString(R.string.auth_required)
            valid = false
        } else if (name.length > MAX_NAME_LENGTH) {
            nameLayout.error = getString(R.string.owner_add_staff_name_too_long)
            valid = false
        }
        if (phone.isEmpty()) {
            phoneLayout.error = getString(R.string.auth_required)
            valid = false
        } else if (!phone.matches(Regex("^[0-9]{1,12}$"))) {
            phoneLayout.error = getString(R.string.auth_phone_invalid)
            valid = false
        }
        if (username.isEmpty()) {
            usernameLayout.error = getString(R.string.auth_required)
            valid = false
        } else if (username.length > MAX_USERNAME_LENGTH) {
            usernameLayout.error = getString(R.string.auth_username_too_long)
            valid = false
        }
        if (password.isEmpty()) {
            passwordLayout.error = getString(R.string.auth_required)
            valid = false
        } else if (password.length > MAX_PASSWORD_LENGTH) {
            passwordLayout.error = getString(R.string.auth_password_too_long)
            valid = false
        }
        if (level == null) {
            findViewById<View>(R.id.addStaffRoleError).visibility = View.VISIBLE
            valid = false
        }
        if (!valid) return
        val selectedLevel = level ?: return

        setLoading(true)
        createCall = RetrofitClient.apiService.createOwnerStaff(
            OwnerCreateStaffRequest(session.getUserId(), name, phone, username, password, selectedLevel)
        ).also { call ->
            call.enqueue(object : Callback<OwnerCreateStaffResponse> {
                override fun onResponse(
                    call: Call<OwnerCreateStaffResponse>,
                    response: Response<OwnerCreateStaffResponse>
                ) {
                    if (isFinishing || isDestroyed) return
                    setLoading(false)
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true) {
                        Toast.makeText(
                            this@AddStaffActivity,
                            body.message.ifBlank { getString(R.string.owner_add_staff_success) },
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    } else {
                        Toast.makeText(
                            this@AddStaffActivity,
                            serverMessage(response) ?: getString(R.string.owner_add_staff_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<OwnerCreateStaffResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    setLoading(false)
                    Toast.makeText(this@AddStaffActivity, R.string.auth_network_error, Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun setLoading(isLoading: Boolean) {
        findViewById<MaterialButton>(R.id.addStaffSubmitButton).isEnabled = !isLoading
        findViewById<View>(R.id.addStaffLoading).visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun serverMessage(response: Response<*>): String? = runCatching {
        JSONObject(response.errorBody()?.string().orEmpty()).optString("message").takeIf { it.isNotBlank() }
    }.getOrNull()

    override fun onDestroy() {
        createCall?.cancel()
        super.onDestroy()
    }

    private fun applySystemBarInsets() {
        val header = findViewById<View>(R.id.addStaffHeader)
        val root = findViewById<View>(R.id.addStaffRoot)
        val headerTop = header.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.setPadding(header.paddingLeft, headerTop + bars.top, header.paddingRight, header.paddingBottom)
            insets
        }
    }

    internal companion object {
        const val MAX_NAME_LENGTH = 9
        const val MAX_USERNAME_LENGTH = 12
        const val MAX_PASSWORD_LENGTH = 16
    }
}
