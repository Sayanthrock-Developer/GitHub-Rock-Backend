package com.sayanthrock.githubrock.backend.routes

import com.sayanthrock.githubrock.backend.config.AppConfig
import com.sayanthrock.githubrock.backend.model.*
import com.sayanthrock.githubrock.backend.security.RefreshTokenReplayGuard
import com.sayanthrock.githubrock.backend.security.WebhookVerifier
import com.sayanthrock.githubrock.backend.service.GitHubDeviceFlowService
import com.sayanthrock.githubrock.backend.service.GitHubWebOAuthService
import com.sayanthrock.githubrock.backend.service.HealthService
import com.sayanthrock.githubrock.backend.service.StoreCatalogService
import com.sayanthrock.githubrock.backend.storage.WebhookDeliveryRepository
import com.sayanthrock.githubrock.backend.store.STORE_CATEGORIES
import com.sayanthrock.githubrock.backend.store.STORE_PLATFORMS
import io.ktor.http.HttpStatusCode
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
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import org.koin.ktor.ext.inject

private class AuthRateLimiter(private val windowMillis: Long = 60_000L, private val maxRequests: Int = 12) {
    private val buckets = mutableMapOf<String, Pair<Long, Int>>()
    @Synchronized fun allow(key: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val current = buckets[key]
        if (current == null || nowMillis - current.first >= windowMillis) { buckets[key] = nowMillis to 1; if (buckets.size > 10_000) buckets.entries.removeIf { nowMillis - it.value.first >= windowMillis }; return true }
        if (current.second >= maxRequests) return false
        buckets[key] = current.first to current.second + 1
        return true
    }
}
private val authRateLimiter = AuthRateLimiter()

