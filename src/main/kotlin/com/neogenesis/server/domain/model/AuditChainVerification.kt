package com.neogenesis.server.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AuditChainVerification(
    val valid: Boolean,
    val checkedEvents: Int,
    val failureIndex: Int?,
    val failureReason: String?,
)
