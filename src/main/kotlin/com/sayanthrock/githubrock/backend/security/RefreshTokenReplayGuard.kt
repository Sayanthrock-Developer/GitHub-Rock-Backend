package com.sayanthrock.githubrock.backend.security

import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/** Prevents replay of an OAuth refresh token across backend instances. */
class RefreshTokenReplayGuard(
    private val redisClient: RedisClient,
) {
    suspend fun claim(refreshToken: String, ttlSeconds: Long = DEFAULT_TTL_SECONDS): Boolean =
        withContext(Dispatchers.IO) {
            val key = "github-rock:oauth:refresh-used:${sha256(refreshToken)}"
            redisClient.connect().use { connection ->
                connection.sync().set(
                    key,
                    "1",
                    SetArgs.Builder.nx().ex(ttlSeconds),
                ) == "OK"
            }
        }

    private companion object {
        const val DEFAULT_TTL_SECONDS = 86_400L

        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
