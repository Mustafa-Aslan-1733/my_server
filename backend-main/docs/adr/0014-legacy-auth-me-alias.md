# 0014 — `GET /auth/me` restored as a time-boxed legacy alias

## Decision

`GET /auth/me` is served again. It is a **legacy alias**: `AuthController.me` delegates to the
same `IdentityService.me` that `GET /admin/auth/me` calls, so the two cannot answer differently,
and `SecurityConfig` matches `GET /auth/me` as `hasRole("ADMIN")` so the alias carries the admin
guard rather than the surrounding `anyRequest().permitAll()`.

It is **time-boxed**: it exists until the admin panel confirms it has migrated to
`GET /admin/auth/me`, and then it goes again. `CHANGELOG` 9.09 (15) says so to the clients in
those words.

## Context

The route was deleted in `7b2eea6` on the belief that it was a test placeholder whose only
caller had already migrated. It had not. `AuthController` mapped five POSTs and no `/me`, and
because the security chain ends in `permitAll()` the path was not a `401` but an unmapped
**404 `Not found`**.

The panel calls it twice. On page load the `404` is ignored — only `401` is acted on — and the
stored session quietly stops refreshing. After a login it is fatal: the call is bare-caught,
`null` reaches `resolveAdminUser`, and the resulting `IdentityUnavailableError` fails the whole
sign-in with "Signed in, but your admin account could not be read". **The code was accepted and
the token was minted, and no administrator could get in.**

Nobody saw it because two documents in this repository disagreed and each was believed by the
side it suited: `docs/adminweb-tasks.md:81-83` said the path was gone, which was correct about
the code, and the panel's copy of an older findings file said nothing was broken. Neither
statement was attached to a test, so both survived, and the suite was green throughout.

## Alternatives considered

**Leave it deleted and have the panel migrate.** This is the outcome the deletion was aiming at
and it is still the destination — but it makes a shipped client's sign-in depend on a release in
another repository on somebody else's schedule. The defect was live in production while this
decision was being taken; the cost of waiting is measured in administrators who cannot log in.

**Change the panel instead.** Not available from here. The panel is a separate repository with a
separate deployment, which is the whole reason the disagreement above went unnoticed.

**Keep it permanently, as a second public identity route.** Rejected: two paths answering the
same question is the condition that produced this finding. An alias with no expiry is a second
route, and the next reader has no way to tell which one is the real one.

**Restore it by copying `AdminAuthController.me`'s body.** Rejected for the reason the test is
written the way it is: two copies drift, and the drift is invisible until a client hits it.
Delegation to one `IdentityService.me` makes divergence impossible rather than merely unlikely.

## Rationale

**A deletion whose premise was wrong is reverted, not defended.** The premise — "the only caller
has migrated" — was a belief about another repository that nothing in this one could check. That
is what makes it a decision worth recording rather than a bug fix: the code was doing exactly
what it was written to do.

**The alias is cheap precisely because it delegates.** It adds a mapping and a security matcher
and no behaviour, so the "keep it until the panel moves" cost is one line in a controller rather
than a second identity implementation to maintain.

**The expiry is written where the client will read it**, in the `CHANGELOG` entry rather than
only here, because an alias that outlives its reason is how a legacy surface is born. This is
the same surface `docs/TODO.md` item 23 is waiting to delete.

## Consequences

- **The suite gained the layer that would have caught it.** F-39 is the case for the consumer
  contract layer — every other layer was green while a shipped client could not sign in. See
  [ADR 0013](0013-consumer-expectations-in-the-backend-repo.md).
- `AdminApiPathSplitTests.theLegacyIdentityPathAnswersWhatTheAdminPathAnswers` asserts the alias
  **against the admin path's own response** rather than against a literal, so the two cannot
  drift apart without failing. `theLegacyIdentityPathRefusesAnAnonymousCallerAndAStudent` pins
  the guard. Both were watched fail with `404 {"message":"Not found","success":false}` first.
- The guard is not incidental: deleting the `SecurityConfig` matcher makes
  `ApiAuthorizationMatrixTests.everyRouteIsEitherDeclaredPublicOrRefusesAnonymousCallers` name
  `GET /auth/me -> 500`. That was proved by doing it and reverting.
- **This alias is on a list to be deleted, and the list has to be read.** When the panel
  confirms the migration, the route, its matcher, both tests and this record go together —
  `docs/TODO.md` item 23.
- The same pass found the mirror-image case, `POST /answers/report` (F-40): a route the app had
  always called and this API had never served. Restoring one and adding the other are the same
  decision seen from two sides — the client's call list, not the server's route list, is what
  says whether a path is needed.

## Sources

[F-39](../test-findings.md#f-39--get-authme-was-unmapped-and-the-admin-panel-could-not-sign-anybody-in);
[worklog.md](../worklog.md) — *`GET /auth/me` — gone, and it had locked the panel out*, which
records "**Stopped and asked**, per the brief; the decision was to restore it as a legacy alias";
`CHANGELOG` 9.09 (15), which carries the expiry condition; `docs/TODO.md` — the original
deletion rationale under *Done recently*, and item 23.