fun Application.configureRoutes() {
    val config by inject<AppConfig>()
    val healthService by inject<HealthService>()
    val deviceFlowService by inject<GitHubDeviceFlowService>()
    val webOAuthService by inject<GitHubWebOAuthService>()
    val refreshTokenReplayGuard by inject<RefreshTokenReplayGuard>()
    val webhookVerifier by inject<WebhookVerifier>()
    val webhookDeliveries by inject<WebhookDeliveryRepository>()
    val storeCatalogService by inject<StoreCatalogService>()

    routing {
        get("/") { call.respond(mapOf("name" to "GitHub Rock Backend", "api" to "/v1", "status" to "running")) }
        route("/v1") {
            get("/health") { val health = healthService.check(); call.respond(if (health.status == "healthy") HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable, health) }
            get("/config") { call.respond(PublicConfigResponse(minSupportedAppVersion = config.minSupportedAppVersion, latestAppVersion = config.latestAppVersion, maintenanceMode = config.maintenanceMode, features = mapOf("oauthDeviceProxy" to deviceFlowService.isConfigured, "oauthRefreshProxy" to deviceFlowService.isRefreshConfigured, "oauthWeb" to webOAuthService.isConfigured, "webhooks" to config.githubWebhookSecret.isNotBlank(), "repositoryCache" to true, "buildMonitoring" to false, "settingsSync" to false))) }

            route("/store") {
                get {
                    call.respond(storeCatalogService.index())
                }
                get("/{category}/{platform}") {
                    val category = call.parameters["category"]
                    val platform = call.parameters["platform"]
                    if (category !in STORE_CATEGORIES || platform !in STORE_PLATFORMS) {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("store_not_found", "Unsupported store category or platform")); return@get
                    }
                    runCatching { storeCatalogService.catalog(category!!, platform!!) }
                        .onSuccess { call.respond(it) }
                        .onFailure { call.respond(HttpStatusCode.BadGateway, ErrorResponse("store_unavailable", "Store data is temporarily unavailable")) }
                }
                get("/search") {
                    val query = call.request.queryParameters["q"].orEmpty()
                    val category = call.request.queryParameters["category"]
                    val platform = call.request.queryParameters["platform"]
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                    if (query.isBlank()) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("missing_query", "Search query is required")); return@get }
                    runCatching { storeCatalogService.search(query, category, platform, limit) }
                        .onSuccess { call.respond(it) }
                        .onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_store_query", it.message ?: "Invalid store query")) }
                }
                get("/repository/{owner}/{repo}") {
                    val owner = call.parameters["owner"]
                    val repo = call.parameters["repo"]
                    if (owner.isNullOrBlank() || repo.isNullOrBlank()) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_repository", "Repository owner and name are required")); return@get }
                    val result = storeCatalogService.search("$owner/$repo", null, null, 100).repositories.firstOrNull { it.fullName.equals("$owner/$repo", ignoreCase = true) }
                    if (result == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("repository_not_found", "Repository is not present in the store catalog"))
                    else call.respond(result)
                }
            }

            route("/auth/device") {
                post("/start") {
                    if (!authRateLimiter.allow("start:${call.request.local.remoteHost}")) { call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many authentication requests")); return@post }
                    if (!deviceFlowService.isConfigured) { call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("oauth_unavailable", "GitHub OAuth Device Flow is not configured")); return@post }
                    call.response.header("Cache-Control", "no-store"); call.respond(deviceFlowService.start())
                }
                post("/poll") {
                    if (!authRateLimiter.allow("poll:${call.request.local.remoteHost}")) { call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many authentication requests")); return@post }
                    if (!deviceFlowService.isConfigured) { call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("oauth_unavailable", "GitHub OAuth Device Flow is not configured")); return@post }
                    val request = call.receive<DevicePollRequest>(); if (request.deviceCode.length !in 10..512) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_device_code", "Invalid device code")); return@post }
                    call.response.header("Cache-Control", "no-store"); call.respond(deviceFlowService.poll(request.deviceCode))
                }
                post("/refresh") {
                    if (!authRateLimiter.allow("refresh:${call.request.local.remoteHost}")) { call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many authentication requests")); return@post }
                    if (!deviceFlowService.isRefreshConfigured) { call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("oauth_refresh_unavailable", "GitHub OAuth token refresh is not configured")); return@post }
                    val request = call.receive<TokenRefreshRequest>(); if (request.refreshToken.length !in 20..4096) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_refresh_token", "Invalid refresh token")); return@post }
                    if (!refreshTokenReplayGuard.claim(request.refreshToken)) { call.respond(HttpStatusCode.Unauthorized, ErrorResponse("refresh_replayed", "Authentication session is invalid or expired")); return@post }
                    call.response.header("Cache-Control", "no-store"); call.respond(deviceFlowService.refresh(request.refreshToken))
                }
            }
            route("/auth/github") {
                get("/start") {
                    if (!authRateLimiter.allow("web-start:${call.request.local.remoteHost}")) { call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many authentication requests")); return@get }
                    if (!webOAuthService.isConfigured) { call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("oauth_web_unavailable", "GitHub web OAuth is not configured")); return@get }
                    val state = call.request.queryParameters["state"]
                    val codeChallenge = call.request.queryParameters["code_challenge"]
                    val method = call.request.queryParameters["code_challenge_method"]
                    if (state.isNullOrBlank() || state.length !in 32..256 || codeChallenge.isNullOrBlank() || codeChallenge.length !in 43..128 || method != "S256") { call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_oauth_request", "Valid OAuth state and S256 code challenge are required")); return@get }
                    call.response.header("Cache-Control", "no-store"); call.respondRedirect(webOAuthService.authorizationUrl(state, codeChallenge), permanent = false)
                }
                get("/callback") {
                    val code = call.request.queryParameters["code"]
                    val state = call.request.queryParameters["state"]
                    val error = call.request.queryParameters["error"]
                    if (error != null) {
                        val description = call.request.queryParameters["error_description"] ?: "GitHub authorization was not completed."
                        call.respondRedirect("githubrock://oauth/callback?error=${error.encodeURLParameter()}&error_description=${description.encodeURLParameter()}", permanent = false); return@get
                    }
                    if (code.isNullOrBlank() || state.isNullOrBlank() || state.length !in 32..256 || code.length !in 10..4096) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_callback", "GitHub OAuth callback is missing required parameters")); return@get }
                    call.response.header("Cache-Control", "no-store"); call.respondRedirect("githubrock://oauth/callback?code=${code.encodeURLParameter()}&state=${state.encodeURLParameter()}", permanent = false)
                }
                post("/exchange") {
                    if (!authRateLimiter.allow("web-exchange:${call.request.local.remoteHost}")) { call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many authentication requests")); return@post }
                    if (!webOAuthService.isConfigured) { call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("oauth_web_unavailable", "GitHub web OAuth is not configured")); return@post }
                    val request = call.receive<WebOAuthExchangeRequest>()
                    if (request.code.length !in 10..4096 || request.codeVerifier.length !in 43..128) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_oauth_exchange", "Invalid authorization code or PKCE verifier")); return@post }
                    val response = webOAuthService.exchange(request.code, request.codeVerifier)
                    if (response.accessToken.isNullOrBlank()) { call.respond(HttpStatusCode.Unauthorized, ErrorResponse("oauth_exchange_failed", response.errorDescription ?: "GitHub authorization code could not be exchanged")); return@post }
                    call.response.header("Cache-Control", "no-store")
                    call.respond(mapOf("state" to "authorized", "access_token" to response.accessToken, "token_type" to response.tokenType, "scope" to response.scope, "expires_in" to response.expiresIn, "refresh_token" to response.refreshToken, "refresh_token_expires_in" to response.refreshTokenExpiresIn))
                }
            }
            post("/github/webhooks") {
                val signature = call.request.headers["X-Hub-Signature-256"]; val deliveryId = call.request.headers["X-GitHub-Delivery"]; val event = call.request.headers["X-GitHub-Event"]
                if (deliveryId.isNullOrBlank() || event.isNullOrBlank()) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("missing_headers", "GitHub delivery and event headers are required")); return@post }
                val payload = call.receiveChannel().readRemaining(max = 1_048_577L).readByteArray()
                if (payload.size > 1_048_576) { call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("payload_too_large", "Webhook payload exceeds 1 MiB")); return@post }
                if (!webhookVerifier.verify(payload, signature)) { call.respond(HttpStatusCode.Unauthorized, ErrorResponse("invalid_signature", "Webhook signature is invalid")); return@post }
                val inserted = webhookDeliveries.register(deliveryId, event)
                call.respond(if (inserted) HttpStatusCode.Accepted else HttpStatusCode.OK, WebhookAcceptedResponse(true, !inserted, deliveryId, event))
            }
        }
    }
}
