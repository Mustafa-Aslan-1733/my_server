# 0001 — Five test layers, and the question each one answers

## Decision

Tests are organised into five layers plus a concurrency class, and each layer is defined by the
**question it answers** rather than by the tool it uses:

| Layer | The question |
|---|---|
| Unit | Does this class compute the right answer? |
| API / role | Who may call this route, what status does it answer, whose rows may it touch? |
| Contract | Does the API describe itself correctly? |
| Integration (real PostgreSQL) | What does the *database* do that H2 cannot answer for? |
| End-to-end (real HTTP) | Do the pieces work together? |
| Concurrency | What happens when two requests collide? |

An assertion belongs in the cheapest layer that can settle it.

## Context

The suite was written for a system that already ran. Seven unit batches came first; the API
sweeps, contract, integration and E2E layers were each added because a specific class of defect
had escaped everything below them.

## Alternatives considered

**One integration suite over everything.** Rejected by evidence, not taste:
[P-2](../test-findings.md#p-2--integration-tests-hide-unit-targets) records integration tests
holding a unit target at 100% coverage while *nothing stated what the unit was supposed to do*.
`ModerationBugReportService` was the clearest case — 3 missed lines, 15 missed branches, because
one valid submission in an integration test covers a six-armed refusal with one arm.

**Unit tests only.** Cannot answer five of the six questions above. F-14 to F-17 were each wrong
on *every* route at once and invisible from any single class; BUG-1's cursor question needed a
real column type; F-20's missing audit entry needed a real write path.

## Rationale

Each layer found a **different kind** of defect, which is the strongest evidence the split is
real rather than administrative. The unit batches found logic — a wrong comparison, a missing
null check. The sweeps found protocol: F-14 through F-17 are all "the right thing happened with
the wrong status code". The contract layer found the API disagreeing with its own schema. The
E2E layer found a create that wrote no audit entry. Adding a layer did not find more of the
same defect; it found a new kind.

## Consequences

- Where an assertion lives is a decision each time, and the layer boundaries are argued in
  [test-plan.md](../test-plan.md) per class rather than assumed.
- The sweeps read their route list from Spring's `RequestMappingHandlerMapping`, never a
  hand-kept list — a list goes stale silently and then passes because it is looking at less
  than it thinks.
- Some questions belong to **no** layer, and that is now written down under *Where the suite
  stops*: performance, load, and migration rollback.

## Sources

[test-plan.md](../test-plan.md) — the opening table and each layer's section;
[P-2](../test-findings.md#p-2--integration-tests-hide-unit-targets).
