package com.neogenesis.server.modules.commercial

import com.neogenesis.server.infrastructure.security.NeoGenesisPrincipal
import com.neogenesis.server.infrastructure.security.actor
import com.neogenesis.server.modules.ApiException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

fun Route.commercialModule(
    service: CommercialService,
) {
    authenticate("auth-jwt") {
        post("/commercial/accounts") {
            val request = call.receive<CreateAccountRequest>()
            requireCommercialHeaders(request.tenantId, request.correlationId, call.principal())
            val account =
                service.createAccount(
                    tenantId = request.tenantId,
                    name = request.name,
                    country = request.country,
                    industry = request.industry,
                    website = request.website,
                    actorId = call.actor(),
                )
            call.respond(account)
        }

        post("/commercial/accounts/{id}") {
            val request = call.receive<UpdateAccountRequest>()
            requireCommercialHeaders(request.tenantId, request.correlationId, call.principal())
            val id = UUID.fromString(call.parameters["id"])
            val account =
                service.updateAccount(
                    tenantId = request.tenantId,
                    accountId = id,
                    name = request.name,
                    country = request.country,
                    industry = request.industry,
                    website = request.website,
                    actorId = call.actor(),
                )
            call.respond(account)
        }

        get("/commercial/accounts") {
            val tenantId = call.request.queryParameters["tenant_id"] ?: throw ApiException("tenant_required", "tenant_id is required", HttpStatusCode.BadRequest)
            requireCorrelation(call.request.headers["X-Correlation-Id"], call.request.headers["X-Request-Id"], call.principal())
            requireTenantMatch(tenantId, call.principal())
            call.respond(service.listAccounts(tenantId))
        }

        post("/commercial/contacts") {
            val request = call.receive<CreateContactRequest>()
            requireCommercialHeaders(request.tenantId, request.correlationId, call.principal())
            val contact =
                service.createContact(
                    tenantId = request.tenantId,
                    accountId = UUID.fromString(request.accountId),
                    fullName = request.fullName,
                    email = request.email,
                    role = request.role,
                    phone = request.phone,
                    actorId = call.actor(),
                )
            call.respond(contact)
        }

        get("/commercial/contacts") {
            val tenantId = call.request.queryParameters["tenant_id"] ?: throw ApiException("tenant_required", "tenant_id is required", HttpStatusCode.BadRequest)
            requireCorrelation(call.request.headers["X-Correlation-Id"], call.request.headers["X-Request-Id"], call.principal())
            requireTenantMatch(tenantId, call.principal())
            val accountId = call.request.queryParameters["account_id"]?.let(UUID::fromString)
            call.respond(service.listContacts(tenantId, accountId))
        }

        post("/commercial/opportunities") {
            val request = call.receive<CreateOpportunityRequest>()
            requireCommercialHeaders(request.tenantId, request.correlationId, call.principal())
            val opportunity =
                service.createOpportunity(
                    tenantId = request.tenantId,
                    accountId = UUID.fromString(request.accountId),
                    stage = OpportunityStage.valueOf(request.stage),
                    expectedValueEur = request.expectedValueEur,
                    probability = request.probability,
                    closeDate = request.closeDate?.let(LocalDate::parse),
                    owner = request.owner,
                    notes = request.notes,
                    actorId = call.actor(),
                )
            call.respond(opportunity)
        }

        post("/commercial/opportunities/{id}") {
            val request = call.receive<UpdateOpportunityRequest>()
            requireCommercialHeaders(request.tenantId, request.correlationId, call.principal())
            val id = UUID.fromString(call.parameters["id"])
            val opportunity =
                service.updateOpportunity(
                    tenantId = request.tenantId,
                    opportunityId = id,
                    accountId = UUID.fromString(request.accountId),
                    stage = OpportunityStage.valueOf(request.stage),
                    expectedValueEur = request.expectedValueEur,
                    probability = request.probability,
                    closeDate = request.closeDate?.let(LocalDate::parse),
                    owner = request.owner,
                    notes = request.notes,
                    actorId = call.actor(),
                )
            call.respond(opportunity)
        }

        get("/commercial/opportunities") {
            val tenantId = call.request.queryParameters["tenant_id"] ?: throw ApiException("tenant_required", "tenant_id is required", HttpStatusCode.BadRequest)
            requireCorrelation(call.request.headers["X-Correlation-Id"], call.request.headers["X-Request-Id"], call.principal())
            requireTenantMatch(tenantId, call.principal())
            val stage = call.request.queryParameters["stage"]?.let(OpportunityStage::valueOf)
            call.respond(service.listOpportunities(tenantId, stage))
        }

        post("/commercial/lois") {
            val request = call.receive<CreateLoiRequest>()
            requireCommercialHeaders(request.tenantId, request.correlationId, call.principal())
            val loi =
                service.createLoi(
                    tenantId = request.tenantId,
                    opportunityId = UUID.fromString(request.opportunityId),
                    signedDate = request.signedDate?.let(LocalDate::parse),
                    amountRange = request.amountRange,
                    attachmentRef = request.attachmentRef,
                    status = request.status,
                    actorId = call.actor(),
                )
            call.respond(loi)
        }

        post("/commercial/lois/{id}/attachment") {
            val request = call.receive<UpdateLoiAttachmentRequest>()
            requireCommercialHeaders(request.tenantId, request.correlationId, call.principal())
            val id = UUID.fromString(call.parameters["id"])
            service.updateLoiAttachment(
                tenantId = request.tenantId,
                loiId = id,
                attachmentRef = request.attachmentRef,
                status = request.status,
                actorId = call.actor(),
            )
            call.respond(HttpStatusCode.OK)
        }

        get("/commercial/lois") {
            val tenantId = call.request.queryParameters["tenant_id"] ?: throw ApiException("tenant_required", "tenant_id is required", HttpStatusCode.BadRequest)
            requireCorrelation(call.request.headers["X-Correlation-Id"], call.request.headers["X-Request-Id"], call.principal())
            requireTenantMatch(tenantId, call.principal())
            val opportunityId = call.request.queryParameters["opportunity_id"]?.let(UUID::fromString)
            call.respond(service.listLois(tenantId, opportunityId))
        }

        get("/commercial/pipeline/summary") {
            val tenantId = call.request.queryParameters["tenant_id"] ?: throw ApiException("tenant_required", "tenant_id is required", HttpStatusCode.BadRequest)
            requireCorrelation(call.request.headers["X-Correlation-Id"], call.request.headers["X-Request-Id"], call.principal())
            requireTenantMatch(tenantId, call.principal())
            call.respond(service.pipelineSummary(tenantId))
        }

        get("/commercial/pipeline/export") {
            val tenantId = call.request.queryParameters["tenant_id"] ?: throw ApiException("tenant_required", "tenant_id is required", HttpStatusCode.BadRequest)
            requireCorrelation(call.request.headers["X-Correlation-Id"], call.request.headers["X-Request-Id"], call.principal())
            requireTenantMatch(tenantId, call.principal())
            val rows = service.listOpportunities(tenantId, null)
            val csv = buildCsv(rows)
            call.respondText(csv, ContentType.Text.CSV)
        }
    }
}

