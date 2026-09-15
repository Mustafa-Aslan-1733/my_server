# Admin Web API

The Admin Web API lives under `/admin`. Every path in this document is a complete
backend path, and the unprefixed paths this document used to carry are still served for
the panel's migration — see [Path migration](#path-migration). JSON field names are
camelCase, IDs are UUID strings, and response timestamps are UTC ISO 8601 strings with
milliseconds (for example, `2026-07-23T10:30:00.000Z`).

Except for the two login operations and health, the Admin Web endpoints require:

```http
Authorization: Bearer <opaque-token>
```

An absent, invalid, expired, or revoked token returns `401`. A valid non-admin token
returns `403`. Admin role membership comes from the `admins` table, not from the
frontend. Error responses use:

```json
{"message":"Human-readable error","success":false}
```

Validation errors return `400`, missing records `404`, a path called with a verb it does
not map `405` with an `Allow` header, protected admin-account state conflicts `409`, a body
sent in a media type the route cannot read `415`, rate limits `429` with `Retry-After`,
unexpected failures `500`, and a failed database readiness check `503`.

Two distinctions worth stating, because both used to answer `500`: a body in the wrong
media type is `415`, while malformed content inside the *right* media type is `400`. A third
used to answer `404`: a path that does not exist is `404`, while a path that exists and does
not map the verb is `405` — the two were indistinguishable until F-16 was fixed, since both
carried the same status and the same `"Not found"` body.

## Path migration

Every administrative route now answers under `/admin` as well as on its old path. The rule
is mechanical: the new path is `/admin` plus the old one.

**The legacy paths are not being removed.** This section used to say they existed so the panel
could move at its own pace and would go once it had. That was the plan until 10 September, when
the deployment made the decision instead: on the deployed host `/admin/` is routed to the admin
panel's own static container, so **every `/admin/**` route here is answered by the panel's
`index.html` and never reaches this application** (F-43). The migration was therefore withdrawn
rather than deferred — see [adminweb-tasks.md](adminweb-tasks.md), which marks both migration
tasks as withdrawn — and **the unprefixed paths are the production contract permanently.**

The `/admin` twins are still real, still tested and still documented below, and they are what a
client would use if the routing ever changed. They are not, today, callable from the deployed
panel.

| Old | New |
| --- | --- |
| `/auth/request-login`, `/auth/login`, `/auth/logout`, `/auth/logout-all`, `/auth/me` | `/admin/auth/...` |
| `/users/**` | `/admin/users/**` |
| `/comments/**`, `/answers/**` | `/admin/comments/**`, `/admin/answers/**` |
| `GET /ratings`, `DELETE /ratings/{id}` | `/admin/ratings...` |
| `/audit-logs/**`, `/activity-logs/**` | `/admin/audit-logs/**`, `/admin/activity-logs/**` |
| `/system/status` | `/admin/system/status` |
| `GET /data/lectures/all`, `GET /data/professor/all`, `PATCH`/`DELETE /data/lectures/{id}`, `PATCH`/`DELETE /data/professor/{id}` | `/admin/data/...` |
| `POST /data/lectures`, `POST /data/professor` | `POST /admin/data/lectures`, `POST /admin/data/professor` |
| `GET /reports`, `PATCH`/`DELETE /reports/{id}`, `POST /reports/{id}/gitlab-issue` | `/admin/reports...` |

Three things deliberately do **not** move:

- **`POST /reports`** is the student's bug submission on the app API. There is no
  `POST /admin/reports`, and a request to it answers `405` with `Allow: GET` — the path
  exists, the verb does not. (It answered `404` until F-16 was fixed.)
- **The public catalogue reads** — `GET /data/lectures`, `GET /data/lectures/{id}`,
  `GET /data/professor`, `GET /data/professor/{id}`, `GET /data/professor/id` — stay on the
  app API. The panel calls them there. Serving the same rows under `/admin` would put one
  resource behind two different authorization outcomes, and the `/all` listings already cover
  what the panel needs beyond them — on their **unprefixed** paths, `GET /data/lectures/all` and
  `GET /data/professor/all`, which are the ones reachable on the deployed host.
- **`GET /admins/validate`** is legacy and undocumented; `GET /admin/auth/me` answers the
  same question with more. It is being retired, not moved. `/admin/**` matches whole
  segments, so it is untouched by the new prefix.

## Authentication

**Which API you logged in through decides the session.** Session type and lifetime are
properties of the endpoint, not of the account: `POST /admin/auth/login` mints an
administrator session on the admin schedule and refuses anyone without an `admins` row,
while `POST /auth/login` on the app API mints an app session that lasts a year for every
account, administrators included. An administrator logging in from the mobile app is
therefore not on the panel's expiry schedule — deriving the lifetime from the account
instead is what used to put one there.

### `POST /admin/auth/request-login`

No authentication. Email is trimmed, lowercased, and must be a
`@student.kit.edu` address.

```json
{"email":"admin@student.kit.edu"}
```

```json
{"message":"Login code sent","success":true}
```

The code is six characters, Argon2-hashed, single-use, and expires after five minutes
by default. Request limits default to 3 per email and 20 per source IP in 15 minutes.
Codes are delivered through SMTP and are never returned or logged.

### `POST /admin/auth/login`

No authentication.

```json
{"email":"admin@student.kit.edu","loginToken":"ABC234"}
```

```json
{
  "message":"Login successful",
  "success":true,
  "authToken":"opaque-random-token",
  "expiresAt":"2026-07-24T10:30:00.000Z"
}
```

An account without an `admins` row is refused with `403 Admin access required`, and the
refusal costs the caller a login-failure slot and is recorded as `LOGIN_REFUSED` with
`reason = NOT_AN_ADMIN`. A `sessionType` field in the body is ignored here: this endpoint
only ever mints an administrator session. On the legacy `POST /auth/login` it still
selects between `APP` and `ADMIN`, and omitting it there still gives an account with an
`admins` row an admin session, which is what keeps an un-migrated panel working.

The opaque token is returned once; only its SHA-256 hash is persisted. This session
expires after `AUTH_ADMIN_SESSION_TTL`, one day by default, which `expiresAt` reports so
the panel can warn an operator instead of discovering it as a `401` mid-form. A session
from the app API lasts `AUTH_APP_SESSION_TTL`, 365 days by default. An eligible email
in `ADMIN_BOOTSTRAP_EMAILS` is provisioned into `admins` at startup, and again after
successful OTP verification if the row is missing. Admin token creation and `ADMIN_LOGIN` audit insertion share one
transaction.

#### Elevated operators

`ADMIN_SUPERUSER_EMAILS` is a comma-separated list of addresses, trimmed and lowercased
the way the panel normalizes them. An elevated operator is the only caller that may
moderate **another administrator account** — block, unblock, warn, delete, or edit its
`status`. Everyone else still gets `409` against an administrator target, and nobody
moderates their own account, elevated or not.

Being listed grants nothing on its own: the address must already be an administrator,
which is what `ADMIN_BOOTSTRAP_EMAILS` is for. The two lists overlap without being the
same thing.

**Elevation is not a role.** `/admin/auth/me`, `GET /admin/users`, `GET /admin/users/{id}` and every audit
`actor.role` keep reporting `ADMIN` for these accounts. The distinction reaches clients
in two separate places: the `isSuperAdmin` boolean on `GET /admin/auth/me`, and `elevated: true`
in the `metadata` of the audit entries that only an elevated operator could have written.

### `POST /admin/auth/logout`

Admin authentication; no body. Revokes the current token and writes
`ADMIN_LOGOUT` in the same transaction.

```json
{"message":"Logged out successfully","success":true}
```

