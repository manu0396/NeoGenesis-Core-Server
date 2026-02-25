@file:Suppress(
    "ktlint:standard:argument-list-wrapping",
    "ktlint:standard:blank-line-before-declaration",
    "ktlint:standard:function-signature",
    "ktlint:standard:max-line-length",
    "ktlint:standard:multiline-expression-wrapping",
    "ktlint:standard:parameter-list-wrapping",
    "ktlint:standard:property-wrapping",
    "ktlint:standard:statement-wrapping",
)

package com.neogenesis.server.infrastructure.persistence

import com.neogenesis.server.application.error.BadRequestException
import com.neogenesis.server.application.error.ConflictException
import com.neogenesis.server.application.regenops.RegenDriftAlert
import com.neogenesis.server.application.regenops.RegenEvidenceChainStatus
import com.neogenesis.server.application.regenops.RegenEvidenceEvent
import com.neogenesis.server.application.regenops.RegenGateway
import com.neogenesis.server.application.regenops.RegenOpsStore
import com.neogenesis.server.application.regenops.ProtocolPublishApproval
import com.neogenesis.server.application.regenops.RegenProtocolDraft
import com.neogenesis.server.application.regenops.RegenProtocolSummary
import com.neogenesis.server.application.regenops.RegenProtocolVersion
import com.neogenesis.server.application.regenops.RegenRun
import com.neogenesis.server.application.regenops.RegenRunEvent
import com.neogenesis.server.application.regenops.RegenRunStatus
import com.neogenesis.server.application.regenops.RegenTelemetryPoint
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

