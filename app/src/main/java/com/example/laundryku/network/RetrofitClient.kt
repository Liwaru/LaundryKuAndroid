package com.example.laundryku.network

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.laundryku.MainActivity
import com.example.laundryku.R
import com.example.laundryku.SessionManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.atomic.AtomicBoolean

object RetrofitClient {
    // Production API must use HTTPS. Cleartext HTTP is enabled only by the debug manifest.
    private const val BASE_URL = "http://10.0.2.2/laundryku_api/"
    private val sessionRedirectInProgress = AtomicBoolean(false)
    private lateinit var applicationContext: Context

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun onLoginSucceeded() {
        sessionRedirectInProgress.set(false)
    }

    private val httpClient: OkHttpClient by lazy {
        check(::applicationContext.isInitialized) { "RetrofitClient must be initialized by Application" }
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val path = original.url().encodedPath()
                val publicEndpoint = path.endsWith("/login.php") ||
                    path.endsWith("/register.php") ||
                    path.endsWith("/layanan.php")
                val handlesOwnUnauthorized = path.endsWith("/logout.php")
                val token = if (publicEndpoint) "" else SessionManager(applicationContext).getAuthToken()
                val request = if (token.isBlank()) {
                    original
                } else {
                    original.newBuilder().header("Authorization", "Bearer $token").build()
                }
                val response = chain.proceed(request)
                if (response.code() == 401 && token.isNotBlank() && !handlesOwnUnauthorized) {
                    redirectExpiredSession()
                }
                response
            }
            .build()
    }

    private fun redirectExpiredSession() {
        SessionManager(applicationContext).clearSession()
        if (!sessionRedirectInProgress.compareAndSet(false, true)) return
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, R.string.session_expired, Toast.LENGTH_LONG).show()
            applicationContext.startActivity(
                Intent(applicationContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            )
        }
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
