-- V15__enforce_multitenancy.sql
-- Enable Row Level Security (RLS) and ensure tenant_id is on all tables

-- 1. Add tenant_id to core tables if missing
ALTER TABLE telemetry_events ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(128);
ALTER TABLE control_commands ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(128);
ALTER TABLE digital_twin_snapshots ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(128);
ALTER TABLE clinical_documents ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(128);
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(128);
ALTER TABLE print_sessions ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(128);
ALTER TABLE print_jobs ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(128);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(128);
ALTER TABLE retinal_print_plans ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(128);
ALTER TABLE latency_budget_breaches ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(128);
ALTER TABLE capa_records ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(128);
ALTER TABLE risk_register ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(128);
ALTER TABLE dhf_artifacts ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(128);
ALTER TABLE gdpr_consents ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(128);
ALTER TABLE gdpr_erasure_requests ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(128);

-- 2. Populate default tenant_id for existing data
UPDATE telemetry_events SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE control_commands SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE digital_twin_snapshots SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE clinical_documents SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE audit_events SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE print_sessions SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE print_jobs SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE devices SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE retinal_print_plans SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE latency_budget_breaches SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE capa_records SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE risk_register SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE dhf_artifacts SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE gdpr_consents SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE gdpr_erasure_requests SET tenant_id = 'default' WHERE tenant_id IS NULL;

-- 3. Make tenant_id NOT NULL and add indexes
ALTER TABLE telemetry_events ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE control_commands ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE digital_twin_snapshots ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE clinical_documents ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE audit_events ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE print_sessions ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE print_jobs ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE devices ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE retinal_print_plans ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE latency_budget_breaches ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE capa_records ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE risk_register ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE dhf_artifacts ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE gdpr_consents ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE gdpr_erasure_requests ALTER COLUMN tenant_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_telemetry_events_tenant ON telemetry_events(tenant_id);
CREATE INDEX IF NOT EXISTS idx_control_commands_tenant ON control_commands(tenant_id);
CREATE INDEX IF NOT EXISTS idx_digital_twin_snapshots_tenant ON digital_twin_snapshots(tenant_id);
CREATE INDEX IF NOT EXISTS idx_clinical_documents_tenant ON clinical_documents(tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_tenant ON audit_events(tenant_id);
CREATE INDEX IF NOT EXISTS idx_print_sessions_tenant ON print_sessions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_print_jobs_tenant ON print_jobs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_devices_tenant ON devices(tenant_id);
CREATE INDEX IF NOT EXISTS idx_retinal_print_plans_tenant ON retinal_print_plans(tenant_id);
CREATE INDEX IF NOT EXISTS idx_latency_breaches_tenant ON latency_budget_breaches(tenant_id);
CREATE INDEX IF NOT EXISTS idx_capa_records_tenant ON capa_records(tenant_id);
CREATE INDEX IF NOT EXISTS idx_risk_register_tenant ON risk_register(tenant_id);
CREATE INDEX IF NOT EXISTS idx_dhf_artifacts_tenant ON dhf_artifacts(tenant_id);
CREATE INDEX IF NOT EXISTS idx_gdpr_consents_tenant ON gdpr_consents(tenant_id);
CREATE INDEX IF NOT EXISTS idx_gdpr_erasure_requests_tenant ON gdpr_erasure_requests(tenant_id);
