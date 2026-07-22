package com.sayanthrock.githubrock.backend.service

import com.sayanthrock.githubrock.backend.config.AppConfig
import com.sayanthrock.githubrock.backend.model.DevicePollResponse
import com.sayanthrock.githubrock.backend.model.DeviceStartResponse
import com.sayanthrock.githubrock.backend.model.GitHubTokenResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters

class GitHubDeviceFlowService(
    private val config: AppConfig,
    private val client: HttpClient,
) {
    suspend fun start(): DeviceStartResponse {
        require(config.githubOauthClientId.isNotBlank()) { "GitHub OAuth is not configured" }
        return client.submitForm(
            url = "https://github.com/login/device/code",
            formParameters = Parameters.build {
                append("client_id", config.githubOauthClientId)
                append("scope", "repo workflow read:user user:email read:org notifications")
            },
        ) {
            headers { append(HttpHeaders.Accept, "application/json") }
        }.body()
    }

    suspend fun poll(deviceCode: String): DevicePollResponse {
        require(deviceCode.isNotBlank()) { "device_code is required" }
        val response: GitHubTokenResponse = client.submitForm(
            url = "https://github.com/login/oauth/access_token",
            formParameters = Parameters.build {
                append("client_id", config.githubOauthClientId)
                append("device_code", deviceCode)
                append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            },
        ) {
            headers { append(HttpHeaders.Accept, "application/json") }
        }.body()

        if (!response.accessToken.isNullOrBlank()) {
            return DevicePollResponse(
                state = "authorized",
                accessToken = response.accessToken,
                tokenType = response.tokenType,
                scope = response.scope,
            )
        }

        val state = when (response.error) {
            "authorization_pending" -> "pending"
            "slow_down" -> "slow_down"
            "expired_token" -> "expired"
            "access_denied" -> "denied"
            else -> "error"
        }
        return DevicePollResponse(
            state = state,
            message = response.errorDescription,
            interval = response.interval,
        )
    }
}
