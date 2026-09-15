# Consumer Findings — Android Client

Risks observed while extracting [frontend-consumer-contract.md](frontend-consumer-contract.md). **Read-only
audit — nothing in this repository was changed.** Every claim carries a file and line
reference; where the source does not settle a question it is marked *undetermined* with the
reason.

Client revision: `748ddbb` (branch `main`). Scope: `app/src/main/java/...`. Build output,
generated binding classes and `node_modules` were not read. Test sources were consulted only
to establish whether an endpoint is otherwise unused.

---

## 1. Status ignored in favour of the body

**The headline concern does not materialise in this client.** Every one of the ten
`body.success()` reads is guarded by `!response.isSuccessful() ||` in the same boolean
expression, so a real 404 or 409 is short-circuited before `success` is ever consulted.
There is no site that trusts `success` alone.

Verified exhaustively — all ten `.success()` reads:

| Site | Guard |
|---|---|
| `Activities/LoginActivity.java:123` | `!response.isSuccessful() \|\| body == null \|\| !body.success()` |
| `Activities/EnterAuthTokenActivity.java:90` | same shape |
| `Fragments/ReplyFragment.java:177` | same shape |
| `Fragments/SettingsFragment.java:258` | same shape |
| `Fragments/SettingsFragment.java:324` | same shape |
| `Fragments/SettingsFragment.java:392` | same shape |
| `logic/CommentHelper.java:74` | same shape |
| `Fragments/NotificationFragment.java:95-97` | same shape |
| `Fragments/NotificationFragment.java:285-287` | same shape |
| `Fragments/NotificationFragment.java:328-331` | same shape |

The inverse problem exists instead, and it is the one worth acting on. **Fifteen call sites
branch on the HTTP status only and never read `success` at all** (`success_determination:
http_only` in the contract). For those, a `200 + {"success": false}` is indistinguishable
from a real success. The move to genuine 404/409 makes these *safer*, not less safe — but
until the migration is complete on every route, these three are user-visible lies:

| Finding | Site | Consequence |
|---|---|---|
| F-1.1 | `Fragments/RatingsFragment.java:334-338` (`POST /social/comments`) | Response type is `ServerResponse`, body never touched. A `200 + success:false` shows *"Your comment is sent."* |
| F-1.2 | `Fragments/RatingsFragment.java:708-718` (`POST /ratings/rate`) | Same. A `200 + success:false` shows *"Rating submitted successfully."* and fires the three refresh calls at lines 721-723 |
| F-1.3 | `logic/CommentHelper.java:153-155` (both vote routes) | Body never read; a `200 + success:false` leaves the optimistic counter increment standing |

Two further sites where the body is ignored but the consequence is only a stale view rather
than a false confirmation: `RatingsFragment.java:647` and `RatingsFragment.java:751,786`.

**F-1.4 — the two `GET /social/comments/{lecture_id}` call sites disagree with each other.**
`NotificationFragment.java:328-331` requires `success == true`;
`RatingsFragment.java:262` does not. The same route is validated two different ways
depending on which screen asked.

**F-1.5 — server error messages never reach the user on a real 4xx.** On any non-2xx,
Retrofit routes the payload to `errorBody()` and `response.body()` is `null`. Every message
fallback in this client is written as *"use `body.message()` when non-empty, otherwise a
string resource"* — so the `body == null` branch always wins and the generic resource is
shown. Sites: `LoginActivity.java:126-129`, `EnterAuthTokenActivity.java:95-100`,
`NotificationFragment.java:99-103`. The only place the error body is read at all is
`LoginActivity.java:167-177`, and it goes to logcat as raw text, never to the UI. As the
backend moves from `200 + success:false` to real 4xx, **the diagnostic text the backend
sends stops being visible to users**, even though it used to be shown.

## 2. Missing error handling

**F-2.1 (critical) — `GET /account/ratings` checks nothing and will crash.**
`Fragments/ListOfMyRatingsFragment.java:107-110`:

```
public void onResponse(Call<UserRatingResponse> getRatingsCall, Response<UserRatingResponse> response) {
    UserRatingResponse body = response.body();
    List<UserRatingDto> ratings = body.ratings();
    runOnUiThread(() -> showRatings(ratings));
}
```

