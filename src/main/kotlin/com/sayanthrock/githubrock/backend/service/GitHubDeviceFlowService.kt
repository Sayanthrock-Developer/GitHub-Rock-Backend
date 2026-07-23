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

internal const val GITHUB_ROCK_OAUTH_SCOPES =
    "repo workflow read:user user:email read:org notifications user:follow"

class GitHubDeviceFlowService(
    private val config: AppConfig,
    private val client: HttpClient,
) {
    val isConfigured: Boolean get() = config.githubOauthClientId.isNotBlank()
    val isRefreshConfigured: Boolean get() = isConfigured && config.githubOauthClientSecret.isNotBlank()

    suspend fun start(): DeviceStartResponse {
        require(isConfigured) { "GitHub OAuth is not configured" }
        return client.submitForm(
            url = "https://github.com/login/device/code",
            formParameters = Parameters.build {
                append("client_id", config.githubOauthClientId)
                append("scope", GITHUB_ROCK_OAUTH_SCOPES)
            },
        ) {
            headers { append(HttpHeaders.Accept, "application/json") }
        }.body()
    }

    suspend fun poll(deviceCode: String): DevicePollResponse {
        require(isConfigured) { "GitHub OAuth is not configured" }
        require(deviceCode.isNotBlank()) { "device_code is required" }
        return requestToken(
            Parameters.build {
                append("client_id", config.githubOauthClientId)
                append("device_code", deviceCode)
                append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            }
        ).toDevicePollResponse()
    }

    suspend fun refresh(refreshToken: String): DevicePollResponse {
        require(isRefreshConfigured) { "GitHub OAuth token refresh is not configured" }
        require(refreshToken.isNotBlank()) { "refresh_token is required" }
        return requestToken(
            Parameters.build {
                append("client_id", config.githubOauthClientId)
                append("client_secret", config.githubOauthClientSecret)
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
            }
        ).toDevicePollResponse()
    }

    private suspend fun requestToken(parameters: Parameters): GitHubTokenResponse =
        client.submitForm(
            url = "https://github.com/login/oauth/access_token",
            formParameters = parameters,
        ) {
            headers { append(HttpHeaders.Accept, "application/json") }
        }.body()
}

internal fun GitHubTokenResponse.toDevicePollResponse(): DevicePollResponse {
    if (!accessToken.isNullOrBlank()) {
        return DevicePollResponse(
            state = "authorized",
            accessToken = accessToken,
            tokenType = tokenType,
            scope = scope,
            expiresIn = expiresIn,
            refreshToken = refreshToken,
            refreshTokenExpiresIn = refreshTokenExpiresIn,
        )
    }

    val state = when (error) {
        "authorization_pending" -> "pending"
        "slow_down" -> "slow_down"
        "expired_token" -> "expired"
        "access_denied" -> "denied"
        else -> "error"
    }
    return DevicePollResponse(
        state = state,
        message = errorDescription,
        interval = interval,
    )
}
