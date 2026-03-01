ALTER TABLE print_sessions
    ADD CONSTRAINT fk_print_sessions_plan
        FOREIGN KEY (plan_id) REFERENCES retinal_print_plans(plan_id);

ALTER TABLE print_sessions
    ADD CONSTRAINT chk_print_sessions_status
        CHECK (status IN ('CREATED', 'ACTIVE', 'PAUSED', 'COMPLETED', 'ABORTED'));

ALTER TABLE integration_outbox
    ADD CONSTRAINT chk_integration_outbox_status
        CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'));

ALTER TABLE telemetry_events
    ADD CONSTRAINT chk_telemetry_cell_viability
        CHECK (cell_viability_index >= 0.0 AND cell_viability_index <= 1.0);

ALTER TABLE telemetry_events
    ADD CONSTRAINT chk_telemetry_morph_defect_probability
        CHECK (morphological_defect_probability >= 0.0 AND morphological_defect_probability <= 1.0);

CREATE INDEX IF NOT EXISTS idx_clinical_documents_type_created
    ON clinical_documents(document_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_print_sessions_plan
    ON print_sessions(plan_id, updated_at DESC);
