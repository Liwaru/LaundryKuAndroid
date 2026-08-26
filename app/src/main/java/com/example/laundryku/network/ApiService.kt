package com.example.laundryku.network

import com.example.laundryku.model.LoginRequest
import com.example.laundryku.model.LoginResponse
import com.example.laundryku.model.CreateOrderRequest
import com.example.laundryku.model.CreateOrderResponse
import com.example.laundryku.model.RegisterRequest
import com.example.laundryku.model.RegisterResponse
import com.example.laundryku.model.ServicesResponse
import com.example.laundryku.model.CustomerOrdersResponse
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
import com.example.laundryku.model.UpdateLaundryStatusRequest
import com.example.laundryku.model.UpdateLaundryStatusResponse
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

    @GET("api/layanan.php")
    fun getServices(): Call<ServicesResponse>

    @POST("api/create_order.php")
    fun createOrder(@Body request: CreateOrderRequest): Call<CreateOrderResponse>

    @GET("api/customer_orders.php")
    fun getCustomerOrders(@Query("id_user") userId: Int): Call<CustomerOrdersResponse>

    @GET("api/customer_history.php")
    fun getCustomerHistory(@Query("id_user") userId: Int): Call<CustomerHistoryResponse>

    @GET("api/customer_order_detail.php")
    fun getCustomerOrderDetail(
        @Query("id_user") userId: Int,
        @Query("id_transaksi") transactionId: Int
    ): Call<CustomerOrderDetailResponse>

    @POST("api/select_cash_payment.php")
    fun selectCashPayment(@Body request: SelectCashPaymentRequest): Call<CashPaymentResponse>

    @GET("api/cashier_transactions.php")
    fun getCashierTransactions(@Query("id_user") userId: Int): Call<CashierTransactionsResponse>

    @POST("api/confirm_cash_payment.php")
    fun confirmCashPayment(
        @Body request: ConfirmCashPaymentRequest
    ): Call<ConfirmCashPaymentResponse>

    @POST("api/complete_transaction.php")
    fun completeTransaction(
        @Body request: CompleteTransactionRequest
    ): Call<CompleteTransactionResponse>

    @GET("api/cashier_dashboard.php")
    fun getCashierDashboard(@Query("id_user") userId: Int): Call<CashierDashboardResponse>

    @GET("api/cashier_customers.php")
    fun getCashierCustomers(@Query("id_user") userId: Int): Call<CashierCustomersResponse>

    @GET("api/staff_jobs.php")
    fun getStaffJobs(@Query("id_user") userId: Int): Call<StaffJobsResponse>

    @POST("api/update_laundry_status.php")
    fun updateLaundryStatus(
        @Body request: UpdateLaundryStatusRequest
    ): Call<UpdateLaundryStatusResponse>

    @GET("api/staff_history.php")
    fun getStaffHistory(@Query("id_user") userId: Int): Call<StaffHistoryResponse>

    @GET("api/staff_dashboard.php")
    fun getStaffDashboard(@Query("id_user") userId: Int): Call<StaffDashboardResponse>
}
