package com.neogenesis.server.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ConsentStatus {
    GRANTED,
    REVOKED,
}

@Serializable
data class GdprConsentRecord(
    val tenantId: String,
    val id: Long? = null,
    val patientId: String,
    val purpose: String,
    val status: ConsentStatus,
    val legalBasis: String,
    val grantedBy: String,
    val createdAtMs: Long = System.currentTimeMillis(),
)
