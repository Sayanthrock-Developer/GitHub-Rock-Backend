package com.sayanthrock.githubrock.backend.routes

import com.sayanthrock.githubrock.backend.config.AppConfig
import com.sayanthrock.githubrock.backend.model.*
import com.sayanthrock.githubrock.backend.security.AuthRateLimiter
import com.sayanthrock.githubrock.backend.security.RefreshTokenReplayGuard
import com.sayanthrock.githubrock.backend.security.WebhookVerifier
import com.sayanthrock.githubrock.backend.service.GitHubDeviceFlowService
import com.sayanthrock.githubrock.backend.service.GitHubWebOAuthService
import com.sayanthrock.githubrock.backend.service.HealthService
import com.sayanthrock.githubrock.backend.service.GitHubDataException
import com.sayanthrock.githubrock.backend.service.GitHubDataService
import com.sayanthrock.githubrock.backend.storage.WebhookDeliveryRepository
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.request.header
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import org.koin.ktor.ext.inject

private const val MAX_WEBHOOK_DELIVERY_ID_LENGTH = 256
private const val MAX_WEBHOOK_EVENT_LENGTH = 100
private const val MAX_OAUTH_ERROR_LENGTH = 512

fun Application.configureRoutes() {
    val config by inject<AppConfig>()
    val healthService by inject<HealthService>()
    val githubDataService by inject<GitHubDataService>()
    val deviceFlowService by inject<GitHubDeviceFlowService>()
    val webOAuthService by inject<GitHubWebOAuthService>()
    val refreshTokenReplayGuard by inject<RefreshTokenReplayGuard>()
    val authRateLimiter by inject<AuthRateLimiter>()
    val webhookVerifier by inject<WebhookVerifier>()
    val webhookDeliveries by inject<WebhookDeliveryRepository>()

    routing {
        get("/") { call.respond(mapOf("name" to "GitHub Rock Backend", "api" to "/v1", "status" to "running")) }
        route("/v1") {
            get("/health") {
                val health = healthService.check()
                call.respond(if (health.status == "healthy") HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable, health)
            }
            get("/config") {
                call.respond(
                    PublicConfigResponse(
                        minSupportedAppVersion = config.minSupportedAppVersion,
                        latestAppVersion = config.latestAppVersion,
                        maintenanceMode = config.maintenanceMode,
                        features = mapOf(
                            "oauthDeviceProxy" to deviceFlowService.isConfigured,
                            "oauthRefreshProxy" to deviceFlowService.isRefreshConfigured,
                            "oauthWeb" to webOAuthService.isConfigured,
                            "webhooks" to config.githubWebhookSecret.isNotBlank(),
                            "repositoryCache" to true,
                            "githubSearch" to true,
                            "repositoryData" to true,
                            "readmeProxy" to true,
                            "userData" to true,
                            "buildMonitoring" to false,
                            "settingsSync" to false,
                        ),
                    ),
                )
            }
            get("/search") {
                val query = call.request.queryParameters["q"]?.trim()
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val perPage = call.request.queryParameters["per_page"]?.toIntOrNull() ?: 30
                if (query.isNullOrBlank() || query.length > 256 || page !in 1..100 || perPage !in 1..100) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_search", "q is required; page must be 1..100 and per_page must be 1..100"))
                    return@get
                }
                runCatching {
                    githubDataService.search(query, page, perPage, githubBearerToken(call.request.header(HttpHeaders.Authorization)))
                }.onSuccess {
                    call.respond(GitHubDataEnvelope(it.data, it.cached))
                }.onFailure {
                    val error = it as? GitHubDataException
                    call.respond(
                        HttpStatusCode.fromValue(error?.status ?: 502),
                        ErrorResponse("github_request_failed", error?.message ?: "GitHub request failed"),
                    )
                }
            }
            get("/search/explore") {
                val query = call.request.queryParameters["q"]?.trim()
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val perPage = call.request.queryParameters["per_page"]?.toIntOrNull() ?: 30
                if (query.isNullOrBlank() || query.length > 256 || page !in 1..100 || perPage !in 1..100) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_search", "q is required; page must be 1..100 and per_page must be 1..100"))
                    return@get
                }
                runCatching {
                    githubDataService.search(query, page, perPage, call.request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim())
                }.onSuccess {
                    call.respond(GitHubDataEnvelope(it.data, it.cached))
                }.onFailure {
                    val error = it as? GitHubDataException
                    call.respond(HttpStatusCode.fromValue(error?.status ?: 502), ErrorResponse("github_request_failed", error?.message ?: "GitHub request failed"))
                }
            }
            get("/repo/{owner}/{name}") {
                val owner = call.parameters["owner"]?.trim()
                val name = call.parameters["name"]?.trim()
                if (!isGitHubName(owner) || !isGitHubName(name)) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_repository", "Invalid GitHub repository owner or name"))
                    return@get
                }
                runCatching {
                    githubDataService.repo(owner!!, name!!, call.request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim())
                }.onSuccess {
                    call.respond(GitHubDataEnvelope(it.data, it.cached))
                }.onFailure {
                    val error = it as? GitHubDataException
                    call.respond(HttpStatusCode.fromValue(error?.status ?: 502), ErrorResponse("github_request_failed", error?.message ?: "GitHub request failed"))
                }
            }
            get("/readme/{owner}/{name}") {
                val owner = call.parameters["owner"]?.trim()
                val name = call.parameters["name"]?.trim()
                if (!isGitHubName(owner) || !isGitHubName(name)) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_repository", "Invalid GitHub repository owner or name"))
                    return@get
                }
                runCatching {
                    githubDataService.readme(owner!!, name!!, call.request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim())
                }.onSuccess {
                    call.respond(GitHubReadmeResponse(it.content, it.cached))
                }.onFailure {
                    val error = it as? GitHubDataException
                    call.respond(HttpStatusCode.fromValue(error?.status ?: 502), ErrorResponse("github_request_failed", error?.message ?: "GitHub request failed"))
                }
            }
            get("/user/{username}") {
                val username = call.parameters["username"]?.trim()
                if (!isGitHubName(username)) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_user", "Invalid GitHub username"))
                    return@get
                }
                runCatching {
                    githubDataService.user(username!!, call.request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim())
                }.onSuccess {
                    call.respond(GitHubDataEnvelope(it.data, it.cached))
                }.onFailure {
                    val error = it as? GitHubDataException
                    call.respond(HttpStatusCode.fromValue(error?.status ?: 502), ErrorResponse("github_request_failed", error?.message ?: "GitHub request failed"))
                }
            }
            get("/topics/{bucket}/{platform}") {
                val bucket = call.parameters["bucket"]?.trim()
                val platform = call.parameters["platform"]?.trim()
                if (!isGitHubName(bucket) || !isGitHubName(platform)) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_topic", "Invalid topic or platform"))
                    return@get
                }
                val query = "topic:" + bucket + if (platform != "all") " topic:" + platform else ""
                runCatching {
                    githubDataService.search(query, 1, 30, call.request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim())
                }.onSuccess {
                    call.respond(GitHubDataEnvelope(it.data, it.cached))
                }.onFailure {
                    val error = it as? GitHubDataException
                    call.respond(HttpStatusCode.fromValue(error?.status ?: 502), ErrorResponse("github_request_failed", error?.message ?: "GitHub request failed"))
                }
            }

            route("/auth/device") {
                post("/start") {
                    if (!authRateLimiter.allow("start", call.request.local.remoteHost)) {
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many authentication requests")); return@post
                    }
                    if (!deviceFlowService.isConfigured) {
                        call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("oauth_unavailable", "GitHub OAuth Device Flow is not configured")); return@post
                    }
                    call.response.header("Cache-Control", "no-store")
                    call.respond(deviceFlowService.start())
                }
                post("/poll") {
                    if (!authRateLimiter.allow("poll", call.request.local.remoteHost)) {
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many authentication requests")); return@post
                    }
                    if (!deviceFlowService.isConfigured) {
                        call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("oauth_unavailable", "GitHub OAuth Device Flow is not configured")); return@post
                    }
                    val request = call.receive<DevicePollRequest>()
                    if (request.deviceCode.length !in 10..512) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_device_code", "Invalid device code")); return@post
                    }
                    call.response.header("Cache-Control", "no-store")
                    call.respond(deviceFlowService.poll(request.deviceCode))
                }
                post("/refresh") {
                    if (!authRateLimiter.allow("refresh", call.request.local.remoteHost)) {
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many authentication requests")); return@post
                    }
                    if (!deviceFlowService.isRefreshConfigured) {
                        call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("oauth_refresh_unavailable", "GitHub OAuth token refresh is not configured")); return@post
                    }
                    val request = call.receive<TokenRefreshRequest>()
                    if (request.refreshToken.length !in 20..4096) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_refresh_token", "Invalid refresh token")); return@post
                    }
                    if (!refreshTokenReplayGuard.claim(request.refreshToken)) {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("refresh_replayed", "Authentication session is invalid or expired")); return@post
                    }
                    call.response.header("Cache-Control", "no-store")
                    call.respond(deviceFlowService.refresh(request.refreshToken))
                }
            }
            route("/auth/github") {
                get("/start") {
                    if (!authRateLimiter.allow("web-start", call.request.local.remoteHost)) {
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many authentication requests")); return@get
                    }
                    if (!webOAuthService.isConfigured) {
                        call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("oauth_web_unavailable", "GitHub web OAuth is not configured")); return@get
                    }
                    val state = call.request.queryParameters["state"]
                    val codeChallenge = call.request.queryParameters["code_challenge"]
                    val method = call.request.queryParameters["code_challenge_method"]
                    if (state.isNullOrBlank() || state.length !in 32..256 || codeChallenge.isNullOrBlank() || codeChallenge.length !in 43..128 || method != "S256") {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_oauth_request", "Valid OAuth state and S256 code challenge are required")); return@get
                    }
                    call.response.header("Cache-Control", "no-store")
                    call.respondRedirect(webOAuthService.authorizationUrl(state, codeChallenge), permanent = false)
                }
                get("/callback") {
                    val code = call.request.queryParameters["code"]
                    val state = call.request.queryParameters["state"]
                    val error = call.request.queryParameters["error"]
                    if (error != null) {
                        val safeError = error.take(MAX_OAUTH_ERROR_LENGTH)
                        val description = (call.request.queryParameters["error_description"] ?: "GitHub authorization was not completed.")
                            .take(MAX_OAUTH_ERROR_LENGTH)
                        // Preserve the OAuth state on error/cancellation so the Android client
                        // can validate the callback before displaying the user's cancellation.
                        val safeState = state?.takeIf { it.length in 32..256 }
                        val stateParameter = safeState?.let { "&state=${it.encodeURLParameter()}" }.orEmpty()
                        call.response.header("Cache-Control", "no-store")
                        call.respondRedirect(
                            "githubrock://oauth/callback?error=${safeError.encodeURLParameter()}&error_description=${description.encodeURLParameter()}$stateParameter",
                            permanent = false,
                        )
                        return@get
                    }
                    if (code.isNullOrBlank() || state.isNullOrBlank() || state.length !in 32..256 || code.length !in 10..4096) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_callback", "GitHub OAuth callback is missing required parameters")); return@get
                    }
                    call.response.header("Cache-Control", "no-store")
                    call.respondRedirect("githubrock://oauth/callback?code=${code.encodeURLParameter()}&state=${state.encodeURLParameter()}", permanent = false)
                }
                post("/exchange") {
                    if (!authRateLimiter.allow("web-exchange", call.request.local.remoteHost)) {
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many authentication requests")); return@post
                    }
                    if (!webOAuthService.isConfigured) {
                        call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("oauth_web_unavailable", "GitHub web OAuth is not configured")); return@post
                    }
                    val request = call.receive<WebOAuthExchangeRequest>()
                    if (request.code.length !in 10..4096 || request.codeVerifier.length !in 43..128) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_oauth_exchange", "Invalid authorization code or PKCE verifier")); return@post
                    }
                    val response = webOAuthService.exchange(request.code, request.codeVerifier)
                    if (response.accessToken.isNullOrBlank()) {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("oauth_exchange_failed", response.errorDescription ?: "GitHub authorization code could not be exchanged")); return@post
                    }
                    call.response.header("Cache-Control", "no-store")
                    call.respond(
                        mapOf(
                            "state" to "authorized",
                            "access_token" to response.accessToken,
                            "token_type" to response.tokenType,
                            "scope" to response.scope,
                            "expires_in" to response.expiresIn,
                            "refresh_token" to response.refreshToken,
                            "refresh_token_expires_in" to response.refreshTokenExpiresIn,
                        ),
                    )
                }
            }
            post("/github/webhooks") {
                val signature = call.request.headers["X-Hub-Signature-256"]
                val deliveryId = call.request.headers["X-GitHub-Delivery"]
                val event = call.request.headers["X-GitHub-Event"]
                if (deliveryId.isNullOrBlank() || event.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("missing_headers", "GitHub delivery and event headers are required")); return@post
                }
                if (deliveryId.length > MAX_WEBHOOK_DELIVERY_ID_LENGTH || event.length > MAX_WEBHOOK_EVENT_LENGTH) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_headers", "GitHub webhook headers exceed allowed length")); return@post
                }
                val payload = call.request.receiveChannel().readRemaining(max = 1_048_577L).readByteArray()
                if (payload.size > 1_048_576) {
                    call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("payload_too_large", "Webhook payload exceeds 1 MiB")); return@post
                }
                if (!webhookVerifier.verify(payload, signature)) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("invalid_signature", "Webhook signature is invalid")); return@post
                }
                val inserted = webhookDeliveries.register(deliveryId, event)
                call.respond(if (inserted) HttpStatusCode.Accepted else HttpStatusCode.OK, WebhookAcceptedResponse(true, !inserted, deliveryId, event))
            }
        }
    }
}

private fun isGitHubName(value: String?): Boolean = value != null && value.length in 1..100 && value.all { it.isLetterOrDigit() || it == '-' || it == '.' || it == '_' }

private fun githubBearerToken(header: String?): String? = header?.trim()?.takeIf { it.regionMatches(0, "Bearer", 0, 6, ignoreCase = true) }?.substring(6)?.trim()?.takeIf { it.isNotBlank() }