**Not every `ADMIN_LOGIN` has a matching `ADMIN_LOGOUT`, and that is the intended
behaviour.** A session ends without one whenever the client never calls this endpoint: a
closed tab, or a request that came back `401` on an already-rejected token, where there is
nothing left to revoke. A session that reaches its TTL (`AUTH_ADMIN_SESSION_TTL`, one day
by default) simply stops authenticating — there is no request at that moment and no actor to
attribute an entry to, and this log records what someone did, not what a clock did.

The one case where the backend itself ends an *admin's* session is a role change through
`PATCH /admin/users/{id}`; blocking and deleting refuse administrator targets outright. That case
is already in the log, as the `USER_UPDATED` entry carrying `metadata.sessionsRevoked = true`
— with its `changes.role` saying why. An `ADMIN_LOGOUT` written there would be attributed to
the admin who made the change rather than to the one whose session died, which is a worse
record than none. So treat a missing `ADMIN_LOGOUT` as ordinary, and never read the absence
of one as evidence that a session is still live.

### `POST /admin/auth/logout-all`

Any authenticated user; no body. Revokes every token of the calling account — including
the token used to make the request — so no session of that account passes authentication
afterwards. The account itself is untouched: the user can sign in again with the normal
email + OTP flow. For an admin caller an `ADMIN_LOGOUT` entry is written with
`metadata.scope = "ALL_DEVICES"`.

```json
{"message":"Logged out from all devices (3 sessions)","success":true}
```

A missing or invalid `Authorization` header returns
`401 {"message":"Not logged in","success":false}`.

### `GET /admin/auth/me`

Any authenticated user.

```json
{
  "message":"Authenticated",
  "success":true,
  "user":{
    "id":"uuid",
    "username":"Administrator",
    "kitEmail":"admin@student.kit.edu",
    "role":"ADMIN",
    "status":"ACTIVE",
    "isSuperAdmin":true
  }
}
```

`isSuperAdmin` is whether this account is an [elevated operator](#elevated-operators). It
is `false` for every student and for an ordinary administrator, and it is a separate field
rather than a third `role` value on purpose — the panel's role union is `STUDENT | ADMIN`.
A client should read the permission from here instead of keeping its own address list.

The legacy `/auth/validate`, `/account/logout`, and student API contracts remain
available.

## Users

All operations require an admin token. `GET /admin/users` omits soft-deleted users and
includes the authenticated admin:

```json
{
  "message":"Success",
  "success":true,
  "users":[{
    "id":"uuid",
    "username":"Student",
    "kitEmail":"student@student.kit.edu",
    "role":"STUDENT",
    "status":"ACTIVE",
    "joined":"2026-07-23T10:00:00.000Z",
    "lastOnline":"2026-07-23T10:00:00.000Z",
    "biography":"",
    "warnings":0,
    "reports":0,
    "credibilityScore":0
  }],
  "nextCursor":null
}
```

`GET /admin/users/{id}` returns that user object directly, **without a wrapper** — it is the one
read in this API that does not wrap. `warnings` is the persisted warning count; `reports`
counts reports against comments authored by the user; `credibilityScore` is the value
`PATCH /admin/users/{id}` writes, returned by both reads so an edit form can seed its input.

**A soft-deleted account reads here, and `404` still means "no such account".** These are two
separate things and the distinction is the contract: an id that names no row answers `404`, an id
that names a soft-deleted row answers `200` with the anonymised account (`status: "DELETED"`,
`username: "Deleted user <first 8 of the id>"`). `404` used to answer both questions, which left
no way to tell a deleted account from one that never existed. It no longer does.

The same rule, in the same words, applies to `GET /admin/users/{id}/warnings` — the two open
together because a client that reads an account and its warning history in one pass sees a
refusal from either as the whole profile being gone.

**Reading only.** Every mutating route on this resource — `PATCH /admin/users/{id}`, the block and
unblock pair, `DELETE`, and issuing, editing or deleting a warning — still answers `404` for a
soft-deleted account. Deletion is irreversible and there is no restore; see
[ADR-0015](adr/0015-deletion-not-split-from-anonymisation.md).

`lastOnline` is **last API activity, not last login**. It is refreshed as authenticated
requests come in, which is what makes it meaningful for accounts that stay signed in for
weeks on one token. The write is throttled to once every five minutes per account, so the
value can trail real activity by that much — render it as a timestamp or a coarse "x hours
ago", not as a live online indicator. A request that is refused (blocked or deleted account,
expired token) does not count as activity. Accounts that have signed in but never called
anything else fall back to `joined`, so the field is never null.

**Soft-deleted accounts are excluded from both reads.** `DELETE /admin/users/{id}` anonymises the
account, and it then disappears from the listing and `404`s by id, so a "total users" counter
built on this listing does not over-report the platform.

### Filtering and pagination

| Parameter | Meaning |
| --- | --- |
| `limit` | Page size, 1–100. **Omit it and every matching user is returned**, with `nextCursor: null`. |
| `cursor` | Opaque cursor from the previous page. `400` without a `limit`. |
| `q` | Case-insensitive search over `username` and `kitEmail`, max 200 characters |
| `status` | `ACTIVE`, `INACTIVE`, `BLOCKED` or `DELETED`. Soft-deleted accounts are excluded from every other call, including an unfiltered one; `status=DELETED` is the only thing that reaches them. |
| `role` | `STUDENT` or `ADMIN`, resolved from `admins` membership |
| `sort` | `newest` (default) or `oldest` |

Pagination is opt-in so that a caller which has not been taught to follow cursors is not
silently truncated to one page. Filters apply either way.

**The two modes order differently, and they have to.** An unparameterised call keeps the
existing order — administrators first, then newest-joined — which is computed in Java because
role is not a column and so cannot be an `ORDER BY`. A call with `limit` orders strictly by
`(createdAt DESC, id DESC)`, the keyset the cursor resumes from.

Bad parameters answer `400` with a specific message rather than a generic failure:
`Limit must be an integer between 1 and 100`, `Invalid user cursor`,
`A cursor requires a limit`, `Invalid user status`,
`Invalid user role`, `Invalid user sort`, `User search is too long`.

**Soft-deleted accounts are listable, and they are anonymised.** A `DELETE` scrubs `username`,
`kitEmail` and `biography` before the row is kept, so the row you get back carries the `id`,
`joined`, `credibilityScore`, `warnings` and `reports` the account had, and
`Deleted user <first 8 of the id>` where the identity was. To put a real name on it, read the
matching `USER_DELETED` entry from `GET /admin/audit-logs` — its `target.label` was written
before the scrub. `target.label` is a **display string**, not two fields: render it, do not
split it.

**Two kinds of deletion reach this list, and only one of them is anonymised.** The paragraph
above is the admin deletion. An account that deleted *itself* from the student app
(`PATCH /account/deleteAccount`) is **not** scrubbed: it keeps its real `username` and
`kitEmail` on the row, and its audit entry is **`USER_SELF_DELETED`**, not `USER_DELETED`.
So a `DELETED` row carrying a real identity is a self-deletion, and the entry to look for is
the other one. A self-deleted account can also **leave this list on its own**, without any
administrative action — see `USER_SELF_REACTIVATED` below.

For dashboard counters, prefer `GET /admin/system/status` — its `counts` block already carries
users, active users, admins, lectures, comments, answers, ratings, open reports and audit
events, and `counts.users` excludes soft-deleted accounts. Polling the full user listing for
a handful of numbers is a full-table read per poll.

Mutation endpoints:

| Method and path | Body | Success message |
| --- | --- | --- |
| `PATCH /admin/users/{id}` | any subset of `{"username","kitEmail","biography","status","role","credibilityScore"}` | `Updated user successfully` |
| `DELETE /admin/users/{id}` | none | `Deleted user successfully` |
| `PATCH /admin/users/{id}/block` | none | `Student blocked successfully` |
| `PATCH /admin/users/{id}/unblock` | none | `Student unblocked successfully` |
| `POST /admin/users/{id}/warnings` | `{"message":"Reason"}` | `Student warned successfully` |
| `PATCH /admin/users/{id}/warnings/{warningId}` | `{"message":"Corrected reason"}` | `Updated warning successfully` |
| `DELETE /admin/users/{id}/warnings/{warningId}` | none | `Deleted warning successfully` |

