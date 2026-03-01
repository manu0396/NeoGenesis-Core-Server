package com.neogenesis.server.infrastructure.persistence

import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import java.sql.Connection
import javax.sql.DataSource

object TenantContext {
    private val currentTenant = ThreadLocal<String?>()

    fun get(): String? = currentTenant.get()

    fun set(tenantId: String?) {
        currentTenant.set(tenantId)
    }

    suspend fun <T> withTenant(
        tenantId: String,
        block: suspend () -> T,
    ): T {
        return withContext(currentTenant.asContextElement(tenantId)) {
            block()
        }
    }
}

fun DataSource.getTenantConnection(tenantId: String? = TenantContext.get()): Connection {
    val connection = this.connection
    if (tenantId != null) {
        val isPostgres = connection.metaData.databaseProductName.contains("PostgreSQL", ignoreCase = true)
        if (isPostgres) {
            connection.prepareStatement("SET LOCAL app.current_tenant = ?").use { statement ->
                statement.setString(1, tenantId)
                statement.execute()
            }
        }
    }
    return connection
}

inline fun <T> DataSource.useTenantConnection(
    tenantId: String? = TenantContext.get(),
    block: (Connection) -> T,
): T {
    return getTenantConnection(tenantId).use(block)
}
