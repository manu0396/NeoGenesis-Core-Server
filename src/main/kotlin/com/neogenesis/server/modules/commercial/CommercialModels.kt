package com.neogenesis.server.modules.commercial

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

object UuidSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

object OffsetDateTimeSerializer : KSerializer<OffsetDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: OffsetDateTime) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): OffsetDateTime = OffsetDateTime.parse(decoder.decodeString())
}

@Serializable
data class CommercialAccount(
    @Serializable(with = UuidSerializer::class)
    val id: UUID,
    val tenantId: String,
    val name: String,
    val country: String?,
    val industry: String?,
    val website: String?,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val createdAt: OffsetDateTime,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val updatedAt: OffsetDateTime,
)

@Serializable
data class CommercialContact(
    @Serializable(with = UuidSerializer::class)
    val id: UUID,
    val tenantId: String,
    @Serializable(with = UuidSerializer::class)
    val accountId: UUID,
    val fullName: String,
    val email: String?,
    val role: String?,
    val phone: String?,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val createdAt: OffsetDateTime,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val updatedAt: OffsetDateTime,
)

@Serializable
enum class OpportunityStage {
    Lead,
    Qualified,
    LOI,
    Negotiation,
    Won,
    Lost,
}

@Serializable
data class CommercialOpportunity(
    @Serializable(with = UuidSerializer::class)
    val id: UUID,
    val tenantId: String,
    @Serializable(with = UuidSerializer::class)
    val accountId: UUID,
    val stage: OpportunityStage,
    val expectedValueEur: Long,
    val probability: Double,
    @Serializable(with = LocalDateSerializer::class)
    val closeDate: LocalDate?,
    val owner: String,
    val notes: String?,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val createdAt: OffsetDateTime,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val updatedAt: OffsetDateTime,
)

@Serializable
data class CommercialLoi(
    @Serializable(with = UuidSerializer::class)
    val id: UUID,
    val tenantId: String,
    @Serializable(with = UuidSerializer::class)
    val opportunityId: UUID,
    @Serializable(with = LocalDateSerializer::class)
    val signedDate: LocalDate?,
    val amountRange: String?,
    val attachmentRef: String?,
    val status: String,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val createdAt: OffsetDateTime,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val updatedAt: OffsetDateTime,
)

@Serializable
data class CommercialActivity(
    @Serializable(with = UuidSerializer::class)
    val id: UUID,
    val tenantId: String,
    val actorId: String,
    val action: String,
    val entityType: String,
    @Serializable(with = UuidSerializer::class)
    val entityId: UUID,
    val metadataJson: String?,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val createdAt: OffsetDateTime,
)

@Serializable
data class PipelineSummary(
    val totalCount: Int,
    val byStage: Map<OpportunityStage, Int>,
    val weightedValueEur: Long,
)
