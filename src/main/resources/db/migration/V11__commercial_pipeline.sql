CREATE TABLE IF NOT EXISTS commercial_accounts (
    id UUID PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    name TEXT NOT NULL,
    country TEXT,
    industry TEXT,
    website TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS commercial_contacts (
    id UUID PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    account_id UUID NOT NULL REFERENCES commercial_accounts(id) ON DELETE CASCADE,
    full_name TEXT NOT NULL,
    email TEXT,
    role TEXT,
    phone TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS commercial_opportunities (
    id UUID PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    account_id UUID NOT NULL REFERENCES commercial_accounts(id) ON DELETE CASCADE,
    stage TEXT NOT NULL,
    expected_value_eur BIGINT NOT NULL DEFAULT 0,
    probability DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    close_date DATE,
    owner TEXT NOT NULL,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS commercial_lois (
    id UUID PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    opportunity_id UUID NOT NULL REFERENCES commercial_opportunities(id) ON DELETE CASCADE,
    signed_date DATE,
    amount_range TEXT,
    attachment_ref TEXT,
    status TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS commercial_activity_log (
    id UUID PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    actor_id TEXT NOT NULL,
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id UUID NOT NULL,
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_commercial_accounts_tenant ON commercial_accounts(tenant_id);
CREATE INDEX IF NOT EXISTS idx_commercial_contacts_tenant ON commercial_contacts(tenant_id);
CREATE INDEX IF NOT EXISTS idx_commercial_opportunities_tenant ON commercial_opportunities(tenant_id);
CREATE INDEX IF NOT EXISTS idx_commercial_lois_tenant ON commercial_lois(tenant_id);
CREATE INDEX IF NOT EXISTS idx_commercial_activity_tenant ON commercial_activity_log(tenant_id);
CREATE INDEX IF NOT EXISTS idx_commercial_opportunities_stage ON commercial_opportunities(stage);
