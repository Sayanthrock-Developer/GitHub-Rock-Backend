package com.sayanthrock.githubrock.backend.config

data class AppConfig(
    val environment: String,
    val port: Int,
    val publicBaseUrl: String,
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    val redisUrl: String,
    val meiliUrl: String,
    val meiliMasterKey: String,
    val githubOauthClientId: String,
    val githubWebhookSecret: String,
    val deviceIdPepper: String,
    val adminToken: String,
    val minSupportedAppVersion: String,
    val latestAppVersion: String,
    val maintenanceMode: Boolean,
    val sentryDsn: String?,
) {
    val isProduction: Boolean get() = environment.equals("production", ignoreCase = true)

    fun validate() {
        if (!isProduction) return
        val required = mapOf(
            "PUBLIC_BASE_URL" to publicBaseUrl,
            "DATABASE_URL" to databaseUrl,
            "DATABASE_USER" to databaseUser,
            "DATABASE_PASSWORD" to databasePassword,
            "REDIS_URL" to redisUrl,
            "MEILI_URL" to meiliUrl,
            "MEILI_MASTER_KEY" to meiliMasterKey,
            "GITHUB_OAUTH_CLIENT_ID" to githubOauthClientId,
            "GITHUB_WEBHOOK_SECRET" to githubWebhookSecret,
            "DEVICE_ID_PEPPER" to deviceIdPepper,
            "ADMIN_TOKEN" to adminToken,
        )
        val missing = required.filterValues {
            it.isBlank() || it.startsWith("change-me") || it.startsWith("replace-")
        }.keys
        require(missing.isEmpty()) {
            "Missing or unsafe production configuration: ${missing.sorted().joinToString()}"
        }
        require(publicBaseUrl.startsWith("https://")) {
            "PUBLIC_BASE_URL must use HTTPS in production"
        }
    }

    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): AppConfig = AppConfig(
            environment = env["APP_ENV"] ?: "development",
            port = env["PORT"]?.toIntOrNull() ?: 8080,
            publicBaseUrl = env["PUBLIC_BASE_URL"] ?: "http://localhost:8080",
            databaseUrl = env["DATABASE_URL"] ?: "jdbc:postgresql://localhost:5432/githubrock",
            databaseUser = env["DATABASE_USER"] ?: "githubrock",
            databasePassword = env["DATABASE_PASSWORD"] ?: "githubrock-dev",
            redisUrl = env["REDIS_URL"] ?: "redis://localhost:6379",
            meiliUrl = env["MEILI_URL"] ?: "http://localhost:7700",
            meiliMasterKey = env["MEILI_MASTER_KEY"] ?: "githubrock-dev-key",
            githubOauthClientId = env["GITHUB_OAUTH_CLIENT_ID"] ?: "",
            githubWebhookSecret = env["GITHUB_WEBHOOK_SECRET"] ?: "",
            deviceIdPepper = env["DEVICE_ID_PEPPER"] ?: "",
            adminToken = env["ADMIN_TOKEN"] ?: "",
            minSupportedAppVersion = env["MIN_SUPPORTED_APP_VERSION"] ?: "0.1.0",
            latestAppVersion = env["LATEST_APP_VERSION"] ?: "0.1.0",
            maintenanceMode = env["MAINTENANCE_MODE"].toBoolean(),
            sentryDsn = env["SENTRY_DSN"]?.takeIf(String::isNotBlank),
        )
    }
}
