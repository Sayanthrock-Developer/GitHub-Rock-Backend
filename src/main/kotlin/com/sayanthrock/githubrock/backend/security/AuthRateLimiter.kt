package com.sayanthrock.githubrock.backend.security

import io.lettuce.core.RedisClient
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.sync.RedisCommands
import java.security.MessageDigest

class AuthRateLimiter(
    redisClient: RedisClient,
    private val windowSeconds: Long = 60,
    private val maxRequests: Long = 12,
) {
    private val connection = redisClient.connect()
    private val commands: RedisCommands<String, String> = connection.sync()

    private val script = """
        local current = redis.call('INCR', KEYS[1])
        if current == 1 then
            redis.call('EXPIRE', KEYS[1], ARGV[1])
        end
        return current
    """.trimIndent()

    fun allow(bucket: String, key: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val count = commands.eval(
            script,
            ScriptOutputType.INTEGER,
            arrayOf("github-rock:auth-rate:$bucket:$digest"),
            windowSeconds.toString(),
        )
        return count <= maxRequests
    }

    fun close() {
        connection.close()
    }
}
