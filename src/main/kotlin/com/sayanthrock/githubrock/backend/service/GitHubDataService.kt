package com.sayanthrock.githubrock.backend.service

import com.sayanthrock.githubrock.backend.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.lettuce.core.RedisClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.security.MessageDigest

class GitHubDataService(
    private val config: AppConfig,
    private val httpClient: HttpClient,
    private val redisClient: RedisClient,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val baseUrl = "https://api.github.com"
    private val maxReadmeBytes = 1_048_576

    suspend fun search(query: String, page: Int, perPage: Int, token: String?): GitHubDataResult {
        val path = "/search/repositories?q=" + query.encodeUrl() + "&page=" + page + "&per_page=" + perPage
        return getJson(path, token, 120)
    }

    suspend fun repo(owner: String, name: String, token: String?): GitHubDataResult =
        getJson("/repos/" + owner.safePath() + "/" + name.safePath(), token, 300)

    suspend fun user(username: String, token: String?): GitHubDataResult =
        getJson("/users/" + username.safePath(), token, 300)

    suspend fun readme(owner: String, name: String, token: String?): GitHubReadmeResult {
        val cacheKey = cacheKey("readme", owner, name)
        if (token == null) readCache(cacheKey)?.let { return GitHubReadmeResult(it, true) }
        val response = httpClient.get(baseUrl + "/repos/" + owner.safePath() + "/" + name.safePath() + "/readme") {
            header(HttpHeaders.Accept, "application/vnd.github.raw+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            if (!token.isNullOrBlank()) bearerAuth(token)
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) throw GitHubDataException(response.status.value, body.take(512))
        if (body.toByteArray(Charsets.UTF_8).size > maxReadmeBytes) {
            throw GitHubDataException(413, "README exceeds the 1 MiB size limit")
        }
        if (token == null && body.isNotBlank()) writeCache(cacheKey, body, 300)
        return GitHubReadmeResult(body, false)
    }

    private suspend fun getJson(path: String, token: String?, ttl: Long): GitHubDataResult {
        val cacheKey = cacheKey("json", path)
        if (token == null) readCache(cacheKey)?.let { return GitHubDataResult(json.parseToJsonElement(it), true) }
        val response = httpClient.get(baseUrl + path) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            if (!token.isNullOrBlank()) bearerAuth(token)
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) throw GitHubDataException(response.status.value, body.take(512))
        val parsed = json.parseToJsonElement(body)
        if (token == null) writeCache(cacheKey, body, ttl)
        return GitHubDataResult(parsed, false)
    }

    private suspend fun readCache(key: String): String? = withContext(Dispatchers.IO) {
        runCatching { redisClient.connect().use { it.sync().get(key) } }.getOrNull()
    }

    private suspend fun writeCache(key: String, value: String, ttl: Long) = withContext(Dispatchers.IO) {
        runCatching { redisClient.connect().use { it.sync().setex(key, ttl, value) } }
    }

    private fun cacheKey(prefix: String, vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(parts.joinToString("|").toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "github-rock:" + prefix + ":" + digest
    }

    private fun String.safePath(): String = encodeUrl().replace("/", "%2F")
    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8).replace("+", "%20")
}

data class GitHubDataResult(val data: JsonElement, val cached: Boolean)
data class GitHubReadmeResult(val content: String, val cached: Boolean)
class GitHubDataException(val status: Int, message: String) : RuntimeException(message)