No `isSuccessful()`, no null check on `body`, no null check on `ratings`. Any 4xx or 5xx
makes `response.body()` null and throws a `NullPointerException` on the callback thread. A
2xx whose body omits `ratings` reaches `showRatings(null)` and then
`ListOfMyRatingsAdapter`. This is the single place where a status-code change on the backend
turns into a crash rather than a degraded view.

**F-2.2 — two endpoints have no failure path whatsoever.**

- `GET /data/professor/{professor_id}` — `RatingsFragment.java:751` enters its block only on
  success; there is no `else`, and `onFailure` at line 773 only prints a stack trace.
- `GET /data/lectures/{lecture_id}` — `RatingsFragment.java:647` likewise, and the
  `onFailure` at lines 660-663 is **empty**. Both run right after a rating submission, so a
  failure leaves the header showing a stale average with no indication anything went wrong.

**F-2.3 — 401 is unhandled on 24 of 25 endpoints.** The sole status-specific branch in the
whole client is `HomeFragment.java:105` (`response.code() == 401` →
`handleExpiredSession`). Confirmed by scanning every `response.code()` usage: the other
eleven occurrences are string interpolation into a log line, never a comparison. A revoked
token therefore produces a generic "couldn't be sent" toast on every authenticated route,
and the dead token stays in `SharedPreferences`.

Aggravating: `Activities/StartActivity.java:33-43` restores the logged-in state purely from
local storage (`session.isLoggedIn()`) and never calls `POST /auth/validate` — which exists
but is dead code. So an expired session is only ever discovered by whichever call happens
to fail first.

**F-2.4 — no status code other than 401 is inspected anywhere.** There is no handling for
400, 403, 404, 405, 409, 415, 429 or any 5xx. Every one collapses into the same generic
branch. The backend cannot signal *why* something failed in a way this client can act on.

**F-2.5 — content reports report success before the server answers.**
`logic/CommentHelper.java:86-88` shows `R.string.bug_report_submitted` and dismisses the
dialog unconditionally, immediately after `enqueue`. The actual outcome at
`CommentHelper.java:74-76` only reaches logcat. Both `POST /social/comments/report` and
`POST /answers/report` therefore appear to the user to always succeed. (The bug report at
`SettingsFragment.java:254-264` does this correctly, so the inconsistency is within one
feature area.)

**F-2.6 — inconsistent handling of transport failure vs. response failure.**
`SettingsFragment.java:266-269`: `onFailure` for `POST /reports` logs under the tag
`"comment report"` and shows **no toast at all**, while the response-level failure two lines
above does show one. A network drop during a bug report is silent.

**F-2.7 — vote UI is optimistic and never rolls back.** `Adapters/CommentsAdapter.java:79-80`
and `:89-91` increment the displayed counter before `listener.vote(...)` is invoked. The
failure branch at `CommentHelper.java:153-155` shows a toast but does not restore the
previous number, so the list keeps a count the server rejected until the screen is rebuilt.

## 3. Hard-coded paths and field names

**Paths are well centralised.** All 26 route strings (27 annotated methods, one of which is the bare `@GET` + `@Url` image fetch) live in
`ServerCommunication/APIService.java` and nowhere else — no path is assembled by string
concatenation at any call site. A route rename is a one-file change. Two exceptions:

- **F-3.1** — the base URL is duplicated: `RetrofitClient.java:31` and, as a literal,
  `app/src/test/java/com/example/ratemyprofessor/APIServiceContractTest.java:33`. Changing
  one without the other breaks the test.
- **F-3.2** — `docs/deployment.md:146,154,160,399,425` already flags the hard-coded base URL
  as an open question (no per-environment build variants).

**Field names are the opposite: scattered and inconsistent.** Wire names are Java record
component names spread across 24 files in `ServerCommunication/Requests/` and
`ServerCommunication/Responses/`, with no `@SerializedName` except two. A backend rename
breaks silently — Gson leaves the unmatched field `null`/`0` rather than failing.

Concrete inconsistencies a rename sweep should know about:

