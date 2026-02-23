package com.neogenesis.server.infrastructure.persistence

import com.neogenesis.server.application.billing.BillingPlan
import com.neogenesis.server.application.billing.BillingSubscription
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.sql.SQLException
import java.time.Instant
import javax.sql.DataSource

class BillingPlanRepository(
    private val dataSource: DataSource,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    fun upsert(plan: BillingPlan) {
        val updated =
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    UPDATE billing_plans
                    SET name = ?, features_json = ?
                    WHERE id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, plan.name)
                    statement.setString(2, json.encodeToString(ListSerializer(String.serializer()), plan.features.toList()))
                    statement.setString(3, plan.id)
                    statement.executeUpdate()
                }
            }
        if (updated == 0) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO billing_plans (id, name, features_json)
                    VALUES (?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, plan.id)
                    statement.setString(2, plan.name)
                    statement.setString(3, json.encodeToString(ListSerializer(String.serializer()), plan.features.toList()))
                    statement.executeUpdate()
                }
            }
        }
    }

    fun findById(planId: String): BillingPlan? {
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT id, name, features_json
                FROM billing_plans
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, planId)
                statement.executeQuery().use { rs ->
                    if (!rs.next()) {
                        return null
                    }
                    val features =
                        json.decodeFromString(
                            ListSerializer(String.serializer()),
                            rs.getString("features_json"),
                        ).toSet()
                    BillingPlan(
                        id = rs.getString("id"),
                        name = rs.getString("name"),
                        features = features,
                    )
                }
            }
        }
    }
}

class BillingSubscriptionRepository(
    private val dataSource: DataSource,
) {
    fun upsert(subscription: BillingSubscription) {
        val updated =
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    UPDATE billing_subscriptions
                    SET status = ?, plan_id = ?, stripe_customer_id = ?, stripe_subscription_id = ?,
                        current_period_end = ?, updated_at = ?
                    WHERE subject_id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, subscription.status)
                    statement.setString(2, subscription.planId)
                    statement.setString(3, subscription.stripeCustomerId)
                    statement.setString(4, subscription.stripeSubscriptionId)
                    statement.setObject(5, subscription.currentPeriodEnd)
                    statement.setObject(6, Instant.now())
                    statement.setString(7, subscription.subjectId)
                    statement.executeUpdate()
                }
            }
        if (updated == 0) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO billing_subscriptions
                        (subject_id, status, plan_id, stripe_customer_id, stripe_subscription_id, current_period_end)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, subscription.subjectId)
                    statement.setString(2, subscription.status)
                    statement.setString(3, subscription.planId)
                    statement.setString(4, subscription.stripeCustomerId)
                    statement.setString(5, subscription.stripeSubscriptionId)
                    statement.setObject(6, subscription.currentPeriodEnd)
                    statement.executeUpdate()
                }
            }
        }
    }

    fun findBySubjectId(subjectId: String): BillingSubscription? {
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT subject_id, status, plan_id, stripe_customer_id, stripe_subscription_id, current_period_end
                FROM billing_subscriptions
                WHERE subject_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, subjectId)
                statement.executeQuery().use { rs ->
                    if (!rs.next()) {
                        return null
                    }
                    BillingSubscription(
                        subjectId = rs.getString("subject_id"),
                        status = rs.getString("status"),
                        planId = rs.getString("plan_id"),
                        stripeCustomerId = rs.getString("stripe_customer_id"),
                        stripeSubscriptionId = rs.getString("stripe_subscription_id"),
                        currentPeriodEnd = rs.getTimestamp("current_period_end")?.toInstant(),
                    )
                }
            }
        }
    }
}

class BillingEventRepository(
    private val dataSource: DataSource,
) {
    fun tryInsert(
        eventId: String,
        payloadHash: String,
    ): Boolean {
        return try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO billing_events (event_id, payload_hash)
                    VALUES (?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, eventId)
                    statement.setString(2, payloadHash)
                    statement.executeUpdate()
                }
            }
            true
        } catch (ex: SQLException) {
            if (ex.sqlState?.startsWith("23") == true) {
                false
            } else {
                throw ex
            }
        }
    }
}
