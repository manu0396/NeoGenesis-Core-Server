package com.neogenesis.server.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TraceabilityRequirement(
    val requirementId: String,
    val isoClause: String,
    val title: String,
    val linkedOperations: List<String>,
    val verification: String
)
