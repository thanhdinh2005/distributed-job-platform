# AGENTS.md

This file instructs AI coding agents (currently: DeepSeek harness) working on this repository.
Humans should read `README.md`. Agents read this file first, every session, before touching code.

Sections are numbered and stable. Cite a section ("see §7") instead of restating a rule.

## 1. Project Context

**What this is**: a personal learning project — a Distributed Job Processing Platform.
Not a production system. Optimize for *understanding*, not for shipping fast or scaling big.

**Current stage** (update this block whenever it changes — it is the first thing an agent uses to
judge scope):
- v1, mid-implementation. Single Spring Boot module in `jobplatform/`.
- Implemented: the job state machine, the REST API (create / get / events / cancel), the
  `CREATED → QUEUED` enqueuer, and the claim → handle → settle worker loop (happy path only).
- Not implemented: retry/backoff, `DEAD_LETTER` handling, stale-`PROCESSING` recovery, the
  transactional outbox (`outbox_events` exists but nothing writes or reads it), worker concurrency
  safety (`attempt_count` is never incremented, no `@Version`).
- Tests: unit and `@WebMvcTest` slices run without Docker; anything importing
  `TestcontainersConfiguration` needs a running Docker daemon.
- No RabbitMQ, no compose file, no deployment target. The database is a remote instance reached
  through a forwarded port (see `.env`), not something this repository provisions.

If this block is stale, say so and ask, rather than assuming either extreme (empty repo or
half-built system).

## 2. Learning Path (priority order for v1)

Each stage exists so the next one lands on something already understood. Do not skip ahead to a
later stage because it looks more impressive.

1. **Job lifecycle, in-process** — model the state machine explicitly, funnel every status change
   through one service, keep an append-only audit trail of transitions.
2. **Failure handling** — retry with backoff in Java, attempt counting, idempotent handlers, and
   tests that cover the failure path rather than only the happy path.
3. **Consistency** — the transactional outbox: why the DB write and the message publish cannot be
   atomic, and at-least-once delivery with duplicate suppression.
4. **RabbitMQ (v2)** — exchange / queue / binding for real, manual ACK after the commit, redelivery
   and prefetch, DLX/TTL and why retry did not start there.
5. **Deployment, CI/CD, hosting, Linux, Cloudflare (v2+)** — deferred, see §9.
6. **AI agent integration (later)** — new job types, no schema migration. See §7 on `job_type`.

## 3. Knowledge Baseline (how to pitch explanations)

I already have working knowledge of: **Java 21, Spring Boot, Maven, PostgreSQL basics, Docker
basics, Linux basics, CI/CD concepts, Cloudflare basics.**
→ Do **not** over-explain these. Assume competence. Correct me only when I am actually wrong, not
by default-explaining fundamentals I did not ask about.

I do **not** yet have working knowledge of: **RabbitMQ** (concepts only — exchange, queue,
binding — never built anything with it).
→ Explain RabbitMQ code more thoroughly than other code: what each config bean does, why ACK/NACK
behaves the way it does, what happens on failure. Do not assume I will catch subtle mistakes here
the way I would in Spring/JPA code.

When I am new to something, the explanation I want is: the mechanism, then the failure mode, then
where the framework's defaults differ from the textbook. Not a tutorial, not a list of options.

## 4. Tech Stack

- **Java 21**, **Spring Boot 4.1.2** (parent POM), **Maven** (not Gradle)
- **PostgreSQL** + **Flyway** migrations
- **Testcontainers** for integration tests (requires a running Docker daemon)
- Actuator + Prometheus registry, Lombok
- Base package: `com.backend.jobplatform`

Conventions that are not obvious from the POM:
- **Lombok**: `@RequiredArgsConstructor` and `@Slf4j` only. DTOs are records, not `@Data` classes.
  Entities use explicit constructors/accessors — no `@Data` on JPA entities.
- **`hypersistence-utils-hibernate-63` is pinned to the Hibernate 6.3 line** while Boot 4.x ships
  Hibernate 7.x. Verify compatibility (or switch artifacts) *before* relying on it for the JSONB
  `payload` mapping.
