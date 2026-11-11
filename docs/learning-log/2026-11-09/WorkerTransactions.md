## 2026-11-09 — The transaction that wasn't

**Context.** I wrote the worker happy path by hand: enqueuer, claim a batch, look up the handler in
a registry, execute, settle. It read fine. Two bugs, and neither was about what I wrote — both were
about *where* I put things.

**Bug 1: `@Transactional` on methods I call from the same class.** The worker called `this.claim(10)`
and `this.settle(...)`. Spring applies `@Transactional` through a proxy, and a call that stays inside
the same object never crosses it. Both annotations were comments. The tell was sitting next to it:
`JobEnqueuer` has the identical call pattern and works, because there the annotation is on the method
Spring actually invokes. The difference is not the code, it's the door you enter through.

What that cost, concretely: `findJobsToHandle` ran in Spring Data's own read-only transaction, which
committed and released every `FOR UPDATE SKIP LOCKED` lock before a single job was transitioned. I
had copied the clause because that's what queues do, not because I understood that a lock only
exists for the duration of a transaction. So the select and the update were never atomic — the thing
the clause was there to guarantee.

**Bug 2: I declared `PROCESSING` twice.** `claim()` already moved each job `QUEUED → PROCESSING`;
then the loop settled it to `PROCESSING` again. `PROCESSING.canTransitionTo(PROCESSING)` is false, so
it threw on every tick, before any handler ran. The worker was a no-op that looked busy.

This one made me feel better rather than worse. The state machine refused an edge that can't exist,
loudly, and the single funnel meant there was exactly one place to look. An anemic entity with a
`setStatus` would have accepted it silently and I'd have found it a week later as "sometimes a job
skips straight to done".

**What I changed.** Claim and settle became their own beans (`JobClaimer`, `JobSettler`) so the
proxy boundary is real; `JobWorker` is deliberately non-transactional, because a handler must never
run inside a database transaction; each job gets its own try/catch so one failure can't abort the
batch; and the failure paths now have tests instead of hopes.

**What I take away.**

- Draw the transaction boundary where the invariant is, then put the annotation on that boundary.
  Claim (select + transition) is atomic. Execution is not transactional at all. Settle is atomic.
  The annotation should describe the design, not decorate methods.
- Self-invocation fails silently. Nothing warns, no log line, no exception — the code just runs
  without the guarantee you think you bought.
- Structure pays for itself quietly. I did not appreciate the funnel until it caught my own
  duplicate transition within a minute of me writing it.

**Still unsure.**

- Two beans vs `TransactionTemplate` vs AspectJ load-time weaving. The last would make
  self-invocation work, which sounds convenient and is exactly why I'm suspicious of it.
- A batch of ten, sequentially, at a 15-second delay: simple to reason about, terrible to demo. I
  haven't decided whether that's a feature (observable states) or just slow.
- `attempt_count` is still always zero, so "failure" currently means "stuck". Until the retry policy
  exists, the failure branch is a place jobs go to die.
- I now keep the learning log in two places: this folder and `docs/learning-log.md`. That is the
  exact duplication I wrote about in the first entry — two homes for one fact, and whichever I read
  first wins by accident. Pick one.

---

**Addendum, same day — `@PrePersist` "did not work".**

It did work. It never ran where I was looking.

`@PrePersist` and `@CreatedDate` are callbacks the *persistence provider* invokes while it persists
an entity. A unit test that does `new Job()` never talks to Hibernate, so neither fires and the
field stays null. Setting it in the factory was the right instinct — but then there were two clocks:
the injected one the service uses for transitions, and the framework's own for creation, plus a
test asserting a fixed instant the code never used.

Resolution: one clock. The service reads `clock.instant()` once and passes it into both the job and
its event; auditing is gone; `@PrePersist` survives only as a null-fallback. Rule of thumb: if a
value matters to the domain, the domain sets it. Callbacks are for values only the framework could
know — they are not a substitute for a constructor.

