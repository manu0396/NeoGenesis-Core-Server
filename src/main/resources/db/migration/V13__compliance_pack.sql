CREATE TABLE IF NOT EXISTS protocol_publish_approvals (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    protocol_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_by VARCHAR(128) NOT NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason TEXT,
    approved_by VARCHAR(128),
    approved_at TIMESTAMP,
    approval_comment TEXT,
    consumed_by VARCHAR(128),
    consumed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_protocol_publish_approvals_lookup
    ON protocol_publish_approvals(tenant_id, protocol_id, status, approved_at DESC);
