package com.sayanthrock.githubrock.backend

import com.sayanthrock.githubrock.backend.config.AppConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppConfigTest {
    @Test
    fun `production rejects insecure defaults`() {
        val config = AppConfig.fromEnvironment(mapOf("APP_ENV" to "production"))
        assertFailsWith<IllegalArgumentException> { config.validate() }
    }

    @Test
    fun `OAuth client secret is loaded only from backend environment`() {
        val config = AppConfig.fromEnvironment(
            mapOf("GITHUB_OAUTH_CLIENT_SECRET" to "server-only-secret")
        )
        assertEquals("server-only-secret", config.githubOauthClientSecret)
    }
}