private fun requireCommercialHeaders(tenantId: String, correlationId: String, principal: NeoGenesisPrincipal?) {
    if (tenantId.isBlank()) {
        throw ApiException("tenant_required", "tenant_id is required", HttpStatusCode.BadRequest)
    }
    if (correlationId.isBlank()) {
        throw ApiException("correlation_required", "correlation_id is required", HttpStatusCode.BadRequest)
    }
    val principalTenant = principal?.tenantId
    if (!principalTenant.isNullOrBlank() && principalTenant != tenantId) {
        throw ApiException("tenant_mismatch", "tenant mismatch", HttpStatusCode.Forbidden)
    }
    val roles = principal?.roles.orEmpty()
    if (!roles.contains("ADMIN") && !roles.contains("FOUNDER")) {
        throw ApiException("forbidden", "admin or founder required", HttpStatusCode.Forbidden)
    }
}

private fun requireCorrelation(correlation: String?, fallback: String?, principal: NeoGenesisPrincipal?) {
    if (correlation.isNullOrBlank() && fallback.isNullOrBlank()) {
        throw ApiException("correlation_required", "correlation_id is required", HttpStatusCode.BadRequest)
    }
    if (principal == null) {
        throw ApiException("unauthorized", "unauthorized", HttpStatusCode.Unauthorized)
    }
    val roles = principal.roles
    if (!roles.contains("ADMIN") && !roles.contains("FOUNDER")) {
        throw ApiException("forbidden", "admin or founder required", HttpStatusCode.Forbidden)
    }
}

private fun requireTenantMatch(tenantId: String, principal: NeoGenesisPrincipal?) {
    val principalTenant = principal?.tenantId
    if (!principalTenant.isNullOrBlank() && principalTenant != tenantId) {
        throw ApiException("tenant_mismatch", "tenant mismatch", HttpStatusCode.Forbidden)
    }
}

private fun buildCsv(opportunities: List<CommercialOpportunity>): String {
    val header = "opportunity_id,account_id,stage,expected_value_eur,probability,close_date,owner,notes"
    val rows = opportunities.map {
        listOf(
            it.id,
            it.accountId,
            it.stage.name,
            it.expectedValueEur,
            it.probability,
            it.closeDate ?: "",
            it.owner,
            it.notes ?: "",
        ).joinToString(",")
    }
    return (listOf(header) + rows).joinToString("\n")
}

@Serializable
data class CreateAccountRequest(
    val tenantId: String,
    val correlationId: String,
    val name: String,
    val country: String? = null,
    val industry: String? = null,
    val website: String? = null,
)

@Serializable
data class UpdateAccountRequest(
    val tenantId: String,
    val correlationId: String,
    val name: String,
    val country: String? = null,
    val industry: String? = null,
    val website: String? = null,
)

@Serializable
data class CreateContactRequest(
    val tenantId: String,
    val correlationId: String,
    val accountId: String,
    val fullName: String,
    val email: String? = null,
    val role: String? = null,
    val phone: String? = null,
)

@Serializable
data class CreateOpportunityRequest(
    val tenantId: String,
    val correlationId: String,
    val accountId: String,
    val stage: String,
    val expectedValueEur: Long,
    val probability: Double,
    val closeDate: String? = null,
    val owner: String,
    val notes: String? = null,
)

@Serializable
data class UpdateOpportunityRequest(
    val tenantId: String,
    val correlationId: String,
    val accountId: String,
    val stage: String,
    val expectedValueEur: Long,
    val probability: Double,
    val closeDate: String? = null,
    val owner: String,
    val notes: String? = null,
)

@Serializable
data class CreateLoiRequest(
    val tenantId: String,
    val correlationId: String,
    val opportunityId: String,
    val signedDate: String? = null,
    val amountRange: String? = null,
    val attachmentRef: String? = null,
    val status: String,
)

@Serializable
data class UpdateLoiAttachmentRequest(
    val tenantId: String,
    val correlationId: String,
    val attachmentRef: String?,
    val status: String,
)
