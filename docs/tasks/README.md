# Writing a Task Brief

An agent starts every session with no memory of our last conversation. Whatever is not in the
brief gets re-derived from the code — or invented. The brief is the spec; the code is the output.

This folder holds one brief per unit of work. A brief is written **before** the work, reviewed by
me, and then executed.

## The eight fields

| Field | What it is for | Bad | Good |
|---|---|---|---|
| **Goal** | One sentence, observable outcome. | "Implement the domain layer." | "A job can be created and read back with a full transition history." |
| **Context** | What exists now, which files matter, what to read. | "See the repo." | "Migrations V1–V3 exist; no Java code yet. Read `AGENTS.md` §6–7." |
| **In scope** | Bullet list of what changes. | — | "`Job` entity, `JobStatus`, `JobStatusUpdater`, repositories, tests." |
| **Out of scope** | What must *not* be touched. | (usually missing) | "No controller, no worker, no scheduler, no outbox poller." |
| **Constraints** | Decisions already made. Cite, do not restate. | Repeating §7 verbatim. | "Transitions only through `JobStatusUpdater` (`AGENTS.md` §7)." |
| **Acceptance criteria** | Checkable statements, not adjectives. | "Code is clean." | "An invalid transition throws `IllegalStateTransition`, and no row is written." |
| **Verification** | Exact command, expected result. | "Run the tests." | "`.\jobplatform\mvnw.cmd -f jobplatform\pom.xml test` — all green, including the new `JobStatusUpdaterTest`." |
| **Open questions** | What the agent should ask instead of assuming. | (usually missing) | "Does the transition table live in the entity or in the updater?" |

**Out of scope** and **Open questions** are the two fields people skip, and they are the two that
prevent the most wasted work. "Out of scope" is cheaper than a revert; "open question" is cheaper
than a wrong abstraction that everything else then builds on.

## Size

- One brief = one reviewable change. If it touches more than roughly five files of *new logic*,
  split it.
- One good size: **one concept + its failure path + its test.** That is also one paragraph in the
  learning log, which is the real acceptance criterion for a learning project.
- If the brief needs a diagram to explain it, it is too big. If it needs no context section, it is
  probably too small to be worth versioning.

## Who decides what

- **Hard to reverse → I decide, the brief states it.** Schema shape, new dependency, package
  boundary, anything that would need an ADR.
- **Reversible and mechanical → the agent decides.** Naming inside a class, stream vs. loop, test
  method names. Micromanaging this wastes the brief's budget.
- **Unsure → put it in Open questions.** Ambiguity left implicit gets resolved silently, in code,
  by whoever is writing it.

## Anti-patterns

- **Adjectives as acceptance criteria.** "Clean", "robust", "proper". Unfalsifiable, so the agent
  optimises for looking done.
- **Several features in one brief.** The diff becomes too large to review honestly, so it gets
  rubber-stamped.
- **Restating `AGENTS.md`.** It drifts. Cite the section instead.
- **"Make the tests pass"** without saying which behaviour is under test — invites deleting the
  test that disagreed.
- **Happy path only.** The failure path is where the learning is, and it is also the half that gets
  skipped when nobody asks for it.
- **Deciding architecture inside a task brief.** If the task needs a real decision, stop and write
  an ADR draft first; the brief then cites it.

## Order of tasks for this project

The dependency chain, one brief each — the next brief's "context" is the previous one merged:

1. **Schema** — done (V1–V3).
2. **Domain model + transition funnel** — `Job`, `JobEvent`, `JobStatus`, `JobStatusUpdater`.
   This is where the state machine becomes real, so it comes first. → `T001`
3. **API boundary** — create / read / history / cancel, with no business logic in controllers.
4. **Worker + handler registry** — strategy per `job_type`, idempotent execution.
5. **Retry scheduler** — backoff, attempt counting, `DEAD_LETTER`.
6. **Outbox poller** — and the open question of what it dispatches to in v1.
7. **v2** — replace in-process dispatch with RabbitMQ.

Picking a different order is fine; writing two of these at once is not.

## Reviewing what comes back

A short checklist, in order of how often it catches something:

- [ ] Does the diff stay inside "In scope"? Anything outside it is either a nice-to-have to revert
      or a scope change to discuss.
- [ ] Do the tests actually assert the failure path, or do they assert `assertNotNull`?
- [ ] Are the `AGENTS.md` §7 rules respected — constructor injection, no `setStatus` outside the
      updater, no business logic in controllers?
- [ ] Were dependencies added? Any new dependency is an architecture decision.
- [ ] Did it claim tests pass, and did it actually run them?
- [ ] Did anything in `docs/` become stale, and was it updated in the same change?
- [ ] Did the brief leave a question unasked that it should have raised?

## Template

Copy [`template.md`](template.md), number it `TNNN-slug.md`, keep it short. The first one is
[`T001-job-domain-model.md`](T001-job-domain-model.md) — a draft, pending my decisions.
