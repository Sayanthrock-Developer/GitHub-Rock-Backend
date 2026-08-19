package com.sayanthrock.githubrock.backend.routes

import com.sayanthrock.githubrock.backend.config.AppConfig
import com.sayanthrock.githubrock.backend.model.DevicePollRequest
import com.sayanthrock.githubrock.backend.model.ErrorResponse
import com.sayanthrock.githubrock.backend.model.PublicConfigResponse
import com.sayanthrock.githubrock.backend.model.TokenRefreshRequest
import com.sayanthrock.githubrock.backend.model.WebhookAcceptedResponse
import com.sayanthrock.githubrock.backend.security.RefreshTokenReplayGuard
import com.sayanthrock.githubrock.backend.security.WebhookVerifier
import com.sayanthrock.githubrock.backend.service.GitHubDeviceFlowService
import com.sayanthrock.githubrock.backend.service.HealthService
import com.sayanthrock.githubrock.backend.storage.WebhookDeliveryRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import org.koin.ktor.ext.inject

private class AuthRateLimiter(
    private val windowMillis: Long = 60_000L,
    private val maxRequests: Int = 12,
) {
    private val buckets = mutableMapOf<String, Pair<Long, Int>>()

    @Synchronized
    fun allow(key: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val current = buckets[key]
        if (current == null || nowMillis - current.first >= windowMillis) {
            buckets[key] = nowMillis to 1
            if (buckets.size > 10_000) buckets.entries.removeIf { nowMillis - it.value.first >= windowMillis }
            return true
        }
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
    val refreshTokenReplayGuard by inject<RefreshTokenReplayGuard>()
    val webhookVerifier by inject<WebhookVerifier>()
    val webhookDeliveries by inject<WebhookDeliveryRepository>()

    routing {
        get("/") {
            call.respond(mapOf("name" to "GitHub Rock Backend", "api" to "/v1", "status" to "running"))
        }

        route("/v1") {
            get("/health") {
                val health = healthService.check()
                call.respond(
                    if (health.status == "healthy") HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                    health,
                )
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
                            "webhooks" to config.githubWebhookSecret.isNotBlank(),
                            "repositoryCache" to false,
                            "buildMonitoring" to false,
                            "settingsSync" to false,
                        ),
                    )
                )
            }

            route("/auth/device") {
                post("/start") {
                    val rateKey = "start:${call.request.local.remoteHost}"
                    if (!authRateLimiter.allow(rateKey)) {
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many authentication requests"))
                        return@post
                    }
                    if (!deviceFlowService.isConfigured) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("oauth_unavailable", "GitHub OAuth Device Flow is not configured"),
                        )
                        return@post
                    }
                    call.response.header("Cache-Control", "no-store")
                    call.respond(deviceFlowService.start())
                }
                post("/poll") {
                    val rateKey = "poll:${call.request.local.remoteHost}"
                    if (!authRateLimiter.allow(rateKey, maxOf(System.currentTimeMillis(), 0L))) {
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many authentication requests"))
                        return@post
                    }
                    if (!deviceFlowService.isConfigured) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("oauth_unavailable", "GitHub OAuth Device Flow is not configured"),
                        )
                        return@post
                    }
                    val request = call.receive<DevicePollRequest>()
                    if (request.deviceCode.length !in 10..512) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_device_code", "Invalid device code"))
                        return@post
                    }
                    call.response.header("Cache-Control", "no-store")
                    call.respond(deviceFlowService.poll(request.deviceCode))
                }
                post("/refresh") {
                    val rateKey = "refresh:${call.request.local.remoteHost}"
                    if (!authRateLimiter.allow(rateKey)) {
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many authentication requests"))
                        return@post
                    }
                    if (!deviceFlowService.isRefreshConfigured) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("oauth_refresh_unavailable", "GitHub OAuth token refresh is not configured"),
                        )
                        return@post
                    }
                    val request = call.receive<TokenRefreshRequest>()
                    if (request.refreshToken.length !in 20..4096) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_refresh_token", "Invalid refresh token"))
                        return@post
                    }
                    if (!refreshTokenReplayGuard.claim(request.refreshToken)) {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("refresh_replayed", "Authentication session is invalid or expired"))
                        return@post
                    }
                    call.response.header("Cache-Control", "no-store")
                    call.respond(deviceFlowService.refresh(request.refreshToken))
                }
            }

            post("/github/webhooks") {
                val signature = call.request.headers["X-Hub-Signature-256"]
                val deliveryId = call.request.headers["X-GitHub-Delivery"]
                val event = call.request.headers["X-GitHub-Event"]
                if (deliveryId.isNullOrBlank() || event.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("missing_headers", "GitHub delivery and event headers are required"),
                    )
                    return@post
                }

                val payload = call.receiveChannel().readRemaining(max = 1_048_577L).readByteArray()
                if (payload.size > 1_048_576) {
                    call.respond(
                        HttpStatusCode.PayloadTooLarge,
                        ErrorResponse("payload_too_large", "Webhook payload exceeds 1 MiB"),
                    )
                    return@post
                }
                if (!webhookVerifier.verify(payload, signature)) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse("invalid_signature", "Webhook signature is invalid"),
                    )
                    return@post
                }

                val inserted = webhookDeliveries.register(deliveryId, event)
                call.respond(
                    if (inserted) HttpStatusCode.Accepted else HttpStatusCode.OK,
                    WebhookAcceptedResponse(true, duplicate = !inserted, deliveryId, event),
                )
            }
        }
    }
}
