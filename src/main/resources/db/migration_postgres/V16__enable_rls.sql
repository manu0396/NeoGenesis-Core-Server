-- V16__enable_rls.sql
-- Enable Row Level Security (RLS) on all tenant-aware tables
-- This script is only executed on PostgreSQL

DO $$
DECLARE
    t TEXT;
BEGIN
    FOR t IN SELECT table_name FROM information_schema.columns 
             WHERE column_name = 'tenant_id' 
               AND table_schema = 'public'
               AND table_name NOT IN ('users', 'admin_tenants', 'roles')
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        
        -- Drop if exists (to be idempotent)
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation_policy ON %I', t);
        
        -- Create the policy: rows are visible only if tenant_id matches the session variable
        EXECUTE format('CREATE POLICY tenant_isolation_policy ON %I USING (tenant_id = current_setting(''app.current_tenant'', true))', t);
    END LOOP;
END $$;
