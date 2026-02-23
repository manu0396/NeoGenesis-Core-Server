package com.neogenesis.server.application.billing

import java.time.Instant

data class BillingPlan(
    val id: String,
    val name: String,
    val features: Set<String>,
)

data class BillingSubscription(
    val subjectId: String,
    val status: String,
    val planId: String,
    val stripeCustomerId: String?,
    val stripeSubscriptionId: String?,
    val currentPeriodEnd: Instant?,
)

data class BillingStatus(
    val planId: String,
    val status: String,
    val periodEnd: Instant?,
    val entitlements: Set<String>,
)
