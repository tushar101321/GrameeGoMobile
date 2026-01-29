package com.example.grameego

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

// ---------- AUTH ----------
data class LoginRequest(
    val mobile: String,
    val password: String
)

data class SignupRequest(
    val name: String,
    val mobile: String,
    val password: String,
    val role: String,
    val village: String? = null,      // customer
    val vehicleType: String? = null,  // driver
    val shopId: String? = null        // shop
)

data class UserDto(
    val id: String,
    val name: String,
    val mobile: String,
    val role: String,
    val village: String? = null,
    val vehicleType: String? = null,
    val shopId: String? = null
)

data class AuthResponse(
    val token: String,
    val user: UserDto
)

data class MeResponse(
    val user: UserDto
)

// ---------- SHOPS ----------
data class ShopSummary(
    val id: String,
    val name: String,
    val address: String,
    val productsCount: Int
)

data class ProductDto(
    val id: String,
    val name: String,
    val price: Double
)

data class ShopDetail(
    val id: String,
    val name: String,
    val address: String,
    val products: List<ProductDto>
)

// ---------- DELIVERY ----------
data class ItemSnapshot(
    val id: String,
    val name: String,
    val qty: Int,
    val price: Double
)

data class CreateDeliveryRequest(
    val itemDescription: String,
    val contactNumber: String,
    val village: String,
    val shopName: String,
    val shopAddress: String,
    val estimatedDistanceKm: Double? = null,
    val needByAt: String? = null,

    val items: List<ItemSnapshot> = emptyList(),
    val productTotal: Double? = null,
    val deliveryFee: Double? = null,
    val grandTotal: Double? = null,

    val shopId: String? = null
)


data class DeliveryDto(
    @SerializedName("_id") val id: String,
    val createdBy: String? = null,

    val itemDescription: String? = null,
    val contactNumber: String? = null,
    val village: String? = null,

    val shopName: String? = null,
    val shopAddress: String? = null,
    val shopId: String? = null,

    val estimatedDistanceKm: Double? = null,
    val price: Double? = null,
    val needByAt: String? = null,

    val assignedDriver: JsonElement? = null,
    val deliveryStatus: String? = null,

    val shopConfirmationStatus: String? = null,
    val shopConfirmationAt: String? = null,
    val shopNote: String? = null,

    val items: List<ItemSnapshot>? = null,
    val productTotal: Double? = null,
    val deliveryFee: Double? = null,
    val grandTotal: Double? = null,

    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class ConfirmOrderRequest(
    val action: String, // accept | reject
    val note: String? = null
)

data class ConfirmOrderResponse(
    val message: String,
    val delivery: ConfirmedDelivery
)

data class ConfirmedDelivery(
    val id: String,
    val shopConfirmationStatus: String,
    val shopConfirmationAt: String?,
    val shopNote: String?
)

data class StatusUpdateRequest(
    val newStatus: String // Picked | Delivered
)

data class GenericMessageResponse(
    val message: String
)
