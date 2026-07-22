package com.sayanthrock.githubrock.backend.storage

import com.sayanthrock.githubrock.backend.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway

fun createDataSource(config: AppConfig): HikariDataSource = HikariDataSource(HikariConfig().apply {
    jdbcUrl = config.databaseUrl
    username = config.databaseUser
    password = config.databasePassword
    maximumPoolSize = 8
    minimumIdle = 1
    connectionTimeout = 5_000
    validationTimeout = 2_000
    isAutoCommit = true
    poolName = "github-rock-backend"
})

fun migrateDatabase(config: AppConfig, dataSource: HikariDataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .cleanDisabled(true)
        .load()
        .migrate()
}
