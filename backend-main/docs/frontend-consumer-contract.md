# Consumer Contract — Android Client

What this repository's Android client actually sends to, and actually reads from, the
backend. Extracted from call sites in `app/src/main/java/...`, not from the type
definitions. Endpoints that exist only as a type definition are listed separately under
[Defined but never called](#defined-but-never-called).

Generated: 2026-09-09. Client revision: `748ddbb` (branch `main`).

## Base URL and path resolution

| Item | Value | Evidence |
|---|---|---|
| Base URL | `https://8a1babdc-cf6d-4fdc-80a7-dc585f5853ed.ka.bw-cloud-instance.org/` | `RetrofitClient.java:31` |

> **This hostname is compiled into released builds, so the deployment cannot close it.** It
> resolves to the same server as `https://ratemyprofessor.dev` and still serves the API at the
> root, but it is no longer an access address and is not in `ADMIN_FRONTEND_ORIGINS`. Returning
> `444` there would break every installed copy of the app; a `301` would be worse, because OkHttp
> downgrades a redirected `POST` to `GET` and logins would fail as if the backend were down.
> Moving the base URL needs a released client first. See `docs/deployment.md`.

| Path prefix | **none** | every `@GET`/`@POST`/`@PATCH` value starts with `/`, so Retrofit resolves it against the host root |
| `/admin` prefix | **not used anywhere** | no route string in `APIService.java` contains `admin` |
| JSON codec | Gson, default field naming (Java field name == wire name) | `RetrofitClient.java:103-113` |
| `LocalDateTime` format | `DateTimeFormatter.ISO_LOCAL_DATE_TIME`, e.g. `2026-07-28T10:15:30` — **no offset, no `Z`** | `RetrofitClient.java:104-108` |
| Auth header | `Authorization: Bearer <authToken>` | `LoginSession.java` (`BEARER_PREFIX = "Bearer "`) |
| Connect / read timeout | 10 s / 10 s | `RetrofitClient.java:36,43` |
| DNS | IPv4 only; AAAA records are filtered out client-side | `RetrofitClient.java:70-81` |

**Paths below are exactly what the backend sees.** The client never rewrites them.

### Enum wire values

Gson serialises enums by constant name and, on the way in, maps an **unrecognised value to
`null`** rather than failing. Every enum the client accepts is therefore a closed set:

| Enum | Accepted values | Declared at |
|---|---|---|
| `VoteType` | `UP`, `DOWN` | `DomainModel/VoteType.java:6-17` |
| `BugSeverity` | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` | `DomainModel/BugSeverity.java:3-8` |
| `SemesterSeason` | `SS`, `WS` | `DomainModel/SemesterSeason.java:6-17` |
| `ContentStatus` | `VISIBLE`, `HIDDEN`, `DELETED` | `ServerCommunication/ContentStatus.java:9-25` |
| `NotificationType` | `ANSWER`, `WARNING` | `Responses/NotificationType.java:3-6` |
| `LectureType` | `LECTURE_ONLY`, `LECTURE_AND_EXERCISE` | `DomainModel/LectureType.java:5-63` |
| `ReportReason` | `SPAM`, `INSULT`, `HARASSMENT`, `HATE_SPEECH`, `PERSONAL_INFORMATION`, `OFF_TOPIC`, `INAPPROPRIATE_CONTENT`, `OTHER` | `ServerCommunication/ReportReason.java:3-23` |
| `RatingCategory` | `OVERALL`, `ROOM`, `ORGANIZATION`, `MATERIALS`, `WORKLOAD`, `LECTURE_UNDERSTANDABILITY`, `LECTURE_INTEREST`, `LECTURE_DIFFICULTY`, `LECTURE_STRUCTURE`, `LECTURE_PACE`, `LECTURE_EXAMPLES`, `PROFESSOR_ENGAGEMENT`, `PROFESSOR_COMMUNICATION`, `PROFESSOR_AVAILABILITY`, `PROFESSOR_EXAM_PREPARATION`, `EXERCISE_BOARDWORK`, `EXERCISE_QUESTIONS`, `EXERCISE_HELPFULNESS`, `EXERCISE_EXPLANATIONS`, `EXERCISE_EXAMPLES`, `EXERCISE_PACE`, `TUTOR_EXPLANATION`, `TUTOR_PREPARATION`, `TUTOR_MOTIVATION`, `TUTOR_FRIENDLINESS`, `TUTOR_FEEDBACK` | `DomainModel/RatingCategory.java:3-61` |

### How to read the `success_determination` field

- `http_only` — the client branches on `response.isSuccessful()` (HTTP 200–299) and ignores
  the body's `success` field entirely.
- `both` — the client requires `response.isSuccessful()` **and** `body.success == true`.
- `none` — the client checks neither and reads the payload straight away.

No endpoint in this client uses `body.success` *without* also checking the HTTP status.

---

## Endpoints

### 1. `POST /auth/request-login`

```yaml
path: /auth/request-login
verb: POST
declared_at: APIService.java:50
called_from: [Activities/LoginActivity.java:107]
auth_header: none
request_body:
  - {name: email,      type: string, required: true, example: "abcde@student.kit.edu"}
  - {name: loginToken, type: string, required: true, example: "", note: always sent as empty string, never omitted}
query_params: []
response_fields_read:
  - {path: success, read_at: "LoginActivity.java:123"}
  - {path: message, read_at: "LoginActivity.java:126,128", note: only surfaced to the user on a 2xx response}
response_fields_declared_but_unread:
  - authToken
expected_success_status: "200 (any 2xx)"
handled_error_statuses:
  - {status: "any non-2xx", behavior: "errorBody read as raw text and logged; toast shows R.string.login_request_failed, NOT the server message", at: "LoginActivity.java:123-130,167-177"}
  - {status: "200 + success:false", behavior: "toast shows body.message, stays on the login screen", at: "LoginActivity.java:123-130"}
success_determination: both
```

Client-side precondition: the address must match the regex `[A-Za-z]{5}@student.kit.edu`
(`LoginActivity.java:91`) — exactly five ASCII letters. The request is not sent at all
otherwise, and it is also skipped when the device reports no internet
(`LoginActivity.java:112`).

On a real 4xx, Retrofit routes the payload to `errorBody()`, so `response.body()` is `null`
and the server's `message` never reaches the user — only the generic string resource does.
The server's text is written to logcat only.

### 2. `POST /auth/login`

```yaml
path: /auth/login
verb: POST
declared_at: APIService.java:61
called_from: [Activities/EnterAuthTokenActivity.java:72]
auth_header: none
request_body:
  - {name: email,      type: string, required: true, example: "abcde@student.kit.edu", note: read from local storage, not re-entered}
  - {name: loginToken, type: string, required: true, example: "123456", note: the one-time token the user typed}
query_params: []
response_fields_read:
  - {path: success,   read_at: "EnterAuthTokenActivity.java:90"}
  - {path: message,   read_at: "EnterAuthTokenActivity.java:95,99"}
  - {path: authToken, read_at: "EnterAuthTokenActivity.java:107", note: stored verbatim and later prefixed with "Bearer "}
response_fields_declared_but_unread: []
expected_success_status: "200 (any 2xx) with authToken present"
handled_error_statuses:
  - {status: "200 + success:false", behavior: "toast R.string.login_failed (or body.message when non-empty)", at: "EnterAuthTokenActivity.java:90-101"}
  - {status: "any non-2xx", behavior: "toast R.string.login_server_unreachable — the client labels every 4xx as a server-reachability problem", at: "EnterAuthTokenActivity.java:96-98"}
success_determination: both
```

This is the only endpoint that distinguishes a 2xx-with-`success:false` from a non-2xx in
its user-facing message, and it only distinguishes those two buckets — no individual status
code is inspected. `authToken` is stored without a null check
(`EnterAuthTokenActivity.java:107`); a 200 with `success:true` and no `authToken` writes
`null` into the session and every later request sends `Authorization: Bearer null`.

### 3. `POST /auth/logout-all`

```yaml
path: /auth/logout-all
verb: POST
declared_at: APIService.java:91
called_from: [Fragments/SettingsFragment.java:306]
auth_header: "Authorization: Bearer <authToken>"
request_body: []
request_body_note: "POST with no @Body — Retrofit sends Content-Length: 0 and no Content-Type header"
query_params: []
response_fields_read:
  - {path: success, read_at: "SettingsFragment.java:324"}
response_fields_declared_but_unread:
  - message
expected_success_status: "200 (any 2xx)"
handled_error_statuses:
  - {status: "any non-2xx, or 200 + success:false", behavior: "toast R.string.logout_all_devices_failed; local session is deliberately NOT cleared", at: "SettingsFragment.java:324-327"}
success_determination: both
```

The local session is cleared only after the server confirms
(`SettingsFragment.java:329`), by design — see the comment at `SettingsFragment.java:290-295`.
Guarded by a connectivity check at `SettingsFragment.java:308`.

### 4. `PATCH /account/deleteAccount`

```yaml
path: /account/deleteAccount
verb: PATCH
declared_at: APIService.java:102
called_from: [Fragments/SettingsFragment.java:374]
auth_header: "Authorization: Bearer <authToken>"
request_body: []
request_body_note: "PATCH with no @Body — Retrofit sends Content-Length: 0 and no Content-Type header"
query_params: []
response_fields_read:
  - {path: success, read_at: "SettingsFragment.java:392"}
response_fields_declared_but_unread:
  - message
expected_success_status: "200 (any 2xx)"
handled_error_statuses:
  - {status: "any non-2xx, or 200 + success:false", behavior: "toast R.string.delete_account_failed; session kept", at: "SettingsFragment.java:392-395"}
success_determination: both
```

Note the verb: **PATCH**, not DELETE. The path is camelCase (`deleteAccount`) while every
other route segment in the API is lowercase or kebab-case.

### 5. `GET /data/professor`

```yaml
path: /data/professor
verb: GET
declared_at: APIService.java:112
called_from: [ServerCommunication/Database.java:135]
auth_header: none
request_body: []
query_params: []
response_fields_read:
  - {path: "professors[]",              read_at: "Database.java:136 (method reference ProfessorsResponse::professors)"}
  - {path: "professors[].id",           read_at: "Database.java:238; Room primary key"}
  - {path: "professors[].firstName",    read_at: "ProfessorAdapter.java:44, ProfileFragment.java:147 (via Room round-trip)"}
  - {path: "professors[].lastName",     read_at: "ProfessorAdapter.java:44, ProfileFragment.java:148 (via Room round-trip)"}
  - {path: "professors[].averageRating", read_at: "ProfileFragment.java:118,120 (via Room round-trip); type is float client-side"}
  - {path: "professors[].ratingCount",  read_at: "stored in Room, Professor.java:16"}
response_fields_declared_but_unread:
  - message
  - success
expected_success_status: "200 (any 2xx) with a non-null professors array"
handled_error_statuses:
  - {status: "any non-2xx, or 2xx with null body/array", behavior: "logged, sync marked failed, no user-visible error", at: "Database.java:186-197"}
success_determination: http_only
```

Whole-table replace: the returned list becomes the local `professors` table
(`Database.java:137`). A successful empty array wipes the local cache.

### 6. `GET /data/professor/{professor_id}`

```yaml
path: /data/professor/{professor_id}
verb: GET
declared_at: APIService.java:121
called_from: [Fragments/RatingsFragment.java:747]
auth_header: none
path_params:
  - {name: professor_id, type: string (UUID), example: "3f2a9c14-...", source: "Professor.id previously received from /data/professor"}
request_body: []
query_params: []
response_fields_read:
  - {path: professor.averageRating, read_at: "RatingsFragment.java:754-755"}
response_fields_declared_but_unread:
  - message
  - success
  - professor.id
  - professor.firstName
  - professor.lastName
  - professor.active
  - professor.ratingCount
  - professor.lectureIds
expected_success_status: "200 (any 2xx) with a non-null professor object"
handled_error_statuses:
  - {status: "any non-2xx", behavior: "nothing at all — the branch at RatingsFragment.java:751 is simply not entered; no log, no toast", at: "RatingsFragment.java:751"}
success_determination: http_only
```

Called only as a refresh after a successful `POST /ratings/rate`
(`RatingsFragment.java:722`). `professor` is dereferenced without a null check
(`RatingsFragment.java:754`).

### 7. `GET /data/lectures`

```yaml
path: /data/lectures
verb: GET
declared_at: APIService.java:131
called_from: [ServerCommunication/Database.java:144]
auth_header: none
request_body: []
query_params: []
response_fields_read:
  - {path: "lectures[]",                 read_at: "Database.java:145 (LecturesResponse::lectures)"}
  - {path: "lectures[].id",              read_at: "Database.java:227"}
  - {path: "lectures[].name",            read_at: "Database.java:228,238"}
  - {path: "lectures[].code",            read_at: "Database.java:229"}
  - {path: "lectures[].semesterYear",    read_at: "Database.java:230"}
  - {path: "lectures[].semesterSeason",  read_at: "Database.java:231"}
  - {path: "lectures[].active",          read_at: "Database.java:232"}
  - {path: "lectures[].commentCount",    read_at: "Database.java:233"}
  - {path: "lectures[].ratingCount",     read_at: "Database.java:234"}
  - {path: "lectures[].averageRating",   read_at: "Database.java:235"}
  - {path: "lectures[].professors[].id", read_at: "Database.java:238"}
response_fields_declared_but_unread:
  - message
  - success
  - "lectures[].professors[].firstName"
  - "lectures[].professors[].lastName"
  - "lectures[].professors[].averageRating"
  - "lectures[].professors[].ratingCount"
expected_success_status: "200 (any 2xx) with a non-null lectures array"
handled_error_statuses:
  - {status: "any non-2xx, or 2xx with null body/array", behavior: "logged, sync marked failed, no user-visible error", at: "Database.java:186-197"}
success_determination: http_only
```

The element type here is `LectureModel` (`DomainModel/LectureModel.java`), whose
`professors` is a `List<Professor>` — the **full** professor shape, not the short one used
by `LectureResponse`. `lectures[].professors` is iterated without a null check
(`Database.java:237`). Whole-table replace, same as `/data/professor`.

### 8. `GET /data/lectures/{lecture_id}`

```yaml
path: /data/lectures/{lecture_id}
verb: GET
declared_at: APIService.java:141
called_from:
  - Fragments/RatingsFragment.java:640   # inside showRatings, to fill the header average
  - Fragments/RatingsFragment.java:782   # updateLectureRating, after a rating was submitted
auth_header: none
path_params:
  - {name: lecture_id, type: string (UUID), example: "9c1e...", source: "Lecture.id from /data/lectures"}
request_body: []
query_params: []
response_fields_read:
  - {path: lecture.averageRating, read_at: "RatingsFragment.java:648, RatingsFragment.java:789"}
response_fields_declared_but_unread:
  - message
  - success
  - lecture.id
  - lecture.name
  - lecture.code
  - lecture.semesterYear
  - lecture.semesterSeason
  - lecture.semesterLabel
  - lecture.title
  - lecture.active
  - lecture.lectureType
  - lecture.professors
  - lecture.commentCount
  - lecture.ratingCount
expected_success_status: "200 (any 2xx) with a non-null lecture object"
handled_error_statuses:
  - {status: "any non-2xx", behavior: "nothing — empty onFailure at RatingsFragment.java:660-663; no log, no toast, header keeps its stale value", at: "RatingsFragment.java:647,786"}
success_determination: http_only
```

The response type `LectureResponse` carries `semesterLabel`, `title` and `lectureType`,
which **no call site anywhere in the client reads**. Only `averageRating` is consumed here.
`lecture()` is dereferenced without a null check at both call sites, and at
`RatingsFragment.java:648` `response.body()` itself is unchecked.

### 9. `POST /social/comments`

```yaml
path: /social/comments
verb: POST
declared_at: APIService.java:152
called_from: [Fragments/RatingsFragment.java:331]
auth_header: "Authorization: Bearer <authToken>"
request_body:
  - {name: lectureID, type: string (UUID), required: true, example: "9c1e...", note: "capital ID — differs from lectureId used by /ratings/rate"}
  - {name: content,   type: string, required: true, example: "Great lecture", note: "sent raw, NOT trimmed; the submit button is only enabled for non-blank input (RatingsFragment.java:303-305)"}
query_params: []
response_fields_read: []
response_fields_declared_but_unread:
  - message
  - success
  - authToken
expected_success_status: "200 (any 2xx)"
handled_error_statuses:
  - {status: "any non-2xx", behavior: 'toast "Your comment couldn''t be sent."', at: "RatingsFragment.java:334-337"}
success_determination: http_only
```

Declared as `Call<ServerResponse>` but the body is never touched — a 200 with
`success:false` is reported to the user as a **success**. The comment list is not reloaded
after posting, so the new comment appears only after the screen is re-entered.

### 10. `GET /social/comments/{lecture_id}`

```yaml
path: /social/comments/{lecture_id}
verb: GET
declared_at: APIService.java:163
called_from:
  - Fragments/RatingsFragment.java:257       # comment list of the offering
  - Fragments/NotificationFragment.java:318  # resolving a notification to its comment thread
auth_header: none
path_params:
  - {name: lecture_id, type: string (UUID), example: "9c1e...", source: Lecture.id}
request_body: []
query_params: []
response_fields_read:
  - {path: "comments[]",                    read_at: "RatingsFragment.java:262,268; NotificationFragment.java:331,335"}
  - {path: "comments[].commentID",          read_at: "RatingsFragment.java:210; CommentsAdapter.java:81,91,96,99; NotificationFragment.java:373"}
  - {path: "comments[].content",            read_at: "RatingsFragment.java:212; CommentsAdapter.java:67"}
  - {path: "comments[].status",             read_at: "RatingsFragment.java:213 (persisted to Room; never branched on)"}
  - {path: "comments[].studentUsername",    read_at: "RatingsFragment.java:214; CommentsAdapter.java:66"}
  - {path: "comments[].lectureID",          read_at: "RatingsFragment.java:215"}
  - {path: "comments[].profilePicture",     read_at: "CommentsAdapter.java:60-62; ReplyFragment.java:76-78", note: "must be an absolute URL; the client does .replaceFirst(\"svg\",\"png\") and hands it to Glide"}
  - {path: "comments[].createdAt",          read_at: "RatingsFragment.java:216"}
  - {path: "comments[].downVotes",          read_at: "RatingsFragment.java:217; CommentsAdapter.java:69,80,90"}
  - {path: "comments[].upVotes",            read_at: "RatingsFragment.java:218; CommentsAdapter.java:68,79,89"}
  - {path: "comments[].answers[]",          read_at: "RatingsFragment.java:199-202; CommentsAdapter.java:98; ReplyFragment.java:144,148"}
  - {path: "comments[].answers[].*",        read_at: "same accessors as the parent — answers are Comment objects, not a separate answer type"}
  - {path: success,                         read_at: "NotificationFragment.java:330 only"}
response_fields_declared_but_unread:
  - message
expected_success_status: "200 (any 2xx) with a non-null comments array"
handled_error_statuses:
  - {status: "any non-2xx (RatingsFragment)", behavior: "logged only, silently keeps the Room-cached list", at: "RatingsFragment.java:262-265"}
  - {status: "any non-2xx (NotificationFragment)", behavior: 'toast "Could not load Comments"', at: "NotificationFragment.java:328-334"}
success_determination: "http_only at RatingsFragment.java:262; both at NotificationFragment.java:328-331 — the two call sites disagree"
```

Replies come back nested inside `comments[].answers[]` as full `Comment` objects; there is
no separate answers endpoint for reading. The client flattens them into a local table with a
`parentID` (`RatingsFragment.java:195-220`). `answers` is null-checked in
`RatingsFragment.java:199` but not in `CommentsAdapter.java:98` or `ReplyFragment.java:144`.

### 11. `GET /ratings/{lectureId}`

```yaml
path: /ratings/{lectureId}
verb: GET
declared_at: APIService.java:173
called_from: [Fragments/RatingsFragment.java:358]
auth_header: none
path_params:
  - {name: lectureId, type: string (UUID), example: "9c1e...", source: Lecture.id}
request_body: []
query_params: []
response_fields_read:
  - {path: "ratings[]",          read_at: "RatingsFragment.java:385,390,394"}
  - {path: "ratings[].category", read_at: "RatingsFragment.java:392,393; RatingAdapter.java:97-98; RatingsFragment.java:862"}
  - {path: "ratings[].value",    read_at: "RatingAdapter.java:99-102; RatingsFragment.java:862"}
response_fields_declared_but_unread:
  - message
  - success
expected_success_status: "200 (any 2xx) with a non-null ratings array"
handled_error_statuses:
  - {status: "any non-2xx", behavior: "status logged only; the screen silently keeps whatever the Room cache held", at: "RatingsFragment.java:385-388"}
success_determination: http_only
```

Requirements this call site places on the payload:

- `ratings` must be a **mutable** list — `removeIf` is called on it twice
  (`RatingsFragment.java:392-393`). Gson produces an `ArrayList`, so this holds today.
- Entries whose `category` is `null` (i.e. a category name this client does not know) and
  entries with `category == OVERALL` are dropped client-side.
- An **empty** `ratings` array is a meaningful state: it triggers
  `GET /ratings/{lectureId}/categories` to render an unrated skeleton
  (`RatingsFragment.java:394-397`).

Only requested when `AuthManager` reports a logged-in user (`RatingsFragment.java:364`);
otherwise the screen renders from the Room cache and no request is made.

### 12. `GET /ratings/own/{lectureId}`

```yaml
path: /ratings/own/{lectureId}
verb: GET
declared_at: APIService.java:279
called_from: [Fragments/RatingsFragment.java:415]
auth_header: "Authorization: Bearer <authToken>"
path_params:
  - {name: lectureId, type: string (UUID), example: "9c1e...", source: Lecture.id}
request_body: []
query_params: []
response_fields_read:
  - {path: "ratings[]",          read_at: "RatingsFragment.java:424-432"}
  - {path: "ratings[].category", read_at: "RatingsFragment.java:434-437; RatingAdapter.java:88-89; RatingsFragment.java:536"}
  - {path: "ratings[].value",    read_at: "RatingAdapter.java:90-93; RatingsFragment.java:537"}
response_fields_declared_but_unread:
  - message
  - success
expected_success_status: "200 (any 2xx) with a non-null ratings array"
handled_error_statuses:
  - {status: "any non-2xx", behavior: "falls back to the Room-cached user ratings; no log, no user-visible error", at: "RatingsFragment.java:424-428"}
success_determination: http_only
```

Shares the `RatingAverageResponse` shape with `GET /ratings/{lectureId}`, but the values are
this user's own scores. Chained after the averages call succeeds
(`RatingsFragment.java:399`). Same client-side filtering: `null` and `OVERALL` categories
are dropped (`RatingsFragment.java:434-437`).

**Path-collision note for the backend:** `/ratings/own/{lectureId}` and
`/ratings/{lectureId}` overlap on the first segment. If `/ratings/{lectureId}` is matched
first by the router, the literal `own` is captured as a `lectureId`.

### 13. `GET /ratings/{lectureId}/categories`

```yaml
path: /ratings/{lectureId}/categories
verb: GET
declared_at: APIService.java:267
called_from: [Fragments/RatingsFragment.java:545]
auth_header: none
path_params:
  - {name: lectureId, type: string (UUID), example: "9c1e...", source: Lecture.id}
request_body: []
query_params: []
response_fields_read:
  - {path: "categories[]",         read_at: "RatingsFragment.java:551,558-559"}
  - {path: "categories[].category", read_at: "RatingsFragment.java:561"}
response_fields_declared_but_unread:
  - message
  - success
  - "categories[].displayName"
  - "categories[].defaultWeight"
expected_success_status: "200 (any 2xx) with a non-null categories array"
handled_error_statuses:
  - {status: "any non-2xx", behavior: "status logged only; the rating list stays empty", at: "RatingsFragment.java:551-554"}
success_determination: http_only
```

`displayName` and `defaultWeight` are sent by the server but the client **ignores both** and
uses its own hard-coded copies from `DomainModel/RatingCategory.java:3-61` — see
`RatingAdapter.java:89,98` (`category.getDisplayName()`) and
`logic/WeightedRatingCalc`. A server-side change to a display name or a weight has no effect
on this client.

Reached only when `GET /ratings/{lectureId}` returned an empty `ratings` array
(`RatingsFragment.java:394-397`).

### 14. `POST /ratings/rate`

```yaml
path: /ratings/rate
verb: POST
declared_at: APIService.java:181
called_from: [Fragments/RatingsFragment.java:705]
auth_header: "Authorization: Bearer <authToken>"
request_body:
  - {name: lectureId, type: string (UUID), required: true, example: "9c1e...", note: "lowercase d — differs from lectureID used by /social/comments"}
  - {name: topics,    type: "array<{category, value}>", required: true, example: '[{"category":"ROOM","value":4.0}]'}
  - {name: "topics[].category", type: "string (RatingCategory constant name)", required: true, example: "PROFESSOR_ENGAGEMENT"}
  - {name: "topics[].value",    type: double, required: true, example: 4.0, note: "star widget value; OVERALL is never sent"}
query_params: []
response_fields_read: []
response_fields_declared_but_unread:
  - message
  - success
  - authToken
expected_success_status: "200 (any 2xx)"
handled_error_statuses:
  - {status: "any non-2xx", behavior: 'toast "Your rating couldn''t be sent."', at: "RatingsFragment.java:708-713"}
success_determination: http_only
```

Client-side precondition: **every** category currently shown must have been touched by the
user, otherwise the request is not sent (`RatingsFragment.java:680-688`). The body contains
only the changed categories (`RatingsFragment.java:693-695`), which after that check means
all of them.

Declared as `Call<ServerResponse>` but the body is never read — a 200 with `success:false`
is shown to the user as "Rating submitted successfully" and triggers the three refresh
calls at `RatingsFragment.java:721-723`.

### 15. `POST /social/answers`

```yaml
path: /social/answers
verb: POST
declared_at: APIService.java:194
called_from: [Fragments/ReplyFragment.java:168]
auth_header: "Authorization: Bearer <authToken>"
request_body:
  - {name: commentID, type: string (UUID), required: true, example: "5b7d...", note: "the parent comment being answered"}
  - {name: content,   type: string, required: true, example: "I agree", note: "trimmed before sending (ReplyFragment.java:155); empty is rejected client-side"}
query_params: []
response_fields_read:
  - {path: success, read_at: "ReplyFragment.java:177"}
response_fields_declared_but_unread:
  - message
expected_success_status: "200 (any 2xx)"
handled_error_statuses:
  - {status: "any non-2xx, or 200 + success:false", behavior: 'toast "Reply couldn''t be sent."; input is kept so the user can retry', at: "ReplyFragment.java:177-180"}
success_determination: both
```

### 16. `POST /social/comments/vote/comment/{comment_id}`

```yaml
path: /social/comments/vote/comment/{comment_id}
verb: POST
declared_at: APIService.java:200
called_from: [logic/CommentHelper.java:143]
auth_header: "Authorization: Bearer <authToken>"
path_params:
  - {name: comment_id, type: string (UUID), example: "5b7d...", source: "Comment.commentID"}
request_body:
  - {name: voteType, type: "string enum", required: true, example: "UP", allowed: [UP, DOWN]}
query_params: []
response_fields_read: []
response_fields_declared_but_unread:
  - message
  - success
expected_success_status: "200 (any 2xx)"
handled_error_statuses:
  - {status: "any non-2xx", behavior: 'toast "Vote couldn''t be sent."; the optimistic counter increment is NOT rolled back', at: "CommentHelper.java:153-155"}
success_determination: http_only
```

The counter in the list is incremented before the request is sent
(`CommentsAdapter.java:79-80,89-90`) and never corrected afterwards. The client has no way
to express a vote withdrawal — `VoteType` has no `NONE` constant.

### 17. `POST /social/comments/vote/answer/{answer_id}`

```yaml
path: /social/comments/vote/answer/{answer_id}
verb: POST
declared_at: APIService.java:207
called_from: [logic/CommentHelper.java:145]
auth_header: "Authorization: Bearer <authToken>"
path_params:
  - {name: answer_id, type: string (UUID), example: "7ac2...", source: "Comment.commentID of a nested answer"}
request_body:
  - {name: voteType, type: "string enum", required: true, example: "DOWN", allowed: [UP, DOWN]}
query_params: []
response_fields_read: []
response_fields_declared_but_unread:
  - message
  - success
expected_success_status: "200 (any 2xx)"
handled_error_statuses:
  - {status: "any non-2xx", behavior: 'toast "Vote couldn''t be sent."; optimistic increment not rolled back', at: "CommentHelper.java:153-155"}
success_determination: http_only
```

Selected over endpoint 16 purely by `CommentType` at the call site
(`CommentHelper.java:142-146`); the id itself is a `commentID` in both cases.

### 18. `POST /social/comments/report`

```yaml
path: /social/comments/report
verb: POST
declared_at: APIService.java:241
called_from: [logic/CommentHelper.java:65]
auth_header: "Authorization: Bearer <authToken>"
request_body:
  - {name: commentID,    type: string (UUID), required: true, example: "5b7d..."}
  - {name: reportReason, type: "string enum", required: true, example: "SPAM", allowed: [SPAM, INSULT, HARASSMENT, HATE_SPEECH, PERSONAL_INFORMATION, OFF_TOPIC, INAPPROPRIATE_CONTENT, OTHER]}
  - {name: explanation,  type: string, required: true, example: "", note: "trimmed; may be an empty string — not validated client-side"}
query_params: []
response_fields_read:
  - {path: success, read_at: "CommentHelper.java:74", note: "logged only, never shown"}
response_fields_declared_but_unread:
  - message
expected_success_status: "200 (any 2xx)"
handled_error_statuses:
  - {status: "any non-2xx, or 200 + success:false", behavior: "written to logcat only — the user has already been told the report was submitted", at: "CommentHelper.java:74-76"}
success_determination: both
```

The confirmation toast and the dialog dismissal happen **before** the response arrives
(`CommentHelper.java:86-88`), so the outcome never reaches the user.

### 19. `POST /answers/report`

```yaml
path: /answers/report
verb: POST
declared_at: APIService.java:247
called_from: [logic/CommentHelper.java:67]
auth_header: "Authorization: Bearer <authToken>"
request_body:
  - {name: commentID,    type: string (UUID), required: true, example: "7ac2...", note: "the answer's id, still sent under the key commentID"}
  - {name: reportReason, type: "string enum", required: true, example: "OFF_TOPIC"}
  - {name: explanation,  type: string, required: true, example: ""}
query_params: []
response_fields_read:
  - {path: success, read_at: "CommentHelper.java:74"}
response_fields_declared_but_unread:
  - message
expected_success_status: "200 (any 2xx)"
handled_error_statuses:
  - {status: "any non-2xx, or 200 + success:false", behavior: "logged only", at: "CommentHelper.java:74-76"}
success_determination: both
```

**Path shape differs from every other social route**: `/answers/report`, not
`/social/answers/report`. Compare endpoint 15 (`/social/answers`) and endpoint 18
(`/social/comments/report`).

### 20. `GET /social/notifications`

```yaml
path: /social/notifications
verb: GET
declared_at: APIService.java:256
called_from: [Fragments/NotificationFragment.java:79]
auth_header: "Authorization: Bearer <authToken>"
request_body: []
query_params: []
response_fields_read:
  - {path: success,                                  read_at: "NotificationFragment.java:97"}
  - {path: message,                                  read_at: "NotificationFragment.java:100 (shown as the error toast)"}
  - {path: "notifications[]",                        read_at: "NotificationFragment.java:109-111"}
  - {path: "notifications[].notificationID",         read_at: "NotificationFragment.java:202", note: "WIRE NAME IS notificationID — @SerializedName at NotificationResponse.java:8"}
  - {path: "notifications[].type",                   read_at: "NotificationFragment.java:181,203-205", allowed: [ANSWER, WARNING]}
  - {path: "notifications[].message",                read_at: "NotificationFragment.java:184", note: "used as the preview text when type == WARNING"}
  - {path: "notifications[].content",                read_at: "NotificationFragment.java:185", note: "used as the preview text otherwise"}
  - {path: "notifications[].userName",               read_at: "NotificationFragment.java:189"}
  - {path: "notifications[].createdAt",              read_at: "NotificationFragment.java:191-197"}
  - {path: "notifications[].ownCommentID",           read_at: "NotificationFragment.java:212", note: "WIRE NAME IS ownCommentID — @SerializedName at NotificationResponse.java:14; used as parentCommentId when opening the thread"}
  - {path: "notifications[].lectureResponse",        read_at: "NotificationFragment.java:199,210-211"}
  - {path: "notifications[].lectureResponse.id",     read_at: "NotificationFragment.java:211", note: "used as the lectureId for GET /social/comments/{lecture_id}"}
  - {path: "notifications[].lectureResponse.name",   read_at: "NotificationFragment.java:228"}
  - {path: "notifications[].lectureResponse.code",   read_at: "NotificationFragment.java:230"}
  - {path: "notifications[].lectureResponse.semesterSeason", read_at: "NotificationFragment.java:222-224"}
  - {path: "notifications[].lectureResponse.semesterYear",   read_at: "NotificationFragment.java:226"}
response_fields_declared_but_unread:
  - "notifications[].lectureResponse.semesterLabel"
  - "notifications[].lectureResponse.title"
  - "notifications[].lectureResponse.active"
  - "notifications[].lectureResponse.lectureType"
  - "notifications[].lectureResponse.professors"
  - "notifications[].lectureResponse.commentCount"
  - "notifications[].lectureResponse.ratingCount"
  - "notifications[].lectureResponse.averageRating"
expected_success_status: "200 (any 2xx) with success:true"
handled_error_statuses:
  - {status: "any non-2xx, or 200 + success:false", behavior: "list emptied and a toast shown — body.message when a body exists, otherwise the German literal \"Benachrichtigungen konnten nicht geladen werden\"", at: "NotificationFragment.java:95-104"}
success_determination: both
```

There is **no read state on the wire**: the client hard-codes `isRead = false` for every
notification it receives (`NotificationFragment.java:207`). A notification is only shown as
read after the user taps it and `PATCH /social/notifications/{id}` succeeds within the same
screen session.

The nested lecture object is keyed `lectureResponse`, not `lecture`
(`NotificationResponse.java:12`).

### 21. `PATCH /social/notifications/{notification_id}`

```yaml
path: /social/notifications/{notification_id}
verb: PATCH
declared_at: APIService.java:261
called_from: [Fragments/NotificationFragment.java:276]
auth_header: "Authorization: Bearer <authToken>"
path_params:
  - {name: notification_id, type: string (UUID), example: "a91f...", source: "notifications[].notificationID"}
request_body: []
request_body_note: "PATCH with no @Body — Retrofit sends Content-Length: 0 and no Content-Type header"
query_params: []
response_fields_read:
  - {path: success, read_at: "NotificationFragment.java:287"}
response_fields_declared_but_unread:
  - message
expected_success_status: "200 (any 2xx) with success:true"
handled_error_statuses:
  - {status: "any non-2xx, or 200 + success:false", behavior: "status logged at WARN; the row stays visually unread; no user-visible error", at: "NotificationFragment.java:285-291"}
success_determination: both
```

Marks exactly one notification, fired from the row's click handler
(`NotificationFragment.java:246`). There is no bulk "mark all seen" call — the loop that
would have done that is commented out at `NotificationFragment.java:121-123`.

### 22. `GET /account/information`

```yaml
path: /account/information
verb: GET
declared_at: APIService.java:228
called_from: [Fragments/HomeFragment.java:99]
auth_header: "Authorization: Bearer <authToken>"
request_body: []
query_params: []
response_fields_read:
  - {path: username,         read_at: "HomeFragment.java:122,130"}
  - {path: profilePicture,   read_at: "HomeFragment.java:117-119,140", note: "must be an absolute URL; .replaceFirst(\"svg\",\"png\") is applied before fetching"}
  - {path: averageRating,    read_at: "HomeFragment.java:123,131", type: double}
  - {path: ratingsCount,     read_at: "HomeFragment.java:124,132", note: "note the plural: ratingsCount, not ratingCount"}
  - {path: commentsWritten,  read_at: "HomeFragment.java:125,133"}
  - {path: credibilityScore, read_at: "HomeFragment.java:126,134"}
response_fields_declared_but_unread:
  - message
  - success
expected_success_status: "200 (any 2xx)"
handled_error_statuses:
  - {status: 401, behavior: "handleExpiredSession — the only status-specific branch in the whole client", at: "HomeFragment.java:105-108"}
  - {status: "any other non-2xx", behavior: "status logged; the cached profile stays on screen", at: "HomeFragment.java:111-114"}
success_determination: http_only
```

Only requested when the user is logged in and the device reports connectivity
(`HomeFragment.java:87-90`).

`Responses/PersonalStatisticsResponse.java` describes the same payload with **different
field names** (`ratingCount`, `writtencomments`, `float averageRating`) and is never used by
any call site — see the findings document.

### 23. `GET /account/ratings`

```yaml
path: /account/ratings
verb: GET
declared_at: APIService.java:236
called_from: [Fragments/ListOfMyRatingsFragment.java:103]
auth_header: "Authorization: Bearer <authToken>"
request_body: []
query_params: []
response_fields_read:
  - {path: "ratings[]",                        read_at: "ListOfMyRatingsFragment.java:109"}
  - {path: "ratings[].overallRating",          read_at: "ListOfMyRatingsAdapter.java:44-45"}
  - {path: "ratings[].lecture.name",           read_at: "ListOfMyRatingsAdapter.java:46,57"}
  - {path: "ratings[].lecture.semesterSeason", read_at: "ListOfMyRatingsAdapter.java:50,55"}
  - {path: "ratings[].lecture.semesterYear",   read_at: "ListOfMyRatingsAdapter.java:51,55"}
  - {path: "ratings[].lecture.professors[0].id", read_at: "ListOfMyRatingsAdapter.java:56", note: "FIRST element only — see findings, order dependency"}
response_fields_declared_but_unread:
  - message
  - success
  - "ratings[].lecture.id"
  - "ratings[].lecture.code"
  - "ratings[].lecture.semesterLabel"
  - "ratings[].lecture.title"
  - "ratings[].lecture.active"
  - "ratings[].lecture.lectureType"
  - "ratings[].lecture.commentCount"
  - "ratings[].lecture.ratingCount"
  - "ratings[].lecture.averageRating"
  - "ratings[].lecture.professors[].firstName"
  - "ratings[].lecture.professors[].lastName"
  - "ratings[].lecture.professors[].active"
expected_success_status: "200 (any 2xx) with a non-null ratings array"
handled_error_statuses: []
success_determination: none
```

**This call site checks nothing.** `response.body().ratings()` is dereferenced immediately
(`ListOfMyRatingsFragment.java:108-109`). Any non-2xx — and any 2xx whose body omits
`ratings` — throws an NPE on the main thread. See finding F-1.

`ratings[].lecture.professors` must be non-empty and must be ordered such that element `0`
is the professor this rating belongs to; the client picks `.get(0)` and uses that id to
navigate.

### 24. `POST /reports`

```yaml
path: /reports
verb: POST
declared_at: APIService.java:273
called_from: [Fragments/SettingsFragment.java:243]
auth_header: "Authorization: Bearer <authToken>"
request_body:
  - {name: title,       type: string, required: true, example: "Crash on rating submit", note: "trimmed; empty rejected client-side at SettingsFragment.java:245-248"}
  - {name: description, type: string, required: true, example: "Steps: ...", note: "trimmed; empty rejected client-side at SettingsFragment.java:249-252"}
  - {name: severity,    type: "string enum", required: true, example: "HIGH", allowed: [LOW, MEDIUM, HIGH, CRITICAL]}
query_params: []
response_fields_read:
  - {path: success, read_at: "SettingsFragment.java:258"}
response_fields_declared_but_unread:
  - message
expected_success_status: "200 (any 2xx) with success:true"
handled_error_statuses:
  - {status: "any non-2xx, or 200 + success:false", behavior: 'status logged and toast "Something went wrong"', at: "SettingsFragment.java:258-262"}
success_determination: both
```

This is the bug-report route. Path is a bare `/reports` — unrelated to
`/social/comments/report` and `/answers/report`, which are content reports.

### 25. `GET <absolute URL>` — profile and comment images

```yaml
path: "@Url — a full absolute URL taken from the payload, not a route on this API"
verb: GET
declared_at: APIService.java:253
called_from:
  - Fragments/HomeFragment.java:194            # own profile picture, via Retrofit
  - ServerCommunication/ImageRetriever.java:18 # DEAD: builds the Call but never enqueues it (returns null at line 19); only reachable from DomainModelTest.java:140
auth_header: none
request_body: []
query_params: []
response_fields_read:
  - {path: "<raw ResponseBody bytes>", read_at: "HomeFragment.java:198-199"}
expected_success_status: "200 (any 2xx) with a non-null body"
handled_error_statuses:
  - {status: "any non-2xx", behavior: "silently ignored; the cached file on disk stays in use", at: "HomeFragment.java:198"}
success_determination: http_only
```

The URL comes from `/account/information` → `profilePicture` and from
`/social/comments/{lecture_id}` → `comments[].profilePicture`. In every case the client
applies `.replaceFirst("svg", "png")` before fetching
(`HomeFragment.java:194`, `CommentsAdapter.java:62`, `ReplyFragment.java:78`), so the
contract is: **the URL must be absolute and must contain the substring `svg`, and the same
URL with the first `svg` replaced by `png` must serve a raster image.**

Comment avatars do not go through Retrofit at all — `CommentsAdapter.java:61-63` and
`ReplyFragment.java:77-79` hand the URL straight to Glide, so those requests carry **no
`Authorization` header** and no client timeout configuration.

---

## Defined but never called

Present in `APIService.java`, but no call site exists anywhere in `app/src/main`. Verified
by searching for each method name across the whole source tree.

| Path | Verb | Declared at | Notes |
|---|---|---|---|
| `/auth/validate` | POST | `APIService.java:74-78` | Takes `Authorization` header + `LoginRequest` body, returns `ServerResponse`. Intended for skipping the login screen for a returning user; `StartActivity` does not call it. Referenced only by `APIServiceContractTest.java:65` (annotation check) and a fake in `ProfessorFeatureInstrumentedTest.java:225`. |
| `/social/sync/comments` | GET | `APIService.java:219-220` | Returns `CommentsResponse` for all comments. Referenced only by a fake in `ProfessorFeatureInstrumentedTest.java:287` and a switch case in `DatabaseSyncTest.java:162`. |

Response types that exist but are never bound to any call:

| Type | File | Notes |
|---|---|---|
| `PersonalStatisticsResponse` | `Responses/PersonalStatisticsResponse.java` | Duplicate of `StudentProfileResponse` with different field names (`ratingCount` vs `ratingsCount`, `writtencomments` vs `commentsWritten`, `float` vs `double averageRating`). No endpoint returns it. |
| `AnswerResponse` | `Responses/AnswerResponse.java` | Answers arrive nested as `Comment` objects inside `CommentsResponse`; this record is unused. |
| `LectureResponse.title`, `.semesterLabel`, `.lectureType` | `Responses/LectureResponse.java:14,15,17` | No call site reads them. |
| `ProfessorResponse.lectureIds` | `Responses/ProfessorResponse.java:12` | No call site reads it. |
| `RatingCategoryResponse.displayName`, `.defaultWeight` | `Responses/RatingCategoryResponse.java:7-8` | Server-provided, client uses its own hard-coded copies. |
| `DomainModel/Vote`, `DomainModel/RatingTopic`, `DomainModel/OfferingRating`, `DomainModel/UserStatistics` | `DomainModel/` | Not referenced by any call site. |

---

## Summary table 1 — all endpoints

| # | Path (backend-visible) | Verb | Calling module | Auth |
|---|---|---|---|---|
| 1 | `/auth/request-login` | POST | `Activities/LoginActivity` | — |
| 2 | `/auth/login` | POST | `Activities/EnterAuthTokenActivity` | — |
| 3 | `/auth/logout-all` | POST | `Fragments/SettingsFragment` | Bearer |
| 4 | `/account/deleteAccount` | PATCH | `Fragments/SettingsFragment` | Bearer |
| 5 | `/data/professor` | GET | `ServerCommunication/Database` | — |
| 6 | `/data/professor/{professor_id}` | GET | `Fragments/RatingsFragment` | — |
| 7 | `/data/lectures` | GET | `ServerCommunication/Database` | — |
| 8 | `/data/lectures/{lecture_id}` | GET | `Fragments/RatingsFragment` | — |
| 9 | `/social/comments` | POST | `Fragments/RatingsFragment` | Bearer |
| 10 | `/social/comments/{lecture_id}` | GET | `Fragments/RatingsFragment`, `Fragments/NotificationFragment` | — |
| 11 | `/ratings/{lectureId}` | GET | `Fragments/RatingsFragment` | — |
| 12 | `/ratings/own/{lectureId}` | GET | `Fragments/RatingsFragment` | Bearer |
| 13 | `/ratings/{lectureId}/categories` | GET | `Fragments/RatingsFragment` | — |
| 14 | `/ratings/rate` | POST | `Fragments/RatingsFragment` | Bearer |
| 15 | `/social/answers` | POST | `Fragments/ReplyFragment` | Bearer |
| 16 | `/social/comments/vote/comment/{comment_id}` | POST | `logic/CommentHelper` | Bearer |
| 17 | `/social/comments/vote/answer/{answer_id}` | POST | `logic/CommentHelper` | Bearer |
| 18 | `/social/comments/report` | POST | `logic/CommentHelper` | Bearer |
| 19 | `/answers/report` | POST | `logic/CommentHelper` | Bearer |
| 20 | `/social/notifications` | GET | `Fragments/NotificationFragment` | Bearer |
| 21 | `/social/notifications/{notification_id}` | PATCH | `Fragments/NotificationFragment` | Bearer |
| 22 | `/account/information` | GET | `Fragments/HomeFragment` | Bearer |
| 23 | `/account/ratings` | GET | `Fragments/ListOfMyRatingsFragment` | Bearer |
| 24 | `/reports` | POST | `Fragments/SettingsFragment` | Bearer |
| 25 | `<absolute URL from payload>` | GET | `Fragments/HomeFragment`, `Adapters/CommentsAdapter` (Glide), `Fragments/ReplyFragment` (Glide) | — |
| — | `/auth/validate` | POST | **not called** | Bearer |
| — | `/social/sync/comments` | GET | **not called** | — |

## Summary table 2 — fields read, per endpoint

Only fields a call site actually consumes. `—` means the client parses the response type but
reads nothing out of it.

| # | Path | Fields read |
|---|---|---|
| 1 | `/auth/request-login` | `success`, `message` |
| 2 | `/auth/login` | `success`, `message`, `authToken` |
| 3 | `/auth/logout-all` | `success` |
| 4 | `/account/deleteAccount` | `success` |
| 5 | `/data/professor` | `professors[]`, `professors[].id`, `.firstName`, `.lastName`, `.averageRating`, `.ratingCount` |
| 6 | `/data/professor/{professor_id}` | `professor.averageRating` |
| 7 | `/data/lectures` | `lectures[]`, `lectures[].id`, `.name`, `.code`, `.semesterYear`, `.semesterSeason`, `.active`, `.commentCount`, `.ratingCount`, `.averageRating`, `lectures[].professors[].id` |
| 8 | `/data/lectures/{lecture_id}` | `lecture.averageRating` |
| 9 | `/social/comments` | — |
| 10 | `/social/comments/{lecture_id}` | `success` (NotificationFragment only), `comments[]`, `comments[].commentID`, `.content`, `.status`, `.studentUsername`, `.lectureID`, `.profilePicture`, `.createdAt`, `.downVotes`, `.upVotes`, `.answers[]` (recursively the same fields) |
| 11 | `/ratings/{lectureId}` | `ratings[]`, `ratings[].category`, `ratings[].value` |
| 12 | `/ratings/own/{lectureId}` | `ratings[]`, `ratings[].category`, `ratings[].value` |
| 13 | `/ratings/{lectureId}/categories` | `categories[]`, `categories[].category` |
| 14 | `/ratings/rate` | — |
| 15 | `/social/answers` | `success` |
| 16 | `/social/comments/vote/comment/{comment_id}` | — |
| 17 | `/social/comments/vote/answer/{answer_id}` | — |
| 18 | `/social/comments/report` | `success` (logged only) |
| 19 | `/answers/report` | `success` (logged only) |
| 20 | `/social/notifications` | `success`, `message`, `notifications[]`, `notifications[].notificationID`, `.type`, `.message`, `.content`, `.userName`, `.createdAt`, `.ownCommentID`, `notifications[].lectureResponse.id`, `.name`, `.code`, `.semesterSeason`, `.semesterYear` |
| 21 | `/social/notifications/{notification_id}` | `success` |
| 22 | `/account/information` | `username`, `profilePicture`, `averageRating`, `ratingsCount`, `commentsWritten`, `credibilityScore` |
| 23 | `/account/ratings` | `ratings[]`, `ratings[].overallRating`, `ratings[].lecture.name`, `.semesterSeason`, `.semesterYear`, `ratings[].lecture.professors[0].id` |
| 24 | `/reports` | `success` |
| 25 | `<absolute URL>` | raw body bytes |

## Summary table 3 — status handling, per endpoint

"Distinct branches" counts user-visible or control-flow-affecting outcomes, not log lines.

| # | Path | Success determined by | Explicit status branches | Behaviour on unhandled 4xx/5xx |
|---|---|---|---|---|
| 1 | `/auth/request-login` | both | none | generic toast, server `message` not shown |
| 2 | `/auth/login` | both | 2xx vs non-2xx only | toast "server unreachable" |
| 3 | `/auth/logout-all` | both | none | generic toast, session kept |
| 4 | `/account/deleteAccount` | both | none | generic toast, session kept |
| 5 | `/data/professor` | http_only | none | logged, sync marked failed, silent to user |
| 6 | `/data/professor/{professor_id}` | http_only | none | **nothing at all** |
| 7 | `/data/lectures` | http_only | none | logged, sync marked failed, silent to user |
| 8 | `/data/lectures/{lecture_id}` | http_only | none | **nothing at all** |
| 9 | `/social/comments` | http_only | none | generic toast |
| 10 | `/social/comments/{lecture_id}` | http_only (Ratings) / both (Notification) | none | Ratings: logged only. Notification: toast |
| 11 | `/ratings/{lectureId}` | http_only | none | logged only, stale cache shown |
| 12 | `/ratings/own/{lectureId}` | http_only | none | silent fallback to Room cache |
| 13 | `/ratings/{lectureId}/categories` | http_only | none | logged only |
| 14 | `/ratings/rate` | http_only | none | generic toast |
| 15 | `/social/answers` | both | none | generic toast |
| 16 | `/social/comments/vote/comment/{comment_id}` | http_only | none | generic toast, UI not rolled back |
| 17 | `/social/comments/vote/answer/{answer_id}` | http_only | none | generic toast, UI not rolled back |
| 18 | `/social/comments/report` | both | none | logged only; user already told it succeeded |
| 19 | `/answers/report` | both | none | logged only; user already told it succeeded |
| 20 | `/social/notifications` | both | none | toast (`message`, or a German literal) |
| 21 | `/social/notifications/{notification_id}` | both | none | logged only |
| 22 | `/account/information` | http_only | **401** → clear session, return to login | logged only |
| 23 | `/account/ratings` | **none** | none | **NullPointerException on the main thread** |
| 24 | `/reports` | both | none | generic toast |
| 25 | `<absolute URL>` | http_only | none | silent, cached image kept |

**401 on 24 of 25 endpoints is not handled.** `GET /account/information`
(`HomeFragment.java:105`) is the only place an expired session is detected. Every other
authenticated call treats 401 as an ordinary failure and leaves the stale token in storage.

Transport-level failures (no connection, DNS, timeout, TLS) land in Retrofit's `onFailure`
and are separate from every status listed above. Three screens additionally pre-check
connectivity and skip the request entirely: `LoginActivity.java:112`,
`EnterAuthTokenActivity.java:80`, `SettingsFragment.java:308`, plus
`HomeFragment.java:87-88` and `MainViewModel.java:36`.

## The contract, as a table

Everything above is prose written by the client's authors, and it stays that way. This block
is the same claims in a shape a test can read: `ConsumerContractSweepTests` parses it and
checks, against the running application, that every route here is mapped with this verb and
that every field here exists in the response that route returns.

It is delimited rather than derived from the prose because the prose uses a leading-dot
shorthand for nested fields, and the two consumer documents resolve that shorthand
differently -- one against the element root, one against the previous path. A parser would
have to guess. Field paths here are written out in full instead, so nothing is inferred.

`[]` marks a list, `[0]` a client that reads one element by index, and `*` an open map whose
keys are data rather than schema -- resolution stops at a `*`, because there is nothing on the
Java side to match it against. `Auth` is what the client puts on the request, not what the
route requires.

<!-- consumer-contract:start -->

| Verb | Path | Auth | Fields the client reads |
| --- | --- | --- | --- |
| POST | /auth/request-login | none | success, message |
| POST | /auth/login | none | success, message, authToken |
| POST | /auth/logout-all | bearer | success |
| PATCH | /account/deleteAccount | bearer | success |
| GET | /data/professor | none | professors[], professors[].id, professors[].firstName, professors[].lastName, professors[].averageRating, professors[].ratingCount |
| GET | /data/professor/{professor_id} | none | professor.averageRating |
| GET | /data/lectures | none | lectures[], lectures[].id, lectures[].name, lectures[].code, lectures[].semesterYear, lectures[].semesterSeason, lectures[].active, lectures[].commentCount, lectures[].ratingCount, lectures[].averageRating, lectures[].professors[].id |
| GET | /data/lectures/{lecture_id} | none | lecture.averageRating |
| POST | /social/comments | bearer | - |
| GET | /social/comments/{lecture_id} | none | comments[], comments[].commentID, comments[].content, comments[].status, comments[].studentUsername, comments[].lectureID, comments[].profilePicture, comments[].createdAt, comments[].downVotes, comments[].upVotes, comments[].answers[], comments[].answers[].*, success |
| GET | /ratings/{lectureId} | none | ratings[], ratings[].category, ratings[].value |
| GET | /ratings/own/{lectureId} | bearer | ratings[], ratings[].category, ratings[].value |
| GET | /ratings/{lectureId}/categories | none | categories[], categories[].category |
| POST | /ratings/rate | bearer | - |
| POST | /social/answers | bearer | success |
| POST | /social/comments/vote/comment/{comment_id} | bearer | - |
| POST | /social/comments/vote/answer/{answer_id} | bearer | - |
| POST | /social/comments/report | bearer | success |
| POST | /answers/report | bearer | success |
| GET | /social/notifications | bearer | success, message, notifications[], notifications[].notificationID, notifications[].type, notifications[].message, notifications[].content, notifications[].userName, notifications[].createdAt, notifications[].ownCommentID, notifications[].lectureResponse, notifications[].lectureResponse.id, notifications[].lectureResponse.name, notifications[].lectureResponse.code, notifications[].lectureResponse.semesterSeason, notifications[].lectureResponse.semesterYear |
| PATCH | /social/notifications/{notification_id} | bearer | success |
| GET | /account/information | bearer | username, profilePicture, averageRating, ratingsCount, commentsWritten, credibilityScore |
| GET | /account/ratings | bearer | ratings[], ratings[].overallRating, ratings[].lecture.name, ratings[].lecture.semesterSeason, ratings[].lecture.semesterYear, ratings[].lecture.professors[0].id |
| POST | /reports | bearer | success |

<!-- consumer-contract:end -->
