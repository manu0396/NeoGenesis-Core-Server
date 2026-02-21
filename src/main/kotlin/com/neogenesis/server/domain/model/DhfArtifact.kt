package com.neogenesis.server.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DhfArtifact(
    val id: Long? = null,
    val artifactType: String,
    val artifactName: String,
    val version: String,
    val location: String,
    val checksumSha256: String,
    val approvedBy: String,
    val approvedAtMs: Long = System.currentTimeMillis()
)
