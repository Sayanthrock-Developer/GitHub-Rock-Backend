package com.sayanthrock.githubrock.backend.plugins

import com.sayanthrock.githubrock.backend.model.ErrorResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import org.slf4j.event.Level

fun Application.configureHttp() {
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("Referrer-Policy", "no-referrer")
        header("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
    }
    install(Compression)
    install(AutoHeadResponse)
    install(CallLogging) {
        level = Level.INFO
        filter { call -> !call.request.path().contains("/health") }
    }
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-Hub-Signature-256")
        allowHeader("X-GitHub-Delivery")
        allowHeader("X-GitHub-Event")
        allowHost("localhost:8080", schemes = listOf("http"))
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", cause.message ?: "Invalid request"))
        }
        exception<Throwable> { call, _ ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_error", "The request could not be completed"))
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, ErrorResponse("not_found", "Route not found"))
        }
    }
}
