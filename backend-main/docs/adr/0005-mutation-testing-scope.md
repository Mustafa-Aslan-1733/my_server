# 0005 — Mutation testing scoped to the context-free packages

## Decision

PIT runs in a Maven **profile** so it never runs in the ordinary build, and it is pointed at
the service, mapper, mail, page, revert and security packages — the classes whose tests are
plain JUnit with **no Spring context**. There is deliberately **no `mutationThreshold`**.

The scope is written as package globs, not a list of classes, which is why it survived the
single-responsibility refactor without being touched. It was **eighteen** test classes when
this record was written; the same globs now match **50 test classes over 77 mutated classes**,
because the refactor split large services into small ones. The decision did not change — the
number it produces is not part of it.

Run it with:
`./mvnw -Ppitest test-compile org.pitest:pitest-maven:mutationCoverage`

## Context

Coverage says every line ran. It says nothing about whether any assertion would have failed had
the line been wrong — and this project had already found that gap **by hand** more than once.
[P-6](../test-findings.md#p-6--a-sweep-asserted-not-refused-which-a-404-also-satisfies) is
exactly a surviving mutant, found by reading a test rather than running one.

## Alternatives considered

**Point PIT at the whole suite.** It would start a Spring context **per mutant** — the
difference between minutes and hours. The scoping is what makes it runnable at all.

**Set a threshold immediately.** Rejected: the first run surfaces every surviving mutant the
suite has ever had, none of which is a regression, and a gate that fails on the day it is
introduced teaches people to pass `-DskipTests` rather than to read it. A threshold is worth
setting once the report has been triaged — the same order the coverage floors were raised in.

## Rationale

The house rule is that unit tests use no Spring context, which is precisely what makes this
subset cheap to re-run per mutant. **1134 mutants, 840 killed (74%), 72 survived, 222 with no
coverage, in under two minutes** — measured 9 September, `target/pit-reports/`. The first run,
on 8 September, was 1194 mutants and 67% killed; the score moved on tests written since, not on
a change to the scope.

The four mail classes are **excluded** rather than left to fail: they are recorded under
*Deliberately uncovered* and have no unit tests at all, so every mutant survives. That is not
information PIT is adding — it is a decision already written down, restated as dozens of
findings that would bury the real ones.

## Consequences

- **`SURVIVED` means "the classes in this profile do not kill it", not "the suite misses it".** The
  first report's most alarming survivor turned out to be covered by two `AdminApiIntegrationTests`
  all along. A survivor is a candidate to investigate, not a finding —
  [P-7](../test-findings.md#p-7--a-surviving-mutant-is-not-a-finding) records this as a reading
  rule.
- **222 mutants have no coverage** because the profile targets classes whose tests it does not
  run. Fixing the `targetTests` scope, **then** setting a threshold, is `docs/TODO.md` item 12,
  in that order. Read this number from `target/pit-reports/` rather than from here: the first
  run reported 324, and the two are different runs rather than a contradiction.
- The `targetTests` globs had to be widened once already: a narrower pattern silently dropped a
  class the moment a collaborator was split out under a name like `RatingAverages` — it stayed
  in `targetClasses`, its tests matched nothing, and every mutant survived for want of anything
  running. That failure is invisible in the report.

## Sources

`pom.xml` — the `pitest` profile and its comments;
[P-7](../test-findings.md#p-7--a-surviving-mutant-is-not-a-finding);
[test-plan.md](../test-plan.md) — *Beyond the suite: two CI-only tools*.
