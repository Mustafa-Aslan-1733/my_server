# Test plan

Layers: **unit → API/role → contract → integration → E2E**, plus a concurrency class inside
the integration layer and two CI-only tools (secret scanning, mutation testing) that answer
questions no test in the suite can. Every batch adds its own section. The unit
layer is covered by batches 1–7, the API/role layer by the authorization, protocol and ownership
sweeps below, the integration layer by the PostgreSQL section, and E2E by six journeys over real
HTTP. **All four layers exist.** What was once recorded as blocking the integration layer — the
Testcontainers question — turned out not to be a blocker at all; see
[Settled questions](#settled-questions).

### Where it stands

**1048 tests, 0 failures**, 31 skipped locally (the `@Disabled` `DemoApplicationTests`, the
twenty-four PostgreSQL integration tests and the six end-to-end journeys, all of which run in
CI).
Bundle **96.68% LINE** (3407/3524), **92.95% BRANCH** (1042/1121) — read from
`target/site/jacoco/jacoco.csv` after `./mvnw clean verify` on **10 September**, Maven exit
code **0**, captured as `$?` immediately rather than from a following command.
Line coverage is gated in two tiers — **90% mandatory, 95% target** — and BRANCH has a single
floor, ratcheted to 0.88 now that batch 7 has cleared it.

**Both percentages moved slightly down from the 9 September figures** (96.70 / 93.16) while the
suite grew by 29 tests, and that is arithmetic rather than a regression: the denominators grew
by 38 lines and 24 branches — `AccountReactivator`, two audit writes and two enum constants —
and the new branches are covered less densely than the bundle average. Both gates pass with
room, and the CI line target of 95 is cleared by 1.68 points.

> **Do not quote these two numbers from here.** They are a snapshot; the artefact is
> `target/site/jacoco/jacoco.csv` after `./mvnw clean verify`, which is what CI reads and what
> `jacoco:check` gates on. Every figure in this document that is not read from that file on the
> day it is quoted is a figure that was true once — which is
> [P-5](test-findings.md#p-5--coverage-measured-without-clean-is-not-the-coverage-ci-measures)
> and [P-1](test-findings.md#p-1--the-coverage-baseline-number-was-wrong) both.
>
> **And read the build's exit code from the build, not from the line after it.** A previous
> pass reported this suite green on an `exit 0` that came from an `echo` at the end of the
> command rather than from Maven: the shell reports the status of the *last* command in a
> list, so `./mvnw verify; echo done` is always 0. Capture `$?` immediately, or let Maven be
> the last thing that runs. Every number on this page was re-measured on 9 September because
> that one was not checkable.

Both figures moved on the refactor rather than on new coverage work: the denominators fell as
duplicated code was collapsed, and the classes that came out of the split are small enough to
test directly. Fifty-one of the tests are new, and every one of them was written because
something the refactor touched turned out to have nothing pinning it — see
[The single-responsibility refactor](#the-single-responsibility-refactor).

> Those are `./mvnw clean verify` numbers, and the `clean` is not decoration. JaCoCo's
> `prepare-agent` appends by default, so `target/jacoco.exec` accumulates across runs and a
> `verify` following a targeted `-Dtest=` run reports coverage the suite did not earn on its
> own. The figures previously recorded here — 95.78% / 89.50% — were measured that way. See
> [P-5](test-findings.md#p-5--coverage-measured-without-clean-is-not-the-coverage-ci-measures).

Line coverage has been above its target since batch 5, so `coverage:line-target` is green
rather than yellow; batch 6 raised the target itself from 94 to 95 rather than banking the
slack.

**[test-findings.md](test-findings.md) has two open entries in the register of fifty, and
neither is a defect waiting on a commit:** F-29 needs credentials rotated at their providers,
and F-34 needs a product decision about a half-built feature.

**Four more are open outside that register**, numbered F-42 to F-48 and deliberately not counted
in it: F-42 (the panel does not read the `/all` routes — its work, after submission), F-46's
remaining half and [F-48](test-findings.md#f-48--an-unauthenticated-caller-can-undo-somebodys-account-deletion)
(an unauthenticated caller can revive a deleted account — four options costed, decision after
submission), and [F-47](test-findings.md#f-47--active--false-is-honoured-by-exactly-two-queries-so-a-deactivated-row-is-still-read-still-rated-and-still-shown)
(a product question about what `active = false` means). **None of the four is waiting on a
commit either**; three are waiting on a decision and one is in another repository. All of them
are pinned by tests here except the two that cannot be.

The batch history behind that: the twelve that were still open
after batch 6 were closed in one pass — eleven fixed, one (P-3) closed by a recorded decision.
Three of them changed a client-visible contract, so they are named here rather than only in the
findings document: **F-5** (app-tier failures answer 4xx instead of 200), **F-16** (a path
called with a verb it does not map answers 405 with an `Allow` header instead of 404), and
**F-3** (`VoteType.NONE` withdraws a vote). This is what the batches were for; closing them is
not a batch of its own and gets no section below, only updated statuses.

F-5 was finished on 8 September rather than that day: the fix had left `LectureService` and
`ProfessorService` alone, parked behind an unscheduled refactor, and the four create failures
there answered `200 {"success": false}` until they were changed to `409` and `404`. The four
tests pinning the old answers were inverted, not deleted. What made parking it the wrong call
was not the code — it was that `admin-api.md` had gone on documenting the old status in the
document the panel team treats as authoritative.

| Layer | State |
|---|---|
| Unit | Batches 1–7 done. |
| API / role | The three sweeps below. |
| Findings | **Two open**, neither a defect: F-29 (needs a rotation) and F-34 (needs a product decision). |
| Integration | **Done for the questions H2 cannot answer.** Six classes, 24 tests, one shared context against a real PostgreSQL in CI. |
| E2E | **Done.** Six journeys over real HTTP against the same PostgreSQL, in a second Maven invocation of the same job. |

The integration layer runs in `server:postgres-integration`, against a `postgres:16` GitLab CI
service with the production Flyway migrations. Its tests skip themselves when
`POSTGRES_SMOKE_JDBC_URL` is unset, so `./mvnw verify` on a developer machine is unchanged and
still needs no Docker. Its first result was ruling out the BUG-1 timestamp hypothesis — see
[Settled questions](#settled-questions); what it covers now is
[The integration layer](#the-integration-layer).

The spread between LINE and BRANCH was 15 points before batch 4, 9.7 after it, 8.9 after batch
5, and 6.2 after batch 6; it is **3.5** today. **75 branches and 115 lines** are left in the
whole module (9 September, `jacoco.csv`).

The four classes this paragraph used to name as the largest branch gaps —
`ModerationUserService` (27), `SocialService` (13), `ModerationContentService` (12) and
`AuditLogService` (11) — **are batch-6 figures and three of those classes no longer exist**;
the single-responsibility refactor split them, and the counts went with the pieces. What the
report shows now is a long flat tail rather than a few large holes: the biggest is
`UserListQuery` at 6 missed branches, then `AuditRevertService` at 5 and
`BugReportIssueTransactions` at 4, and nothing else is above 3. The line side is still
concentrated, and still where *Deliberately uncovered* says it is: `HtmlMailSender` (19),
`LoginNotificationMail` (15), `MailTemplateService` (12) and `LoginCodeMail` (8) are 54 of the
115. A growing share of the rest is not unit-testable at all — Criteria predicates and
specification lambdas. See the note at the end of the batch 6 section.

### The metric

The coverage metric is LINE coverage, target 90%. Batch 1's result:

| Class | LINE | BRANCH | Tests |
|---|---|---|---|
| `KeysetCursorCodec` | 100.0% | 100% | 23 |
| `AuditCursorCodec` | 100.0% | — | 5 |
| `RevertValues` | 100.0% | 97% | 37 |
| `AdminBootstrapPolicy` | 100.0% | 100% | 18 |
| `OtpService` | 100.0% | 100% | 13 |
| `RateLimitService` | 98.2% | 93% | 26 |
| `AuditRevertService` | 100.0% | 92% | 30 |
| `LoginLocationService` | 98.8% | 91% | 39 |

**191 tests in total (good 80 / bad 111).** Whole suite: 359 tests, 0 failures.
The counts are parameterized tests expanded into their individual cases.

Shared rules: plain JUnit + AssertJ + Mockito, no Spring context, no database/network/file
system, `Clock.fixed` everywhere a clock is injected. Tests live in mirror packages — this
is required because `RateLimitService.validateConfiguration()` and
`LoginLocationService.warnIfTokenMissing()` are package-private.

## Batch 2

Target: all of the `com.pse.audit.revert` handlers, the account lifecycle, and voting.

| Class | LINE | BRANCH | Tests |
|---|---|---|---|
| `CatalogueRevertHandler.LectureHandler` | 100.0% | 100% | 9 |
| `CatalogueRevertHandler.ProfessorHandler` | 100.0% | 100% | 6 |
| `ReportStatusRevertHandler.CommentReportHandler` | 100.0% | 100% | 6 |
| `ReportStatusRevertHandler.AnswerReportHandler` | 100.0% | 100% | 4 |
| `WarningRevertHandler` | 100.0% | 100% | 6 |
| `UserRevertHandler` | 100.0% | 100% | 9 |
| `SocialService` (vote methods only) | 91.8%* | 67%* | 9 |
| `StudentService` | 100.0% | 94% | 12 |
| `AccountService` | 100.0% | 100% | 6 |

\* The number for the whole class. The `voteComment` / `voteAnswer` methods this batch
targeted are covered **completely** (0 missed lines); the remaining 16 lines belong to the
`submit*` and report methods and were left to a later batch.

**67 tests in total (good 34 / bad 33).** Whole suite: 439 tests, 0 failures (2 skipped:
the `@Disabled` `DemoApplicationTests` and `PostgreSqlMigrationSmokeTests`, which runs on
CI only). The counts are parameterized tests expanded into their individual cases.

With this batch **all nine handler beans** in the `com.pse.audit.revert` package, plus
`AuditRevertService` and `RevertValues`, reached 100% LINE; no missed lines are left in
the package.

### Bundle and thresholds

Measured bundle: **91.41% LINE** (3054/3341), **75.51% BRANCH** (845/1119).
Before the batch the measurement was 87.76% LINE / 72.83% BRANCH.

`jacoco:check` was pulled up to two limits: LINE `0.85` → **`0.90`**, plus a new **BRANCH
`0.73`** floor. Both sit about one and a half points below what is measured — the rule is
to leave the measurement some headroom, not to pin it to a round target. The reason:
`check` is bound to the `verify` phase rather than `test`, so a threshold with no headroom
breaks on CI, in somebody else's MR, on a change that has nothing to do with tests — which
is exactly the failure mode the comment in the pom says it was chosen to avoid. BRANCH is
a floor, not a target: a LINE-only gate let a method with many branches arrive with a
single happy-path test while branch coverage quietly dropped.

The threshold really is a gate, and that was verified: with LINE temporarily raised to
`0.95`, `./mvnw verify` went red with "lines covered ratio is 0.91, but expected minimum
is 0.95", and green at `0.90`.

**Deliberately not done** — all three are shortcuts that raise the number without writing
a test, which redefines the ratchet instead of clearing it:

- The `<excludes>` list was not widened. Adding `**/model/**` or `**/dto/**` would have
  bought several points without a single test.
- No `lombok.config` was added to filter Lombok out. The repo has no `lombok.config` and
  `addLombokGeneratedAnnotation` defaults to `false`, so every generated getter/setter
  counts towards the bundle. Because accessors are heavily covered through serialization,
  filtering them could delete more *covered* lines than uncovered ones and lower the
  ratio — and it would quietly change what the number measures.
- No reflection tests were written for the private constructors of static-only classes;
  JaCoCo has filtered those since 0.8.0.

`ModerationCatalogService.updateLecture` / `updateProfessor` (31 missed lines, 57/106
branches) was this batch's conditional fourth group: it was not needed because the bundle
passed 91%. It is the first candidate for the next batch, being the most branch-heavy
untested method left.

### Three different coverage numbers

To keep them apart: these numbers measure different things; this is not an inconsistency.

- **The mandatory line floor, 90%.** Enforced twice on purpose, and the two have to be
  moved together: `jacoco:check` in the pom (`LINE 0.90`) catches it on a local
  `mvnw verify`, and `COVERAGE_LINE_MIN` in `.gitlab-ci.yml` catches it in the pipeline.
  Below it the build is red.
- **The line target, 95%** (`COVERAGE_LINE_TARGET`). Missing it is a **warning, not a
  failure**: `scripts/CheckLineCoverage.java` prints it inside `server:test`, and the
  `coverage:line-target` job — `allow_failure: true` — turns the same condition into a
  visible yellow mark on the pipeline while letting it pass. It is where the suite is
  trying to be, not a gate.
- **The BRANCH floor, `0.88`,** in the pom only. A floor, not a target: without it a
  heavily branched method can arrive with one happy-path test and pull branch coverage down
  without failing anything. Batch 4 was chosen by this counter rather than by LINE, because
  every class it touched was already above 82% LINE and a line target would never have
  picked them.
- **`scripts/CheckChangedCoverage.java --threshold 30`** — the MR gate, which looks only
  at the coverage of *changed* code. Unrelated to the bundle: a change can be fully covered
  and still drop the bundle, and the bundle can sit above its floor while a new file arrives
  untested.

---

## Batch 3 — the last two revert handlers

Target: the two handlers in `com.pse.audit.revert` that batches 1 and 2 did not reach, which
closes the package.

| Class | LINE | BRANCH | Tests |
|---|---|---|---|
| `BugReportRevertHandler` | 100.0% | 100% | 6 |
| `ContentRevertHandler.CommentHandler` | 100.0% | 100% | 5 |
| `ContentRevertHandler.AnswerHandler` | 100.0% | 100% | 4 |

**15 tests in total.** There are **nine** handler beans in the package; batch 2 covered six of
them and these are the last three. Every one of the nine, plus `AuditRevertService` and
`RevertValues`, now has **0 missed lines**. Branches are not quite there: `AuditRevertService`
still has 5 missed and `RevertValues` 1.

What these tests added is not LINE coverage — the three handlers were already reachable through
`AdminApiIntegrationTests`, which is [P-2](test-findings.md#p-2--integration-tests-hide-unit-targets)
exactly: an integration test can hold a unit target at 100% while nothing states what the unit
is supposed to do. What they added is the statement of intent, at the level where a wrong answer
is cheap to see.

These arrived on the `bugreports` branch (`05a9eac`) rather than as a planned batch, which is
why they were in `src` without being in this document until now. They follow the batch 2 shape
exactly: a manual constructor factory rather than `@InjectMocks`, mocked repository plus mocked
moderation service, and the same four questions per handler — the target type, the field map,
a deleted row being **absent** rather than mapped to null, and `applyInverse` replaying through
the service the panel itself calls.

## Batch 4 — the moderation services

Target: the branch gap. Three classes held 128 of the module's missed branches between them,
and unlike the earlier batches this one was chosen by the BRANCH counter rather than by LINE —
all three were already above 82% LINE and would never have been picked by a line-coverage
target.

| Class | LINE missed | BRANCH missed | Tests |
|---|---|---|---|
| `ModerationCatalogService` | 30 → **0** | 48 → **8** | 29 |
| `ModerationUserService` | 26 → **13** | 51 → **27** | 49 |
| `ModerationContentService` | 23 → **17** | 29 → **12** | 29 |

**107 tests in total.** Bundle: 92.56% → **94.02% LINE**, 77.09% → **84.20% BRANCH**. Seven
points of branch coverage from three classes, which is what "the gap is concentrated" meant.

### What the tests are organised around

All three classes have the same shape — validate, walk the request field by field, record a
change only where the value actually differs, and write nothing at all when nothing did. That
last property gets the most tests, and deliberately: a method that saves and writes an audit
entry for an edit that changed nothing fills the moderation trail with events nobody
performed. There are now tests for a resubmitted name, year, season, type, active flag,
professor assignment, username, biography, credibility score, content and status.

The second theme is the **protection matrix** in `updateStudent`. Three guards overlap on
every field — is the target an administrator, is the target the caller, is the target the
elevated operator — and the answers differ per field: a status change refuses self, a role
change refuses self, an address change refuses neither but refuses an elevated target
outright, for everybody including an elevated caller. Driving that through `MockMvc` means
minting a different session per case; at the unit level it is a boolean, which is the whole
argument for testing this class here rather than through the API.

### BUG-3's write side, closed

The deterministic write-side test that batch 2 deferred to this batch now exists.
`updateLecture` builds `before` from a `Set` — which defines no order — and `after` from
whatever order `findAllById` returns, then compares them position by position, so an
unchanged assignment records a bogus change and a bogus audit event. The test states that with
a `LinkedHashSet` and a stubbed repository order rather than relying on real `HashSet`
iteration: `Professor` overrides neither `equals` nor `hashCode`, so real order varies per run
and the flakiness would end up in the test instead of in the defect.

The finding stayed open through batch 6 as a product decision about `RevertValues`'s contract,
and is **fixed** now: both sides compare as multisets. The two deterministic tests were
inverted rather than deleted, which is what that way of writing them bought — neither depends
on real `HashSet` order, so they state the fixed behaviour exactly as reliably as they stated
the broken one.

### The gates moved

`jacoco:check` went BRANCH `0.73` → **`0.82`**, following the rule the batch 2 writeup set:
about one and a half points below what is measured, so the gate stops a regression without
breaking an unrelated MR.

LINE was raised to `0.92` in this batch and then **settled at `0.90`** when the two-tier CI
check landed: 90% became the mandatory floor and 94% the advisory target, so the number that
used to do both jobs was split into the two it was actually doing. See *Three different
coverage numbers* above. Verified green at both values.


## Batch 5 — the GitLab adapter, and a duplicate deleted

Target: the one entry in *Deliberately uncovered* that had to be written down as **debt**.
`RestClientGitLabClient` was at 23.1% LINE and 4 of 22 branches — the largest single branch gap
left outside the moderation services, and the only untested adapter whose absence had no
principled reason behind it.

| Class | LINE missed | BRANCH missed | Tests |
|---|---|---|---|
| `RestClientGitLabClient` | 30 → **0** | 18 → **0** | 25 |

Bundle: 93.97% → **94.86% LINE**, 84.27% → **86.01% BRANCH**. The line target (94%) is cleared
for the first time, so `coverage:line-target` stops warning.

### How the HTTP conversation is driven

Not by mocking the fluent `RestClient` chain, which is what `LoginLocationServiceTests` does.
That chain is three calls deep; this one is six (`post().uri().header().contentType().body()
.retrieve().body()`), and a mock of it pins the *shape of the call* rather than its result —
every future edit to the request would break the test whether or not the behaviour changed.

`MockRestServiceServer.bindTo(RestClient.Builder)` is used instead: real status codes, real
error types, no network. The failure arms close by themselves because the client library throws
what it really throws, and the assertions are about the request that went out and the answer
that came back rather than about which builder methods were called.

**This required a seam.** The class assembled its own `RestClient` in the constructor, so
nothing could be injected. A second, package-private constructor was added — the public
`@Value` one delegates to it — and the test lives in the mirror package to reach it. The
production wiring is unchanged; `RestClientConfig`'s javadoc already records why a
`RestClient.Builder` cannot simply be injected here (`spring-boot-restclient` is not on the
classpath, so Boot auto-configures no builder).

### What it found

Two defects, both fixed, both written up in
[test-findings.md](test-findings.md#f-18--the-project-id-reaches-gitlab-encoded-twice):

- **F-18** — the project id was encoded by hand *and* by the URI template, so a path-shaped id
  (`group/project`) reached GitLab as `group%252Fproject` and every issue creation answered 502.
  Found by writing down the request that ought to go out, not by chasing a report.
- The seam itself broke the container: two constructors with neither marked meant Spring looked
  for a default constructor and no `@SpringBootTest` could start. The unit tests stayed green
  the whole time — the API sweeps and the context tests are what caught it. `@Autowired` on the
  public constructor closes it.

### The token, which is the point of the class

Three of the 25 tests exist for one property: the credential never leaves the backend. The
header carries it; the log line carries the exception **class name** and nothing else; the
message the administrator receives says "Could not reach GitLab" whatever went wrong. The
failure test hands the client an exception whose own message contains the token, then asserts
that neither the log nor the `ApiException` repeats it. A future edit turning `{}` into
`exception.getMessage()` — an entirely reasonable-looking change — now fails a test instead of
writing a credential into the logs.

The cost is pinned too: a revoked token and an unreachable tracker are **indistinguishable** to
the caller, both 502. That is characterization, not endorsement.

### Also in this batch

The two duplicate test classes in `com.pse.user` were deleted (P-4 in the findings). The bundle
did not move by a single line, which is the proof that they were duplication rather than
coverage.

### The gates moved

`jacoco:check` went BRANCH `0.82` → **`0.84`**, following the same rule as batch 2 and batch 4:
about one and a half points below what is measured. LINE stays at `0.90` — it is the mandatory
floor of the two-tier arrangement, and the 94% target lives in CI, not in the pom.

Verified the same way as before: with BRANCH temporarily at `0.90`, `./mvnw verify` went red
with "branches covered ratio is 0.86, but expected minimum is 0.90", and green at `0.84`.


## Batch 6 — the security path and the bug-report path

The first batch chosen by **neither** counter. Measured at method level, the worst single
uncovered method left was 7 branches: the concentration batch 4 exploited is gone, and "take
the biggest number" would have led straight to `ModerationUserService`'s Criteria predicates —
which this document already assigns to the integration layer.

So the four targets were picked by what a missed branch there would actually cost. Three of
them had **no direct test at all**: `TokenAuthenticationService` appeared in the suite only as
a mock inside `AuthServiceTests`, and `ModerationBugReportService` was driven exclusively
through `AdminApiIntegrationTests`.

| Class | LINE missed | BRANCH missed | Tests |
|---|---|---|---|
| `TokenAuthenticationService` | 6 → **0** | 6 → **0** | 24 |
| `AccessRefusalAuditor` | 3 → **0** | 2 → **0** | 5 |
| `ModerationBugReportService` | 3 → **0** | 15 → **0** | 32 |
| `AuthService` | 8 → **0** | 14 → **0** | 16 (added to 13 existing) |

Bundle: 94.86% → **95.45% LINE**, 86.01% → **89.23% BRANCH**. 79 tests, and all four classes
are now at zero on both counters.

### Why the token gate was worth 24 tests

Every uncovered branch in `TokenAuthenticationService.authenticate` was a **refusal**. Nothing
in the suite said that a token should be turned down — only that a good one gets through.

That is the wrong way round for this class. A missed refusal here is not a wrong status code;
it is a session that keeps working after the account behind it was blocked or deleted, for as
long as the token lives, and a student token lives for a year. The class enforces the status on
every request precisely so that a path which changes it without revoking sessions cannot leave
one alive, and that intent is written in a comment. It has a test now.

The other properties pinned there: the repository is queried with the **SHA-256 hash** and
never with the plaintext; an `APP` session never even asks whether the account is an
administrator, so the tier is a property of the session rather than of the person; a legacy
plaintext token is upgraded in place and the plaintext cleared; and a refused request does not
count as activity. The "last seen" write is tested on the boundary of its five-minute window
with a fixed clock, in both directions.

One characterization worth flagging: a token with **no session type** — the shape the older
test helper mints, and the reason `AdminApiPathSplitTests` exists — is treated as neither `APP`
nor `ADMIN`. It picks up the admin row if there is one, and a missing one is not a refusal.

### The bug-report path, and P-2 again

`ModerationBugReportService` was at 3 missed lines and 15 missed branches — the clearest live
example of [P-2](test-findings.md#p-2--integration-tests-hide-unit-targets) left in the module.
`sendBugReport` refuses through a single six-armed condition, so one valid submission in an
integration test covers the whole disjunction with one arm while five different ways of
silently dropping a student's report stay untested. Each arm is its own case now.

`updateBugReport` got the treatment batch 4 gave the other three moderation services: every
field resubmitted unchanged must record nothing, save nothing and write no audit entry. Plus
both text bounds at and past the limit, and the `createIssue` arm that decides a missing report
is **not** a failed attempt and must not be marked as one.

### What it did not find

**No defect.** Every behaviour matched what the code's own comments said it should do, which is
worth recording rather than leaving implicit: after F-18 in batch 5, the reasonable expectation
was another one here. Two observations were pinned as characterization instead — the untyped
session above, and `AuthService.validate`, whose `student == null` arm is reachable only
through a well-formed header carrying a token nobody holds, because a malformed header throws
out of `verifyUser` before that line.

### The gates moved

`jacoco:check` went BRANCH `0.84` → **`0.87`**, and `COVERAGE_LINE_TARGET` in CI went
`94` → **`95`**. The pom's LINE floor stays at `0.90`: it is the mandatory tier, and the point
of splitting the two was that only one of them has to be conservative. Verified as before —
with BRANCH temporarily at `0.92`, `verify` went red with "branches covered ratio is 0.89, but
expected minimum is 0.92", and green at `0.87`.

### Where the unit layer stops

**As batch 6 left it:** 124 branches and 154 lines were left in the module, and the composition
had changed: 46 lines were `MailService` and 12 `MailTemplateService`, both decisions recorded
under *Deliberately uncovered*. `MailService` has since been split into `HtmlMailSender`,
`LoginCodeMail` and `LoginNotificationMail` — the name in this paragraph is the one the class
had at the time, kept for the same reason the findings keep theirs, and the current figures are
in *Where it stands*. A large share of the rest is Criteria predicates and specification
lambdas, which build a query rather than compute an answer. Chasing those with mocks would
assert the shape of a query nobody runs.

In other words the coverage number is now a poor proxy for what is untested. The places in
this document that defer something to the integration layer are the real remaining gap, and
they are the integration layer's backlog. There were 16 of them when this was written; F-9
is no longer one, because it was closed by removing the write rather than by testing it. They
are no longer blocked either — see [Settled questions](#settled-questions).


## Batch 7 — the three services covered only through the API

`docs/TODO.md` carried the same line for weeks: "services with real branching
(`ModerationUserService`, `AuditLogService`) are covered only through the API." That is not the
same complaint as "not covered" — the bundle counted those lines, because ten `@SpringBootTest`
classes drive them end to end. It is a complaint about *where* the covering test lives: a
rejection reached through MockMvc costs a Spring context and, when it fails, names a status code
rather than the argument that was at fault.

### `AuditLogService` — a service with no unit test class at all

Before this batch it had none. Almost everything `getLogs` does before it reaches the database is
parse and reject nine query parameters, and each rejection is a status the panel depends on: an
unparseable limit, a limit outside 1–100, a malformed actor id, an unknown actor type, target
type or action, a date that will not parse, a range that ends before it starts, a search longer
than the column. **21 tests**, and the interesting one is the scope rejection: an action from the
*other* log page is refused rather than quietly matching nothing, because an empty page reads as
"no such events" instead of "wrong endpoint".

### `ModerationUserService` — the warning lifecycle

The existing 50 tests covered `getStudents` and `updateStudent` thoroughly and **the entire
warning lifecycle not at all** — `warnStudent`, `updateWarning`, `deleteWarning` and
`getWarningsForStudent` had no unit test between them. That is the moderation action a student
actually experiences: `warnStudent` is the only place in the service that writes a
`Notification`. **11 tests**, covering the blank and over-long message rejections, that the text
is stored trimmed the way it was validated, that the account status is deliberately left alone,
that a reworded warning does *not* notify the student a second time, that a warning belonging to
another student reads as 404 rather than 403, and that a warning whose issuing admin row is gone
still lists.

### `SocialService` — the response builders

`createCommentResponse` and `createAnswerResponse` decide `userVote`, the field the app colours
the vote arrows from. It has three states that look alike from outside — nobody signed in, the
reader has not voted, the reader has — and only the third produces a value. **7 tests**, plus the
one that matters most: a visible comment carrying a hidden answer must not leak the answer.

### What this batch deliberately did not do

The remaining branches in these three classes are mostly inside `Specification` lambdas —
`rolePredicate`, `cursorPredicate`, the filter assembly in `getLogs`. The only way to run those
without a database is to hand them a mocked `Root`, `CriteriaQuery` and `CriteriaBuilder` and
then assert which builder methods were called, which asserts the implementation rather than the
behaviour and would go red on any rewrite that produced the same query. They are covered where
they mean something: `CursorPaginationPostgresTests` pages all four cursor endpoints against a
real PostgreSQL. This is the position this document already took on Criteria predicates, and
batch 7 keeps it.

### The gate moved

BRANCH went 89.15% → **90.61%**, and the pom floor 0.87 → **0.88**. The floor is set from the
measurement rather than from the hope: two and a half points of headroom, which is the shape the
0.87 step chose.

## The API / role batch

A different layer with a different metric. Coverage is not the measure here: these tests drive
the whole application through `MockMvc` against a real Spring context, and what they assert is
the **shape of the API surface**, not the lines of one class.

Three sweeps, all built the same way — the route list is read from Spring's own
`RequestMappingHandlerMapping` rather than kept by hand. That choice is the point of the batch.
A list maintained by a person goes stale silently and the sweep then passes because it is
looking at less than it thinks; a list read from the mapping cannot, and a new endpoint has to
be classified by whoever adds it.

| Test class | Question it asks | Tests |
|---|---|---|
| `ApiAuthorizationMatrixTests` | May an **anonymous** caller in? | 6 |
| `ApiProtocolContractTests` | Does the API answer with the **right status code**? | 5 |
| `ApiOwnershipMatrixTests` | May an **authenticated** caller touch somebody else's row? | 7 |

### The authorization matrix

The security chain ends in `anyRequest().permitAll()`, so an endpoint is anonymous unless some
matcher names it. Reading `SecurityConfig` alone therefore cannot distinguish "deliberately
public" from "nobody thought about it", and a new controller method inherits the open default
in silence. `everyRouteIsEitherDeclaredPublicOrRefusesAnonymousCallers` closes that: every one
of the 122 mapped routes must either refuse an anonymous caller with 401 or appear in
`PUBLIC_ROUTES`, a list of 14 entries that someone had to write deliberately.

The reverse direction is tested too — a route declared public has to stay reachable — so the
list cannot be used to wave a route through after it has been accidentally locked down.

Three findings came out of it, all recorded in [test-findings.md](test-findings.md): the
missing slash in the `/vote/answer` route (F-4, fixed), a missing bearer token answering 500
rather than 401, and `POST /data/professor` answering `200 {"success": false}` to a non-admin
instead of a real 403.

### The protocol sweep

The same idea applied to status codes rather than to authorization. It found F-14 through
F-17, and **all four are fixed** — three of them after the batch, in the pass that closed the
findings document:

- **F-16** — a path that exists, called with a verb it does not map, answered **404 instead of
  405**, on every mapped route — 122 of them when the finding was written, a count that has
  since drifted and is not re-derived here (`docs/TODO.md` item 28). It was pinned rather than
  fixed here, as a contract change with two
  documented dependents. Both turned out to be documentation *of* the defect rather than uses
  of it, so it was fixed: 405 with an `Allow` header naming the verbs that do map.
- **F-17** — a body in a media type no converter can read answered **500 instead of 415** on
  all 37 body-reading routes. **Fixed** with one narrow `@ExceptionHandler`; the writeup in the
  findings document records why extending `ResponseEntityExceptionHandler` was the wrong fix
  and what the red run proved before the pin was flipped.
- **F-14, F-15** — a missing required query parameter and the category list of an unknown
  lecture both answered 500. **Fixed**: 400 and 404.

The two statuses live in `com.pse.support.ApiContract` rather than in the sweep, because
`AdminApiPathSplitTests` asserts one of them incidentally. Both hold the right answer now, and
the constant earned itself: flipping `WRONG_VERB_STATUS` from 404 to 405 carried 93 refusals
across two suites with no other edit, which is what the batch claimed it would do.

`PUBLIC_ROUTES_ANSWERING_SERVER_ERROR` — the two-entry exemption list F-14 and F-15 lived on —
is empty and gone with them. A public route answering 5xx is a plain failure again.

### The ownership sweep

The half the authorization matrix does not cover: a valid token is not permission to touch a
row belonging to somebody else. All 61 id-taking routes are classified into four sets —
owner-scoped (1), cross-user by design (2), admin tier (52), and ids that name no user's row
(6) — and the ratchet requires every id-taking route to be in exactly one of them.

**It found no IDOR.** The negative result and the evidence behind it are written up under
[Negative results](test-findings.md#negative-results); the short version is that the app tier
takes the caller's identity only from `@AuthenticationPrincipal`, which
`noAppTierHandlerTakesACallerIdentityFromTheRequest` now pins by reflection over every app-tier
handler and its `@RequestBody` type. That invariant, not the individual probes, is what keeps
the owner-scoped column one line long.

### The app-facing routes, which the sweeps do not answer for

The three sweeps ask questions *about* a route — who may call it, what status it answers,
whose rows it touches. None of them asks what it returns. That distinction went unnoticed on
`GET /social/sync/comments`, the route the Android client syncs from: it appeared in
`ApiAuthorizationMatrixTests` (deliberately anonymous), in `OpenApiResponseValidationTests`
(body matches the schema) and in `LazyLoadingRegressionTests` (does not throw on a detached
collection) — three classes touching it, and **no test of what it answers**. Three green
mentions read, at a glance, like coverage.

Six tests were added to `SocialApiIntegrationTests`, taking it from 10 to 16:

| Test | What it pins |
|---|---|
| `syncReturnsAnEmptyListRatherThanNullWhenThereAreNoComments` | `comments: []`, not `null` — the shape of defect F-9 was (`ratings: null` for an unrated lecture), on the route with the most callers |
| `syncNestsTheAnswersInsideTheirComment` | the handler's own comment says it returns "both Comments *AND* answers"; the answers are **nested**, which is the part a client has to know. Also that `userVote` is absent, because the route passes `student = null` |
| `syncCrossesLectures` | what separates it from `GET /social/comments/{lecture_id}` — the whole catalogue in one answer |
| `syncLeavesOutAHiddenComment` | the `findAllByStatus(VISIBLE)` filter, stated so that a later rewrite that pages or joins the query has to keep it |
| `syncLeavesOutAHiddenAnswerUnderAVisibleComment` | the half a SQL filter cannot cover: visible comment, hidden answer, skipped in Java by `getAnswerResponses` |
| `anAnswerVoteCanBeCastAndWithdrawn` | the answer-vote route had only its bad-request case, so it was known to *reject* a bad vote and not to *accept* a good one — a separate implementation from comment voting, with its own repository, entity and audit action |

Both hidden-content tests were run against a deliberately broken filter first — `findAll()`
in place of `findAllByStatus(VISIBLE)`, and `if (false)` in place of the answer's status
check — and both failed, which is the only thing that distinguishes them from two tests that
would pass on an API leaking moderated content.

What is left on this side is breadth rather than absence. `/account` has five tests and they
cover the properties that matter (logout invalidates *every* session, delete is a soft delete
that invalidates the session); `/ratings` has eight. Neither is a hole of the kind
`sync/comments` was.

## The contract layer

A fourth question, after "does the code work", "who may call it" and "what status does it
answer": **does the API describe itself correctly?** The schema is generated by springdoc
from the controllers, so it cannot drift the way a hand-written one does — but it can be
incomplete, or wrong about a field, in ways nobody notices until a generated client breaks.

| Test class | Question it asks | Tests |
|---|---|---|
| `OpenApiContractTests` | Does the schema cover exactly the routes the application maps? | 3 |
| `OpenApiResponseValidationTests` | Do the response bodies match what the schema says about them? | 1 (a sweep) |

Both read the truth from the running application rather than from a checked-in file: the
schema is fetched over `/v3/api-docs` in the test itself, so there is no generation step to
forget and no copy to go stale. Route coverage is checked in **both** directions — a missing
entry shortchanges a generated client, an extra one sends it at a 404 it was told to expect
a body from — and both came back clean.

### What adding it turned up

**The schema was published anonymously.** springdoc registers `/v3/api-docs` in its own
package, and the authorization sweep filtered handlers to `com.pse` — so a 65 KB description
of every route and request body, the administrative surface included, was served to anyone,
and no test could see it. It is admin-tier now, and the sweep no longer has the blind spot:
`everyRouteADependencyRegisteredIsEitherDeclaredPublicOrRefusesAnonymousCallers` requires a
route from outside the project to refuse anonymous callers or be named in
`PUBLIC_THIRD_PARTY_ROUTES`. That test immediately found two more: `/v3/api-docs.yaml`, which
the first matcher did not cover because a matcher for one path is not a matcher for another
that looks similar, and `/error`, which is declared — the container dispatches to it to render
an error, so requiring a token there would mean an anonymous failure could not be told what
went wrong.

**Six response fields the schema was wrong about.** Every paging cursor plus
`database.error`, `counts` and `lastWrite` are `null` by design and documented as such in
their own javadocs, and the schema declared all of them non-nullable. A client generated from
it would have typed them as required and broken on the ordinary case. Four are fixed with
`@Schema(nullable = true)`; the two that are `$ref` properties cannot be — springdoc emits
`{"type":"null","$ref":…}` for those, which claims the value can only ever be null, so
neither form is right. The one the sweep actually observes, `lastWrite`, is named in
`KNOWN_SCHEMA_GAPS` rather than hidden by skipping the endpoint. `counts` has the same defect
and is deliberately **not** listed: this fixture keeps the database up, so `counts` is an
object and matches, and listing it would claim an observation the sweep does not make.

### A note on how these two classes are written

Both were rewritten once, and the reason is worth more than the diff. The first version
recovered the failing field by letting the MockMvc matcher throw and running a regular
expression over the exception's rendered message; and it walked the schema document by
casting nested `Map<String, Object>`. Both were rebuilding, less safely, something already on
the classpath: the validator hands the same information over as a typed `ValidationReport`,
and swagger-parser — which arrives transitively under that same validator — parses the
document into `OpenAPI`/`PathItem` and was sitting unused. Parsing a library's *error text*
is the loudest available signal that its real API went unread.

The rewrite paid for itself immediately: with the typed report the sweep became precise
enough to show that `KNOWN_SCHEMA_GAPS` was over-declared, naming a mismatch that no longer
occurred. An exemption list that names more than it observes passes for the same reason a
hand-maintained route list does — by looking at less than it thinks.

## The integration layer

Six classes, twenty-four tests, against a real PostgreSQL 16 with the production Flyway migrations.
They exist for the questions H2 cannot answer, and for nothing else: an assertion that H2 can
settle belongs in a faster job.

**One context, and it is load-bearing.** `PostgresTestDatabase` drops and recreates the
`public` schema when it initialises, so two Spring contexts using it would wipe each other
mid-run. Every class therefore carries `@PostgresIntegrationTest` — a composed annotation
holding the four annotations that used to be copied by hand — and Spring's context cache hands
them one context between them. That is also what keeps the job cheap: twenty tests cost about
seven seconds on top of one application start, not five starts. The `server:postgres-integration`
job greps its own log afterwards and **fails unless exactly one context started**, because a
fork is otherwise invisible: it looks like unrelated flakiness in whichever class ran second.

### What each class is for

| Class | The question only a real server answers |
|---|---|
| `PostgreSqlMigrationSmokeTests` | Do the production migrations produce a schema the application starts against? |
| `AuditTimestampPrecisionPostgresTests` | What does `timestamp(6)` do with the nanoseconds `Instant.now()` carries — truncate or round? |
| `CursorPaginationPostgresTests` | Do the four keyset predicates page correctly against that column? |
| `AuditJsonbRoundTripPostgresTests` | Does `jsonb` — the one place the two schemas differ — hand back what the revert feature put in it? |
| `ReadOnlyTransactionSweepPostgresTests` | Does any readable route write inside a transaction the server has marked read-only? |

### The cursor, applied

`KeysetCursorCodec` was tested to 100% at the unit layer, and its entry under
[Classes](#keysetcursorcodec) recorded what that could not reach: "producing a cursor correctly
and *applying* it correctly are separate things; the predicate and the page boundary belong to
the integration layer." Four endpoints paginate by keyset, over two different column types:

| Route | Cursor column | Shapes |
|---|---|---|
| `GET /admin/activity-logs` | `Instant` | one |
| `GET /admin/audit-logs` | `Instant` | one |
| `GET /admin/ratings` | `LocalDateTime` via `Cursor.createdAtLocal()` | one |
| `GET /admin/users` | `LocalDateTime` | **two** — `sort=newest` and `sort=oldest` mirror every comparison |

Each is paged from end to end with `limit=1`, and the sequence of ids served is compared to the
ordering the database itself produces, **element for element**. That is deliberate, and it is
the part worth copying: "no row is served twice" is also satisfied by a walk that silently
drops rows, and a dropped row is the same defect wearing the other face. One assertion covers a
repeat, a skip and a wrong boundary.

Every route is tested twice: once with distinct timestamps, and once with **every row forced
into the same microsecond**. The second is the point. The tiebreaker branch — `createdAt =
cursor AND id < cursor.id` — is unreachable while timestamps differ, and it is the branch that
decides whether a page boundary repeats or skips. It is also the exact shape BUG-1 describes.
`@CreationTimestamp` overwrites whatever an entity carries, so students and ratings are stamped
afterwards through `JdbcTemplate`; `AuditLog.@PrePersist` only fills a null, so those rows are
stamped before the insert.

**The sweep was checked against a defect it was meant to find**, rather than trusted because it
was green: changing `lessThan` to `lessThanOrEqualTo` in `AuditLogService`'s cursor predicate —
the classic off-by-one that serves the boundary row again — turned exactly the four audit tests
red, with `the cursor is not advancing past a row`. Without that check a green pagination sweep
says very little, because the same green comes from a sweep that never paged.

Result: **BUG-1 does not reproduce on any of the four endpoints**, in either direction, with or
without a shared microsecond. Recorded as a negative result in
[test-findings.md](test-findings.md#the-cursor-endpoints-page-correctly-against-postgresql).

### The `jsonb` round trip

The H2 baseline and the PostgreSQL baseline are the same file except for two lines:
`audit_logs.changes` and `audit_logs.metadata` are `json` on H2 and `jsonb` on PostgreSQL.
`json` stores the document as text and returns it byte for byte; `jsonb` parses it into a binary
form that reorders keys, collapses duplicates and normalises numbers. Every revert handler reads
its `before` values back out of `changes`, so the whole revert feature rests on that round trip
— and the H2 suite, on the other column type, cannot vouch for it.

Three tests. The first asserts the column really is `jsonb` in this database, because without it
the other two would still pass while quietly testing the H2 shape. The second round-trips nested
objects, arrays, nulls, an empty object, non-ASCII text and a quote inside a key — and then
re-reads two of the values through PostgreSQL's own `->>` operator, so the assertion cannot be
satisfied by a value that never left the JVM. The third reverts a lecture rename end to end and
asserts the *name is restored*, not merely that the JSON survived.

Nothing was lost in the round trip, including the integer and boolean values `RevertValues`
already treats as representation-insensitive.

### The read-only sweep

F-9 was a write inside a `@Transactional(readOnly = true)` method, and the findings record why a
unit test could not settle it: dirty checking is Hibernate's business, not a mock's. It was
fixed by removing the write, which left the general question open — there are 27 such methods
and one of them had ever been looked at.

PostgreSQL is what makes it answerable: it marks the transaction read-only on the server, so a
write that reaches one fails with `cannot execute UPDATE in a read-only transaction`, arriving
here as a 500. The sweep reads every GET from `RequestMappingHandlerMapping` — never a
hand-kept list — fills path variables from a fixture by placeholder name, and drives all of
them with an administrator session.

It asserts three things, and the second two are what stop it being decoration:

1. No route answers 5xx.
2. The routes that answer something other than 2xx are **exactly** one documented entry
   (`GET /data/professor/id`, which needs two query parameters this sweep does not send).
   Without this, every route could be answering 400 and rule 1 would still be green.
3. At least 47 routes actually entered a read transaction and answered from it.

Result: **no second F-9.** All 47 readable routes came back clean.

### Reused rather than rewritten

`ApiAuthorizationMatrixTests`, `ApiOwnershipMatrixTests` and `ApiProtocolContractTests` each held
a private copy of the same `handlerMapping.getHandlerMethods()` walk, and the read-only sweep
would have been the fourth. It is now `com.pse.support.MappedRoutes`, and all four use it. The
one thing the three copies disagreed about — what a mapping that declares no verb means — is
reported rather than decided: such a route arrives with a `null` verb and every call site states
its own reading. The three sweeps ran 7/7/6 before the extraction and 7/7/6 after.

## The E2E layer

Six journeys, over real HTTP, against the database the application actually runs on. This is the
last of the four layers and the only one that exercises a socket.

**What it buys over the layer above it.** Every other API test calls handlers through MockMvc,
which is Spring's dispatcher without a server: no port, no HTTP parsing, no real connection
handling. That is the right tool for checking a response field by field, and that checking stays
there. What it cannot say is whether the pieces work *together* — whether a session minted by one
endpoint is accepted by another, whether a student's comment comes back on the lecture page they
wrote it on, whether a revert really restores the row. Each test here is one such story, and
asserts its outcome rather than the shape of every response on the way.

### The client is `RestClient`, and that was a finding

`TestRestTemplate` is the obvious answer and is **not on this classpath**: Spring Boot 4
reorganised the test starters and this project pulls `spring-boot-starter-webmvc-test`.
`WebTestClient` is in `spring-test` but is the reactive one and would drag WebFlux into a servlet
application for the sake of a test. `RestClient` (spring-web) is already there, needs no new
dependency, and is the client the application itself uses in `RestClientGitLabClient`.

It throws on 4xx and 5xx, and half of these journeys are about a refusal, so
`com.pse.support.E2EClient` wraps it with a no-op status handler: a response is a value rather
than an exception, and a journey reads as a sequence of requests and assertions instead of a
sequence of try/catch.

### The six journeys

| # | Journey | What it proves that the layers below cannot |
|---|---|---|
| 1 | Sign up, sign in, sign out | Requesting a code creates nothing; **redeeming** it creates the account; and the logout actually stops the token working |
| 2 | Rate, comment, answer, vote | Each write is read back through the endpoint the app reads it through — "the write returned 200" and "the reader sees it" are different claims |
| 3 | Admin CRUD, audited and reverted | Create → edit → both in the audit log → revert the edit → the row is restored and the reversal is its own entry |
| 4 | Being turned away | A signed-in student gets 403 and is recorded; an anonymous caller gets 401 and is not — `AccessRefusalAuditor` returns early without a principal |
| 5 | Which login decides the clock | One account, two logins: `/auth/login` about a year, `/admin/auth/login` about a day |
| 6 | A bug report reaches the tracker | One report, exactly one issue, through the recording client |

Journey 5 is worth its own note: it is the property a **two-minute deployment experiment** was
once run to demonstrate. It now lives in a test instead of in a setting somebody has to remember
to change back.

### One database, two contexts, two invocations

The end-to-end context cannot be the integration one: `webEnvironment = RANDOM_PORT` alone would
fork it, and these journeys additionally need `TestDeliveryConfig`, because the login code is
normally emailed and `CapturingLoginCodeDelivery` is the only way a test can read it.

Two contexts against one database would reset the schema under each other. So
`server:postgres-integration` runs **two Maven invocations** sharing one `postgres:16` service. A
separate JVM cannot interleave, and the *exactly one context* guard stays true for each
invocation on its own rather than being weakened to allow two. The job greps both logs and fails
if either started more than one. Cost: one extra Maven start against a container that is already
running.

`TestDeliveryConfig`'s beans became `@Primary` for this: `MailService` is `@Profile("!test")`, so
under the `test` profile the capturing delivery is the only candidate and nothing has to win —
but the PostgreSQL layers run with no profile, where `MailService` is a bean too and two
`LoginCodeDelivery` beans is a context that will not start.

### Where a browser would come in, and why it is not here

This layer drives the API, not a user interface, and that is the boundary rather than a gap.
There is no frontend in this repository: the Android client and the admin panel are their own,
and a browser driver (Playwright or anything else) would have nothing here to open. Its place is
the panel's repository, against a deployed backend — and what it would test is the panel's
rendering and navigation, not these routes, which are covered from here.

The one thing worth agreeing with whoever owns the panel is *who asserts what*, so the same
journey is not written twice and neither side assumes the other has it: the contract between them
is [admin-api.md](admin-api.md), and the panel's work list is
[adminweb-tasks.md](adminweb-tasks.md).

### What the journeys do not do through the API

Two seams, both deliberate: a lecture is seeded through the repository so journey 2 has something
to rate, and an account is promoted to administrator by writing the row. Neither has a
self-service endpoint — administrators are not supposed to be able to make themselves — and
inventing one for a test would be inventing product.

## Two requests at once

`ConcurrencyPostgresTests`, four tests, inside the integration layer and sharing its context.

**Why it is here and not in the main job.** The three protections it exercises are the
database's — `SELECT … FOR UPDATE` row locking, two unique constraints, and an optimistic
`@Version` column. H2 does not reproduce their timing, so the same class in `server:test` would
be measuring its own fixture. It carries `@PostgresIntegrationTest` like the other five classes,
so Spring's context cache hands it the same context and the job's "exactly one context" guard
still reads 1.

**It drives services rather than routes**, for two specific reasons rather than as a style
choice. `POST /auth/request-login` sends mail after the rate limiter and this layer deliberately
has no `TestDeliveryConfig` — adding one would fork the context for all six classes. And
`MockMvc` does not document itself as thread-safe, so driving it from two threads would put the
fixture in the race alongside the code under test.

**It is not written as characterization, which is a deliberate exception to the house rule.**
`CLAUDE.md` says to pin current behaviour and invert the assertion later. That cannot be done
for a race: one that does not happen produces no failure, so "one of these two requests fails"
is flaky by construction and would pass on a fixed system and on a broken one that simply did
not collide. These tests assert the invariant that has to hold either way — nothing escapes as
an unhandled failure, and the row count is what serialised execution would have produced.

**The latch is load-bearing and was checked.** Submitting two tasks to a pool does not make them
collide; the first can finish before the second is scheduled. Both threads block on a
`CountDownLatch` and are released together. Removing it makes all four tests **pass with zero
collisions** — the silent failure mode of the whole category, and the reason it is written down
here.

Result: no defect. What it did establish is which mechanism holds each race, and that each one
fails the tests when removed — see
[Negative results](test-findings.md#two-requests-at-once-collide-and-every-protection-holds).

## The 9 September defect pass

Not a new layer and not a new batch: a read of the code against a list of defect *shapes* the
suite had already found once. Seven fixes, [F-22 to F-28](test-findings.md), of which two were
already written down in the backlog and five were not.

What it was looking for, and what each shape turned up:

| Shape | Where it had been seen | What it found this time |
| --- | --- | --- |
| A failure answered with `200 {"success": false}` | F-5 | F-22 (two catalogue reads), F-28 |
| Caller input parsed without a guard | F-15 | F-24 (four `/social` routes), F-25 (two bodies) |
| A collection reaching the client with no order | F-21 | F-27 (rating categories, a `HashMap` keyed by an enum) |
| A field written but never read, or read but never written | the refactor | F-26 (`ratingCount` always 0) |
| A guard added in one method and not its neighbour | — | F-23 (revert of a refusal answers 500) |

**The last two rows are the ones worth keeping.** Both are regressions of a fix this repository
had already made: F-23's null guard sits twenty lines above the crash *with a comment explaining
why it is there*, and was never mirrored into the method beside it; F-26's neighbour
`averageRating` was moved to recompute on read during the single-responsibility refactor while
`ratingCount` was left reading a column nothing writes. Neither is a new kind of mistake. Both
say the same thing: **a defect that has been fixed once is worth grepping for**, because the fix
records where the shape lives and the next instance is usually nearby.

Every one of the seven was watched fail before it was fixed — for F-23 that meant seeing the 500
over real HTTP, and for F-27 seeing all five categories in the wrong positions. Where a
characterization test pinned the old behaviour it was **inverted rather than deleted**: four for
F-22, one for F-28.

The suite went 927 → 975 tests; LINE 95.91% → 96.65%, BRANCH 90.61% → 93.28%.

## The Kontrollphase pass — the five shapes, swept to the end

The 9 September pass found seven defects by taking five known defect *shapes* and looking for
the same shape elsewhere. It proved the method and then stopped: **none of the five shapes was
swept exhaustively.** F-23 had been searched in one class, F-26 on one field, F-21 and F-27 in
two places each. This pass finished each one, and recorded the coverage whether or not it found
anything — an unrecorded sweep gets repeated, or worse, assumed.

| Shape | What was scanned | What it found |
| --- | --- | --- |
| A guard in one twin and not its neighbour (F-23) | 13 twin pairs, line by line | **F-31**, **F-32**, plus one documented-and-deliberate asymmetry |
| A field read but never written, or written but never read (F-26) | every persistent field on all 21 entities, both baselines | **F-33**, **F-34**, plus 8 dead-data fields recorded with a verdict each |
| An order-undefined collection reaching the client (F-21, F-27) | 5 mappers, **all 76 DTO records**, every `*Response` builder, all 15 repositories | **F-35** — four instances |
| An unguarded parse of caller input (F-24, F-25) | all 15 parse sites in `src/main` | **F-36**, in one class. **11 of 15 already guarded both failure modes** |
| A failure answered `200 {"success": false}` (F-5, F-22, F-28) | **every** `BasicResponse(..., false)` — 14 sites | **F-37**. 11 were already correct; 2 of the remaining 3 proved *unreachable* |

Plus two gaps an earlier finding had parked: **F-38**, the size limit F-17 left "to be measured
separately", and **P-8**, the `admin-api.md` drift.

### What the sweeps taught that the fixes did not

**Two shapes are now closed rather than merely searched.** The parse shape came back nearly
clean, and the `200 {"success": false}` shape is down to one reachable instance —
`GET /ratings/own/{lectureId}`, pinned with the reason it was left. A shape is finished when
its coverage is written down, not when somebody stops looking.

**Probing beats reading, twice.** Two candidates that read like live defects turned out to be
unreachable, and only a probe could say so: `AdminService.validateAdmin`'s
`"Is Not Admin"` body (the chain answers 403 first — the test was written expecting 200 and
went red) and two of `IdentityService.validate`'s three branches (the bearer filter and
`@NotBlank` answer first). Both are now in *Unreachable states deliberately not tested* with
the mechanism that makes them so.

**A fixture can make a sweep impossible, not just weak.** F-38's plan was to extend
`ApiProtocolContractTests`. MockMvc has no servlet container and therefore no multipart parsing,
so neither exception can be raised there — an oversized body answers 415 from content
negotiation and a sweep built on it would have passed against a broken handler. `TransportLimitTests`
drives a real container instead, at the cost of one extra Spring context, and that cost is
written down rather than hidden.

### Two new sweeps

`ApiErrorMessageContractTests` asks the fifth question — **what does the API say when it
refuses?** 189 refusals provoked; body shape and message style both conform, so it is a
negative result that now protects a property which had been true by luck.

`AdminApiDocumentationDriftTests` puts P-8 under test, in both directions, and found a
documentation error on its first run.

The suite went 977 → **1001** tests; LINE 96.65% → 96.67%, BRANCH 93.28% → 93.16% (the small
branch dip is new guard branches, not lost coverage).

## Beyond the suite: two CI-only tools

Two questions the suite cannot answer, so neither is a test.

**Secret scanning (`secrets:new`, `secrets:history`).** Both now read `.gitleaks.toml`, added
9 September: rules keyed on this project's own property names rather than on the entropy of a
value, because three of the five known credentials were invisible to the default rules for
exactly that reason. It found three more nobody had — one of them in a design document rather
than a config file — which is [F-29](test-findings.md#f-29--three-more-leaked-credentials-nobody-had-found). No test can see a credential in a
tracked file; the bearer token in `testclient/TestClient.java` was found by a person reading it.
gitleaks over the history found **five** leaked credentials on its first run, four of them
previously unrecorded — see `docs/TODO.md`, which tracks the rotations. The blocking job scans
only the commits a push or merge request brings, so it starts green and goes red when somebody
commits a secret; the full-history job is schedule-only and `allow_failure`, because its red is
expected until the rotations are done and an expected red must not be able to block anything.

**Mutation testing (`mutation:nightly`).** Coverage says every line ran. It says nothing about
whether any assertion would have failed had the line been wrong, and that gap has been found by
hand here more than once — P-6 most clearly. PIT answers it mechanically: **1134 mutants, 840
killed (74%), 72 survived, 222 with no coverage**, in under two minutes — 9 September, read
from `target/pit-reports/`. The first run, 8 September, was 1194 mutants at 67%.

Read its report with the caveat [P-7](test-findings.md#p-7--a-surviving-mutant-is-not-a-finding)
records. The profile runs only the context-free service unit test classes — 50 of them today, over 77
mutated classes — because pointing PIT at the whole suite would start a Spring context per
mutant. So `SURVIVED` means "the classes in this profile do not kill it", not "the suite misses
it" — the first report's most
alarming survivor turned out to be covered by two `AdminApiIntegrationTests` all along. A
survivor is a candidate to investigate, not a finding.

## The single-responsibility refactor

Seven commits that moved code and changed no interface. No route, status code, response field
or message string differs, which is why the `CHANGELOG` has no entry for any of it and why the
API, contract and E2E layers were expected to stay green **unedited** — that expectation is
what the work was checked against.

The sections above are a record of what was tested when, and they name classes by the names
those classes had at the time. They have not been rewritten. This table is how a reader gets
from one of those names to where the code and its tests live now.

| Was | Is now |
| --- | --- |
| `AuthService` (468 lines, 18 constructor parameters) | `LoginCodeService`, `SessionIssuer`, `SessionRevoker`, `IdentityService`, plus `SessionAuditWriter`, `StudentProvisioning`, `KitEmail` |
| `SocialService` (591) | `CommentService`, `AnswerService`, `VoteService`, `ContentReportService`, `NotificationService`, `SocialResponseMapper`, `ContentAudit` |
| `ModerationUserService` (843) | `UserDirectoryService`, `StudentProfileService`, `StudentLifecycleService`, `WarningService`, plus `ModeratedStudents`, `UserListQuery`, `UserResponseMapper` |
| `ModerationContentService` (430) | `ContentModerationService`, `RatingModerationService`, `UserReferenceMapper` |
| `ModerationCatalogService` (350) | `LectureModerationService`, `ProfessorModerationService`, `CatalogueEdits` |
| `LoginLocationService` (342) | `LoginLocationService` (geo-IP only), `LoginMapFragment`, `Coordinates` |
| `MailService` (194) | `HtmlMailSender`, `LoginCodeMail`, `LoginNotificationMail` |
| `HashGenerator` | `TokenHasher` (SHA-256, session tokens), `OtpHasher` (Argon2, login codes) |
| `LectureService` / `ProfessorService` response builders | `LectureResponseMapper`, `ProfessorResponseMapper`, `LectureLabels`, `RatingAverages`, `ProfessorRatings` |
| the paging block in three listings | `shared/page/KeysetPage` |
| `toCountMap` in two services | `shared/repository/IdCount`, a Spring Data projection |
| `escapeHtml` in two classes | `shared/util/Html` |

Test classes moved with the code they cover and their bodies are unchanged, so the numbers in
the batch tables above still describe the same assertions under different file names.
`AuthServiceTests` became four classes, `SocialServiceTests` five, `ModerationUserServiceTests`
three, `ModerationContentServiceTests` and `ModerationCatalogServiceTests` two each.
`SocialServiceVoteTests` is `VoteServiceTests`, and the four `null` collaborators it passed on
purpose are gone — not because the intent was dropped but because the class under test now has
exactly the five collaborators the vote methods use, which is the same statement made
structurally.

### What the refactor found

Splitting a class does not find defects. Asking what pins each half does, and these are the
gaps that answered "nothing":

- **The grouped counts** feeding `UserResponse.warnings`, `UserReferenceResponse.warnings` and
  the role field. Converting five `List<Object[]>` queries to a projection left the whole suite
  green, which proved nothing — no unit test stubbed those queries. The alias was deliberately
  broken to confirm `AdminApiIntegrationTests.warningIsDeliveredToTheStudentWithoutRestrictingThem`
  goes red on it, and `UserReferenceMapperTests` now pins the two derived fields directly.
- **`KeysetPage`.** Three listings resume their pages through it and a boundary error there
  drops or repeats a row on all three. The services reach it with mocked repositories and so
  never execute the slice at all; `KeysetPageTests` does, twenty cases including a full page,
  an empty one and a limit of zero.
- **`ContentAudit.metadataFor`.** The 120-character preview boundary had no test in the
  mutation profile's reach; both mutants survived. Four cases now.
- **`KitEmail`.** The same pattern and the same message existed in three places. The rule now
  has one home and fifteen cases of its own.
- **Configuration binding.** `RateLimitProperties` is exercised by an API test that spends
  exactly three login codes, but `@DefaultValue("3")` and the configured `3` are the same
  number, so the test could not tell binding from default. Setting the default to 99 and
  watching the test still pass is what showed the value comes from
  `application-test.properties`.

### What it did not do

- **`ModerationCommentService` and `ModerationAnswerReportService` are still two classes.**
  They are the same 150 lines twice and merging them was the plan. Measured, the differences
  are wide rather than deep — two tables, two repositories, two sets of audit constants, two
  response shapes — and a template method over them needs thirteen abstract methods to save
  a hundred and twenty lines, which separates the algorithm from its data for no gain. The one
  rule that could silently diverge, `ACTION_TAKEN` being the only status that hides content,
  is extracted as `ReportOutcome.visibilityFor`. The rest is plumbing the compiler keeps
  honest. The same reasoning applies to `VoteService.voteComment`/`voteAnswer` and to the two
  methods in `ContentReportService`, and is written in each of those classes.
- **No behaviour was corrected.** Everything the work turned up that is client-visible went to
  the backlog in [TODO.md](TODO.md) instead, unfixed and named.

## The consumer contract layer

**The question it answers:** does what this API serves match what the two client repositories
actually read?

That is not the question any other layer here asks. The unit, API, contract, integration and
E2E layers all ask whether a route behaves correctly, and every one of them was green while
`GET /auth/me` was unmapped and the admin panel could not sign anybody in
([F-39](test-findings.md#f-39--get-authme-was-unmapped-and-the-admin-panel-could-not-sign-anybody-in)),
and while `POST /answers/report` answered 405 to every report the Android app has ever filed
([F-40](test-findings.md#f-40--post-answersreport-was-never-served-and-every-report-from-the-app-failed-silently)).
Correct behaviour on the routes that exist says nothing about the routes a client calls.

The suite went 1001 → **1019** tests over this layer and the three fixes it produced (F-39,
F-40, F-41); LINE 96.67% → 96.70%, BRANCH unchanged at 93.16%.

### What it reads

Two documents in `docs/`, each an audit written **in the consumer's own repository** against its
own call sites: `adminweb-consumer-contract.md` (42 routes) and `frontend-consumer-contract.md`
(24 called routes). Each ends in a delimited `consumer-contract` block — verb, path, whether the
client sends a session, and every field that client reads, written out in full. The prose above
the block is the clients' own and is not generated from anything.

The block exists for the reason [ADR 0012](adr/0012-documentation-under-test.md) gave for
`admin-api.md`'s contract table, and [ADR 0013](adr/0013-consumer-expectations-in-the-backend-repo.md)
records why it is hand-written rather than parsed out of the prose: the two documents use a
leading-dot shorthand for nested fields and resolve it differently, so a parser has to guess —
and did guess wrong, on `ratings[].lecture.semesterSeason`.

### What it checks

`ConsumerContractSweepTests`, in three directions:

| Check | Fails when |
| --- | --- |
| Route and verb | A consumer calls something this application does not map. Each one is a broken call in a shipped client. |
| **Field names** | A field the client reads is not in the response type that route returns. |
| Routes claimed by nobody | The count of application routes no consumer lists moves — a new undocumented surface, or a consumer list gone stale. |

**The field check is the part that did not exist anywhere.** Status agreement is covered three
times over; field-name agreement was covered nowhere, and it is the failure both clients describe
as silent. The panel's `expectArray` verifies arrayness and casts the elements without checking
them, so a renamed field inside a list renders as an empty cell; Gson leaves an unmatched field
`null`. A rename reaches a user as a blank column, and no test, log line or error report says so.

Fields are resolved by reflection against the handler's return type rather than against a live
response. That reaches every field of every route with no fixture per endpoint, including nested
ones a probe would not populate. It found two on its first run — the panel's ratings tab reads
`author` and `scores` where this API serves `student` and `topics` — and both had been wrong for
as long as the endpoint had existed.

Alongside it, `ConsumerEnumContractTests` pins the eight enums that cross the wire by constant
name, at the unit layer, because Gson maps an unrecognised value to `null` rather than failing
and one new `RatingCategory` would crash the app's rating screen.

### What it does not answer

- **Whether the client is right about itself.** The blocks record what each client reads, taken
  from that client's own audit. If an audit is wrong about its own source, this layer inherits
  the error. Those audits live in the repositories that can check them.
- **Whether a field's *value* is correct.** It compares names and shapes. That a `createdAt` is
  the right instant, or that an average is the right number, is what each endpoint's own tests
  are for.
- **Whether a client handles what it receives.** Both audits are explicit that they mostly do
  not — 34 of the panel's 42 calls branch on no status, and the app inspects no status but 401 —
  and no test here can fix a `catch` in another repository.
- **Anything about a third consumer.** Two are inventoried. A client nobody has audited is
  invisible to this layer, and the routes-claimed-by-nobody count is the only hint it gives.

## Characterization: what `active = false` actually stops

Five tests added on 10 September pin [F-47](test-findings.md#f-47--active--false-is-honoured-by-exactly-two-queries-so-a-deactivated-row-is-still-read-still-rated-and-still-shown).
They assert **current** behaviour on purpose, and each javadoc says so and names the open product
question (`docs/TODO.md` item 38) rather than implying the behaviour is intended.

Where each one sits, and why it sits there rather than one layer up:

| Claim | Layer | Test | Why this layer |
| --- | --- | --- | --- |
| A deactivated lecture is served by id to an anonymous caller | API / role | `LectureApiIntegrationTests.getLectureByIdStillServesADeactivatedLectureToAnAnonymousCaller` | The claim contains "anonymous", so it needs the security chain. A unit test on `LectureService` could only say `findById` does not filter, which is half the sentence. |
| Same for a professor | API / role | `ProfessorApiIntegrationTests.getProfessorByIdStillServesADeactivatedProfessorToAnAnonymousCaller` | As above. |
| A deactivated lecture still accepts a rating | Unit | `RatingServiceTests.submitRatingIsAcceptedForADeactivatedLecture` | Pure service logic with a mocked repository: no chain, no database and no route are involved in the rule being pinned. |
| A deactivated lecture still accepts a comment | Unit | `CommentServiceTests.submitCommentIsAcceptedForADeactivatedLecture` | As above. |
| A deactivated professor stays in the lecture's staff list and in its `title` | Unit | `LectureResponseMapperTests.toResponse_deactivatedProfessor_staysInTheListAndInTheTitle` | The mapper is a pure function; the fixture is a `LinkedHashSet`, for the reason the tests around it already record. |

**The two API-layer tests assert the pair, not the route.** Each performs the single read *and*
the list it disappeared from, in one test, because the finding is the difference between them. A
test that only asserted `200` on the single read would pass just as well if the list stopped
filtering.

**Each was seen red before it was seen green.** All five were written inverted — asserting what a
fix would produce — run, and flipped only once the failure had been observed. The reds are quoted
in the finding, and they are the reason these are not five tests that pass by looking at nothing.

**Two of them state the rule twice on purpose.** The service tests end in
`verify(deactivated, never()).isActive()`: the service does not ask. The `lenient()` stub above it
is the same evidence in another form — a strict stub of `isActive()` would fail as an unnecessary
stub, because nothing calls it.

**Nothing here belongs in the integration layer.** None of the five asks a question H2 cannot
answer, and three of them touch no database at all.

## The account lifecycle from the app side, and the check that is missing under it

Five tests added on 10 September cover what a student does to their own account. Two record a
behaviour that was added ([ADR-0016](adr/0016-self-service-lifecycle-recorded-not-authorised.md));
three are **characterizations** of [F-48](test-findings.md#f-48--an-unauthenticated-caller-can-undo-somebodys-account-deletion),
which is deliberately unfixed, and each of those says so in its javadoc.

| Claim | Layer | Test | Why this layer |
| --- | --- | --- | --- |
| An anonymous caller revives a self-deleted account | API / role | `AuthApiIntegrationTests.anAnonymousLoginCodeRequestBringsBackAnAccountThatDeletedItself` | The claim is "anonymous, through the public route", so nothing below the security chain can make it. It also runs the whole shape end to end — register, self-delete, revive — which is how the two findings are shown to be one story. |
| Both entries are served on the administrative log and neither can be reverted | API / role | `AdminApiIntegrationTests.selfDeletionAndRevivalAreServedOnTheAdministrativeLogAndNeitherCanBeReverted` | `revertible` and `revertBlockedReason` are computed per read, and the scope decision is only observable in which endpoint answers. This is the assertion that keeps `CHANGELOG` `10.09 (20)` and the response from drifting apart. |
| The account is back **before a code exists** | Unit | `LoginCodeServiceTests.requestLoginRevivesADeletedAccountBeforeTheCodeIsEvenIssued` | Ordering, asserted with `InOrder` against mocks. The API test can show the effect; only this one shows that the flip precedes the code, which is the defect rather than its consequence. |
| The revival outlives the failure of the request that caused it | Unit | `LoginCodeServiceTests.theRevivalOfADeletedAccountOutlivesAFailureToSendTheCode` | Delivery has to be made to throw, which is a mock's job. |
| The deletion and the revival are recorded, with the right action, label and metadata | Unit | `StudentServiceTests.deleteAccount_existingStudent_recordsTheDeletionAgainstTheAccountItself`, both tests in `AccountReactivatorTests` | Pure collaborator verification. The label is asserted in full because the panel recovers a deleted account's identity from `target_label`, so its format is a contract and not a detail. |

**Every one was seen red first**, including the two that describe behaviour that already existed:
the ordering test by asserting the reverse order, the others by asserting the state or the action
a fix would produce. The reds are quoted in [worklog.md](worklog.md).

**Nothing here is in the integration layer.** None of the five asks a question H2 cannot answer,
and three of them touch no database at all.

## Where the suite stops

*Deliberately uncovered* below names the **classes** nobody tests and why. This section names
eight **questions** nobody tests — four the suite was drawn around, one a sweep's own filter,
one that is about where the tests run rather than what they cover, one about where the
application runs, and one about **what it runs against**, which is the only one of the eight
that has since produced a live defect. That is the larger and less
visible gap: absence is not a defect, but an *unwritten* absence is — it reads as an oversight to anyone auditing this, and
the next person either re-derives the reasoning or, worse, assumes the question was answered.

Each of these is a boundary the suite was drawn to, not a hole in it. Written so that crossing
one is a decision.

### `active = false` is a filter on two queries, and nothing else

The suite pins what deactivation does; it does not pin what most people assume it does, and the
difference is a scope limit worth stating outright rather than leaving to be inferred from five
test names.

**`UserStatus`-style gating does not exist for this flag.** `active` is consulted by exactly two
queries — `LectureRepository.findByActiveTrueOrderByNameAsc` and
`ProfessorRepository.findByActiveTrueOrderByLastNameAscFirstNameAsc` — and by nothing else in
`src/main/java`. So a deactivated row is still served by id to an anonymous caller, still accepts
new ratings and comments, and a deactivated professor is still nested in every active lecture's
staff list and inside its generated `title`. That is
[F-47](test-findings.md#f-47--active--false-is-honoured-by-exactly-two-queries-so-a-deactivated-row-is-still-read-still-rated-and-still-shown),
pinned by five characterization tests and **deliberately unfixed**: whether `active = false`
means *hidden from the catalogue* or *retired* is a product question, `docs/TODO.md` item 38.

**Nothing in production is misbehaving because of it, and that is measured rather than assumed.**
Curled on 10 September, `GET /data/lectures` serves 92 lectures and `GET /data/professor` 132
professors, and **not one row carries `active: false`** — the nested professors included. The
flag has never been set on the deployed data.

**That single measurement explains two findings at once**, which is why it belongs here and not
only in the findings document. It is why F-47 has cost nobody anything: there is no deactivated
row for any of the three consequences to apply to. And it is why
[F-42](test-findings.md#f-42--deactivating-a-catalogue-row-hides-it-from-the-only-list-the-panel-reads)
went unnoticed for the life of the product: the panel's catalogue form can deactivate a row and
has no screen that can find it again, but nobody has used the switch, so the trap has never been
sprung. **Both are armed rather than burning**, and both become live the first time an operator
uses that switch in anger — which is a limit of what "green" means here, not a reason to relax.

### Performance and load — not measured at all

There is **no** load test, no latency budget, no throughput figure and no query-count
assertion anywhere in this repository. Nothing here says how the API behaves under
concurrency beyond the four correctness races in `ConcurrencyPostgresTests`, and those assert
*outcomes*, not timings.

`LazyLoadingRegressionTests` is the closest thing and it is worth being precise about, because
its name invites the wrong reading. It is **six tests over nine read paths** —
`/account/information`, `/account/ratings`, `/data/lectures`, `/data/lectures/{id}`,
`/data/professor`, `/data/professor/{id}`, `/ratings/{id}`, `/social/comments/{id}`,
`/social/sync/comments` — and what it asserts is that walking a lazy collection outside a
session does not throw `LazyInitializationException` and turn into a 500. `open-in-view` is
disabled, so that is a real and recurring failure. It says **nothing** about how many queries
those paths run. A route issuing one query per row would pass every assertion in that class.

So the known N+1 in `SocialResponseMapper` (`docs/TODO.md`, item 15) is **unmeasured, not
absent** — and there is no test that would notice a second one appearing. Adding one means
counting statements, which needs a Hibernate statistics hook or a proxying `DataSource`;
neither exists here, and neither is pretended to.

### Injection and output escaping — where the protection actually comes from

**SQL injection is closed structurally rather than by testing for it**, and the structure is
worth stating because a reader cannot tell it from the absence of tests:

- There are **no native queries** — `nativeQuery = true`, `createQuery` and
  `createNativeQuery` appear **zero** times in `src/main`.
- All 18 `@Query` annotations are JPQL with named parameters and **no string concatenation**.
- Everything else is a derived query or a Criteria predicate, both of which bind parameters.
- The one place a caller's text is shaped before it reaches a query is `SqlLike.escape`, used
  in exactly two places (`AuditLogService`, `UserDirectoryService`). Even there the escaped
  text is passed as a **bound parameter** to `builder.like(..., pattern, SqlLike.ESCAPE)`;
  the escaping is about `%` and `_` meaning *wildcards* rather than about injection.

So there is no unparameterised path for a test to attack. A test that sent `'; DROP TABLE` and
watched nothing happen would be asserting against JPA, not against this application.

**Output escaping is not this backend's responsibility, with one exception, and that exception
is tested.** The API returns JSON; escaping for a browser belongs to whoever renders it — the
admin panel and the Android client, in their own repositories. The exception is the mail the
backend renders itself as HTML, where `shared/util/Html.escape` runs on every interpolated
value in `LoginNotificationMail`, `LoginCodeMail` and `LoginMapFragment`, and
`LoginMapFragmentTests` pins the hostile-URL case.

Stated as a boundary because an unstated one reads as an oversight: **if the panel renders a
username, a comment or a warning message as HTML without escaping it, that is a defect in the
panel and nothing in this repository will catch it.**

### Migration reversibility — forward-only, by decision

`PostgreSqlMigrationSmokeTests` proves the production migrations produce a schema the
application starts against. There is **no `undo` migration, no down-script and no rollback
test**, and the repository has exactly one migration file (`V1__existing_schema_baseline.sql`)
plus its H2 twin.

The decision: **a bad migration is rolled forward, not back.** The consequence, so it is not
discovered during an incident — a deployment that has to be undone needs a new migration
written under pressure, or a restore from the backup `deployment.md` describes. Flyway's
community edition has no `undo` anyway, so this is a decision about *process*, not a gap in
tooling.

What that costs is bounded today by there being one baseline. It gets more expensive with each
migration added, and `docs/TODO.md` items 14 and 23 both want a `V2` — which is the point at
which this decision should be revisited rather than inherited.

### Time representation, and the invariants holding it together

Two Java types, two column shapes, four paginating endpoints and one `Clock`. Nothing in the
code says how they relate, and [F-30](test-findings.md#f-30--a-rate-limiter-that-is-only-correct-because-a-clock-in-another-file-is-utc)
already showed what that costs: a rate limiter that is correct only because of a bean in
another file.

| Column | SQL type | Java type | Read by |
|---|---|---|---|
| `audit_logs.created_at` | `timestamp(6) **with time zone**` | `Instant` | `KeysetCursorCodec`'s `Instant` overload |
| every other `*_at` — 30 columns across 13 tables | `timestamp(6)`, no zone | `LocalDateTime` | `Cursor.createdAtLocal()` |

`AuditLog` is the **only** entity using `Instant` and its column is the only one carrying a
zone. `GET /admin/audit-logs` and `GET /admin/activity-logs` page on the first row of that
table; `GET /admin/ratings` and `GET /admin/users` on the second.

**The invariants, named with the file that owns each and what breaks if it moves.** F-30 wrote
down the first two; the sweep found three more:

| Invariant | Owner | What breaks |
|---|---|---|
| The `Clock` bean is `Clock.systemUTC()` | `TimeConfig` | `RateLimitService.isCurrentWindow` and `retryAfter` do wall-clock arithmetic on a `LocalDateTime`. `systemDefaultZone()` gives the limiter a bug twice a year — F-30 |
| `spring.jpa.properties.hibernate.jdbc.time_zone=UTC` | `application.properties` | it is what makes a zone-less `LocalDateTime` *mean* UTC on the wire. Change it and every stored timestamp shifts, silently, including the ones cursors are built from |
| `timestamp(6)` **rounds**, it does not truncate | both baselines, and PostgreSQL/H2 agree | the BUG-1 hypothesis was built on truncation and could not have been right. A cursor built from an unflushed entity carries nanoseconds the column cannot store — see *Settled questions* |
| `AuditLog.@PrePersist` fills only a **null** `createdAt`, while `@CreationTimestamp` elsewhere **overwrites** | `AuditLog` vs every other entity | it is why `CursorPaginationPostgresTests` can stamp audit rows before insert and has to stamp students and ratings afterwards through `JdbcTemplate`. A test that gets this backwards silently measures its own fixture |
| `audit_logs.changes` / `metadata` are **`jsonb`** on PostgreSQL and **`json`** on H2 | the two baselines, which differ in exactly these two lines | `jsonb` normalises object key order, and about fifty `Map.of` call sites build those documents. `Map.of` iteration order is randomised per JVM run, so those sites are correct **only** because of the column type — and the H2 suite runs on the one type that would not save them. Array order is *not* normalised either way, which is what the 9 September sweep found and fixed |

The last one is the same shape as F-30 and is recorded here for the same reason: it is true,
nothing states it, and the file that would have to change is not the file that would break.


### The documentation drift sweep only looks at `/admin` in one direction

`AdminApiDocumentationDriftTests` checks `docs/admin-api.md` against the code **both ways**, and
the two ways do not cover the same surface.

- **Document → code** covers every row in the contract table, whatever its path.
- **Code → document** — `everyAdministrativeRouteTheApplicationMapsIsInTheDocument` — filters on
  `route.contains(" /admin")`.

So the reverse direction sees only the `/admin`-prefixed surface, and **the entire legacy surface
the panel actually calls today is outside it**: `/data/**`, `/auth/**`, `/users/**` and the rest
of the unprefixed routes. A route added there can be undocumented without this test noticing.
The document→code direction still catches a row that documents something the code does not serve,
so the gap is one-sided: **undocumented legacy routes, specifically.**

This is a scope limit and not a defect to fix here. The filter is what makes the assertion mean
"the admin API is documented" rather than "every route in the application is in the admin API
document", which is not true and is not meant to be.

**It used to say here that the gap stops mattering when `docs/TODO.md` item 23 lands and the
legacy surface is deleted. That is no longer true and the sentence was wrong to leave standing.**
On 10 September the routing decision behind [F-43](test-findings.md) and F-44 settled that
`/admin/` on the deployed host belongs to the panel's own container permanently, so **the
unprefixed legacy surface is the production contract and is not being deleted**
([adminweb-tasks.md](adminweb-tasks.md) withdraws both migration tasks). The gap is therefore
**permanent, not transitional**: this sweep is not what would catch a new undocumented `/data`
route, and nothing is scheduled that would make it so.

Recorded against the same filter in
[test-findings.md](test-findings.md#p-8--admin-apimd-drifted-five-times-and-nothing-noticed).

### The drift sweep is green about `/admin/**` routes that the deployment does not serve

**`AdminApiDocumentationDriftTests` passing is not a claim that those routes are reachable.** It
runs this application in-process, where `/admin/**` maps exactly as `docs/admin-api.md` says.
The deployed host does not route them here at all.

One nginx serves `https://ratemyprofessor.dev`: `/admin/` goes to the admin panel's own static
container, everything else to this backend with the path unchanged. So on the deployment, every
`/admin/**` API route is answered by the panel's `index.html` — `GET /admin/auth/me` and
`GET /admin/system/status` come back `200 text/html` instead of `401` JSON, and
`POST /admin/auth/login` comes back `405` from that nginx. Measured by curl, with the response
headers used to name the serving layer rather than guessed at. Recorded as
[F-43](test-findings.md); the decision to leave the routing that way is
[F-44](test-findings.md).

**Nothing is broken by it.** The panel calls the unprefixed legacy routes — `/auth/**`,
`/users`, `/system/status`, `/data/**` — and those do reach this backend. That is the contract
carrying production, and under the 10 September decision it is the contract permanently.

**What the gap actually is.** Over 100 claims in that sweep are probed and correct *about this
application*, and roughly half of them concern a surface no deployed client can call. The
assertion is true and worth having — it is what keeps the document honest about the code — but
it establishes nothing about production, and **no test in this repository can**, including the
E2E layer, which drives this application directly rather than the host in front of it. Reading a
green drift sweep as "the admin API works in production" is the specific misreading this
paragraph exists to prevent. It is the same shape as F-39, F-40 and F-42: the path the client
actually calls is the one carrying production, and the documented path is the one nobody
exercises.

Closing it would need a test that runs against the deployed host — a different thing from this
suite, with different credentials and a different failure meaning — and that has not been built.

### The tests do not verify the production schema

**The suite never reads the schema it will be deployed onto.** Stated outright because every
layer above unit looks as though it does, and because this is the one limit in this section that
has already cost something: it is why
[F-50](test-findings.md#f-50--the-gitlab-issue-success-path-answers-409-state-conflict-and-the-issue-stays-open)
— a `409` on a working button — was green here and broken in production. The limit itself is
[F-51](test-findings.md#f-51--nothing-verifies-the-production-schema-and-it-is-not-the-schema-in-the-repository).

**Four facts, each defensible, that combine into it:**

1. **`V1` was baselined into production, not applied to it.** The deployed database was baselined
   on 2026-09-05 (`deployment.md:61`, `:358`). Flyway's baseline writes a row in
   `flyway_schema_history` and **does not execute the migration**, so
   `V1__existing_schema_baseline.sql` has never run against the deployment.
2. **The production schema is therefore the residue of the pre-Flyway `ddl-auto=update` era** —
   accumulated by Hibernate, recorded in no file.
3. **`ddl-auto=validate` cannot see the difference.** It checks that tables and columns *exist*
   with compatible types. It does **not** check column length, nullability, defaults, unique
   indexes or check constraints. A production column narrower than the mapping, or still `NOT
   NULL` from an older one, or carrying a leftover unique index, passes and the application starts
   clean.
4. **The schema the tests execute is a second, hand-maintained baseline.**
   `spring.flyway.locations=classpath:db/migration/{vendor}` resolves to
   `src/test/resources/db/migration/h2/V1__existing_schema_baseline.sql` under the test profile.
   Two files holding one truth, kept in step by hand.

**What each layer actually proves, so this is not read as broader than it is.** The integration
layer runs on H2's translation. The PostgreSQL layer and E2E run on `V1` **executed** against a
real PostgreSQL 16 — `PostgresTestDatabase:64-66` sets `baseline-on-migrate=false` and
`ddl-auto=validate`, and `PostgreSqlMigrationSmokeTests` asserts the application starts against
the result. That is a real check and it is worth what it is: **`V1` is self-consistent with the
entities.** It is not a check that production matches `V1`, and no test in this repository can be.

**The measurement that makes the gap concrete rather than theoretical.** The two baselines were
diffed line by line: they differ in exactly one place — `audit_logs.changes` / `.metadata` are
`jsonb` in the PostgreSQL file and `json` in the H2 twin, a deliberate translation. `bug_reports`
is identical in both and carries no unique or check constraint on the issue columns. And
`EndToEndJourneyTests:260-261` opens a GitLab issue over this exact path against real PostgreSQL
built from `V1`, and passes. **Production refuses the same write.** So the constraint that fires
there is in none of the artefacts this repository holds — which is the gap, stated as an
observation rather than a worry.

**Crossing this one is a decision.** What closes it is a comparison against the deployed database
(`\d` on the affected tables, or a dump) followed by a `V2` that corrects whatever the comparison
finds — `docs/TODO.md` item 42. Until then, **a green pipeline says this application is consistent
with `V1`, and says nothing about the database it is deployed onto.** Note also that this
interacts with *Migration reversibility* above: the forward-only decision is cheap while there is
one baseline, and the first `V2` is the migration that has to be written against a schema nobody
has read.

### Four layers do not run on a developer machine, and CI status cannot be read from here

`./mvnw clean verify` runs the unit, API/role and contract layers and nothing else. The rest
either skips itself or lives only in the pipeline, which means **a green local build is not a
green project** and the difference should not have to be rediscovered.

| Layer / job | Trigger | In CI | Locally |
|---|---|---|---|
| `server:test` — unit, API/role, contract, JaCoCo, `COVERAGE_LINE_MIN=90` | push + MR, never on schedule | runs, **blocking** | runs |
| `coverage:line-target` — the `COVERAGE_LINE_TARGET=95` tier, `--fail-on-target` | push + MR | runs, **`allow_failure`** — yellow, never red | the same script runs inside `verify`, without the gate |
| `server:postgres-integration`, first invocation — the six PostgreSQL classes, 24 tests | push + MR | runs, **blocking** | **does not run** — the tests skip themselves unless `POSTGRES_SMOKE_JDBC_URL` is set, which is what keeps the local build Docker-free |
| `server:postgres-integration`, second invocation — `EndToEndJourneyTests`, six journeys | push + MR | runs, **blocking** | **does not run**, same reason |
| `secrets:new` — gitleaks over the new commit range | push + MR | runs, **blocking** | **does not run** — gitleaks is not installed here |
| `secrets:history` — gitleaks over the whole history | **schedule only** | **`allow_failure`**, and expected red until the rotations are done | does not run |
| `mutation:nightly` — PIT | **schedule only** | **`allow_failure`** | runs on demand: `./mvnw -Ppitest test-compile org.pitest:pitest-maven:mutationCoverage` |
| `dependency:scan` — Trivy over the SBOM `server:test` publishes | push + MR | runs, **blocking** | does not run |
| `sonarcloud` | push + MR, **and only if `$SONAR_TOKEN` exists** | `allow_failure`; with no token it does not run at all | does not run |
| `health:check`, `backup:offsite` | **schedule only** | run against the deployment | do not run |
| `server:package` / `server:deploy:staging` | `main`, non-schedule / the same plus `when: manual` | run / by hand | do not run |
| Mermaid rendering for `class-diagrams.md` | — | **there is no such job** | no renderer here |

Two things that are easy to misread from the job list:

- **E2E has no job of its own.** It is the second Maven invocation inside
  `server:postgres-integration`, sharing one PostgreSQL service with the integration classes.
  Each invocation asserts it started **exactly one** Spring context, by counting
  `PostgresTestDatabase: resetting the public schema` in its own log — two contexts would mean
  two classes resetting the schema under each other, and everything above the check would have
  proved nothing.
- **`secrets:history` is expected to be red.** All twelve fingerprints in `.gitleaksignore` are
  still commented out, because a line is uncommented only after that credential is rotated at
  its provider. It is `allow_failure` for exactly that reason — see F-29 and the rotation table
  in `docs/TODO.md`.

**Pipeline status cannot be checked from this repository.** CI is a private GitLab and there is
no token on this machine, so nothing here has seen a job result: the table above is read from
`.gitlab-ci.yml`, and it says what *should* run, not what *did*. **No claim is made that the
last pipeline was green.** Job output has to be pasted in by somebody who can open it.

---

## Deliberately uncovered

Four classes are visibly low and none of them is an oversight. Saying so here is the point: an
unexplained 0% reads as neglect, and the next person re-derives the reason or, worse, writes a
test that pins an adapter's plumbing. The table had a third row until batch 5 —
`RestClientGitLabClient`, described there as debt rather than as a decision. It is covered now,
and the row is gone rather than reworded, which is the only honest way to keep this list
readable: everything still in it is a decision.

| Class | LINE | Why |
|---|---|---|
| `HtmlMailSender` | **24.0%** (6/25) | The SMTP adapter itself, behind `LoginCodeDelivery` / `LoginSuccessDelivery`. Every test substitutes `CapturingLoginCodeDelivery` through `TestDeliveryConfig`, so the real class is never constructed. Testing it means mocking `JavaMailSender` and asserting on a `MimeMessageHelper` — that is, asserting that the adapter calls the library the way the library documents. |
| `LoginNotificationMail` | **0.0%** (0/15) | Template fill for the login-notification mail. Reached only through `HtmlMailSender`, and its logic worth testing — the location lookup and the map fragment — already lives in `LoginLocationService` and `LoginMapFragment`, both of which are tested. |
| `LoginCodeMail` | **0.0%** (0/8) | Template fill for the one-time-code mail. Same seam and the same reason. |
| `MailTemplateService` | **7.7%** (1/13) | Its entire job is reading template files from `static/mail/` off the classpath and substituting `{{placeholders}}`. The house rule for unit tests is no file system; a test here would either break that rule or assert over its own fixture. It is a candidate for the integration layer, not this one. |

**Two classes became four**, which is why this table has more rows than it used to and not more
uncovered code: the single-responsibility refactor split `MailService` (194 lines) into
`HtmlMailSender`, `LoginCodeMail` and `LoginNotificationMail`. The decision did not change with
the split, and neither did the seam — `LoginCodeDelivery` / `LoginSuccessDelivery` is
substituted in every context, so no test can reach the real SMTP adapter by accident. `MailTemplateService` is the one to revisit when the integration layer
starts — a real classpath and a real template file is exactly what it needs, and exactly what a
unit test is not allowed to give it.

---

## Findings

### BUG-1 — "Does the cursor work on the backend?"

**Not reproducible on the backend.** It was traced end to end and every step came out
correct: the wire format (`base64url_nopad("1|<Instant>|<UUID>")`), `decode`'s seven
separate rejections, the agreement between the keyset predicate in `AuditLogService`
(`createdAt < c.createdAt OR (createdAt = c.createdAt AND id < c.id)`) and the
`ORDER BY createdAt DESC, id DESC` ordering, the `limit + 1` last-row probe, the controller
`@RequestParam cursor` binding, and the `nextCursor` field in the DTO. `AuditLog.createdAt`
is an `Instant` and uses the `Instant` overload; the two moderation lists that store
`LocalDateTime` correctly call `Cursor.createdAtLocal()` — there is no mixed-type
comparison anywhere. The extraction commit `096a484` is faithful line for line.

Decision: no red test, characterization tests instead. The format and every rejection were
pinned as a regression net; the problem is most likely on the panel side.

### BUG-2 — "Approximate location stopped working"

Three findings:

1. **There is no provider chain.** Geoapify only renders the static map image
   (`createStaticMapUrl`); it was never a geolocation fallback. The only fallback is
   `catch (Exception) → LoginLocation("Unknown", null, null)`.
2. **The most likely production cause:** `ipinfo.token=${IPINFO_TOKEN:}` falls back to
   empty and `getLoginLocation` returns `"Unknown"` through `ipInfoToken.isBlank()` without
   making any HTTP call at all. That path is still silent per request, but it now logs a
   WARN once at startup via `warnIfTokenMissing()` (`@PostConstruct`) — it is no longer the
   one failure that leaves no trace when the token is unset.
3. **The swallowed exception is logged.** `LOGGER.warn("Could not determine location for IP
   {}", ip, exception)` writes it with the stack trace (`8d18abb` added this). So 401/403/429
   and timeouts can be told apart in the logs; the only path that is silent during a request
   is the empty-token path, and that one is recognizable from the startup warning.

### Newly found defect — `normalizeIp` was crashing (fixed)

`","` (an `X-Forwarded-For` consisting of nothing but a comma) took `normalizeIp` down with
an `ArrayIndexOutOfBoundsException`: Java's `split(",")` drops trailing empty pieces, so the
array came back empty and `[0]` blew up. Because `normalizeIp` runs **before** the `try`
block, this was the one input the broad `catch` did not catch — the exception escaped
`getLoginLocation` and surfaced as an uncaught error in the login email sent from the
`AFTER_COMMIT` listener. The login itself kept succeeding.

Fix: `rawIp.split(",", -1)`. The negative limit keeps empty pieces, so the result is `[""]`
and falls into the existing blank check. The marker test
`getLoginLocation_addressOfOnlyCommas_returnsUnknownWithoutCallingIpInfo` now pins the
fixed behaviour.

### BUG-3 — Professor assignment order silently refuses a revert

`Lecture.professors` is a `Set<Professor>` — a type that defines no order — but
`CatalogueRevertHandler.LectureHandler.currentValues` turns it into an **ordered** `List`,
and `RevertValues.sameValue` compares two collections through
`asStrings(left).equals(asStrings(right))`, that is, position by position. The result: a
lecture whose professor assignment never changed can be marked `VALUE_CHANGED` and refuse a
legitimate revert, purely because the set's iteration order does not match the order written
into the audit JSON.

The same order sensitivity exists on the write side: `ModerationCatalogService.updateLecture`
builds `before` from the `Set` and `after` from the order of
`professorRepository.findAllById(...)` and compares them with `!before.equals(after)` — so an
unchanged assignment can write a bogus `professors` change and a bogus audit event.

**Why it was not tested against real `HashSet` order.** `Professor` overrides neither
`equals` nor `hashCode`, so identity hashing is used and bucket distribution depends on
object addresses: the order can differ **on every run**. Picking UUIDs to force an order
does not help, because the UUIDs never enter `hashCode` at all. Such a test would be flaky,
and the flakiness would be written into the test rather than into the defect. The defect was
split into two deterministic facts instead: with a `LinkedHashSet` fixture, that the handler
**produces an order the declared type does not define**, and that `sameValue` counts the same
two elements in reverse order as different — pinned separately.

**Fixed.** `sameValue` sorts before comparing, and `ModerationCatalogService` does the same on
the write side through one `sameAssignment` helper. Sorted rather than de-duplicated: dropping
one of two identical entries is still a change. The two deterministic tests were **inverted
rather than deleted**, which is the return on having written them that way — neither depends
on real `HashSet` order, so they pin the fix exactly as reliably as they pinned the defect.
The handler still hands the check an ordered list, and that half is still pinned; what changed
is that nothing reads the order any more.

### Four gaps on the voting path — all four closed

Four behaviours pinned in the `SocialService.voteComment` / `voteAnswer` characterization
tests, none of which looked deliberate. All four are fixed; the list is kept because what a
characterization batch is *for* is turning "this looks wrong" into something specific enough
to act on, and this is the clearest example of that in the document.

1. **No audit was written.** `submitComment` / `submitAnswer` / `submit*Report` in the same
   class all call `auditWriter.writeStudentAction(...)`; the two vote methods did not, so vote
   manipulation did not appear in the moderation trail at all. **F-1, fixed:** `COMMENT_VOTED`
   and `ANSWER_VOTED`, with the direction as metadata and a withdrawal recorded too.
2. **`voteType` was not validated.** A `null` vote type travelled all the way to
   `setVote(null)` and was handed to `save`; the only thing that stopped it was the
   `nullable = false` column, at flush time, surfacing as a 409 where a client expects a 400.
   **F-2, fixed** at the boundary with `@NotNull`.
3. **Neither was `@Transactional`.** The other write methods in the class are. Fixed with
   F-1 — `AuditWriter` joins the caller's transaction on purpose, so without one a vote could
   commit while its entry did not.
4. **There was no toggle-off path.** A second vote in the same direction rewrote the vote
   instead of removing it, and `VoteType` (`UP`, `DOWN`) had no third value for removal.
   **F-3, fixed** with `NONE`, which deletes the row — a withdrawal rather than a third
   direction, chosen over a toggle because a toggle would have changed what an existing
   client's retry does without asking it.

### `SocialController` route defect — the slash is missing from `/vote/answer`

The mapping at `SocialController:96` is `"/social/comments/vote/answer{answer_id}"`. There
is no slash before the path variable, so the real route is
`/social/comments/vote/answer<id>`. The sibling endpoint on the comment side (`:73`) is
written correctly (`.../vote/comment/{comment_id}`). This is a controller-level defect,
outside the unit layer; it was not fixed in this batch, only recorded. (Fixed in the
API/role batch — see F-4 for the current state.)

### Deleted dead code — `StudentService`'s second moderation path

Seven methods with no callers were deleted from `StudentService`: `deleteUser`,
`blockStudent`, `unblockStudent`, `findByKitEmail`, `addStudent`, `getAllUsers`, `getUser`.
The code was already asking the question itself: `//TODO: Why arent they in use?`.

The reason for deleting rather than testing is not just coverage arithmetic. The live
administrator flow is `ModerationUserService` (`blockStudent` :591, `unblockStudent` :617),
and that one also does `protectAdministrator`, an idempotency short-circuit, the
`blockedAt`/`blockedReason` bookkeeping, `revokeTokens(target)` and
`auditWriter.write(... USER_BLOCKED ...)`. `StudentService`'s copies did **none** of that —
only `setStatus` + `save`. No token revocation, no audit record, no administrator
protection. Writing tests for those would have nailed down a second, quietly weaker
moderation path that nobody depends on, at the very moment the code is being reduced to a
single path.

The unused `import com.pse.user.service.StudentService;` in `RatingService` was removed as
well. `StudentService`'s only consumer in `src/main` is now `AccountService`, and it uses
exactly three methods.

Because the deletion lowers the denominator, the measurement was taken at the *start* of the
batch, **after** the deletion; that is why the 3341 in this document is smaller than batch
1's 3403.

### A test file in the wrong package

`StudentServiceTests` sat in the `com.pse` root while the class lives in
`com.pse.user.service`. A version in the house style was written at `com/pse/user/service/`: a
manual constructor factory instead of `@InjectMocks`, AssertJ instead of JUnit `assertEquals`,
`method_scenario_expectation` names. The same intent as `1cd449b` (ProfessorServiceTests).

**This entry said "it was moved". It was not.** The original stayed where it was, and so did
`AccountServiceTests`, so 16 tests went on pinning the same contract twice, in the style the
rewrite existed to replace. Both were deleted in batch 5 and the bundle did not move; the
process finding is [P-4](test-findings.md#p-4--a-moved-test-file-was-copied-not-moved).

### Prompt/code mismatches

The code won in every case: `AuditCursorCodec` is in `com.pse.audit.service`;
`AuditRevertService` is in `com.pse.audit.revert` and takes a `List<AuditRevertHandler>`
rather than a `Map`; `RateLimitService` takes a `PlatformTransactionManager` rather than a
`TransactionTemplate`; `LoginLocationService` was not injecting `RestClient`.

---

## Settled questions

### The BUG-1 nanosecond hypothesis — measured, and it does not hold

**The hypothesis was.** `AuditLog.@PrePersist` writes nanosecond precision through
`Instant.now()`, while the Postgres column **truncates** to microseconds. A cursor built from
an entity still in the persistence context carries `…123456789` while the row holds
`…123456`. Since `.123456 < .123456789`, the keyset predicate matches the same row again and
it **repeats** at the top of the next page.

**Two of its premises were wrong, and the third does not lead where it was thought to.**

1. *"Only a real Postgres can show this."* Both baselines declare `created_at timestamp(6)`
   — the H2 one as well — so whatever precision is lost on PostgreSQL is lost on H2 too. Most
   of this was measurable with no database at all, and
   `AuditTimestampPrecisionTests` measures it.
2. *"The column truncates."* **It rounds.** PostgreSQL 16 answers `.123457` for
   `.1234569`, and H2 rounds too. That is not a detail: truncation can only move an instant
   backwards, which is what the hypothesis's arithmetic depended on. Rounding moves it
   forwards half the time, so the mechanism as described could not have been derived from
   what the column actually does.
3. *The premise that did hold:* `Instant.now()` really does carry nanoseconds on this
   platform, so the entity really does hold digits the column cannot store. That much is
   pinned, as a property of the clock rather than as a constant.

**And the defect does not reproduce.** Against a real PostgreSQL 16 with the production
migrations, `pagingOneRowAtATimeNeverServesTheSameRowTwice` walks the log one row at a time
and no row is served twice. The read path builds its cursor from `page.getLast()` — a row the
query loaded, so it carries the stored value — and the keyset predicate pairs a strict
`createdAt <` with an id tiebreaker, which is the shape that makes a repeat impossible even
when two rows share a microsecond.

BUG-1 therefore has no explanation on the backend, which is where the two bug reports in
[Findings](#findings) already left it. What is different now is that the timestamp
explanation has been ruled out rather than left standing as the leading theory.

**The Testcontainers question was the wrong question.** It was recorded as the blocker for
the whole integration layer: adding the dependency would put Docker in a build that
deliberately did not need it. The pipeline already ran a real `postgres:16` as a **GitLab CI
service** for the migration smoke test, and a service is a plain TCP server — no Docker
socket, no privileged runner, no dependency. `com.pse.support.PostgresTestDatabase` is that
job's setup generalised, tests opt in with
`@EnabledIfEnvironmentVariable(named = "POSTGRES_SMOKE_JDBC_URL", …)`, and the local build is
as Docker-free as it was. (The `CLAUDE.md` this document cited for the Docker-free rule is
not in the repository and never has been — `git log --all -- CLAUDE.md` is empty. The rule
itself is real and visible in the test profile; only the citation was.)

---

## Classes

### KeysetCursorCodec

- **Responsibility:** encodes and decodes the opaque cursor for keyset pagination over the
  `(createdAt DESC, id DESC)` ordering.
- **Behaviours tested:**
  - The `encode → decode` round trip, nanosecond precision included
  - The wire format: `1|<Instant>|<UUID>`, URL-safe base64 without padding
  - That the `LocalDateTime` overload gives the same result as the UTC `Instant` overload
    and is the exact inverse of `Cursor.createdAtLocal()`
  - The seven rejection paths: null, empty, the 512-character limit, non-base64, wrong
    version, wrong number of parts, undecodable `Instant`/`UUID` — all 400
  - That the error message is the text the caller supplied
- **Deliberately not tested here:** the cursor's effect on the database query — a Criteria
  predicate, which needs a database. Nanosecond/microsecond precision, likewise.
- **Mocks used:** none; the class has no dependencies.
- **Residual risk: closed.** Producing a cursor correctly and *applying* it correctly are
  separate things, and both are now covered: the precision question by
  `AuditTimestampPrecisionPostgresTests`, and the predicate and page boundary of all four
  cursor endpoints by `CursorPaginationPostgresTests`. See
  [The integration layer](#the-integration-layer).
- **Test count:** 23 (good: 6, bad: 17)

### AuditCursorCodec

- **Responsibility:** a thin delegate that binds the audit-specific error message onto the
  shared codec.
- **Behaviours tested:** that `encode`/`decode` are forwarded unchanged, that
  `"Invalid audit cursor"` is passed into `decode`, that an `ApiException` thrown by the
  delegate propagates unwrapped, and a round trip through the real codec.
- **Deliberately not tested:** the codec logic itself — that is in `KeysetCursorCodecTests`.
  The `encode(LocalDateTime, …)` overload is not exposed on this class.
- **Mocks used:** `KeysetCursorCodec` — mocking is the only way to verify that delegation
  actually happens; two tests go end to end with the real codec instead.
- **Residual risk:** negligible; the class is 5 lines.
- **Test count:** 5 (good: 3, bad: 2)

### RevertValues

- **Responsibility:** reads and compares the `before`/`after` values coming back from an
  audit entry's JSON column, insensitive to type.
- **Behaviours tested:**
  - `sameValue`: two nulls, one null, enum ↔ name, `Integer` ↔ `Long`, number ↔ text,
    ordered list equality, a change of order counting as a difference
  - `asString` / `asInteger` / `asBoolean` / `asEnum` / `asIds`: present, missing, wrong
    type, malformed value
  - Exceptions: `NumberFormatException`, `IllegalArgumentException` (`No enum constant`,
    `Invalid UUID string`)
- **Deliberately not tested:** real Jackson serialization — the input here is an already
  deserialized `Map`. Which handler reads which field is each handler's own business.
- **Mocks used:** none; a static helper class.
- **Residual risk:** two behaviours were deliberately pinned "as is" and may not actually be
  wanted: because number comparison goes through `longValue()`, `1` and `1.9` count as
  equal; `Boolean.valueOf` quietly turns `"yes"` into `false`. No recorded field is
  fractional today and no field writes `"yes"`, but a new field could change that.
- **Test count:** 37 (good: 20, bad: 17)

### AdminBootstrapPolicy

- **Responsibility:** resolves the configured `email` / `email:Name` list into the set of
  accounts to be made administrators at startup.
- **Behaviours tested:** empty configuration, entries with and without a name, several
  entries, case normalization, whitespace trimming, dropping empty entries, splitting on the
  first colon, an entry without an email, a duplicate email, immutability of the returned set.
- **Deliberately not tested:** `AdminBootstrapRunner` actually creating the account — a
  different class and an integration concern. The order of `bootstrapEmails()` is not tested
  because `Map.copyOf` does not preserve ordering; it is not part of the contract.
- **Mocks used:** none; a constructor with a single `String` parameter.
- **Residual risk:** the argument is **not** normalized — `shouldBootstrap` does an exact
  match, so an unnormalized address misses silently. The contract lives in the parameter name
  (`normalizedEmail`) and the caller, `AuthService`, lowercases; the test pins that. A `null`
  argument throws `NullPointerException` via `Map.copyOf` — pinned as the current behaviour,
  as agreed.
- **Test count:** 18 (good: 6, bad: 12)

### OtpService

- **Responsibility:** generates the one-time login code, stores it hashed, and consumes it
  exactly once.
- **Behaviours tested:** invalidation of outstanding codes before a new one is generated
  (`InOrder`), `expiresAt = now + ttl` (default and custom TTL, `Clock.fixed`), that the
  plaintext is not stored, that the correct code sets `used = true`, a wrong code, no
  candidate, a legacy plaintext match, a candidate with neither a hash nor a legacy value,
  and that only the matching one of several candidates is marked. Also that the time passed
  into the query comes from the injected clock.
- **Deliberately not tested:** `TokenGenerator`'s randomness (covered by
  `TokenGeneratorTests`) and Argon2 itself. **The real filtering at the TTL boundary is the
  repository query's job** (`…AndExpiresAtAfter…`), so only the written `expiresAt` and the
  instant handed to the query are verified here.
- **Mocks used:** `OneTimePasswordRepository` — to stay off the database and to compose the
  candidate list. `HashGenerator` is static and cannot be mocked; because Argon2
  (m=32768, t=3) is expensive, a real hash is computed only once per class and the other
  `consume` tests use the legacy plaintext branch.
- **Residual risk:** that expired and used codes really are filtered out belongs to the
  integration layer; at the unit level they are represented as "an empty candidate list".
  Pessimistic lock behaviour is not tested either.
- **Test count:** 13 (good: 9, bad: 4)

### RateLimitService

- **Responsibility:** limits code requests and login attempts by keeping a fixed-window
  bucket per operation and HMAC'd subject.
- **Behaviours tested:**
  - The subject hash: stability, email/IP namespace separation, 64 hex characters,
    `null` IP becoming `"unknown"`, `IllegalStateException` on an empty key
  - `validateConfiguration`: 31 / 32 / `null` key
  - `consume`: creating a bucket when none exists, incrementing below the limit, **the exact
    moment the bucket fills** (still successful), **the request after it fills**
    (`RateLimitException`, no record), reset in an expired window, a bucket whose window ends
    exactly now, success after two collisions, a rethrow on three collisions, no retry on a
    rejection, separate buckets for different subjects
  - `ensureAllowed`: no bucket / below the limit / at the limit / expired, that it never
    writes, and that the `Retry-After` value is at least 1 second
  - `reset`: that only that bucket is deleted
- **Deliberately not tested:** real `REQUIRES_NEW` transaction semantics and the unique
  constraint race — a mocked manager does not actually apply propagation. The limit values in
  `AuthService` are not read here either.
- **Mocks used:** `AuthRateLimitBucketRepository` (no database) and
  `PlatformTransactionManager`. The manager is mocked because the service builds the
  `TransactionTemplate` itself, which means the real template does run the callback.
- **Residual risk:** that two concurrent requests count the same bucket correctly can only be
  shown at the integration layer; the three-attempt retry loop here is simulated with mocks.
- **Test count:** 26 (good: 18, bad: 8)

### AuditRevertService

- **Responsibility:** decides whether an audit entry can be reverted and reverses a single
  field edit.
- **Behaviours tested:**
  - `describe`: an empty page, a clean field diff, a non-revertible action, a target type
    with no handler, the lifecycle key (`exists`), a change without `before`/`after`, empty
    `changes`, **a refusal entry with no target id** (an old 500 regression), an already
    reverted entry (plus the id of the reversing entry), outside the window, **exactly on the
    cutoff** (still revertible), one nanosecond past the cutoff, a vanished target, a changed
    value, a field the handler does not know
  - Batch behaviour: a **single** `currentValues` call for two entries of the same type; no
    query at all on a page with no candidates; one decision per entry on a mixed page
  - `revert`: applying the `before` values, `AuditRevertContext` being populated while the
    handler runs and empty afterwards, being cleared even if the handler blows up, 404, and
    409 + message + `reason.name()` code for each of the five refusals
  - Handler wiring: `IllegalStateException` for two handlers claiming the same target type,
    routing to the correct handler, and no handler at all
- **Deliberately not tested:** the handler implementations themselves (`UserRevertHandler`
  and friends) and the reversal reaching the database — separate classes, an integration
  concern. `@Transactional` semantics are absent here.
- **Mocks used:** `AuditLogRepository` (no database, and to compose the `findReversalsOf`
  rows) and `AuditRevertHandler` — mock handlers inside a real `List`, because the service
  builds the map itself with `Collectors.toMap`.
- **Residual risk:** the window enters the message through `Duration.toString()`, so `P7D`
  reaches the user as `PT168H`. The test pins that, but it is a product decision; if the
  panel wants readable text the message has to change.
- **Test count:** 30 (good: 8, bad: 22)

### LoginLocationService

- **Responsibility:** resolves the approximate location for an IP address.
- **Note:** it used to render the login mail's map HTML as well. That half is
  `LoginMapFragment` now, with its own dossier below; the cases listed here are the ones that
  stayed. The coordinate predicate both halves needed is `Coordinates.isValid`.
- **Behaviours tested:**
  - The resolved path: a full response, city only, an empty region (no double comma), and
    that **only the first address** in an X-Forwarded-For list is queried (verified by
    resolving the captured `UriBuilder` function against a real factory)
  - The paths that never touch the network: null/empty address, empty token
  - **Each failure separately:** 401, 403, 429, 5xx, timeout (`SocketTimeoutException`), DNS
    failure (`UnknownHostException`), an undeserializable body, a `null` body, a bogon
    address (all fields null), an unparseable `loc`
  - That each of those returns **exactly** `LoginLocation("Unknown", null, null)` and logs a
    WARN, while the empty-token path logs **nothing at all** during the request
  - `warnIfTokenMissing()`: a startup WARN containing the name `IPINFO_TOKEN` when the token
    is empty or whitespace only, and no log at all when it is set
  - `createStaticMap`: valid coordinates + key (`lonlat` order reversed), an empty Geoapify
    key, invalid coordinate formats (parameterized), empty output when there are no
    coordinates and no URL, HTML escaping
- **Deliberately not tested:** real IPinfo/Geoapify calls. The full text of the generated
  HTML — that would be brittle; distinguishing fragments are verified instead. **The provider
  fallback chain was not tested because there is none:** Geoapify only renders the static
  image, and the only fallback is `catch (Exception) → "Unknown"`.
- **Mocks used:** `RestClient` and three levels of its fluent chain
  (`RequestHeadersUriSpec`, `RequestHeadersSpec`, `ResponseSpec`) — to avoid real HTTP. To
  make that possible, `RestClient` is now injected through the constructor
  (`RestClientConfig`); behaviour did not change. Also Logback's `ListAppender`, because the
  difference between a silent failure and a logged one is this batch's main finding.
- **Residual risk:**
  - The coordinate regex rejects whitespace around the comma (`"49.0, 8.4"` is invalid).
    Harmless today because IPinfo returns no space, but it would silently drop the map if the
    provider's format changed.
- **Since closed (F-8):** the two risks that used to head this list are gone. The broad
  `catch (Exception)` is split into `RestClientResponseException` (logged with the status, so
  401/403 and 429 are distinguishable) and `ResourceAccessException` (network or timeout), and
  the six failure-mode tests each assert their own sentence instead of one generic one. A token
  revoked *after* startup — which no log line fired once at boot can catch — is reported as
  `locationLookupEnabled` in `GET /system/status`. A per-request warning is still deliberately
  not added.
- **Test count:** 42 (good: 12, bad: 30)

### LoginMapFragment

- **Responsibility:** the map in the login notification mail — a rendered static image when
  Geoapify is configured and the coordinates are usable, a "View on Google Maps" button
  otherwise, and nothing at all when there is no location to show.
- **Behaviours tested:** that valid coordinates and a key render an `<img>` with the pair
  emitted **lonlat**, which is the order Geoapify takes and the reverse of what IPinfo
  returns; that a blank key falls back to the button; the coordinate formats that decide
  between image and button, whitespace around the comma included; that no coordinates and no
  URL render **nothing** rather than an empty box; and that a hostile maps URL is escaped
  into the `href` rather than interpolated raw.
- **Deliberately not tested:** the exact CSS in the two text blocks.
- **Mocks used:** none. It has one `@Value` and no collaborators.
- **Residual risk:** that Geoapify changes its URL contract. Nothing here would notice.
- **Test count:** 5, moved from `LoginLocationServiceTests` with the code.

### CatalogueRevertHandler.LectureHandler / .ProfessorHandler

- **Responsibility:** reverts lecture and professor field edits, mutual assignments included.
  The only handler pair in `com.pse.audit.revert` that declares a *collection* field.
- **Behaviours tested:**
  - `LectureHandler.currentValues`: all seven fields (`professors` as a list of UUID
    strings), an **empty list** (not a missing key) for a lecture with no professors, a
    deleted lecture not appearing in the map, an empty target list
  - **BUG-3, in two deterministic halves:** with a `LinkedHashSet` fixture, that the handler
    produces an order the declared `Set` type does not define; and that `RevertValues.sameValue`
    counts the same two elements in reverse order as **the same value**. The second assertion
    was inverted when the finding was fixed — it read "as different" before — which is what
    writing it deterministically bought: it pins the fix as reliably as it pinned the defect
  - `LectureHandler.applyInverse`: resolving all seven fields — in particular mapping the
    record's `"professors"` key onto the request's `professorIds` field — and leaving the
    others `null` in a single-field `before` half
  - `ProfessorHandler`: four fields plus the `lectures` list, a deleted professor, an empty
    target list, full and partial `applyInverse`, and that it goes to **`updateProfessor`**
    rather than `updateLecture`
- **Deliberately not tested:** `ModerationCatalogService` itself (a separate class, the next
  batch). `lecture.getSemesterSeason().name()` and `getLectureType().name()` have no null
  guard, but both columns are `nullable = false`, so the state is unreachable from a
  persisted row — a test would assert over its own fixture. Real `HashSet` iteration order
  (rationale in BUG-3).
- **Mocks used:** `LectureRepository`, `ProfessorRepository` (to stay off the database and to
  compose the "no row" case) and `ModerationCatalogService` — `applyInverse` does not write
  the fields itself, it calls the method the panel calls, so what has to be verified is the
  delegation.
- **Residual risk:** none outstanding. BUG-3 is fixed — `sameValue` compares collections as
  multisets — which matters most for exactly the reason the finding gave: real order varies per
  run because `Professor` has no `hashCode`, so the defect looked **flaky** in production, the
  same edit reverting once and giving `VALUE_CHANGED` the next time. The handler still hands
  the check an ordered list, and that is still pinned; nothing reads the order any more.
- **Test count:** 15 (good: 7, bad: 8) — LectureHandler 9, ProfessorHandler 6

### ReportStatusRevertHandler.CommentReportHandler / .AnswerReportHandler

- **Responsibility:** reverts a report's status change. Why it matters: `ACTION_TAKEN` hides
  the content the report is about, so a status given by mistake takes a post out of the app.
- **Behaviours tested:** for both handlers, that the single `status` field is mapped by name,
  that a deleted report does not appear in the map, an empty target list, the enum passed to
  `updateReportStatus`, and `null` being passed for a `before` half without a `status`. Both
  `applyInverse` tests also verify that the **other** service is never touched.
- **Deliberately not tested:** the status change actually hiding or restoring the content —
  `ModerationCommentService` / `ModerationAnswerReportService`'s job, an integration concern.
- **Mocks used:** `CommentReportRepository`, `AnswerReportRepository`,
  `ModerationCommentService`, `ModerationAnswerReportService`. All four together, because in
  a handler pair carrying this little logic the mistake worth catching is a copy-paste one: a
  handler wired to the wrong repository or the wrong service reverts the wrong report — or
  nothing at all.
- **Residual risk:** negligible; the two handlers are 30 lines together and both are at 100%.
- **Test count:** 10 (good: 6, bad: 4) — CommentReportHandler 6, AnswerReportHandler 4

### WarningRevertHandler

- **Responsibility:** reverts a correction to a warning's text. The **only** handler that
  runs its own query inside `applyInverse`: a warning is addressed as a sub-resource of the
  student it was issued to, so the owner has to be resolved first — and that makes it the
  only handler that can refuse.
- **Behaviours tested:** the single `message` field, a withdrawn warning not appearing in the
  map, an empty target list, the `updateWarning(principal, studentId, warningId, request)`
  argument order being built from the warning row (not from the audit entry — the owner is
  not one of the recorded fields), and refusing a missing warning with `ApiException` +
  `NOT_FOUND` + the `TARGET_MISSING` refusal code.
- **Deliberately not tested:** a `warning.getStudent()` NPE. `Warning.student` is
  `@ManyToOne(optional = false)`, so a row with a null student cannot exist; a test would
  assert over an unreachable state.
- **Mocks used:** `WarningRepository` (for both `findAllById` and `findById`, the second of
  which only this handler has) and `ModerationUserService`.
- **Residual risk:** the race itself is still there and stays — nothing holds a lock between
  the revertibility check and the revert, so the check can say "revertible" and the row can be
  gone by the time the revert runs. Locking would only move the window, since the operator's
  decision is older than either query. What F-11 was actually about is fixed: the refusal
  carries `AuditRevertRefusal.TARGET_MISSING` and the wording the check itself uses, so the
  panel says the entry can no longer be reverted rather than reporting a missing record.
- **Test count:** 6 (good: 3, bad: 3)

### UserRevertHandler

- **Responsibility:** reverts student account field edits. The widest handler: six fields, and
  the only one that builds the value set it reports from **two tables** — the student row plus
  the administrator id list, because the role is not a column on the student.
- **Behaviours tested:**
  - All six fields; `role = ADMIN` for a student in the administrator id set and `STUDENT`
    for one that is not (both arms of the ternary, separately)
  - `biography == null → ""`: a normalization that carries weight, because the audit entry
    also records `""` — reporting `null` would make an untouched biography look changed and
    refuse the revert
  - **Skipping** a `UserStatus.DELETED` row: an anonymized account's recorded username and
    address are already gone, so it must read as a **vanished** target rather than a stale
    one; reporting it would suggest a revert that writes the anonymized values back as if
    they were real
  - An id that did not survive not appearing in the map
  - That the administrator id list is **not** queried for an empty target list — the same
    assertion F-12 recorded as a characterization, inverted when the finding was fixed rather
    than deleted, so the order of the two queries cannot drift back
  - `applyInverse`: all six fields, and the others staying `null` in a single-field `before`
    half
- **Deliberately not tested:** `ModerationUserService.updateStudent` itself — role demotion
  protection and validation belong there. The handler deliberately calls that path so the
  revert inherits the same protections.
- **Mocks used:** `StudentRepository`, `AdminRepository` (the role can only be derived from
  it) and `ModerationUserService`.
- **Residual risk:** this handler's `currentValues` and `applyInverse` were already driven end
  to end by `AdminApiIntegrationTests.aFieldEditIsRevertibleAndTheReversalIsItsOwnAuditEvent`;
  the unit tests' real contribution was only the `DELETED` and `ADMIN` branches (2 missed
  lines before the batch). So the value here is not coverage but writing down **why** the
  branches are the way they are.
- **Test count:** 9 (good: 4, bad: 5)

### VoteService (was `SocialService.voteComment` / `voteAnswer`)

- **Responsibility:** votes on a comment or an answer; a second vote by the same student has
  to update the existing row, because the table has a unique constraint on
  `(student, comment)`.
- **Behaviours tested:** that the vote table is never touched for a target that is not found;
  that the first vote builds the row with `student` + `comment`/`answer` + `vote`; that the
  second vote mutates and saves **the same instance** (`isSameAs`, no second row inserted);
  that the answer path does not touch the comment vote table; that every vote, **withdrawal
  included**, writes its activity entry and a refused one writes none; and that `NONE` deletes
  the row rather than storing a third direction. And, as characterization, the two gaps that
  remain deliberate: no toggle-off on a repeated identical vote, and a `null` `voteType`
  reaching `save` because rejection lives at the boundary.
- **Deliberately not tested:** `submitComment` / `submitAnswer` / the report methods and the
  notification path — the class's remaining missed lines, a later batch. That the unique
  constraint really does prevent the second row is an integration concern; only *what the
  code hands to* `save` is verified here.
- **Mocks used:** the four repositories the two methods touch (`CommentRepository`,
  `AnswerRepository`, `CommentVoteRepository`, `AnswerVoteRepository`) plus `AuditWriter`. The
  constructor's other four dependencies (`LectureService`, the two report repositories,
  `NotificationRepository`) are passed as **`null`** rather than mocks: putting mocks there
  would let a future edit start using these collaborators silently, while `null` turns that
  into an immediate NPE in the method's own test. `AuditWriter` was on that list until F-1 was
  closed — it *was* the finding.
- **Residual risk:** the two remaining gaps are deliberate and written down as such. F-1 (no
  audit) and F-3 (no way to withdraw a vote) are fixed; F-2 was fixed at the boundary, so a
  `null` `voteType` is a 400 rather than the 409 it used to produce, and the characterization
  test that reaches `save` is what stands between a future unvalidated caller and a constraint
  violation at flush time.
- **Test count:** 16 (good: 11, bad: 5)

### StudentService

- **Responsibility:** assembles profile information from an entity graph and soft-deletes the
  account.
- **Behaviours tested:**
  - `getUserInformation`: the credibility score being ups minus downs, the case that comes
    out to zero, the average score plus the rating/comment counts, a zero average with no
    ratings (rather than a division by zero), the 404 for a student that is not found, and
    that the derived score reaches the response **without** being written back to the entity
  - That the score counts **all** votes reaching the student who wrote the content,
    regardless of who cast them (pinned so that a future "don't count your own vote" change
    is a deliberate one)
  - `getUserRatings`: each rating being mapped to a `LectureResponse` plus the average score,
    the count embedded in the message (`"Success, found 2 ratings."`), `success = true` even
    for an empty list, and a 404 for a student that is not found
  - `deleteAccount`: a `null` principal; a principal whose email is not in the table (nothing
    is written); and on the happy path, `UserStatus.DELETED` being written and saved and the
    message containing the email
- **Deliberately not tested:** there are no direct tests for `softDeleteByKitEmail` and
  `getAverageRating` — every branch of both is exercised through `deleteAccount` /
  `getUserInformation`, and separate tests would only count the same lines twice.
  `ProfilePictureGenerator`, `LectureService.createLectureResponse` and
  `RatingService.calculateAverageScore` are **static** and are not mocked; `mockStatic` would
  mean a new dependency (mockito-inline) and a departure from the house style, and it is not
  needed — plain fixtures drive all three.
- **Mocks used:** only `StudentRepository`; the class's single dependency.
- **Residual risk:** none outstanding. All three entries that stood here are closed:
  - **No path threw** — every failure was a response carrying `success = false` and HTTP 200,
    so a caller reading the status mistook a broken lookup for a successful one. F-5: the four
    failures throw `ApiException` now, 404 or 401, and the body a client parses is unchanged.
  - `getUserInformation` called `student.setCredibilityScore(...)` inside a `readOnly = true`
    transaction. F-9: the write is gone, so the dirty-checking question it raised no longer
    has to be answered at the integration layer.
  - Typos in the message strings (`couldnt`, `succesfully`) were going out to the client;
    the three in `deleteAccount` were fixed in `edaf2bc` and the remaining five in `src/main`
    in `57658c3`, with the tests that pin them updated alongside (F-10 closed). Two of those
    three strings no longer exist at all — F-5 replaced the responses that carried them.
- **Test count:** 13 (good: 7, bad: 6)

### AccountService

- **Responsibility:** a thin facade over `StudentService` with exactly one piece of logic of
  its own: revoking the tokens issued to an account when it is deleted.
- **Behaviours tested:** the two read paths delegating unchanged with `student.getId()`; that
  `invalidateAllAuthTokensForEmail` is called on a **successful** deletion and **not** called
  on a failed one — in both shapes a failure can take, the thrown `ApiException` that
  `StudentService` produces since F-5 and the unsuccessful response the guard still refuses to
  act on; and `requestLogout` embedding the returned session count in the message (three
  sessions and zero sessions).
- **Deliberately not tested:** `AuthService.invalidateAllAuthTokensForEmail` actually revoking
  the tokens — `AuthService`'s job. That a deleted account's token can no longer make requests
  is an integration concern (`TokenAuthenticationService` rejects `DELETED`).
- **Mocks used:** `AuthService` and `StudentService`. `StudentService` is mocked because what
  is under examination is not the deletion itself but whether token revocation happens
  **based on its outcome**.
- **Residual risk:** without the `success` condition the account would go `DELETED` while
  every token stayed valid until it expired, and a student token lives for a year — which is
  why the failed-deletion branch is the single most valuable test in this batch. F-5 moved
  that branch: the failure throws now, so the condition is no longer the only thing enforcing
  the rule. It is kept, and tested against the collaborator's contract rather than against
  today's implementation of it, so the rule outlives another change to how failure is
  signalled. The class throws an NPE on `getId()`/`getKitEmail()` if `student` arrives as
  `null`; not tested, because the principal is supplied by the security chain.
- **Test count:** 7 (good: 4, bad: 3)

### BugReportRevertHandler

- **Responsibility:** reverts an administrator's edit to a bug report — status, severity and
  the fields the moderation panel can change on one.
- **Behaviours tested:** the declared target type; `currentValues` mapping every field the
  handler can revert; an id with **no surviving row** being absent from the map rather than
  mapped to `null`; an empty target list; `applyInverse` replaying the recorded values through
  `ModerationBugReportService`; and a `before` half carrying one field leaving the others
  `null`.
- **Deliberately not tested:** `ModerationBugReportService.update` itself, and the GitLab issue
  the report may be linked to — `RestClientGitLabClient` is a separate class, covered by its own
  tests since batch 5.
- **Mocks used:** `BugReportRepository` and `ModerationBugReportService`. The service is mocked
  rather than stubbed because what has to be verified is the delegation: the handler does not
  write the fields itself, it calls the same path the panel calls, so the revert inherits that
  path's validation.
- **Residual risk:** the same check/apply race as `WarningRevertHandler` (F-11) applies in
  principle — nothing holds a lock between the revertibility check and the revert — but this
  handler does not re-query inside `applyInverse`, so it cannot refuse; it would replay onto
  whatever the row has become.
- **Test count:** 6

### ContentRevertHandler.CommentHandler / .AnswerHandler

- **Responsibility:** reverts a moderator's edit to a comment or an answer: the content text
  and the `ContentStatus`. Why it matters: status is what hides a post from students, so a
  status set by mistake removes content from the app until it is reverted.
- **Behaviours tested:** for both handlers, the declared target type, `content` and `status`
  mapped by name, a deleted row being absent from the map, `applyInverse` replaying both
  fields through `ModerationContentService`, and — on the comment side — a `before` half
  carrying only `status` leaving `content` `null`.
- **Deliberately not tested:** that the status change actually hides or restores the content;
  that is `ModerationContentService`'s job and an integration concern. The two handlers live in
  one class because they share the field mapping, and the tests keep them apart the way the
  class does.
- **Mocks used:** `CommentRepository`, `AnswerRepository` and `ModerationContentService` — all
  three together, because in a handler pair this thin the mistake worth catching is a
  copy-paste one: a handler wired to the wrong repository reverts the wrong row.
- **Residual risk:** negligible at the unit level; both handlers are at 100% LINE and BRANCH.
  `ModerationContentService` itself is the class carrying the risk, and it still has 23 missed
  lines and 29 missed branches.
- **Test count:** 9 — CommentHandler 5, AnswerHandler 4

### ApiAuthorizationMatrixTests

- **Responsibility:** the anonymous boundary across the whole published surface, plus the
  `authenticated()` tier, which had no role coverage before this batch.
- **Behaviours tested:** every mapped route either refusing an anonymous caller with 401 or
  being declared public; the declared public routes staying reachable; an anonymous caller
  never being able to provoke a 403 (which would mean the entry point is being bypassed); bug
  submission without a header answering 401 rather than 500; the legacy professor create
  refusing a student with 403; a student session reaching the endpoints the app needs; and
  that no handler relies on `@PreAuthorize` and friends, which are **inert** here because the
  application has no `@EnableMethodSecurity`.
- **Deliberately not tested:** the admin tier by role — that is `AdminApiPathSplitTests`, which
  mints its sessions through the real login endpoints because the older helper writes tokens
  with no session type and would stay green with the admin path broken.
- **Mocks used:** none. A real context, a real security chain, a real database; only mail
  delivery and the GitLab client are substituted.
- **Residual risk:** the sweep proves a route *refuses* an anonymous caller, not that the rule
  behind the refusal is the right one. A route locked to admins that should be open to students
  passes this test and fails nobody until a user complains. The `PUBLIC_ROUTES_ANSWERING_SERVER_ERROR`
  exemption list is gone with F-14 and F-15: a public route answering 5xx is a plain failure.
- **Test count:** 6

### ApiProtocolContractTests

- **Responsibility:** the status codes the API answers with, swept across the surface rather
  than asserted one endpoint at a time.
- **Behaviours tested:** every body-reading route answering an unreadable media type with the
  declared status (415 since F-17 was fixed) and carrying the project's `BasicResponse` body
  rather than a `ProblemDetail`; malformed JSON in the *right* media type still being a clean
  400; a path called with a verb it does not map answering 405 with the project error body; and
  every such refusal naming the verbs that **would** have worked in an `Allow` header.
- **Deliberately not tested:** nothing outstanding. The `Allow` assertion this section used to
  defer arrived with F-16, as its own sweep rather than as a line in
  `wrongVerbCarriesTheProjectErrorBody`: the header is built in one place from the mapping's
  own verb set, so a route that stopped emitting it would otherwise fail silently.
- **Mocks used:** none, as above.
- **Residual risk:** none outstanding. F-16 was the last wrong status here, and closing it
  tested the batch's own claim — flipping `WRONG_VERB_STATUS` carried 93 refusals across two
  suites with no other edit.
- **Test count:** 6

### ApiOwnershipMatrixTests

- **Responsibility:** horizontal authorization — whether an authenticated caller can reach a
  row belonging to another student.
- **Behaviours tested:** the classification ratchet over all 61 id-taking routes; the
  cross-user probe on the one owner-scoped route, asserted on the **row** first and the status
  second — the row check is what proved the refusal back when the status was a 200, and it
  stays the primary assertion because a status is evidence about the answer, not about the
  table; the owner's own call still working; `/ratings/own` being scoped by the principal
  rather than the path; the notification list not leaking another student's rows; the
  administrative routes refusing a student who carries a **real** victim id; and the structural
  invariant that no app-tier handler takes a caller identity from the request.
- **Deliberately not tested:** the admin tier by role, again `AdminApiPathSplitTests`. The vote
  routes are classified cross-user *by design* and are not probed for refusal, because
  refusing there would be the defect.
- **Mocks used:** none.
- **Residual risk:** the ratchet forces a new id-taking route to be classified, but nothing
  forces the classification to be **honest** — an owner-scoped route dropped into
  `NOT_OWNED_BY_A_USER` would pass. The invariant test is the backstop, and it is the one to
  keep working if the sets ever have to be trimmed.
- **Test count:** 7

### LectureModerationService, ProfessorModerationService (was `ModerationCatalogService`)

- **Responsibility:** the catalogue behind the ratings — correcting a lecture or a professor
  that was entered wrong, and the assignment between them.
- **Behaviours tested:**
  - Both rejection gates: a `null` request and a request whose every field is `null`, each
    refused **before** the row is looked up
  - Every validation bound, at the bound and past it: name 300, code 100, professor names
    200, semester year 1900 and 2200 at both ends
  - Trimming before comparison — a name resubmitted with surrounding whitespace is not a
    change
  - Every field resubmitted unchanged recording nothing, and the whole method then saving
    nothing and writing no audit entry
  - `professorIds` and `lectureIds`: an id that does not resolve being a 404, an empty list
    clearing the assignment, and a reassignment being written **from the owning (lecture)
    side** with the detach and attach loops verified on both lectures
  - **BUG-3's write side**: the same professors in a different order recording **no** change
    and no audit entry at all — the two halves of what the bogus change used to produce
  - The audit entry: action, target type and the label built from the row *after* the edit
- **Deliberately not tested:** `deleteLecture` / `deleteProfessor`, which were already
  covered; the cascade behaviour they rely on is an integration concern.
- **Mocks used:** the five constructor dependencies. `AuditWriter` is mocked and captured
  rather than stubbed, because the field diff it receives *is* the observable behaviour of
  these methods — the response says only "Updated … successfully" either way.
- **Residual risk:** 7 branches remain, in the delete paths and the label helpers. BUG-3 is
  fixed on both sides through one `sameAssignment` helper; the test that pinned the defect
  states the fix, which is what a deterministic characterization test is for.
- **Test count:** 29

### UserDirectoryService, StudentProfileService, WarningService (was `ModerationUserService`)

- **Responsibility:** the administrator's view of an account — listing and filtering
  (`UserDirectoryService`), the field edit with its protection rules
  (`StudentProfileService`), and warnings (`WarningService`). The guards the mutating
  endpoints share are `ModeratedStudents`; the query parsing is `UserListQuery`.
- **Where each behaviour below is tested:** the query-parameter rejections in
  `UserDirectoryServiceTests`, the protection matrix in `StudentProfileServiceTests`, the
  warning lifecycle in `WarningServiceTests`.
- **Behaviours tested:**
  - Every query-parameter rejection, each verified to happen **before the first repository
    call**: limit out of range or not a number, a cursor with no limit (refused before the
    cursor is even decoded), an unknown status, role or sort, a search over 200 characters,
    and the refusal to filter for `DELETED` at all
  - The accepted sort spellings, since the parser trims and ignores case — a blank value is
    the default rather than an error
  - The protection matrix on `updateStudent`: self-status, self-role, an administrator
    target for an ordinary caller, and the elevated operator's address, which is refused for
    **everybody including an elevated caller**
  - Promotion saving the admin row and demotion deleting it, with the moderation-history
    refusal checked up front rather than left to the constraint
  - Session revocation on a changed address, on a block, and on either role change, asserted
    through the token rows and the `sessionsRevoked` metadata
  - `blockedAt` stamped from the injected `Clock` and cleared again on unblock
  - The `biography == null → ""` normalization, the same one `UserRevertHandler` depends on
- **Deliberately not tested:** the Criteria predicates (`rolePredicate`, `cursorPredicate`),
  which build a query rather than compute an answer — they belong to the integration layer,
  and mocking `CriteriaBuilder` would assert the shape of a query nobody runs.
- **Mocks used:** all ten constructor dependencies, plus a `Clock.fixed`.
- **Residual risk:** 27 branches remain, most of them in `getStudents`'s two ordering modes
  and the predicates above.
- **Test count:** 49

### ModerationContentService

- **Responsibility:** moderating a comment or an answer — editing the text, and the
  visibility switch the student app reads.
- **Behaviours tested:** the shared `contentChanges` guard from both sides; blank and
  overlong content; content and status resubmitted unchanged writing nothing; the audit
  preview being truncated to 120 characters **with a visible ellipsis** and left whole at the
  boundary; the lecture label with a code, with a blank code and with no code at all; and the
  answer path being audited as an `ANSWER_UPDATED` labelled through its parent comment.
- **Deliberately not tested:** the listing methods' response assembly, which is mapping; the
  rating deletion path, already covered.
- **Mocks used:** all ten constructor dependencies.
- **Residual risk:** 12 branches remain, in `getRatings`'s specification lambda and the
  listing helpers.
- **Test count:** 29

### RestClientGitLabClient

- **Responsibility:** opens an issue in the project's GitLab tracker, and decides whether the
  integration is configured at all. The only class in the codebase that holds a credential and
  sends it somewhere.
- **Behaviours tested:**
  - `isEnabled`: all three of base URL, project id and token required; `null`, empty and
    whitespace-only each rejected for each of them; a base URL of nothing but slashes
    (`"///"`) disabling the integration, because the trailing-slash trim runs before the
    emptiness check
  - `createIssue` with the integration off: **503** `"GitLab integration is not configured"`,
    with **no request sent at all** — asserted by the mock server, which fails on any request
    it was not told to expect
  - The request itself: `POST /api/v4/projects/{id}/issues`, the `PRIVATE-TOKEN` header, the
    JSON content type, and `title` / `description` in the body
  - **F-18:** a project id shaped like `group/project` reaching the tracker as **one** path
    segment (`group%2Fproject`), not as a double-encoded `group%252Fproject`
  - Project id and token trimmed before use
  - The answer: an empty body and a body with no `web_url` both **502**; an `iid` that is
    missing or not a number giving a `CreatedIssue` with a `null` issue number and the URL
    intact
  - Failures: a 500, a 401 and a transport failure all becoming **502 "Could not reach
    GitLab"**, and the client's own 502 **not** being rewritten by the catch-all
  - **The credential:** given a failure whose own message contains the token, neither the log
    line nor the message the caller receives repeats it, and the log carries no stack trace
- **Deliberately not tested:** the configured timeouts. They live in a
  `SimpleClientHttpRequestFactory` built in the constructor; asserting on them means either
  reflection over a Spring class or a real slow socket, and the mock server bypasses the
  factory entirely. Also not tested: what GitLab does with the issue — that is the tracker's
  behaviour, not this class's.
- **Mocks used:** none in the Mockito sense. `MockRestServiceServer` bound to a
  `RestClient.Builder` drives the HTTP conversation, and Logback's `ListAppender` reads the log,
  because "the token is not in the log" is an assertion about a log line and nothing else can
  make it.
- **Residual risk:** a revoked token and an unreachable tracker are indistinguishable to the
  caller — both 502 — so an administrator whose issue creation stops working cannot tell from
  the response which one to fix; the distinction exists only in the ERROR line, and only as an
  exception class name. Narrowing that is a product decision, not a test one. The `@Autowired`
  marker on the public constructor is now load-bearing: removing it breaks every
  `@SpringBootTest`, and nothing in the unit layer would notice.
- **Test count:** 25

### TokenAuthenticationService

- **Responsibility:** turns a bearer token into an authenticated caller, or refuses it. Every
  authenticated request in the application passes through this one method.
- **Behaviours tested:**
  - The input gate: `null`, empty, whitespace-only and over-long tokens refused **without
    reaching the database**, and a token of exactly the 512-character limit still looked up
  - That the lookup uses the **SHA-256 hash** and never the plaintext
  - The legacy upgrade: a token found by its plaintext gets a hash written, its
    `legacyValue` cleared and the row saved; a token found by hash never looks for one
  - A token whose student is gone, and `BLOCKED` / `DELETED` accounts, each refused —
    separately, because they are separate arms of the same condition
  - That a refused request does **not** update `lastSeenAt` and does not save
  - The five-minute "last seen" window on both sides of its boundary and exactly on it, with a
    fixed clock
  - The session tier: an `APP` session never queries the admin table; an `ADMIN` session with
    no admin row is refused; one with an admin row carries it; and a token with **no** session
    type is treated as neither
- **Deliberately not tested:** that expired and revoked tokens are filtered — that lives in the
  repository query's name (`…AndRevokedFalseAndExpiresAtAfter…`) and is an integration concern;
  at this level it arrives as an empty result. `@Transactional` semantics likewise.
- **Mocks used:** `TokenRepository`, `AdminRepository`, `StudentRepository`, plus `Clock.fixed`.
  `HashGenerator` is static and is used for real — unlike Argon2 in `OtpService`, token hashing
  is SHA-256 and costs nothing.
- **Residual risk:** the untyped-token arm is pinned as characterization, not endorsed. If
  session types are ever made mandatory, this is the test that has to change, and it names the
  decision rather than leaving it to be rediscovered.
- **Test count:** 24

### AccessRefusalAuditor

- **Responsibility:** records the requests authorization turned down, for the panel's refusal
  view.
- **Behaviours tested:** an anonymous caller and a principal that is not one of ours both
  produce **no** record; an authenticated caller produces one carrying the method, the path,
  the `403` and the actor; an administrator arrives as an administrator; and a failing
  `AuditWriter` neither escapes nor changes the answer — it leaves a WARN with the stack trace.
- **Deliberately not tested:** where the entry ends up. That is `AuditWriter`'s job and has its
  own tests.
- **Mocks used:** `AuditWriter`, plus `MockHttpServletRequest` and a real
  `SecurityContextHolder` context, cleared after each test.
- **Residual risk:** the last test is the one that matters and the easiest to delete by
  accident. Without the try/catch a failed audit write turns a correct 403 into a 500, on
  behalf of somebody who was being refused anyway.
- **Test count:** 5

### ModerationBugReportService

- **Responsibility:** the bug report queue — what a student submits, what an administrator
  edits, and the tracker issue opened from it.
- **Behaviours tested:**
  - `sendBugReport`: an invalid token refused before anything is read; **each of the six arms**
    of the validity condition separately, none of them storing anything; and a valid report
    stored trimmed, attributed to its reporter and audited with its severity
  - `updateBugReport`: a `null` request and an all-null request refused **before the report is
    looked up**; an unknown report 404; each field's change recorded as before/after; title and
    description trimmed; both bounds at and past the limit
  - **Every field resubmitted unchanged writing nothing at all** — no save, no audit entry —
    field by field and all four at once
  - `createIssue`: unavailable when the integration is off, without touching the report; a
    missing report **not** marked as a failed attempt; any other refusal marked and rethrown;
    and the existing-issue answer distinguished from the created one
- **Deliberately not tested:** `BugReportIssueTransactions` itself, which is about transaction
  boundaries and belongs to the integration layer — the reason it is a separate class is that
  its failure marker must survive a rolled-back transaction.
- **Mocks used:** all five constructor dependencies. `AuditWriter` is captured rather than
  stubbed: the field diff it receives is the only observable difference between an edit that
  changed something and one that did not.
- **Residual risk:** the idempotency the class promises is enforced inside
  `BugReportIssueTransactions`, so what is verified here is that this class asks for it and
  reports both answers — not that two concurrent requests really produce one issue.
- **Test count:** 32

