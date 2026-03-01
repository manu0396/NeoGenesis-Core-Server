package com.neogenesis.server.modules.commercial

import java.sql.Date
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

class CommercialRepository(
    private val dataSource: DataSource,
) {
    fun createAccount(account: CommercialAccount): CommercialAccount {
        dataSource.connection.use { c ->
            val sql =
                """
                    |INSERT INTO commercial_accounts(
                    |    id,
                    |    tenant_id,
                    |    name,
                    |    country,
                    |    industry,
                    |    website,
                    |    created_at,
                    |    updated_at
                    |) VALUES (?,?,?,?,?,?,?,?)
                """.trimMargin()
            c.prepareStatement(sql).use { s ->
                s.setObject(1, account.id)
                s.setString(2, account.tenantId)
                s.setString(3, account.name)
                s.setString(4, account.country)
                s.setString(5, account.industry)
                s.setString(6, account.website)
                s.setTimestamp(7, Timestamp.from(account.createdAt.toInstant()))
                s.setTimestamp(8, Timestamp.from(account.updatedAt.toInstant()))
                s.executeUpdate()
            }
        }
        return account
    }

    fun updateAccount(account: CommercialAccount): CommercialAccount {
        dataSource.connection.use { c ->
            val sql =
                """
                    |UPDATE commercial_accounts
                    |SET name=?, country=?, industry=?, website=?, updated_at=?
                    |WHERE id=? AND tenant_id=?
                """.trimMargin()
            c.prepareStatement(sql).use { s ->
                s.setString(1, account.name)
                s.setString(2, account.country)
                s.setString(3, account.industry)
                s.setString(4, account.website)
                s.setTimestamp(5, Timestamp.from(account.updatedAt.toInstant()))
                s.setObject(6, account.id)
                s.setString(7, account.tenantId)
                s.executeUpdate()
            }
        }
        return account
    }

    fun listAccounts(tenantId: String): List<CommercialAccount> {
        return dataSource.connection.use { c ->
            val sql =
                """
                    |SELECT id, tenant_id, name, country, industry, website, created_at, updated_at
                    |FROM commercial_accounts
                    |WHERE tenant_id=?
                    |ORDER BY updated_at DESC
                """.trimMargin()
            c.prepareStatement(sql).use { s ->
                s.setString(1, tenantId)
                s.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                CommercialAccount(
                                    id = rs.getObject("id", UUID::class.java),
                                    tenantId = rs.getString("tenant_id"),
                                    name = rs.getString("name"),
                                    country = rs.getString("country"),
                                    industry = rs.getString("industry"),
                                    website = rs.getString("website"),
                                    createdAt = rs.getTimestamp("created_at").toInstant().atOffset(ZoneOffset.UTC),
                                    updatedAt = rs.getTimestamp("updated_at").toInstant().atOffset(ZoneOffset.UTC),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun createContact(contact: CommercialContact): CommercialContact {
        dataSource.connection.use { c ->
            val sql =
                """
                    |INSERT INTO commercial_contacts(
                    |    id,
                    |    tenant_id,
                    |    account_id,
                    |    full_name,
                    |    email,
                    |    role,
                    |    phone,
                    |    created_at,
                    |    updated_at
                    |) VALUES (?,?,?,?,?,?,?,?,?)
                """.trimMargin()
            c.prepareStatement(sql).use { s ->
                s.setObject(1, contact.id)
                s.setString(2, contact.tenantId)
                s.setObject(3, contact.accountId)
                s.setString(4, contact.fullName)
                s.setString(5, contact.email)
                s.setString(6, contact.role)
                s.setString(7, contact.phone)
                s.setTimestamp(8, Timestamp.from(contact.createdAt.toInstant()))
                s.setTimestamp(9, Timestamp.from(contact.updatedAt.toInstant()))
                s.executeUpdate()
            }
        }
        return contact
    }

    fun listContacts(
        tenantId: String,
        accountId: UUID?,
    ): List<CommercialContact> {
        val sql =
            if (accountId == null) {
                """
                    |SELECT id, tenant_id, account_id, full_name, email, role, phone, created_at, updated_at
                    |FROM commercial_contacts
                    |WHERE tenant_id=?
                    |ORDER BY updated_at DESC
                """.trimMargin()
            } else {
                """
                    |SELECT id, tenant_id, account_id, full_name, email, role, phone, created_at, updated_at
                    |FROM commercial_contacts
                    |WHERE tenant_id=? AND account_id=?
                    |ORDER BY updated_at DESC
                """.trimMargin()
            }
        return dataSource.connection.use { c ->
            c.prepareStatement(sql).use { s ->
                s.setString(1, tenantId)
                if (accountId != null) s.setObject(2, accountId)
                s.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                CommercialContact(
                                    id = rs.getObject("id", UUID::class.java),
                                    tenantId = rs.getString("tenant_id"),
                                    accountId = rs.getObject("account_id", UUID::class.java),
                                    fullName = rs.getString("full_name"),
                                    email = rs.getString("email"),
                                    role = rs.getString("role"),
                                    phone = rs.getString("phone"),
                                    createdAt = rs.getTimestamp("created_at").toInstant().atOffset(ZoneOffset.UTC),
                                    updatedAt = rs.getTimestamp("updated_at").toInstant().atOffset(ZoneOffset.UTC),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun createOpportunity(opportunity: CommercialOpportunity): CommercialOpportunity {
        dataSource.connection.use { c ->
            val sql =
                """
                    |INSERT INTO commercial_opportunities(
                    |    id,
                    |    tenant_id,
                    |    account_id,
                    |    stage,
                    |    expected_value_eur,
                    |    probability,
                    |    close_date,
                    |    owner,
                    |    notes,
                    |    created_at,
                    |    updated_at
                    |) VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """.trimMargin()
            c.prepareStatement(sql).use { s ->
                s.setObject(1, opportunity.id)
                s.setString(2, opportunity.tenantId)
                s.setObject(3, opportunity.accountId)
                s.setString(4, opportunity.stage.name)
                s.setLong(5, opportunity.expectedValueEur)
                s.setDouble(6, opportunity.probability)
                s.setDate(7, opportunity.closeDate?.let(Date::valueOf))
                s.setString(8, opportunity.owner)
                s.setString(9, opportunity.notes)
                s.setTimestamp(10, Timestamp.from(opportunity.createdAt.toInstant()))
                s.setTimestamp(11, Timestamp.from(opportunity.updatedAt.toInstant()))
                s.executeUpdate()
            }
        }
        return opportunity
    }

    fun updateOpportunity(opportunity: CommercialOpportunity): CommercialOpportunity {
        dataSource.connection.use { c ->
            val sql =
                """
                    |UPDATE commercial_opportunities
                    |SET account_id=?, stage=?, expected_value_eur=?, probability=?, close_date=?, owner=?, notes=?, updated_at=?
                    |WHERE id=? AND tenant_id=?
                """.trimMargin()
            c.prepareStatement(sql).use { s ->
                s.setObject(1, opportunity.accountId)
                s.setString(2, opportunity.stage.name)
                s.setLong(3, opportunity.expectedValueEur)
                s.setDouble(4, opportunity.probability)
                s.setDate(5, opportunity.closeDate?.let(Date::valueOf))
                s.setString(6, opportunity.owner)
                s.setString(7, opportunity.notes)
                s.setTimestamp(8, Timestamp.from(opportunity.updatedAt.toInstant()))
                s.setObject(9, opportunity.id)
                s.setString(10, opportunity.tenantId)
                s.executeUpdate()
            }
        }
        return opportunity
    }

    fun listOpportunities(
        tenantId: String,
        stage: OpportunityStage?,
    ): List<CommercialOpportunity> {
        val sql =
            if (stage == null) {
                """
                    |SELECT id, tenant_id, account_id, stage, expected_value_eur, probability, close_date, owner, notes,
                    |    created_at, updated_at
                    |FROM commercial_opportunities
                    |WHERE tenant_id=?
                    |ORDER BY updated_at DESC
                """.trimMargin()
            } else {
                """
                    |SELECT id, tenant_id, account_id, stage, expected_value_eur, probability, close_date, owner, notes,
                    |    created_at, updated_at
                    |FROM commercial_opportunities
                    |WHERE tenant_id=? AND stage=?
                    |ORDER BY updated_at DESC
                """.trimMargin()
            }
        return dataSource.connection.use { c ->
            c.prepareStatement(sql).use { s ->
                s.setString(1, tenantId)
                if (stage != null) s.setString(2, stage.name)
                s.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                CommercialOpportunity(
                                    id = rs.getObject("id", UUID::class.java),
                                    tenantId = rs.getString("tenant_id"),
                                    accountId = rs.getObject("account_id", UUID::class.java),
                                    stage = OpportunityStage.valueOf(rs.getString("stage")),
                                    expectedValueEur = rs.getLong("expected_value_eur"),
                                    probability = rs.getDouble("probability"),
                                    closeDate = rs.getDate("close_date")?.toLocalDate(),
                                    owner = rs.getString("owner"),
                                    notes = rs.getString("notes"),
                                    createdAt = rs.getTimestamp("created_at").toInstant().atOffset(ZoneOffset.UTC),
                                    updatedAt = rs.getTimestamp("updated_at").toInstant().atOffset(ZoneOffset.UTC),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun createLoi(loi: CommercialLoi): CommercialLoi {
        dataSource.connection.use { c ->
            val sql =
                """
                    |INSERT INTO commercial_lois(
                    |    id,
                    |    tenant_id,
                    |    opportunity_id,
                    |    signed_date,
                    |    amount_range,
                    |    attachment_ref,
                    |    status,
                    |    created_at,
                    |    updated_at
                    |) VALUES (?,?,?,?,?,?,?,?,?)
                """.trimMargin()
            c.prepareStatement(sql).use { s ->
                s.setObject(1, loi.id)
                s.setString(2, loi.tenantId)
                s.setObject(3, loi.opportunityId)
                s.setDate(4, loi.signedDate?.let(Date::valueOf))
                s.setString(5, loi.amountRange)
                s.setString(6, loi.attachmentRef)
                s.setString(7, loi.status)
                s.setTimestamp(8, Timestamp.from(loi.createdAt.toInstant()))
                s.setTimestamp(9, Timestamp.from(loi.updatedAt.toInstant()))
                s.executeUpdate()
            }
        }
        return loi
    }

    fun updateLoiAttachment(
        tenantId: String,
        loiId: UUID,
        attachmentRef: String?,
        status: String,
        updatedAt: OffsetDateTime,
    ) {
        dataSource.connection.use { c ->
            val sql =
                """
                    |UPDATE commercial_lois
                    |SET attachment_ref=?, status=?, updated_at=?
                    |WHERE id=? AND tenant_id=?
                """.trimMargin()
            c.prepareStatement(sql).use { s ->
                s.setString(1, attachmentRef)
                s.setString(2, status)
                s.setTimestamp(3, Timestamp.from(updatedAt.toInstant()))
                s.setObject(4, loiId)
                s.setString(5, tenantId)
                s.executeUpdate()
            }
        }
    }

    fun listLois(
        tenantId: String,
        opportunityId: UUID?,
    ): List<CommercialLoi> {
        val sql =
            if (opportunityId == null) {
                """
                    |SELECT id, tenant_id, opportunity_id, signed_date, amount_range, attachment_ref, status,
                    |    created_at, updated_at
                    |FROM commercial_lois
                    |WHERE tenant_id=?
                    |ORDER BY updated_at DESC
                """.trimMargin()
            } else {
                """
                    |SELECT id, tenant_id, opportunity_id, signed_date, amount_range, attachment_ref, status,
                    |    created_at, updated_at
                    |FROM commercial_lois
                    |WHERE tenant_id=? AND opportunity_id=?
                    |ORDER BY updated_at DESC
                """.trimMargin()
            }
        return dataSource.connection.use { c ->
            c.prepareStatement(sql).use { s ->
                s.setString(1, tenantId)
                if (opportunityId != null) s.setObject(2, opportunityId)
                s.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                CommercialLoi(
                                    id = rs.getObject("id", UUID::class.java),
                                    tenantId = rs.getString("tenant_id"),
                                    opportunityId = rs.getObject("opportunity_id", UUID::class.java),
                                    signedDate = rs.getDate("signed_date")?.toLocalDate(),
                                    amountRange = rs.getString("amount_range"),
                                    attachmentRef = rs.getString("attachment_ref"),
                                    status = rs.getString("status"),
                                    createdAt = rs.getTimestamp("created_at").toInstant().atOffset(ZoneOffset.UTC),
                                    updatedAt = rs.getTimestamp("updated_at").toInstant().atOffset(ZoneOffset.UTC),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun logActivity(activity: CommercialActivity) {
        dataSource.connection.use { c ->
            val sql =
                """
                    |INSERT INTO commercial_activity_log(
                    |    id,
                    |    tenant_id,
                    |    actor_id,
                    |    action,
                    |    entity_type,
                    |    entity_id,
                    |    metadata_json,
                    |    created_at
                    |) VALUES (?,?,?,?,?,?,?,?)
                """.trimMargin()
            c.prepareStatement(sql).use { s ->
                s.setObject(1, activity.id)
                s.setString(2, activity.tenantId)
                s.setString(3, activity.actorId)
                s.setString(4, activity.action)
                s.setString(5, activity.entityType)
                s.setObject(6, activity.entityId)
                s.setString(7, activity.metadataJson)
                s.setTimestamp(8, Timestamp.from(activity.createdAt.toInstant()))
                s.executeUpdate()
            }
        }
    }

    fun pipelineSummary(tenantId: String): PipelineSummary {
        val opportunities = listOpportunities(tenantId, null)
        val byStage = opportunities.groupingBy { it.stage }.eachCount()
        val weighted = opportunities.sumOf { (it.expectedValueEur * it.probability).toLong() }
        return PipelineSummary(
            totalCount = opportunities.size,
            byStage = byStage,
            weightedValueEur = weighted,
        )
    }
}
