ALTER TABLE integration_outbox
    ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMP;

ALTER TABLE integration_outbox
    DROP CONSTRAINT IF EXISTS chk_integration_outbox_status;

ALTER TABLE integration_outbox
    ADD CONSTRAINT chk_integration_outbox_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED'));

UPDATE integration_outbox
SET
    status = 'PENDING',
    processing_started_at = NULL
WHERE status = 'PROCESSING';

CREATE INDEX IF NOT EXISTS idx_outbox_processing_started
    ON integration_outbox(status, processing_started_at);