The target is always the path ID and the actor is always the token principal.
No account can be deleted, blocked, unblocked or warned by itself. Another admin account
is `409` for an ordinary administrator, and permitted for an elevated operator.
Repeated block/unblock requests are successful no-ops and create no new audit event.
Delete is a soft delete and revokes the target's sessions.

### Editing a user

`PATCH /admin/users/{id}` is how a wrong name, a typo'd address or a stale biography gets
fixed. An omitted field is left alone, an empty body is `400`, and a request that changes
nothing is a successful no-op with no audit event. One `USER_UPDATED` event records every
field that actually changed.

- `username` and `kitEmail` are unique; a collision is `409`. `kitEmail` must be a
  `@student.kit.edu` address and is trimmed and lowercased.
- Changing `kitEmail` **revokes the account's sessions** — the address is the login
  identity, so the old sessions no longer correspond to how the account signs in.
- `status` accepts `ACTIVE` and `BLOCKED`. `DELETED` is `400`: deleting anonymizes the
  account, which is `DELETE /admin/users/{id}` and not a field edit. For the same reason a
  deleted account cannot be restored — its identifying fields are already scrubbed. This is
  unchanged by `GET /admin/users?status=DELETED`, which makes those accounts **readable and
  nothing more**: every mutating route on this resource still answers `404` for one, because
  `ModeratedStudents.findMutable` refuses a deleted target before any of them runs.
- `role` moves the account in and out of the `admins` table. Demoting also revokes its
  sessions, because an admin session outlives a student one and the panel would keep
  working until it expired. Demoting is `409` once the account has moderation history —
  a warning it issued or a report it reviewed points at its `admins` row, and that
  attribution is not thrown away to satisfy a role change.
- The guards return `409`, all about not locking anyone out of an API with no other
  recovery path:
  - an admin cannot change **its own** status or role;
  - another admin account's `status` is protected exactly as block/unblock/delete
    protect it — lifted for an elevated operator;
  - an elevated operator's `kitEmail` cannot be changed by anyone, because the address is
    what elevation is keyed on, and moving it is how the account would be taken over;
  - an elevated operator's `role` cannot be changed by an ordinary administrator.
  `username`, `biography` and `credibilityScore` of an admin account are editable, as
  they always have been.
- Reverting a field edit routes back through this endpoint and so meets the same guards:
  an elevated operator's `status` change on an admin target can be reverted by an elevated
  operator, and an ordinary administrator's revert of it is refused with the same `409` the
  direct edit would have been.

`PATCH`/`DELETE /admin/users/{id}/warnings/{warningId}` correct or withdraw a warning that was
already issued. The student is not re-notified: the notification went out when the
warning was created, and rewording it corrects the record rather than warning again. A
warning addressed under the wrong student is `404`.

`GET /admin/users/{id}/warnings` returns:

```json
{
  "message":"Successfully found warnings for student",
  "success":true,
  "warnings":[{
    "id":"warning-uuid",
    "userID":"target-user-uuid",
    "message":"Reason",
    "createdAt":"2026-07-23T10:00:00.000Z",
    "createdFrom":{
      "id":"admin-user-uuid",
      "name":"Administrator",
      "createdAt":"2026-07-23T10:00:00.000Z"
    }
  }]
}
```

`id` is the warning's own id and is what `PATCH`/`DELETE /admin/users/{id}/warnings/{warningId}`
address.

**A soft-deleted account's warning history reads, and `404` still means "no such account".** The
history records why the account was moderated and outlives the account, which is the point of
being able to open it. An id that names no row is `404`, exactly as before; only the deleted case
moved. This route and `GET /admin/users/{id}` changed together — see the note there.

Issuing, editing and deleting a warning are unaffected and still answer `404` for a soft-deleted
account.

`createdFrom` identifies the issuing admin. `id` is that admin's **student** id — the same
value `GET /admin/auth/me` returns as `user.id`, so a client can tell whether it issued a warning
itself by comparing the two. `name` is the admin's display name. `createdFrom.createdAt` is
when the warning was issued and therefore repeats the outer `createdAt`.

**The issuer is always taken from the auth token.** A client does not get to choose who a
warning is attributed to, and the request body carries only `message`. The `userID` and
`createdFrom` fields this endpoint used to accept were read nowhere; they have been removed,
and a caller still sending them is unaffected because unknown JSON fields are ignored.

## Comments

`GET /admin/comments` returns every comment, not only reported ones — this is what the panel
edits or removes content from independent of any report:

```json
{
  "message":"Successfully found all comments",
  "success":true,
  "comments":[{
    "id":"comment-uuid",
    "content":"Comment text",
    "status":"VISIBLE",
    "author":{"id":"uuid","name":"Student","role":"STUDENT","warnings":0,"status":"ACTIVE"},
    "postContext":"CS101 — Algorithms",
    "lectureId":"lecture-uuid",
    "answers":2,
    "reports":0,
    "createdAt":"2026-07-23T10:00:00.000Z"
  }]
}
```

`PATCH /admin/comments/{id}` accepts any subset of `{"content","status"}`, at least one
required:

```json
{"content":"Corrected text","status":"HIDDEN"}
```

`content` is trimmed and 1–10000 characters. `status` is `VISIBLE`, `HIDDEN`, or
`DELETED`; `HIDDEN` is what the student app's read path filters out, independent of
whether a report exists. Returns `{"message":"Updated comment successfully","success":true}`;
an unchanged request is a no-op with no audit event.

`DELETE /admin/comments/{id}` is a real delete, not a hide — it removes the comment, its
answers, votes and reports. Use `status = HIDDEN` to take content out of the student
app while keeping its history. Returns
`{"message":"Deleted comment successfully","success":true}`.

## Comment reports

`GET /admin/comments/reported` returns every report:

```json
{
  "message":"Success",
  "success":true,
  "comments":[{
    "id":"report-uuid",
    "commentId":"comment-uuid",
    "commentContent":"Reported comment",
    "postContext":"CS101 — Algorithms",
    "reportText":"",
    "reportedUser":{"id":"uuid","name":"User","role":"STUDENT","warnings":0,"status":"ACTIVE"},
    "reporter":{"id":"uuid","name":"Reporter","role":"STUDENT","warnings":0,"status":"ACTIVE"},
    "reason":"HARASSMENT",
    "status":"OPEN",
    "date":"2026-07-23T10:00:00.000Z"
  }]
}
```

`id` is the **report's** id, which the status and withdrawal routes below address.
`commentId` is the reported comment itself, which is what opening, hiding, editing or
deleting the content the report is about addresses — without it the reports tab can only
change a report's status, and acting on the content means finding it by eye in the content
tab.

`PATCH /admin/comments/reported/{id}` accepts a `ReportStatus`:

```json
{"status":"REVIEWED"}
```

It returns `{"message":"Updated","success":true}`. An unchanged status is an
audits-free no-op. Moving a report to `ACTION_TAKEN` hides the comment (and its
answers, which nest under it in the student read path); moving it to any other status
puts the comment back.

`DELETE /admin/comments/reported/{id}` withdraws the report record itself — the reported
comment is untouched, since retracting a report that should not have been filed is not
a judgement on the content. Returns `{"message":"Deleted report successfully","success":true}`.

## Answers

Mirrors comments in every respect, including `content`/`status` validation.

`GET /admin/answers` returns every answer:

```json
{
  "message":"Successfully found all answers",
  "success":true,
  "answers":[{
    "id":"answer-uuid",
    "content":"Answer text",
    "status":"VISIBLE",
    "author":{"id":"uuid","name":"Student","role":"STUDENT","warnings":0,"status":"ACTIVE"},
    "commentId":"comment-uuid",
    "commentPreview":"First 120 characters of the parent comment…",
    "postContext":"CS101 — Algorithms",
    "reports":0,
    "createdAt":"2026-07-23T10:00:00.000Z"
  }]
}
```

`PATCH /admin/answers/{id}` body and behavior are identical to `PATCH /admin/comments/{id}`, and
returns `{"message":"Updated answer successfully","success":true}`.

`DELETE /admin/answers/{id}` deletes the answer and its votes/reports. Returns
`{"message":"Deleted answer successfully","success":true}`.

## Answer reports

Mirrors comment reports for answers.

`GET /admin/answers/reported` returns every report:

```json
{
  "message":"Success",
  "success":true,
  "answers":[{
    "id":"report-uuid",
    "answerId":"answer-uuid",
    "commentId":"parent-comment-uuid",
    "answerContent":"Reported answer",
    "commentContent":"The comment it answers",
    "postContext":"CS101 — Algorithms",
    "reportText":"",
    "reportedUser":{"id":"uuid","name":"User","role":"STUDENT","warnings":0,"status":"ACTIVE"},
    "reporter":{"id":"uuid","name":"Reporter","role":"STUDENT","warnings":0,"status":"ACTIVE"},
    "reason":"HARASSMENT",
    "status":"OPEN",
    "date":"2026-07-23T10:00:00.000Z"
  }]
}
```

As with comment reports, `id` is the report's id; `answerId` is the reported answer and
`commentId` is the thread it sits in, so both the content and its context can be opened
directly.

`PATCH /admin/answers/reported/{id}` accepts `{"status":"<ReportStatus>"}` and
`DELETE /admin/answers/reported/{id}` withdraws the report — both behave exactly like the
comment-report equivalents, hiding/restoring the answer on `ACTION_TAKEN` transitions.

## Ratings

`GET /admin/ratings` returns every rating individually, not aggregated into a lecture
average — this is what a bogus rating gets identified and deleted by id from:

```json
{
  "message":"Successfully found all ratings",
  "success":true,
  "ratings":[{
    "id":"rating-uuid",
    "student":{"id":"uuid","name":"Student","role":"STUDENT","warnings":0,"status":"ACTIVE"},
    "lectureLabel":"CS101 — Algorithms",
    "lectureId":"lecture-uuid",
    "topics":[{"category":"LECTURE_UNDERSTANDABILITY","value":4.5}],
    "createdAt":"2026-07-23T10:00:00.000Z"
  }],
  "nextCursor":null
}
```

Accepts `limit` (1–100), `cursor` and `lectureId`, with the same opt-in pagination as
`GET /admin/users`: no `limit` returns every rating and a null cursor. `lectureId` is a query
parameter rather than a path segment for a reason that no longer applies on this path —
`GET /ratings/{lectureId}` is the app API's per-lecture averages read, which used to sit on
the same base path. It stays a query parameter; changing it is its own decision. A malformed `lectureId` is
`400 Invalid lecture id`; a malformed cursor is `400 Invalid rating cursor`.

`category` is a `RatingCategory` (see `LectureType` below for which categories a given
lecture offers); `value` is the score the student gave that category.

`DELETE /admin/ratings/{id}` discards the rating. Deliberately delete-only: rewriting
someone's scores would put an opinion they never gave into the lecture average, so a
bogus rating is removed rather than corrected. Returns
`{"message":"Deleted rating successfully","success":true}`.

## Catalogue: lectures and professors

Lectures and professors are the catalogue the ratings are filed against. `GET`
endpoints under `/data` are public and shared with the student app; writes
(`POST`/`PATCH`/`DELETE`) are admin-only.

`GET /data/lectures` and `GET /data/professor` — the public catalogue reads used by the
student app — filter to `active = true`. **Once a lecture or professor is deactivated it
disappears from both**, so the panel has no way back to it through those endpoints. Use
the `/all` variants below instead when building the management view; they are
admin-only and return every row regardless of `active`.

**Both `/all` reads answer on two paths**, the `/admin` one and the unprefixed one, from a
single handler. That matters for a deployed client rather than for this document: on the
deployed host `/admin/**` is the panel's own static server (F-43), so **the unprefixed path
is the one a panel can actually call** — `GET /admin/data/lectures/all` comes back as the
panel's `index.html`. The panel's catalogue view therefore reads `GET /data/lectures/all`
and `GET /data/professor/all`, and sends its bearer token with them.

| Method and path | Body | Notes |
| --- | --- | --- |
| `GET /data/lectures` | — | Public. Active lectures only. |
| `GET /admin/data/lectures/all`, `GET /data/lectures/all` | — | Admin-only, both paths, one handler (`ModerationCatalogController.getAllLectures`). Every lecture, active or not, ordered by name. The unprefixed path is **not** legacy and is not being retired: it is the one reachable on the deployed host, and it is what the panel calls. `hasRole("ADMIN")` is matched in `SecurityConfig` for the unprefixed path and by the `/admin/**` rule for the other, so the two cannot answer differently. Anonymous is `401`, a student token is `403`. |
| `GET /data/lectures/{id}` | — | Public. An unknown id answers a real HTTP `404 {"message":"Lecture not found","success":false}`. Earlier revisions of this table promised a `200` with a `"lecture":null` body; that was true until 9.09 and the key is gone with it — see the `CHANGELOG`. |
| `POST /admin/data/lectures` | `{"name","code","semesterYear","semesterSeason","active","professorIds"}` | Refused with a plain `403` by the security chain when the caller is not an admin. `active` is currently ignored; new lectures are always created active, but the field must be **present** — omitting it answers `400 {"message":"Invalid request"}`. A duplicate name answers `409`, and a `professorIds` entry that does not resolve answers `404`; both keep the body they always had — `{"message", "success": false}`. `LECTURE_CREATED` audit event, which is deliberately **not** revertible: undoing a creation is a deletion. |
| `POST /data/lectures` | same | **Legacy, removed once the panel has moved.** Same write, same `409` and `404`, and refused with a plain `403` by the security chain exactly like the `/admin` version. Earlier revisions of this table described a `200 {"success":false}` here; that was never true of this path — `SecurityConfig` has always matched it. |
| `PATCH /admin/data/lectures/{id}` | any subset of `{"name","code","semesterYear","semesterSeason","active","lectureType","professorIds"}` | `LECTURE_UPDATED` audit event. |
| `DELETE /admin/data/lectures/{id}` | none | Deletes the lecture and every comment, answer and rating filed under it. Use `active = false` instead to take a lecture out of the app while keeping its history. `LECTURE_DELETED` audit event. |
| `GET /data/professor` | — | Public. Active professors only. |
| `GET /admin/data/professor/all`, `GET /data/professor/all` | — | Admin-only, both paths, one handler (`ModerationCatalogController.getAllProfessors`). Every professor, active or not, ordered by last name then first name. The unprefixed path is the one reachable on the deployed host and is not being retired — same rule and same refusals as the lecture pair above. |
| `GET /data/professor/{id}` | — | Public. An unknown id answers `404 {"message":"Professor not found","success":false}`, with no `"professor"` key — it answered `200 {"success":false}` until 9.09. |
| `GET /data/professor/id?firstName=&lastName=` | — | Public. Returns a bare UUID string, or `null` if no match — not wrapped in a response object. |
| `POST /admin/data/professor` | `{"firstName","lastName","lectureIDs"}` | Refused with a plain `403`, like `POST /admin/data/lectures`. A duplicate first+last name answers `409`, and a `lectureIDs` entry that does not resolve answers `404`; both keep the body they always had. `PROFESSOR_CREATED` audit event, not revertible for the same reason. |
| `POST /data/professor` | same | **Legacy, removed once the panel has moved.** Same `409` and `404`, and refused with a plain `403`, like the `/admin` version. This route did once check the caller inside the handler — answering `200 {"success":false}` to a non-admin, and a `500` to an anonymous caller because the principal it read was null — and is now matched by the security chain like every other `/data` write. |
| `PATCH /admin/data/professor/{id}` | any subset of `{"firstName","lastName","active","lectureIds"}` | `PROFESSOR_UPDATED` audit event. |
| `DELETE /admin/data/professor/{id}` | none | Detaches the professor from every lecture; the lectures themselves survive. `PROFESSOR_DELETED` audit event. |

