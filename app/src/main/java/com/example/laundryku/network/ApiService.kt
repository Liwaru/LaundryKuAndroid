package com.example.laundryku.network

import com.example.laundryku.model.LoginRequest
import com.example.laundryku.model.LoginResponse
import com.example.laundryku.model.CreateOrderRequest
import com.example.laundryku.model.CreateOrderResponse
import com.example.laundryku.model.RegisterRequest
import com.example.laundryku.model.RegisterResponse
import com.example.laundryku.model.ServicesResponse
import com.example.laundryku.model.CustomerOrdersResponse
import com.example.laundryku.model.CustomerDashboardResponse
import com.example.laundryku.model.CustomerHistoryResponse
import com.example.laundryku.model.CustomerOrderDetailResponse
import com.example.laundryku.model.CashPaymentResponse
import com.example.laundryku.model.SelectCashPaymentRequest
import com.example.laundryku.model.CashierTransactionsResponse
import com.example.laundryku.model.ConfirmCashPaymentRequest
import com.example.laundryku.model.ConfirmCashPaymentResponse
import com.example.laundryku.model.CompleteTransactionRequest
import com.example.laundryku.model.CompleteTransactionResponse
import com.example.laundryku.model.CashierDashboardResponse
import com.example.laundryku.model.CashierCustomersResponse
import com.example.laundryku.model.StaffDashboardResponse
import com.example.laundryku.model.StaffHistoryResponse
import com.example.laundryku.model.StaffJobsResponse
import com.example.laundryku.model.OwnerDashboardResponse
import com.example.laundryku.model.OwnerCreateStaffRequest
import com.example.laundryku.model.OwnerCreateStaffResponse
import com.example.laundryku.model.OwnerStaffResponse
import com.example.laundryku.model.OwnerReportsResponse
import com.example.laundryku.model.LogoutResponse
import com.example.laundryku.model.UpdateLaundryStatusRequest
import com.example.laundryku.model.UpdateLaundryStatusResponse
import com.example.laundryku.model.CreateQrisPaymentRequest
import com.example.laundryku.model.PaymentStatusResponse
import com.example.laundryku.model.QrisPaymentResponse
import com.example.laundryku.model.SimulateEWalletPaymentRequest
import com.example.laundryku.model.SimulateEWalletPaymentResponse
import com.example.laundryku.model.UpdateProfileRequest
import com.example.laundryku.model.UpdateProfileResponse
import com.example.laundryku.model.ChangePasswordRequest
import com.example.laundryku.model.ChangePasswordResponse
import com.example.laundryku.model.CashierCustomerDetailResponse
import com.example.laundryku.model.OwnerStaffDetailResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.POST

interface ApiService {
    @POST("api/login.php")
    fun login(@Body request: LoginRequest): Call<LoginResponse>

    @POST("api/register.php")
    fun register(@Body request: RegisterRequest): Call<RegisterResponse>

    @POST("api/logout.php")
    fun logout(): Call<LogoutResponse>

    @POST("api/update_profile.php")
    fun updateProfile(@Body request: UpdateProfileRequest): Call<UpdateProfileResponse>

    @POST("api/change_password.php")
    fun changePassword(@Body request: ChangePasswordRequest): Call<ChangePasswordResponse>

    @GET("api/layanan.php")
    fun getServices(): Call<ServicesResponse>

    @POST("api/create_order.php")
    fun createOrder(@Body request: CreateOrderRequest): Call<CreateOrderResponse>

    @GET("api/customer_orders.php")
    fun getCustomerOrders(): Call<CustomerOrdersResponse>

    @GET("api/customer_dashboard.php")
    fun getCustomerDashboard(): Call<CustomerDashboardResponse>

    @GET("api/customer_history.php")
    fun getCustomerHistory(): Call<CustomerHistoryResponse>

    @GET("api/customer_order_detail.php")
    fun getCustomerOrderDetail(
        @Query("id_transaksi") transactionId: Int
    ): Call<CustomerOrderDetailResponse>

    @POST("api/select_cash_payment.php")
    fun selectCashPayment(@Body request: SelectCashPaymentRequest): Call<CashPaymentResponse>

    @POST("api/create_qris_payment.php")
    fun createQrisPayment(@Body request: CreateQrisPaymentRequest): Call<QrisPaymentResponse>

    @POST("api/simulate_ewallet_payment.php")
    fun simulateEWalletPayment(
        @Body request: SimulateEWalletPaymentRequest
    ): Call<SimulateEWalletPaymentResponse>

    @GET("api/payment_status.php")
    fun getPaymentStatus(
        @Query("id_transaksi") transactionId: Int
    ): Call<PaymentStatusResponse>

    @GET("api/cashier_transactions.php")
    fun getCashierTransactions(): Call<CashierTransactionsResponse>

    @POST("api/confirm_cash_payment.php")
    fun confirmCashPayment(
        @Body request: ConfirmCashPaymentRequest
    ): Call<ConfirmCashPaymentResponse>

    @POST("api/complete_transaction.php")
    fun completeTransaction(
        @Body request: CompleteTransactionRequest
    ): Call<CompleteTransactionResponse>

    @GET("api/cashier_dashboard.php")
    fun getCashierDashboard(): Call<CashierDashboardResponse>

    @GET("api/owner_dashboard.php")
    fun getOwnerDashboard(): Call<OwnerDashboardResponse>

    @GET("api/owner_staff.php")
    fun getOwnerStaff(): Call<OwnerStaffResponse>

    @GET("api/owner_staff_detail.php")
    fun getOwnerStaffDetail(@Query("id_staff") staffId: Int): Call<OwnerStaffDetailResponse>

    @POST("api/owner_create_staff.php")
    fun createOwnerStaff(@Body request: OwnerCreateStaffRequest): Call<OwnerCreateStaffResponse>

    @GET("api/owner_reports.php")
    fun getOwnerReports(
        @Query("period") period: String
    ): Call<OwnerReportsResponse>

    @GET("api/cashier_customers.php")
    fun getCashierCustomers(): Call<CashierCustomersResponse>

    @GET("api/cashier_customer_detail.php")
    fun getCashierCustomerDetail(
        @Query("id_pelanggan") customerId: Int
    ): Call<CashierCustomerDetailResponse>

    @GET("api/staff_jobs.php")
    fun getStaffJobs(): Call<StaffJobsResponse>

    @POST("api/update_laundry_status.php")
    fun updateLaundryStatus(
        @Body request: UpdateLaundryStatusRequest
    ): Call<UpdateLaundryStatusResponse>

    @GET("api/staff_history.php")
    fun getStaffHistory(): Call<StaffHistoryResponse>

    @GET("api/staff_dashboard.php")
    fun getStaffDashboard(): Call<StaffDashboardResponse>
}
