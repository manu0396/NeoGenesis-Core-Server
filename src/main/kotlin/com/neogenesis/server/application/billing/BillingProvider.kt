package com.neogenesis.server.application.billing

data class CheckoutSessionRequest(
    val subjectId: String,
    val planId: String,
    val customerEmail: String?,
)

data class CheckoutSessionResult(
    val url: String,
    val providerSessionId: String,
)

data class PortalSessionRequest(
    val subjectId: String,
    val customerId: String,
)

data class PortalSessionResult(
    val url: String,
)

data class WebhookEvent(
    val id: String,
    val type: String,
    val payload: String,
)

interface BillingProvider {
    fun createCheckoutSession(request: CheckoutSessionRequest): CheckoutSessionResult

    fun createPortalSession(request: PortalSessionRequest): PortalSessionResult

    fun verifyWebhook(
        payload: String,
        signatureHeader: String,
    ): WebhookEvent
}
