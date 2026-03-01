package com.neogenesis.server.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class GdprErasureRecord(
    val id: Long? = null,
    val patientId: String,
    val requestedBy: String,
    val reason: String,
    val outcome: String,
    val affectedRows: Int,
    val createdAtMs: Long = System.currentTimeMillis(),
)
