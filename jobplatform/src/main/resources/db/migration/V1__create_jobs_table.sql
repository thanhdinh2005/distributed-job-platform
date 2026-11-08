-- V1: Core jobs table
-- The central entity of the platform. One row per job, regardless of job_type.

CREATE EXTENSION IF NOT EXISTS pgcrypto; -- needed for gen_random_uuid()

CREATE TABLE jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_type        VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    priority        INT NOT NULL DEFAULT 0,
    attempt_count   INT NOT NULL DEFAULT 0,
    max_attempts    INT NOT NULL DEFAULT 3,
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    queued_at       TIMESTAMPTZ,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,

    CONSTRAINT chk_jobs_status CHECK (status IN (
        'CREATED', 'QUEUED', 'PROCESSING', 'SUCCEEDED',
        'FAILED_RETRYABLE', 'DEAD_LETTER', 'CANCELLED'
    )),
    CONSTRAINT chk_jobs_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT chk_jobs_max_attempts CHECK (max_attempts >= 1)
);

-- Queried constantly by the retry scheduler and status dashboards
CREATE INDEX idx_jobs_status ON jobs(status);

-- Queried by job type when debugging a specific handler's behavior
CREATE INDEX idx_jobs_job_type ON jobs(job_type);

COMMENT ON TABLE jobs IS 'Core job records. One row per job regardless of job_type.';
COMMENT ON COLUMN jobs.payload IS 'Arbitrary JSON payload, shape depends on job_type. Validated at the application layer, not the DB layer.';
