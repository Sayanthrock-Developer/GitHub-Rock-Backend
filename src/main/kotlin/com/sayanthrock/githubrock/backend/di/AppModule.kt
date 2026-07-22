package com.sayanthrock.githubrock.backend.di

import com.sayanthrock.githubrock.backend.config.AppConfig
import com.sayanthrock.githubrock.backend.security.WebhookVerifier
import com.sayanthrock.githubrock.backend.service.GitHubDeviceFlowService
import com.sayanthrock.githubrock.backend.service.HealthService
import com.sayanthrock.githubrock.backend.storage.WebhookDeliveryRepository
import com.sayanthrock.githubrock.backend.storage.createDataSource
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.lettuce.core.RedisClient
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val appModule = module {
    single { AppConfig.fromEnvironment().also(AppConfig::validate) }
    single { createDataSource(get()) }
    single { RedisClient.create(get<AppConfig>().redisUrl) }
    single {
        HttpClient(CIO) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                })
            }
        }
    }
    single { WebhookVerifier(get<AppConfig>().githubWebhookSecret) }
    single { WebhookDeliveryRepository(get<HikariDataSource>()) }
    single { HealthService(get(), get<HikariDataSource>(), get(), get()) }
    single { GitHubDeviceFlowService(get(), get()) }
}