`LectureResponse` (returned by the lecture reads, and nested in `LectureDetailResponse`
as `lecture`):

```json
{
  "id":"lecture-uuid",
  "name":"Algorithmen 1",
  "code":"CS101",
  "semesterYear":2026,
  "semesterSeason":"SS",
  "semesterLabel":"SS26",
  "title":"SS26 Algorithmen 1 — Anna Bauer, Peter Sanders",
  "active":true,
  "lectureType":"LECTURE_AND_EXERCISE",
  "professors":[{"id":"prof-uuid","firstName":"Anna","lastName":"Bauer","active":true}],
  "commentCount":3,
  "ratingCount":12,
  "averageRating":4.25
}
```

`semesterSeason` is `SS` or `WS` and nothing else. This example said `WINTER` until
2026-09-08, which is not a value the enum accepts: a request carrying it is rejected with
`400 {"message":"Invalid request"}`. The same example was also missing `averageRating`.

`semesterLabel` and `title` are **derived, not stored** — there is no column behind either and
neither can be written. `semesterLabel` is the semester as the university writes it: `SS26`,
and `WS25/26` for a winter semester, which spans two calendar years. `semesterYear` on a
winter row is the year the semester **starts** in. `title` is `semesterLabel`, the name, and
the teaching staff, joined with an em dash — the string a client shows above a rating.

`professors` is ordered by last name, then first name, then id, and that order is now
guaranteed: it is read out of a `Set` and used to come back in a different order between two
requests. It is **alphabetical, not by role** — `lecture_professors` records only that a
person teaches a lecture, so nothing in the schema says who lectures and who runs an exercise
group. Putting the lecturer first would need a column on the join table.

`lectureType` is `LECTURE_ONLY` or `LECTURE_AND_EXERCISE`; it decides which
`RatingCategory` values the lecture offers on `GET /admin/ratings`. Changing it does not
rewrite ratings already submitted — they keep the categories they were given.

`ProfessorResponse` (returned by the professor reads, and nested in
`ProfessorDetailResponse` as `professor`):

```json
{
  "id":"prof-uuid",
  "firstName":"Jane",
  "lastName":"Doe",
  "active":true,
  "averageRating":4.2,
  "ratingCount":12,
  "lectureIds":["lecture-uuid"]
}
```

`ratingCount` is the number of ratings across every lecture the professor teaches, and
`averageRating` their mean. Both are computed on read, so a rating an administrator removes is
reflected immediately. **Until 9.09 `ratingCount` was always `0`** — it was read from a stored
column that nothing ever wrote — so a client that concluded the field was unused was reading it
correctly at the time. It carries a real number now.

`lectureIds` is the professor's own view of the assignment that `PATCH /admin/data/professor/{id}`
writes. Read it from here rather than reverse-indexing it out of `GET /data/lectures`: the
lecture listing is the other direction of the same relation, and reverse-indexing it returns
partial results the moment that listing is filtered or paginated — a professor would appear
to have stopped teaching the lectures that fell outside the page.

`GET /data/professor/{id}` returns `{"message":"...","success":true,"professor":{...}}`.
Its `success` was previously hard-coded `false` on the success path, so a client that
trusted the flag treated every found professor as a miss.

`PATCH /admin/data/lectures/{id}` and `PATCH /admin/data/professor/{id}` validate names and years
the same way `PATCH /admin/users/{id}` validates a username: trimmed, non-empty,
length-capped, `400` on violation. `professorIds`/`lectureIds` replace the assignment
wholesale — pass `[]` to clear it — and a `404` if any id in the list does not resolve.

## Bug reports

`GET /admin/reports` returns:

```json
{
  "message":"Successfully found all entries",
  "success":true,
  "bugReports":[{
    "id":"report-uuid",
    "title":"Profile upload fails",
    "description":"Description",
    "severity":"HIGH",
    "status":"OPEN",
    "reporterName":"Student",
    "reportedAt":"2026-07-23T10:00:00.000Z",
    "issueUrl":null,
    "issueState":"NONE"
  }]
}
```

`issueState` is `NONE`, `CREATED` or `FAILED`, and with `issueUrl` is what decides whether a
client shows a link to the tracker issue, an action to open one, or a retry.

`PATCH /admin/reports/{id}` accepts any subset of `{"status","severity","title","description"}`,
at least one required:

```json
{"status":"ACTION_TAKEN","severity":"HIGH"}
```

Only changed fields are persisted. A request changing several fields creates one
`BUG_REPORT_UPDATED` event. `title` and `description` are trimmed and length-capped
(500 and 10000 characters) the same way a comment's content is.

`DELETE /admin/reports/{id}` discards the report — a duplicate, or one filed by mistake.
Returns `{"message":"Deleted bug report successfully","success":true}` and a
`BUG_REPORT_DELETED` audit event.

`POST /reports` is the authenticated-student submission endpoint and stays open to
students on the app API. It has no `/admin` twin: `POST /admin/reports` answers `405` with
`Allow: GET`, since `GET /admin/reports` is the listing.

### `POST /admin/reports/{id}/gitlab-issue`

Admin only. Opens a GitLab issue for the bug report and stores where it landed.

```json
{
  "message":"Created GitLab issue",
  "success":true,
  "issueUrl":"https://gitlab.example/group/project/-/issues/42",
  "issueIid":42,
  "issueState":"CREATED"
}
```

**Idempotent.** A report that already has an issue is answered with that issue, with
`message` reading `Bug report already has a GitLab issue` and GitLab not contacted at all.
The row is locked for the duration of the call, so a double-clicked button, two admins
working the same queue, and a retry after a timeout cannot open duplicate issues.

**Admin-triggered, not automatic on submission.** Creating an issue for every report as it
arrives would make student-facing bug reporting depend on GitLab being reachable, and would
file the duplicates and the spam into the tracker alongside the real bugs.

- `503 GitLab integration is not configured` when the credentials are unset. Read
  `gitlabEnabled` from `GET /admin/system/status` to hide the action rather than discover this by
  pressing it.
- `502` when GitLab refuses or cannot be reached. The report is left at `issueState:"FAILED"`
  so the attempt is visible and can be retried by calling again.
- `BUG_REPORT_ISSUE_CREATED` audit event on success.

The API token is read from the environment, sent only as a `PRIVATE-TOKEN` request header,
and never logged or returned by any endpoint. That is why issue creation lives in the backend
at all: a token that reaches the browser is a leaked token.

## Audit

This is the activity log: it holds both administrative actions and what users did in
the student app, separated by `actor.type`.

`GET /admin/audit-logs` accepts `limit` (1–100, default 50), opaque `cursor`, `q`,
`actorId`, `actorType`, `action`, `targetType`, and inclusive ISO date-time `from`/`to`.
Results use stable keyset ordering by `(createdAt DESC, id DESC)`. `q` searches the
snapshotted actor name/email and target label case-insensitively.

```json
{
  "message":"Success",
  "success":true,
  "auditLogs":[{
    "id":"audit-uuid",
    "action":"USER_BLOCKED",
    "createdAt":"2026-07-23T10:00:00.000Z",
    "actor":{"type":"ADMIN","id":"uuid","name":"Administrator","email":"admin@student.kit.edu","role":"ADMIN"},
    "target":{"type":"USER","id":"uuid","label":"Student (student@student.kit.edu)"},
    "changes":{"status":{"before":"ACTIVE","after":"BLOCKED"}},
    "metadata":{},
    "revertible":true,
    "revertBlockedReason":null,
    "revertedByAuditId":null
  }],
  "nextCursor":null
}
```