- **Auth is not in scope.** `.env` / `.env.example` contain `SECRET_KEY`, `TOKEN_EXP`,
  `REFRESH_EXP`, `ALLOWED_ORIGINS` — treat these as placeholders/leftovers. Do not implement
  authentication or CORS from them; ask first.

## 5. Layout and Commands

The repo root is **not** a Maven project. All Java lives in `jobplatform/`.

From the **repo root** (preferred — works regardless of the shell's cwd). The wrapper itself is
inside `jobplatform/`, so the path includes it:

Windows / PowerShell:
```
.\jobplatform\mvnw.cmd -f jobplatform\pom.xml clean install
.\jobplatform\mvnw.cmd -f jobplatform\pom.xml test
.\jobplatform\mvnw.cmd -f jobplatform\pom.xml test -Dtest=SomeTest
```

Unix:
```
./jobplatform/mvnw -f jobplatform/pom.xml test
```

From inside `jobplatform/`, the short form works: `./mvnw test`

Never write a bare `mvn ...` from the repo root — it fails with "no POM in this directory" and then
the next step gets improvised.

Notes:
- `mvnw` / `mvnw.cmd` are committed and pinned to Maven 3.10.0. Prefer them over the locally
  installed Maven, even though `mvn` happens to be on PATH.
- Tests use Testcontainers, so Docker must be running. A failure caused by a missing daemon is not
  a code failure.
- There is **no** `docker-compose.yml` yet. If a task needs local Postgres, stop and ask how it
  should be provisioned rather than inventing a compose file.
- Maven needs write access to `~/.m2` for its artifact locks. In a sandboxed agent session this can
  surface as `Could not open file channel for ...\.m2\repository\.locks\...` — that is an
  environment denial, not a broken build. Request wider access; do not "fix" it by editing the POM
  or switching to an offline/local-repo workaround.
- **Transaction boundaries.** `@Transactional` is applied by a proxy, so a method calling a sibling
  `@Transactional` method **in the same class** gets no transaction at all. Transactional work lives
  on its own bean (`JobClaimer`, `JobSettler`, `JobEnqueuer`); the calling loop (`JobWorker`) must
  stay non-transactional so handlers never run inside one.
- If the build changes, update this section in the same commit. Stale commands are worse than none.

## 6. Package Structure

```
com.backend.jobplatform
├── api/        # Controllers, DTOs — HTTP boundary only, no business logic
├── domain/     # Job, JobEvent entities + business logic, status transitions
├── worker/     # Consumers, job handlers — executes job logic
├── outbox/     # Outbox publisher, poller — DB-to-queue bridge
└── config/     # RabbitMQ, DB, Actuator config
```

Rule: a class belongs to exactly one package by responsibility. If a class seems to belong to two,
that is a sign a responsibility boundary is being violated — raise it, don't just place it somewhere.

## 7. Code Style and Rules

General:
- Java 21 — records for immutable DTOs; pattern matching where it improves clarity, not for its own
  sake
- Constructor injection only; field injection (`@Autowired` on a field) is forbidden
- No business logic in `api/` controllers — a controller calls a service in `domain/`, nothing else
- Every public method that can fail makes failure explicit (checked outcome, `Optional`, or a
  documented exception) — no silent nulls
- Composition over inheritance for job handlers (strategy pattern over base-class overrides)

Project-specific (these encode architecture decisions):
- **Do NOT** publish a message directly inside a `@Transactional` service method. Always go through
  the outbox table. *(Decision recorded here; ADR-0002 is still unwritten — see §11.)*
- **Do NOT** use RabbitMQ auto-ack once RabbitMQ is introduced. Manual ACK only, and only after the
  DB state update succeeds.
- **Do NOT** scatter `job.setStatus(...)` calls across classes. All status transitions go through a
  single `JobStatusUpdater` service in `domain/`. This exists so a future WebSocket/SSE upgrade only
  touches one place.
- Retry logic in v1 lives in Java (scheduled backoff), **not** RabbitMQ DLX/TTL. Do not introduce
  broker-based retry until explicitly told v2 has started. *(Decision recorded here; ADR-0003 is
  still unwritten.)*
- `job_type` stays a free-form string at the DB level, not a hardcoded enum — new job types
  (including future AI agent tasks) must not require a schema migration.
- Status values are constrained by `chk_jobs_status`: `CREATED`, `QUEUED`, `PROCESSING`,
  `SUCCEEDED`, `FAILED_RETRYABLE`, `DEAD_LETTER`, `CANCELLED`. Adding a status is a migration, not a
  Java-only change — flag it before doing it.

Testing:
- Every job status transition needs at least one test covering the failure path, not just the happy
  path
- Idempotency of worker handlers must be tested explicitly once workers exist (processing the same
  `job_id` twice is safe)

**Definition of done** for any code task:
1. The full test command in §5 passes.
2. Anything that can fail has a failure-path test.
3. No rule in this section is worked around. If a rule seems wrong, raise it — do not silently
   deviate, and do not silently comply with a request that contradicts it.
4. If you could not run the tests, say so explicitly and why. Do not report success without a run.

## 8. RabbitMQ (special handling — read before touching RabbitMQ code)

This is **not part of v1**. v1 keeps retry, queueing, and worker dispatch in-process (Spring
`@Scheduled`, direct method calls) specifically so the job lifecycle and failure handling are
learned *before* a broker is added on top.

Conflict note: **ADR-0001** describes RabbitMQ as the v1 asynchronous backbone. That is superseded by
this file — v1 has no broker. If ADR text and this section disagree, follow this section and flag the
drift instead of acting on the ADR.

When v2 starts:
- Explain concepts as we go; do not assume exchange/queue/binding semantics survived from reading
- Prefer small, runnable examples over abstract explanation
- Call out explicitly where Spring AMQP's defaults differ from "textbook RabbitMQ" (connection
  factory pooling, `SimpleMessageListenerContainer` behaviour) — that is where newcomers get lost
