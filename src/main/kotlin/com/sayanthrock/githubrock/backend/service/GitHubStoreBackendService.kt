package com.sayanthrock.githubrock.backend.service

import com.sayanthrock.githubrock.backend.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.http.URLProtocol
import io.ktor.http.takeFrom
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class GitHubStoreBackendService(
    private val client: HttpClient,
    private val config: AppConfig,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun get(path: String, query: Map<String, String>): StoreProxyResult {
        val response = client.get(buildUrl(path, query))
        return response.toResult()
    }

    suspend fun post(path: String, body: String): StoreProxyResult {
        val response = client.post(buildUrl(path, emptyMap())) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return response.toResult()
    }

    private fun buildUrl(path: String, query: Map<String, String>): String = buildString {
        append(config.storeBackendBaseUrl)
        append('/').append(path.trimStart('/'))
        if (query.isNotEmpty()) {
            append('?')
            query.entries.joinTo(this, "&") { "${it.key.encodeURLParameter()}=${it.value.encodeURLParameter()}" }
        }
    }

    private suspend fun HttpResponse.toResult(): StoreProxyResult {
        val text = bodyAsText()
        val body = runCatching { json.parseToJsonElement(text) }.getOrElse {
            Json.parseToJsonElement("{\"error\":\"invalid_upstream_response\"}")
        }
        return StoreProxyResult(status = status, body = body)
    }
}

data class StoreProxyResult(
    val status: HttpStatusCode,
    val body: JsonElement,
)

private fun String.encodeURLParameter(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8)

suspend fun ApplicationCall.respondStore(result: StoreProxyResult) {
    respond(result.status, result.body)
}
