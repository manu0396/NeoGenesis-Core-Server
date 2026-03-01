package com.neogenesis.server.infrastructure.persistence

import com.neogenesis.server.infrastructure.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

class DatabaseFactory(private val config: AppConfig.DatabaseConfig) {

    fun initialize(): DataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.username
            password = config.password
            maximumPoolSize = config.maximumPoolSize
            isAutoCommit = true
            poolName = "NeoGenesis-Hikari"
        }

        val dataSource = HikariDataSource(hikariConfig)

        if (config.migrateOnStartup) {
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate()
        }

        return dataSource
    }
}