- Do not swap Java-based retry for DLX/TTL retry without discussing it first; it is an architecture
  decision (ADR-0003), not a code change

## 9. Deployment, CI/CD, Hosting, Cloudflare (deferred — v2+)

Not relevant to v1. v1 is local-only: run from the IDE or terminal, no deployment target.

For v2+:
- I already maintain a reusable CI/CD pipeline (build → test → release) from other projects. Ask me
  for the reference workflow instead of designing one from scratch.
- Branch protection / ruleset strategy will be defined then, not now.
- Hosting target and Cloudflare usage (DNS? proxy? tunnels? Workers?) are undecided — do not assume
  one.

**Do not pre-build** CI/CD config, deployment Dockerfiles, or hosting code in v1. If something
genuinely must be deployment-aware now, use a clearly marked placeholder and flag it.

## 10. Working Agreement

- Communicate primarily in **English**.
- Do **not** propose new patterns, libraries, or architectural changes unprompted. If you think one
  is needed, ask first and explain the trade-off — don't just implement it.
- Default to the simplest solution that fits the *current* stage (§1). Check `docs/architecture/`
  and the latest ADRs before assuming a more complex pattern is warranted.
- When a request conflicts with an existing decision, cite it and flag the conflict explicitly —
  don't silently override the past, and don't silently comply with a request that contradicts it.
- If a request is a significant architecture decision (new dependency, new pattern, changed
  responsibility boundary), stop and discuss before writing code. Draft the ADR for review; I
  approve or reject it. Never mark an ADR `Accepted` yourself.
- Ask clarifying questions when a request is ambiguous. Guessing and building the wrong thing is
  more expensive than one question.
- Prefer one small, reviewable change over a large one. If a task implies more than a few files of
  new logic, propose splitting it before starting.

## 11. Where to Look for More Context

- `docs/architecture/overview.md` — current components, data model, job lifecycle, open questions
- `docs/adr/` — why specific decisions were made (index in `docs/adr/README.md`). ADR-0002 and
  ADR-0003 are indexed as *Pending*: until they are written, the operative text of those decisions is
  the bullet list in §7 of this file, and the index title alone is not enough to infer intent.
- `docs/learning-log.md` — my own notes on what I have learned or misunderstood. Context for *why* I
  ask things a certain way; **not** a source of rules.
- `docs/tasks/` — how to write and execute a task brief (`README.md`, `template.md`), plus the
  current task.
