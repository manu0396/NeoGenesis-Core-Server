package com.neogenesis.server.modules.commercial

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class CommercialAccount(
    val id: UUID,
    val tenantId: String,
    val name: String,
    val country: String?,
    val industry: String?,
    val website: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class CommercialContact(
    val id: UUID,
    val tenantId: String,
    val accountId: UUID,
    val fullName: String,
    val email: String?,
    val role: String?,
    val phone: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

enum class OpportunityStage {
    Lead,
    Qualified,
    LOI,
    Negotiation,
    Won,
    Lost,
}

data class CommercialOpportunity(
    val id: UUID,
    val tenantId: String,
    val accountId: UUID,
    val stage: OpportunityStage,
    val expectedValueEur: Long,
    val probability: Double,
    val closeDate: LocalDate?,
    val owner: String,
    val notes: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class CommercialLoi(
    val id: UUID,
    val tenantId: String,
    val opportunityId: UUID,
    val signedDate: LocalDate?,
    val amountRange: String?,
    val attachmentRef: String?,
    val status: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class CommercialActivity(
    val id: UUID,
    val tenantId: String,
    val actorId: String,
    val action: String,
    val entityType: String,
    val entityId: UUID,
    val metadataJson: String?,
    val createdAt: OffsetDateTime,
)

data class PipelineSummary(
    val totalCount: Int,
    val byStage: Map<OpportunityStage, Int>,
    val weightedValueEur: Long,
)
