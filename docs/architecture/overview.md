# Architecture Overview

What the system looks like **now**. This file describes; it does not decide (that is `docs/adr/`)
and it does not instruct (that is `AGENTS.md`). If this file and `AGENTS.md` disagree, `AGENTS.md`
wins — and the disagreement is a bug to report.

## Status

v1. Schema and application skeleton exist; **no feature code is implemented yet**. Everything under
"Job lifecycle" and "Write path" below is the intended design, not a description of working
software.

## System Shape

One Spring Boot application (`jobplatform/`), a modular monolith (see
[ADR-0001](../adr/0001-modular-monolith.md)). In v1 there is **no message broker**: retry, queueing,
and dispatch are in-process. RabbitMQ arrives in v2, which is also when the module boundaries get
tested as if they were real boundaries.

| Package | Responsibility | State |
|---|---|---|
| `api/` | HTTP boundary: submit a job, read a job, list its event history, cancel it. No business logic. | not implemented |
| `domain/` | `Job`, `JobEvent`, the status machine, and `JobStatusUpdater` — the only place status changes. | not implemented |
| `worker/` | Handler registry (one strategy per `job_type`), execution, idempotency guarantees. | not implemented |
| `outbox/` | Poller that turns committed outbox rows into dispatched work. | not implemented |
| `config/` | Persistence, scheduler, Actuator configuration. | not implemented |

## Data Model

Defined by the Flyway migrations in `jobplatform/src/main/resources/db/migration/`.

- **`jobs`** — one row per job, regardless of type. `job_type` is a free-form string; `payload` is
  JSONB validated at the application layer; `status` is constrained by `chk_jobs_status`;
  `attempt_count` / `max_attempts` drive retry; `priority` exists for ordering;
  `created_at`, `updated_at`, `queued_at`, `started_at`, `completed_at` are the timeline.
- **`job_events`** — append-only audit trail, one row per transition (`from_status`, `to_status`,
  `message`, `created_at`). Never updated. Answers "what happened to this job".
- **`outbox_events`** — unpublished rows that describe a state change to be dispatched. Written in
  the same transaction as the `jobs` row it describes. `aggregate_id` references `jobs.id`
  logically, deliberately not by foreign key.

Statuses (`chk_jobs_status`): `CREATED`, `QUEUED`, `PROCESSING`, `SUCCEEDED`, `FAILED_RETRYABLE`,
`DEAD_LETTER`, `CANCELLED`. Terminal: `SUCCEEDED`, `DEAD_LETTER`, `CANCELLED`.

## Job Lifecycle (intended)

```
CREATED ──► QUEUED ──► PROCESSING ──► SUCCEEDED
              ▲            │
              │            ├──► FAILED_RETRYABLE ──(attempts left)──► QUEUED
              │            │                      └─(attempts exhausted)─► DEAD_LETTER
              │
CANCELLED ◄───┴── (from any non-terminal state)
```

Rules the design intends:
- Every transition writes a `job_events` row in the same transaction as the `jobs` update.
- Only `JobStatusUpdater` mutates status; the transition table lives in one class.
- Retry in v1 is a scheduled scan of `FAILED_RETRYABLE` rows whose backoff has elapsed.

The exact transition table is **not decided yet** — it is the first thing the first task should
pin down, and it belongs in code with a test per edge, not only in prose here.

## Write Path (intended, v1)

1. `POST /jobs` inserts `jobs` (`CREATED`), a `job_events` row, and an `outbox_events` row in one
   transaction. Nothing is dispatched inside that transaction.
2. The outbox poller selects unpublished rows, dispatches them, and marks them published.
3. The worker claims work, moves the job to `PROCESSING`, runs the handler for its `job_type`, then
   moves it to a terminal or retryable state — always through `JobStatusUpdater`.
4. A scheduler re-queues `FAILED_RETRYABLE` jobs once their backoff has elapsed, or moves them to
   `DEAD_LETTER` when `attempt_count` reaches `max_attempts`.

## Open Questions

Undecided, and deliberately not decided here:

1. **What does the v1 outbox poller dispatch *to*, with no broker?** In-process Spring event, a
   direct call into the worker, or nothing at all (the worker polls `jobs` directly and the outbox
   is proven only in v2). This decides whether the outbox is a real v1 component or scaffolding.
2. **Local PostgreSQL.** No compose file exists. Does v1 run against a container, a local install,
   or only Testcontainers?
3. **Auth.** `.env` suggests JWT and a separate frontend origin; nothing else in the docs does.
   In scope or leftover boilerplate?
4. **Claiming work.** Single-threaded poller first, or `SELECT ... FOR UPDATE SKIP LOCKED` from the
   start? The first is simpler to understand; the second is the realistic version and is the whole
   point of a durable queue.
5. **`priority`** exists in the schema. Is it actually honoured in v1 ordering, or is it a column
   waiting for a feature?
