package com.neogenesis.server.modules.commercial

import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.domain.model.AuditEvent
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class CommercialService(
    private val repository: CommercialRepository,
    private val auditTrailService: AuditTrailService,
) {
    fun createAccount(tenantId: String, name: String, country: String?, industry: String?, website: String?, actorId: String): CommercialAccount {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val account =
            CommercialAccount(
                id = UUID.randomUUID(),
                tenantId = tenantId,
                name = name.trim(),
                country = country,
                industry = industry,
                website = website,
                createdAt = now,
                updatedAt = now,
            )
        repository.createAccount(account)
        audit("commercial.account.create", tenantId, actorId, account.id, account.name)
        repository.logActivity(
            CommercialActivity(
                id = UUID.randomUUID(),
                tenantId = tenantId,
                actorId = actorId,
                action = "account.create",
                entityType = "account",
                entityId = account.id,
                metadataJson = "{\"name\":\"${account.name}\"}",
                createdAt = now,
            ),
        )
        return account
    }

    fun updateAccount(tenantId: String, accountId: UUID, name: String, country: String?, industry: String?, website: String?, actorId: String): CommercialAccount {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val account =
            CommercialAccount(
                id = accountId,
                tenantId = tenantId,
                name = name.trim(),
                country = country,
                industry = industry,
                website = website,
                createdAt = now,
                updatedAt = now,
            )
        repository.updateAccount(account)
        audit("commercial.account.update", tenantId, actorId, account.id, account.name)
        repository.logActivity(
            CommercialActivity(
                id = UUID.randomUUID(),
                tenantId = tenantId,
                actorId = actorId,
                action = "account.update",
                entityType = "account",
                entityId = account.id,
                metadataJson = "{\"name\":\"${account.name}\"}",
                createdAt = now,
            ),
        )
        return account
    }

    fun listAccounts(tenantId: String): List<CommercialAccount> = repository.listAccounts(tenantId)

    fun createContact(
        tenantId: String,
        accountId: UUID,
        fullName: String,
        email: String?,
        role: String?,
        phone: String?,
        actorId: String,
    ): CommercialContact {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val contact =
            CommercialContact(
                id = UUID.randomUUID(),
                tenantId = tenantId,
                accountId = accountId,
                fullName = fullName.trim(),
                email = email,
                role = role,
                phone = phone,
                createdAt = now,
                updatedAt = now,
            )
        repository.createContact(contact)
        audit("commercial.contact.create", tenantId, actorId, contact.id, contact.fullName)
        repository.logActivity(
            CommercialActivity(
                id = UUID.randomUUID(),
                tenantId = tenantId,
                actorId = actorId,
                action = "contact.create",
                entityType = "contact",
                entityId = contact.id,
                metadataJson = "{\"fullName\":\"${contact.fullName}\"}",
                createdAt = now,
            ),
        )
        return contact
    }

    fun listContacts(tenantId: String, accountId: UUID?): List<CommercialContact> = repository.listContacts(tenantId, accountId)

    fun createOpportunity(
        tenantId: String,
        accountId: UUID,
        stage: OpportunityStage,
        expectedValueEur: Long,
        probability: Double,
        closeDate: java.time.LocalDate?,
        owner: String,
        notes: String?,
        actorId: String,
    ): CommercialOpportunity {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val opportunity =
            CommercialOpportunity(
                id = UUID.randomUUID(),
                tenantId = tenantId,
                accountId = accountId,
                stage = stage,
                expectedValueEur = expectedValueEur,
                probability = probability,
                closeDate = closeDate,
                owner = owner.trim(),
                notes = notes,
                createdAt = now,
                updatedAt = now,
            )
        repository.createOpportunity(opportunity)
        audit("commercial.opportunity.create", tenantId, actorId, opportunity.id, opportunity.stage.name)
        repository.logActivity(
            CommercialActivity(
                id = UUID.randomUUID(),
                tenantId = tenantId,
                actorId = actorId,
                action = "opportunity.create",
                entityType = "opportunity",
                entityId = opportunity.id,
                metadataJson = "{\"stage\":\"${opportunity.stage.name}\"}",
                createdAt = now,
            ),
        )
        return opportunity
    }

    fun updateOpportunity(
        tenantId: String,
        opportunityId: UUID,
        accountId: UUID,
        stage: OpportunityStage,
        expectedValueEur: Long,
        probability: Double,
        closeDate: java.time.LocalDate?,
        owner: String,
        notes: String?,
        actorId: String,
    ): CommercialOpportunity {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val opportunity =
            CommercialOpportunity(
                id = opportunityId,
                tenantId = tenantId,
                accountId = accountId,
                stage = stage,
                expectedValueEur = expectedValueEur,
                probability = probability,
                closeDate = closeDate,
                owner = owner.trim(),
                notes = notes,
                createdAt = now,
                updatedAt = now,
            )
        repository.updateOpportunity(opportunity)
        audit("commercial.opportunity.update", tenantId, actorId, opportunity.id, opportunity.stage.name)
        repository.logActivity(
            CommercialActivity(
                id = UUID.randomUUID(),
                tenantId = tenantId,
                actorId = actorId,
                action = "opportunity.update",
                entityType = "opportunity",
                entityId = opportunity.id,
                metadataJson = "{\"stage\":\"${opportunity.stage.name}\"}",
                createdAt = now,
            ),
        )
        return opportunity
    }

    fun listOpportunities(tenantId: String, stage: OpportunityStage?): List<CommercialOpportunity> = repository.listOpportunities(tenantId, stage)

    fun createLoi(
        tenantId: String,
        opportunityId: UUID,
        signedDate: java.time.LocalDate?,
        amountRange: String?,
        attachmentRef: String?,
        status: String,
        actorId: String,
    ): CommercialLoi {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val loi =
            CommercialLoi(
                id = UUID.randomUUID(),
                tenantId = tenantId,
                opportunityId = opportunityId,
                signedDate = signedDate,
                amountRange = amountRange,
                attachmentRef = attachmentRef,
                status = status,
                createdAt = now,
                updatedAt = now,
            )
        repository.createLoi(loi)
        audit("commercial.loi.create", tenantId, actorId, loi.id, loi.status)
        repository.logActivity(
            CommercialActivity(
                id = UUID.randomUUID(),
                tenantId = tenantId,
                actorId = actorId,
                action = "loi.create",
                entityType = "loi",
                entityId = loi.id,
                metadataJson = "{\"status\":\"${loi.status}\"}",
                createdAt = now,
            ),
        )
        return loi
    }

    fun updateLoiAttachment(
        tenantId: String,
        loiId: UUID,
        attachmentRef: String?,
        status: String,
        actorId: String,
    ) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        repository.updateLoiAttachment(tenantId, loiId, attachmentRef, status, now)
        audit("commercial.loi.attachment", tenantId, actorId, loiId, status)
        repository.logActivity(
            CommercialActivity(
                id = UUID.randomUUID(),
                tenantId = tenantId,
                actorId = actorId,
                action = "loi.attachment",
                entityType = "loi",
                entityId = loiId,
                metadataJson = "{\\\"status\\\":\\\"$status\\\"}",
                createdAt = now,
            ),
        )
    }

    fun listLois(tenantId: String, opportunityId: UUID?): List<CommercialLoi> = repository.listLois(tenantId, opportunityId)

    fun pipelineSummary(tenantId: String): PipelineSummary = repository.pipelineSummary(tenantId)

    private fun audit(action: String, tenantId: String, actorId: String, entityId: UUID, details: String) {
        auditTrailService.record(
            AuditEvent(
                tenantId = tenantId,
                actorId = actorId,
                action = action,
                entityId = entityId.toString(),
                entityType = "commercial",
                outcome = "success",
                details = details,
            ),
        )
    }
}
