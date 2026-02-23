package com.neogenesis.server.application.billing

import com.neogenesis.server.infrastructure.config.AppConfig
import com.neogenesis.server.infrastructure.persistence.BillingEventRepository
import com.neogenesis.server.infrastructure.persistence.BillingPlanRepository
import com.neogenesis.server.infrastructure.persistence.BillingSubscriptionRepository
import com.neogenesis.server.modules.ApiException
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant

class BillingService(
    private val config: AppConfig.BillingConfig,
    private val planRepository: BillingPlanRepository,
    private val subscriptionRepository: BillingSubscriptionRepository,
    private val eventRepository: BillingEventRepository,
    private val provider: BillingProvider,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    fun seedPlans() {
        val free = BillingPlan(config.freePlanId, "Free", config.freePlanFeatures)
        val pro = BillingPlan(config.proPlanId, "Pro", config.proPlanFeatures)
        planRepository.upsert(free)
        planRepository.upsert(pro)
    }

    fun createCheckoutSession(
        subjectId: String,
        planId: String,
        customerEmail: String?,
    ): CheckoutSessionResult {
        ensureEnabled()
        ensurePlanExists(planId)
        return provider.createCheckoutSession(
            CheckoutSessionRequest(
                subjectId = subjectId,
                planId = planId,
                customerEmail = customerEmail,
            ),
        )
    }

    fun createPortalSession(subjectId: String): PortalSessionResult {
        ensureEnabled()
        val subscription =
            subscriptionRepository.findBySubjectId(subjectId)
                ?: throw ApiException(
                    code = "subscription_not_found",
                    message = "No active subscription for subject",
                    status = HttpStatusCode.NotFound,
                )
        val customerId =
            subscription.stripeCustomerId
                ?: throw ApiException(
                    code = "missing_customer_id",
                    message = "Subscription has no billing customer id",
                    status = HttpStatusCode.BadRequest,
                )
        return provider.createPortalSession(
            PortalSessionRequest(
                subjectId = subjectId,
                customerId = customerId,
            ),
        )
    }

    fun statusFor(subjectId: String): BillingStatus {
        if (!config.enabled) {
            return BillingStatus(
                planId = config.freePlanId,
                status = "disabled",
                periodEnd = null,
                entitlements = config.allEntitlements(),
            )
        }
        val subscription = subscriptionRepository.findBySubjectId(subjectId)
        val plan =
            subscription?.planId
                ?.let { planRepository.findById(it) }
                ?: BillingPlan(config.freePlanId, "Free", config.freePlanFeatures)
        val status = subscription?.status ?: "none"
        return BillingStatus(
            planId = plan.id,
            status = status,
            periodEnd = subscription?.currentPeriodEnd,
            entitlements = plan.features,
        )
    }

    fun requireEntitlement(
        subjectId: String,
        entitlement: String,
    ) {
        if (!config.enabled) {
            return
        }
        val status = statusFor(subjectId)
        if (!status.entitlements.contains(entitlement)) {
            throw ApiException(
                code = "payment_required",
                message = "Entitlement required: $entitlement",
                status = HttpStatusCode.PaymentRequired,
            )
        }
    }

    fun handleWebhook(
        payload: String,
        signatureHeader: String,
    ): WebhookResult {
        if (!config.enabled) {
            return WebhookResult("billing_disabled")
        }

        val event = provider.verifyWebhook(payload, signatureHeader)
        val payloadHash = sha256(payload)
        val inserted = eventRepository.tryInsert(event.id, payloadHash)
        if (!inserted) {
            return WebhookResult("duplicate")
        }

        val envelope = json.decodeFromString(StripeEventEnvelope.serializer(), payload)
        when (event.type) {
            "checkout.session.completed" -> handleCheckoutCompleted(envelope)
            "customer.subscription.created",
            "customer.subscription.updated",
            "customer.subscription.deleted",
            -> handleSubscriptionEvent(envelope)
        }

        return WebhookResult("processed")
    }

    private fun handleCheckoutCompleted(envelope: StripeEventEnvelope) {
        val session = json.decodeFromJsonElement<StripeCheckoutSession>(envelope.data.objectPayload)
        val subjectId = session.metadata["subjectId"] ?: return
        val planId = session.metadata["planId"] ?: config.proPlanId
        upsertSubscription(
            subjectId = subjectId,
            status = "active",
            planId = planId,
            stripeCustomerId = session.customer,
            stripeSubscriptionId = session.subscription,
            currentPeriodEnd = null,
        )
    }

    private fun handleSubscriptionEvent(envelope: StripeEventEnvelope) {
        val subscription = json.decodeFromJsonElement<StripeSubscription>(envelope.data.objectPayload)
        val subjectId = subscription.metadata["subjectId"] ?: return
        val planId =
            subscription.metadata["planId"]
                ?: config.planIdForPrice(subscription.primaryPriceId())
                ?: config.freePlanId
        val currentPeriodEnd =
            subscription.currentPeriodEnd?.let { Instant.ofEpochSecond(it) }
        upsertSubscription(
            subjectId = subjectId,
            status = subscription.status,
            planId = planId,
            stripeCustomerId = subscription.customer,
            stripeSubscriptionId = subscription.id,
            currentPeriodEnd = currentPeriodEnd,
        )
    }

    private fun upsertSubscription(
        subjectId: String,
        status: String,
        planId: String,
        stripeCustomerId: String?,
        stripeSubscriptionId: String?,
        currentPeriodEnd: Instant?,
    ) {
        subscriptionRepository.upsert(
            BillingSubscription(
                subjectId = subjectId,
                status = status,
                planId = planId,
                stripeCustomerId = stripeCustomerId,
                stripeSubscriptionId = stripeSubscriptionId,
                currentPeriodEnd = currentPeriodEnd,
            ),
        )
    }

    private fun ensureEnabled() {
        if (!config.enabled) {
            throw ApiException(
                code = "billing_disabled",
                message = "Billing is disabled in this environment",
                status = HttpStatusCode.BadRequest,
            )
        }
    }

    private fun ensurePlanExists(planId: String) {
        if (planRepository.findById(planId) == null) {
            throw ApiException(
                code = "plan_not_found",
                message = "Plan not found: $planId",
                status = HttpStatusCode.BadRequest,
            )
        }
    }

    private fun sha256(payload: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}

data class WebhookResult(val status: String)

@Serializable
private data class StripeEventEnvelope(
    val id: String,
    val type: String,
    val data: StripeEventData,
)

@Serializable
private data class StripeEventData(
    val `object`: JsonElement,
) {
    val objectPayload: JsonElement
        get() = `object`
}

@Serializable
private data class StripeCheckoutSession(
    val id: String,
    val customer: String? = null,
    val subscription: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
private data class StripeSubscription(
    val id: String,
    val status: String,
    val customer: String? = null,
    @SerialName("current_period_end")
    val currentPeriodEnd: Long? = null,
    val metadata: Map<String, String> = emptyMap(),
    val items: StripeSubscriptionItems = StripeSubscriptionItems(emptyList()),
) {
    fun primaryPriceId(): String? {
        return items.data.firstOrNull()?.price?.id
    }
}

@Serializable
private data class StripeSubscriptionItems(
    val data: List<StripeSubscriptionItem>,
)

@Serializable
private data class StripeSubscriptionItem(
    val price: StripePrice? = null,
)

@Serializable
private data class StripePrice(
    val id: String? = null,
)

fun AppConfig.BillingConfig.allEntitlements(): Set<String> {
    return freePlanFeatures + proPlanFeatures
}

fun AppConfig.BillingConfig.planIdForPrice(priceId: String?): String? {
    if (priceId.isNullOrBlank()) {
        return null
    }
    return when (priceId) {
        stripe.priceIdPro -> proPlanId
        else -> null
    }
}
