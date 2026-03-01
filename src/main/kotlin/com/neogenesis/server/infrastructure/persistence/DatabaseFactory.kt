package com.neogenesis.server.infrastructure.persistence

import com.neogenesis.server.infrastructure.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

class DatabaseFactory(private val config: AppConfig.DatabaseConfig) {
    fun initialize(): DataSource {
        val hikariConfig =
            HikariConfig().apply {
                jdbcUrl = config.jdbcUrl
                username = config.username
                password = config.password
                maximumPoolSize = config.maximumPoolSize
                connectionTimeout = config.connectionTimeoutMs
                validationTimeout = config.validationTimeoutMs
                idleTimeout = config.idleTimeoutMs
                maxLifetime = config.maxLifetimeMs
                isAutoCommit = true
                poolName = "NeoGenesis-Hikari"
            }

        val dataSource = HikariDataSource(hikariConfig)

        if (config.migrateOnStartup) {
            val locations = mutableListOf("classpath:db/migration")
            if (config.jdbcUrl.startsWith("jdbc:postgresql:", ignoreCase = true)) {
                locations.add("classpath:db/migration_postgres")
            }

            Flyway.configure()
                .dataSource(dataSource)
                .locations(*locations.toTypedArray())
                .baselineOnMigrate(true)
                .load()
                .migrate()
        }

        return dataSource
    }
}
