# Consumer findings — `admin-web`

Risks found while writing [adminweb-consumer-contract.md](adminweb-consumer-contract.md). **Nothing was
changed.** This is a reading of the client as it stands at commit `b49a84b`
(branch `hard-refactoring`, 2026-09-09), recorded so the backend can decide what to
test and what to warn us about.

Every claim carries a file and a line. Where the source does not settle a question, the
entry says **uncertain** and says why.

Severity is about what an operator would experience, not about how hard it is to fix.

---

## Summary

| # | Finding | Severity |
| --- | --- | --- |
| [C-01](#c-01--get-authme-may-already-be-gone-and-the-page-load-path-swallows-the-404) | `GET /auth/me` may already return `404`; the page-load path ignores it | **Blocker if confirmed** |
| [C-02](#c-02--the-client-sends-no-admin-prefix-on-any-of-its-42-paths) | No `/admin` prefix on any of 42 paths | Major |
| [C-03](#c-03--405-and-415-are-invisible-to-every-call-site) | `405` and `415` are indistinguishable from any other 4xx | Minor |
| [C-04](#c-04--path-ids-are-never-escaped) | Path ids are interpolated raw, never escaped | Minor |
| [C-05](#c-05--34-of-42-calls-branch-on-no-status-at-all) | 34 of 42 calls branch on no status at all | Medium |
| [C-06](#c-06--four-calls-swallow-every-failure-404-included) | Four calls swallow every failure, `404` included | Medium |
| [C-07](#c-07--nested-objects-are-read-without-a-null-check) | Nested objects read without a null check | Medium |
| [C-08](#c-08--open-maps-are-enumerated-without-a-presence-check) | `changes`, `metadata`, `counts` enumerated without a presence check | Medium |
| [C-09](#c-09--path-and-field-names-are-spread-across-19-files) | Paths and response keys are spread across 19 files | Medium |
| [C-10](#c-10--the-compiled-in-audit-action-list-is-three-values-behind) | The compiled-in audit action list is three values behind | Low |
| [C-11](#c-11--the-actortype-filter-is-built-and-never-populated) | The `actorType` filter is built and never populated | Low |
| [C-12](#c-12--promiseall-turns-one-failed-read-into-a-dead-screen) | `Promise.all` turns one failed read into a dead screen | Low |
| [C-13](#c-13--expiresat-is-not-read-so-the-panel-cannot-warn-before-a-session-ends) | `expiresAt` is not read | Low |
| [C-14](#c-14--a-stale-comment-claims-the-professor-read-carries-no-assignment) | A stale comment contradicts the code beside it | Cosmetic |

---

## 1. Places that ignore the status and look at the body

**This class of bug is not present.** It is worth stating plainly, because it is the
thing the backend most needs to know.

The panel has **one** response reader, `ApiResponseParser.parse`
(`src/api/client/apiResponse.ts:86-115`), and it checks the HTTP status **before** it
looks at anything in the body:

```
if (!response.ok) throw await this.toResponseError(response, path)   // :87-89
```

Only after that does it test `body.success === false`
(`src/api/client/apiResponse.ts:27-30`, `:106-112`). A real `404` or `409` therefore
becomes a `NotFoundError` / `ConflictError` and reaches the call site as a rejected
promise. Nothing is swallowed by the parser.

Nothing outside `src/api/client/` reads a `success` field off a response. Verified by
searching `src/` for `.success`, `success ===` and `success:` — the only hits outside
the client are comments, the `BasicResponse`/`GitlabIssueResponse` type declarations,
and `parsed.success` in `src/context/authSessionStorage.ts:94`, which is a Zod
`safeParse` result on `localStorage`, not an HTTP response.

**Consequence for the backend's migration from `200 {success:false}` to real `404`/
`409`:** the panel already handles both shapes. The *messages* it shows will change
slightly — a `RefusedError` and a `NotFoundError` both surface `body.message` — but no
failure gets swallowed either way.

**One caveat.** The switch is not free at the call sites listed in
[C-05](#c-05--34-of-42-calls-branch-on-no-status-at-all): a `404` arriving where a
`200 {success:false}` used to arrive is still reported to the operator, but with the
same generic wording. Two call sites *do* change behaviour, and both change for the
better:

- `GET /users/{id}` — a `404` now renders "this account is gone" instead of a generic
  error (`src/features/users/hooks/useUserProfile.ts:29-32`).
- `POST /audit-logs/{id}/revert` — a `409` now yields a specific sentence read from
  `reason` (`src/features/audit-logs/auditLogModel.ts:140-143`).

---

## 2. Findings

### C-01 — `GET /auth/me` may already be gone, and the page-load path swallows the 404

**Severity: blocker if confirmed. Uncertain — two documents in this repository
contradict each other, and the backend source is not here to settle it.**

The client calls `GET /auth/me` at `src/api/auth/AuthSessionService.ts:26`.

`docs/adminweb-tasks-from-backend.md:81-83` states, in the backend team's own words:

> `GET /auth/me` → `GET /admin/auth/me` — **nothing to migrate: the old path is gone.**
> It was removed from the backend and is not coming back, so a panel still calling it
> gets a `404`.

`docs/admin-api.md` supports this indirectly: it documents only `GET /admin/auth/me`
(line 181), and its path-migration table (lines 41-49) lists `/auth/request-login`,
`/auth/login`, `/auth/logout` and `/auth/logout-all` as still-served legacy paths —
**`/auth/me` is not in that table.**

But `docs/testfindings.md:15` (F-005) says the panel "still sends `/users`,
**`/auth/me`**, `/audit-logs` … Nothing is broken today", and cites the backend's dual
`@RequestMapping` annotations as evidence.

Both cannot be right. If the first is right, this is what happens:

**On page load** — `src/context/AuthContext.tsx:88-93`:

```
.catch((error: unknown) => {
  if (!cancelled && error instanceof UnauthorizedError) expireSession()
})
```

A `404` is a `NotFoundError`, not an `UnauthorizedError`, so **nothing happens**. The
stored session stays; the sidebar keeps rendering the name captured at sign-in; a name
or role changed on the backend never propagates
(`src/context/AuthContext.tsx:76-79` describes exactly the behaviour that would stop
working). The operator is told nothing.

**After a login** — `src/features/auth/authenticateAdmin.ts:32-42`:

```
try { return (await authService.me()).user ?? null } catch { return null }
```

A bare `catch`. `null` reaches `resolveAdminUser`
(`src/features/auth/authenticateAdmin.ts:26-30`), which throws
`IdentityUnavailableError`, which **fails the entire sign-in** with "Signed in, but
your admin account could not be read. Try again in a moment."
(`authenticateAdmin.ts:14-15`). The code was accepted, the token was minted, and the
operator cannot get in.

**What the backend can do:** answer whether the legacy `GET /auth/me` is still routed.
If it is not, this is the highest-priority item in this document — it is not a
degradation, it is a locked-out panel. If it is, `docs/adminweb-tasks-from-backend.md`
needs correcting, because it is being read as authoritative.

### C-02 — The client sends no `/admin` prefix on any of its 42 paths

**Severity: major. Already known on both sides; restated here with the exact
inventory, because a sweep should be able to check it mechanically.**

`ApiRequestBuilder.url()` concatenates base URL and literal path with no prefixing
(`src/api/client/apiRequest.ts:70-72`). The base URL is `/api` in development, which
the Vite proxy **strips** before forwarding
(`vite.config.ts:53`, `rewrite: (path) => path.replace(/^\/api/, '')`), and a bare
origin in production (`.gitlab-ci.yml:13`, `Dockerfile:14`). So the backend receives
the literal service-file path.

A search of `src/` for the string `/admin` returns no HTTP path — only route constants
for the panel's own URLs.

All 42 paths are in Table 1 of [adminweb-consumer-contract.md](adminweb-consumer-contract.md). Removing
the legacy routes breaks every screen simultaneously.

Already tracked as `H2` in `docs/backend-tasks.md:221-241` and `F-005` in
`docs/testfindings.md:15`. Nothing new is asked for; it is listed here so that a
backend-side sweep of this contract does not conclude the panel has migrated.

### C-03 — `405` and `415` are invisible to every call site

**Severity: minor, and the fix is on our side.**

The status→type table (`src/api/client/apiErrors.ts:183-194`) has rows for 400, 401,
403, 404, 409 and 429. Everything else — 405, 415, 500, 503 — becomes a plain
`ResponseError` carrying `status`. Nothing outside `src/api/client/` reads `.status`
off an error; verified by searching `src/` for `.status` outside the API and domain
layers, which returns only UI reads of a *record's* `status` field.

Concretely:

- **`405` with an `Allow` header:** the header is never read. `ApiResponseParser`
  reads exactly one response header, `Retry-After`
  (`src/api/client/apiResponse.ts:14`, `:37-42`). An operator hitting a
  wrong-verb route sees `body.message`, or the fallback
  "Request to /x failed with status 405" (`src/api/client/apiResponse.ts:128`).
  This is an improvement over the old `404`, which the audit-log screens would have
  reinterpreted as "this backend has no audit endpoints"
  (`src/features/audit-logs/auditLogModel.ts:153-155`) — but nothing exploits the
  `Allow` header.
- **`415`:** same. The client sends `Content-Type: application/json` whenever a body is
  present (`src/api/client/apiRequest.ts:106`), so a `415` from this client would
  indicate a route that does not accept JSON at all, not a client mistake. Worth noting
  for the sweep: **five calls send no `Content-Type` at all**, because
  `JSON.stringify(undefined)` is `undefined` and the header is conditional on
  `init.body !== undefined`:
  `POST /auth/logout` (`src/api/auth/AuthSessionService.ts:14`),
  `POST /audit-logs/{id}/revert` (`src/api/logs/AuditLogRevertService.ts:11`),
  `POST /reports/{id}/gitlab-issue` (`src/api/feedback/GitlabIssueService.ts:11`),
  `PATCH /users/{id}/block` (`src/api/users/UserAccountsService.ts:77`),
  `PATCH /users/{id}/unblock` (`src/api/users/UserAccountsService.ts:85`).
  If any of those routes requires a `Content-Type` header, they will now answer `415`
  where they previously answered `500` — and the panel will report it as an opaque
  failure. **This is the one place the 500→415 change could bite, and it is worth a
  backend test.**

### C-04 — Path ids are never escaped

**Severity: minor.**

Every parameterised path is a raw template literal — `` `/users/${id}` ``
(`src/api/users/UserAccountsService.ts:49`), `` `/${this.resource}/${id}` ``
(`src/api/moderation/ContentService.ts:31`), and 15 others. There is no
`encodeURIComponent` anywhere in `src/`; the only hit in the repository is inside a
test's expected URL (`src/api/logs/AuditLogsService.unit.test.ts:80`).

Every id the panel sends comes from a backend response, so in practice they are UUIDs
and this never fires. It matters for the backend's "malformed or missing id now answers
`400` instead of `500`" change in one specific way: an **empty** id would produce
`/users/` rather than `/users//`, which is a different route, and an id containing `/`
would silently address a different path. Neither is reachable from the current UI —
`useUserProfile` is disabled while `userId` is `null`
(`src/features/users/hooks/useUserProfile.ts:59`) and the warning controls are hidden
when the warning's id is `null`
(`src/features/users/hooks/useUserProfileActions.ts:117`, `:123`).

Recorded rather than raised: it is a latent hazard, not a live one.

### C-05 — 34 of 42 calls branch on no status at all

**Severity: medium.**

Only eight call sites inspect what kind of failure they got. They are, exhaustively:

| Call site | Status | File and line |
| --- | --- | --- |
| Login code request | `429` | `src/features/auth/authenticateAdmin.ts:59-61` |
| Login | `403` | `src/features/auth/authenticateAdmin.ts:74-79` |
| Session check (page load) | `401` | `src/context/AuthContext.tsx:92` |
| Profile read | `404` | `src/features/users/hooks/useUserProfile.ts:30` |
| Audit log read | `404` | `src/features/audit-logs/auditLogModel.ts:154` |
| Audit meta read | `404` | same branch, via `useAuditLogs.ts:67` |
| Audit revert | `409` + `reason` | `src/features/audit-logs/auditLogModel.ts:141` |
| Query retry predicate | `>= 500` | `src/query.ts:27-30` (global, not per call) |

Every other call — all 16 mutations, plus the moderation, catalogue, ratings,
bug-report and system-status reads — renders `getErrorMessage(error, fallback)`
(`src/utils/getErrorMessage.ts:32-36`), which is the backend's `message` verbatim, or a
per-screen fallback when the body explained nothing.

This is a deliberate design (`docs/adr/0013-errors-are-types-not-status-codes.md`) and
it is not wrong. What it means for the backend:

- **`4xx` is uniformly handled and uniformly uninformative.** A `400` from a validation
  rule, a `403` from an authorization guard and a `409` from a conflict all render the
  same way. The panel is entirely dependent on `body.message` being a sentence an
  operator can act on. Two are already relied on by name in a comment:
  `A cursor requires a limit` and `Invalid user status`
  (`src/features/users/hooks/useUsersPage.ts:132-133`).
- **`403` on a write is not distinguished from any other refusal.** The panel hides the
  control instead (`src/features/users/model/access.ts:42-53`), which is a *usability*
  gate; if the backend refuses something the panel offered, the operator sees a generic
  message.
- **`reason` is read on exactly one endpoint.** `ApiError.reason` is populated for
  every failure (`src/api/client/apiResponse.ts:133`,
  `src/api/client/apiErrors.ts:39`) and read only for the audit revert. A `reason` code
  added to any other endpoint would be parsed and then discarded.

### C-06 — Four calls swallow every failure, `404` included

**Severity: medium — these are the places where a moved or removed route fails
silently.**

| Call | Where | What is swallowed |
| --- | --- | --- |
| `POST /auth/logout` | `src/context/AuthContext.tsx:50-57` | Everything. Deliberate: signing out locally is safer than staying signed in. |
| `GET /auth/me` on page load | `src/context/AuthContext.tsx:88-93` | Everything except `401`. See [C-01](#c-01--get-authme-may-already-be-gone-and-the-page-load-path-swallows-the-404). |
| `GET /auth/me` after login | `src/features/auth/authenticateAdmin.ts:32-42` | Bare `catch`; converted into a failed sign-in. |
| `GET /system/status` (users table) | `src/features/users/hooks/useUsersPage.ts:35-41` | Everything. Counters render as dashes. |
| `GET /system/status` (bug reports) | `src/features/feedback/hooks/useFeedbackReports.ts:15-21` | Everything. `gitlabEnabled` becomes `false`, so the "Create issue" button silently disappears. |

The last one is the one worth a backend test: if `/system/status` moves or breaks, the
GitLab escalation feature vanishes from the UI with no error anywhere. There is no
diagnostic path for an operator asking "why is the button gone?".

### C-07 — Nested objects are read without a null check

**Severity: medium. These would throw a `TypeError` inside React, which
`getErrorMessage` deliberately refuses to show to the operator
(`src/utils/getErrorMessage.ts:12-18`, `:34`) — so the failure mode is a blank screen
or a caught error boundary, not a message.**

`expectArray` verifies only that the field **is an array**; the element type is
asserted, never checked (`src/api/client/apiResponse.ts:51-53`, `:61-69`). So every
field inside an element is an unvalidated assumption.

Reads with no guard, where the backend could plausibly send `null`:

| Read | Where | If the field is null/absent |
| --- | --- | --- |
| `log.actor.name`, `.email` | `src/features/audit-logs/components/AuditLogRow.tsx:41`, `:44` | `TypeError` on every row of the audit table |
| `log.actor.name`, `.email`, `.role` | `src/features/audit-logs/components/details/ActorSummary.tsx:19`, `:22` | `TypeError` in the detail modal |
| `item.author.name` | `src/features/moderation/model/content.ts:108` | `TypeError` on any search in the Comments table |
| `item.author.role/.warnings/.status` | `src/features/moderation/components/ContentRow.tsx:63-65` | `TypeError` per row |
| `report.reportedUser.name`, `report.reporter.name` | `src/features/moderation/model/reports.ts:75-76` | `TypeError` on any search in the Reports tab |
| `user.id.slice(0, 8)` | `src/features/users/components/ProfileSummary.tsx:42` | `TypeError` if `id` is not a string |
| `new Date(user.joined)` | `src/features/dashboard/components/RecentRegistrations.tsx:18` | Guarded — `NaN` sorts last (`:19`) |

Reads that **are** guarded, for contrast — this is the pattern the rest should follow:

- `log.target` — null-checked in both places it is used
  (`src/features/audit-logs/components/AuditLogRow.tsx:53`,
  `details/AuditLogDetails.tsx:35-41`); the domain type declares it nullable
  (`src/domain/auditLog.ts:105`).
- `rating.author?.name?.trim() || 'Unknown'` — `src/api/catalog/ratingMapper.ts:19`.
- `warning.createdFrom?.id ?? ''` and `createdFrom?.name?.trim() || 'Unknown'` —
  `src/api/users/userWarningMapper.ts:30-31`.
- `counts` on `/system/status` — `null` and absent both stay `null`, never zero
  (`src/api/system/systemStatusMapper.ts:19-27`).

**The asymmetry is the finding.** Ratings and warnings have defensive mappers; audit
actors and moderation authors do not, and they are nested exactly the same way. If the
backend can ever send `actor: null` (a system-initiated audit entry, say) or
`author: null` (content by a deleted account), the audit and moderation screens go
blank.

### C-08 — Open maps are enumerated without a presence check

**Severity: medium.**

Three response fields are read as free-form maps and enumerated directly:

- `log.changes` — `Object.keys(log.changes)`
  (`src/features/audit-logs/components/AuditLogRow.tsx:26`) and
  `Object.entries(changes)` (`details/AuditChangeList.tsx:30`). The mapper
  `toAuditLog` fills in `revertible`, `revertBlockedReason` and `revertedByAuditId`
  but **not** `changes` (`src/api/logs/auditLogMapper.ts:13-20`). An entry served
  without a `changes` key throws `TypeError: Cannot convert undefined or null to
  object` on **every row of the audit table**, not just in the detail view.
- `log.metadata` — `Object.entries(metadata)`
  (`src/features/audit-logs/components/details/MetadataList.tsx:13`). Same exposure,
  detail modal only. Also not defaulted by the mapper.
- `rating.scores` — guarded: `rating.scores ?? {}`
  (`src/api/catalog/ratingMapper.ts:21`). This is the pattern the two above lack.

`counts` on `/system/status` is also an open map and is handled correctly
(`src/api/system/systemStatusMapper.ts:19-27`).

**For the backend:** `changes` and `metadata` must be present on every audit entry —
empty objects are fine, absent keys are not. Worth pinning in a test, because an entry
type that legitimately has neither (a sign-in, a deletion) is exactly the kind of thing
a serializer would omit.

### C-09 — Path and field names are spread across 19 files

**Severity: medium — a rename on the backend has 19 places to break, and TypeScript
catches none of them.**

There is no central path table. Every path is a string literal in the service that
calls it. The 42 calls live in these 19 files:

`AuthSessionService.ts`, `LoginCodeService.ts`, `TokenLoginService.ts`,
`UserAccountsService.ts`, `UserWarningsService.ts`, `ContentService.ts`,
`ReportsService.ts`, `CommentsService.ts`, `AnswersService.ts`,
`LectureCatalogService.ts`, `ProfessorCatalogService.ts`, `RatingReadService.ts`,
`RatingDeletionService.ts`, `AuditLogReaderService.ts`, `AuditLogMetaService.ts`,
`AuditLogRevertService.ts`, `SystemStatusService.ts`, `BugReportsService.ts`,
`GitlabIssueService.ts`.

Three things are centralised and worth crediting: the base URL
(`src/api/client/ApiClient.ts:138`), the page-size vocabulary
(`src/api/client/paging.ts:11-17`), and the `/system/status` counter names, which are
lifted out of the open map in exactly one place
(`src/api/system/systemStatusMapper.ts:19-27`) precisely because a rename once broke
two features (`src/api/system/systemStatusTypes.ts:31-35`).

**What breaks on a rename, and how loudly:**

| Rename | Where it breaks | How it is detected |
| --- | --- | --- |
| A path segment | The one service that spells it | **Runtime `404` only.** No compile error. |
| A **list key** — `users`, `comments`, `answers`, `lectures`, `professors`, `ratings`, `auditLogs`, `bugReports`, `warnings`, `actors`, `actions`, `targetTypes` | The `expectArray` call in that service | **`ContractError` with a readable message** — the good case (`src/api/client/apiResponse.ts:61-69`) |
| A field **inside** a list element | Everywhere it is rendered | **Silent.** `expectArray` checks arrayness only; the element type is cast, not verified (`apiResponse.ts:51-53`). A renamed `username` renders as an empty cell. |
| The resource noun `comments` / `answers` | Both the path **and** the response key at once, because they are the same string (`src/api/moderation/ContentService.ts:17-26`, `ReportsService.ts:13-25`) | `ContractError` |

The last row is a deliberate coupling and is documented as such
(`src/api/moderation/ContentService.ts:8-11`). It is worth the backend knowing: the
panel assumes `GET /comments` returns the list under `comments`, `GET /answers` under
`answers`, and — less obviously — **`GET /comments/reported` also returns its list
under `comments`, not under `reports`** (`src/api/moderation/ReportsService.ts:25`).

`docs/adr/0008-generated-schema-is-the-contract-authority.md` is where this repository
records the intended fix (a generated schema). It is not built yet.

### C-10 — The compiled-in audit action list is three values behind

**Severity: low — the runtime is fine, the type is stale.**

`src/domain/auditLog.ts:11-36` lists 24 administrative actions.
`docs/admin-api.md:821-828` lists 27. Missing from the client:

- `BUG_REPORT_ISSUE_CREATED`
- `LECTURE_CREATED`
- `PROFESSOR_CREATED`

Also absent are the student-activity actions (`USER_LOGIN`, `COMMENT_CREATED`,
`RATING_SUBMITTED`, …) and the refusals (`LOGIN_REFUSED`, `ACCESS_REFUSED`), which
`/audit-logs/meta` may still report — `docs/backend-tasks.md:212-215` says so
explicitly.

**This does not break anything at runtime**, and it is worth saying why, because a
naive reading of the constant suggests otherwise:

- The filter dropdown is built from `meta.actions`, the backend's list, not from the
  constant (`src/features/audit-logs/components/AuditSelectFilters.tsx:55`,
  `src/features/audit-logs/auditLogModel.ts:49-51`).
- An unknown action renders with a generic label — `enumLabel` is a pure string
  transform (`src/utils/enumLabel.ts:2-7`) — and a default colour, since
  `auditActionColor` falls through a partial table to `'blue'`, or `'red'` for anything
  ending in `_DELETED` (`src/constants/colors.ts:146-156`, `:172-177`).
- `AUDIT_ACTIONS`, `AUDIT_TARGET_TYPES` and `AUDIT_ACTOR_TYPES` are used **only** to
  derive the type aliases. No runtime code validates against them; verified by
  searching `src/` for each name, which returns only `src/domain/auditLog.ts`,
  the re-export in `src/api/logs/index.ts:2`, and a unit test.

So: **carried, not broken**. The cost is that `AuditAction` is a union that does not
match reality, so `AUDIT_ACTION_COLORS` (`src/constants/colors.ts:146`) cannot be given
a row for a new action until the constant is updated — which is the compile-time prompt
the comment at `src/constants/colors.ts:142-145` says it wants, working in reverse.

`AUDIT_TARGET_TYPES` (13 values, `src/domain/auditLog.ts:43-57`) matches
`docs/admin-api.md:833-835` exactly. `AUDIT_ACTOR_TYPES` matches too.

### C-11 — The `actorType` filter is built and never populated

**Severity: low.**

`toLogQueryParams` builds an `actorType` parameter
(`src/api/logs/logQueryParams.ts:26`), and `AuditLogFilters` declares it
(`src/domain/auditLog.ts:145`). But `FilterDraft` — the form's working copy — has no
`actorType` field (`src/features/audit-logs/auditLogModel.ts:21-28`), and
`toAuditFilters` therefore never sets it (`auditLogModel.ts:69-78`). It is always
`undefined` and always dropped by the client (`src/api/client/apiRequest.ts:88`).

Symmetrically, `meta.actorTypes` is read out of the response
(`src/api/logs/AuditLogMetaService.ts:21`) and then never rendered. Verified: searching
`src/` for `actorType` outside `src/api/` and `src/domain/` returns only two test
fixtures.

**For the backend:** the `actorType` query parameter on `GET /audit-logs` currently has
no client. Do not treat its presence in this repository's types as evidence that it is
exercised.

### C-12 — `Promise.all` turns one failed read into a dead screen

**Severity: low.**

Three screens fan out and fail together:

- **Comments** — `commentsService.getAll()` and `answersService.getAll()`
  (`src/features/comments/CommentsPage.tsx:26`), and separately
  `getReported()` for both (`:30-33`). A failure of any one of the four fails the
  whole screen (`src/features/moderation/hooks/useModerationPage.ts:52-53`,
  `src/features/moderation/ModerationPage.tsx:49-50`).
- **Catalogue** — lectures and professors are two queries, but the error is combined
  with `??` and either one blanks the page
  (`src/features/catalog/hooks/useCatalogLists.ts:34`, `:46-51`).
- **Profile modal** — `getById` and `getWarnings`
  (`src/features/users/hooks/useUserProfile.ts:24-27`). A `404` from *either* renders
  the profile as "account gone" (`:30`), including the case where the account exists
  and only its warnings endpoint answered `404`.

That last one is the sharpest: a `404` on `GET /users/{id}/warnings` for an account
that exists would tell the operator the account is deleted.

The dashboard is the counter-example done right: two independent queries, half the page
arriving is an accepted outcome (`src/features/dashboard/hooks/useDashboardData.ts:30-36`).

### C-13 — `expiresAt` is not read, so the panel cannot warn before a session ends

**Severity: low. Reported because `docs/adminweb-tasks-from-backend.md:141-146` asks
for it and it has not been done.**

Searching `src/` for `expiresAt` returns nothing. `TokenLoginService` reads exactly one
field off the login response, `authToken`
(`src/api/auth/TokenLoginService.ts:23-27`).

The panel therefore discovers an expired session as a `401` on whatever request happens
to be next — which does route back to login
(`src/api/client/ApiClient.ts:116-118`, `src/context/AuthContext.tsx:64`) with a
readable message (`src/features/auth/hooks/useLoginFlow.ts:32-33`), but only after the
operator has lost whatever they were typing. That is precisely the failure the backend
added the field to prevent.

`sessionType` is likewise absent from `src/`, which is the desired end state
(`docs/adminweb-tasks-from-backend.md:89-90`) — the panel simply never sent it.

### C-14 — A stale comment claims the professor read carries no assignment

**Severity: cosmetic. No behaviour is wrong; the prose contradicts the code beside
it.**

`src/features/catalog/model/catalog.ts:20-24`:

> Which lectures a professor teaches. The professor endpoint does not report the
> assignment, but the lecture endpoint lists each lecture's professors, so the reverse
> index is built from the lectures the panel already loaded.

The same claim appears at `src/features/catalog/model/edit.ts:47-49`.

But `Professor.lectureIds` is declared (`src/api/catalog/catalogTypes.ts:37`), read
directly from the response, and used to render the assignment
(`src/features/catalog/components/ProfessorTableRow.tsx:32-34`) and to seed the edit
form (`src/features/catalog/CatalogPage.tsx:126`). `docs/admin-api.md:637`, `:647`
confirms `GET /data/professor` serves `lectureIds`.

The comment is also attached to `matchesLectureQuery`, which does not build a reverse
index at all — it searches lecture names, codes and professor names
(`src/features/catalog/model/catalog.ts:25-31`).

Recorded rather than fixed, per the instruction not to change code.

---

## 3. The nine backend changes, checked one by one

Each is marked **addressed** / **not addressed** / **not applicable**, with evidence.
"Not applicable" means the panel does not call the affected endpoint at all — which is
itself a finding worth reporting, since the backend cannot see that from its side.

### 3.1 — The missing slash in the vote endpoint path was fixed; the old path now answers 404

**Not applicable.**

The admin panel calls no vote endpoint. A case-insensitive search for `vote` across
`src/` returns **zero** hits; the only occurrences in the repository are in
`docs/adr/0003-remove-the-system-status-screen.md:13` (the word "devoted") and three
prose mentions in `docs/admin-api.md` (lines 406, 481, 874) describing cascade
behaviour and what is not audited.

The full call inventory is Table 1 of [adminweb-consumer-contract.md](adminweb-consumer-contract.md);
no entry addresses votes. Votes are a student-app concern; this panel deletes content
and the backend cascades the votes with it
(`src/api/moderation/CommentsService.ts:18-21`).

**Nothing to verify on this client. The Android repository is where this change lands.**

### 3.2 — Many failures now answer 404 or 409 instead of `200 {success:false}`

**Addressed.**

The response parser checks the HTTP status before the body, in one place, for every
endpoint: `src/api/client/apiResponse.ts:87-89`, then `:106-112`. Both shapes are
raised as errors — `ResponseError` subclasses for a failing status
(`src/api/client/apiErrors.ts:206-216`), `RefusedError` for a 2xx that declines
(`src/api/client/apiErrors.ts:171-176`).

No code outside `src/api/client/` reads a `success` field off a response; see section 1
above for the search that establishes this.

Two call sites get *better* under the change:

- `GET /users/{id}` — `src/features/users/hooks/useUserProfile.ts:29-32` now gets a
  real "gone" signal.
- `POST /audit-logs/{id}/revert` — `src/features/audit-logs/auditLogModel.ts:140-143`
  reads `reason` off a `409` and maps it to one of five sentences
  (`auditLogModel.ts:108-116`).

Caveat: at the other 34 call sites the change is invisible — the operator sees
`body.message` either way. See [C-05](#c-05--34-of-42-calls-branch-on-no-status-at-all).

### 3.3 — A wrong HTTP verb now answers 405 with an `Allow` header, not 404

**Addressed for the status; the `Allow` header is not addressed.**

- **Status:** a `405` reaches the call site as a `ResponseError` with `status: 405`
  (`src/api/client/apiErrors.ts:212-215`) and is reported with `body.message` or the
  fallback at `src/api/client/apiResponse.ts:128`. Nothing crashes.
- **`Allow` header: not read.** `ApiResponseParser` reads exactly one header,
  `Retry-After` (`src/api/client/apiResponse.ts:14`, `:37-42`). There is no code path
  that could surface `Allow`.
- **This change fixes a real latent misreport.** Under the old behaviour, a
  wrong-verb `404` on `/audit-logs` or `/audit-logs/meta` would have been reinterpreted
  by `getAuditErrorMessage` as "The configured backend does not support audit logs yet.
  Deploy the audit API endpoints, then try again."
  (`src/features/audit-logs/auditLogModel.ts:14-15`, `:153-155`) — a wrong and
  actively misleading diagnosis. A `405` is not `NotFoundError`, so that branch no
  longer fires. Same for `GET /users/{id}`, which would have rendered a live account as
  deleted (`src/features/users/hooks/useUserProfile.ts:30`).

**Net: the change helps this client. No work needed; a small improvement available if
we ever surface `Allow`.**

### 3.4 — An unsupported media type now answers 415 instead of 500

**Addressed, with one exposure worth a backend test.**

- The client sets `Content-Type: application/json` on every request that carries a body
  (`src/api/client/apiRequest.ts:104-107`), so a well-formed JSON write should never
  see a `415`.
- A `415` would arrive as a plain `ResponseError` with `status: 415` and be reported
  with `body.message`. No crash, no special wording.
- **The retry behaviour improves.** `500` reports `transient: true`
  (`src/api/client/apiErrors.ts:102-104`), so a `500` on a **query** was retried twice
  before the operator was told — three round trips that could not succeed
  (`src/query.ts:27-30`). `415` is below 500 and is not retried. Writes were never
  retried either way (`src/query.ts:43`).
- **The exposure:** five calls send **no** `Content-Type` header at all, because they
  send no body and the header is conditional
  (`src/api/client/apiRequest.ts:106`; `JSON.stringify(undefined)` is `undefined`):
  `POST /auth/logout`, `POST /audit-logs/{id}/revert`,
  `POST /reports/{id}/gitlab-issue`, `PATCH /users/{id}/block`,
  `PATCH /users/{id}/unblock`. If the new media-type check treats an absent
  `Content-Type` on a `POST`/`PATCH` as unsupported, all five start failing —
  and `POST /auth/logout` fails **silently**, because its failures are swallowed
  (`src/context/AuthContext.tsx:50-57`), leaving a token live on the backend after the
  operator believes they signed out.

**Request to the backend: please confirm those five bodyless writes are accepted
without a `Content-Type` header, and pin it in a test.**

### 3.5 — A missing required query parameter now answers 400 instead of 500

**Addressed; no case identified where this client would trigger it.**

The client drops `undefined`, `null` **and the empty string** from the query string
(`src/api/client/apiRequest.ts:85-94`). So an "absent" parameter is genuinely absent
rather than sent empty.

Auditing the four endpoints that take query parameters:

| Endpoint | Parameters | Could one required parameter be missing? |
| --- | --- | --- |
| `GET /users` | `limit`, `cursor`, `q`, `status`, `role`, `sort` | `cursor` is never sent without `limit` — the table always sends `limit: 50` (`src/features/users/hooks/useUsersPage.ts:77`). `getAll()` sends **nothing at all** (`src/api/users/UserAccountsService.ts:39-41`), which is the documented way to get administrators-first ordering. |
| `GET /ratings` | `limit`, `cursor`, `lectureId` | `limit: 50` always sent from the ratings tab (`src/features/catalog/hooks/useRatings.ts:26`). |
| `GET /audit-logs` | `limit`, `cursor`, `q`, `actorId`, `actorType`, `action`, `targetType`, `from`, `to` | `limit` is hard-wired to `50` and cannot be omitted (`src/api/logs/logQueryParams.ts:19`). The first page is fetched through a one-argument call so no `cursor` key exists (`src/features/audit-logs/hooks/useAuditLogs.ts:54-57`). |
| all others | none | — |

The backend's own `A cursor requires a limit` refusal is already expected and shown
verbatim (`src/features/users/hooks/useUsersPage.ts:132-133`).

The one improvement: a `400` is not `transient` and is not retried, whereas a `500` was
retried twice (`src/query.ts:27-30`). So a malformed filter now reports faster.

**No change needed. Nothing here is a live risk — but I could not verify which
parameters the backend now considers *required*, so this rests on the client's side of
the contract only.**

### 3.6 — A malformed or missing id now answers 400 instead of 500

**Addressed; no case identified where this client would trigger it.**

Every id the panel puts in a path came out of a backend response — row keys from
`users[].id`, `comments[].id`, `auditLogs[].id`, and so on (Table 2 of the contract).
There is no free-text id input anywhere in the UI.

The two nullable ids are both guarded before they reach a path:

- `warnings[].id` is `string | null`; when it is `null` the edit and withdraw controls
  return `Promise.resolve()` without calling anything
  (`src/features/users/hooks/useUserProfileActions.ts:116-127`, and the type at
  `src/api/users/userTypes.ts:17`).
- The profile read is disabled while `userId` is `null`
  (`src/features/users/hooks/useUserProfile.ts:59`).

The residual hazard is [C-04](#c-04--path-ids-are-never-escaped) — ids are never
URL-escaped — which is latent rather than live.

As with 3.5, the improvement is that a `400` is not retried while a `500` was.

### 3.7 — `professorIds` is now optional when creating a lecture

**Not applicable.**

**The panel never creates a lecture.** `LectureCatalogService` implements exactly three
calls — `GET /data/lectures`, `PATCH /data/lectures/{id}`, `DELETE /data/lectures/{id}`
(`src/api/catalog/LectureCatalogService.ts:13`, `:23`, `:28`) — and there is no `POST`
anywhere in `src/api/catalog/`. Neither is there a create control in the catalogue UI:
`CatalogPage.tsx` offers edit and delete only.

The same holds for `POST /data/professor` — `ProfessorCatalogService.ts:13`, `:23`,
`:28` are the only three calls.

`professorIds` **is** sent, but only on `PATCH /data/lectures/{id}`
(`src/api/catalog/LectureCatalogService.ts:23`), and only when the assignment set
actually changed (`src/features/catalog/model/edit.ts:88`). On a PATCH it has always
been optional — the whole body is a diff
(`src/api/catalog/catalogTypes.ts:46-61`).

**Related, and worth flagging even though it is out of scope for this item:**
`docs/adminweb-tasks-from-backend.md:116-123` warns that the two catalogue creates now
answer a plain `403` where `POST /data/professor` used to answer
`200 {"success": false}`, and asks the panel to handle the status. **That request does
not apply to this repository — the panel calls neither create.** The backend can close
that checkbox.

### 3.8 — New values were added to the audit action list

**Addressed at runtime, not addressed in the type.** Full analysis in
[C-10](#c-10--the-compiled-in-audit-action-list-is-three-values-behind).

- **Rendering:** an unknown action renders correctly. `enumLabel` is a pure string
  transform (`src/utils/enumLabel.ts:2-7`) and `auditActionColor` falls through to a
  default (`src/constants/colors.ts:172-177`).
- **Filtering:** the dropdown is built from `meta.actions` — the backend's list — and
  the `action` query value is validated against that, not against the compiled-in
  constant (`src/features/audit-logs/auditLogModel.ts:49-51`,
  `src/features/audit-logs/components/AuditSelectFilters.tsx:55`).
- **Stale constant:** `AUDIT_ACTIONS` (`src/domain/auditLog.ts:11-36`) has 24 entries;
  `docs/admin-api.md:821-828` lists 27. Missing: `BUG_REPORT_ISSUE_CREATED`,
  `LECTURE_CREATED`, `PROFESSOR_CREATED`. It is used only to derive the `AuditAction`
  type — no runtime code validates against it (verified by searching `src/`).
- `AUDIT_TARGET_TYPES` and `AUDIT_ACTOR_TYPES` match the backend's lists exactly
  (`src/domain/auditLog.ts:43-60` against `docs/admin-api.md:820`, `:833-835`).

**Nothing breaks. The gap is that the panel's type no longer describes reality, so a
per-action colour cannot be assigned to the three new values until it is updated.**

### 3.9 — `VoteType.NONE` was added for withdrawing a vote

**Not applicable.** Same evidence as 3.1: no vote endpoint is called, no vote type is
modelled, and `vote` appears nowhere in `src/`. The vocabulary the panel does model is
in `src/domain/moderation.ts` (report statuses, reasons, severities, content statuses)
and `src/domain/catalog.ts` (semester seasons, lecture types) — no vote type among
them.

---

## 4. What the backend could usefully pin in a test

Ordered by what would hurt most if it broke, and phrased as things the backend can
assert without seeing this repository.

1. **Answer whether legacy `GET /auth/me` is still routed.** Two documents here
   disagree ([C-01](#c-01--get-authme-may-already-be-gone-and-the-page-load-path-swallows-the-404)).
   If it is gone, the panel cannot sign anyone in.
2. **Accept the five bodyless writes without a `Content-Type` header** —
   `POST /auth/logout`, `POST /audit-logs/{id}/revert`,
   `POST /reports/{id}/gitlab-issue`, `PATCH /users/{id}/block`,
   `PATCH /users/{id}/unblock` ([C-03](#c-03--405-and-415-are-invisible-to-every-call-site),
   §3.4).
3. **Always serve `changes` and `metadata` on an audit entry**, even as empty objects
   ([C-08](#c-08--open-maps-are-enumerated-without-a-presence-check)).
4. **Never serve `actor: null` on an audit entry, or `author: null` on a comment,
   answer or report** — or tell us, so we can add the guards
   ([C-07](#c-07--nested-objects-are-read-without-a-null-check)).
5. **Keep the list keys exactly as they are**, especially the two that do not match
   their path: `GET /reports` returns **`bugReports`**, and
   `GET /comments/reported` returns **`comments`** (not `reports`)
   ([C-09](#c-09--path-and-field-names-are-spread-across-19-files)).
6. **Keep `body.message` an operator-readable sentence on every 4xx.** 34 of 42 call
   sites render it verbatim with no status branch
   ([C-05](#c-05--34-of-42-calls-branch-on-no-status-at-all)).
7. **Tell us before the legacy paths are removed.** All 42 are unprefixed
   ([C-02](#c-02--the-client-sends-no-admin-prefix-on-any-of-its-42-paths)); already
   tracked as `H2` in `docs/backend-tasks.md:221`.
8. **Close the two checkboxes this reading answers:** the panel does **not** call
   `GET /admins/validate` (`docs/adminweb-tasks-from-backend.md:135`), and it does
   **not** call either catalogue create, so "handle `403` on the two catalogue creates"
   (`:123`) does not apply here.