| # | Issue | Evidence |
|---|---|---|
| F-3.3 | The lecture id is `lectureID` when posting a comment but `lectureId` when posting a rating | `Requests/PostCommentRequest.java:10` vs `Requests/RatingRequest.java:6` |
| F-3.4 | An answer's id is sent under the key `commentID` when reporting an answer | `Requests/CommentReportRequest.java:6`, used for `/answers/report` at `CommentHelper.java:67` |
| F-3.5 | The profile payload is `ratingsCount` / `commentsWritten`, but the unused twin DTO says `ratingCount` / `writtencomments` | `Responses/StudentProfileResponse.java:9-10` vs `Responses/PersonalStatisticsResponse.java:8-10` |
| F-3.6 | Only two fields in the whole client depend on `@SerializedName`, both in notifications: wire `notificationID` → field `notificationId`, wire `ownCommentID` → field `ownCommentId` | `Responses/NotificationResponse.java:8-9,14-15` |
| F-3.7 | The nested lecture inside a notification is keyed `lectureResponse`, not `lecture` as everywhere else | `Responses/NotificationResponse.java:12` vs `Responses/LectureDetailResponse.java:6` |
| F-3.8 | `/answers/report` is the only social route without the `/social` prefix | `APIService.java:247` vs `:194`, `:241` |
| F-3.9 | `/account/deleteAccount` is the only camelCase path segment, and uses PATCH rather than DELETE | `APIService.java:102` |
| F-3.10 | Path placeholder naming is split between snake_case and camelCase: `{lecture_id}` at `:141,163` vs `{lectureId}` at `:173,267,279` | `APIService.java` |

**F-3.11 — `/ratings/own/{lectureId}` collides in shape with `/ratings/{lectureId}`.**
`APIService.java:279` and `:173`. If the backend router matches the single-segment pattern
first, the literal `own` is captured as a lecture id. Whether the backend actually orders
them safely is *undetermined from this repository* — the routing table is in the other repo.

**F-3.12 — enum constant names are the wire format for eight enums**, listed in the contract.
`RatingCategory` alone contributes 26 constants (`DomainModel/RatingCategory.java:3-61`) that
must match the backend exactly, in both directions (sent in `/ratings/rate`, received from
three rating endpoints).

## 4. Order dependency

**F-4.1 (real) — `ratings[].lecture.professors[0]` is taken as *the* professor.**
`Adapters/ListOfMyRatingsAdapter.java:56`:

```
String profId = rating.lecture().professors().get(0).id(); // Currently just choosing the first prof if multiple profs
```

The client's own comment acknowledges it. For a co-taught lecture the row navigates to
whichever professor the backend happened to serialise first. If that ordering is not stable
across requests, the same rating opens different professors on different loads. `.get(0)`
also throws `IndexOutOfBoundsException` on an empty array — see F-5.3.

**F-4.2 (real) — `ListOfMyRatingsFragment.java:149` takes `lectures.get(0)`** from a local
Room query. Local, not server-ordered, so the backend is not the cause — but the assumption
"one lecture per (professor, name, semester)" is the same one the backend must uphold for
F-4.1 to be meaningful.

**F-4.3 (no dependency) — rating lists are explicitly re-sorted client-side.**
`RatingsFragment.java:604-608` and `:617-620` sort by `category.getDisplayName()` with
`String.CASE_INSENSITIVE_ORDER` before display, with the comment *"so they always come out
in the same order"*. The client does **not** rely on the order of `ratings[]` from
`/ratings/{lectureId}`, `/ratings/own/{lectureId}` or `/ratings/{lectureId}/categories`.

**F-4.4 (no dependency) — comments and notifications are rendered in received order** but
nothing depends on it: `RatingsFragment.java:268` and `NotificationFragment.java:107-117`
just iterate. `NotificationFragment.java:368-378` searches the list by id rather than by
position. There is no client-side sort for either, so the display order *is* the backend's
order — a presentation concern, not a correctness one.

**F-4.5 — `formatUserRatings` pairs by category, not by index.**
`RatingsFragment.java:574-590` matches each average against the user rating with the same
`category()`, falling back to a synthetic zero. Correct regardless of how
`/ratings/own/{lectureId}` orders its array.

## 5. Field-presence assumptions

Ranked by consequence. Note that Gson's default behaviour makes all of these reachable from
the backend side: an absent JSON key leaves an object field `null`, and an **unrecognised
enum value also deserialises to `null`** rather than raising.

**F-5.1 — unknown enum value → `null` → NPE in the rating list.**
`Adapters/RatingAdapter.java:89` and `:98` call `userRating.category().getDisplayName()` and
`rating.category().getDisplayName()` with no null check. The averages path filters nulls out
first (`RatingsFragment.java:392`), but the **categories path does not**:
`RatingsFragment.java:561` builds `new TopicAverageResponse(category.category(), 0)` from
`/ratings/{lectureId}/categories` without filtering. So **adding a new `RatingCategory`
constant on the backend crashes this client's rating screen** for any lecture whose category
list contains it. The same is true of the sort comparators at
`RatingsFragment.java:606` and `:618`.

