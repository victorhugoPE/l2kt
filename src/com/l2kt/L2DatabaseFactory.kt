package com.l2kt

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

import java.sql.Connection
import java.sql.SQLException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Database connection factory using HikariCP.
 *
 * HikariCP is the fastest JDBC connection pool (microsecond-level getConnection).
 * Replaces legacy C3P0 0.9.5-pre5 which was a pre-release with known instability.
 *
 * Key improvements over C3P0:
 * - ~100x faster connection acquisition
 * - Built-in connection leak detection
 * - Prepared statement caching via MySQL driver (cachePrepStmts)
 * - Proper idle connection eviction
 */
object L2DatabaseFactory {

    private val log = Logger.getLogger(L2DatabaseFactory::class.java.name)
    private val dataSource: HikariDataSource

    val connection: Connection
        get() = dataSource.connection

    init {
        try {
            val config = HikariConfig().apply {
                // Driver & URL
                driverClassName = "com.mysql.cj.jdbc.Driver"
                jdbcUrl = Config.DATABASE_URL
                username = Config.DATABASE_LOGIN
                password = Config.DATABASE_PASSWORD

                // Pool sizing
                minimumIdle = 10
                maximumPoolSize = maxOf(10, Config.DATABASE_MAX_CONNECTIONS)

                // Timeouts
                connectionTimeout = 30_000   // 30s max wait for connection
                idleTimeout = 600_000        // 10min idle before eviction
                maxLifetime = 1_800_000      // 30min max connection lifetime
                keepaliveTime = 300_000      // 5min keepalive ping

                // Leak detection (log warning if connection held > 60s)
                leakDetectionThreshold = 60_000

                // MySQL performance tuning (driver-level statement cache)
                addDataSourceProperty("cachePrepStmts", "true")
                addDataSourceProperty("prepStmtCacheSize", "250")
                addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
                addDataSourceProperty("useServerPrepStmts", "true")
                addDataSourceProperty("useLocalSessionState", "true")
                addDataSourceProperty("rewriteBatchedStatements", "true")
                addDataSourceProperty("cacheResultSetMetadata", "true")
                addDataSourceProperty("cacheServerConfiguration", "true")
                addDataSourceProperty("elideSetAutoCommits", "true")
                addDataSourceProperty("maintainTimeStats", "false")

                // Pool name (visible in JMX/logs)
                poolName = "L2kt-HikariPool"

                // Auto-commit (matches legacy C3P0 behavior)
                isAutoCommit = true
            }

            dataSource = HikariDataSource(config)

            // Validate connectivity at startup
            dataSource.connection.use { conn ->
                log.info("Database connected: ${conn.metaData.databaseProductName} ${conn.metaData.databaseProductVersion}")
                log.info("HikariCP pool: min=${config.minimumIdle}, max=${config.maximumPoolSize}")
            }
        } catch (e: SQLException) {
            log.log(Level.SEVERE, "Failed to initialize database connection pool", e)
            throw e
        } catch (e: Exception) {
            log.log(Level.SEVERE, "Failed to initialize database", e)
            throw SQLException("Could not init DB connection: ${e.message}", e)
        }
    }

    fun shutdown() {
        try {
            if (!dataSource.isClosed) {
                dataSource.close()
                log.info("Database pool closed.")
            }
        } catch (e: Exception) {
            log.log(Level.WARNING, "Error closing database pool", e)
        }
    }
}