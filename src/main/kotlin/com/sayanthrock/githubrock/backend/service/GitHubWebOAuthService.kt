package com.sayanthrock.githubrock.backend.service

import com.sayanthrock.githubrock.backend.config.AppConfig
import com.sayanthrock.githubrock.backend.model.GitHubTokenResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder

internal const val GITHUB_ROCK_WEB_OAUTH_SCOPES =
    "repo workflow read:user user:email read:org notifications user:follow"

class GitHubWebOAuthService(
    private val config: AppConfig,
    private val client: HttpClient,
) {
    val isConfigured: Boolean
        get() = config.githubOauthClientId.isNotBlank() && config.githubOauthClientSecret.isNotBlank()

    val callbackUrl: String
        get() = "${config.publicBaseUrl.trimEnd('/')}/v1/auth/github/callback"

    fun authorizationUrl(state: String): String {
        require(isConfigured) { "GitHub OAuth web flow is not configured" }
        require(state.length in 32..256) { "Invalid OAuth state" }
        return URLBuilder("https://github.com/login/oauth/authorize").apply {
            parameters.append("client_id", config.githubOauthClientId)
            parameters.append("redirect_uri", callbackUrl)
            parameters.append("scope", GITHUB_ROCK_WEB_OAUTH_SCOPES)
            parameters.append("state", state)
        }.buildString()
    }

    suspend fun exchange(code: String): GitHubTokenResponse {
        require(isConfigured) { "GitHub OAuth web flow is not configured" }
        require(code.length in 10..4096) { "Invalid authorization code" }
        return client.submitForm(
            url = "https://github.com/login/oauth/access_token",
            formParameters = Parameters.build {
                append("client_id", config.githubOauthClientId)
                append("client_secret", config.githubOauthClientSecret)
                append("code", code)
                append("redirect_uri", callbackUrl)
            },
        ) {
            headers { append(HttpHeaders.Accept, "application/json") }
        }.body()
    }
}