**F-5.2 — response wrapper objects dereferenced without a null check.**

| Site | Expression | Fails when |
|---|---|---|
| `RatingsFragment.java:648` | `response.body().lecture().averageRating()` | `body` null (2xx with no content) or `lecture` null/absent |
| `RatingsFragment.java:754-755` | `professorDetail.professor().averageRating()` | `professor` null/absent |
| `RatingsFragment.java:788-789` | `lectureDetail.lecture().averageRating()` | `lecture` null/absent |
| `ListOfMyRatingsFragment.java:109` | `body.ratings()` | `body` null — see F-2.1 |

**F-5.3 — nested collections dereferenced without a null or emptiness check.**

| Site | Expression | Fails when |
|---|---|---|
| `ListOfMyRatingsAdapter.java:46,50,55,57` | `rating.lecture().name()`, `.semesterSeason().name()`, `.semesterYear()` | `lecture` or `semesterSeason` null |
| `ListOfMyRatingsAdapter.java:56` | `rating.lecture().professors().get(0).id()` | `professors` null **or empty** |
| `ServerCommunication/Database.java:237` | `for (Professor professor : model.professors())` | `lectures[].professors` null — aborts the whole lecture sync |
| `Adapters/CommentsAdapter.java:98` | `comment.answers().size()` | `answers` null |
| `Fragments/ReplyFragment.java:144` | `comment.answers().size()` | `answers` null |

`RatingsFragment.java:199` *does* null-check `comment.answers()` before iterating, so the
same field is treated as optional in one file and mandatory in two others.

**F-5.4 — `authToken` stored without a check.** `EnterAuthTokenActivity.java:107` calls
`session.saveTokens(token, body.authToken())` on the success path. A `200 + success:true`
that omits `authToken` writes `null`, and `LoginSession.bearerToken()` then produces the
literal string `"Bearer null"` on every subsequent request.

**F-5.5 — image URLs are assumed absolute and assumed to contain `svg`.**
`HomeFragment.java:194`, `CommentsAdapter.java:62` and `ReplyFragment.java:78` all apply
`.replaceFirst("svg", "png")` to the URL the backend supplied and fetch the result. A
relative path, or a URL that is already `.png`, silently yields no image.
`CommentsAdapter.java:60` and `ReplyFragment.java:76` do null-check `profilePicture` first;
`HomeFragment.java:117-119` coerces null to `""` and then skips.

**F-5.6 — fields correctly treated as optional.** For contrast, and worth preserving on the
backend side: `notifications[].createdAt` (`NotificationFragment.java:191`),
`notifications[].type` (`:203`), `notifications[].lectureResponse` (`:211,218`),
`lectureResponse.semesterSeason` (`:222`), and every string passed through
`valueOrEmpty(...)` (`:235-237`) are all null-tolerant.

**F-5.7 — `NotificationType` degrades quietly.** An unrecognised type deserialises to
`null`, which `NotificationFragment.java:181` treats as "not a warning" and `:203-205` maps
to an empty label. Adding a third notification type will not crash, but those rows render
with a blank type and the comment preview taken from `content` rather than `message`.

---

## 6. Backend changes — one-by-one assessment

Verdicts: **covered** (the client already behaves correctly), **not covered** (the client
needs a change), **not applicable** (nothing in this repository is affected).

### 6.1 Missing slash in the vote endpoint path fixed; the old path now returns 404

**Covered.** This client has always used the slashed form. Current declarations:

- `APIService.java:200` — `@POST("/social/comments/vote/comment/{comment_id}")`
- `APIService.java:207` — `@POST("/social/comments/vote/answer/{answer_id}")`

`git log -S"vote/comment"` on `APIService.java` shows the routes were introduced in commit
`e08f225` already as `/social/comments/vote/comment/{commentID}` and
`/social/comments/vote/answer/{answerID}`; the only later edit renamed the placeholders to
snake_case. No revision of this file ever contained the unslashed variant, so no build of
this client would hit the retired path.

### 6.2 Many failures now return 404 or 409 instead of `200 + success:false`