### Reverting an entry

`revertible` is computed by the server on every read, so a client never reimplements the
window or the staleness rule — two copies of that rule would drift apart. When it is false,
`revertBlockedReason` says why, using the same vocabulary the refusal below uses, so one
explanation covers both the hidden control and the control clicked after the entry went
stale. `revertedByAuditId` names the entry that already reversed this one.

`POST /admin/audit-logs/{id}/revert` applies the inverse of one entry. Admin only.

```json
{"message":"Reverted USER_UPDATED","success":true}
```

The reversal is written as **its own** audit event — the entry being reversed is never
modified — and it is applied by calling the same service the panel calls, so it inherits
every guard that path enforces. An entry is revertible when all of the following hold:

- its action is a field edit: `USER_UPDATED`, `USER_BLOCKED`, `USER_UNBLOCKED`,
  `USER_WARNING_UPDATED`, `COMMENT_UPDATED`, `ANSWER_UPDATED`, `LECTURE_UPDATED`,
  `PROFESSOR_UPDATED`, `BUG_REPORT_UPDATED`, `COMMENT_REPORT_STATUS_CHANGED`,
  `ANSWER_REPORT_STATUS_CHANGED`;
- it is inside the revert window (`AUDIT_REVERT_WINDOW`, default 7 days);
- every field's current value still equals the `after` this entry recorded;
- the record it is about still exists, and no entry has already reversed it.

Otherwise it answers `409` with a machine-readable `reason`:

```json
{"message":"The value changed after this entry, so reverting would discard that change",
 "success":false,
 "reason":"VALUE_CHANGED"}
```

| `reason` | Meaning |
| --- | --- |
| `ACTION_NOT_REVERTIBLE` | Not a field edit — a creation, a deletion, a session event or a refusal |
| `WINDOW_EXPIRED` | Older than the revert window |
| `VALUE_CHANGED` | Something changed the field afterwards; reverting would discard it |
| `ALREADY_REVERTED` | A later entry already reversed this one |
| `TARGET_MISSING` | The record the entry is about no longer exists |

**Deletions cannot be reversed and refuse rather than half-applying.** `USER_DELETED`
anonymises the account and `LECTURE_DELETED` takes its comments, answers and ratings with it;
nothing in the log carries the removed data, so there is nothing to restore from.
`USER_SELF_DELETED` and `USER_SELF_REACTIVATED` are refused for the same reason and by the same
rule — they are lifecycle events with an empty `changes` map, so there is no field diff to
invert. That is deliberate for the second one in particular: making an account's revival
revertible would be a restore route arriving through the revert machinery, and there is no
restore. `reason` is
the only field added to any error body in this API, and it is omitted entirely when absent, so
every other error response is unchanged.

`GET /admin/audit-logs/meta` returns actor snapshots plus these enums:

- Actor types: `ADMIN`, `USER`
- Administrative actions: `ADMIN_LOGIN`, `ADMIN_LOGOUT`, `USER_UPDATED`,
  `USER_WARNING_CREATED`, `USER_WARNING_UPDATED`, `USER_WARNING_DELETED`,
  `USER_BLOCKED`, `USER_UNBLOCKED`, `USER_DELETED`, `COMMENT_UPDATED`,
  `COMMENT_DELETED`, `ANSWER_UPDATED`, `ANSWER_DELETED`, `RATING_DELETED`,
  `COMMENT_REPORT_STATUS_CHANGED`, `COMMENT_REPORT_DELETED`,
  `ANSWER_REPORT_STATUS_CHANGED`, `ANSWER_REPORT_DELETED`, `BUG_REPORT_UPDATED`,
  `BUG_REPORT_ISSUE_CREATED`, `BUG_REPORT_DELETED`, `LECTURE_CREATED`, `LECTURE_UPDATED`,
  `LECTURE_DELETED`, `PROFESSOR_CREATED`, `PROFESSOR_UPDATED`, `PROFESSOR_DELETED`,
  `USER_SELF_DELETED`, `USER_SELF_REACTIVATED`
- Student activity: `USER_LOGIN`, `USER_LOGOUT`, `COMMENT_CREATED`, `ANSWER_CREATED`,
  `RATING_SUBMITTED`, `COMMENT_REPORT_CREATED`, `ANSWER_REPORT_CREATED`,
  `BUG_REPORT_CREATED`
- Refusals: `LOGIN_REFUSED`, `ACCESS_REFUSED`
- Target types: `ADMIN_SESSION`, `USER_SESSION`, `USER`, `WARNING`, `COMMENT`,
  `ANSWER`, `RATING`, `LECTURE`, `PROFESSOR`, `COMMENT_REPORT`, `ANSWER_REPORT`,
  `BUG_REPORT`, `ENDPOINT`

`actors` lists admin actors only. Every account that ever signed in appears in the log
as a `USER` actor, so an exhaustive list of those is unbounded; filter students with `q`
or `actorType` instead.

Actor and target display data is copied into each event, so later lifecycle changes do
not make old records unreadable. Domain mutations and audit inserts run in the same
transaction.

