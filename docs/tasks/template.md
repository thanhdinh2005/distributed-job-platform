# TNNN — <short imperative title>

Status: Draft | Approved | In progress | Merged
Owner: me (author) / agent (executor)
Depends on: TNNN or "none"

## Goal

One sentence. An observable outcome, not an activity. If it cannot be verified by reading a test
or running a command, rewrite it.

## Context

What exists right now, in three or four lines. Which files matter. What to read first
(`AGENTS.md` §N, the ADR, the previous task).

## In scope

- ...

## Out of scope

- ... (explicitly: no controller, no scheduler, no new dependency, no refactor of X)

## Constraints

- Decision already made: ... (cite `AGENTS.md` §N or ADR-NNNN; do not restate the whole rule)

## Acceptance criteria

- [ ] ...
- [ ] Failure path covered: ...
- [ ] ...

## Verification

```
.\jobplatform\mvnw.cmd -f jobplatform\pom.xml test
```

Expected: all green, including `<NewTestClass>`.

## Open questions

- Q: ... → ask before starting; do not pick one silently.

## Notes / decisions during execution

Filled in while working, not before. Anything that surprised us goes here and, if it changes a
rule, gets promoted to `AGENTS.md` (or an ADR draft) in the same change.
