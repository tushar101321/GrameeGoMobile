package com.example.grameego

import retrofit2.http.*

interface ApiService {

    // -------- AUTH --------
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/auth/signup")
    suspend fun signup(@Body body: SignupRequest): AuthResponse

    @GET("api/auth/me")
    suspend fun me(): MeResponse

    // -------- SHOPS --------
    @GET("api/shops")
    suspend fun listShops(): List<ShopSummary>

    @GET("api/shops/{id}")
    suspend fun getShop(@Path("id") id: String): ShopDetail

    // -------- DELIVERIES --------
    @POST("api/deliveries")
    suspend fun createDelivery(@Body body: CreateDeliveryRequest): DeliveryDto

    @GET("api/deliveries/mine")
    suspend fun myDeliveries(): List<DeliveryDto>

    @GET("api/deliveries/available")
    suspend fun availableDeliveries(): List<DeliveryDto>

    @POST("api/deliveries/{id}/accept")
    suspend fun acceptDelivery(@Path("id") id: String): DeliveryDto

    @GET("api/deliveries/assigned-to-me")
    suspend fun assignedToMe(): List<DeliveryDto>

    // ✅ FIXED: StatusUpdateRequest (not UpdateStatusRequest)
    @PATCH("api/deliveries/{id}/status")
    suspend fun updateStatus(
        @Path("id") id: String,
        @Body body: StatusUpdateRequest
    ): GenericMessageResponse

    @POST("api/deliveries/{id}/unassign")
    suspend fun unassign(@Path("id") id: String): GenericMessageResponse

    @DELETE("api/deliveries/{id}")
    suspend fun cancelDelivery(@Path("id") id: String): GenericMessageResponse

    // -------- SHOP PANEL --------
    @GET("api/shops/my/orders")
    suspend fun shopMyOrders(): List<DeliveryDto>

    @PATCH("api/shops/my/orders/{id}/confirm")
    suspend fun shopConfirm(
        @Path("id") id: String,
        @Body body: ConfirmOrderRequest
    ): ConfirmOrderResponse
}
