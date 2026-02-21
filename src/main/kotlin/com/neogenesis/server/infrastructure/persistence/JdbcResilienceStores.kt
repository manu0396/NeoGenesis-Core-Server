package com.neogenesis.server.infrastructure.persistence

import com.neogenesis.server.application.port.IdempotencyRememberResult
import com.neogenesis.server.application.port.RequestIdempotencyStore
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

class JdbcRequestIdempotencyStore(
    private val dataSource: DataSource
) : RequestIdempotencyStore {

    override fun remember(
        operation: String,
        key: String,
        payloadHash: String,
        ttlSeconds: Long
    ): IdempotencyRememberResult {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    """
                    DELETE FROM request_idempotency
                    WHERE expires_at < CURRENT_TIMESTAMP
                    """.trimIndent()
                ).use { statement ->
                    statement.executeUpdate()
                }

                val existingHash = connection.prepareStatement(
                    """
                    SELECT payload_hash
                    FROM request_idempotency
                    WHERE operation = ?
                      AND idempotency_key = ?
                      AND expires_at >= CURRENT_TIMESTAMP
                    """.trimIndent()
                ).use { statement ->
                    statement.setString(1, operation)
                    statement.setString(2, key)
                    statement.executeQuery().use { rs ->
                        if (rs.next()) rs.getString("payload_hash") else null
                    }
                }

                if (existingHash != null) {
                    connection.commit()
                    return if (existingHash == payloadHash) {
                        IdempotencyRememberResult.DUPLICATE_MATCH
                    } else {
                        IdempotencyRememberResult.DUPLICATE_MISMATCH
                    }
                }

                val expiresAt = Timestamp.from(Instant.now().plusSeconds(ttlSeconds.coerceAtLeast(60L)))
                connection.prepareStatement(
                    """
                    INSERT INTO request_idempotency(
                        operation,
                        idempotency_key,
                        payload_hash,
                        created_at,
                        expires_at
                    ) VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?)
                    """.trimIndent()
                ).use { statement ->
                    statement.setString(1, operation)
                    statement.setString(2, key)
                    statement.setString(3, payloadHash)
                    statement.setTimestamp(4, expiresAt)
                    statement.executeUpdate()
                }
                connection.commit()
                return IdempotencyRememberResult.STORED
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }
}
