package com.neogenesis.server.application.billing

import com.neogenesis.server.infrastructure.billing.FakeBillingProvider
import com.neogenesis.server.infrastructure.config.AppConfig
import com.neogenesis.server.infrastructure.persistence.BillingEventRepository
import com.neogenesis.server.infrastructure.persistence.BillingPlanRepository
import com.neogenesis.server.infrastructure.persistence.BillingSubscriptionRepository
import com.neogenesis.server.infrastructure.persistence.DatabaseFactory
import com.neogenesis.server.modules.ApiException
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EntitlementResolverTest {
    @Test
    fun enforces_entitlements_when_billing_enabled() {
        val dataSource = DatabaseFactory(h2Config()).initialize()
        val billingConfig = billingConfig(enabled = true)
        val service =
            BillingService(
                config = billingConfig,
                planRepository = BillingPlanRepository(dataSource),
                subscriptionRepository = BillingSubscriptionRepository(dataSource),
                eventRepository = BillingEventRepository(dataSource),
                provider = FakeBillingProvider(),
            )
        service.seedPlans()

        val subscriptionRepo = BillingSubscriptionRepository(dataSource)
        subscriptionRepo.upsert(
            BillingSubscription(
                subjectId = "user-1",
                status = "active",
                planId = billingConfig.proPlanId,
                stripeCustomerId = null,
                stripeSubscriptionId = null,
                currentPeriodEnd = null,
            ),
        )

        service.requireEntitlement("user-1", "audit:evidence_export")

        val error =
            assertFailsWith<ApiException> {
                service.requireEntitlement("user-2", "audit:evidence_export")
            }
        assertEquals(HttpStatusCode.PaymentRequired, error.status)
    }

    private fun h2Config(): AppConfig.DatabaseConfig {
        return AppConfig.DatabaseConfig(
            jdbcUrl = "jdbc:h2:mem:billing-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            username = "sa",
            password = "",
            maximumPoolSize = 5,
            migrateOnStartup = true,
            connectionTimeoutMs = 3_000,
            validationTimeoutMs = 1_000,
            idleTimeoutMs = 600_000,
            maxLifetimeMs = 1_800_000,
        )
    }

    private fun billingConfig(enabled: Boolean): AppConfig.BillingConfig {
        return AppConfig.BillingConfig(
            enabled = enabled,
            provider = "fake",
            freePlanId = "free",
            proPlanId = "pro",
            freePlanFeatures = emptySet(),
            proPlanFeatures = setOf("audit:evidence_export", "compliance:traceability_audit"),
            stripe =
                AppConfig.BillingConfig.StripeConfig(
                    secretKey = null,
                    webhookSecret = null,
                    priceIdFree = null,
                    priceIdPro = null,
                    successUrl = "https://example.com/billing/success",
                    cancelUrl = "https://example.com/billing/cancel",
                    portalReturnUrl = "https://example.com/billing/portal",
                ),
        )
    }
}
