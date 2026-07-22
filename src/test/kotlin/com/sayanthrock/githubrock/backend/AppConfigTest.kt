package com.sayanthrock.githubrock.backend

import com.sayanthrock.githubrock.backend.config.AppConfig
import kotlin.test.Test
import kotlin.test.assertFailsWith

class AppConfigTest {
    @Test
    fun `production rejects insecure defaults`() {
        val config = AppConfig.fromEnvironment(mapOf("APP_ENV" to "production"))
        assertFailsWith<IllegalArgumentException> { config.validate() }
    }
}
