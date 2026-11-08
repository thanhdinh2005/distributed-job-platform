-- V2: Job lifecycle audit trail
-- Every status transition a job goes through is recorded here, independent of
-- the current state stored on the `jobs` row. Append-only: rows are never updated.

CREATE TABLE job_events (
    id              BIGSERIAL PRIMARY KEY,
    job_id          UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    from_status     VARCHAR(30),
    to_status       VARCHAR(30) NOT NULL,
    message         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Queried whenever someone asks "what happened to job X" (GET /jobs/{id}/events)
CREATE INDEX idx_job_events_job_id ON job_events(job_id, created_at);

COMMENT ON TABLE job_events IS 'Append-only audit trail of job status transitions. Never updated, only inserted.';
