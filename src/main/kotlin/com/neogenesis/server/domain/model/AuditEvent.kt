package com.neogenesis.server.domain.model

data class AuditEvent(
    val tenantId: String,
    val actor: String,
    val action: String,
    val resourceType: String,
    val resourceId: String?,
    val outcome: String,
    val requirementIds: List<String>,
    val details: Map<String, String>,
    val createdAtMs: Long = System.currentTimeMillis(),
)
