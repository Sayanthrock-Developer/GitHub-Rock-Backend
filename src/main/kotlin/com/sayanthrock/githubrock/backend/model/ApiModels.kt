package com.sayanthrock.githubrock.backend.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val code: String, val message: String, val requestId: String? = null)

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val postgres: String,
    val redis: String,
    val meilisearch: String,
    val timestamp: String,
)

@Serializable
data class PublicConfigResponse(
    val apiVersion: String = "v1",
    val minSupportedAppVersion: String,
    val latestAppVersion: String,
    val maintenanceMode: Boolean,
    val features: Map<String, Boolean>,
)

@Serializable
data class DeviceStartResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int = 5,
)

@Serializable
data class DevicePollRequest(@SerialName("device_code") val deviceCode: String)

@Serializable
data class GitHubTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    val interval: Int? = null,
)

@Serializable
data class DevicePollResponse(
    val state: String,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    val message: String? = null,
    val interval: Int? = null,
)

@Serializable
data class WebhookAcceptedResponse(
    val accepted: Boolean,
    val duplicate: Boolean,
    val deliveryId: String,
    val event: String,
)
