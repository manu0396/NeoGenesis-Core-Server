package com.neogenesis.server.application.compliance

import com.neogenesis.server.application.regenops.ProtocolPublishApproval
import org.slf4j.LoggerFactory

interface ComplianceHooks {
    fun onApprovalRequested(approval: ProtocolPublishApproval)

    fun onApprovalApproved(approval: ProtocolPublishApproval)

    fun onApprovalConsumed(approval: ProtocolPublishApproval)

    companion object {
        fun noop(): ComplianceHooks = NoopComplianceHooks()
    }
}

private class NoopComplianceHooks : ComplianceHooks {
    override fun onApprovalRequested(approval: ProtocolPublishApproval) = Unit

    override fun onApprovalApproved(approval: ProtocolPublishApproval) = Unit

    override fun onApprovalConsumed(approval: ProtocolPublishApproval) = Unit
}

class LoggingComplianceHooks(
    private val esignEnabled: Boolean,
    private val scimEnabled: Boolean,
    private val samlEnabled: Boolean,
) : ComplianceHooks {
    private val logger = LoggerFactory.getLogger(LoggingComplianceHooks::class.java)

    override fun onApprovalRequested(approval: ProtocolPublishApproval) {
        if (esignEnabled) {
            logger.info("compliance.esign.requested approvalId={}", approval.id)
        }
    }

    override fun onApprovalApproved(approval: ProtocolPublishApproval) {
        if (esignEnabled) {
            logger.info("compliance.esign.approved approvalId={}", approval.id)
        }
        if (scimEnabled) {
            logger.info("compliance.scim.sync approvalId={}", approval.id)
        }
        if (samlEnabled) {
            logger.info("compliance.saml.audit approvalId={}", approval.id)
        }
    }

    override fun onApprovalConsumed(approval: ProtocolPublishApproval) {
        logger.info("compliance.approval.consumed approvalId={}", approval.id)
    }
}
