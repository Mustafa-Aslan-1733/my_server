# 0006 — Lombok is not filtered out of the coverage report

## Decision

No `lombok.config` is added, and `lombok.addLombokGeneratedAnnotation` is left at its default
(`false`). Every generated getter, setter and constructor counts towards the coverage bundle.

## Context

JaCoCo can be told to ignore Lombok-generated members, and it is a common first move when a
coverage number needs to go up. This project has Lombok on almost every entity and DTO.

## Alternatives considered

**Add `lombok.config` with `addLombokGeneratedAnnotation = true`.** The standard advice.

## Rationale

Two reasons, and the first is the one that decided it.

**It might well lower the number.** Accessors here are *heavily covered* through serialization —
every response DTO is written by Jackson in an API test. Filtering them deletes covered lines
as well as uncovered ones, and there was no reason to expect the ratio to improve.

**It would quietly change what the number measures.** The gate is a ratchet, and a ratchet
whose definition moves is not one. Changing the denominator and the numerator at the same time
makes every figure in [test-plan.md](../test-plan.md) incomparable with the ones before it —
which is
[P-1](../test-findings.md#p-1--the-coverage-baseline-number-was-wrong) and
[P-5](../test-findings.md#p-5--coverage-measured-without-clean-is-not-the-coverage-ci-measures)
in a third disguise: a coverage number is only worth the conditions it was measured under.

It sits with two other shortcuts rejected in the same breath: widening `<excludes>` to
`**/model/**` or `**/dto/**`, and writing reflection tests for private constructors of
static-only classes (JaCoCo has filtered those since 0.8.0 — such a test would be fake
coverage). All three raise the number without writing a test.

## Consequences

- The bundle figure includes generated code, so it is **not** comparable with a project that
  filters. Any external comparison has to say so.
- The decision is revisitable, but only together with a re-baseline: turning it on means
  re-measuring every figure and re-deriving the floors, not just flipping a flag.
- This is the one finding closed **as a decision rather than a fix**, alongside
  [P-7](../test-findings.md#p-7--a-surviving-mutant-is-not-a-finding).

## Sources

[P-3](../test-findings.md#p-3--lombok-is-not-filtered-in-jacoco);
[test-plan.md](../test-plan.md) — batch 2, *Deliberately not done*.
