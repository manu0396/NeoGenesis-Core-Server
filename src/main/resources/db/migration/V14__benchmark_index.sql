CREATE TABLE IF NOT EXISTS benchmark_opt_in (
    tenant_id VARCHAR(128) PRIMARY KEY,
    opted_in BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS benchmark_aggregates (
    protocol_type VARCHAR(128) NOT NULL,
    instrument_type VARCHAR(128) NOT NULL,
    metric_key VARCHAR(128) NOT NULL,
    sample_count INTEGER NOT NULL,
    mean_value DOUBLE PRECISION NOT NULL,
    stddev_value DOUBLE PRECISION NOT NULL,
    drift_rate DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (protocol_type, instrument_type, metric_key)
);
