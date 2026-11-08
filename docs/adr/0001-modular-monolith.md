# ADR-0001: Use a Modular Monolith, Not Microservices, for v1

## Status
Accepted

## Date
2026-11-05

## Context
This is a learning project for distributed job processing concepts: asynchronous processing,
queues, failure handling, retries, and observability. The project is built and maintained by a
single developer, learning the domain for the first time, with AI coding assistance.

Microservices would introduce real distributed-systems failure modes (network partitions between
services, service discovery, distributed transactions across service boundaries) on top of the
concepts already being learned (async processing via a message broker). That is a lot of new
complexity to absorb simultaneously, and most of it is operational overhead rather than learning
value for this project's stated goals.

## Decision
Build v1 as a single Spring Boot application (a "modular monolith") with clear internal module
boundaries: `api`, `domain`, `worker`, `outbox`, `config`. RabbitMQ is used as the asynchronous
backbone between the "producer" side (API writes jobs) and the "consumer" side (worker processes
jobs) even though both sides run in the same process/deployment unit in v1.

Module boundaries are enforced by package structure and by convention (see `AGENTS.md`), not by
network boundaries. A class in `worker/` must not reach into `api/` internals, for example.

## Consequences
- Simpler to run, debug, and reason about locally (`docker-compose up`, one JVM, one log stream)
- No distributed transaction problem across services — the harder version of the outbox problem
  (DB write vs. message publish consistency) is still present and still worth learning, but it's
  not compounded by a second network hop between services
- Learning value is retained for: async processing, queueing, retry/backoff, idempotency,
  observability — the actual stated goals of this project
- Lost learning value (deferred, not eliminated): inter-service network failure handling, service
  discovery, distributed tracing across service boundaries, deployment coordination between
  independently-versioned services
- The module boundaries (`api` / `worker` / `outbox`) are deliberately drawn so that splitting
  `api` and `worker` into two separately deployable processes later (if ever desired) should not
  require a rewrite — just a new entry point and possibly two Maven modules instead of one

## Alternatives Considered
- **Full microservices split (API service + Worker service) from day one** — rejected: adds
  operational complexity (service discovery, inter-service auth, separate deployments) without a
  corresponding increase in learning value for this project's current goals. Revisit if/when a
  concrete reason emerges (e.g., needing to scale workers independently in a way a single process
  genuinely cannot support).
- **Single unstructured Spring Boot app with no module boundaries** — rejected: would still
  "work" but defeats the purpose of learning to reason about component responsibilities, and
  would make a later split (if ever needed) much harder.