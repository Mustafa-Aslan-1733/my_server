# Architecture Decision Records

One file per decision, in the form **Decision / Context / Alternatives considered / Rationale /
Consequences**, each closing with a **Sources** section naming where the reasoning already
lived.

**Every record here is extracted, not invented.** The reasoning already existed — in
`CHANGELOG` entries, in [test-findings.md](../test-findings.md), in
[test-plan.md](../test-plan.md), and in comments inside `pom.xml` and `.gitlab-ci.yml` — it was
just spread across the places the work happened. Each record cites where its reasoning lives so
the citation can be checked rather than trusted. Where a decision's rationale was **not** in the
record it was left out and written up as an open question in [worklog.md](../worklog.md)
instead; a plausible-sounding reconstruction is worse than an admission.

| # | Decision |
|---|---|
| [0001](0001-five-test-layers.md) | Five test layers, and the question each one answers |
| [0002](0002-two-tier-coverage-gate.md) | A two-tier coverage gate: 0.90 mandatory, 0.95 advisory |
| [0003](0003-gitlab-service-over-testcontainers.md) | A GitLab CI service instead of Testcontainers |
| [0004](0004-twin-database-baselines.md) | Twin H2 and PostgreSQL baselines, differing in two lines |
| [0005](0005-mutation-testing-scope.md) | Mutation testing scoped to the context-free packages |
| [0006](0006-lombok-not-filtered-from-coverage.md) | Lombok is not filtered out of the coverage report |
| [0007](0007-characterization-and-inversion.md) | Characterization tests, inverted rather than deleted |
| [0008](0008-single-responsibility-refactor.md) | A single-responsibility refactor that changed no interface |
| [0009](0009-vote-withdrawal-over-toggle.md) | `VoteType.NONE` rather than a toggle |
| [0010](0010-405-over-404-for-a-wrong-verb.md) | 405 with `Allow` instead of 404 for a verb a path does not map |
| [0011](0011-narrow-exception-handlers.md) | Narrow `@ExceptionHandler`s over `ResponseEntityExceptionHandler` |
| [0012](0012-documentation-under-test.md) | The admin API document is tested against the code |
| [0013](0013-consumer-expectations-in-the-backend-repo.md) | Consumer expectations live here, as a generated list under test |
| [0014](0014-legacy-auth-me-alias.md) | `GET /auth/me` restored as a time-boxed legacy alias |
| [0015](0015-deletion-not-split-from-anonymisation.md) | Deletion is not split from anonymisation |
| [0016](0016-self-service-lifecycle-recorded-not-authorised.md) | The app-side account lifecycle is recorded, on the administrative log, and not revertible |
