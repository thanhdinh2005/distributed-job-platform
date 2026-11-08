# Learning Log

Personal notes: what I have learned, what I got wrong, what I am still unsure about.
This is context, **not** rules. Agents may read it to understand why I ask things a certain way;
nothing here overrides `AGENTS.md` or the ADRs.

---

## 2026-11-06 — Writing instructions an AI agent can actually follow

**Context.** Before writing a single line of application code, I wrote `AGENTS.md`. The reasoning
was that the agent's context is fresh every session, so the file *is* the onboarding document. I
then had the agent audit that file against the repository. It found about nine places where the
file and the repo disagreed. That audit was more valuable than any code review I have had, because
documentation rots silently — nothing fails, nothing warns, the agent just quietly does the wrong
thing.

**What I got wrong, ordered by how much damage it would have caused.**

1. **Commands that do not run.** I wrote `mvn clean install`; the POM lives in `jobplatform/`. The
   expensive part is not the error message — it is what happens *next*: the agent guesses a fix.
   Once it has learned that my commands are approximate, every other line in the file becomes
   negotiable too.
2. **Pointers to files I had not written.** I told the agent to "check `docs/architecture/overview.md`
   before assuming a more complex pattern is warranted." That file did not exist. So the agent
   falls back to the summary inside `AGENTS.md` and treats a summary as the architecture — and a
   summary cannot tell you what it compressed away.
3. **Rules citing unwritten ADRs.** ADR-0002 and ADR-0003 existed only as rows in an index. The
   rules were enforceable ("don't publish inside a transaction") but not explainable. An agent can
   follow that mechanically, but it cannot tell when an exception is legitimate, so it will refuse
   good changes and allow bad ones for the same reason: it has the rule, not the reason.
4. **Two sources of truth.** ADR-0001 said RabbitMQ was the v1 backbone; `AGENTS.md` said v1 is
   in-process. Whichever the agent read first would have won by accident. Facts need exactly one
   canonical home, and the instruction file needs to say which source wins on a conflict.
5. **A stale "current stage".** It said "pre-code" after I had committed the schema and the
   skeleton. That field is what decides whether the agent scaffolds from scratch or asks about
   work that already exists.
6. **`.env` told a story the docs did not.** `SECRET_KEY`, `TOKEN_EXP`, `REFRESH_EXP`,
   `ALLOWED_ORIGINS` imply JWT auth and a separate frontend. Nothing in the docs mentioned either.
   Config files are documentation, and they are the kind that gets read literally.

**What I changed in the file.**

- Numbered sections, so the agent can cite "§7" instead of re-deriving a rule.
- A "current stage" block at the top with an instruction to update it — it is the field that goes
  stale first and matters most.
- Commands with paths, the Maven wrapper, the Windows form, and an explicit "never write a bare
  `mvn` from the root".
- A definition of done: tests pass, failure paths covered, no silent deviation, and *say so if you
  could not run them*. An agent that cannot distinguish "I verified" from "I believe" will report
  both the same way.
- A precedence note for the ADR-0001 conflict instead of silently leaving two truths.
- The things that were only implied: Lombok policy, auth out of scope, the pinned Hibernate
  version, the missing compose file.

**What I think generalizes to the next project.**

- A rule without a "because" gets over-applied. A rule with one can be reasoned about at the edges,
  which is where architecture actually happens.
- "Do not build X yet" is worth more than three "build X this way" rules. The deferred sections
  are load-bearing; they are what stops an eager agent from building three weeks of work I did not
  ask for.
- The most valuable section is not the style rules — those can be inferred from the existing code.
  It is the *explanation contract*: what to over-explain and what to assume. Nobody can infer my
  knowledge level from a repository.
- Keep it scannable — headings, and no section longer than a screen. If a rule needs a paragraph of
  justification, the justification belongs in an ADR and the rule here gets one clause and a link.
- Update it in the same commit as the thing that changed. Stale instructions are worse than absent
  ones, because they are trusted.
- Prefer the wrapper and the absolute path in every command block; a command that only works in the
  author's shell is a command that teaches the agent to improvise.

**What I still do not know.**

- Whether the learning-path section changes the agent's behaviour or just makes me feel organized.
- How to verify that an explanation was *good*. Probably only visible here, in this log.
- Whether to keep one shared instruction core across projects. Not yet — not until the second
  project has real drift of its own; copying before that just copies assumptions.
- Whether numbered sections actually get cited in practice, or whether they are decoration.

**Next step.** Write the first task brief using `docs/tasks/`, because the brief is where all of
these rules get tested for the first time.
