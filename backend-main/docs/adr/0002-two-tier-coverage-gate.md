# 0002 — A two-tier coverage gate: 0.90 mandatory, 0.95 advisory

## Decision

Line coverage is enforced in **two tiers**:

- **0.90 mandatory** — `jacoco:check` in `pom.xml`, and `COVERAGE_LINE_MIN` in
  `.gitlab-ci.yml`. Below it the build is red.
- **0.95 advisory** — `COVERAGE_LINE_TARGET`, checked by `scripts/CheckLineCoverage.java` in a
  job marked `allow_failure: true`. Missing it is a **visible yellow mark**, not a failure.

Branch coverage has a single floor (`0.88`) in the pom only. The pom and `.gitlab-ci.yml`
numbers are kept in step **by hand**; moving one means moving the other.

## Context

One number was doing two jobs. `jacoco:check` is bound to `verify`, so a threshold with no
headroom breaks CI in somebody else's merge request, on a change that has nothing to do with
tests. But a floor set low enough to be safe stops describing where the suite is trying to be.

## Alternatives considered

**One number.** It has to be either conservative (and stops being a target) or ambitious (and
becomes a tax on unrelated work). Batch 4 tried `0.92` and settled back to `0.90` when the
two-tier arrangement landed.

**Widening the JaCoCo `<excludes>`** — adding `**/model/**` or `**/dto/**` would have bought
several points without a single test. Rejected in batch 2: that redefines the ratchet instead
of clearing it.

## Rationale

Splitting the number lets **only one of the two be conservative**. The floor can sit safely
below the measurement while the target says where the suite is going, and neither has to
compromise for the other.

The floor is raised only when the suite clears it, and it sits about one and a half to two and
a half points below the measurement — enough headroom that ordinary work cannot trip it. The
ratchet is one-directional by rule: *raise the minimum when the suite clears it, never lower it
to make a build pass.*

Both gates were verified to actually gate, by temporarily raising them until the build went
red and reading the message.

## Consequences

- Two files hold the same number and drift is possible. The comment in each names the other.
- `scripts/CheckChangedCoverage.java --threshold 30` is a **third**, unrelated number: it looks
  only at changed code. A change can be fully covered and still drop the bundle, and the bundle
  can sit above its floor while a new file arrives untested.
- Coverage figures are only comparable when measured with `./mvnw clean verify` —
  [P-5](../test-findings.md#p-5--coverage-measured-without-clean-is-not-the-coverage-ci-measures).
- Coverage is now a **poor proxy** for what is untested: what remains is mostly Criteria
  predicates and specification lambdas. The gate stops regressions; it does not direct work.

## Sources

`pom.xml` (the `check` execution comments); `.gitlab-ci.yml`, the `COVERAGE_LINE_MIN` and
`COVERAGE_LINE_TARGET` variables and the `coverage:line-target` job that reads them;
[test-plan.md](../test-plan.md) — *Three different coverage numbers*;
[P-1](../test-findings.md#p-1--the-coverage-baseline-number-was-wrong),
[P-5](../test-findings.md#p-5--coverage-measured-without-clean-is-not-the-coverage-ci-measures).
