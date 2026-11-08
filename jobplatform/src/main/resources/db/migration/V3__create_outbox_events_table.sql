-- V3: Transactional outbox table (see ADR-0002)
-- Written in the SAME DB transaction as the job it describes. A separate poller
-- process reads unpublished rows and dispatches them, decoupling "job was created"
-- from "job was successfully queued for processing".

CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    UUID NOT NULL, -- = jobs.id, not a FK on purpose (see note below)
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    published       BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

-- The poller's main query: "give me all unpublished rows, oldest first"
-- Partial index — only indexes rows where published = false, which is the
-- small, hot subset this query actually needs. Keeps the index small even
-- as the table grows with published history.
CREATE INDEX idx_outbox_unpublished ON outbox_events(created_at) WHERE published = false;

COMMENT ON TABLE outbox_events IS 'Transactional outbox. Written in the same transaction as the job row it describes; polled and dispatched separately. See ADR-0002.';
COMMENT ON COLUMN outbox_events.aggregate_id IS 'References jobs.id logically, not via FK — the outbox is intentionally decoupled from job lifecycle (e.g. a job could theoretically be purged while outbox history is retained for audit).';
