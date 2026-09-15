# 0004 — Twin H2 and PostgreSQL baselines, differing in two lines

## Decision

The schema is kept as **two Flyway baselines** — `src/main/resources/db/migration/postgresql/`
for production and `src/test/resources/db/migration/h2/` for the fast suite — and they are
**identical except for two lines**: `audit_logs.changes` and `audit_logs.metadata` are `jsonb`
on PostgreSQL and `json` on H2.

`spring.jpa.hibernate.ddl-auto=validate` in both, so the entities are checked against whichever
schema is present rather than generating one.

## Context

The unit and API layers need a database that starts in milliseconds and needs no service. The
integration layer needs the real thing. Hibernate's `create-drop` would give neither: it would
test a schema Flyway never produced.

## Alternatives considered

**H2 only.** Cannot answer the `jsonb` question, and `jsonb` is load-bearing — every revert
handler reads its `before` values back out of that column, so the whole revert feature rests on
that round trip.

**PostgreSQL for everything.** Would put a service dependency on the fast suite and on every
developer machine. See [0003](0003-gitlab-service-over-testcontainers.md).

**Generating the H2 schema from the entities.** Then the test schema and the production schema
could diverge without anything noticing, which is the failure this arrangement exists to
prevent.

## Rationale

Two files that differ in **two lines** can be diffed. The difference is the one place the
databases genuinely disagree, and it is exactly where the integration layer aims:
`AuditJsonbRoundTripPostgresTests` first asserts the column really *is* `jsonb` — without that,
the other two tests would pass while quietly testing the H2 shape.

## Consequences

- **Every schema change is two edits.** A migration that touches only the production baseline
  breaks `ddl-auto=validate` under the test profile, loudly and immediately — which is the
  intended failure mode, but it is a step people forget.
- The H2 suite runs on the column type that does **not** normalise JSON object keys. About
  fifty `Map.of` call sites build audit `changes`/`metadata`, and `Map.of` iteration order is
  randomised per JVM run; they are correct in production **only** because `jsonb` normalises.
  The fast suite would not catch a regression there. Written up under *Where the suite stops*
  in [test-plan.md](../test-plan.md) — it is
  [F-30](../test-findings.md#f-30--a-rate-limiter-that-is-only-correct-because-a-clock-in-another-file-is-utc)'s
  shape: correctness resting on a fact in another file.
- The repository has exactly one baseline version (`V1`). Two pending items want a `V2`
  (`docs/TODO.md` 14 and 23), and that is when this arrangement gets its first real exercise.

## Sources

The two baseline files; [test-plan.md](../test-plan.md) — *The `jsonb` round trip*;
`application-test.properties`.