**Partially covered.** No site trusts `success` alone (section 1), so a real 404/409 is
correctly recognised as a failure everywhere except one. But:

- **Not covered — `Fragments/ListOfMyRatingsFragment.java:107-110`** dereferences the body
  with no checks at all and will throw an NPE instead of showing an error (F-2.1). This is
  the one place where the change turns a silent no-op into a crash.
- **Not covered — no site distinguishes 404 from 409.** Section 2, F-2.4. "Not found" and
  "conflict / already exists" produce byte-identical user feedback, so the extra precision
  the backend now offers is discarded. Most affected: `POST /ratings/rate`
  (`RatingsFragment.java:708`), where a 409 for "already rated" is indistinguishable from a
  404 for "no such lecture".
- **Regression to expect — F-1.5.** Messages the backend used to deliver in a `200` body are
  no longer shown, because on a non-2xx `response.body()` is null and every fallback picks
  the generic string resource.

### 6.3 Wrong HTTP verb now returns 405 with an `Allow` header instead of 404

**Not applicable.** Verbs are fixed at compile time by the Retrofit annotations in
`APIService.java`; the client cannot send a verb the interface does not declare, so it will
not provoke a 405 in normal operation. Nothing reads response headers anywhere — grep for
`headers()` over `app/src/main` returns nothing, so the `Allow` header would be discarded.
Were a 405 to occur (a route retired backend-side), it would fall into the generic non-2xx
branch of whichever call site is involved, per summary table 3.

### 6.4 Unsupported media type now returns 415 instead of 500

**Not covered — and the most likely of these changes to break this client.** Three routes
are declared with a body-bearing verb but **no `@Body` parameter**:

- `APIService.java:91-94` — `@POST("/auth/logout-all")`
- `APIService.java:102-105` — `@PATCH("/account/deleteAccount")`
- `APIService.java:261-265` — `@PATCH("/social/notifications/{notification_id}")`

With no `@Body`, Retrofit sends an empty request with `Content-Length: 0` and **no
`Content-Type` header at all**. If the backend now requires `application/json` on POST/PATCH
before dispatching, these three will start returning 415. Consequences at the call sites:
`SettingsFragment.java:324` and `:392` show a generic failure toast and, by design, keep the
local session (`SettingsFragment.java:290-295`), so *"log out on all devices"* and *"delete
account"* would fail with no indication of why; `NotificationFragment.java:285-291` logs at
WARN and the row silently stays unread.

Every other body-bearing call passes a `@Body` and therefore gets
`Content-Type: application/json; charset=UTF-8` from `GsonConverterFactory`.

Whether the backend actually enforces a media type on empty-bodied requests is
*undetermined from this repository*. Worth a targeted backend test on exactly these three.

### 6.5 A missing required query parameter now returns 400 instead of 500

**Not applicable.** This client sends **no query parameters at all**. `APIService.java`
contains zero `@Query` and zero `@QueryMap` annotations, and `retrofit2.http.Query` is never
imported. (All 32 `@Query` hits in the tree are Room SQL annotations in
`Adapters/SearchService.java`.) Every parameter is carried as a path segment, a header, or a
JSON body field.

### 6.6 A malformed or missing id now returns 400 instead of 500

**Not covered.** The client can send an empty path segment, and would not handle the 400.

The concrete path: `Fragments/NotificationFragment.java:202` builds the notification id with
`valueOrEmpty(response.notificationId())`, so a missing `notificationID` in the payload
becomes `""`. `NotificationFragment.java:246` then calls `markNotificationsAsSeen` — **before**
the emptiness guard at `:253-254`, which only protects the *thread-opening* branch. The
result is `PATCH /social/notifications/` with a trailing empty segment. Same shape for
`lectureOfferingId` if `lectureResponse.id` is absent, though that one is guarded.

Ids reaching path parameters are never validated for UUID shape anywhere in the client.
There is no 400 branch at any call site (F-2.4), so a 400 lands in the generic handler:
`NotificationFragment.java:289` logs it and the row stays unread.

### 6.7 `professorIds` is now optional when creating a Lecture

**Not applicable.** This repository is the Android client and has no lecture-creation
endpoint. `APIService.java` declares no POST or PUT to `/data/lectures`, and the string
`professorIds` does not appear anywhere in `app/src/main` — the only near-match is
`ProfessorResponse.lectureIds` (`Responses/ProfessorResponse.java:12`), which is the inverse
relation and is itself never read by any call site. This change concerns the admin-web
client, which is not in this repository.

