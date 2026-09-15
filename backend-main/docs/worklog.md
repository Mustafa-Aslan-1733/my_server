# Worklog

A running record of the Kontrollphase pass that follows the 9 September defect pass. It holds
what a finished document does not: the **scan coverage** behind each sweep, the **red run**
behind each test, and the things deliberately left undone. Findings themselves live in
[test-findings.md](test-findings.md); this page is how they were arrived at.

Written as work happens, newest section at the bottom.

---

## P0 — `docs/test-findings.md` was doubled in the working tree

Found before any other work. The file held **3478** lines where `HEAD` holds **1739**, and
`git diff` was 1739 insertions with 0 deletions: the whole document had been appended to
itself, the seam visible as a single concatenated line (`are still green.# Test findings`).

Checked before discarding rather than assumed: a sorted line-set comparison against
`git show HEAD:docs/test-findings.md` found **exactly one** line in the working tree that was
not already in HEAD — that seam — and **zero** lines in HEAD missing from the working tree.
So there was no unique content to preserve.

Restored with `git checkout -- docs/test-findings.md`. This mattered before anything else
because every package below appends to that file; starting from the doubled copy would have
doubled every later edit too.

## Baseline, measured before touching anything

`./mvnw clean verify`, exit 0. The `clean` is not decoration — see
[P-5](test-findings.md#p-5--coverage-measured-without-clean-is-not-the-coverage-ci-measures).

| | |
|---|---|
| Tests | **977**, 0 failures, 0 errors, 31 skipped (the `@Disabled` class, the PostgreSQL layer and the E2E journeys, all CI-only) |
| LINE | **96.65%** (3344/3460) |
| BRANCH | **93.28%** (1014/1087) |

Read from `target/site/jacoco/jacoco.csv` and the Surefire reports of that run, not quoted
from a document.

---

## P1 · Shape 1 — a guard in one twin and not the other (F-23's shape)

F-23 was a null guard that sat twenty lines above the crash and had never been mirrored into
the method beside it. It was only ever searched for inside `AuditRevertService`. This is that
search finished.

### Scan coverage

Thirteen twin pairs read side by side, line for line, looking for a null check, an
empty/blank check, a `trim()`, a bounds check, an authorization call, a status check, an
existence check, an audit write or a `@Transactional` present on one side and absent on the
other.

**Clean — mirrored token for token, and that is the negative result:**

| Pair | |
|---|---|
| `ContentRevertHandler` | `CommentHandler` / `AnswerHandler` |
| `ReportStatusRevertHandler` | `CommentReportHandler` / `AnswerReportHandler` (the *guards* mirror; see the defect below, which is symmetric and therefore not a Shape-1 hit) |
| `VoteService` | `voteComment` / `voteAnswer` |
| `ContentReportService` | `submitCommentReport` / `submitAnswerReport` |
| `CatalogueRevertHandler` | `LectureHandler` / `ProfessorHandler` |
| `SocialResponseMapper` | the two `toResponse` overloads |
| `ContentModerationService` | `updateComment`/`updateAnswer`, `deleteComment`/`deleteAnswer` |
| `ModerationCommentService` / `ModerationAnswerReportService` | every guard matches: null status, `findByIdForUpdate`, the no-op short circuit, `reviewedBy`/`reviewedAt`, `ReportOutcome.visibilityFor`, the audit write, `@Transactional` on all three methods |

**Nine asymmetries found.** Three were defects and are fixed, three are product decisions and
are pinned as characterization, three are recorded without a change.

### Fixed

**Report-status reverts were impossible on exactly the transitions worth reverting.**
`ModerationCommentService.updateReportStatus` records two changed fields whenever the new
status flips the comment's visibility — `status` **and** `commentStatus` — while
`ReportStatusRevertHandler.currentValues` offered only `status`, and
`AuditRevertService.evaluate` refuses any entry carrying a key the handler does not know.
So every `ACTION_TAKEN` transition came back `ACTION_NOT_REVERTIBLE`. The handler's own
javadoc says those are the reason it exists.

*The red, in full, is the response body itself:*

```
"changes":{"status":{"before":"OPEN","after":"ACTION_TAKEN"},
           "commentStatus":{"before":"VISIBLE","after":"HIDDEN"}},
"revertible":false,"revertBlockedReason":"ACTION_NOT_REVERTIBLE"
```

```
AdminApiIntegrationTests.anActionTakenReportStatusIsRevertibleThroughTheAuditEntry
JSON path "$.auditLogs[?(@.action == 'COMMENT_REPORT_STATUS_CHANGED')].revertible"
Expected: a collection containing <true>
```

Fixed by publishing the second key from both handlers. `applyInverse` is unchanged —
`updateReportStatus` derives visibility from the status, so restoring the status restores the
comment. What the second key buys beyond making the entry revertible at all is the *check*: if
somebody moved the comment in the meantime the revert is now refused as `VALUE_CHANGED`
instead of quietly overwriting them.

`ReportStatusRevertHandlerTests.commentReportHandler_currentValues_mapsOnlyTheStatus` had
pinned the defect with `containsExactly(entry("status", …))`. **Inverted, not deleted**, and
renamed to `…mapsTheStatusAndTheCommentVisibility`; the answer-side twin with it.

**Worth its own line:** `AdminApiIntegrationTests.actionTakenHidesTheCommentAndRevertingRestoresIt`
already existed and is *named* for this. It does not call the revert endpoint — it toggles the
report back with a second PATCH. A green test whose name claims more than it asserts, which is
[P-6](test-findings.md#p-6--a-sweep-asserted-not-refused-which-a-404-also-satisfies) again. It
is left alone; the new test is the one that presses the button.

**Two halves of one rule, one half in each twin.** `addProfessor` stored the trimmed name and
checked uniqueness on the raw one; `addLecture` was consistent but trimmed neither.

- `LectureService.addLecture` did not trim. `@NotBlank` passes `"  Algorithmen 1  "`, so the
  padding reached the column — and `LectureModerationService` trims the incoming PATCH before
  comparing it to the stored value, so the first admin edit of such a lecture recorded a name
  change nobody made and wrote a `LECTURE_UPDATED` entry for it.
- `ProfessorService.addProfessor` checked `findByFirstNameAndLastName` on the raw strings and
  then stored `.trim()`ed ones, so `" Stefan"` / `"Kuehnlein "` walked past the 409 and
  created a second row holding exactly the name the first one holds.

Both now trim once and use that value for the check and the store.

*The reds:*

```
LectureServiceTests.addLectureStoresTheNameAndCodeTrimmed » PotentialStubbingProblem
LectureServiceTests.addLectureRefusesANameThatDiffersFromAnExistingOneOnlyByWhitespace
ProfessorServiceTests.addProfessorRefusesANameThatDiffersFromAnExistingOneOnlyByWhitespace
```

The `PotentialStubbingProblem` is the better of the three: Mockito refused the call because
the stub was on `findByName("Lineare Algebra 1")` and the code asked for
`findByName("  Lineare Algebra 1  ")`. That is the untrimmed value reaching the lookup, said
by the framework rather than by an assertion.

### Found, checked against the contract, and deliberate

**`UserResponse.reports` counts comment reports only.** `CommentReportRepository` has
`countByCommentStudent` and `countGroupedByCommentStudent`; `AnswerReportRepository` has
neither. A student whose *answers* were reported shows `reports: 0` in the admin directory.

Not a defect: `admin-api.md` says *"`reports` counts reports against comments authored by the
user"*, so the code matches its contract and the field is narrower than its name. That is a
product question, not a bug, and widening it changes a number the panel shows. Pinned by
`theUserReportCountCountsCommentReportsAndNotAnswerReports`, which creates one report of each
kind and asserts the count is 1 — so widening it later is a deliberate act with a test to
invert. **Open question for the team: should it?**

---

## P1 · Shape 2 — a field read but never written, or written but never read (F-26's shape)

F-26 was `ratingCount`, always 0 because nothing ever wrote `professors.rating_count`. It was
only ever looked for on that one field.

### Scan coverage

Every persistent field on all 21 entities, classified by grepping `src/main` for a setter
call, a constructor assignment, a `@PrePersist`/`@CreationTimestamp`/`@UpdateTimestamp`, or a
`@Modifying` query on the write side, and for a getter call **or a JPQL/derived query naming
the field** on the read side. The second half matters: eight fields have zero getter calls in
`src/main` and are read only by a query, so a getter-only grep would have reported them dead.

Cross-checked against the single Flyway baseline in both directions: **every column maps to an
entity field and every entity field maps to a column.** No orphans either way.

### (b) READ but never written — one hit, and it is F-26's shape exactly

**`BugReport.resolvedBy` — fixed.** `setResolvedBy(` appeared **zero** times in `src/main` and
`src/test`, while `AdminRepository.hasModerationHistory` reads it:

```sql
OR EXISTS (SELECT report FROM BugReport report WHERE report.resolvedBy = admin)
```

So one of that guard's three clauses could never evaluate true. The other two —
`CommentReport.reviewedBy` and `AnswerReport.reviewedBy` — *are* written, which is what made
this one stand out: one repository method reads three columns and only one of them was dead.

The consequence is not a wrong number on a screen. It is that an administrator whose entire
moderation history is bug reports could be demoted, taking the attribution with it, while the
same administrator could not be demoted over a single warning. `admin-api.md` already promised
otherwise: *"Demoting is 409 once the account has moderation history — a warning it issued or a
report it reviewed"*.

`resolvedAt` beside it was in the same state — neither written nor read — so the pair is now
recorded together, mirroring the `reviewedBy`/`reviewedAt` the two report services beside this
one have always written. `OPEN` is the one status that is not a disposition, so reopening
clears the pair rather than leaving a name on an open report.

*The red:*

```
AdminApiIntegrationTests.demotingAnAdministratorWhoResolvedABugReportIsRefused
Expecting actual not to be null      <- report.getResolvedBy() after a status change
```

`ModerationBugReportService` gained a `Clock` for `resolvedAt`, injected per the house rule and
fixed in the unit tests, so the timestamp is an assertable value rather than "now".

### (c) WRITTEN but never read — dead data, recorded not changed

| Field | Column | State | Verdict |
|---|---|---|---|
| `Professor.ratingCount` | `professors.rating_count` **NOT NULL** | written once as the Java field initialiser `0`; zero reads | **Dead. Drop it** — see below |
| `Professor.averageRating` | `professors.average_rating` **NOT NULL** | same, initialiser `BigDecimal.ZERO` | **Dead. Drop it** |
| `Student.blockedReason` | `students.blocked_reason` | three writes, **all literal `null`**; zero reads | A feature that was never wired. Nothing shows a block reason anywhere |
| `Student.blockedAt` | `students.blocked_at` | written on block; zero reads | Keep — forensic, and cheap |
| `Student.deletedAt` | `students.deleted_at` | written on delete; zero reads (deletion is detected by `status == DELETED`) | Keep — forensic |
| `CommentReport.reviewedAt`, `AnswerReport.reviewedAt` | | written on every status change; zero reads | Keep — the attribution half is read by `hasModerationHistory`, and these are its timestamps |
| `updatedAt` on **nine** entities | nine `updated_at` columns | `@UpdateTimestamp`, written on every flush; `getUpdatedAt(` occurs **zero** times in `src/main` | Keep — a row's last-touched time is worth having in the database whether or not the API returns it |

Only the first two are recommended for removal, and the distinction is the point: a column
written for the record is not the same as a column nothing writes and nothing reads.

### Proposed migration, not written

`professors.rating_count` and `professors.average_rating` are both `NOT NULL` with no default,
mapped by `Professor` with Java initialisers, written by nothing else and read by nothing.
Dropping them needs, in one migration:

```sql
ALTER TABLE professors DROP COLUMN rating_count;
ALTER TABLE professors DROP COLUMN average_rating;
```

plus deleting the two fields from `Professor` and the same two lines from the **H2** baseline,
which is a separate file and would otherwise let `ddl-auto=validate` disagree between the two
databases. **Deliberately not done here.** This is `V2`, the repository has only ever had a
`V1` baseline, and `docs/TODO.md` item 14 already parks it behind the `V2` that the
legacy-surface deletion needs — landing two unrelated drops in the project's first migration
is worse than waiting for the one that has to exist anyway.

The stale claim that sent F-26 the wrong way is corrected in place:
`ProfessorRatings`'s javadoc said *"`RatingService` recomputes the figure when a rating is
submitted and stores it on the row"*. No such write has ever existed.

### Dead code in the same sweep, split by whether intent is visible

**Deleted** — `CommentRepository.findAllByLectureId` and `findAllByStudent`, zero callers,
no design intent attached to either. F-6's precedent: delete rather than test. The
now-unused `Student` import went with them.

**Kept and pinned** — `NotificationRepository.markAllAsSeen` is written, `@Modifying`, and has
zero callers; `SecurityConfig:90` declares `PATCH /social/notifications/all` as authenticated;
and `SocialController` maps only `/notifications/{notification_id}`. So the declared route
falls into the single-notification handler, fails to bind `"all"` as a UUID, and answers
**400 `"Invalid request"`** — pinned by
`markingAllNotificationsAsSeenIsNotAnEndpointDespiteBeingDeclared`.

This is a **half-built feature**, not dead code: the query and the security rule are the only
evidence anybody intended a "mark everything read" action. Writing the handler would be adding
a feature, which this pass is not for; deleting the query would throw away the record that one
was planned. **Open question for the team: finish it, or remove both halves?**

---

## P1 · Shape 3 — an order-undefined collection reaching the client (F-21 / F-27's shape)

F-21 was a lecture's professor list, F-27 the rating categories. Two places found, no sweep.

### Scan coverage

All five mappers plus `LectureLabels`; **all 76 DTO records** under any `dto/` directory;
every service method that builds a `*Response`; all 15 repositories; all four
collection-holding entities. Looking for a `Set`, `HashSet`, `HashMap`, `Collectors.toSet`,
`Collectors.toMap` (which returns a `HashMap`), `Map.of`, `Set.of`, `keySet()`, `values()` or
an unordered JPA bag turning into a `List`, a `String`, or a response field.

Of the 76 DTO records, exactly **one** declares a `Map`/`Set` field (`AuditLogResponse`);
every other response collection is already a `List`.

**Checked and cleared, so the next reader does not repeat it:**
`LectureResponseMapper` and `ProfessorResponseMapper` both sort explicitly with an id
tiebreak (the F-21 fix); `RatingService.getRatings` uses an `EnumMap`; `AuditLogService`'s
actor list is a `LinkedHashMap` fed by a query with `ORDER BY`; `IdCount`, the revert
handler map and `UserReferenceMapper`'s sets are lookup-only, never iterated; every
`findAllByOrderBy…` listing is a `List` end to end.

**Five candidates, all five real, all five fixed.**

### 1. The audit record kept the `HashSet`'s order

BUG-3 was fixed on the *comparison* side — `CatalogueEdits.sameAssignment` sorts before
comparing — and left unfixed on the *recorded* side: `labels()` did not sort, so the `before`
list written into `changes.professors` carried `lecture.getProfessors()`'s identity-hash order
into a `jsonb` column, which **preserves array order**, and out through
`GET /admin/audit-logs`. `Professor` overrides neither `equals` nor `hashCode`, so that order
can differ per run and between two instances answering the same request.

```
LectureModerationServiceTests.updateLecture_recordsTheProfessorAssignmentInAStableOrder
expected: ["aaaaaaaa-…", "cccccccc-…"]
 but was: ["cccccccc-…", "aaaaaaaa-…"]
```

`labels()` sorts now, and `sameAssignment` is defined in terms of it — so the order a change
is *decided* by and the order it is *recorded* in are one thing rather than two that have to
be kept in step. Deterministic `LinkedHashSet` fixture, per the BUG-3 rule.

### 2. F-27's own surface, on the route beside the one that was fixed

`RatingService.getRatings` got an `EnumMap` so the public read returns declaration order.
`getOwnRating`, **two methods below it**, builds the same `RatingsAverageResponse.ratings`
field by walking `rating.getTopics()` — a JPA bag with no `@OrderBy`.

```
RatingServiceTests.getOwnRatingReturnsTheCategoriesInDeclarationOrder
element at index 0: expected "ROOM" but was "WORKLOAD"
element at index 2: expected "WORKLOAD" but was "ROOM"
```

Both that route and the admin `GET /admin/ratings`, which walks the same bag, now sort by
category — declaration order, because that is what the F-27 CHANGELOG entry already promised
for the sibling route and one response field cannot have two orders. Sorted in Java rather
than with `@OrderBy("category")`, which would have given *alphabetical* order: the column is
`@Enumerated(STRING)`.

### 3 and 4. Three more lists whose order was the database's choice

`Comment.answers` was a bag with no `@OrderBy`, and both app-tier comment listings
(`findAllByLectureIdAndStatus`, `findAllByStatus`) carried no `ORDER BY` — while the admin
listing beside them, `findAllByOrderByCreatedAtDesc`, always had one. Comments are newest
first now, matching the admin listing; answers oldest first, because a thread under a question
reads forwards.

**This test was checked against a defect rather than trusted for being green**, because it was
written after the fix. Inverting the comment order made it fail on the comment assertion, and
inverting only the answer order made it fail on the answer assertion:

```
JSON path "$.comments[0].content" expected:<Asked second> but was:<Asked first>
JSON path "$.comments[1].answers[0].content" expected:<Answered first> but was:<Answered second>
```

Both halves discriminate, which is the only thing separating this from a test that would pass
on an unordered API.

### 5. The declared type said the opposite of the query

`LectureRepository` and `ProfessorRepository` declared **`Set`** returns on four queries that
carry an `ORDER BY`, and those results are the catalogue listings served to the client. The
order survived only because Spring Data happens to materialise a `LinkedHashSet` — a fact
about the framework, not a guarantee anyone wrote down, and nothing in the code recorded the
dependency. Changed to `List`. No behaviour change today; that is the point of doing it before
there is one.

### Loud flag: ~50 `Map.of` sites are safe only because of a column type

`Map.of` iteration order is randomised per JVM run by `ImmutableCollections.SALT`, and about
fifty call sites use it to build audit `changes` / `metadata`. Those keys reach the client
through `GET /admin/audit-logs`. They are safe **because `jsonb` normalises object keys**, not
because the code does anything.

That is an invariant of exactly [F-30](test-findings.md#f-30--a-rate-limiter-that-is-only-correct-because-a-clock-in-another-file-is-utc)'s
kind: correctness resting on a fact in another file that nobody has written down. And the H2
baseline uses **`json`**, which does *not* normalise — so the test suite runs against the one
column type that would not protect production. Nothing changed; it is named in P4's invariant
list. Array *values* are the half that `jsonb` does not save, which is candidate 1 above.

**One existing assertion was inverted rather than deleted.**
`AdminApiIntegrationTests.ratingsListingIsAdminOnlyAndReturnsTheManagedShape` read
`topics[0].category == ORGANIZATION`, which was the order the rows happened to be inserted
in. It now reads `OVERALL` then `ORGANIZATION` — declaration order. The suite caught the
change by itself, which is what that assertion was for.

---

## P1 · Shape 4 — an unguarded parse of caller input (F-24 / F-25's shape)

F-24 collected `UUID.fromString` into `com.pse.shared.util.Uuids`; the question left open was
whether every call site had been migrated. Its lesson was that **an NPE guard is not an
`IllegalArgumentException` guard** — the two have to be thought about together.

### Scan coverage — and this shape is nearly clean

Every `UUID.fromString`, `Integer.parseInt`/`valueOf`, `Long.parse*`, `Enum.valueOf`,
`LocalDate`/`LocalDateTime`/`Instant`/`OffsetDateTime.parse`, `DateTimeFormatter` parse and
Base64 decode in `src/main`. **Fifteen call sites; eleven guard both failure modes.**

That is the negative result, and the detail is the point:

| Site | Null | Malformed |
|---|---|---|
| `Uuids.parse` / `parseOrNull` (6 call sites depend on it) | explicit check → 400 | `catch (IllegalArgumentException)` → 400 |
| `KeysetCursorCodec` — `Instant.parse`, `UUID.fromString`, Base64 | null/blank/length guard | `catch (RuntimeException)`, **deliberately broad** — its own comment records that `DateTimeParseException` is *not* an `IllegalArgumentException`, so a narrower catch would have missed it |
| `KeysetPage` — `Integer.parseInt` | null/blank guard | `catch (NumberFormatException)` + a range check |
| `AuditLogService` — `OffsetDateTime.parse`, `Enum.valueOf` ×3 | null/blank guard | typed catch |
| `UserListQuery` — `UserStatus.valueOf`, `UserRole.valueOf` | null/blank guard | typed catch |

Outside those, every `@PathVariable UUID` is converted by Spring (→ 400 through
`MethodArgumentTypeMismatchException`) and every `@RequestBody` enum by Jackson (→ 400
through `HttpMessageNotReadableException`). `TokenGenerator`'s Base64 **encodes** and cannot
fail. So the migration F-24 started really was finished on the request path.

### Four misses, all in one class — fixed

`RevertValues.asInteger` (`Integer.valueOf` → `NumberFormatException`), `asEnum`
(`Enum.valueOf` → `IllegalArgumentException`) and `asIds` (`UUID.fromString` →
`IllegalArgumentException`, and `Uuids` was never adopted here). All three guard `null` and
none guarded a value that is *present but unreadable* — F-24's lesson, landing on the other
side of the same class. `asBoolean` is the fourth reader and is safe by accident:
`Boolean.valueOf` never throws.

**Why it is reachable, which is the part worth arguing.** These values come from the audit
entry's `jsonb` column, not from a request, so every row written today is well-formed. But the
column outlives the code that wrote it. Remove or rename a constant of `UserStatus`,
`SemesterSeason`, `LectureType`, `ReportStatus` or `BugSeverity` — an ordinary refactor — and
every audit row naming the old one still names it. `evaluate()` compares recorded values as
strings, so it happily marks such an entry **revertible**; the panel then draws the button,
and pressing it reached the catch-all and answered **500**.

That is F-23 exactly: a revert button that answers 500 instead of refusing. Found by the
sweep rather than by a report, because the trigger is a future edit.

Fixed with one `parsed(field, supplier)` helper that turns `IllegalArgumentException` (which
`NumberFormatException` extends) into `409` + `ACTION_NOT_REVERTIBLE`, naming the field.
**Deliberately not a sixth `AuditRevertRefusal` constant**: the five reasons are a documented,
client-visible vocabulary the panel branches on, and "the recorded value cannot be read back"
is a true instance of "this entry cannot be reversed" — a new constant would be a contract
change for a case nobody can act on differently.

**Three existing characterization tests asserted the raw exceptions and were inverted, not
deleted.** They were checked against the un-fixed code to be sure the inversion discriminates
— with the guard removed, all three go red:

```
RevertValuesTests.asInteger_nonNumericString_isRefusedRatherThanCrashing:182
RevertValuesTests.asEnum_valueThatIsNotAConstant_isRefusedRatherThanCrashing:255
RevertValuesTests.asIds_listContainingSomethingThatIsNotAUuid_isRefusedRatherThanCrashing:298
Expecting actual throwable to be an instance of: ApiException
```

---

## P1 · Shape 5 — a failure answered `200 {"success": false}` (F-5 / F-22 / F-28's shape)

Three rounds found three separate instances. This is the sweep of what was left.

### Scan coverage

**Every** `BasicResponse(..., false)` in `src/main` — fourteen sites — plus the one response
DTO that carries its own success flag. Eleven are correct: nine in `GlobalExceptionHandler`,
each on a `ResponseEntity.status(...)`, plus `SecurityConfig.writeError` and
`BearerTokenAuthenticationFilter.unauthorized`, which both set the status before writing the
body. Every other `new BasicResponse(...)` in the tree passes `true`.

Four candidates. **One was fixed, and the other three turned out to be a more interesting
result than the fix.**

### Fixed — `POST /auth/validate`

`IdentityService.validate` had three failure branches, all answering `200 {"success": false}`,
while the failure three lines away — a header that is not a bearer token at all — has always
thrown `InvalidAuthTokenException` and answered **401**. One method, and "no token" got the
stricter answer than "wrong token".

**But only one of the three is reachable over HTTP, and finding that out is the point of
sweeping rather than reading.** Checked, not assumed:

| Branch | Reachable? |
|---|---|
| `student == null` (well-formed header, invalid token) | **No.** `BearerTokenAuthenticationFilter` answers 401 for any present-but-invalid `Authorization` header before the controller runs |
| `request.email() == null` | **No.** `@NotBlank` on the record plus `@Valid` on the parameter make it a 400 from Bean Validation |
| a well-formed address that is not the token owner's | **Yes.** This is the one a client can observe |

All three were corrected anyway, so the method agrees with itself and with the filter in front
of it — the two unreachable ones now answer what the layer above them already answers, rather
than contradicting it. The `CHANGELOG` entry describes **only the reachable one**, because
that is all a client sees.

Three unit characterizations were inverted rather than deleted, and the missing-email case was
**split** from the wrong-address case: a malformed body is 400, an address that is not yours is
401. They used to be one 200, so a client could not tell "you sent no address" from "that is
not your address". `AuthApiIntegrationTests` gained the API-level test the route never had.

### Not changed — and two of them are unreachable

**`GET /admins/validate`.** `AdminService.validateAdmin` ends in
`new BasicResponse("Is Not Admin", false)`, which reads like another F-5. It **cannot be
reached**: `SecurityConfig` matches `/admins/**` with `hasRole("ADMIN")`, so a caller without
the role is refused **403** by the chain, and an invalid token is refused 401 by the filter
before that.

The test that found this was written expecting 200 and went red with `expected:<200> but
was:<403>` — which is exactly why the shape was swept by probing rather than by reading. It is
the same dead second answer `LectureService.addLecture` already carries a comment about: *"a
second, weaker answer to a question already settled"*. Pinned at 403 and left in place; the
route is being retired (`TODO.md` item 22) and rewriting a dead branch on a dying route buys
nothing.

**`GET /ratings/own/{lectureId}`** with no rating yet answers `200`, `success: false`, empty
list. The 200 and the empty list are right and the code says why — "I have not rated this
lecture" is the ordinary answer for any lecture a student has not rated, and a 404 would make
the ordinary case an error. What does not follow is `success: false`: the request succeeded
and the answer is "none". Pinned, not changed — it is a client-visible flag on a route the app
reads, and the call belongs to whoever owns the app contract.

**Checked and deliberately not a candidate:** `GET /system/status` answers `200 success:true`
while reporting `status: DOWN`, documented in its own DTO javadoc — a page that must show "the
API is up but the database is not" cannot do it if the request itself fails.

### Where shape 5 now stands

One reachable instance left in the whole application (`/ratings/own`), pinned with the reason
it was left. The other two `success: false` sites a reader would flag are unreachable, and
that is now written down in tests rather than in nobody's head.

---

## P2 · The two narrow code gaps

### P2.1 — Size limits, and what measuring them actually took

F-17's writeup left this open: *"In the production logs, `MaxUploadSizeExceededException` and
Tomcat's 512-byte multipart header limit fall into the same catch-all branch. … Still to be
measured separately."*

**The plan was to extend `ApiProtocolContractTests` the way F-17 did. That would have been
worthless, and finding out why is the useful part of this package.** MockMvc is Spring's
dispatcher without a server: no connector, no Tomcat, **no multipart parsing**. Neither
exception can be raised there. Probed before writing anything: a 2 MB `multipart/form-data`
body through MockMvc answers **415**, from content negotiation, because the request never
reaches a parser. A sweep built on MockMvc would have passed against a broken handler — the
exact failure this repository keeps writing down about lists and exemptions, in a new place.

So `TransportLimitTests` drives a **real servlet container**
(`@SpringBootTest(webEnvironment = RANDOM_PORT)` on the H2 profile). The measurement:

| Probe | Before | Verdict |
|---|---|---|
| a multipart body over the 1 MB default | **500** `"Unexpected backend error"` | the defect; now **413** |
| a malformed multipart envelope | **415** | **already correct** — see below |

Only the first was wrong. `MaxUploadSizeExceededException` now maps to **413** — the request is
well formed and the server is refusing it for size, which is what RFC 9110 gives that code
for — with the project's `BasicResponse` body, through one narrow handler beside F-17's, and
explicitly not by extending `ResponseEntityExceptionHandler`.

**No general `MultipartException` handler was added, deliberately.** Every route in this
application reads a JSON `@RequestBody`, so a malformed multipart envelope is refused by
content negotiation with 415 before any parser runs — which is the right answer, since the
route genuinely cannot read that media type. There is no request that reaches the general
case, and a handler for a state nothing can produce is a claim this suite cannot check. Both
answers are pinned, so adding a multipart endpoint later has to be a deliberate act.

A third test asserts the **body** of the 413, not just its status — F-17's lesson: a sweep that
reads only status codes stays green while somebody swaps in `ProblemDetail`, which the Android
client cannot parse.

**The cost, stated because it is real:** `RANDOM_PORT` forks a second Spring context in
`server:test`. Measured on the verify run: exactly one `Tomcat started on port …`, so one extra
application start. The alternative was leaving a 500 that appears in production logs unmeasured,
or asserting it against a fixture incapable of producing it.

### P2.2 — The error-message sweep, and a clean negative result

New: `ApiErrorMessageContractTests`. Route list from `MappedRoutes`, as the house rule
requires. It provokes the four refusals that need no per-route fixture — an anonymous call, a
verb the path does not map, a body in a media type nothing reads, malformed JSON in the right
one — and asserts two things over every response it gets back.

**Shape.** The body parses as JSON, is an object, carries `message` and `success`, has
`success: false`, and carries **no field outside** `{message, success, reason}`. That last rule
is the one with teeth: a route that grows a bespoke error shape is one a client cannot read.

**Style**, mechanically — the rules are chosen to be checkable rather than aspirational:
non-blank, no surrounding whitespace, opening capital, no trailing full stop, ASCII only, no
unfilled `{placeholder}`, under 200 characters. "Is it English" is not testable; these are the
things that actually go wrong when a message is written in a hurry.

**Result: 189 refusals provoked and examined, and every one conforms.** No inconsistency to
list, which is the outcome the brief asked to be recorded either way. The corpus was already
in good shape and now something keeps it there: all 49 distinct `ApiException` messages are
string literals with no concatenation or `String.format`, and there are **no custom Bean
Validation messages at all** — every validation failure collapses to the single literal
`"Invalid request"`, so Hibernate Validator's locale-dependent defaults ("must not be blank")
never reach a client. That was true by luck; it is now true by test.

**Checked against a defect rather than trusted for being green.** `"Method not allowed"` was
temporarily changed to `"method not allowed."` and the sweep named it:

```
These break it: {method not allowed.=does not start with a capital  (wrong verb DELETE /health)}
```

The floor assertion was checked the same way — raising it reports the real count, 189, so the
sweep cannot go quiet if the handler mapping stops resolving.

**What it deliberately does not cover:** the statuses that need a route's own fixture — a 409
on a duplicate name, a 502 from the tracker, a 429 from the limiter. Four of the eight statuses
in `admin-api.md`'s preamble are provoked here; a sweep claiming the other four would be
asserting against its own setup.

---

## P3 · The `admin-api.md` drift, under test

The document has fallen behind the code four times — F-5 (a status that had changed), F-16 (a
404 that had become a 405), F-22 (a body that no longer existed), F-26 (a field that had never
been right). Each was corrected by hand and nothing stopped the next. The repository's own
principle, applied to itself.

### The constraint that decided the design

**The generated OpenAPI schema cannot check a status claim.** Verified: zero `@ApiResponse`,
`@Operation` or `@ResponseStatus` annotations across all 80 mappings, so springdoc emits only
the default `200` per operation. `OpenApiContractTests` can check that a route *exists* — it
already does — and nothing more. So the sweep splits: route existence against the mapping,
statuses against **real responses**.

**Rejected, with the reason:** annotating all 80 mappings with `@ApiResponse` so the schema
carries the statuses. It would make one source of truth, but it is ~80 controller edits for a
documentation property, it states the contract twice (the annotation and the handler that
actually produces it), and springdoc still would not know *which condition* produces which
status — which is the half of `admin-api.md` that has any value.

### What was added to the document

A new appendix, `## The contract, as a table`, between
`<!-- contract-table:start -->` / `<!-- contract-table:end -->` markers. **53 rows**, one per
`(METHOD, path)`, each listing the statuses that endpoint claims. Purely additive: not one
existing sentence, cell or code fence was touched. The document had no HTML comments and no
anchor convention, so the markers collide with nothing.

The section opens by saying what it is for and that the prose above remains the authority on
*why* — a table cannot say that the `409` on `PATCH /admin/users/{id}` is about not locking
anyone out of an API with no other recovery path, and that sentence is worth more than the
number.

### What `AdminApiDocumentationDriftTests` checks

| | |
|---|---|
| Direction A | every route the table claims is mapped by the application |
| Direction B | every `/admin/**` route the application maps is in the table |
| The claims | `401` anonymous, `403` as a student, `415` for an unreadable media type, `404` for an invented id — provoked and compared |
| The gap | the claims it *cannot* provoke, asserted as a **count** rather than left implicit |

Route list from `MappedRoutes`, per the house rule. **One named exemption**,
`GET /admins/validate` — legacy, undocumented in the prose too, being retired in favour of
`GET /admin/auth/me`; named with its reason the way `PUBLIC_ROUTES` names its own.

Result: **over 100 claims probed, all correct**, and the route lists agree in both directions.

### Two things writing it turned up

**The table was wrong before the code was.** The first run failed with
`POST /admin/auth/login claims 401 for an anonymous caller, answered 400` — because that
route's `401`/`403` are about the *credentials in the body*, not the caller's session. The
document's own preamble draws exactly that line ("Except for the two login operations and
health…") and my table had flattened it. Two rows are now named in
`REFUSALS_ARE_ABOUT_THE_BODY` with the reason. The sweep found a documentation error on its
first run, which is the shortest possible argument for having it.

**A limitation, found by trying to make it fail.** The `404` probe needs an id to invent, so a
`404` claimed on a route with **no path variable** is carried in the table and never checked —
adding one to `GET /admin/comments` passes. Written into the test's javadoc rather than left
for the next reader to discover. 87 claimed statuses are unprovoked here (`200`, `400`, `409`,
`429`, `502`, `503` — the ones needing a route's own fixture), and that number is itself
asserted, so it cannot grow quietly.

### Proved it can fail, in all three directions

```
(A) claims routes the application does not map: [GET /admin/nonexistent]
(B) maps administrative routes docs/admin-api.md does not describe: [GET /admin/ratings]
(C) claims statuses the application does not answer:
    [GET /health claims 403 for a non-administrator, answered 200]
```

Each was inserted, watched, and reverted; the document was diffed against its backup
afterwards to confirm nothing was left behind.

---

## P4 · Scope declarations — `Where the suite stops`

No code. A new section in `test-plan.md`, ahead of *Deliberately uncovered*, which names two
**classes** nobody tests; this names four **questions** nobody tests, which is the larger and
less visible gap.

Each statement was checked rather than asserted from memory:

- **Performance.** `LazyLoadingRegressionTests` is 6 tests over 9 read paths and asserts that
  walking a lazy collection does not throw `LazyInitializationException` — it makes **no**
  claim about query counts, and a route issuing one query per row would pass it. Written
  explicitly so its name stops implying N+1 coverage. The known N+1 in `SocialResponseMapper`
  is recorded as *unmeasured*, not absent.
- **Injection.** Counted, not assumed: **zero** occurrences of `nativeQuery = true`,
  `createQuery` or `createNativeQuery` in `src/main`; all **18** `@Query` annotations are JPQL
  with named parameters and no concatenation; `SqlLike.escape` has exactly two call sites and
  even there the result is a **bound parameter** to `builder.like(..., ESCAPE)`, so it is about
  wildcard semantics rather than injection. There is no unparameterised path to attack, which
  is why there is no test attacking one.
- **XSS.** Named as a boundary: the API returns JSON and the escaping duty is the panel's and
  the Android client's. The one exception is the HTML mail the backend renders itself, where
  `Html.escape` covers every interpolated value in the three mail classes and
  `LoginMapFragmentTests` pins the hostile-URL case. Said plainly: **if the panel renders a
  username or a warning message as HTML without escaping, nothing here will catch it.**
- **Migrations.** Forward-only, as a decision with its consequence written down. One baseline
  file today; the note says this decision should be revisited at `V2`, which `TODO.md` items 14
  and 23 both want.

### The time table, and three invariants F-30 did not name

Transcribed from the schema rather than from memory: `audit_logs.created_at` is the **only**
column with a time zone and `AuditLog` the **only** entity using `Instant`; the other 30
`*_at` columns across 13 tables are zone-less `timestamp(6)` read as `LocalDateTime`.

F-30 named two invariants. The sweep found three more, and each is recorded with the file that
owns it and what breaks when it moves:

1. `hibernate.jdbc.time_zone=UTC` is what makes a zone-less `LocalDateTime` *mean* UTC.
2. `AuditLog.@PrePersist` fills only a null `createdAt` while `@CreationTimestamp` elsewhere
   overwrites — which is why the cursor tests stamp audit rows before insert and other rows
   after, through `JdbcTemplate`.
3. **`jsonb` on PostgreSQL, `json` on H2** — the two baselines differ in exactly those two
   lines. ~50 `Map.of` sites build audit documents, `Map.of` iteration order is randomised per
   JVM run, and those sites are correct **only** because `jsonb` normalises object keys. The
   H2 suite runs on the column type that would *not* save them.

That last one is F-30's shape exactly — correctness resting on a fact in another file, where
the file that would have to change is not the file that would break.

---

## P5 · F-29 ready to hand over

F-29 is the one finding nothing in this repository can close: seven leaked credentials, each of
which has to be rotated at its provider. What *could* be wrong with it at hand-over was that
the record said **what leaked** and not **what to do about it** — so whoever picks it up starts
by re-deriving the plan.

### The rotation table, completed

`docs/TODO.md` had *What / Where it leaked / Commit*. It now carries, per row:
**credential / provider / where the rotation is actually done / owner / rotated ☐**, and the
"where it leaked" columns are kept as a separate table so the evidence and the action are not
interleaved.

Rows are numbered **R1–R7**. Three distinctions that were implicit and are now written on the
row itself, because each is a way a rotation gets done wrongly:

- **R1 has no external provider.** The app bearer token is a session *this backend issued*;
  the rotation is invalidating that session server-side, not visiting a dashboard.
- **R3 and R4 are different credentials at the same provider.** Rotating the ipinfo *token*
  leaves the ipinfo *account password* valid — and the account password can mint new tokens,
  so rotating only the token is the weaker half.
- **R2, R3 and R5 each need two steps.** Change it at the provider *and* update the CI/CD
  variable; the deploy job is where the value reaches the server, so a rotation that stops at
  the dashboard takes the feature down instead of securing it.

**Owner is `[owner: ?]` on all seven, deliberately.** That is board item 8 and inventing a name
would be worse than an empty cell. The *plan* is written for every row regardless, which is
what "even if the rotation cannot be done, the plan is written down" asks for.

### `.gitleaksignore` cross-referenced

Every commented fingerprint now names the row it retires. **Twelve fingerprints for seven
credentials**, and the reason is written at the top of the file: a fingerprint is per **rule**
and per **commit**, so one value can need several lines and silencing one rule leaves the
others. The mapping that was previously prose is now explicit, including the four-line block
in `245174c` that has to be read against a line number:

```
line 23 -> R3 (ipinfo.token)      line 24 -> R4 (ipinfo.io account password)
line 26 -> R5 (Geoapify account)  line 28 -> R5 (Geoapify API key)
```

The instruction added with it: **uncomment every line of a row together**, or `secrets:history`
stays red for a credential that has in fact been rotated — which is exactly the failure that
teaches people to stop reading the job.

Note R6 and R7 are the **same** database password in two commits, so they retire together;
that is now on both the table row and the fingerprint comment.

### Not done here

`gitleaks` is not installed on this machine, so the file's syntax was not re-validated by
running the scanner — only the fingerprint lines themselves are untouched, and every edit is
inside a comment. **Worth one `secrets:history` run in CI to confirm the file still parses.**

---

## P6 · Delivery documents

### `docs/adr/` — fourteen Entwurfsentscheidungen

One file per decision, fixed sections: **Decision / Context / Alternatives considered /
Rationale / Consequences**, plus **Sources**. `README.md` indexes them.

**Every record is extracted, and each cites where its reasoning already lived** — a `CHANGELOG`
entry, a finding, a `test-plan.md` section, or a comment in `pom.xml` / `.gitlab-ci.yml` — so
the citation can be checked rather than trusted. The fourteen: the five test layers; the
two-tier coverage gate; a GitLab CI service over Testcontainers; the twin H2/PostgreSQL
baselines; PIT's context-free scope; Lombok left unfiltered; characterization-and-inversion; the
single-responsibility refactor; `VoteType.NONE`; 405 over 404; narrow exception handlers; the
document under test; consumer expectations kept in this repository; and the `GET /auth/me`
legacy alias.

The last was written on 9 September, in this pass — F-39's decision was the one reversal in the
record with no ADR behind it, and a reversal is the case an ADR exists for. **0001 was renamed**
at the same time: it was filed as "four test layers" before the contract layer existed and its
own table had listed five for some time.

**Nothing was invented.** Where the record did not carry a rationale it is not in an ADR; the
open questions are on the board instead (items 24–27). Two records were sharpened by this pass
rather than merely transcribed: ADR 0008 now records that the twin-pair sweep **vindicated and
qualified** the decision to leave `ModerationCommentService` and `ModerationAnswerReportService`
unmerged — their guards mirror perfectly, and what did not mirror was a handler against the
service it inverts (F-31), which merging would not have prevented. ADR 0011 records that two of
F-17's three reasons against `ResponseEntityExceptionHandler` have since expired and which one
has not.

### `docs/class-diagrams.md` — six Mermaid diagrams

**There were none in this repository** — no `.puml`, `.mmd` or `.drawio`, and no occurrence of
"diagram" or "Klassendiagramm" anywhere in `docs/` or `*.md`. So this is authoring, not
updating; post-refactor names throughout, with `test-plan.md`'s old-to-new mapping as the
bridge from anything the team holds externally.

Mermaid, in the repository, because GitLab renders it natively, there is no toolchain to
install, and it diffs as text. Six areas rather than one unreadable whole: `auth`, `social`,
`moderation`, `audit` + `audit.revert`, `lecture`/`professor`/`rating`, and
`security` + `shared`.

**Arrows are constructor injection**, extracted from the source rather than drawn from memory —
so an arrow means "this class cannot be built without that one", which is the relationship that
constrains the design. **No getters or setters**; entities carry their *relations*, not their
columns. Each diagram is followed by the two or three sentences that say what it is *for* —
the `LoginCodeDelivery` seam, why the two report services are still two classes, why the
revert handlers call services instead of writing fields, and the `Set` edge that produced BUG-3,
F-21 and half of F-35.

**Not rendered here.** No Mermaid renderer is installed on this machine; the blocks were checked
structurally (six `classDiagram` blocks, braces balanced, no malformed `class` line) and are
otherwise unverified. **Worth one look in GitLab's preview before the presentation.**

### Numbers

`test-plan.md` and `test-findings.md` are updated, and the figures carry a sentence saying
**not to quote them from there**: the artefact is `target/site/jacoco/jacoco.csv` after
`./mvnw clean verify`, which is what CI reads and what `jacoco:check` gates on. That is
[P-5](test-findings.md#p-5--coverage-measured-without-clean-is-not-the-coverage-ci-measures)
and [P-1](test-findings.md#p-1--the-coverage-baseline-number-was-wrong) applied to this pass's
own numbers rather than only to the previous ones.

Findings went 38 → **47**; the "Found by" table gained two rows so the Kontrollphase pass is
attributed separately from the 9 September one; and the summary's "nothing is open" line is now
"two are open, and neither is a defect waiting on a commit".

---

# Hand-over

Read this section on its own if you read nothing else.

## Where the suite is

| | Before | After |
|---|---|---|
| Tests | 977 | **1001** (31 skipped locally: the `@Disabled` class, the PostgreSQL layer, the E2E journeys — all CI-only) |
| Failures | 0 | **0** |
| LINE | 96.65% | **96.67%** (3363/3479) |
| BRANCH | 93.28% | **93.16%** (1022/1097) |
| Findings | 38 | **47** |

Both figures from `target/site/jacoco/jacoco.csv` after `./mvnw clean verify`, exit 0. **Quote
that artefact, not this table.** The branch dip is new guard branches arriving, not coverage
lost; both gates (LINE 0.90, BRANCH 0.88) pass with room.

## Every shape, its scan coverage, and its result

The point of the pass. A sweep that finds nothing is worth something **only** if what it
covered is written down.

| Shape | Scanned | Candidates | Result |
|---|---|---|---|
| **1.** A guard in one twin and not its neighbour (F-23) | 13 twin pairs, line by line | 9 asymmetries | **F-31**, **F-32** fixed; 3 pinned as product decisions; 8 pairs mirrored token for token |
| **2.** Read but never written / written but never read (F-26) | every persistent field on all 21 entities, both baselines, JPQL as well as getters | 1 + 8 + 3 | **F-33** fixed, **F-34** pinned; 8 dead-data fields with a verdict each; 2 dead queries deleted |
| **3.** An order-undefined collection reaching the client (F-21, F-27) | 5 mappers, **all 76 DTO records**, every `*Response` builder, all 15 repositories | 5 | **F-35** — all five fixed |
| **4.** An unguarded parse of caller input (F-24, F-25) | all 15 parse sites in `src/main` | 4 | **F-36** fixed. **11 of 15 already guarded both NPE and IAE** — a near-clean negative result |
| **5.** A failure answered `200 {"success": false}` (F-5, F-22, F-28) | **every** `BasicResponse(..., false)` — 14 sites | 4 | **F-37** fixed; **2 proved unreachable**; 1 pinned. 11 were already correct |

Plus **F-38** (the size limit F-17 parked) and **P-8** (the document drift), and two new sweeps:
`ApiErrorMessageContractTests` (189 refusals, all conforming — a clean negative result) and
`AdminApiDocumentationDriftTests` (both directions, found a documentation error on its first
run).

**Two shapes are now closed rather than merely searched.** The parse shape is nearly clean, and
the `200 {"success": false}` shape is down to one reachable instance.

## What is still open

| | What | Who can close it |
|---|---|---|
| **F-29** | Seven leaked credentials | Nobody, from this repository. The rotation table (`TODO.md`, R1–R7) now names the provider, the exact steps and the CI/CD variable for each; `.gitleaksignore`'s twelve fingerprints each name the row they retire. **Owners are `[owner: ?]` on all seven** — board item 8 — and inventing names would be worse than empty cells. |
| **F-34** | `PATCH /social/notifications/all` is declared and unimplemented | The team. Finish it or remove both halves; board item 24 |
| board 25 | Should `UserResponse.reports` count answer reports? | The team — the code matches its documentation, so this is product, not a bug |
| board 26 | `GET /ratings/own/{lectureId}` answers `200 success:false` | Whoever owns the app contract |
| board 27 | Drop `professors.rating_count` and `average_rating` | Needs the `V2` migration board item 14 already parks it behind |

Each has a test pinning today's behaviour, so whichever way it goes there is an assertion to
**invert** rather than a decision to re-derive.

## What could not be verified here, and needs a person

- **The PostgreSQL and E2E layers.** They skip locally without `POSTGRES_SMOKE_JDBC_URL`, and
  CI is a private GitLab with no token on this machine. Nothing in this pass touched their
  fixtures, but `server:postgres-integration` should be read once.
- **`secrets:history`.** `.gitleaksignore` was edited (comments only; no fingerprint line
  changed) and `gitleaks` is not installed here, so the file was not re-parsed by the scanner.
- **The Mermaid diagrams.** Checked structurally, never rendered — no renderer on this machine.
  One look at GitLab's preview before the presentation.
- **`TransportLimitTests` forks a second Spring context** (`RANDOM_PORT`). Measured locally as
  exactly one extra `Tomcat started on port …`, so one extra application start in
  `server:test`. Worth knowing before someone wonders why the job got slower.

## The two habits worth keeping

**Probe, do not read.** Two of this pass's candidates read like live defects and were
unreachable; one sweep design (F-38's) would have been *incapable* of failing and was only
caught by probing the fixture first. Both were found by running something, not by reasoning
about it.

**A green test proves nothing until it has been seen red.** Every fix here was watched fail
first, and the reds are quoted in the sections above — including the three cases where the test
was written *after* the fix and had to be checked by deliberately breaking the code
(the comment/answer ordering, the message-style sweep, and the drift sweep in all three of its
directions).

---

# The consumer contract pass — 9 September

Two read-only audits arrived from the client repositories and were sitting in `docs/`: the admin
panel's (`adminweb-consumer-*`) and the Android app's (`frontend-consumer-*`). Each inventories
what that client sends and what it reads, with a file and line for every claim. This pass read
them against this code, fixed what was broken, and turned the reading into a sweep so it does not
have to be done by hand again.

**Three breaks, two of them shipped and invisible.** The suite was green throughout.

## Step 1 — the three urgent questions

### 1a · `GET /auth/me` — gone, and it had locked the panel out

Determined from the code, not the documents, because the two documents here disagreed:
`AuthController` maps five POSTs and no `/me`; `GET /me` exists only on `AdminAuthController`
under `/admin/auth`; `SecurityConfig` ends in `anyRequest().permitAll()`, so the path was a 404
rather than a 401. `docs/adminweb-tasks.md:81-83` was right and the panel's copy of an older
findings file was wrong.

The consequence was worse than a degradation. The panel bare-catches this call after login, turns
the 404 into a null identity and raises an error that fails the whole sign-in — so the panel could
not sign anybody in. **Stopped and asked**, per the brief; the decision was to restore it as a
legacy alias. → **F-39**, `CHANGELOG` 9.09 (15).

Red seen first: both new tests failed with `404 {"message":"Not found","success":false}`. The
security guard was proved separately by deleting its matcher and watching
`ApiAuthorizationMatrixTests` name `GET /auth/me -> 500`.

### 1b · Bodyless writes and `Content-Type` — no regression, and nothing had been looking

The highest-count risk either audit raised: eight client calls send no `Content-Type` because
both clients build the header only when there is a body. F-17's 415 does not catch them, and the
reason is structural — Spring raises `HttpMediaTypeNotSupportedException` only when something has
to read the body, and **no mapping in this application declares `consumes` at all**.

Scope: all **35** mapped non-GET routes with no `@RequestBody`, generated as the complement of
`bodyReadingRoutes()`. Worth stating why nothing covered this before: the three sweeps that would
have (`ApiProtocolContractTests`, `ApiErrorMessageContractTests`,
`AdminApiDocumentationDriftTests`) all gate on `readsBody`, so these routes were skipped by all
three **by construction**.

**The proof step found a defect in the new sweep itself.** With `consumes` added to two routes it
named only one: the loop authenticated as a single admin, and `PATCH /account/deleteAccount`
answers 200 and deletes that account, after which every remaining route answers 401 — not 415, so
the sweep passed while covering a fraction of its subject. It now mints a fresh account per route
and names both. This is the failure mode the repository keeps writing down, produced by the fix
for it.

### 1c · `GET /account/ratings`

`ratings[].lecture.professors` is never null and is ordered by F-21's own comparator — this
endpoint maps through `LectureResponseMapper` rather than carrying a second copy. It can be
**empty**, which is legitimate here and is where the app's `.get(0)` throws; reported to the
client rather than changed.

The enclosing `ratings[]` array had no defined order at all — F-21's shape on the one list the
F-35 sweep did not reach. → **F-41**, `CHANGELOG` 9.09 (17).

*A fixture that lied, caught and recorded.* The first version set `createdAt` on the managed
entity after insert; `@CreationTimestamp` maps the column non-updatable, so the write was dropped
silently and every row kept its insertion instant — which would have made the test pass on an
unordered bag. Written with `jdbcTemplate` now.

## Steps 2–4 — the guarantees, all of which held

Every field-presence risk either client raised came back clean. Written up with scope in
`test-findings.md` under *The consumer contract audit*: `changes`/`metadata` always present as
keys (three layers, and proved by putting `NON_EMPTY` inclusion on `AuditLogResponse` and
watching `metadata` vanish from the `USER_DELETED` entry); `actor` never null (five `NOT NULL`
columns, one persistence path, no `SYSTEM` actor type); no author ever null (deletion anonymises
in place); `authToken` on every 2xx login (already pinned, recorded rather than duplicated).

**2b did not need the stop-and-ask the brief reserved for it** — a system-originated audit entry
cannot be written, so there was no decision to take.

The eight shared enums are pinned by name at the unit layer, `RatingCategory`'s 26 constants
with a failure message saying a category cannot be added before the app ships. Proved red by
adding a `ContentStatus` constant.

## Step 5 — routing edges

`own` is not captured as a lecture id (three segments against two; the neighbour that *can*
shadow, `GET /ratings/rate`, is real and already recorded). An empty trailing segment —
`PATCH /social/notifications/`, `GET /users/` — answers **404 `Not found`**, not 500 and not the
collection route, which answers 405. Observed before the assertions were written, not predicted.

## Step 6 — the two routes whose bodies are never read

Both clean: `POST /social/comments` and `POST /ratings/rate` throw `ApiException(NOT_FOUND)`, so
no failure arrives as a 2xx the app would show as success. The only `200 {"success": false}` left
in `src/main` is behind `GET /admins/validate` and is unreachable.

**And one that was not clean at all.** `POST /answers/report` — which the brief filed under
step 3c as a naming inconsistency to record rather than change — was never served. The app has
always sent it unprefixed; this API has only ever served `/social/answers/report`; the path
matched `/answers/{id}`, which maps no POST, and answered **405**. The app announces success
before the response arrives, so no user and no developer could see it. `git log -S` confirms the
mapping was introduced already prefixed, so the path the client calls never existed.
**Stopped and asked**; decision was to serve it as an alias. → **F-40**, `CHANGELOG` 9.09 (16).

Red seen first, with `Allow: DELETE, PATCH` in the response — which is the evidence for the
diagnosis, not just for the fix.

## Step 7 — the sweep

`ConsumerContractSweepTests` reads a delimited `consumer-contract` block from each document and
checks routes, verbs and **every field the client reads** against the handler's return type, by
reflection. Both directions; rows labelled by consumer, because 42 belong to one client and 24 to
the other and a removal decision needs to know which. Rationale in
[ADR 0013](adr/0013-consumer-expectations-in-the-backend-repo.md), including why Pact was
rejected (no shared pipeline; without a broker it is a JSON file plus a dependency).

*Proved it can fail:* renamed `bugReports` to `reports` and watched it name all nine
`bugReports[...]` reads; reverted.

**It found two live panel defects on its first run.** The ratings tab reads `author` and `scores`
where this API serves `student` and `topics` — documented as `student` and `topics`, with a
worked example, since the endpoint existed. Both reads are optional-chained on the panel side, so
the author column shows "Unknown" on every row and the scores column is empty on every row.
Reported in `docs/adminweb-tasks.md`; held in `READ_BUT_NOT_SERVED`, which is asserted as an
**exact set** so the entry must be deleted when the panel moves.

The parsing was done twice. The first attempt parsed the prose tables directly and got
`ratings[].lecture.semesterSeason` wrong, because the two documents resolve their leading-dot
shorthand differently. The block removes the guess.

*A formatting slip the new guard caught immediately.* Inserting the blocks stripped a newline and
welded the separator row onto the first data row, silently costing one route. The per-consumer
row-count assertion — 42 and 24, rather than a combined floor — found it.

`ApiAuthorizationMatrixTests.studentSessionReachesTheEndpointsTheAppNeeds` now derives its route
list from the app's contract instead of the three routes it listed by hand. P-6's assertion
strictness is kept (`isEqualTo(200)`, because "not refused" is satisfied by a 404), and the
javadoc's old argument that the list *had* to be hand-written was rewritten rather than left
standing. Proved it reads the document by pointing one row at a path that does not exist.

## Step 8 — questions the audits closed

Recorded, nothing deleted. The panel calls neither `GET /admins/validate` nor either catalogue
create, so the "handle 403 on the two creates" task does not apply to it and the `/admins/validate`
confirmation is in. It still does not read `expiresAt`. The app calls neither `/auth/validate` nor
`/social/sync/comments`; **`/social/sync/comments` and its six tests are kept**, with the reason
written down — it is a live served route and those tests are the only thing checking it, so
deleting coverage because one client stopped calling it would leave the route and lose the check.

## What was deliberately not done

- **No route was deleted**, including `GET /admins/validate`. The audits answered the questions
  that were blocking those decisions; taking them is separate.
- **The two panel field defects were not "fixed" here** by renaming `student` to `author` or
  `topics` to `scores`. The document has said `student` and `topics` since the endpoint existed;
  renaming to match a client's mistake would break the contract to match the mis-reading of it.
- **The four suite-hygiene notes** found while reading the sweeps (a stale route count, a fourth
  copy of the handler-mapping walk in `OpenApiContractTests`, an unused `MappedRoutes` method, and
  `statusOf`'s bare throw for an unhandled verb) were recorded on the board as items 28–31 and
  not fixed. Three are refactors of green tests; the fourth wants a message, not a behaviour.
- **CI was not checked.** Private GitLab, no token here — job output has to be pasted.


# `dependency:scan`'s first CI run — 9 September

The one job added that day that had never run where it runs. It ran, and it failed. This is what
the log said, what the diagnosis was, and what is still unchecked.

## The log, in the order it happened

Three things, and only the third is the failure:

1. `trivy --version` → `0.74.0`, then the vulnerability database downloaded: 112 MB in about
   four seconds. **That is the failure the job comment told the next reader to suspect first,
   and it is not this one.**
2. `Restoring cache ... No URL provided, cache will not be downloaded from shared cache server.
   Instead a local version of cache will be extracted.` → `WARNING: Cache file does not exist` →
   `Failed to extract cache`.
3. `FATAL Error remote Maven repository returned 429 Too Many Requests for
   .../spring-integration-bom/7.0.6/spring-integration-bom-7.0.6.pom. Retry-After: 1800.`

No `trivy-report.json` was produced, so the artifact upload failed too — a second red line that
is a consequence, not a second problem.

## What was wrong, and it was not Trivy

Trivy resolves the Maven parent-pom chain itself. Warm `~/.m2`: local, no network. Cold: the
network, from an IP the whole runner cluster shares. The job knew this and arranged for a warm
repository two ways — it caches `.m2/repository`, and `needs: server:test` runs it after the job
that fills the cache. Both are real; neither is a guarantee:

- `needs:` orders two jobs. It does not put them on one machine, and this is a Kubernetes
  executor.
- The runner has **no distributed cache** — `No URL provided` is the runner saying so. A cache
  therefore lives on the node that wrote it. `server:test` warmed some node; this job started on
  `w3.glr.scc.kit.edu`, found nothing, and resolved over the network.

Written up as [P-9 — A cache is not an input](test-findings.md#p-9--a-cache-is-not-an-input),
because the general shape outlives this job: a comment explaining why an optimisation will
always be there is describing a dependency.

## The change

`server:test` already resolves the whole tree, so it is the job that can write it down.
`cyclonedx-maven-plugin` — declared in `pom.xml` with no version and no configuration, because
`spring-boot-starter-parent` manages both — writes
`target/classes/META-INF/sbom/application.cdx.json` during `package`, and `server:test`
publishes it as an artifact. `dependency:scan` takes it through `needs: artifacts: true` and runs
`trivy sbom` on it. Nothing in that job resolves a Maven coordinate any more, so its `.m2` cache
is gone (`cache: []`, because the file-level default would otherwise pull and push one).

## Scan coverage, and the two directions it was checked in

A scan that quietly looks at less and a scan that cannot go red both look exactly like a pass, so
both were measured rather than argued:

- **It sees no less.** `trivy fs --scanners vuln --skip-dirs target .` against a warm `~/.m2` and
  `trivy sbom` against the generated SBOM, same commit, package lists dumped with
  `--list-all-pkgs` and diffed: **identical except one line** — the SBOM adds
  `org.projectlombok:lombok`, which Trivy's pom parser drops. 107 packages either way,
  **0 vulnerabilities** either way. The repository has no other manifest for the old filesystem
  scan to have been reading: one `pom.xml`, no lockfiles, and `--scanners vuln` does not look at
  `src/Dockerfile`.
- **It can still go red.** The SBOM was rebuilt against `spring-boot-starter-parent` 4.0.6 — the
  version 4.0.8 replaced — and scanned: **exit 1, 13 HIGH**. Reverted.

Both runs used `docker.io/aquasec/trivy:latest`, the image the job uses.

## What could not be verified here

- **The CI run itself.** Private GitLab, no token: the artifact handover, which is the new thing
  that can fail, has only been reasoned about. Item 10 on the board.
- **The 4.0.6 number is not comparable to the 57 from 9 September.** It was measured with today's
  vulnerability database and with this pom's `tomcat.version` and `bcprov` overrides still in
  place. It is evidence that the gate fails, not a count of anything.

## An accident worth writing down

The first local `clean verify` of this change reported nine failures — a missing `UserRatingDto`,
a missing `AuditLogRepository` bean, contexts that would not start. None of it was real: a
`-Ppitest mutationCoverage` run had started in the same working directory at the same second, and
two Maven builds sharing one `target/` delete each other's classes. The verification was redone
in a copy of the tree with its own `target/`, which is the only way to get an answer while
another build is in flight. **The pitest run in progress was damaged by this and its numbers
should be thrown away.**

---

# Delivery preparation — 9 September

No code was written in this pass. It re-measured what the documents claim, wrote down what CI
actually runs, made F-29 handover-ready, audited the two documents the Betreuer will ask for,
and left a demo note.

## The numbers, re-measured, and the exit code that was not real

**The last pass reported `exit 0` and the zero came from an `echo` at the end of the command,
not from Maven.** A shell reports the status of the *last* command in a list, so
`./mvnw verify; echo done` is 0 whatever Maven did. Every figure that rested on that run was
therefore unverified, and `target/jacoco.exec` was sitting in the tree from a run whose `clean`
could not be confirmed — P-5's exact condition.

`./mvnw clean verify`, exit code captured into a variable on the line that ran it:

| | Measured | The document said |
|---|---|---|
| **Maven exit code** | **0** | — |
| Tests | **1019**, 0 failures, 31 skipped | 1001 (`test-plan.md`), 975 and 977 elsewhere |
| LINE | **96.70%** (3371/3486) | 96.67% (3363/3479) |
| BRANCH | **93.16%** (1022/1097) | 93.16% (1022/1097) — correct |
| Missed | **115 lines, 75 branches** | 125 branches / 154 lines, and 124/154 two sections later |
| `jacoco:check` | all checks met | — |

Run twice, `clean` both times, identical both times; the second was after another session had
changed `pom.xml` in this tree. The 31 skipped are exactly what the document said they were:
`DemoApplicationTests`, the 24 PostgreSQL integration tests and the 6 journeys.

**PIT, run locally to settle a contradiction.** ADR 0005 and board item 12 said 222 mutants
have no coverage; `test-findings.md` said 324. Neither was wrong — they are different runs.
Today: **1134 mutants, 840 killed (74%), 72 survived, 222 with no coverage**, 77 mutated classes
over 50 test classes. Reproduced on two independent runs, because the first one overlapped
another build in this tree and a damaged run had to be ruled out. The 8 September figures
(1194 / 797 / 73 / 324) are kept where they are, labelled as the first run.

ADR 0005 also claimed the profile covers "eighteen test classes". It did when it was written;
the same package globs now match 50, because the refactor split the services. The globs are why
the scope survived the refactor untouched — the number was never the decision, and the record
now says so.

## What CI actually runs

Read from `.gitlab-ci.yml`; the table is in
[test-plan.md](test-plan.md#four-layers-do-not-run-on-a-developer-machine-and-ci-status-cannot-be-read-from-here).
The parts worth repeating:

- `server:postgres-integration` **runs on every push and MR**, blocking, against a `postgres:16`
  service. It is **two Maven invocations** — the six integration classes, then
  `EndToEndJourneyTests` — and **the "exactly one context" guard is in place on both**: each
  counts `PostgresTestDatabase: resetting the public schema` in its own log and fails unless it
  is exactly 1.
- **There is no separate E2E job.** E2E is that second invocation.
- `secrets:new` is **blocking**, push and MR. `secrets:history` is **schedule-only and
  `allow_failure`**, and is expected red: all twelve `.gitleaksignore` fingerprints are still
  commented out.
- `mutation:nightly` is **schedule-only and `allow_failure`**.
- `dependency:scan` (Trivy) is **blocking**, push and MR. `coverage:line-target` runs on push
  and MR but is **`allow_failure`** — yellow, never red. `sonarcloud` runs only if `$SONAR_TOKEN`
  exists, and is `allow_failure` when it does.
- **There is no Mermaid or diagram-rendering job.**

**No claim is made about the last pipeline.** CI is a private GitLab, there is no token on this
machine, and nothing here has seen a job result. The table says what *should* run. Job output
has to be pasted in.

## F-29, made handover-ready

The rotation table had provider and procedure on all seven rows, **no owner on any row, and no
date column at all** — which reads as "we knew and did nothing" however much procedure is
written next to it. Now: owner **Alparslan** and target **before submission (13.09)** on all
seven, with the note that a row slipping past that date should be re-dated rather than left to
look done. Board item 8 is closed for this table and still open for the rest of the page.

The `.gitleaksignore` mapping was re-checked and is **complete**: twelve fingerprints, each
labelled with the R-row it retires, each carrying its reason, all twelve still commented out —
so nothing in the file claims a rotation that has not happened. The prose beside the table said
"all five findings are listed commented out"; it is twelve, because a fingerprint is per rule
*and* per commit. Fixed.

F-29's status now reads **"Open — needs a rotation at the provider, not a commit"**.

## Delivery documents

**ADR audit.** All thirteen had the five sections plus `Sources`, and **every citation checks
out** — the F-, P- and BUG- anchors all resolve, and so do the `pom.xml`, `.gitlab-ci.yml`,
`CHANGELOG` and `CLAUDE.md` references. **No invented rationale was found**, which is the thing
that was actually being looked for.

Four corrections and one addition:

- **0001 renamed** `four-test-layers` → `five-test-layers`. It was filed before the contract
  layer existed and its own table had been listing five for some time.
- **0014 written** — `GET /auth/me` restored as a time-boxed legacy alias. This was the one
  decision in the brief's list with no record, and it is a genuine reversal: the `CHANGELOG`
  said "gone for good" and then un-said it. Extracted from F-39, the worklog entry that records
  the stop-and-ask, `CHANGELOG` 9.09 (15) and the original deletion rationale on the board.
- **0005** re-measured (above). **0011**'s Spring Framework version corrected 7.0.7 → 7.0.9.
  **0002**'s `.gitlab-ci.yml:26-30` citation replaced with the variable names, because a line
  range clips the moment the file moves — and it already had.

F-40's decision (a separate `LegacyAnswerReportController` rather than widening
`SocialController`) is the same shape as 0014 and still has no ADR. Left, because it was not in
the brief's list.

**Class diagrams.** Six `classDiagram` fences, structurally checked and clean: every arrow
endpoint declared, braces balanced, and **all 99 class names resolve to a file under
`src/main`** — the post-refactor names are right. Two things were added: `RatingCategory` was
used by an arrow without being declared, and the five classes that appear inside a diagram
belonging to *another* package now carry that package as a stereotype, so a package boundary is
visible rather than implied by the heading. `namespace` blocks were deliberately not used —
nothing here can render Mermaid, and syntax the rest of the file already uses is the safer bet.
**The GitLab preview still needs a look.**

**Scope limits** — two new sections in *Where the suite stops*: the drift sweep's `" /admin"`
filter, written as a scope limit rather than corrected (its reverse direction cannot see the
legacy surface the panel actually calls), and the table of what does not run locally.

## One new defect, recorded and not fixed

**F-42 — deactivating a catalogue row hides it from the only list the panel reads.** The backend
is not at fault: `/data/lectures/all` and `/data/professor/all` exist precisely so a deactivated
row stays reachable, and `PATCH {"active": true}` works. The panel does not call them — its own
contract records the pair as *"Not called"* — so `active: false` from the catalogue form removes
the row from every screen it has, and the id needed to undo it goes with the row.

Measured by reading, not by running: `LectureService.getLectures(includeInactive)`,
`ModerationCatalogController:38-40`, `LectureModerationService:108`, and the called-route list in
the panel's contract. Deferred because the fix is in the panel repository and this pass wrote no
code. Estimated at about an hour there and nothing here. Board item 32,
`adminweb-tasks.md` task 2b, and a pointer in the findings that explains why it is **not** one
of the fifty: nothing in this repository can pin it with a test.

## Documents updated

`test-plan.md`, `test-findings.md`, `TODO.md`, `adminweb-tasks.md`, `class-diagrams.md`,
`adr/README.md`, ADRs 0001 (renamed), 0002, 0005, 0011, new ADR 0014, and this file. New:
`docs/coursework/demo-notes.md`.

## Still open: one thing

**F-29's rotation.** Seven credentials, plan written, owner and date now on every row, and not
one of them closable from this repository. Everything else on the delivery list is done.

## Two things the next person should know about this pass

- **Another session was editing `pom.xml` and `.gitlab-ci.yml` in this working tree while this
  ran.** A `pom.xml` read mid-write killed one PIT run outright, and a `clean` from the other
  build removed `target/site` under this one. Every number above was re-measured afterwards and
  reproduced. Two agents sharing one `target/` is the hazard; the other session hit it from the
  other side and wrote it up under *An accident worth writing down*.
- **Nothing was committed.** The working tree carries this pass's documentation changes and the
  other session's `pom.xml` / `.gitlab-ci.yml` / `deployment.md` changes together.

# 10 September — the panel's deleted-accounts request

## What was asked, and what was measured first

The admin panel asked for soft-deleted accounts to be **listable and restorable**. The pass was
scoped to measurement before any code: four obstacles read out of the code rather than out of the
request, three options costed, and the decision left to the panel. The measurement and all three
options are in [adminweb-tasks.md](adminweb-tasks.md) §6, so the panel could see what was
possible; only the outcome is repeated here.

**Decided: visibility, no restore.** `GET /users?status=DELETED` lists them, `GET /users/{id}` and
`GET /users/{id}/warnings` read one, and nothing else moved. A restore was refused because the
scrub is irreversible: the only surviving copy of the identity is `audit_logs.target_label`, a
display string, and `biography` has no copy anywhere. Splitting deletion from anonymisation to
make a real restore possible was written up and declined in
[ADR-0015](adr/0015-deletion-not-split-from-anonymisation.md) — the blocking reason is not the
1.5–2 days but what the grace window means, and F-46 sitting inside it.

## The consumer contract changed a backend decision, for the first time

The open question after the first change was whether to open the single read too. It was settled
**out of `adminweb-consumer-contract.md`**, not out of this repository: that document records that
the panel's `fetchProfile` reads the account and its warning history in one pass and collapses a
`404` from either into a single "account gone" branch (`useUserProfile.ts:24-32`). Two consequences
fell out of one recorded fact — the two routes had to move as a pair, and the option of opening
`GET /users/{id}` alone was worthless, spending a contract change to fix nothing on the screen.

[ADR-0013](adr/0013-consumer-expectations-in-the-backend-repo.md) argued for keeping consumer
expectations in this repository as a generated list under test. **This is the first time that
document has directly changed a backend decision** rather than confirming one, and it is worth
naming as the return on having written it.

## Inversion applies to assertions, not to mock setup

Three characterization assertions were inverted, not deleted, per
[ADR-0007](adr/0007-characterization-and-inversion.md): the `400` in
`UserDirectoryServiceTests` and `AdminApiIntegrationTests:2504`, and the deleted-account `404` at
`AdminApiIntegrationTests:475`, which the first run of the changed code is what found — it was not
in the plan and no grep for the refusal message reached it.

`WarningServiceTests:322` was **not** an inversion and was simply repointed. It stubs
`findByIdAndStatusNot` inside a test about a warning with no issuer; the stub is how the fixture
is delivered, not a claim about behaviour, and re-pointing it at `findById` asserts nothing new.
**The rule attaches to what a test asserts, not to every line that mentions the changed method.**
Treating a stub as a characterization to be ceremonially inverted would put a behavioural claim in
a test that never made one.

**One test sat between the two categories, and it is the more interesting case.**
`getWarningsForStudent_deletedStudent_readsAsNotFound` read like coverage of the deleted account,
but what it stubbed was an empty `Optional` — so what it actually pinned was "nothing came back,
answer 404", the *unknown-id* rule, which is still true. It only looked like the deleted case
because the query it stubbed happened to be the one that excluded them. It was split in two: the
unknown-id rule kept under an honest name, and a new test for the deleted account that now lists.

That is [P-2](test-findings.md#p-2--integration-tests-hide-unit-targets)'s shape once more, and
the third sighting of it in this pass — the second being F-46, where `SessionIssuerTests:516`
pins a neighbouring rule and reads as though it covered `LoginCodeService:76-79`, which no test
reaches. P-2 was recorded as a coverage-counter problem. Both sightings here fooled a **reader**
instead, which is the harder half: no column reports it, and the only thing that catches it is
reading what a test asserts rather than what its name says.

## Red seen before green, in every case

| Assertion | The red |
| --- | --- |
| `UserDirectoryServiceTests` DELETED filter | 3/3 `ApiException: Deleted users are not listed` at `UserListQuery.parseStatusFilter:66` |
| `AdminApiIntegrationTests` DELETED filter | `Status expected:<200> but was:<400>` |
| the listing's positive assertion | made red on purpose by forcing the exclusion unconditional again, then restored |
| `WarningServiceTests`, both new tests | 3 × `UnnecessaryStubbingException` — `findById` stubbed and never called |
| the profile-modal pair | `Status expected:<200> but was:<404>` |

## What was deliberately not done

- **F-46 was not fixed.** It touches the account lifecycle and is deferred past submission by
  decision; it stays on the deferred list in [TODO.md](TODO.md) row 36 with its estimate.
- **`ModeratedStudents.findMutable` was not touched.** Every mutating route still refuses a
  soft-deleted target, so the visibility change cannot grow into a restore by accident. Pinned
  by two assertions in the new integration test rather than left as an intention.
- **`RatingAverages` and `LectureResponseMapper` were not touched.** Both still exclude deleted
  users' ratings from every average. Nothing in this pass changed what the app computes.

---

# 10 September, second pass — F-42 measured, and the flag behind it pinned

## What was asked, and what the measurement changed

The panel asked for a feature — a deactivated lecture or professor should stay visible in the
catalogue, marked inactive, instead of disappearing — and the request came with an obstacle
attached: the routes that return deactivated rows are documented under `/admin`, and F-43 says
`/admin/**` on the deployed host is the panel's own static server. The pass was scoped to
**measure and bring options**, not to implement.

The measurement answered the question before the options did. `ModerationCatalogController:75-86`
already maps both `/all` reads twice, under `/admin` and unprefixed, and has since `5a11d29`
split the two APIs. **The option that looked cheapest was already shipped**, so what was on the
table was never "build it" but "say so in the documents".

## Verified against the deployed host, not against the repository

The serving layer was named from the response headers, the method F-43 established:

| Request | Answer | Named by |
| --- | --- | --- |
| `GET /admin/data/lectures/all` | `200 text/html`, the panel's `index.html` | `no-store`, `nosniff`, `DENY`, `same-origin`, `etag`, `last-modified` — the panel's nginx template |
| `GET /data/lectures/all` | `401 {"message":"Not logged in","success":false}` | `vary: Origin`, `pragma: no-cache`, `expires: 0` — Spring |
| `GET /data/professor/all` | `401`, same body | same |
| `GET /data/lectures` | `200`, `Found 92 lectures` | same |

A `401` was the good answer: it proves the request reached this application and was refused for
want of a token rather than swallowed upstream.

**And the production data was counted, not assumed.** 92 lectures, 132 professors, and **no row
anywhere carrying `active: false`** — including the professors nested inside each lecture. The
flag has never been set on the deployed data. That single number explains two findings at once:
why F-42 has never actually cost anyone a row, and why F-47 below is not hurting anybody today.
Both are traps still armed rather than fires.

## Three options, and the two that were rejected

Costed on `docs/adminweb-tasks.md` §2b and decided by the person who owns the panel relationship:
**A**, read the twins that exist. **B**, fix the host routing — rejected: it is one nginx on the
deployment host, version controlled in none of the three repositories, it carries F-44's
precedent, and it buys *nothing* for this feature. **C**, `?includeInactive=true` on the public
lists — rejected on a specific mechanism rather than on taste: Spring's `requestMatchers` match a
path and a verb and **not a query parameter**, so the admin-only rule would have had to move into
the handler as an `isAdmin()` check. That is exactly the shape `SecurityConfig:86-89` records as
removed, where the in-handler check answered a non-admin `200 {"success": false}` and an
anonymous caller a `500`. C would have re-opened a fixed defect to reach rows that were already
being served.

## One new defect, pinned and not fixed

Reading every consumer of the `active` flag — the same method as F-39/F-40/F-42, pointed at a
*field* instead of at a route — turned up **F-47**: `grep` for `isActive()`, `setActive(` and
`ActiveTrue` returns seventeen lines in `src/main/java`, and only the two active-only list
queries gate anything. So the single reads serve a deactivated row to an anonymous caller, both
write paths still accept ratings and comments on a deactivated lecture, and a deactivated
professor stays in every active lecture's staff list and inside its generated `title`.

Five characterization tests were added. **Nothing was fixed**, and that is a decision rather than
caution: the three consequences are not the same kind of thing until somebody answers whether
`active = false` means *hidden from the catalogue* or *retired*. (3) is likely deliberate either
way — who taught a lecture is a fact about the past. (2) reads as an oversight. The distinction
is recorded as its own board item, [TODO.md](TODO.md) item 38, because a later reader who finds
only "three behaviours, unfixed" would have to re-derive which of them anyone meant.

## Red seen before green, in every case

Every one of the five was written **inverted** first — asserting what a fix would produce — run,
and flipped only after the failure had been observed:

| Assertion | The red |
| --- | --- |
| `LectureApiIntegrationTests.getLectureByIdStillServesADeactivatedLectureToAnAnonymousCaller` | `Status expected:<404> but was:<200>` |
| `ProfessorApiIntegrationTests.getProfessorByIdStillServesADeactivatedProfessorToAnAnonymousCaller` | `Status expected:<404> but was:<200>` |
| `RatingServiceTests.submitRatingIsAcceptedForADeactivatedLecture` | `[nothing was thrown]` |
| `CommentServiceTests.submitCommentIsAcceptedForADeactivatedLecture` | `[nothing was thrown]` |
| `LectureResponseMapperTests.toResponse_deactivatedProfessor_staysInTheListAndInTheTitle` | `["Abt=false", "Sanders=true"] to contain exactly ["Sanders=true"]` |

## An unnecessary stub is evidence, not an obstacle

The two service tests needed a lecture that is deactivated, and stubbing `isActive()` strictly
would have failed as an `UnnecessaryStubbingException` — because `submitRating` and
`submitComment` never call it. That failure *is* the finding. Rather than working around it, both
tests keep the stub under `lenient()` with the reason written next to it, and then say the same
thing directly: `verify(deactivated, never()).isActive()`. When the rule arrives, the `lenient()`
becomes a real stub and the assertion inverts. This is the mirror of the note in the previous
pass about inversion applying to assertions rather than to mock setup: here the mock setup is
what carries the evidence, so it was written to be read.

## Documents updated

- `docs/admin-api.md` — both `/all` rows now list both paths, name the one reachable on the
  deployed host, and state that the unprefixed path is **not** legacy and is not being retired.
  The prose tables only; the `contract-table` block that `AdminApiDocumentationDriftTests` parses
  was not touched, and the drift sweep scopes itself to routes containing `/admin`, so nothing in
  it moved.
- `docs/adminweb-consumer-contract.md` — the *"Not called"* row now records the decision and the
  scheduled move instead of the old rationale. It does **not** claim the panel calls the routes
  today, because it does not.
- `docs/adminweb-tasks.md` §2b — the measurement, the three costed options, the decision, and a
  six-item instruction the panel can work from.
- `docs/TODO.md` — item 32 updated with the decision, item 37 (F-47) updated to pinned, item 38
  opened for the product question.
- `docs/test-findings.md` — F-47 as a full entry, with the tests that pin it.
- `docs/test-plan.md` — a section on which layer each of the five sits at and why.

**No `CHANGELOG` entry.** Nothing client-visible changed: no route, no field, no status, no body.
The whole backend change is documentation plus five tests.

## What was deliberately not done

- **F-47 was not fixed.** It waits on the product question in item 38, and the five assertions
  are written to be inverted rather than deleted when the answer comes.
- **The `ConsumerContractSweepTests` counts were not moved.** They go red when the panel's
  contract document gains the two rows, and that red is the signal the move actually happened.
  Editing them now would spend the signal in advance and assert a migration nobody has made.
- **`admin-api.md`'s contract table and `UNDOCUMENTED_ON_PURPOSE` were not touched.** The
  unprefixed twins are outside the drift sweep's scope by its own rule; widening that scope is a
  separate decision from documenting two routes in the prose.
- **Nothing was implemented for the panel.** The catalogue list and the All/Active/Inactive filter
  are in the panel repository, after submission, and this repository serves everything they need
  today.

---

# 10 September, third pass — F-46 measured and closed, F-48 opened underneath it

## What was asked, and what the measurement changed

Two questions: does `PATCH /account/deleteAccount` reach the audit log, and does the silent
reactivation leave a record. Both answers were "no", and confirming that took reading rather than
grepping for a branch: **neither path has an `AuditWriter` at all** — not in `AccountController`,
`AccountService`, `StudentService` or `LoginCodeService`. It was absence, not a branch that
happened not to be taken.

Then the third question — what the panel could see if both were closed — turned the pass around.
The answer depended on **which log** the entries went to, and that depended on a fact about the
panel rather than about this repository: it deleted its Activity Log screen and asserts the route
is gone. An `ACTIVITY`-scope entry would have been semantically right and invisible.

**And reading `LoginCodeService:63-84` line by line, instead of summarising it, found something
larger than the missing record.** The flip happens when a code is **requested**, not when one is
entered, and the route is public. That is F-48, and it is why F-46's severity as written was
wrong.

## Three decisions, taken rather than defaulted

Recorded in [ADR-0016](adr/0016-self-service-lifecycle-recorded-not-authorised.md) with the
alternatives that were refused:

- **Administrative scope**, not activity — the scope follows the log a reader needs, which
  `AuditActionScope`'s own note already says and which a refused *admin* login already
  demonstrates in the opposite direction.
- **New constants**, not `USER_DELETED` reused — reuse would have invalidated the rule the panel
  had just been given in writing, that a `DELETED` row with a real username has no matching
  entry.
- **Not revertible** — recording the revival as a `USER_UPDATED` field diff would have been the
  cheapest option and would have put a working restore into the panel through the revert
  machinery, in a product where [ADR-0015](adr/0015-deletion-not-split-from-anonymisation.md)
  refused restore on purpose.

## The transactional boundary was chosen, and the precedent was checked rather than copied

`StudentService.deleteAccount` is `@Transactional` now so the status change and its entry commit
together. `AccountReactivator` is a bean of its own because a `@Transactional` method called from
inside `LoginCodeService` is not proxied — the annotation would have been inert and the two
writes would have committed separately.

Both use `REQUIRED`. `AuditWriter.writeRefusal` uses `REQUIRES_NEW`, and the temptation was to
follow it; the reason it does is written on it — **its callers throw immediately afterwards and
would roll a joined record back**. Nothing here throws after the write, so `REQUIRES_NEW` would
only have let an entry outlive the change it describes. The comment on each new boundary says
which of the two it is and why, so the next person does not have to re-derive the difference.

**What deliberately did not change:** the revival still commits before the code is mailed, so it
still survives a delivery failure answered `500`. Widening the new transaction over delivery
would have fixed half of F-48 as a side effect of a refactor, which is not a change to make
quietly.

## Red seen before green, in every case

Nothing reached the reactivation branch before this pass — `LoginCodeServiceTests` did not
contain the word `DELETED` — so the branch was pinned first and the audit assertions were added
on top of it:

| Assertion | The red |
| --- | --- |
| `AuthApiIntegrationTests.anAnonymousLoginCodeRequestBringsBackAnAccountThatDeletedItself`, the branch | `expected: DELETED but was: ACTIVE` — asserted the account stays deleted, then flipped |
| the same test, the two entries | `AssertionError: no USER_SELF_DELETED entry was written` |
| `StudentServiceTests.deleteAccount_existingStudent_recordsTheDeletionAgainstTheAccountItself` | Mockito `Argument(s) are different!` — wanted `USER_DELETED`, got `USER_SELF_DELETED` |
| `AccountReactivatorTests.reactivateSetsTheAccountBackToActiveAndSavesIt` | `expected: DELETED but was: ACTIVE` |
| `AccountReactivatorTests.reactivateRecordsTheRevivalAgainstTheAccountAndSaysNobodyProvedTheyOwnedIt` | `Argument(s) are different!` — wanted `USER_UPDATED`, which is exactly the shape the decision refused |
| `LoginCodeServiceTests.requestLoginRevivesADeletedAccountBeforeTheCodeIsEvenIssued` | `VerificationInOrderFailure` — the order asserted the other way round |
| `LoginCodeServiceTests.theRevivalOfADeletedAccountOutlivesAFailureToSendTheCode` | `expected: DELETED but was: ACTIVE` |

The compile error that came first is worth naming too rather than counting as a red: the audit
assertions did not compile until the two constants existed, which is the one kind of failure that
proves nothing about behaviour.

## An overstated severity is as useless as a missed one

F-48 is written with what it is **not** in it: it is not account takeover. The login code still
goes to the KIT address, so the caller who triggers a revival cannot sign in and reads nothing.
What they can do is change the account's state and what the app shows about its owner — the real
username returns on every comment and answer, the ratings return to the public averages, and the
row leaves the panel's deleted list. That is serious and it is a different thing, and the finding
says which.

## Documents updated

- `CHANGELOG` `10.09 (20)` — two new action values, with both record shapes and what a client has
  to do (nothing).
- `docs/admin-api.md` — the two actions in the meta list, a section of their own, the
  non-revertible paragraph, and a correction to the deleted-accounts note, which described only
  the administrative deletion.
- `docs/adminweb-tasks.md` §6 — the section that told the panel a self-deletion has no entry now
  says it does, names the constant, and opens F-48 in its place.
- `docs/adr/0016-...` — the three decisions; `docs/adr/0015-...` corrected, since it cited F-46
  as a precondition for a reason that is now F-48.
- `docs/TODO.md` — item 36 closed on its record half, item 39 opened for F-48 with four costed
  options.
- `docs/test-findings.md`, `docs/test-plan.md` — the F-48 entry and the layer rationale.

## What was deliberately not done

- **The reactivation was not fixed.** Requiring authentication, moving the flip to a completed
  login, or closing the public route are all product decisions, costed under item 39 and taken
  after submission. The three characterizations invert rather than delete when it is answered.
- **`SessionIssuerTests:516-525` was not touched.** It pins that a `DELETED` account cannot
  complete a login, which is still true and is the assertion option A would invert. Naming it in
  the option is what stops that estimate being made without it.
- **`deletedAt` was not exposed.** It is still set only by the administrative path and served by
  no DTO. The new entries carry `createdAt`, which answers the same question for both kinds of
  deletion, and adding a field to the user response is a contract change nobody asked for.
- **The rate limits were not changed.** They bound the rate of F-48 and not its effect, and
  tightening them would have looked like a fix while being none.

---

# 10 September, fourth pass — closing verification, no new code

A verification pass, not a work pass. **No production code was written, no test was added, and
no finding was fixed.** What follows is what was measured, what was found to be wrong in the
documents, and what is still open.

## A — the real numbers

`./mvnw clean verify`, with `clean`, on 10 September:

| | |
| --- | --- |
| **Maven exit code** | **0** — captured as `$?` on the line after Maven and nothing else, not from a following `echo` |
| Tests | **1048** run, **0 failures, 0 errors**, 31 skipped locally |
| LINE | **96.68%** — 3407 covered, 117 missed, 3524 total |
| BRANCH | **92.95%** — 1042 covered, 79 missed, 1121 total |
| Gates | `jacoco:check` LINE 0.90, BRANCH 0.88 — both met; CI's soft LINE target of 95 cleared by 1.68 points |

Read from `target/site/jacoco/jacoco.csv`, summed across the bundle, not quoted from any
document. The exit-code discipline is P-5's neighbour and is written into
[test-plan.md](test-plan.md) because this repository has already reported a green build off an
`echo`'s status once.

**Both percentages are slightly below the 9 September figures** (96.70 / 93.16) while the suite
grew by 29 tests. That is arithmetic, not a regression: the denominators grew by 38 lines and 24
branches — `AccountReactivator`, two audit writes, two enum constants — and the new branches are
covered less densely than the bundle average. The documents said 1019 tests and 96.70 / 93.16;
**the documents were corrected, not the numbers.**

## B — what the documents were getting wrong

Seven documents had drifted, and three of the seven contradicted a decision taken two days after
they were written.

**Three claims that a decision had already falsified:**

- `admin-api.md` said the legacy paths *"are removed once [the panel] has [moved]"*. They are
  not: the 10 September routing decision made the unprefixed paths the production contract
  permanently. Corrected, with the reason and the pointer to F-43.
- `admin-api.md` told a reader that `/admin/data/.../all` covers what the panel needs. On the
  deployed host those two answer `200 text/html` from the panel's own nginx. Corrected to the
  unprefixed pair.
- `test-plan.md` said the drift sweep's one-sided `/admin` filter *"stops mattering when item 23
  lands and the legacy surface is deleted"*. Item 23 is withdrawn and the legacy surface is
  staying, so **the gap is permanent, not transitional** — and a scope limit that reads as
  temporary is worse than one stated plainly. `TODO.md` item 23 is struck through with the same
  reasoning rather than deleted.

**Six broken document anchors**, found by resolving every in-repo markdown link against the
headings it points at: three copies of a `C-01` link missing a hyphen, one `C-06` link with the
wrong tail, and two links to P-8 built from an older version of its own heading. The last pair
uncovered a real inconsistency rather than a typo: the at-a-glance row said `admin-api.md` had
drifted **four** times while P-8's entry says **five** and lists them. The entry is the
authority; the row was corrected.

**Two class-diagram members that no longer exist**, found by resolving every `+method()` in
`class-diagrams.md` against the class it is drawn on: `LoginCodeService.requestLoginCode()` is
`requestLogin()`, and `OtpService.generate()` is `issue()`. All 99 class names in that document
do resolve to real files. `AccountReactivator` was added to the auth diagram — it is a new
collaborator of `LoginCodeService`, so leaving it out made the picture wrong about a dependency,
not merely incomplete.

**Seven stale line citations in the demo notes**, all of them from code moving underneath them:
the `active` branches in both moderation services, the rate-limit and GitLab property blocks, the
bootstrap-email line and the lecture-delete cascade. Every one was re-read and corrected. The
note now says it was re-checked and that several numbers had moved, because a demo note whose
citations cannot be followed is the thing it exists not to be.

**Two source-attribution gaps in `test-findings.md`.** The "found by" table stops at the register
of fifty, so F-42 to F-48 — seven findings, including the two open ones that matter most — had no
row saying where they came from. A second table was added rather than renumbering the register:
one from the consumer contract audit, three from curling the deployed host, three from measuring
a client request instead of answering it. **That third row is a method and is now named as one**:
each of those three came from being asked for a feature and reading the code it would touch.

**One duplicate-looking definition.** F-48 had a `###` section in `TODO.md` and a full entry in
`test-findings.md`. The board section is now headed *"Item 39 — F-48's four options, costed"* and
says in its first line which document defines the finding. The costed options stay on the board,
which is where a decision-pending item belongs.

## C — the scope limits, now written rather than implied

[test-plan.md](test-plan.md#where-the-suite-stops) already carried performance and load,
injection and output escaping, migration reversibility, the drift sweep's one-directional filter
(F-44's surface), the drift sweep's silence about routes the deployment shadows (F-43), and the
four layers that do not run locally with the CI table that says where they do. Two were fixed or
added:

- the `/admin`-filter limit no longer describes itself as temporary;
- **`active = false` is a filter on two queries and nothing else** is now stated as a scope limit
  in its own right, with the production measurement that explains why neither F-47 nor F-42 has
  cost anybody anything: 92 lectures, 132 professors, **not one row with `active: false`**. Both
  findings are armed rather than burning, and both go live the first time an operator uses that
  switch.

## D — F-29

Re-checked, unchanged, and correctly stated: **open, needs a rotation at a provider, not a
commit.** All seven rows carry a credential, a provider, where the rotation is done, an owner and
a target date of 13.09. `.gitleaksignore` holds **twelve fingerprints, every one still commented
out**, each labelled with the `R`-row it would retire — so nothing in the repository claims a
rotation that has not happened. Nothing to correct.

## E — the demo notes

Beyond the citation corrections: the `active` switch section now records that the routes the
panel would need already exist unprefixed and already answer in production, that the fix is
option A and is scheduled after submission, and that nothing on the deployed data is deactivated
today. The rate limits, the hidden GitLab action and the F-39 sign-in story were re-read against
the code and are unchanged.

## What is still open

**One thing needs doing and it is not a commit:** F-29's seven rotations, at six provider
dashboards, target 13.09.

**Four decisions wait for after submission**, and none of them blocks anything:

| | Waiting on |
| --- | --- |
| **F-48** | Which of four costed options. An inclination toward A is recorded on the board — the existing intent implemented at the login rather than at the request — and marked as an inclination, not a decision |
| **F-47** | Does `active = false` mean *hidden from the catalogue* or *retired*? |
| **F-46's remaining half** | Nothing: the record is written. What is left of it is F-48 |
| **F-42** | The panel's catalogue list and its All/Active/Inactive filter — work in the other repository, needing nothing from here |

**And two entries stay open in the register of fifty for reasons that are not commits:** F-29
above, and F-34, a half-built feature awaiting a product decision.

---

# 10 September, fifth pass — closing the Abgabe list

The inventory from the previous pass became a decision list, and this pass worked it. Three items
came back **decided as no action** and were not reopened: the six real KIT identifiers and first
names, the real professor names in fixtures, and the `## Notebook` section's content. F-29's
rotations stay where they belong, at the providers.

## The item that mattered: a reviewer could not use the product

Following `README.md` exactly produced a running API nobody could log into. Not a documentation
gap — a **500**. A login is a one-time code delivered by e-mail with no other way in;
`application.properties:30-31` defaults `spring.mail.host/port` to `localhost:25`, nothing
listens there inside the container, and `LoginCodeService:98-103` turns the delivery failure into
`500 "Could not send login code"` and discards the code. Even past that, the reviewer would have
been a *student*: the local stack set no `ADMIN_BOOTSTRAP_EMAILS`, so it bootstrapped the six
hardcoded team addresses.

**Fixed rather than documented**, because the documentation-only option asks a reviewer to obtain
SMTP credentials from a third party before anything works, and we cannot verify that for them.
`docker-compose.yml` gained `axllent/mailpit:v1.31.1` and the backend gained **two** environment
variables — `SPRING_MAIL_HOST` and `SPRING_MAIL_PORT` — because `application.properties:34-35`
already defaults the auth and STARTTLS toggles to `false`, which is exactly what an
unauthenticated catcher wants. It also gained `ADMIN_BOOTSTRAP_EMAILS: admin@student.kit.edu`, so
whoever starts the stack is an administrator instead of collecting `403` on every panel screen.

**The blast radius was measured before anything was written**, because a compose change sounds
like a deployment change: `.gitlab-ci.yml:338` copies `docker-compose.prod.yml` to the server *as*
`$DEPLOY_COMPOSE_FILE`, so the local file is never deployed; `DockerConfigurationTests` never
opens a compose file, and **no test under `src/test` reads any compose file, `.env.example` or the
`Dockerfile`**; the only consumer is `scripts/docker-smoke-test.sh`, which no CI job calls.

## It was verified, not asserted

There is no `docker` on this machine, only `podman` — so `docker compose up` could not be run and
that is stated rather than glossed. What *was* run is the mechanism the README documents, with the
same two images and the same variables: Mailpit and `postgres:16` under podman, the built jar
against them.

| Step | Result |
| --- | --- |
| startup | `Administrator bootstrap complete: 1 configured, 1 accounts created, 1 promoted` |
| `POST /auth/request-login` | `{"message":"Login code sent","success":true}` — was a 500 before |
| Mailpit inbox | one message, `Your Login Code for RateMyProfApp`, code `eSreC4` |
| `POST /auth/login` | `Login successful`, with an `authToken` |
| `GET /auth/me` | `"role":"ADMIN"` |
| `GET /users`, `GET /system/status` | `200` and `200` — admin-only, both open |

What is **not** verified from here is compose's own orchestration: service-name DNS and
`depends_on`. Service-name resolution is the same mechanism `SPRING_DATASOURCE_URL` has always
used in this file, so it is not a new risk, but it has not been executed and is not claimed.

## What else closed

`pom.xml` lost four empty Initializr blocks and gained a real `<name>` and `<description>`; the
licence question and the `com.example:demo` coordinates went to the board rather than being
decided here. `testclient/TestClient.java` got a header saying it is a historical manual client
and naming the three things in it that are out of date — **framed rather than rewritten**, because
rewriting a record destroys it. The `## Notebook` got its one framing sentence and nothing else.

## The class diagrams were the largest piece, and the audit found more than name drift

**Four arrows described dependencies that exist in neither direction.**
`SuccessfulLoginListener --> LoginLocationService` (the listener injects only the interface — the
lookup hangs off `LoginNotificationMail`, the implementation), `UserResponseMapper ..>
UserReferenceMapper` (the real callers are four moderation services), `LectureResponseMapper -->
LectureLabels` (that mapper never references it) and `KeysetPage --> KeysetCursorCodec`, whose
javadoc says the opposite in as many words.

**Three `..|>` realizations were drawn on classes that do not implement the interface.**
`ContentRevertHandler`, `ReportStatusRevertHandler` and `CatalogueRevertHandler` are `final`
classes implementing nothing; their nested `@Component` classes are the beans. The prose already
said "nine handler beans from six files" while the picture drew six — the picture now draws nine,
with composition arrows to the file each pair lives in, and a sentence explaining that
`Outer_Inner` is a nested type because Mermaid has no syntax for one.

**And the seam the diagram existed to show was the one thing it did not show.**
`LoginCodeDelivery` and `LoginSuccessDelivery` carried `<<interface>>` with no realization, and
their implementations were absent — so the prose claiming "that is the seam every test
substitutes" pointed at nothing, and named `MailService`, a class deleted months ago.
`LoginCodeMail` and `LoginNotificationMail` are drawn now.

**Selectivity is now stated**, which was the point of the exercise: 102 of 258 source files
appear, and a box being absent means "nothing here turns on it", never "this does not exist".
The legend gained `..>`, `<-->` and multiplicity, all of which the file already used and none of
which it explained. The package rule was self-contradictory — it said every box without a
stereotype belongs to the package, while `<<interface>>` and `<<enumeration>>` are stereotypes —
and its claim that five boxes are "where the package boundaries are actually crossed" was simply
false; `AuditWriter` alone is injected by a dozen services.

**Structural check after the edits: 108 boxes, all resolving to a type under `src/main` (102
files plus six nested classes), every declared member resolving, and — for the first time —
every arrow endpoint a declared class.** That last one had four violations before. Rendering is
still unverified: no renderer here, and exporting to images is item 43.

## Red seen before green

Only one thing failed in this pass and it is worth recording because it was caught by the build
rather than by review: the comment written into `pom.xml` contained a `--`, which is illegal
inside an XML comment. `Non-parseable POM ... in comment after two dashes (--) next character
must be >`, Maven exit **1**. Rephrased and re-run.

## The numbers

`./mvnw clean verify`, with `clean`, exit code captured as `$?` immediately: **Maven exit 0**,
**1048 tests, 0 failures, 0 errors**, 31 skipped, **LINE 96.68%** (3407/3524), **BRANCH 92.95%**
(1042/1121), both gates met. Identical to the previous pass, which is the expected result: **no
file under `src/main/java` was touched**, so there is no `CHANGELOG` entry either.

Documentation checks: **0 broken anchors** across every in-repo markdown link.

## One new finding, opened not fixed — F-49

`.gitlab-ci.yml:319` writes `SPRING_MAIL_HOST` with **no default**, alone among its neighbours.
`write_env` writes the key unconditionally, so an unset CI/CD variable produces an empty value in
`.deploy.env`, and Spring's `${SPRING_MAIL_HOST:localhost}` **only falls back when a variable is
absent, not when it is empty**. The result would be a deployment nobody can log into, with
nothing failing at startup to say so.

It is not a new hazard — `.gitlab-ci.yml:234-239` writes the rule out in full, and names the
incident that taught it: an empty CORS allow-list answering every browser call with `403`. Both
variables in that comment were given explicit defaults. The mail host has the same shape and was
missed. **Latent rather than live**, since production mail works, which makes it F-30's kind of
finding: a landmine under an unrelated change. Board item 40, ~15 minutes, not fixed here.

## What was deliberately not done

- **F-49 not fixed**, by the rule that a new defect gets a number rather than a patch — and it
  touches a deploy job that cannot be tested from here.
- **`docker-compose.prod.yml`, `.env.example` and `.gitlab-ci.yml` untouched.** The catcher is a
  local-stack decision and must not look like a deployment one.
- **Entities still not drawn** in the class diagrams. Comment, Answer and Student appear in no
  diagram, so showing their multiplicities would mean adding boxes — a redraw, decided against.
- **The stale `/admin/**` migration bullets were annotated, not deleted.** They record what was
  planned before the routing decision, and a struck-through plan with the reason attached is
  worth more than a gap.
- **No image export**, no `LICENSE` decision, no rename of `com.example:demo` — all three are on
  the board with what it would take.

---

# 10 September, sixth pass — the demo measurement, and what it found under a button

Not a test pass. This one started as a question about a single button before the Abgabe demo and
ended in two findings, one of which is about the whole suite. Written out step by step because
**not one step of this chain came from running a test.** The suite was green throughout, and is
still green: nothing under `src/main/java` was touched.

## The chain, in the order it happened

**1. Blocked at the first measurement, and said so rather than guessing.** The question was what
`gitlabEnabled` returns on the deployment, because that flag decides whether the "create issue"
button exists in the panel at all. `curl https://ratemyprofessor.dev/system/status` answered
**`401 {"message":"Not logged in","success":false}`** — the route needs an admin bearer token and
there is none on this machine. Checked what could be read without one: `/` answers `200`, so the
service is up, and `/actuator/health` is `404`, so there is no unauthenticated status surface.
**No value was reported.** The flag's live value stayed unknown until it was supplied from
outside, and the report said that instead of inferring it from `.gitlab-ci.yml`.

**2. The property that controls the flag, derived while blocked.** `gitLabClient.isEnabled()` is
computed, not stored: `RestClientGitLabClient:89-91` requires a non-null `restClient` **and** a
non-empty `projectId` **and** a non-empty `token`, and the client is only built when `baseUrl` is
non-empty (`:65-75`). So any one of `GITLAB_BASE_URL` / `GITLAB_PROJECT_ID` / `GITLAB_TOKEN`
turns the integration off, they are `@ConfigurationProperties` read at startup, and the CI/CD
variables reach the container through `write_env` at `.gitlab-ci.yml:330-333` — which is why
changing one needs a redeploy and not a restart.

**3. The correction that changed the question.** The flag was `true` and the button was live, and
a first click returned **`409`** while the issue **was** created in GitLab. So the success path
reports failure. From here the measurement is entirely code reading.

**4. The flow order, read line by line rather than summarised.** Two beans, two transaction
boundaries: `ModerationBugReportService.createIssue:273-292` and
`BugReportIssueTransactions.createOrReturnExisting:64-91`. The `isEnabled()` check comes **first**
and is the `503`; then the transaction opens, the row is locked `FOR UPDATE`, the already-created
guard is evaluated, **the HTTP POST to GitLab runs**, and only then are the three issue fields
written and the audit entry flushed. **The external call sits between the guard and the write** —
which is the shape the rest of the finding follows from.

**5. The single source of the `409`, found by grep rather than by assumption.** `grep CONFLICT`
over both files on the path returns **nothing**: neither throws it. One place in
`src/main/java` produces this body — `GlobalExceptionHandler:166-171`, keyed on
`DataIntegrityViolationException`. So the condition is **an exception type, not a business rule**,
and `grep "State conflict"` confirms the string the panel displays is the backend's own literal
and not a label the client invented.

**6. The status hypothesis, eliminated in both directions.** The first and most natural reading —
issue creation is gated on a status, or it moves the report to one whose transition is refused —
is wrong twice. `createOrReturnExisting` neither reads nor branches on `report.getStatus()` and
does not import `ReportStatus`; its only writes are the three issue fields. `BugReport` carries no
`@PreUpdate`, `@PrePersist` or `@EntityListeners`, so there is nowhere to hide a transition.
Transitions exist only behind `PATCH`, in `updateBugReport:113-127`. **Eliminating this is what
pointed at the exception type instead**, so it is recorded as a negative result rather than
dropped.

**7. Why `markFailed` does not run, which is what makes it worse on a retry.**
`DataIntegrityViolationException` is not an `ApiException`, and
`ModerationBugReportService:279` catches only `ApiException`. The marker is skipped, the rollback
leaves the row `NONE` with a null `issueUrl`, and the panel keeps offering the button — so each
click opens **another** issue. `BugReportIssueTransactions:22-27` was written to close exactly
this trap and its two-transaction split is correct; it is entered through a `catch` too narrow to
reach, which is worse than no comment at all because it reads as handled.

**8. The two baselines, diffed rather than eyeballed.** `spring.flyway.locations` is
`classpath:db/migration/{vendor}`, so the tests execute
`src/test/resources/db/migration/h2/V1__existing_schema_baseline.sql` and production points at
the PostgreSQL twin. Diffed line by line: they differ in **exactly one place**,
`audit_logs.changes` / `.metadata` being `jsonb` against `json` — a deliberate translation, not
drift. `bug_reports` is identical in both, and carries **no unique or check constraint** on
`issue_url`, `issue_iid` or `issue_state`. Every NOT NULL column the two writes touch was then
checked against its source: `audit_logs.action` is `varchar(50)` for a 24-character value, and
`actor_name` / `actor_email` come from `Student` columns that are themselves NOT NULL and UNIQUE.
**Nothing in either baseline explains the refusal.**

**9. The step that turned a puzzle into a finding.** `EndToEndJourneyTests:260-261` exercises
**this exact success path** — student files a report, admin opens an issue — and asserts `200`.
It runs against a real PostgreSQL 16 with the production migration directory **actually
executed**: `PostgresTestDatabase:64-66` sets `spring.flyway.locations=classpath:db/migration/postgresql`,
`baseline-on-migrate=false` and `ddl-auto=validate`. It passes in CI. **So the constraint that
fires in production is not in `V1`.** That is not a deduction about a URL length or a null column;
it is the difference between two schemas.

**10. Why the schemas differ, read out of the deployment document.** `deployment.md:61` and
`:358`: *the deployed database was baselined on 2026-09-05.* Flyway's baseline **records** a
version in `flyway_schema_history` and **does not execute it**, so `V1` has never run against the
deployment — the production schema is the residue of the pre-Flyway `ddl-auto=update` era,
recorded in no file. And `ddl-auto=validate` (`application.properties:24`) checks that tables and
columns *exist* with compatible types; it does **not** check length, nullability, defaults, unique
indexes or check constraints, so a divergent production column starts the application clean.
**That is the finding the button was only the visible end of.**

## Not one step came from a test run

Worth stating plainly, because the shape of this pass is the lesson in it. Step 1 was a `curl`
that returned `401`. Steps 2 and 4 through 7 were reading four files line by line. Step 3 came
from outside this machine. Step 8 was `diff` over two SQL files. Steps 9 and 10 were reading a
test's configuration and a deployment document — **the E2E test was used as evidence without
being run**, and what it proved is that it *cannot* catch this: it passes, correctly, against a
schema production does not have.

**The suite was green before this pass and is green after it**, and that is precisely the
finding. `./mvnw clean verify` was not re-run, because no file under `src/main` or `src/test` was
touched: the previous pass's figures — Maven exit 0, 1048 tests, LINE 96.68%, BRANCH 92.95% —
still stand and are not restated as if re-measured.

## Three findings opened, none fixed

| # | Finding | Board |
| --- | --- | --- |
| **F-50** | The GitLab issue success path answers `409 State conflict`; the issue stays open in the tracker and every retry opens another | item 44, ~15 min to name the constraint + ~3–4 h |
| **F-51** | Nothing verifies the production schema, and it is not the schema in this repository | item 45, ~1 h to measure, then unknown |
| **F-52** | A constraint violation and a business-rule refusal are the same `409 State conflict` to the client — **the third sighting** of this shape | item 46, ~2–3 h, after F-51 |

## Second half of the pass: the diagrams were exported, and the export was checked

Board item 43 had been deferred with the reason attached — the six `classDiagram` fences render
on GitLab and come out as **code** in a plain Markdown-to-PDF export — and the submission is a
PDF plus a repository link, so it came due.

**Rendered with `@mermaid-js/mermaid-cli` (`mmdc` 11.17.0)**, installed outside the tree, each
fence extracted and rendered at `-b white -s 2`. Six PNGs, 1568px wide, committed under
`docs/diagrams/` and embedded in `class-diagrams.md` **above** their fences — the fences stay,
because the image is what a PDF shows and the fence is what stays diffable.

**`<details>` was tried first and abandoned.** Wrapping each fence in a collapsible block keeps
the document readable on GitLab, and raw HTML is dropped or mishandled by Markdown-to-PDF
converters — which would have silently deleted the source from the PDF, the exact thing the
requirement was there to prevent. A plain bold label costs a little vertical space and cannot
fail that way.

**All six images were opened and compared to their fences, not just checked for existence.**
`<-->` at the end of diagram 5 — flagged by the previous pass as the one construct worth watching
in a real renderer — draws correctly, as a bidirectional association between `Lecture` and
`Professor` with `*` at both ends. No image came out as a Mermaid error box. The **108 boxes** the
diagrams have claimed since the audit were re-confirmed against the fences while extracting them:
116 `class` declarations, **108 unique**, the difference being the deliberate crossings.

**One diagram fails the format rather than the renderer.** Diagram 3 is 1568×194 — roughly
**8:1** — because the moderation package is six independent controller trees laying out side by
side. It is a correct picture and its boxes come out about **1.5 mm** tall at a normal text width.
Recorded as item 47 with three costed options rather than redrawn under a deadline. The other five
are between 1:0.6 and 1:1.6.

## Then the question behind the question: what else breaks in a PDF

Mermaid was the known problem, so the rest was measured rather than assumed — a script over all
36 in-repo markdown files, counting row length and column count outside fences and line length
inside them.

**The worst of it is table cells, not diagrams.** `TODO.md` has 36 rows over 200 characters and a
worst row of **3717**; `deployment.md` 29 rows, worst 1163; `test-findings.md` 22; `test-plan.md`
20; `adminweb-consumer-contract.md` 17 across five columns. For overflowing fences,
`frontend-consumer-contract.md` has **105 lines over 90 characters, the longest 251**. **This board
is the worst offender and this pass made it worse** — items 44 to 49 are all long rows.

**Links came back in better shape than expected, with one real break.** 257 cross-file links and
138 same-file anchors, and after one fix, **0 broken**: four `consumer-contract.md` links in
`adminweb-consumer-findings.md` and `frontend-consumer-findings.md` were left behind by a rename to
`adminweb-` / `frontend-consumer-contract.md`. **Fixed in this pass**, because the target was
unambiguous and a stale link costs nothing to repair. Worth noting against the previous pass's
"0 broken anchors": that check looked at *anchors*, and these were missing *files* — a narrower
sweep reporting a clean result, which is the shape this repository keeps finding.

**The one link class that needs a decision rather than a fix:** 22 line-anchored links into Java
sources in `adminweb-tasks.md` (`…SecurityConfig.java#L143-L147`), plus one each in `README.md`
and `Wiki.md`. GitLab resolves them; a PDF cannot. Item 49.

## What was deliberately not done

- **F-50 not fixed, and deliberately not guessed at.** The mechanism is established; the
  constraint that fires is not named, and it cannot be named from this repository. Naming it needs
  the production log line — `GlobalExceptionHandler:168` logs the stack trace and the
  `PSQLException` carries the constraint name — or `\d bug_reports` / `\d audit_logs`. Fixing
  before that would be a change written against a guess.
- **No characterization test for F-50.** Pinning it needs the constraint, for the same reason.
  The finding records what the test should assert when it can be written: that a persistence
  failure after the GitLab call leaves the row `FAILED` rather than `NONE`, and that the answer is
  not a `409`.
- **`BugReport.java:88-93` not corrected.** The comment still says *"Production runs
  `ddl-auto=update`"*; production runs `validate`. Its reasoning is a correct account of the era
  the column was added in and its conclusion still holds — only its tense is wrong. Recorded under
  F-51 so it lands with whoever reconciles the schema, rather than being tidied away from the one
  place in `src/main/java` that describes production schema management at all.
- **No `CHANGELOG` entry.** Nothing client-visible changed; this pass wrote documents.
- **`gitlabEnabled` never measured from here.** It was supplied. The `401` is in the record
  because a blocked measurement that is written down is worth more than an inferred value.
- **Diagram 3 not redrawn** and no diagram edited. Splitting it would change a fence, and with it
  the caption and the box count that were just re-verified — a redraw is not a thing to do the
  night before a submission. Item 47, three options costed.
- **No renderer added to the repository and no CI job for it.** `mmdc` was installed outside the
  tree, so the committed PNGs can go stale silently if a fence is edited. That is a real gap and
  it is item 48, not something to bolt on now: the version worth having re-renders and fails on a
  difference, which is a pipeline change.
- **No document reformatted for the PDF.** The wide tables and long fences were measured and left
  alone; changing 36 rows of this board to fit a page is not a documentation improvement. Item 49
  costs both the thorough option and the cheaper one — export only what an examiner reads.
- **`./mvnw clean verify` not re-run for this half either.** Only `docs/` changed, plus six new
  PNGs. The previous figures stand and are not restated as if re-measured.
