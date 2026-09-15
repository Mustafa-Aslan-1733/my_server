# 0008 — A single-responsibility refactor that changed no interface

## Decision

Six large services were split into focused collaborators across seven commits, under one
constraint: **no route, status code, response field or message string may differ.** The API,
contract and E2E layers were expected to stay green **unedited**, and that expectation was the
acceptance criterion.

`AuthService` (468 lines, 18 constructor parameters) → `LoginCodeService`, `SessionIssuer`,
`SessionRevoker`, `IdentityService` and three helpers. `SocialService` (591) → seven classes.
`ModerationUserService` (843) → seven. And so on; the full old-to-new map is in
[test-plan.md](../test-plan.md).

## Context

Several services had grown past the point where a reader could hold one in their head, and
their test classes had grown with them. The suite was mature enough to act as a safety net,
which is the only condition under which a refactor of this size is worth attempting.

## Alternatives considered

**Refactor and fix behaviour together.** Rejected: it makes the safety net useless, because a
green suite no longer distinguishes "the move was faithful" from "the fix compensated for a
mistake in the move". Everything the work turned up that was client-visible went to the backlog
**unfixed and named** instead.

**Leave them.** The `CHANGELOG` has no entry for any of this, and a client cannot tell it
happened — which is exactly the argument for it being safe, and also why it needed a rule
saying it would stay that way.

## Rationale

The constraint is what made the refactor checkable. Because no interface moved, the three
outer layers are a **regression oracle**: they were not edited, and they stayed green.

## Consequences

- **Splitting a class does not find defects. Asking what pins each half does.** Five gaps
  answered "nothing": the grouped counts feeding `UserResponse.warnings`, `KeysetPage`'s slice
  boundary, `ContentAudit`'s 120-character preview, `KitEmail`'s three copies of one rule, and
  configuration binding — where `@DefaultValue("3")` and the configured `3` were the same
  number, so no test could tell binding from default until the default was set to 99.
- Findings written before the refactor name classes that no longer exist. They are
  **deliberately not rewritten**: an entry saying where a defect *was* is a record, and editing
  it to name a class that did not exist then makes it a worse one. `test-plan.md` carries the
  mapping.
- Two things it deliberately did **not** do. `ModerationCommentService` and
  `ModerationAnswerReportService` are still two classes: measured, a template method over them
  needs thirteen abstract methods to save a hundred and twenty lines, which separates the
  algorithm from its data for no gain. The one rule that could silently diverge —
  `ACTION_TAKEN` being the only status that hides content — was extracted as
  `ReportOutcome.visibilityFor`. **The Kontrollphase twin-pair sweep vindicated that call and
  qualified it:** the two classes' guards mirror perfectly, and what did *not* mirror was a
  handler against the service it inverts ([F-31](../test-findings.md#f-31--reverting-a-report-status-is-impossible-on-exactly-the-changes-worth-reverting)),
  which merging them would not have prevented.
- The class diagrams in [class-diagrams.md](../class-diagrams.md) use post-refactor names.

## Sources

[test-plan.md](../test-plan.md) — *The single-responsibility refactor*; `docs/TODO.md`, *Backlog*.
