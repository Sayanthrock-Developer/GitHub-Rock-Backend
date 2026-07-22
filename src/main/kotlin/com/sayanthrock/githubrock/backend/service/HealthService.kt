package com.sayanthrock.githubrock.backend.service

import com.sayanthrock.githubrock.backend.config.AppConfig
import com.sayanthrock.githubrock.backend.model.HealthResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import io.lettuce.core.RedisClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.sql.DataSource

class HealthService(
    private val config: AppConfig,
    private val dataSource: DataSource,
    private val redisClient: RedisClient,
    private val httpClient: HttpClient,
) {
    suspend fun check(): HealthResponse {
        val postgres = runCatching { checkPostgres() }.fold({ "ok" }, { "unavailable" })
        val redis = runCatching { checkRedis() }.fold({ "ok" }, { "unavailable" })
        val meilisearch = runCatching { checkMeili() }.fold({ "ok" }, { "unavailable" })
        val overall = if (listOf(postgres, redis, meilisearch).all { it == "ok" }) "healthy" else "degraded"
        return HealthResponse(overall, "0.1.0", postgres, redis, meilisearch, Instant.now().toString())
    }

    private suspend fun checkPostgres() = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT 1").use { statement ->
                check(statement.executeQuery().next())
            }
        }
    }

    private suspend fun checkRedis() = withContext(Dispatchers.IO) {
        redisClient.connect().use { connection ->
            check(connection.sync().ping() == "PONG")
        }
    }

    private suspend fun checkMeili() {
        check(httpClient.get("${config.meiliUrl.trimEnd('/')}/health").status.isSuccess())
    }
}
