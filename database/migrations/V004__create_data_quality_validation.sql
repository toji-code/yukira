-- V004__create_data_quality_validation.sql
-- YUKIRA MVP
-- Data ingestion, validation, quality and quarantine metadata


CREATE TABLE ingestion_run (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL,
    dataset_type VARCHAR(100) NOT NULL,
    source_reference TEXT,
    source_version VARCHAR(255),
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'RUNNING',
    records_received BIGINT NOT NULL DEFAULT 0,
    records_accepted BIGINT NOT NULL DEFAULT 0,
    records_rejected BIGINT NOT NULL DEFAULT 0,
    records_quarantined BIGINT NOT NULL DEFAULT 0,
    error_summary TEXT,
    application_version VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ingestion_run_source
        FOREIGN KEY (source_id)
        REFERENCES data_source(id),

    CONSTRAINT chk_ingestion_run_status
        CHECK (
            status IN (
                'RUNNING',
                'COMPLETED',
                'FAILED',
                'PARTIAL'
            )
        ),

    CONSTRAINT chk_ingestion_run_counts
        CHECK (
            records_received >= 0
            AND records_accepted >= 0
            AND records_rejected >= 0
            AND records_quarantined >= 0
        ),

    CONSTRAINT chk_ingestion_run_completed
        CHECK (
            completed_at IS NULL
            OR completed_at >= started_at
        )
);


CREATE TABLE validation_check (
    id BIGSERIAL PRIMARY KEY,
    check_code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    dataset_type VARCHAR(100) NOT NULL,
    check_type VARCHAR(50) NOT NULL,
    severity VARCHAR(30) NOT NULL DEFAULT 'ERROR',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    methodology_version VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_validation_check_code
        UNIQUE (check_code),

    CONSTRAINT chk_validation_check_type
        CHECK (
            check_type IN (
                'SCHEMA',
                'REQUIRED_FIELD',
                'TYPE',
                'RANGE',
                'DUPLICATE',
                'REFERENTIAL_INTEGRITY',
                'DATE_CONSISTENCY',
                'HISTORICAL_CONTINUITY',
                'CROSS_SOURCE_RECONCILIATION',
                'FRESHNESS'
            )
        ),

    CONSTRAINT chk_validation_check_severity
        CHECK (
            severity IN (
                'INFO',
                'WARNING',
                'ERROR',
                'CRITICAL'
            )
        )
);


CREATE TABLE validation_issue (
    id BIGSERIAL PRIMARY KEY,
    ingestion_run_id BIGINT NOT NULL,
    validation_check_id BIGINT NOT NULL,
    dataset_type VARCHAR(100) NOT NULL,
    record_reference VARCHAR(255),
    issue_status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    severity VARCHAR(30) NOT NULL,
    message TEXT NOT NULL,
    observed_value TEXT,
    expected_value TEXT,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMPTZ,
    resolution_note TEXT,

    CONSTRAINT fk_validation_issue_ingestion
        FOREIGN KEY (ingestion_run_id)
        REFERENCES ingestion_run(id),

    CONSTRAINT fk_validation_issue_check
        FOREIGN KEY (validation_check_id)
        REFERENCES validation_check(id),

    CONSTRAINT chk_validation_issue_status
        CHECK (
            issue_status IN (
                'OPEN',
                'INVESTIGATING',
                'RESOLVED',
                'IGNORED',
                'QUARANTINED',
                'REJECTED'
            )
        ),

    CONSTRAINT chk_validation_issue_severity
        CHECK (
            severity IN (
                'INFO',
                'WARNING',
                'ERROR',
                'CRITICAL'
            )
        ),

    CONSTRAINT chk_validation_issue_resolution
        CHECK (
            resolved_at IS NULL
            OR resolved_at >= detected_at
        )
);


CREATE INDEX idx_ingestion_run_source
    ON ingestion_run (source_id);

CREATE INDEX idx_ingestion_run_dataset
    ON ingestion_run (dataset_type, started_at);

CREATE INDEX idx_validation_issue_ingestion
    ON validation_issue (ingestion_run_id);

CREATE INDEX idx_validation_issue_check
    ON validation_issue (validation_check_id);

CREATE INDEX idx_validation_issue_status
    ON validation_issue (issue_status, severity);

CREATE INDEX idx_validation_issue_dataset
    ON validation_issue (dataset_type, record_reference);