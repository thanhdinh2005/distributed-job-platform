# Architecture Decision Records

Index of all ADRs for this project. Don't delete superseded ADRs — update their status and
keep them for historical context.

| ADR | Title | Status |
|---|---|---|
| [0001](0001-modular-monolith.md) | Use a Modular Monolith, Not Microservices, for v1 | Accepted |
| 0002 | Transactional Outbox Pattern for DB-to-Queue Consistency | Pending |
| 0003 | Retry Logic in Java (Scheduler) Before RabbitMQ DLX/TTL | Pending |

## When to write an ADR
A significant, hard-to-reverse architectural choice: framework/library choice, a pattern that
shapes multiple modules, a trade-off with real consequences later. Not: minor implementation
details, temporary workarounds, personal style preferences, easily-reversible choices.

## Template
See [`template.md`](template.md) in this directory.