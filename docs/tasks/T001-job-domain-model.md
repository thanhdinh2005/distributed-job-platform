# T001 — Job domain model and the status transition funnel

Status: **Draft — needs my decisions before it is approved.** Nothing in this brief has been
executed. The proposed transition table below is a proposal, not a decision.

Depends on: nothing (schema V1–V3 already exists)

## Goal

A `Job` can be loaded, moved between legal statuses through exactly one entry point, and retain an
append-only history of every change — with an illegal transition rejected and leaving no trace.

## Context

- `jobplatform/` boots; `JobplatformApplication` is the only Java class.
- Migrations V1–V3 define `jobs`, `job_events`, `outbox_events`. Read them before mapping.
- `api/`, `domain/`, `worker/`, `outbox/`, `config/` do not exist yet.
- Read `AGENTS.md` §6 (package rule) and §7 (status funnel, failure-path tests), and
  `docs/architecture/overview.md` (data model, intended lifecycle).

## In scope

- `com.backend.jobplatform.domain` package:
  - `JobStatus` enum — the seven values constrained by `chk_jobs_status`.
  - `Job` JPA entity mapped to `jobs` (`job_type` stays a `String`, `payload` is JSONB, timestamps
    per migration).
  - `JobEvent` JPA entity mapped to `job_events` (append-only; no setters that mutate an existing row).
  - `JobRepository`, `JobEventRepository` (Spring Data JPA).
  - `JobStatusUpdater` — the only class allowed to change `Job.status`. It validates the transition,
    updates the row, sets the relevant timestamp, and writes exactly one `JobEvent`, all in one
    transaction.
- A transition table, expressed in one place, with one test per edge.
- Tests: every legal edge; every illegal edge tried at least once per source state; and a test
  proving a rejected transition writes neither a `jobs` change nor a `job_events` row.

## Out of scope

- No controller, no DTOs, no HTTP (that is T002).
- No worker, no handler registry, no `@Scheduled`.
- No outbox writer or poller — `outbox_events` is not touched in this task.
- No retry/backoff logic beyond representing `FAILED_RETRYABLE`.
- No new dependencies. No changes to the migrations (if a column is wrong, stop and say so).
- No security, no CORS, no auth.

## Constraints

- Status changes only through `JobStatusUpdater` (`AGENTS.md` §7) — no `job.setStatus(...)` anywhere
  else, including in tests that would "just set up state"; use the updater to reach a state, or a
  test builder that is explicit about it.
- Constructor injection only (`AGENTS.md` §7).
- Adding or renaming a status is a migration, not a Java change — out of scope here.
- `job_type` is a plain `String`; do not introduce a `JobType` enum (`AGENTS.md` §7).

## Acceptance criteria

- [ ] A newly persisted job is `CREATED` and has exactly one `job_events` row.
- [ ] Each legal transition succeeds, sets `status`, and appends exactly one event with the correct
      `from_status` / `to_status`.
- [ ] Each illegal transition throws a domain exception (name it, don't reuse a generic
      `IllegalArgumentException`), and leaves both tables unchanged.
- [ ] Terminal states (`SUCCEEDED`, `DEAD_LETTER`, `CANCELLED`) accept no further transition.
- [ ] Timestamps: `queued_at` set on entering `QUEUED`, `started_at` on `PROCESSING`,
      `completed_at` on any terminal state. (Confirm in open questions.)
- [ ] The transition table is a single source of truth in code — no duplicated `if` chains.
- [ ] `./mvnw -f jobplatform/pom.xml test` passes.

## Verification

```
.\jobplatform\mvnw.cmd -f jobplatform\pom.xml test      # Windows, from repo root
./jobplatform/mvnw -f jobplatform/pom.xml test          # Unix
```

Expected: green, including the new domain tests. If Testcontainers cannot reach a Docker daemon,
say so instead of switching the persistence tests to H2 — the schema uses PostgreSQL-specific types
(JSONB, `TIMESTAMPTZ`, `gen_random_uuid()`), so H2 would verify a different database.

## Proposed transition table (needs approval)

```
CREATED           → QUEUED, CANCELLED
QUEUED            → PROCESSING, CANCELLED
PROCESSING        → SUCCEEDED, FAILED_RETRYABLE, DEAD_LETTER
FAILED_RETRYABLE  → QUEUED, DEAD_LETTER, CANCELLED
SUCCEEDED         → (terminal)
DEAD_LETTER       → (terminal)
CANCELLED         → (terminal)
```

Two deliberate omissions to confirm or overrule:

- `PROCESSING → CANCELLED` is **not** allowed here. Cancelling mid-execution is a coordination
  problem (the handler may already be running), not a status update. If you want it in v1, it needs
  a story for what happens to in-flight work.
- `PROCESSING → QUEUED` is not allowed. Going back to the queue is `FAILED_RETRYABLE → QUEUED`, so
  that every retry is recorded as a failure first.

## Open questions (answer before starting; do not pick silently)

1. **Where does the transition table live?** Proposal: `JobStatus` exposes `canTransitionTo(...)`
   (pure, trivially unit-testable, no Spring), and `JobStatusUpdater` owns the persistence and the
   event write. Alternative: the table lives entirely inside the updater. The first keeps the state
   machine independent of the transaction.
2. **Does `JobStatusUpdater` write the `JobEvent` itself?** Proposal: yes — one funnel means one
   transaction, and a separate "recorder" invites someone to skip it. The cost is that the updater
   has two repositories; that is acceptable here.
3. **Optimistic locking now or later?** Proposal: later. But the updater should take a job **id**
   and re-load inside the transaction rather than trust a detached entity from a controller.
4. **Who sets the timestamps?** Proposal: the updater, since it knows which transition happened.
   Alternative: a DB trigger, which keeps it true even for manual SQL, but hides it from Java.
5. **`@Enumerated(EnumType.STRING)` for status?** Proposal: yes, storing the same strings the CHECK
   constraint allows.
6. **Testcontainers**: is Docker running on the machine where this will be executed, and should
   T001 include persistence tests or only pure unit tests on the transition table? Proposal: both —
   the append-only guarantee is a persistence property.

## Notes / decisions during execution

(empty — filled in while working)
