package com.neogenesis.server.modules

import com.neogenesis.server.application.billing.BillingService
import com.neogenesis.server.infrastructure.security.actor
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

fun Route.billingModule(billingService: BillingService) {
    authenticate("auth-jwt") {
        post("/billing/checkout-session") {
            val request = call.receive<CheckoutSessionRequest>()
            val session =
                billingService.createCheckoutSession(
                    subjectId = call.actor(),
                    planId = request.planId,
                    customerEmail = request.customerEmail,
                )
            call.respond(
                CheckoutSessionResponse(
                    url = session.url,
                ),
            )
        }

        post("/billing/portal-session") {
            val session = billingService.createPortalSession(call.actor())
            call.respond(PortalSessionResponse(url = session.url))
        }

        get("/billing/status") {
            val status = billingService.statusFor(call.actor())
            call.respond(
                BillingStatusResponse(
                    planId = status.planId,
                    status = status.status,
                    periodEnd = status.periodEnd?.toString(),
                    entitlements = status.entitlements.toList(),
                ),
            )
        }
    }

    post("/billing/webhook") {
        val signature =
            call.request.header("Stripe-Signature")
                ?: throw ApiException(
                    "missing_signature",
                    "Stripe-Signature header is required",
                    HttpStatusCode.BadRequest,
                )
        val payload = call.receiveText()
        val result = billingService.handleWebhook(payload, signature)
        call.respond(
            HttpStatusCode.OK,
            mapOf("status" to result.status),
        )
    }
}

@Serializable
data class CheckoutSessionRequest(
    val planId: String,
    val customerEmail: String? = null,
)

@Serializable
data class CheckoutSessionResponse(
    val url: String,
)

@Serializable
data class PortalSessionResponse(
    val url: String,
)

@Serializable
data class BillingStatusResponse(
    val planId: String,
    val status: String,
    val periodEnd: String?,
    val entitlements: List<String>,
)
