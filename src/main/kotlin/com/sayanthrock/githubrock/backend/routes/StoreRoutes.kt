package com.sayanthrock.githubrock.backend.routes

import com.sayanthrock.githubrock.backend.service.GitHubStoreBackendService
import com.sayanthrock.githubrock.backend.service.respondStore
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.configureStoreRoutes() {
    val storeService by inject<GitHubStoreBackendService>()

    route("/store") {
        get("/search") {
            respondStore(storeService.get("/v1/search", call.request.queryParameters.toMap()))
        }
        get("/search/explore") {
            respondStore(storeService.get("/v1/search/explore", call.request.queryParameters.toMap()))
        }
        get("/categories/{category}/{platform}") {
            respondStore(storeService.get("/v1/categories/${call.parameters["category"]}/${call.parameters["platform"]}", call.request.queryParameters.toMap()))
        }
        get("/topics/{bucket}/{platform}") {
            respondStore(storeService.get("/v1/topics/${call.parameters["bucket"]}/${call.parameters["platform"]}", call.request.queryParameters.toMap()))
        }
        get("/repo/{owner}/{name}") {
            respondStore(storeService.get("/v1/repo/${call.parameters["owner"]}/${call.parameters["name"]}", emptyMap()))
        }
        get("/readme/{owner}/{name}") {
            respondStore(storeService.get("/v1/readme/${call.parameters["owner"]}/${call.parameters["name"]}", emptyMap()))
        }
        get("/user/{username}") {
            respondStore(storeService.get("/v1/user/${call.parameters["username"]}", emptyMap()))
        }
        post("/events") {
            respondStore(storeService.post("/v1/events", call.receiveText()))
        }
        get("/badge/{owner}/{name}/{kind}/{style}/{variant}") {
            respondStore(storeService.get("/v1/badge/${call.parameters["owner"]}/${call.parameters["name"]}/${call.parameters["kind"]}/${call.parameters["style"]}/${call.parameters["variant"]}", call.request.queryParameters.toMap()))
        }
        get("/badge/{kind}/{style}/{variant}") {
            respondStore(storeService.get("/v1/badge/${call.parameters["kind"]}/${call.parameters["style"]}/${call.parameters["variant"]}", call.request.queryParameters.toMap()))
        }
        get("/health") {
            respondStore(storeService.get("/v1/health", emptyMap()))
        }
    }
}
