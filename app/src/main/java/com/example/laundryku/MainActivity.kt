package com.example.laundryku

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.widget.NestedScrollView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.example.laundryku.model.LoginRequest
import com.example.laundryku.model.LoginResponse
import com.example.laundryku.model.RegisterRequest
import com.example.laundryku.model.RegisterResponse
import com.example.laundryku.network.RetrofitClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    private lateinit var loginTab: TextView
    private lateinit var registerTab: TextView
    private lateinit var tabSelectionIndicator: View
    private lateinit var loginForm: View
    private lateinit var registerForm: View
    private lateinit var authFormContainer: View
    private lateinit var formScrollView: NestedScrollView
    private lateinit var loginButton: MaterialButton
    private lateinit var registerButton: MaterialButton
    private lateinit var sessionManager: SessionManager

    private var currentMode = AuthMode.LOGIN
    private var activeCall: Call<*>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        if (sessionManager.isLoggedIn()) {
            if (routeToDashboard(sessionManager.getLevel())) return
            sessionManager.clearSession()
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContentView(R.layout.activity_main)

        applySystemBarInsets()
        bindAuthViews()

        val restoredMode = if (savedInstanceState?.getBoolean(KEY_REGISTER_MODE) == true) {
            AuthMode.REGISTER
        } else {
            AuthMode.LOGIN
        }
        showModeImmediately(restoredMode)

        loginTab.setOnClickListener { switchMode(AuthMode.LOGIN) }
        registerTab.setOnClickListener { switchMode(AuthMode.REGISTER) }
        loginButton.setOnClickListener { submitLogin() }
        registerButton.setOnClickListener { submitRegister() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_REGISTER_MODE, currentMode == AuthMode.REGISTER)
        super.onSaveInstanceState(outState)
    }

    private fun bindAuthViews() {
        loginTab = findViewById(R.id.loginTab)
        registerTab = findViewById(R.id.registerTab)
        tabSelectionIndicator = findViewById(R.id.tabSelectionIndicator)
        loginForm = findViewById(R.id.loginForm)
        registerForm = findViewById(R.id.registerForm)
        authFormContainer = findViewById(R.id.authFormContainer)
        formScrollView = findViewById(R.id.formScrollView)
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)
    }

    private fun submitLogin() {
        val usernameLayout = findViewById<TextInputLayout>(R.id.usernameInputLayout)
        val passwordLayout = findViewById<TextInputLayout>(R.id.passwordInputLayout)
        val username = findViewById<TextInputEditText>(R.id.usernameEditText).text?.toString()?.trim().orEmpty()
        val password = findViewById<TextInputEditText>(R.id.passwordEditText).text?.toString().orEmpty()

        usernameLayout.error = null
        passwordLayout.error = null

        var valid = true
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
        if (!valid) return

        setAuthLoading(true)
        activeCall = RetrofitClient.apiService.login(LoginRequest(username, password)).also { call ->
            call.enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    if (isFinishing || isDestroyed) return
                    activeCall = null
                    setAuthLoading(false)
                    val body = response.body()
                    val user = body?.data
                    if (response.isSuccessful && body?.success == true && user != null) {
                        if (!sessionManager.saveLoginSession(user)) {
                            Toast.makeText(
                                this@MainActivity,
                                R.string.session_save_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                            return
                        }
                        Toast.makeText(this@MainActivity, body.message, Toast.LENGTH_SHORT).show()
                        if (!routeToDashboard(user.level)) sessionManager.clearSession()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            serverMessage(response, body?.message),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<LoginResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    activeCall = null
                    setAuthLoading(false)
                    Toast.makeText(this@MainActivity, R.string.auth_network_error, Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun submitRegister() {
        val nameLayout = findViewById<TextInputLayout>(R.id.fullNameInputLayout)
        val phoneLayout = findViewById<TextInputLayout>(R.id.phoneInputLayout)
        val usernameLayout = findViewById<TextInputLayout>(R.id.registerUsernameInputLayout)
        val passwordLayout = findViewById<TextInputLayout>(R.id.registerPasswordInputLayout)
        val confirmationLayout = findViewById<TextInputLayout>(R.id.confirmPasswordInputLayout)
        val nameInput = findViewById<TextInputEditText>(R.id.fullNameEditText)
        val phoneInput = findViewById<TextInputEditText>(R.id.phoneEditText)
        val usernameInput = findViewById<TextInputEditText>(R.id.registerUsernameEditText)
        val passwordInput = findViewById<TextInputEditText>(R.id.registerPasswordEditText)
        val confirmationInput = findViewById<TextInputEditText>(R.id.confirmPasswordEditText)

        val name = nameInput.text?.toString()?.trim().orEmpty()
        val phone = phoneInput.text?.toString()?.trim().orEmpty()
        val username = usernameInput.text?.toString()?.trim().orEmpty()
        val password = passwordInput.text?.toString().orEmpty()
        val confirmation = confirmationInput.text?.toString().orEmpty()

        listOf(nameLayout, phoneLayout, usernameLayout, passwordLayout, confirmationLayout)
            .forEach { it.error = null }

        var valid = true
        if (name.isEmpty()) {
            nameLayout.error = getString(R.string.auth_required)
            valid = false
        } else if (name.length > MAX_NAME_LENGTH) {
            nameLayout.error = getString(R.string.auth_name_too_long)
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
        if (confirmation.isEmpty()) {
            confirmationLayout.error = getString(R.string.auth_required)
            valid = false
        } else if (password != confirmation) {
            confirmationLayout.error = getString(R.string.auth_password_mismatch)
            valid = false
        }
        if (!valid) return

        setAuthLoading(true)
        val request = RegisterRequest(name, phone, username, password, confirmation)
        activeCall = RetrofitClient.apiService.register(request).also { call ->
            call.enqueue(object : Callback<RegisterResponse> {
                override fun onResponse(call: Call<RegisterResponse>, response: Response<RegisterResponse>) {
                    if (isFinishing || isDestroyed) return
                    activeCall = null
                    setAuthLoading(false)
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true) {
                        Toast.makeText(this@MainActivity, body.message, Toast.LENGTH_SHORT).show()
                        findViewById<TextInputEditText>(R.id.usernameEditText).setText(username)
                        passwordInput.text?.clear()
                        confirmationInput.text?.clear()
                        switchMode(AuthMode.LOGIN)
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            serverMessage(response, body?.message),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<RegisterResponse>, throwable: Throwable) {
                    if (call.isCanceled || isFinishing || isDestroyed) return
                    activeCall = null
                    setAuthLoading(false)
                    Toast.makeText(this@MainActivity, R.string.auth_network_error, Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun routeToDashboard(level: Int): Boolean {
        val destination = dashboardActivityForLevel(level) ?: run {
            Toast.makeText(this, R.string.auth_invalid_role, Toast.LENGTH_SHORT).show()
            return false
        }
        startActivity(Intent(this, destination).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        finish()
        return true
    }

    private fun setAuthLoading(loading: Boolean) {
        loginButton.isEnabled = !loading
        registerButton.isEnabled = !loading
        loginButton.setText(if (loading) R.string.auth_processing else R.string.login_button)
        registerButton.setText(if (loading) R.string.auth_processing else R.string.register_button)
        setTabsClickable(!loading)
    }

    private fun serverMessage(response: Response<*>, fallback: String?): String {
        if (!fallback.isNullOrBlank()) return fallback
        return try {
            val json = response.errorBody()?.string().orEmpty()
            JSONObject(json).optString("message").ifBlank { getString(R.string.auth_invalid_response) }
        } catch (_: Exception) {
            getString(R.string.auth_invalid_response)
        }
    }

    override fun onDestroy() {
        activeCall?.cancel()
        activeCall = null
        super.onDestroy()
    }

    private fun showModeImmediately(mode: AuthMode) {
        currentMode = mode
        loginForm.visibility = if (mode == AuthMode.LOGIN) View.VISIBLE else View.GONE
        registerForm.visibility = if (mode == AuthMode.REGISTER) View.VISIBLE else View.GONE
        updateTabAppearance(mode)

        tabSelectionIndicator.doOnLayout { indicator ->
            indicator.translationX = if (mode == AuthMode.REGISTER) {
                indicator.width.toFloat()
            } else {
                0f
            }
        }
    }

    private fun switchMode(targetMode: AuthMode) {
        if (targetMode == currentMode) return

        val outgoingForm = if (currentMode == AuthMode.LOGIN) loginForm else registerForm
        val incomingForm = if (targetMode == AuthMode.LOGIN) loginForm else registerForm
        val direction = if (targetMode == AuthMode.REGISTER) 1f else -1f
        val slideDistance = authFormContainer.width.toFloat()

        currentMode = targetMode
        setTabsClickable(false)
        updateTabAppearance(targetMode)
        formScrollView.smoothScrollTo(0, 0)

        tabSelectionIndicator.animate()
            .translationX(if (targetMode == AuthMode.REGISTER) tabSelectionIndicator.width.toFloat() else 0f)
            .setDuration(ANIMATION_DURATION)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()

        incomingForm.apply {
            visibility = View.VISIBLE
            alpha = MIN_FORM_ALPHA
            translationX = direction * slideDistance
            animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(ANIMATION_DURATION)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        }

        outgoingForm.animate()
            .alpha(MIN_FORM_ALPHA)
            .translationX(-direction * slideDistance)
            .setDuration(ANIMATION_DURATION)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                outgoingForm.visibility = View.GONE
                outgoingForm.alpha = 1f
                outgoingForm.translationX = 0f
                setTabsClickable(true)
            }
            .start()
    }

    private fun updateTabAppearance(mode: AuthMode) {
        val loginSelected = mode == AuthMode.LOGIN
        loginTab.isSelected = loginSelected
        registerTab.isSelected = !loginSelected
        loginTab.setTextColor(getColor(if (loginSelected) R.color.laundry_on_primary else R.color.laundry_primary))
        registerTab.setTextColor(getColor(if (loginSelected) R.color.laundry_primary else R.color.laundry_on_primary))
    }

    private fun setTabsClickable(clickable: Boolean) {
        loginTab.isClickable = clickable
        registerTab.isClickable = clickable
    }

    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                maxOf(systemBars.bottom, ime.bottom)
            )
            insets
        }
    }

    private enum class AuthMode {
        LOGIN,
        REGISTER
    }

    private companion object {
        const val ANIMATION_DURATION = 250L
        const val MIN_FORM_ALPHA = 0.2f
        const val KEY_REGISTER_MODE = "register_mode"
        const val MAX_NAME_LENGTH = 8
        const val MAX_USERNAME_LENGTH = 12
        const val MAX_PASSWORD_LENGTH = 16
    }
}