class JdbcRegenOpsStore(private val dataSource: DataSource) : RegenOpsStore {
    override fun createDraft(tenantId: String, protocolId: String, title: String, contentJson: String, updatedBy: String): RegenProtocolDraft {
        val now = Timestamp.from(Instant.now())
        try {
            dataSource.connection.use { c ->
                c.prepareStatement("INSERT INTO regen_protocol_drafts(tenant_id,protocol_id,title,content_json,updated_by,created_at,updated_at) VALUES (?,?,?,?,?,?,?)").use { s ->
                    s.setString(1, tenantId); s.setString(2, protocolId); s.setString(3, title)
                    s.setString(4, canonicalizeJson(contentJson)); s.setString(5, updatedBy)
                    s.setTimestamp(6, now); s.setTimestamp(7, now); s.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            if (e.sqlState?.startsWith("23") == true) throw ConflictException("protocol_draft_exists", "Draft already exists")
            throw e
        }
        return getDraft(tenantId, protocolId) ?: error("failed to persist protocol draft")
    }

    override fun updateDraft(tenantId: String, protocolId: String, title: String, contentJson: String, updatedBy: String): RegenProtocolDraft {
        val rows = dataSource.connection.use { c ->
            c.prepareStatement("UPDATE regen_protocol_drafts SET title=?,content_json=?,updated_by=?,updated_at=? WHERE tenant_id=? AND protocol_id=?").use { s ->
                s.setString(1, title); s.setString(2, canonicalizeJson(contentJson)); s.setString(3, updatedBy)
                s.setTimestamp(4, Timestamp.from(Instant.now())); s.setString(5, tenantId); s.setString(6, protocolId)
                s.executeUpdate()
            }
        }
        if (rows == 0) throw BadRequestException("protocol_draft_missing", "Draft does not exist")
        return getDraft(tenantId, protocolId) ?: error("failed to load updated draft")
    }

    override fun getDraft(tenantId: String, protocolId: String): RegenProtocolDraft? {
        return dataSource.connection.use { c ->
            c.prepareStatement("SELECT tenant_id,protocol_id,title,content_json,updated_by,created_at,updated_at FROM regen_protocol_drafts WHERE tenant_id=? AND protocol_id=?").use { s ->
                s.setString(1, tenantId); s.setString(2, protocolId)
                s.executeQuery().use { rs ->
                    if (!rs.next()) return null
                    RegenProtocolDraft(
                        tenantId = rs.getString("tenant_id"), protocolId = rs.getString("protocol_id"), title = rs.getString("title"),
                        contentJson = rs.getString("content_json"), updatedBy = rs.getString("updated_by"),
                        createdAtMs = rs.getTimestamp("created_at").time, updatedAtMs = rs.getTimestamp("updated_at").time,
                    )
                }
            }
        }
    }

    override fun nextProtocolVersion(tenantId: String, protocolId: String): Int {
        return dataSource.connection.use { c ->
            c.prepareStatement("SELECT COALESCE(MAX(version),0) latest FROM regen_protocol_versions WHERE tenant_id=? AND protocol_id=?").use { s ->
                s.setString(1, tenantId); s.setString(2, protocolId)
                s.executeQuery().use { rs -> rs.next(); rs.getInt("latest") + 1 }
            }
        }
    }

    override fun insertProtocolVersion(tenantId: String, protocolId: String, version: Int, title: String, contentJson: String, publishedBy: String, changelog: String): RegenProtocolVersion {
        dataSource.connection.use { c ->
            c.prepareStatement("INSERT INTO regen_protocol_versions(tenant_id,protocol_id,version,title,content_json,published_by,changelog,created_at) VALUES (?,?,?,?,?,?,?,?)").use { s ->
                s.setString(1, tenantId); s.setString(2, protocolId); s.setInt(3, version); s.setString(4, title)
                s.setString(5, canonicalizeJson(contentJson)); s.setString(6, publishedBy); s.setString(7, changelog)
                s.setTimestamp(8, Timestamp.from(Instant.now())); s.executeUpdate()
            }
        }
        return getProtocolVersion(tenantId, protocolId, version) ?: error("failed to persist protocol version")
    }

    override fun getProtocolVersion(tenantId: String, protocolId: String, version: Int): RegenProtocolVersion? {
        return dataSource.connection.use { c ->
            c.prepareStatement("SELECT tenant_id,protocol_id,version,title,content_json,published_by,changelog,created_at FROM regen_protocol_versions WHERE tenant_id=? AND protocol_id=? AND version=?").use { s ->
                s.setString(1, tenantId); s.setString(2, protocolId); s.setInt(3, version)
                s.executeQuery().use { rs -> if (!rs.next()) null else rs.toProtocolVersion() }
            }
        }
    }

    override fun listProtocols(tenantId: String, limit: Int): List<RegenProtocolSummary> {
        return dataSource.connection.use { c ->
            c.prepareStatement(
                "SELECT d.tenant_id,d.protocol_id,d.title,COALESCE(v.latest_version,0) latest_version,TRUE has_draft,d.updated_at updated_at FROM regen_protocol_drafts d LEFT JOIN (SELECT tenant_id,protocol_id,MAX(version) latest_version FROM regen_protocol_versions GROUP BY tenant_id,protocol_id) v ON v.tenant_id=d.tenant_id AND v.protocol_id=d.protocol_id WHERE d.tenant_id=? UNION ALL SELECT pv.tenant_id,pv.protocol_id,pv.title,pv.version latest_version,FALSE has_draft,pv.created_at updated_at FROM regen_protocol_versions pv INNER JOIN (SELECT tenant_id,protocol_id,MAX(version) latest_version FROM regen_protocol_versions WHERE tenant_id=? GROUP BY tenant_id,protocol_id) latest ON latest.tenant_id=pv.tenant_id AND latest.protocol_id=pv.protocol_id AND latest.latest_version=pv.version WHERE NOT EXISTS (SELECT 1 FROM regen_protocol_drafts d WHERE d.tenant_id=pv.tenant_id AND d.protocol_id=pv.protocol_id) ORDER BY updated_at DESC LIMIT ?",
            ).use { s ->
                s.setString(1, tenantId); s.setString(2, tenantId); s.setInt(3, limit)
                s.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                RegenProtocolSummary(
                                    tenantId = rs.getString("tenant_id"), protocolId = rs.getString("protocol_id"), title = rs.getString("title"),
                                    latestVersion = rs.getInt("latest_version"), hasDraft = rs.getBoolean("has_draft"), updatedAtMs = rs.getTimestamp("updated_at").time,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun latestProtocolVersion(tenantId: String, protocolId: String?): RegenProtocolVersion? {
        val withProtocol = !protocolId.isNullOrBlank()
        val sql = if (withProtocol) {
            "SELECT tenant_id,protocol_id,version,title,content_json,published_by,changelog,created_at FROM regen_protocol_versions WHERE tenant_id=? AND protocol_id=? ORDER BY version DESC LIMIT 1"
        } else {
            "SELECT tenant_id,protocol_id,version,title,content_json,published_by,changelog,created_at FROM regen_protocol_versions WHERE tenant_id=? ORDER BY created_at DESC LIMIT 1"
        }
        return dataSource.connection.use { c ->
            c.prepareStatement(sql).use { s ->
                s.setString(1, tenantId); if (withProtocol) s.setString(2, protocolId)
                s.executeQuery().use { rs -> if (!rs.next()) null else rs.toProtocolVersion() }
            }
        }
    }

    override fun requestPublishApproval(
        tenantId: String,
        protocolId: String,
        requestedBy: String,
        reason: String?,
    ): ProtocolPublishApproval {
        val id = java.util.UUID.randomUUID().toString()
        val now = Timestamp.from(Instant.now())
        dataSource.connection.use { c ->
            c.prepareStatement(
                "INSERT INTO protocol_publish_approvals(id,tenant_id,protocol_id,status,requested_by,requested_at,reason) VALUES (?,?,?,?,?,?,?)",
            ).use { s ->
                s.setString(1, id)
                s.setString(2, tenantId)
                s.setString(3, protocolId)
                s.setString(4, "PENDING")
                s.setString(5, requestedBy)
                s.setTimestamp(6, now)
                s.setString(7, reason)
                s.executeUpdate()
            }
        }
        return getPublishApproval(tenantId, id) ?: error("failed to create approval")
    }

    override fun approvePublishApproval(
        tenantId: String,
        approvalId: String,
        approvedBy: String,
        comment: String?,
    ): ProtocolPublishApproval {
        val now = Timestamp.from(Instant.now())
        val rows =
            dataSource.connection.use { c ->
                c.prepareStatement(
                    "UPDATE protocol_publish_approvals SET status='APPROVED',approved_by=?,approved_at=?,approval_comment=? WHERE tenant_id=? AND id=? AND status='PENDING'",
                ).use { s ->
                    s.setString(1, approvedBy)
                    s.setTimestamp(2, now)
                    s.setString(3, comment)
                    s.setString(4, tenantId)
                    s.setString(5, approvalId)
                    s.executeUpdate()
                }
            }
        if (rows == 0) throw BadRequestException("approval_missing", "Approval not found or already processed")
        return getPublishApproval(tenantId, approvalId) ?: error("failed to load approval")
    }

    override fun consumePublishApproval(
        tenantId: String,
        protocolId: String,
        publisherId: String,
    ): ProtocolPublishApproval? {
        return dataSource.inTransaction { c ->
            val approval =
                c.prepareStatement(
                    "SELECT id FROM protocol_publish_approvals WHERE tenant_id=? AND protocol_id=? AND status='APPROVED' AND consumed_by IS NULL AND approved_by <> ? ORDER BY approved_at DESC LIMIT 1",
                ).use { s ->
                    s.setString(1, tenantId)
                    s.setString(2, protocolId)
                    s.setString(3, publisherId)
                    s.executeQuery().use { rs -> if (rs.next()) rs.getString("id") else null }
                } ?: return@inTransaction null

            val rows =
                c.prepareStatement(
                    "UPDATE protocol_publish_approvals SET status='CONSUMED',consumed_by=?,consumed_at=? WHERE tenant_id=? AND id=? AND consumed_by IS NULL",
                ).use { s ->
                    s.setString(1, publisherId)
                    s.setTimestamp(2, Timestamp.from(Instant.now()))
                    s.setString(3, tenantId)
                    s.setString(4, approval)
                    s.executeUpdate()
                }
            if (rows == 0) return@inTransaction null
            getPublishApproval(c, tenantId, approval)
        }
    }

    override fun createRun(tenantId: String, runId: String, protocolId: String, protocolVersion: Int, gatewayId: String?, startedBy: String): RegenRun {
        val now = Timestamp.from(Instant.now())
        try {
            dataSource.connection.use { c ->
                c.prepareStatement("INSERT INTO regen_runs(run_id,tenant_id,protocol_id,protocol_version,status,gateway_id,started_by,abort_reason,started_at,paused_at,aborted_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)").use { s ->
                    s.setString(1, runId); s.setString(2, tenantId); s.setString(3, protocolId); s.setInt(4, protocolVersion)
                    s.setString(5, RegenRunStatus.RUNNING.name); s.setString(6, gatewayId); s.setString(7, startedBy)
                    s.setString(8, null); s.setTimestamp(9, now); s.setTimestamp(10, null); s.setTimestamp(11, null); s.setTimestamp(12, now)
                    s.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            if (e.sqlState?.startsWith("23") == true) throw ConflictException("run_exists", "runId already exists")
            throw e
        }
        return getRun(tenantId, runId) ?: error("failed to create run")
    }

    override fun getRun(tenantId: String, runId: String): RegenRun? {
        return dataSource.connection.use { c ->
            c.prepareStatement("SELECT run_id,tenant_id,protocol_id,protocol_version,status,gateway_id,started_by,abort_reason,started_at,paused_at,aborted_at,updated_at FROM regen_runs WHERE tenant_id=? AND run_id=?").use { s ->
                s.setString(1, tenantId); s.setString(2, runId)
                s.executeQuery().use { rs -> if (!rs.next()) null else rs.toRun() }
            }
        }
    }
    override fun updateRunStatus(tenantId: String, runId: String, status: RegenRunStatus, reason: String?): RegenRun {
        val now = Timestamp.from(Instant.now())
        val rows = dataSource.connection.use { c ->
            c.prepareStatement("UPDATE regen_runs SET status=?,updated_at=?,paused_at=CASE WHEN ?='PAUSED' THEN ? ELSE paused_at END,aborted_at=CASE WHEN ?='ABORTED' THEN ? ELSE aborted_at END,abort_reason=CASE WHEN ?='ABORTED' THEN ? ELSE abort_reason END WHERE tenant_id=? AND run_id=?").use { s ->
                s.setString(1, status.name); s.setTimestamp(2, now)
                s.setString(3, status.name); s.setTimestamp(4, now)
                s.setString(5, status.name); s.setTimestamp(6, now)
                s.setString(7, status.name); s.setString(8, reason)
                s.setString(9, tenantId); s.setString(10, runId)
                s.executeUpdate()
            }
        }
        if (rows == 0) throw BadRequestException("run_missing", "Run does not exist")
        return getRun(tenantId, runId) ?: error("failed to load updated run")
    }

    override fun appendRunEvent(tenantId: String, runId: String, eventType: String, source: String, payloadJson: String, createdAtMs: Long) {
        dataSource.connection.use { c ->
            c.prepareStatement("INSERT INTO regen_run_events(tenant_id,run_id,event_type,source,payload_json,created_at) VALUES (?,?,?,?,?,?)").use { s ->
                s.setString(1, tenantId); s.setString(2, runId); s.setString(3, eventType); s.setString(4, source)
                s.setString(5, canonicalizeJson(payloadJson)); s.setTimestamp(6, Timestamp.from(Instant.ofEpochMilli(createdAtMs)))
                s.executeUpdate()
            }
        }
    }

    override fun listRunEvents(tenantId: String, runId: String, sinceMs: Long, sinceSeq: Long, limit: Int): List<RegenRunEvent> {
        return dataSource.connection.use { c ->
            c.prepareStatement("SELECT id,tenant_id,run_id,event_type,source,payload_json,created_at FROM regen_run_events WHERE tenant_id=? AND run_id=? AND (created_at>? OR (created_at=? AND id>?)) ORDER BY created_at ASC,id ASC LIMIT ?").use { s ->
                val sinceTs = Timestamp.from(Instant.ofEpochMilli(sinceMs))
                s.setString(1, tenantId); s.setString(2, runId); s.setTimestamp(3, sinceTs); s.setTimestamp(4, sinceTs); s.setLong(5, sinceSeq); s.setInt(6, limit)
                s.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                RegenRunEvent(
                                    tenantId = rs.getString("tenant_id"), runId = rs.getString("run_id"), seq = rs.getLong("id"), eventType = rs.getString("event_type"),
                                    source = rs.getString("source"), payloadJson = rs.getString("payload_json"), createdAtMs = rs.getTimestamp("created_at").time,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun appendTelemetry(points: List<RegenTelemetryPoint>) {
        if (points.isEmpty()) return
        dataSource.inTransaction { c ->
            c.prepareStatement("INSERT INTO regen_run_telemetry(tenant_id,run_id,gateway_id,metric_key,metric_value,unit,drift_score,recorded_at) VALUES (?,?,?,?,?,?,?,?)").use { s ->
                points.forEach { p ->
                    s.setString(1, p.tenantId); s.setString(2, p.runId); s.setString(3, p.gatewayId); s.setString(4, p.metricKey)
                    s.setDouble(5, p.metricValue); s.setString(6, p.unit); s.setDouble(7, p.driftScore)
                    s.setTimestamp(8, Timestamp.from(Instant.ofEpochMilli(p.recordedAtMs))); s.addBatch()
                }
                s.executeBatch()
            }
        }
    }

    override fun listTelemetry(tenantId: String, runId: String, sinceMs: Long, sinceSeq: Long, limit: Int): List<RegenTelemetryPoint> {
        return dataSource.connection.use { c ->
            c.prepareStatement("SELECT id,tenant_id,run_id,gateway_id,metric_key,metric_value,unit,drift_score,recorded_at FROM regen_run_telemetry WHERE tenant_id=? AND run_id=? AND (recorded_at>? OR (recorded_at=? AND id>?)) ORDER BY recorded_at ASC,id ASC LIMIT ?").use { s ->
                val sinceTs = Timestamp.from(Instant.ofEpochMilli(sinceMs))
                s.setString(1, tenantId); s.setString(2, runId); s.setTimestamp(3, sinceTs); s.setTimestamp(4, sinceTs); s.setLong(5, sinceSeq); s.setInt(6, limit)
                s.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                RegenTelemetryPoint(
                                    tenantId = rs.getString("tenant_id"), runId = rs.getString("run_id"), seq = rs.getLong("id"), gatewayId = rs.getString("gateway_id"),
                                    metricKey = rs.getString("metric_key"), metricValue = rs.getDouble("metric_value"), unit = rs.getString("unit"),
                                    driftScore = rs.getDouble("drift_score"), recordedAtMs = rs.getTimestamp("recorded_at").time,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun upsertGateway(tenantId: String, gatewayId: String, displayName: String, certificateSerial: String): RegenGateway {
        val now = Timestamp.from(Instant.now())
        val rows = dataSource.connection.use { c ->
            c.prepareStatement("UPDATE regen_gateways SET display_name=?,certificate_serial=?,status='registered',updated_at=? WHERE tenant_id=? AND gateway_id=?").use { s ->
                s.setString(1, displayName); s.setString(2, certificateSerial); s.setTimestamp(3, now)
                s.setString(4, tenantId); s.setString(5, gatewayId); s.executeUpdate()
            }
        }
        if (rows == 0) {
            dataSource.connection.use { c ->
                c.prepareStatement("INSERT INTO regen_gateways(tenant_id,gateway_id,display_name,certificate_serial,status,last_heartbeat_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)").use { s ->
                    s.setString(1, tenantId); s.setString(2, gatewayId); s.setString(3, displayName); s.setString(4, certificateSerial)
                    s.setString(5, "registered"); s.setTimestamp(6, null); s.setTimestamp(7, now); s.setTimestamp(8, now); s.executeUpdate()
                }
            }
        }
        return getGateway(tenantId, gatewayId) ?: error("failed to upsert gateway")
    }

    override fun heartbeatGateway(tenantId: String, gatewayId: String, certificateSerial: String): RegenGateway {
        val now = Timestamp.from(Instant.now())
        val rows = dataSource.connection.use { c ->
            c.prepareStatement("UPDATE regen_gateways SET certificate_serial=?,status='online',last_heartbeat_at=?,updated_at=? WHERE tenant_id=? AND gateway_id=?").use { s ->
                s.setString(1, certificateSerial); s.setTimestamp(2, now); s.setTimestamp(3, now); s.setString(4, tenantId); s.setString(5, gatewayId)
                s.executeUpdate()
            }
        }
        if (rows == 0) throw BadRequestException("gateway_missing", "Gateway is not registered")
        return getGateway(tenantId, gatewayId) ?: error("failed to load gateway after heartbeat")
    }

    override fun getGateway(tenantId: String, gatewayId: String): RegenGateway? {
        return dataSource.connection.use { c ->
            c.prepareStatement("SELECT tenant_id,gateway_id,display_name,certificate_serial,status,last_heartbeat_at,updated_at FROM regen_gateways WHERE tenant_id=? AND gateway_id=?").use { s ->
                s.setString(1, tenantId); s.setString(2, gatewayId)
                s.executeQuery().use { rs ->
                    if (!rs.next()) return null
                    RegenGateway(
                        tenantId = rs.getString("tenant_id"), gatewayId = rs.getString("gateway_id"), displayName = rs.getString("display_name"),
                        certificateSerial = rs.getString("certificate_serial") ?: "", status = rs.getString("status"),
                        lastHeartbeatAtMs = rs.getTimestamp("last_heartbeat_at")?.time, updatedAtMs = rs.getTimestamp("updated_at").time,
                    )
                }
            }
        }
    }

    override fun insertDriftAlert(alert: RegenDriftAlert) {
        dataSource.connection.use { c ->
            c.prepareStatement("INSERT INTO regen_drift_alerts(tenant_id,run_id,severity,metric_key,metric_value,threshold,created_at) VALUES (?,?,?,?,?,?,?)").use { s ->
                s.setString(1, alert.tenantId); s.setString(2, alert.runId); s.setString(3, alert.severity); s.setString(4, alert.metricKey)
                s.setDouble(5, alert.metricValue); s.setDouble(6, alert.threshold); s.setTimestamp(7, Timestamp.from(Instant.ofEpochMilli(alert.createdAtMs))); s.executeUpdate()
            }
        }
    }

    override fun listDriftAlerts(tenantId: String, runId: String, limit: Int): List<RegenDriftAlert> {
        return dataSource.connection.use { c ->
            c.prepareStatement("SELECT tenant_id,run_id,severity,metric_key,metric_value,threshold,created_at FROM regen_drift_alerts WHERE tenant_id=? AND run_id=? ORDER BY created_at DESC,id DESC LIMIT ?").use { s ->
                s.setString(1, tenantId); s.setString(2, runId); s.setInt(3, limit)
                s.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                RegenDriftAlert(
                                    tenantId = rs.getString("tenant_id"), runId = rs.getString("run_id"), severity = rs.getString("severity"),
                                    metricKey = rs.getString("metric_key"), metricValue = rs.getDouble("metric_value"), threshold = rs.getDouble("threshold"),
                                    createdAtMs = rs.getTimestamp("created_at").time,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
    override fun countRunEvents(tenantId: String, runId: String): Int = countByRun("regen_run_events", tenantId, runId)

    override fun countTelemetry(tenantId: String, runId: String): Int = countByRun("regen_run_telemetry", tenantId, runId)

    override fun appendEvidenceEvent(event: RegenEvidenceEvent) {
        val canonicalPayload = canonicalizeJson(event.payloadJson)
        val payloadHash = sha256(canonicalPayload)
        dataSource.inTransaction { c ->
            val prevHash = lastEvidenceHash(c, event.tenantId)
            val eventHash = sha256("${event.tenantId}|${prevHash.orEmpty()}|$payloadHash|${event.actionType}|${event.actorId}|${event.resourceType}|${event.resourceId}|${event.createdAtMs}")
            c.prepareStatement("INSERT INTO regen_evidence_events(tenant_id,action_type,actor_id,resource_type,resource_id,payload_json,payload_hash,prev_hash,event_hash,created_at) VALUES (?,?,?,?,?,?,?,?,?,?)").use { s ->
                s.setString(1, event.tenantId); s.setString(2, event.actionType); s.setString(3, event.actorId)
                s.setString(4, event.resourceType); s.setString(5, event.resourceId); s.setString(6, canonicalPayload)
                s.setString(7, payloadHash); s.setString(8, prevHash); s.setString(9, eventHash)
                s.setTimestamp(10, Timestamp.from(Instant.ofEpochMilli(event.createdAtMs))); s.executeUpdate()
            }
        }
    }

    override fun verifyEvidenceChain(tenantId: String, limit: Int): RegenEvidenceChainStatus {
        return dataSource.connection.use { c ->
            c.prepareStatement("SELECT tenant_id,action_type,actor_id,resource_type,resource_id,payload_json,payload_hash,prev_hash,event_hash,created_at FROM regen_evidence_events WHERE tenant_id=? ORDER BY id ASC LIMIT ?").use { s ->
                s.setString(1, tenantId); s.setInt(2, limit)
                s.executeQuery().use { rs ->
                    var checked = 0
                    var expectedPrev: String? = null
                    while (rs.next()) {
                        checked += 1
                        val payloadHash = rs.getString("payload_hash")
                        val prevHash = rs.getString("prev_hash")
                        val createdAtMs = rs.getTimestamp("created_at").time
                        if (prevHash != expectedPrev) {
                            return RegenEvidenceChainStatus(false, checked, checked, "previous hash mismatch")
                        }
                        if (sha256(canonicalizeJson(rs.getString("payload_json"))) != payloadHash) {
                            return RegenEvidenceChainStatus(false, checked, checked, "payload hash mismatch")
                        }
                        val recomputed = sha256("${rs.getString("tenant_id")}|${prevHash.orEmpty()}|$payloadHash|${rs.getString("action_type")}|${rs.getString("actor_id")}|${rs.getString("resource_type")}|${rs.getString("resource_id")}|$createdAtMs")
                        if (recomputed != rs.getString("event_hash")) {
                            return RegenEvidenceChainStatus(false, checked, checked, "event hash mismatch")
                        }
                        expectedPrev = rs.getString("event_hash")
                    }
                    RegenEvidenceChainStatus(true, checked, null, null)
                }
            }
        }
    }

    private fun countByRun(table: String, tenantId: String, runId: String): Int {
        return dataSource.connection.use { c ->
            c.prepareStatement("SELECT COUNT(*) total FROM $table WHERE tenant_id=? AND run_id=?").use { s ->
                s.setString(1, tenantId); s.setString(2, runId)
                s.executeQuery().use { rs -> rs.next(); rs.getInt("total") }
            }
        }
    }

    private fun lastEvidenceHash(c: Connection, tenantId: String): String? {
        return c.prepareStatement("SELECT event_hash FROM regen_evidence_events WHERE tenant_id=? ORDER BY id DESC LIMIT 1").use { s ->
            s.setString(1, tenantId)
            s.executeQuery().use { rs -> if (rs.next()) rs.getString("event_hash") else null }
        }
    }

    private fun getPublishApproval(
        tenantId: String,
        approvalId: String,
    ): ProtocolPublishApproval? {
        return dataSource.connection.use { c ->
            getPublishApproval(c, tenantId, approvalId)
        }
    }

    private fun getPublishApproval(
        connection: Connection,
        tenantId: String,
        approvalId: String,
    ): ProtocolPublishApproval? {
        return connection.prepareStatement(
            "SELECT id,tenant_id,protocol_id,status,requested_by,requested_at,reason,approved_by,approved_at,approval_comment,consumed_by,consumed_at FROM protocol_publish_approvals WHERE tenant_id=? AND id=?",
        ).use { s ->
            s.setString(1, tenantId)
            s.setString(2, approvalId)
            s.executeQuery().use { rs ->
                if (!rs.next()) return null
                ProtocolPublishApproval(
                    id = rs.getString("id"),
                    tenantId = rs.getString("tenant_id"),
                    protocolId = rs.getString("protocol_id"),
                    status = rs.getString("status"),
                    requestedBy = rs.getString("requested_by"),
                    requestedAtMs = rs.getTimestamp("requested_at").time,
                    reason = rs.getString("reason"),
                    approvedBy = rs.getString("approved_by"),
                    approvedAtMs = rs.getTimestamp("approved_at")?.time,
                    approvalComment = rs.getString("approval_comment"),
                    consumedBy = rs.getString("consumed_by"),
                    consumedAtMs = rs.getTimestamp("consumed_at")?.time,
                )
            }
        }
    }

    private fun java.sql.ResultSet.toProtocolVersion(): RegenProtocolVersion {
        return RegenProtocolVersion(
            tenantId = getString("tenant_id"), protocolId = getString("protocol_id"), version = getInt("version"), title = getString("title"),
            contentJson = getString("content_json"), publishedBy = getString("published_by"), changelog = getString("changelog"), createdAtMs = getTimestamp("created_at").time,
        )
    }

    private fun java.sql.ResultSet.toRun(): RegenRun {
        return RegenRun(
            tenantId = getString("tenant_id"), runId = getString("run_id"), protocolId = getString("protocol_id"), protocolVersion = getInt("protocol_version"),
            status = RegenRunStatus.valueOf(getString("status")), gatewayId = getString("gateway_id"), startedBy = getString("started_by"),
            startedAtMs = getTimestamp("started_at").time, updatedAtMs = getTimestamp("updated_at").time, pausedAtMs = getTimestamp("paused_at")?.time,
            abortedAtMs = getTimestamp("aborted_at")?.time, abortReason = getString("abort_reason"),
        )
    }
}

private fun canonicalizeJson(value: String): String {
    val element = runCatching { Json.parseToJsonElement(value) }.getOrElse { return value.trim() }
    return canonicalizeJsonElement(element)
}

private fun canonicalizeJsonElement(element: JsonElement): String {
    return when (element) {
        is JsonObject -> {
            element.entries.sortedBy { it.key }.joinToString(prefix = "{", postfix = "}") { (key, child) ->
                "${Json.encodeToString(String.serializer(), key)}:${canonicalizeJsonElement(child)}"
            }
        }
        is JsonArray -> element.joinToString(prefix = "[", postfix = "]") { canonicalizeJsonElement(it) }
        is JsonPrimitive -> if (element.isString) Json.encodeToString(String.serializer(), element.content) else element.content
    }
}

private fun sha256(value: String): String {
    val hash = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return hash.joinToString("") { "%02x".format(it) }
}

private fun <T> DataSource.inTransaction(block: (Connection) -> T): T {
    connection.use { c ->
        val previous = c.autoCommit
        c.autoCommit = false
        try {
            val value = block(c)
            c.commit()
            return value
        } catch (e: Throwable) {
            c.rollback()
            throw e
        } finally {
            c.autoCommit = previous
        }
    }
}
