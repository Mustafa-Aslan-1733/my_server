# 0003 — A GitLab CI service instead of Testcontainers

## Decision

The integration and end-to-end layers run against a real `postgres:16` provided as a **GitLab
CI service**. Testcontainers is not a dependency. Tests opt in with
`@EnabledIfEnvironmentVariable(named = "POSTGRES_SMOKE_JDBC_URL", …)` and **skip themselves**
locally, so `./mvnw verify` on a developer machine needs no Docker.

## Context

This was recorded as *the blocker for the whole integration layer*: adding Testcontainers would
put Docker into a build that deliberately did not need it, and the team was waiting on a
decision about it.

## Alternatives considered

**Testcontainers.** The obvious answer. It needs a Docker socket, which means either a
privileged runner or a Docker-in-Docker setup, and it makes the local build depend on a daemon.

**H2 only.** Cannot answer the questions the layer exists for: `jsonb` versus `json` key
normalisation, `timestamp(6)` rounding, row locking, and whether a read-only transaction is
actually enforced on the server.

**Waiting for the team decision.** What the work actually found is that the question was the
wrong one.

## Rationale

**The pipeline already ran a real `postgres:16`** as a GitLab CI service, for the migration
smoke test. A service is a plain TCP server — no Docker socket, no privileged runner, no new
dependency. `com.pse.support.PostgresTestDatabase` is that job's setup generalised.

So the blocker was never Testcontainers-or-nothing; it was that nobody had noticed the database
was already there. Recorded because the *shape* of the mistake is worth keeping: a decision was
parked for weeks on a premise that one look at the pipeline file disproved.

## Consequences

- The local build is unchanged and still Docker-free. 31 tests skip locally and run in CI.
- **Those layers cannot be verified from a developer machine.** CI is a private GitLab with no
  token available here, so anything touching them needs the job output requested from a person.
- `PostgresTestDatabase` drops and recreates the `public` schema on init, so two Spring contexts
  sharing it would wipe each other mid-run. Every class carries `@PostgresIntegrationTest` and
  the job **greps its own log and fails unless exactly one context started** — a fork is
  otherwise invisible, looking like unrelated flakiness in whichever class ran second.
- The E2E journeys need a *different* context (`RANDOM_PORT` plus `TestDeliveryConfig`), so
  they run as a **second Maven invocation** against the same service rather than weakening that
  guard to allow two.

## Sources

[test-plan.md](../test-plan.md) — *Settled questions* and *The integration layer*;
`.gitlab-ci.yml`, the `server:postgres-integration` job; `docs/TODO.md`, *Tests — next steps*.
