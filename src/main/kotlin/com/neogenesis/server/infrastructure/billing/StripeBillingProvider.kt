package com.neogenesis.server.infrastructure.billing

import com.neogenesis.server.application.billing.BillingProvider
import com.neogenesis.server.application.billing.CheckoutSessionRequest
import com.neogenesis.server.application.billing.CheckoutSessionResult
import com.neogenesis.server.application.billing.PortalSessionRequest
import com.neogenesis.server.application.billing.PortalSessionResult
import com.neogenesis.server.application.billing.WebhookEvent
import com.neogenesis.server.infrastructure.config.AppConfig
import com.neogenesis.server.modules.ApiException
import com.stripe.Stripe
import com.stripe.model.checkout.Session
import com.stripe.param.checkout.SessionCreateParams
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import com.stripe.model.billingportal.Session as PortalSession
import com.stripe.param.billingportal.SessionCreateParams as PortalSessionParams

class StripeBillingProvider(
    private val config: AppConfig.BillingConfig,
) : BillingProvider {
    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    override fun createCheckoutSession(request: CheckoutSessionRequest): CheckoutSessionResult {
        val stripe = config.stripe
        val secret =
            stripe.secretKey
                ?: throw ApiException(
                    "stripe_secret_missing",
                    "Stripe secret key is required",
                    HttpStatusCode.BadRequest,
                )
        val priceId =
            when (request.planId) {
                config.proPlanId -> stripe.priceIdPro
                config.freePlanId -> stripe.priceIdFree
                else -> null
            }
                ?: throw ApiException(
                    "stripe_price_missing",
                    "Stripe price id is required",
                    HttpStatusCode.BadRequest,
                )

        Stripe.apiKey = secret

        val paramsBuilder =
            SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(stripe.successUrl)
                .setCancelUrl(stripe.cancelUrl)
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1L)
                        .build(),
                )
                .putMetadata("subjectId", request.subjectId)
                .putMetadata("planId", request.planId)
                .setSubscriptionData(
                    SessionCreateParams.SubscriptionData.builder()
                        .putMetadata("subjectId", request.subjectId)
                        .putMetadata("planId", request.planId)
                        .build(),
                )

        if (!request.customerEmail.isNullOrBlank()) {
            paramsBuilder.setCustomerEmail(request.customerEmail)
        }

        val session = Session.create(paramsBuilder.build())
        val url =
            session.url
                ?: throw ApiException(
                    "stripe_session_missing",
                    "Stripe did not return a checkout URL",
                    HttpStatusCode.BadRequest,
                )
        return CheckoutSessionResult(url = url, providerSessionId = session.id)
    }

    override fun createPortalSession(request: PortalSessionRequest): PortalSessionResult {
        val stripe = config.stripe
        val secret =
            stripe.secretKey
                ?: throw ApiException(
                    "stripe_secret_missing",
                    "Stripe secret key is required",
                    HttpStatusCode.BadRequest,
                )
        Stripe.apiKey = secret

        val params =
            PortalSessionParams.builder()
                .setCustomer(request.customerId)
                .setReturnUrl(stripe.portalReturnUrl)
                .build()
        val session = PortalSession.create(params)
        val url =
            session.url
                ?: throw ApiException(
                    "stripe_portal_missing",
                    "Stripe did not return a portal URL",
                    HttpStatusCode.BadRequest,
                )
        return PortalSessionResult(url = url)
    }

    override fun verifyWebhook(
        payload: String,
        signatureHeader: String,
    ): WebhookEvent {
        val secret =
            config.stripe.webhookSecret
                ?: throw ApiException(
                    "stripe_webhook_secret_missing",
                    "Stripe webhook secret is required",
                    HttpStatusCode.BadRequest,
                )
        if (!StripeSignatureVerifier(secret).verify(signatureHeader, payload)) {
            throw ApiException("invalid_signature", "Stripe signature verification failed", HttpStatusCode.BadRequest)
        }
        val envelope = json.decodeFromString(StripeEnvelope.serializer(), payload)
        return WebhookEvent(
            id = envelope.id,
            type = envelope.type,
            payload = payload,
        )
    }
}

class FakeBillingProvider : BillingProvider {
    override fun createCheckoutSession(request: CheckoutSessionRequest): CheckoutSessionResult {
        return CheckoutSessionResult(
            url = "https://billing.local/checkout?plan=${request.planId}&subject=${request.subjectId}",
            providerSessionId = "fake-session-${request.subjectId}",
        )
    }

    override fun createPortalSession(request: PortalSessionRequest): PortalSessionResult {
        return PortalSessionResult(
            url = "https://billing.local/portal?customer=${request.customerId}",
        )
    }

    override fun verifyWebhook(
        payload: String,
        signatureHeader: String,
    ): WebhookEvent {
        throw ApiException("webhook_not_supported", "Fake billing provider does not accept webhooks", HttpStatusCode.BadRequest)
    }
}

class StripeSignatureVerifier(
    private val secret: String,
    private val toleranceSeconds: Long = 300,
) {
    fun verify(
        signatureHeader: String,
        payload: String,
    ): Boolean {
        val parts = signatureHeader.split(',').map { it.trim() }
        val timestampPart = parts.firstOrNull { it.startsWith("t=") } ?: return false
        val signaturePart = parts.firstOrNull { it.startsWith("v1=") } ?: return false
        val timestamp = timestampPart.removePrefix("t=").toLongOrNull() ?: return false
        val signature = signaturePart.removePrefix("v1=")

        val signedPayload = "$timestamp.$payload"
        val computed = hmacSha256(secret, signedPayload)
        val withinTolerance = abs(System.currentTimeMillis() / 1000 - timestamp) <= toleranceSeconds
        return withinTolerance && computed.equals(signature, ignoreCase = true)
    }

    private fun hmacSha256(
        secret: String,
        data: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val bytes = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

@Serializable
private data class StripeEnvelope(
    val id: String,
    val type: String,
)
