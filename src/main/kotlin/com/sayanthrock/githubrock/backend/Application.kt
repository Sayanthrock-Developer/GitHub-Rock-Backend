package com.sayanthrock.githubrock.backend

import com.sayanthrock.githubrock.backend.config.AppConfig
import com.sayanthrock.githubrock.backend.di.appModule
import com.sayanthrock.githubrock.backend.plugins.configureHttp
import com.sayanthrock.githubrock.backend.plugins.configureSerialization
import com.sayanthrock.githubrock.backend.routes.configureRoutes
import com.sayanthrock.githubrock.backend.storage.migrateDatabase
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.lettuce.core.RedisClient
import io.sentry.Sentry
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main() {
    val config = AppConfig.fromEnvironment().also(AppConfig::validate)
    config.sentryDsn?.let { dsn ->
        Sentry.init { options ->
            options.dsn = dsn
            options.environment = config.environment
            options.release = "github-rock-backend@0.1.0"
            options.tracesSampleRate = 0.01
            options.isSendDefaultPii = false
        }
    }
    embeddedServer(Netty, host = "0.0.0.0", port = config.port, module = Application::module).start(wait = true)
}

fun Application.module() {
    install(Koin) {
        slf4jLogger()
        modules(appModule)
    }

    val config = get<AppConfig>()
    val dataSource = get<HikariDataSource>()
    migrateDatabase(config, dataSource)

    configureSerialization()
    configureHttp()
    configureRoutes()

    monitor.subscribe(ApplicationStopped) {
        runCatching { get<RedisClient>().shutdown() }
        runCatching { dataSource.close() }
        Sentry.close()
    }
}
