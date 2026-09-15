# 0016 — The app-side account lifecycle is recorded, on the administrative log, and not revertible

## Decision

Two audit actions were added for what a student does to their own account:

- **`USER_SELF_DELETED`** — written by `StudentService.deleteAccount`, the app's own
  `PATCH /account/deleteAccount`.
- **`USER_SELF_REACTIVATED`** — written by `AccountReactivator`, when a soft-deleted account is
  set back to `ACTIVE`.

Three things were decided with them, and each had a cheaper alternative that was refused:

1. **Both are `ADMINISTRATIVE` scope**, so they answer on `GET /admin/audit-logs` beside
   `USER_DELETED` rather than on `/activity-logs` — even though `actor.type` is `USER`.
2. **Both are new constants**, rather than `USER_DELETED` reused and the reactivation written as
   a `USER_UPDATED` field diff.
3. **Neither is revertible.** Both stay outside `AuditRevertService.REVERTIBLE_ACTIONS` and both
   carry an empty `changes` map, so the generic machinery refuses them twice over.

**The reactivation itself was not touched.** These entries record it; they do not authorise it.
That behaviour is [F-48](../test-findings.md) and is open by decision, not by omission.

## Context

[F-46](../TODO.md) recorded that `PATCH /account/deleteAccount` writes no audit entry. Measuring
it confirmed the stronger form: **none of `AccountController`, `AccountService` or
`StudentService` holds an `AuditWriter` at all**, and neither does `LoginCodeService`. So an
account could leave and come back with nothing anywhere recording either, and `deletedAt` — the
one field that would have dated it — is set only by the administrative path and is served by no
DTO.

The admin panel had just been given a `status=DELETED` list ([ADR-0015](0015-deletion-not-split-from-anonymisation.md),
`CHANGELOG` `10.09 (18)`) and, with it, a rule in writing: *a `DELETED` row carrying a real
username is a self-deletion and has no matching `USER_DELETED` entry*. That rule was the only way
an operator could tell the two kinds of deletion apart, and it existed because of the gap.

## Alternatives considered

**Write both to the activity log (`ACTIVITY` scope).** Semantically the tidier reading: the actor
is a student, and `USER_LOGIN` and `COMMENT_CREATED` live there. Refused on a measurement rather
than on taste — **the admin panel deleted its Activity Log screen and asserts the route is gone**
(`adminweb-consumer-contract.md:1145`, its own `App.integration.test.tsx:194-212`). An
`ACTIVITY` entry would have closed the record gap and none of the visibility gap, which was the
entire request. `AuditActionScope`'s own class note already licenses the other reading — *"the
split is by action rather than by actor type on purpose"* — and cites a refused **admin** login
sitting in the activity log as the mirror image.

**Reuse `USER_DELETED` for the self-deletion.** No new enum value, and
`GET /audit-logs?action=USER_DELETED` would have found both kinds at once. Refused because the
two are not the same event: the administrative path anonymises the row in the same transaction
and this one does not, so a self-deleted account keeps its real identity. Merging them would also
have **invalidated the rule the panel had just been given** — the only means it has of
distinguishing the two — and replaced it with nothing.

**Record the reactivation as a `USER_UPDATED` field diff** (`status: DELETED → ACTIVE`). The
cheapest of all: no constant, and the panel's existing renderer already draws status diffs.
Refused because **`USER_UPDATED` is in `REVERTIBLE_ACTIONS`**. The entry would have been
revertible, which hands an administrator a working control that flips an account back to
`DELETED` — a restore route arriving through the back door of the revert machinery, in a product
where [ADR-0015](0015-deletion-not-split-from-anonymisation.md) refused restore deliberately. A
capability nobody decided to add is worse than one that was argued for and declined.

**Fix the reactivation instead of recording it** — require the code to be entered, move the flip
into `SessionIssuer`, or drop the behaviour. Out of scope by instruction and correctly so: the
revival was added on purpose (commit `bf86ee6`), so removing or gating it is a product decision.
The three options are costed under F-48 in [TODO.md](../TODO.md).

## Rationale

**Scope follows the reader, not the actor.** That rule was already written and already applied in
the opposite direction; applying it here is consistency, not an exception. An operator asking
"where did this account go" asks on one screen, and a lifecycle split across two logs answers
nobody.

**A separate constant costs nothing on either client.** `ConsumerEnumContractTests:39-42` had
already established, with the reason, that a new `AuditAction` is safe for the panel: its filter
is built from `/audit-logs/meta` rather than compiled in, and an unknown action renders through a
default. The Android client does not consume `AuditAction` at all. So the argument for reuse was
only ever brevity, against a rule the panel actually depends on.

**Two guards on revertibility rather than one.** The action is outside the revertible set *and*
the entry carries no field diff. Either alone would hold today; both together mean a later edit
to one of them cannot quietly open a restore.

**The transactional boundary was chosen, not inherited.** `StudentService.deleteAccount` is now
`@Transactional` so the status change and its entry commit together — a crash between them would
otherwise leave an account `DELETED` with nothing recording it, the exact state the entry exists
to deny. `AccountReactivator` is a bean of its own because a `@Transactional` method called from
inside `LoginCodeService` would not be proxied and the annotation would be inert. Both use
`REQUIRED`, **not** the `REQUIRES_NEW` that `AuditWriter.writeRefusal` uses: that method suspends
its caller's transaction because every one of its callers throws immediately afterwards, and
copying it here would only let an entry outlive the change it claims to describe.

**What deliberately did not change:** the reactivation still commits before the login code is
mailed, so it still survives a delivery failure that answers the caller `500`. Widening the new
transaction to cover delivery would have fixed half of F-48 as a side effect of a refactor.

## Consequences

- **Two new values in a response**, so `CHANGELOG` `10.09 (20)`. Nothing on the panel needs a
  release: the filter is meta-driven and unknown actions already render.
- **`AccountReactivator` now owns one decision** — what a login-code request does to a deleted
  account. Whichever way F-48 is answered, the change lands in that class rather than in the
  middle of a method about mailing codes.
- **The panel's rule changed and was rewritten**, not silently broken:
  [adminweb-tasks.md §6](../adminweb-tasks.md) now says a self-deletion *does* have an entry, and
  names which one.
- **The reactivation is now visible without being authorised.** An operator can see that an
  account came back; nothing yet says who asked, because nobody identified themselves.
  `metadata.callerAuthenticated: false` states that in every row rather than in a comment.
- **F-46 closes; F-48 opens in its place** and is the sharper of the two. What the measurement
  found was not a missing record but a missing check.
- **Five tests pin it**, three of them characterizations of behaviour left unfixed —
  [ADR-0007](0007-characterization-and-inversion.md)'s rule applies to all three: when F-48 is
  answered, they are inverted rather than deleted.

## Sources

[TODO.md](../TODO.md) items 36 and 39 — the measurement of both gaps and the three costed
options for F-48; [adminweb-tasks.md §6](../adminweb-tasks.md) — the panel's rule and what
replaced it; `AuditActionScope`'s class note — the scope rule, quoted rather than invented;
`ConsumerEnumContractTests:39-42` — why a new action is safe for both clients;
`AuditWriter.writeRefusal` — the propagation choice this one is measured against;
[ADR-0015](0015-deletion-not-split-from-anonymisation.md) — the no-restore decision that ruled
out the `USER_UPDATED` shape; `CHANGELOG` `10.09 (20)`.