`USER_BLOCKED`, `USER_UNBLOCKED`, `USER_WARNING_CREATED`, `USER_DELETED` and
`USER_UPDATED` carry `metadata.elevated = true` when the target was itself an
administrator and the actor was an [elevated operator](#elevated-operators) — that is,
when elevation is what allowed the action. `actor.role` stays `"ADMIN"`: elevation is not
a role, and this is where the distinction is recorded instead.

### The account lifecycle from the app side

Two actions record what a student did to their own account. Both are **administrative** scope
and answer on `GET /admin/audit-logs`, not on `/activity-logs`, even though `actor.type` is
`USER`: the scope follows the log a reader needs rather than the actor, the same rule that puts
a refused *admin* login in the activity log. The reader here is an operator asking where an
account went, and half a lifecycle on each of two screens answers nobody.

| Action | Written when | `metadata` |
| --- | --- | --- |
| `USER_SELF_DELETED` | `PATCH /account/deleteAccount` | `{"anonymized": false}` |
| `USER_SELF_REACTIVATED` | a deleted account is set back to `ACTIVE` | `{"trigger":"LOGIN_CODE_REQUEST","callerAuthenticated":false}` |

```json
{
  "id":"audit-uuid",
  "action":"USER_SELF_DELETED",
  "createdAt":"2026-09-10T09:12:00.000Z",
  "actor":{"type":"USER","id":"uuid","name":"ada","email":"ada@student.kit.edu","role":"STUDENT"},
  "target":{"type":"USER","id":"uuid","label":"ada (ada@student.kit.edu)"},
  "changes":{},
  "metadata":{"anonymized":false},
  "revertible":false,
  "revertBlockedReason":"ACTION_NOT_REVERTIBLE",
  "revertedByAuditId":null
}
```

`anonymized: false` is what separates it from `USER_DELETED`: the administrative path scrubs the
account in the same transaction and this one does not, so both the row and this entry's
`target.label` carry the real identity.

**`callerAuthenticated: false` is literal, and it is the part to read carefully.** A deleted
account is set back to `ACTIVE` when a login code is **requested** for its address, not when one
is entered, and `POST /auth/request-login` is public. The caller who triggered the revival
therefore proved nothing and need not be the account's owner. The actor recorded is the account
itself, because there is no identified actor to name. **This entry records that behaviour; it
does not authorise it, and the behaviour is an open defect rather than a feature of the API.**

Neither action is revertible, and neither is elevated: they have no administrative actor to
elevate.

### Student activity

Actions performed with a student token are recorded with `actor.type = "USER"` and
`actor.role = "STUDENT"`. `changes` is empty for these — nothing existing is modified —
and `metadata` carries the detail:

```json
{
  "id":"audit-uuid",
  "action":"COMMENT_CREATED",
  "createdAt":"2026-07-23T10:30:00.000Z",
  "actor":{"type":"USER","id":"uuid","name":"Student","email":"student@student.kit.edu","role":"STUDENT"},
  "target":{"type":"COMMENT","id":"comment-uuid","label":"CS101 — Algorithms"},
  "changes":{},
  "metadata":{"contentPreview":"Clear and well paced","contentLength":20}
}
```

`contentPreview` is the first 120 characters of the submitted text; the full body stays
in its own table. `USER_LOGIN` carries
`{"method":"EMAIL_OTP","newAccount":<bool>,"api":"APP"}` and targets the created session.
`ADMIN_LOGIN` carries `{"method":"EMAIL_OTP","api":"APP"|"ADMIN"}`, where `api` names the
login endpoint the session came from — an `ADMIN_LOGIN` with `"api":"APP"` is a panel that
has not moved to `/admin/auth/login` yet. Votes are not recorded: they carry no content and would
outnumber every other event.

### Refusals

Two kinds of refusal are recorded, both written in their own transaction so that the
record survives the rollback of the request it describes:

| Action | When | `metadata` |
| --- | --- | --- |
| `LOGIN_REFUSED` | `POST /admin/auth/login` answered `403` or `401` | `reason` is `INVALID_CODE` or `ACCOUNT_<status>`, plus `method` |
| `ACCESS_REFUSED` | an authenticated caller was answered `403` | `method`, `path`, `status` |

Only refusals attributable to a known account are recorded. A login attempt for an
address with no account, and an anonymous request answered `401`, have no actor — and
recording them would let anyone fill the table. A failing refusal insert is logged and
swallowed for `ACCESS_REFUSED`, because the documented `403` answer has to hold either
way.

Both recorded login refusals consume the login-failure budget, so a single address
produces at most `app.auth.rate-limit.login-email` of them per window. Once that budget is
spent the endpoint answers `429` and records nothing: that gate consumes nothing itself, so
it answers every request for the rest of the window and one record per refused request
would be unbounded. A blocked account therefore reaches `429` under replay rather than
answering `403` indefinitely.

## System

`GET /admin/system/status` requires an admin token and answers `200` whenever the API is
serving, reporting a failing database in `status` rather than in the HTTP code — a page
that has to show "the API is up but the database is not" cannot do that if the request
itself fails.

```json
{
  "message":"Success",
  "success":true,
  "status":"OK",
  "checkedAt":"2026-07-23T10:30:00.000Z",
  "startedAt":"2026-07-23T09:00:00.000Z",
  "uptimeSeconds":5400,
  "database":{"reachable":true,"latencyMs":3,"error":null},
  "counts":{
    "users":42,
    "activeUsers":37,
    "admins":6,
    "lectures":12,
    "comments":83,
    "answers":19,
    "ratings":57,
    "openCommentReports":2,
    "openBugReports":1,
    "auditEvents":314
  },
  "lastWrite":{
    "at":"2026-07-23T10:29:48.000Z",
    "ageSeconds":12,
    "action":"USER_BLOCKED",
    "actorName":"Administrator"
  },
  "gitlabEnabled":false
}
```

`gitlabEnabled` reports whether the GitLab issue integration is configured, so a client can
hide the "create issue" action instead of discovering it is unavailable by pressing it.

`status` is one of:

- `OK` — the `SELECT 1` probe answered and the counts were read
- `DEGRADED` — the probe answered but a count query failed; `counts` and `lastWrite`
  are `null`
- `DOWN` — the database is unreachable; `database.error` names the failure class and
  `counts`/`lastWrite` are `null`

`counts.users` excludes soft-deleted accounts, matching `GET /admin/users`, and
`counts.activeUsers` narrows that to `status == ACTIVE`; the two differ by the `INACTIVE`
and `BLOCKED` accounts, so a dashboard can show both without reading the user listing.

`counts.admins` counts the accounts that hold the administrator role — the rows `GET /admin/users`
returns with `role: "ADMIN"` — and not `ADMIN_BOOTSTRAP_EMAILS`. The two overlap without
being the same. A bootstrap address counts without anyone having signed in, because the
startup runner creates and promotes those accounts. An account promoted through
`PATCH /admin/users/{id}` counts from that moment, configured or not. And a demotion that leaves
the address in `ADMIN_BOOTSTRAP_EMAILS` is undone by the next restart or that account's next
sign-in, either of which re-promotes it, so the count climbs back on its own — removing the
address from the configuration is what makes a demotion stick.

`lastWrite` is the newest audit event and is absent when nothing has been recorded yet; it is what answers
whether an action taken in the panel actually reached the database.

## Health

`GET /health` is public and performs `SELECT 1`, then verifies that Flyway has
no failed or pending migrations and has a current migration or baseline:

```json
{"message":"API healthy","success":true}
```

It answers `503 {"message":"API unhealthy","success":false}` when the database
query fails or Flyway does not report a clean migration state. This is the
liveness probe for deployment tooling; the admin panel's status page uses
`GET /admin/system/status` instead, which stays `200` so it can render the failure.

## Configuration

Copy `.env.example` and supply deployment-specific values. In production,
`AUTH_RATE_LIMIT_HMAC_KEY` must be an unpredictable value of at least 32 characters,
`ADMIN_BOOTSTRAP_EMAILS` must be explicit, and `ADMIN_FRONTEND_ORIGINS` must contain
only exact comma-separated origins. Wildcards are rejected. SMTP and third-party API
credentials are read only from environment variables.

`ADMIN_FRONTEND_ORIGINS` must list every origin the panel is served from, scheme and
port included. A browser call from an origin outside the list is answered with
`403 Invalid CORS request` before it reaches a controller, so `POST /admin/auth/request-login`
fails and the panel cannot be logged into. An empty value falls back to the local
development origins and logs a warning at startup; it does not disable the check.

**A same-origin caller never reaches this check**, so it does not need to be listed. This
page previously claimed the opposite — that the origin had to be listed even when the panel
and the API shared a host, because a browser sends `Origin` on non-`GET` requests regardless.
The header is indeed sent; Spring simply does not reject on it when the origin matches the
request's own. Measured against the deployed host, where the allow-list did **not** contain
`https://ratemyprofessor.dev`:

| Preflight | Result |
| --- | --- |
| `Origin: https://ratemyprofessor.dev` → `https://ratemyprofessor.dev` | `200` |
| `Origin: https://evil.example.invalid` → `https://ratemyprofessor.dev` | `403` |
| `Origin: https://ratemyprofessor.dev` → the other hostname | `403` |

The first two lines together are the proof: the same list rejects a stranger and admits the
page's own origin, so the admission is the same-origin path and not a permit.

This is why the deployed panel is configured with an **empty** API base URL — its calls are
same-origin and CORS is out of the picture — and why `ADMIN_FRONTEND_ORIGINS` is best read as
what is left over for callers that are *not* same-origin. It still matters: get it wrong and a
panel served from anywhere else is locked out.

The bypass rests on `server.forward-headers-strategy=framework`. Spring compares `Origin`
against the request URL it reconstructs, and behind a proxy that reconstruction is only right
while forwarded headers are honoured.

To check the list itself, send an origin that is definitely not on it:

```bash
curl -i -X POST -H "Origin: https://evil.example.invalid" -H "Content-Type: application/json" \
  -d '{"email":"nobody@example.invalid"}' <api base>/auth/request-login
```

`403 Invalid CORS request` is the expected answer there. Against a listed or same-origin
caller the same call answers `400 Invalid KIT email`, meaning it reached controller
validation. Neither spends the login-code budget: the KIT-address check throws before the
rate limiter runs.

### Optional integrations

| Variable | Default | Meaning |
| --- | --- | --- |
| `GITLAB_BASE_URL` | unset | GitLab instance, e.g. `https://gitlab.example`. Unset disables issue creation. |
| `GITLAB_PROJECT_ID` | unset | Numeric id or URL-encoded path of the project issues are filed in |
| `GITLAB_TOKEN` | unset | API token with permission to create issues. Never leaves the backend. |
| `GITLAB_TIMEOUT` | `PT10S` | Connect and read timeout for the GitLab call |
| `AUDIT_REVERT_WINDOW` | `P7D` | How long an administrative field edit stays reversible |

With any of the three GitLab values unset the integration reports itself as disabled:
`GET /admin/system/status` returns `gitlabEnabled:false` and `POST /admin/reports/{id}/gitlab-issue`
answers `503`. Everything else works unchanged.

The Admin Web setting for a backend you are running yourself is:

```dotenv
VITE_API_BASE_URL=http://localhost:8080
```

**The deployed panel sets it empty**, because it is served from the same host that proxies
this backend and an empty base keeps every call same-origin.

`VITE_API_BASE_URL=/api` is for a reverse proxy that exposes this backend under `/api` **and
strips the prefix before forwarding** — `/api` is a proxy prefix, never a backend route
prefix. The deployment host does no such stripping, so `/api` there would arrive as
`/api/auth/login` and get this backend's `404`. The panel's dev server does strip it, which is
why `/api` is right in development and wrong in production.

## Schema management

The backend schema is managed by Flyway migrations under
`src/main/resources/db/migration/postgresql`. Flyway runs during backend startup,
records state in `flyway_schema_history`, and Hibernate validates the result with
`spring.jpa.hibernate.ddl-auto=validate`.

Deployments should point the backend at the target PostgreSQL database with
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and
`SPRING_DATASOURCE_PASSWORD`. Before deploying against an existing database, take
a database backup. Existing non-empty databases from the pre-Flyway period are
baselined at version `1` on first startup; after every persistent environment has
that history table, set `SPRING_FLYWAY_BASELINE_ON_MIGRATE=false`.

## The contract, as a table

Everything above is the description a person reads: what each endpoint does, and **why** it
answers what it answers. This section is the same claims in a shape a test can read, and it
exists because this document has fallen behind the code four times — F-5 (a status that had
changed), F-16 (a 404 that had become a 405), F-22 (a body that no longer existed), F-26 (a
field that had never been right). Each was corrected by hand and nothing stopped the next one.

`AdminApiDocumentationDriftTests` reads the table between the markers below and checks it
**both ways**: a route claimed here that the application does not map is a failure, and an
`/admin/**` route the application maps that is missing here is a failure too. It then provokes
the statuses that can be provoked without knowing a route's fixtures — `401` anonymous, `403`
as a non-administrator, `415` for a body in a media type the route cannot read, and `404` for
an id that names nothing — and compares them with what each row claims.

**The prose above stays the authority on _why_.** This table cannot say that a `409` on
`PATCH /admin/users/{id}` is about not locking anyone out of an API with no other recovery
path; the section that owns the endpoint does, and it should be read first. What the table adds
is that neither half can drift without something going red.

Statuses only a route's own fixture can produce — a `409` on a duplicate name, a `502` from the
tracker, a `429` from the rate limiter — are listed here but **not** provoked by the test, and
the test says so rather than passing quietly.

`GET /admins/validate` is deliberately absent: it is legacy, undocumented above, and being
retired in favour of `GET /admin/auth/me`. The test carries it as a named exemption with that
reason, the way `PUBLIC_ROUTES` carries its own.

<!-- contract-table:start -->

| Method and path | Statuses |
| --- | --- |
| `POST /admin/auth/request-login` | 200, 400, 415, 429 |
| `POST /admin/auth/login` | 200, 400, 401, 403, 415, 429 |
| `POST /admin/auth/logout` | 200, 401 |
| `POST /admin/auth/logout-all` | 200, 401 |
| `GET /admin/auth/me` | 200, 401 |
| `GET /auth/me` | 200, 401, 403 |
| `GET /admin/users` | 200, 400, 401, 403 |
| `GET /admin/users/{id}` | 200, 401, 403, 404 |
| `PATCH /admin/users/{id}` | 200, 400, 401, 403, 404, 409, 415 |
| `DELETE /admin/users/{id}` | 200, 401, 403, 404, 409 |
| `PATCH /admin/users/{id}/block` | 200, 401, 403, 404, 409 |
| `PATCH /admin/users/{id}/unblock` | 200, 401, 403, 404, 409 |
| `POST /admin/users/{id}/warnings` | 200, 400, 401, 403, 404, 409, 415 |
| `GET /admin/users/{id}/warnings` | 200, 401, 403, 404 |
| `PATCH /admin/users/{id}/warnings/{warningId}` | 200, 400, 401, 403, 404, 415 |
| `DELETE /admin/users/{id}/warnings/{warningId}` | 200, 401, 403, 404 |
| `GET /admin/comments` | 200, 401, 403 |
| `PATCH /admin/comments/{id}` | 200, 400, 401, 403, 404, 415 |
| `DELETE /admin/comments/{id}` | 200, 401, 403, 404 |
| `GET /admin/comments/reported` | 200, 401, 403 |
| `PATCH /admin/comments/reported/{id}` | 200, 400, 401, 403, 404, 415 |
| `DELETE /admin/comments/reported/{id}` | 200, 401, 403, 404 |
| `GET /admin/answers` | 200, 401, 403 |
| `PATCH /admin/answers/{id}` | 200, 400, 401, 403, 404, 415 |
| `DELETE /admin/answers/{id}` | 200, 401, 403, 404 |
| `GET /admin/answers/reported` | 200, 401, 403 |
| `PATCH /admin/answers/reported/{id}` | 200, 400, 401, 403, 404, 415 |
| `DELETE /admin/answers/reported/{id}` | 200, 401, 403, 404 |
| `GET /admin/ratings` | 200, 400, 401, 403 |
| `DELETE /admin/ratings/{id}` | 200, 401, 403, 404 |
| `GET /admin/data/lectures/all` | 200, 401, 403 |
| `POST /admin/data/lectures` | 200, 400, 401, 403, 404, 409, 415 |
| `PATCH /admin/data/lectures/{id}` | 200, 400, 401, 403, 404, 409, 415 |
| `DELETE /admin/data/lectures/{id}` | 200, 401, 403, 404 |
| `GET /admin/data/professor/all` | 200, 401, 403 |
| `POST /admin/data/professor` | 200, 400, 401, 403, 404, 409, 415 |
| `PATCH /admin/data/professor/{id}` | 200, 400, 401, 403, 404, 409, 415 |
| `DELETE /admin/data/professor/{id}` | 200, 401, 403, 404 |
| `GET /admin/reports` | 200, 401, 403 |
| `PATCH /admin/reports/{id}` | 200, 400, 401, 403, 404, 415 |
| `DELETE /admin/reports/{id}` | 200, 401, 403, 404 |
| `POST /admin/reports/{id}/gitlab-issue` | 200, 401, 403, 404, 502, 503 |
| `GET /admin/audit-logs` | 200, 400, 401, 403 |
| `GET /admin/audit-logs/meta` | 200, 401, 403 |
| `POST /admin/audit-logs/{id}/revert` | 200, 401, 403, 404, 409 |
| `GET /admin/activity-logs` | 200, 400, 401, 403 |
| `GET /admin/activity-logs/meta` | 200, 401, 403 |
| `GET /admin/system/status` | 200, 401, 403 |
| `GET /health` | 200, 503 |
| `GET /data/lectures` | 200 |
| `GET /data/lectures/{lecture_id}` | 200, 404 |
| `GET /data/professor` | 200 |
| `GET /data/professor/{professor_id}` | 200, 404 |
| `GET /data/professor/id` | 200, 400 |

<!-- contract-table:end -->