### 6.8 New values added to the audit action list

**Not applicable.** No audit endpoint, DTO, enum or field exists in this client — a
case-insensitive search for `audit` across `app/src/main` returns nothing. This client never
reads an audit log.

Worth carrying over as a general caution, though: because Gson maps unknown enum values to
`null`, *any* additive enum change on a field this client does read is a latent crash. F-5.1
shows the live instance — a new `RatingCategory` constant would NPE
`Adapters/RatingAdapter.java:89,98`.

### 6.9 `VoteType.NONE` added for withdrawing a vote

**Not covered.** `DomainModel/VoteType.java:6-17` declares exactly two constants:

```
public enum VoteType {
    UP,
    DOWN
}
```

`Requests/VoteRequest.java:5` wraps that single field, so this client can only ever send
`"UP"` or `"DOWN"`. The UI matches: `Adapters/CommentsAdapter.java:81` always sends
`VoteType.UP` and `:91` always sends `VoteType.DOWN`, with no toggle-off state and no
tracking of the user's existing vote — both handlers unconditionally increment
(`:79-80`, `:89-90`). **Vote withdrawal is unreachable from this client.**

The receiving direction is unaffected: no response DTO carries a vote type, only the
aggregate `upVotes` / `downVotes` counters on `Comment` (`DomainModel/Comment.java:30-31`),
so a backend that stores `NONE` will not break parsing here.

---

## Suggested backend test targets

Derived from the above; ordered by how much client behaviour depends on the answer. Listed
as observations, not as changes to make in this repository.

1. `GET /account/ratings` — assert the response always carries a `ratings` array on 2xx, and
   that `ratings[].lecture.professors` is non-empty and stably ordered (F-2.1, F-4.1, F-5.3).
2. The three empty-bodied routes in §6.4 — assert they are accepted with no `Content-Type`
   header (`POST /auth/logout-all`, `PATCH /account/deleteAccount`,
   `PATCH /social/notifications/{id}`).
3. `GET /ratings/{lectureId}/categories` — assert the returned `category` values stay within
   the 26 constants listed in the contract; a new one crashes the client (F-5.1).
4. `PATCH /social/notifications/` with an empty id segment — assert 400/404 rather than 500,
   and confirm it does not match the collection route (§6.6).
5. `GET /ratings/own/{lectureId}` vs `GET /ratings/{lectureId}` — assert `own` is not
   routable as a lecture id (F-3.11).
6. `GET /social/notifications` — assert the wire keys are `notificationID`, `ownCommentID`
   and `lectureResponse`, since only these three depend on non-default naming (F-3.6, F-3.7).
7. `POST /social/comments` and `POST /ratings/rate` — the client shows success on
   `200 + success:false`, so failures on these two must use a non-2xx status (F-1.1, F-1.2).

## Client-side defects noted but not fixed

Found while reading; unrelated to the backend contract, recorded so they are not lost.

- **`Fragments/RatingsFragment.java:560`** — `if (!category.equals(RatingCategory.OVERALL))`
  compares a `RatingCategoryResponse` record against a `RatingCategory` enum constant.
  `equals` across those two types is always `false`, so the negation is always `true` and
  **`OVERALL` is never filtered out** of the empty-rating skeleton. The three sibling checks
  at `:372`, `:393` and `logic/WeightedRatingCalc.java:18` compare correctly; only this one
  forgot the `.category()` accessor.
- **`ServerCommunication/ImageRetriever.java:17-20`** — `getPicture()` builds the `Call` and
  then `return null;` without ever calling `enqueue` or `execute`. The class is never
  instantiated in `app/src/main`; its only construction is
  `app/src/test/.../DomainModelTest.java:140`.
- **`Fragments/SettingsFragment.java:243`** — the `Call` is created before the validation at
  `:245-252` that can `return` early. Harmless (an unexecuted `Call` is inert) but it does
  build a request object that is then discarded.
- **`Fragments/NotificationFragment.java:101,136`** — two user-facing strings are German
  literals (`"Benachrichtigungen konnten nicht geladen werden"`, `"Keine Verbindung zum
  Server"`) while every neighbouring message is English and most of the app uses
  `R.string.*` resources.
