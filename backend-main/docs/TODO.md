# TODO

Team working notes. **This file is tracked in git**, despite what this line used to claim —
it said it was gitignored and never left the machine, and neither was true: `.gitignore:43`
covers `CLAUDE.md` and `STATUS.md`, not this file. Write nothing here you would not put in
the repository.

---

## Notebook

**Internal working notes. Not part of the product, and not part of what is assessed** — this is
where questions get parked and answered while the work is in flight, in whichever language they
were thought in. Nothing else in this repository depends on a line of it.

<!-- Write freely here. Nothing above this section depends on it. -->
Need to look at CI/CD on GitLab — there are failures. Cannot be checked from here (private
GitLab, no token): paste the job output for whichever job is red. **Still open.**

Refactoring. → **done**, both. The "held for the heavier refactoring" reasoning did not
survive contact with `admin-api.md`, which was documenting a status code the fix changes:
holding a client-visible correction behind an unscheduled refactor meant shipping a wrong
contract in the meantime. See *Backlog* for what each one was.

Playwright'e gerek var mı? → **No.** There is no frontend in this repository, so a browser
driver has nothing here to open, and the E2E layer already drives the API over real HTTP
against a real PostgreSQL. Its place is the panel's repository, against a deployed backend —
[test-plan.md](test-plan.md) argues it under "Where a browser would come in".

Wiki.md de lazım. → **done.** [Wiki.md](../Wiki.md) at the repository root. It holds only the
two things nothing else covers — the domain vocabulary and the three-repository map — and
points at `README.md` and `docs/` for the rest. A wiki that restates them would be a second
copy, and the second copy is the one that goes stale unnoticed.

Testfindingsin en üstüne kısaca kaç tane bug ne sayesinde hangi tür test sayesinde bulundu ve
hangileri kaldırıldı halloldu yazılması lazım. → **done.** "At a glance" at the top of
[test-findings.md](test-findings.md): 37 findings, 33 fixed, which layer found how many, and
what each layer's findings had in common.

Server tarafına bakmalıyız ama onu ben buraya yazıyorum hatırlamak için sonrasında şimdi yapılacak bir şey değil.


---

# Open right now

Everything below is open as of **9 September**. This section is an index, not a second copy:
each line says where the detail lives. If you close something, close it *there* and delete the
line here.

The split is by what would close it, because that turned out to be the thing this page kept
losing track of. **Ten of the forty-three** cannot be closed by any change to this repository —
nine of them at a provider or in somebody's inbox, and item 32 in the panel's own repository.

**Four of them are new (Kontrollphase pass, 9 September) and all four are questions rather than
work:** items 24–27 below. Each has a test pinning the current behaviour, so whichever way it
is decided the change is deliberate and there is an assertion to invert. The reasoning is in
[worklog.md](worklog.md); the findings are F-34 and the notes beside F-35.

## Nobody can close these from the repository

| # | What | Where |
| --- | --- | --- |
| 1 | **Rotate seven leaked credentials.** The largest open item on the page. | [My next steps](#my-next-steps) |
| 2 | Is `IPINFO_TOKEN` set on the VM? (ssh) | [My next steps](#my-next-steps) |
| 3 | Run the manual deploy job, confirm a one-day admin session | [My next steps](#my-next-steps) |
| 4 | GitLab → CI/CD → Variables: `AUTH_ADMIN_SESSION_TTL` still does not exist | [My next steps](#my-next-steps) |
| 5 | **Create the SonarCloud project**, then set its three variables. Everything else about Sonar is done and verified. | [Tests — next steps](#tests--next-steps-in-priority-order) |
| 6 | Send `adminweb-tasks.md` to whoever owns the panel repository | [My next steps](#my-next-steps) |
| 7 | Paste `Wiki.md` into the GitLab wiki, and decide which of the two is authoritative | [Sonstiges](#outside-testing--until-sunday-1309) |
| 8 | Say who owns what. Most items here are `[owner: ?]`. | [Sonstiges](#outside-testing--until-sunday-1309) |
| 9 | Präsi: the testing story, and the process retrospective | [Präsi](#präsi) |

## Watch once, then close

| # | What | Why it is not just "done" |
| --- | --- | --- |
| 10 | The **second** `dependency:scan` run in CI | The first one ran, and went red on something this row did not predict: a `429` from **Maven Central**, not the vulnerability-database download. Trivy no longer reads `pom.xml` — it scans the SBOM `server:test` publishes as an artifact, and resolves nothing itself. Verified locally in both directions (same packages as before plus lombok; still exits 1 against the 4.0.6 tree), and still unverified **in CI**. |
| 11 | The Spring Boot 4.0.6 → 4.0.8 bump, and the two overrides beside it | 977 tests pass and it takes the CVE count 57 → 0, but a framework bump deserves a human read. **Drop `tomcat.version` when a Boot release pins 11.0.25 or later** — an override that outlives its reason holds a dependency back. |

## Repo work, deliberately not done

Each is named in the [Backlog](#backlog) with the reason it was left. None is a defect.

| # | What | Why it was left |
| --- | --- | --- |
| 12 | Fix the `pitest` `targetTests` scope, **then** set a threshold | 222 of 1134 mutants have no coverage because the profile targets classes whose tests it does not run (9 September, `target/pit-reports/`). A threshold now would measure the profile, not the suite. |
| 13 | The ~295 remaining SonarQube code smells | Test-style opinions (111 `isZero()`, 87 assertion lambdas). No regression among them. The team's call, and the same argument as the coverage gate below: tightening quietly is how a gate stops being believed. |
| 14 | Drop `professors.rating_count` and `professors.average_rating` | Both are now written by nothing and read by nothing. A migration, and it belongs with the `V2` the legacy-surface deletion already needs. |
| 15 | The N+1 in `SocialResponseMapper` | A performance change, not a refactor. |
| 16 | Merge `ModerationCommentService` and `ModerationAnswerReportService` | Measured and declined: 13 abstract methods to save 120 lines. **Wants a second opinion** — if the two drift in review, merge them. |
| 17 | `SessionAuditWriter` disagrees with itself about "administrative" | A product decision, not a fix. Somebody has to say which reading is right. |
| 18 | Two `List<Object[]>` queries in `AuditLogRepository` | A different shape from the five that became `IdCount`. |
| 19 | `orElse(null)` against `orElseThrow`, split by package | Not a defect. It is why the two halves of the codebase read differently. |
| 20 | API test breadth on `/account` and `/ratings` | Thin rather than absent. The two holes worth naming are closed. |
| 21 | Raise `COVERAGE_THRESHOLD` above 30 | It gates **other people's** merge requests. Deliberately the team's call. |
| 22 | `GET /admins/validate` is undocumented | Deliberate: it is being retired. It is now a named exemption in `AdminApiDocumentationDriftTests` rather than a silent gap, and its 403 is pinned. |
| 24 | **Finish or remove `PATCH /social/notifications/all`** | F-34. The security matcher and `NotificationRepository.markAllAsSeen` exist; no controller maps it, so the route answers 400. Writing the handler is a feature; deleting the query throws away the record that one was planned. **Needs a decision.** |
| 25 | Should `UserResponse.reports` count answer reports too? | It counts comment reports only, and `admin-api.md` documents exactly that — so the code matches its contract and the field is narrower than its name. Pinned by a test. **A product question, not a bug.** |
| 26 | `GET /ratings/own/{lectureId}` answers `200` with `success: false` | The last reachable `200 {"success": false}` in the API. The 200 and the empty list are right; the flag is what does not follow. Client-visible, so it waits for whoever owns the app contract. |
| 27 | Drop `professors.rating_count` **and** `average_rating` | Item 14 with the second column named: both are written only by the entity's field initialisers and read by nothing. Needs the `V2` migration item 14 already parks it behind, **and** the same two lines removed from the H2 baseline. |
| 41 | **Decide whether this project wants a `LICENSE` file — and whether `com.example:demo` should become its own name.** | The empty `<licenses>`, `<developers>`, `<scm>` and `<url/>` placeholders were deleted from `pom.xml` on 10 September rather than filled: an empty element reads as a field somebody forgot, and filling `<licenses>` would assert a licence nobody has chosen. `<name>` and `<description>` were filled, because those cost nothing and a nameless artifact is a poor thing to hand in. **Two decisions are left and neither is the backend's to take alone:** whether a `LICENSE` file is wanted at all, and whether the Initializr coordinates `com.example` / `demo` should become the project's own. Nothing in the repository reads either — no test, no script, no CI job, and `src/Dockerfile:19` copies the jar by glob — so renaming is safe whenever it is decided. **Estimate: minutes, once decided.** |
| 42 | **`static/mail/logo.png` has no recorded origin, and it cannot be recovered from this repository.** | The one binary asset that ships (512×512 PNG, 136 KB), embedded in every login e-mail by `HtmlMailSender:34`. **What was checked on 10 September:** it entered in `245174c` ("local changes"), authored by the machine identity `Server <server@pse>` — so not even *who* added it is recorded; the same commit is the R2–R5 credential leak and its other four files are all mail and geolocation work, none naming an asset. The PNG itself carries **no text chunks at all** — no Software, Author, Copyright, Comment or Creation Time; they were never written rather than stripped. No document, ADR, `CHANGELOG` entry, wiki page or code comment anywhere mentions where it came from, and there is no `LICENSE`, `NOTICE` or `CREDITS` file. **No guess is recorded here between "drawn by the team", "generated" and "downloaded" — the evidence does not say.** Two avenues remain, both outside this repository: a reverse-image search, or asking whoever had shell access to the deployment box on 13 July 2026. sha256 `9abc5efbc027797bf11e69be8e26d8c93d38656e906c19fada87274ba47d0766`. **Estimate: minutes if somebody on the team simply knows; otherwise unbounded.** |
| 43 | **Export the Mermaid class diagrams as images, if the Abgabe wants a PDF.** ~~Nothing in the repository exports them.~~ **DONE on 10 September.** `@mermaid-js/mermaid-cli` (`mmdc` 11.17.0) was installed outside the tree and each of the six fences rendered to PNG at `-b white -s 2`; the images are committed under `docs/diagrams/` and embedded in `docs/class-diagrams.md` **above** their fences, which stay in place as the source of truth. All six were opened and compared to their source, `<-->` in diagram 5 included — it draws as a bidirectional association with `*` at both ends. | **What is left, and it is not this item:** the renderer is still not in the repository and there is no CI job for it, so an image goes stale silently if a fence is edited — that is item 48. **And diagram 3 is unusable on a portrait page** at roughly 8:1 — item 47. The original note stands as a record of why this was deferred: it renders on GitLab and GitHub and **does not render in a plain Markdown-to-PDF export**, where the six `classDiagram` blocks would come out as code. |
| 47 | **Diagram 3 is correct and unreadable on an A4 portrait page.** `docs/diagrams/3-moderation.png` is 1568×194 — roughly **8:1** — because the moderation package is six independent controller trees that lay out side by side under `direction TB`. Scaled to a normal text width its boxes come out about **1.5 mm** tall. | **How it was measured:** rendered, then opened and read; the other five are between 1:0.6 and 1:1.6 and are fine. **Three options, and the choice is presentational:** a landscape or rotated full page for that one figure (**~15 minutes**, and it needs whatever produces the PDF to support it); splitting the diagram into two pictures of three controller trees each (**~1 hour**, and it is a redraw — the fence changes, so the caption and the 108-box count in `class-diagrams.md` are re-checked with it); or leaving it and pointing at the repository for that figure (**free**, and the weakest of the three for a printed submission). **Not done here** because it is a redraw and the other five needed none. |
| 48 | **A rendered diagram can go stale silently, because the renderer is outside the repository.** The six PNGs under `docs/diagrams/` are committed, but nothing regenerates or checks them: `mmdc` is not a dependency, there is no CI job, and editing a fence in `class-diagrams.md` leaves the image beside it wrong with nothing to say so. | **This is the hand-maintained-list failure mode in a new place** — the same reason the sweeps read routes from `RequestMappingHandlerMapping` rather than a list, and the same reason the twin database baselines (`ADR-0004`) are a standing risk. **Estimate: ~2–3 hours** — a job that re-renders the six fences and fails if any committed PNG differs, which is the version worth having because it turns a silent divergence into a red pipeline. **Cheaper interim, ~10 minutes:** a line in `class-diagrams.md` telling the next person to re-render after editing a fence, which is documentation rather than a mechanism and should not be mistaken for one. **Do not bundle this with item 47:** that one is a redraw decision, this one is a pipeline gap. |
| 49 | **Several documents will not survive a Markdown-to-PDF export, and the worst of it is table cells, not Mermaid.** Measured across all 36 in-repo markdown files. **Wide table rows** (over 200 characters in a single row): `TODO.md` **36 rows, worst 3717 characters** in a 4-column table — this board itself is the worst offender and items 44–49 added to it, this row included; `deployment.md` 29 rows, worst 1163; `test-findings.md` 22; `test-plan.md` 20; `adminweb-consumer-contract.md` 17 rows across **5 columns**, worst 802. **Code and fence lines over 90 characters**, which overflow rather than wrap: `frontend-consumer-contract.md` **105 lines, longest 251 characters**; `admin-api.md` 11; `test-findings.md` 5; `worklog.md` 3; `deployment.md` 1 at **182**. | **How it was measured:** a script over every tracked `.md`, counting row length and column count outside fences and line length inside them; the figures above are that output, not an impression. **Links were measured too and are in better shape than expected:** 257 cross-file links and 138 same-file anchors, **0 broken** — four stale `consumer-contract.md` links left behind by a rename were found by the same script and fixed in the same pass. **What still dies in a PDF regardless:** the **22 line-anchored links into Java sources** in `adminweb-tasks.md` (`…SecurityConfig.java#L143-L147`) plus two in `README.md` and `Wiki.md` — GitLab resolves those, a PDF cannot, and they are the one link class that needs a decision rather than a fix. **Estimate: ~2 hours** to make the export legible — landscape pages or a smaller font for the widest tables, `\footnotesize` on the offending fences, and a decision on the source links (drop them, or print the path and line numbers as text). **Cheaper and probably right for this submission, ~30 minutes:** export only the documents an examiner actually reads and leave the boards and consumer contracts to the repository link — `TODO.md` and `frontend-consumer-contract.md`, the two worst files, are both working documents rather than deliverables. |

## Suite hygiene, found while writing the consumer contract layer

Four things noticed while reading the sweeps for the consumer contract work (9 September).
None is a defect and none was fixed in that pass — they are recorded so the next person does
not rediscover them.

| # | What | Why it was left |
| --- | --- | --- |
| 28 | `ApiAuthorizationMatrixTests:159-161` says "122 routes are mapped today". It is **120**. | The assertion is a `>= 100` floor, so the number in the comment is decoration that nothing checks and it has already drifted. Either correct it or make it an assertion; a counted number that nothing counts goes stale exactly like a hand-kept route list. |
| 29 | `OpenApiContractTests:154-167` is a **fourth** copy of the handler-mapping walk | `MappedRoutes` exists to remove that duplication and its javadoc names the three it consolidated; this one was missed. It is also not equivalent: it has no `verbs.isEmpty()` branch, so it drops verb-less mappings entirely where `MappedRoutes.of` reports them with a `null` verb. Moving it is a refactor of a green test, which is not this pass's work. |
| 30 | `MappedRoutes.hasPathVariable()` has no call site | Dead since the three sweeps were consolidated. Deleting it is right, but it is the kind of thing worth checking against the next sweep that needs it rather than removing on sight. |
| 31 | `ApiAuthorizationMatrixTests.statusOf:379-385` throws a bare `IllegalArgumentException` for any verb outside GET/POST/PATCH/DELETE | No `@PutMapping` exists in `src/main` today, so it is unreachable — but that method now takes routes from a generated list rather than a hand-written one, which is exactly the change that makes an unhandled verb arrive. **The intended fix, for whoever picks it up: a message naming the verb and the route**, so the failure says "PUT /x is not probed here" instead of an exception with no subject. |

## Found on 9 September, recorded rather than fixed

| # | What | Why it was left |
| --- | --- | --- |
| 32 | **F-42 — deactivating a catalogue row hides it from the only list the panel reads.** `GET /data/lectures` and `GET /data/professor` filter to `active = true` and the panel reads only those; the `/all` pair that returns inactive rows is recorded in the panel's own contract as *"Not called"*. So `active: false` from the catalogue form removes the row from every panel screen, and the `PATCH` that would undo it needs an id that is no longer on any screen. | **How it was measured:** by reading `LectureService.getLectures(includeInactive)`, `ModerationCatalogController:38-40` (which says the `/all` routes exist so a deactivated row stays "reachable from the panel to correct or reactivate"), `LectureModerationService:108` and the called-route list in `adminweb-consumer-contract.md`. **No test was run and none is written** — see below. **Why it was deferred:** the fix is in the panel repository and this pass wrote no code; the backend already serves everything the fix needs. **Estimate:** ~1 hour in the panel (point the catalogue list at `/all`, add an All/Active/Inactive filter), zero in the backend. **Agreed for the panel's deferred list, after submission.** → `docs/adminweb-tasks.md` task 2b. **Re-measured on 10 September, this time against the deployed host rather than by reading:** the unprefixed twins `GET /data/lectures/all` and `GET /data/professor/all` already exist (`ModerationCatalogController:75-86`, added by `5a11d29`), are admin-only in the chain (`SecurityConfig:143-147`), answer `401` JSON from this backend on `https://ratemyprofessor.dev` where the `/admin` twins answer `200 text/html` from the panel's nginx, and are pinned by `AdminApiIntegrationTests:1624-1643`. So **the option that looked cheapest was already shipped**: the backend cost is documentation only, and the panel is waiting on nothing. Three options were costed in §2b (read the twins / fix the host routing / `?includeInactive=true`) and **option A was taken on 10 September**; B and C are rejected, with their reasons on that page. **The backend's whole share is documentation and it is done:** `admin-api.md` lists both paths for each `/all` read and names the reachable one, the *"Not called"* row in `adminweb-consumer-contract.md` records the decision, and §2b now carries the instruction the panel works from. **No `CHANGELOG` entry** — no client-visible behaviour changed. One correction fell out of it: this page previously told the panel to read the `/admin/...` form once task 2 was done, and task 2 is withdrawn. **Still open, and only in the panel:** the catalogue list and the All/Active/Inactive filter, after submission. `ConsumerContractSweepTests` goes red when their contract document gains the two rows — the `admin-web` count 42 → 44 and `UNCLAIMED_BY_EITHER_CONSUMER` 60 → 58 — and that red is the backend's cue to move both numbers in one commit. |
| 33 | **F-43 — the deployed host serves the panel at `/admin/`, so every `/admin/**` API route is unreachable in production.** `/admin/` is routed to the panel's static nginx, which answers every path under it with `index.html`. `GET /admin/auth/me` and `GET /admin/system/status` come back `200 text/html` instead of `401` JSON, and `POST /admin/auth/login` comes back `405` from nginx. | **How it was measured:** by curl against the deployed host on 9 September, with the response headers used to name the serving layer (`no-store`/`DENY`/`same-origin` is the panel's nginx template, `vary: Origin`/`pragma: no-cache` is Spring) and `GET /healthz` used to show the split is made upstream of the panel container. **Nothing is broken today** — the panel calls the legacy root paths only, and the deployed bundle contains no `/admin/` prefix. **Why it was deferred:** the routing rule is on the deployment host and is not in any of the three repositories; the direction (move the panel off `/admin/`, or put the API behind `/api`) is a deployment decision, not a code change. **Hazard while it stands:** `docs/adminweb-tasks.md` tells the panel to move every call onto `https://<host>/admin` (task 2) and its auth calls onto `/admin/auth/**` (task 1) — doing either would break the panel completely. **Decided 10 September: the routing stays as it is** — the panel keeps `/admin/`, this backend keeps its unprefixed root paths, and the `/admin/**` twins are unreachable in production by design. What forced it was F-44: fixing the panel's login needed same-origin calls, which the host already routes, whereas `/api/**` would have needed a new prefix-stripping rule on an nginx that is version controlled nowhere. **Both migration tasks are withdrawn, not deferred**, and `docs/adminweb-tasks.md` carries the decision and its reasoning so nobody revives them from the task list alone. |
| 34 | **F-44 — the deployed panel could not be logged into at all.** Its bundle was built with an absolute `VITE_API_BASE_URL` pointing at the bw-cloud hostname, so calls from `https://ratemyprofessor.dev` were cross-origin; `ADMIN_FRONTEND_ORIGINS` did not contain that domain, and the preflight was refused `403` before any controller ran. | **How it was measured:** by curl against both hostnames, plus grepping the deployed bundle for the baked-in host. A preflight carrying the page's own origin answered `200` while one carrying a stranger's answered `403` on the same host with the same list — which is what showed a same-origin caller never reaches the check, and disproved a claim `docs/admin-api.md` was making in writing. **Fixed, not deferred:** the panel now builds with an empty base so its calls are same-origin, and `ADMIN_FRONTEND_ORIGINS` narrows to `https://ratemyprofessor.dev`. **Deliberately not done:** the bw-cloud hostname stays served — the released Android client is compiled against it (`RetrofitClient.java:31`), and both names resolve to one address, so closing it needs a released client that points elsewhere first. |
| 35 | **F-45 — nothing verifies that what is deployed is what was built.** The panel's `web:deploy` smoke check asserts `/admin/` answers `200`, which the previous container also does, so a deploy that did not happen and one that did are indistinguishable from outside. The backend's scheduled `health:check` has the same shape. | **How it was measured:** by curling the live bundle after the fix was committed and getting the pre-fix file back — identical asset hashes, `index.html` `last-modified` older than the commit, the bw-cloud URL still in the chunk, caching ruled out with `no-store` plus a cache-buster. **Why it was not fixed:** the job is in the panel repository and this pass changed no code. **What would close it — the recipe, not the direction.** In `web:deploy`, after the container swap, read the asset name out of the served page and compare it to the one just built:

```
built=$(docker run --rm --entrypoint sh "$IMAGE" -c 'ls /usr/share/nginx/html/admin/assets/index-*.js' | xargs -n1 basename)
served=$(curl -s "http://127.0.0.1:${WEB_PORT:-8081}/admin/" | grep -oE 'index-[A-Za-z0-9_-]+\.js' | head -1)
test "$built" = "$served" || { echo "served $served, built $built"; exit 1; }
```

The hash is content-derived, so this fails exactly when the container is not the image just built — which the `200` check cannot see. **Estimate:** ~30 minutes in the panel repository (write it, then prove it goes red by deploying without the swap), plus ~15 minutes to give the backend's scheduled `health:check` the same treatment against a build id. **Deliberately not done tonight** — the delivery work took priority and this is the check that would have saved the hours, not the thing being delivered. |

## Found on 10 September, recorded rather than fixed

| # | What | Why it was left |
| --- | --- | --- |
| 36 | **F-46 — there are two soft-deletes and only one of them anonymises, so self-deleted accounts are invisible, unaudited, and silently restorable.** `PATCH /account/deleteAccount` goes through `StudentService.softDeleteByKitEmail:205-214`, which sets `status = DELETED` and nothing else: no scrub, no `deletedAt`, and **no audit entry at all**. The address therefore stays intact, and `LoginCodeService:76-79` flips such an account back to `ACTIVE` when a login code is requested for it. Undelete already exists in production, uncontrolled and unrecorded. The admin path (`StudentLifecycleService.deleteStudent`) is unaffected: it scrubs the address to `@invalid.local` in the same transaction, **so the scrub is what is currently containing this branch** — which is the reason it is a precondition for [ADR-0015](adr/0015-deletion-not-split-from-anonymisation.md) ever being revisited. | **How it was measured:** by reading both delete paths and following `findByKitEmail` into the reactivation branch; the asymmetry is visible in `StudentService:211-212` against `StudentLifecycleService:85-93`. **The most instructive part, and it is P-2's shape:** `grep` over `src/test` shows **no test reaches `LoginCodeService:76-79`**, while `SessionIssuerTests:516-525` pins the neighbouring rule — a `DELETED` account cannot complete a login — and reads, from its name and its subject, as though it covered this. [P-2](test-findings.md#p-2--integration-tests-hide-unit-targets) is a test elsewhere making a unit look examined when it is not; there it fooled a coverage counter, here it fooled a reader, which is the harder half to catch because no column reports it. **Why it was deferred:** found while measuring the panel's deleted-accounts request, a pass scoped to measurement and options — see `docs/adminweb-tasks.md` §6. **It touches the account lifecycle, so it is deliberately not fixed before submission:** **the reactivation is deliberate, and that sharpens this record rather than weakening it.** Commit `bf86ee6`, "Deleted User can login again", is where the branch was added on purpose, so the intent — signing in undoes your own deletion — is settled and is not what is being reported here. **The defect is that it is unaudited and invisible:** it writes no audit entry, the account it restores was never visible to an operator in the first place, and no test reaches the branch, so nothing records that this is the behaviour or would notice it changing. Whether that intent should survive contact with the panel's new deleted-accounts list is the product call nobody has made. **Estimate: ~4 hours** — a characterization test on the branch first, since nothing pins it today and it cannot be inverted until it is pinned; then either an audit entry plus admin visibility, or removal of the branch, plus a `CHANGELOG` entry if the behaviour goes. **Blocks nothing in option A**, which shipped; visible on the panel's new list as a `DELETED` row carrying a real username and no matching `USER_DELETED` entry.  **Measured on 10 September (second pass), then FIXED — the record half only.** Both absences were confirmed first (neither service held an `AuditWriter` at all), then `USER_SELF_DELETED` and `USER_SELF_REACTIVATED` were added: administrative scope, non-revertible, new constants rather than `USER_DELETED` reused. `CHANGELOG` `10.09 (20)`, [ADR-0016](adr/0016-self-service-lifecycle-recorded-not-authorised.md), and "F-46, measured and closed" below the table. **The reactivation itself is untouched and is now item 39 (F-48)** — measuring this found that the missing record was the smaller half of the problem, and that F-46's severity as written understated what the reactivation actually is. |
| 37 | **F-47 — `active = false` is honoured by exactly two queries; every other route serves and mutates the row unchanged.** Deactivation is a list filter, not a lifecycle state. `GET /data/lectures/{id}` (`LectureService.getLecture:96-98`, a plain `findById`) and `GET /data/professor/{professor_id}` (`ProfessorService.getProfessor:82-84`) answer `200` for a deactivated row to an **anonymous** caller — both are in `ApiAuthorizationMatrixTests.PUBLIC_ROUTES:98-101`. `RatingService:72` resolves the rating target through `lectureService.getById` with no active check, so a deactivated lecture can still be rated and commented on. And `LectureResponseMapper:63-66` sorts `lecture.getProfessors()` without filtering, so a professor who has left `GET /data/professor` is still nested in every active lecture they teach and still inside the generated `title` prose. | **How it was measured:** by grepping `isActive()`, `setActive(` and `ActiveTrue` across `src/main/java` — seventeen lines, and the only two that gate anything are the active-only list queries themselves (`findByActiveTrueOrderByNameAsc`, `findByActiveTrueOrderByLastNameAscFirstNameAsc`); everything else writes the flag, audits it, or copies it into a response. Then by reading each caller of the ungated reads. **Nothing was observed misbehaving in production:** curled on 10 September, the public catalogue serves 92 lectures and 132 professors and **not one row carries `active: false`**, nested professors included — the switch has never been used on the deployed data, which is also why F-42 has never actually cost anyone a row. **Pinned as characterization, deliberately not fixed:** `LectureApiIntegrationTests.getLectureByIdStillServesADeactivatedLectureToAnAnonymousCaller`, `ProfessorApiIntegrationTests.getProfessorByIdStillServesADeactivatedProfessorToAnAnonymousCaller`, `RatingServiceTests.submitRatingIsAcceptedForADeactivatedLecture`, `CommentServiceTests.submitCommentIsAcceptedForADeactivatedLecture`, `LectureResponseMapperTests.toResponse_deactivatedProfessor_staysInTheListAndInTheTitle`. Each was written inverted first and seen red before being flipped — the reds were `404 expected but was 200` twice, `[nothing was thrown]` twice, and `["Abt=false", "Sanders=true"] to contain exactly ["Sanders=true"]` — so none of them is a test that passes by looking at nothing. **Why it is not fixed:** the three consequences are not the same kind of thing until the product question in item 38 is answered, and pinning first is what makes the answer cheap either way. **Estimate once decided:** the pins are written, so the remaining cost is the rule — roughly half a day for the *retired* reading (a filter on two reads, a guard on two writes, five assertions inverted, a `CHANGELOG` entry), and nothing at all for the other. **Relevant to F-42 now**, because the panel is about to make "inactive" a state an operator can see and choose. → `docs/test-findings.md` F-47, `docs/adminweb-tasks.md` §2b. |
| 38 | **The product question behind F-47: does `active = false` mean *hidden from the catalogue*, or *retired*?** Nobody has answered it, and until somebody does the three behaviours item 37 pins are not the same kind of thing. **Hidden from the catalogue** is a display rule: the current behaviour is then right in all three places, F-47 closes as working as intended, and only the documentation changes. **Retired** is a lifecycle state: (1) a row nobody can find is still readable by id by an anonymous caller and (2) it is still collecting new ratings and comments — (2) being the one that produces *new* data on a row an operator has deliberately taken out of circulation. | **The distinction worth keeping, and the reason this is its own item rather than a sentence inside 37:** the three are not equally suspect. **(3) is likely deliberate either way** — who taught a lecture is a fact about the past, filtering a departed professor out of the lecture they actually taught rewrites it, and the generated `title` is prose that cannot carry a flag. **(2) reads as an oversight**: nothing in `RatingService` or `CommentService` says the question was considered, and both simply check that the lecture exists. Writing this down is the point — a later reader who finds only "F-47: three behaviours, unfixed" would have to re-derive which of them anyone actually meant. **Who decides:** not the backend alone. The panel's Inactive view (F-42) is what turns this flag from something nobody sets into a state an operator chooses, so the answer changes what that view means — an Inactive row probably should not be presented as something students can still reach if the answer is *retired*. **Decision after submission.** Blocks nothing: the behaviour is pinned either way, and whichever answer comes, the five tests in item 37 are inverted rather than deleted. → `docs/adminweb-tasks.md` §2b. |
| 39 | **F-48 — an unauthenticated caller can undo somebody's account deletion.** `LoginCodeService.requestLogin` sets a `DELETED` account back to `ACTIVE` when a login code is **requested** for its address, not when one is entered, and `POST /auth/request-login` is public (`SecurityConfig:52`, `ApiAuthorizationMatrixTests.PUBLIC_ROUTES:93`). Anyone who knows a KIT address that deleted itself can revive that account with one anonymous request, having proved nothing; the flip commits before the code is mailed, so it survives a delivery failure answered `500`. **Not account takeover** — the code still goes to the KIT address, so the caller cannot sign in. **What it does reverse:** `SocialResponseMapper:70,111` put the person's real username back on every comment and answer they wrote, `RatingAverages:85` and `LectureResponseMapper:73` put their ratings back into the public averages, and `UserDirectoryService:119` takes the row off the panel's `status=DELETED` list. A third party can undo somebody's decision to be forgotten. | **How it was measured:** by reading `LoginCodeService:63-84` line by line rather than summarising it, then following `UserStatus.DELETED` to all seventeen sites that consult it. **Opened as its own number** rather than left under F-46: F-46 described an operator's blind spot, and this is a missing authorization check. **Pinned as characterization, not fixed** — `AuthApiIntegrationTests.anAnonymousLoginCodeRequestBringsBackAnAccountThatDeletedItself`, `LoginCodeServiceTests.requestLoginRevivesADeletedAccountBeforeTheCodeIsEvenIssued` and `...theRevivalOfADeletedAccountOutlivesAFailureToSendTheCode`, each seen red first and each written to be inverted. **The behaviour is deliberate in origin** (commit `bf86ee6`, "Deleted User can login again"); what was never decided is that it happens before anyone proves they hold the address. **Mitigated but not contained** by the request-code rate limits — they bound the rate, not the effect. **Four options costed below the table** (move the flip to a completed login ~4–6 h and inverts `SessionIssuerTests:516-525`; remove the revival ~1–2 h; a mailed confirmation ~1–2 days; or leave it, now that it is recorded). **Decision after submission, and it is a product call.** |
| 40 | **F-49 — the deploy job writes `SPRING_MAIL_HOST` with no default, and an empty value beats the fallback.** `.gitlab-ci.yml:319` is `write_env SPRING_MAIL_HOST "${SPRING_MAIL_HOST}"` — alone among its neighbours, which all carry one (`:320` `587`, `:323` `true`, `:324` `true`). `write_env` (`:282-287`) writes the key unconditionally, so an unset CI/CD variable produces `SPRING_MAIL_HOST=""` in `.deploy.env`, `docker-compose.prod.yml:27` passes it through, and Spring's `${SPRING_MAIL_HOST:localhost}` **only falls back when a variable is absent, not when it is empty**. The result would be a deployment where nobody can log in — a login code is the only way in — with nothing failing at startup to say so. | **How it was measured:** by reading the deploy script's variable block while wiring the local mail catcher, then following the value through `write_env`, the prod compose file and the property placeholder. **This is not a new hazard, it is a known one with a gap in its coverage:** `.gitlab-ci.yml:234-239` writes the rule out in full — *"an empty value silently wins over the defaults in application.properties. That is how the panel ended up with an empty CORS allow-list and answered every browser call with 403"* — and both variables named in that comment were given explicit defaults. The mail host has the same shape and was not. **Latent, not live:** production mail works, so the variable is set today; this is F-30's kind of finding, a landmine under an unrelated change — the day somebody clears or renames that CI/CD variable, logins stop and the cause is three files away. **Why it is deferred:** it is a one-line change to a deploy job that cannot be tested from here (private GitLab, no token), and this pass was scoped to the Abgabe list. **Estimate: ~15 minutes** — give `:319`, `:321` and `:322` explicit defaults the way `:320` has one, then watch one deploy job. **Not fixed, by the rule that a new defect gets a number rather than a patch.** |
| 44 | **F-50 — the GitLab issue success path answers `409 State conflict`, and the issue stays open in the tracker.** `POST /admin/reports/{id}/gitlab-issue` opens the issue and then tells the operator it failed, on the **first** call. The chain, in order: the `isEnabled()` check first (`ModerationBugReportService:274`), then `@Transactional` opens (`BugReportIssueTransactions:64`), the row is locked (`:66-67`), the already-created guard is skipped because the row is still `NONE` (`:69-71`), **the HTTP POST to GitLab** runs (`:73-74`), and only then are the three issue fields written (`:76-79`) and the audit entry flushed (`:81-89`). A `DataIntegrityViolationException` on one of those two writes reaches `GlobalExceptionHandler:166-171`, which is the only place in `src/main/java` that produces this body. **The GitLab call shares the transaction with the write**, so the rollback undoes the database and cannot undo the HTTP call — the issue exists with nothing pointing at it. **And `markFailed` never runs:** `DataIntegrityViolationException` is not an `ApiException` and `ModerationBugReportService:279` catches only that, so the row stays `NONE`/`issueUrl = null`, the panel keeps offering the button, and **every further click opens another issue and answers 409 again.** | **How it was measured:** by reading both files line by line after the deployment returned the 409, then grepping `CONFLICT` across `src/main/java` — neither file on the path throws it, and `GlobalExceptionHandler:166-171` is the sole source of the string. **`BugReportIssueTransactions:22-27` was written to close exactly this trap and closes half of it** — the two-transaction split is right, and routing it through a `catch` on `ApiException` alone covers the adapter's deliberate `502`/`503` while leaving a hole under every `RuntimeException` the persistence layer raises after GitLab has been called; the comment will read to the next person as though the case were handled. **The status hypothesis was refuted, and that is recorded as a negative result** in `test-findings.md`: issue creation neither reads nor writes `report.getStatus()`, `ReportStatus` is not imported by the file, and `BugReport` has no lifecycle hook to hide a transition in. **Why the suite is green:** the fake tracker invents its payload (`TestGitLabConfig:44`), and — the larger reason — `EndToEndJourneyTests:260-261` exercises this exact path against real PostgreSQL built from `V1` and passes, so **the constraint that fires is not in `V1`** (item 45). **Blocked on one measurement, which is not available from here:** the production log line (`GlobalExceptionHandler:168` logs the stack trace, and the `PSQLException` message carries the constraint name) or `\d bug_reports` / `\d audit_logs`. **Estimate: ~15 minutes to name the constraint** once either arrives, then **~3–4 hours to fix**: widen the `catch` to the persistence failure so `markFailed` runs, move the GitLab call out of the transaction that writes, add the integration test asserting the row lands `FAILED` rather than `NONE` and that the response is not a `409`, and a `CHANGELOG` entry because the status a client sees changes. **Not fixed, by the rule that a new defect gets a number rather than a patch** — and deliberately not guessed at while the constraint is unnamed. → `test-findings.md` F-50. |
| 45 | **F-51 — nothing verifies the production schema, and it is not the schema in this repository.** Four facts combine: (1) the deployed database was **baselined** on 2026-09-05 (`deployment.md:61`, `:358`), and Flyway's baseline records a version without executing it, so `V1__existing_schema_baseline.sql` has never run against production; (2) the production schema is therefore the residue of the pre-Flyway `ddl-auto=update` era, recorded in no file; (3) `ddl-auto=validate` (`application.properties:24`) checks that tables and columns *exist* with compatible types and does **not** check length, nullability, defaults, unique indexes or check constraints, so a divergent production column starts the application clean; (4) the schema the tests actually execute is a **second, hand-maintained baseline** at `src/test/resources/db/migration/h2/V1__existing_schema_baseline.sql`. **Consequence: every layer above unit proves its assertions against a schema that exists nowhere but CI.** F-50 is the visible end of it. | **How it was measured:** by diffing the two baselines line by line — they differ in **exactly one place**, `audit_logs.changes` / `.metadata` `jsonb` against `json`, which is a deliberate translation and not drift; `bug_reports` is identical in both and carries no unique or check constraint on the issue columns. Then by reading `PostgresTestDatabase:64-66` to establish what the PostgreSQL layer does verify. **Scope stated so it is not overread:** `PostgreSqlMigrationSmokeTests` runs the production migration directory against a real PostgreSQL 16 with `baseline-on-migrate=false` and `ddl-auto=validate` and asserts `/health` is green — that genuinely proves **`V1` is self-consistent with the entities**, and it is not a comparison against the deployment, which no test here can make. **The sharpest evidence that the two differ:** `EndToEndJourneyTests:260-261` opens a GitLab issue over F-50's exact path against real PostgreSQL built from `V1` and passes, while production refuses the same write. **A stale comment recording the confusion:** `BugReport.java:88-93` still says *"Production runs `ddl-auto=update`"*; production runs `validate`. Its reasoning is a correct account of the era the column was added in and its conclusion still holds — only the tense is wrong, and it is the only place in `src/main/java` that describes production schema management at all. Recorded rather than edited, so it lands with whoever reconciles the schema. **Estimate: ~1 hour to measure, then unknown until measured** — a `\d` dump or `pg_dump --schema-only` from production diffed against `V1` is an hour; what it costs to correct is whatever the diff shows, and it needs a `V2` (which is also the point at which the forward-only decision in `test-plan.md` should be revisited rather than inherited). **A cheap partial guard worth costing separately, ~2–3 hours:** a scheduled CI job that dumps the deployed schema and diffs it against `V1`, so the answer stops being a one-off. **Why it is deferred:** it needs access this machine does not have, and it cannot be done under an evening deadline. → `test-findings.md` F-51, and `test-plan.md` *Where the suite stops* now states the limit outright. |
| 46 | **F-52 — a constraint violation and a business-rule refusal are the same `409 State conflict` to the client.** `GlobalExceptionHandler:166-171` maps every `DataIntegrityViolationException` to `409 {"message":"State conflict","success":false}` with no `reason` and no detail, so a caller cannot tell a refusal it should show against a field ("this username is taken") from a database accident it caused nothing (F-50's failing write, where the right answer is that the server broke and retrying makes it worse). | **This is the third sighting of the shape, which is why it gets a number rather than a note.** `test-findings.md:295` already records a malformed request coming back as *"an unexplained **State conflict**"* where the caller expected a `400`; `StudentProfileService:227` carries a pre-check placed for this exact reason, with the reason in the comment. Both earlier sightings were handled **at the call site** by adding a guard ahead of the constraint — which works and does not scale, because it needs somebody to anticipate every constraint, and item 45 shows nobody currently can: the constraints in production are not written down. **The third sighting is what turns a pattern of local fixes into a question about the handler**, and recording it now is what stops the next reader adding a fourth guard. **What it is not:** an argument for `ProblemDetail`. The error contract is settled — failure is an exception, the body is `{message, success}` — and this is about that body carrying a message worth reading, and about whether a violation the caller did not cause belongs in `4xx` at all. **It cost real diagnosis time:** the status hypothesis on F-50 was followed first precisely because "State conflict" reads like a state-machine refusal. **Estimate: ~2–3 hours** — decide the split (a caller-caused violation stays `409` with a `reason`; an internal one becomes `500`), then it is a handler change plus assertions at the API sweep layer, plus a `CHANGELOG` entry since the status and body a client sees would change. **Do not start it before item 45**: what the handler should say about a violation depends on knowing which violations are reachable. → `test-findings.md` F-52. |

### F-46, measured and closed — both lifecycle events are recorded now

Measured on 10 September, then closed. **Both halves were confirmed absent before anything was
written:** the chain `AccountController:84` → `AccountService.deleteAccount:59` →
`StudentService.deleteAccount:146` → `softDeleteByKitEmail:205-214` held **no `AuditWriter` at
all**, and neither did `LoginCodeService`. It was absence, not a branch that happened not to be
taken. `deletedAt` — the one field that would have dated a deletion — is set only by the
administrative path and is served by no DTO, so even an admin deletion could not be dated by a
client.

**What was added:** `USER_SELF_DELETED` and `USER_SELF_REACTIVATED`, both `ADMINISTRATIVE`
scope, both non-revertible, written by `StudentService.deleteAccount` and by the new
`AccountReactivator`. The three decisions behind that shape — which log, new constants rather
than reuse, and no revertibility — are in
[ADR-0016](adr/0016-self-service-lifecycle-recorded-not-authorised.md) with the alternatives that
were refused and why. `CHANGELOG` `10.09 (20)`; the panel's rule was rewritten rather than
silently broken in `docs/adminweb-tasks.md` §6.

**Pinned by** `AuthApiIntegrationTests.anAnonymousLoginCodeRequestBringsBackAnAccountThatDeletedItself`
(both entries, through the public routes),
`AdminApiIntegrationTests.selfDeletionAndRevivalAreServedOnTheAdministrativeLogAndNeitherCanBeReverted`
(the client-visible shape, including `revertBlockedReason`),
`StudentServiceTests.deleteAccount_existingStudent_recordsTheDeletionAgainstTheAccountItself`,
and both tests in the new `AccountReactivatorTests`.

**What is not closed is the part that matters more.** Measuring this found that the record was
the smaller of the two problems — see item 39.

### Item 39 — F-48's four options, costed

**The finding itself is written up in [test-findings.md](test-findings.md#f-48--an-unauthenticated-caller-can-undo-somebodys-account-deletion),
which is where it is defined.** What follows is the board's half: what it would cost to close,
and the four ways of doing it. Restated here only as far as the options need it.


**This is an authorization defect, not a record-keeping one**, and it is the reason F-46's
severity as written was wrong. Opened as its own number rather than left under F-46, because
what F-46 described — *invisible, unaudited, silently restorable* — reads as an operator's
blind spot, and this is not that.

**What.** `LoginCodeService.requestLogin` sets a `DELETED` account back to `ACTIVE` when a login
code is **requested** for its address — not when one is entered. `POST /auth/request-login` is
public (`SecurityConfig:52`, `ApiAuthorizationMatrixTests.PUBLIC_ROUTES:93`). So **anyone who
knows a KIT address that deleted itself can bring that account back with one anonymous request**,
having proved nothing. The flip is also committed before the code is mailed, so it survives a
delivery failure that answers the caller `500`.

**What it is not, stated plainly so the severity is not overstated.** It is **not account
takeover.** The login code goes to the KIT address, so the caller who triggers this cannot sign
in and cannot read anything. What they can do is change the account's *state* and what the app
shows about its owner.

**What it actually costs, which is what makes it worth a number.** `UserStatus.DELETED` is
consulted at seventeen sites in `src/main/java`, and going back to `ACTIVE` reverses the reads:

- `SocialResponseMapper:70` and `:111` render a `DELETED` author as `"Deleted User"`, so **the
  person's real username returns on every comment and answer they ever wrote**.
- `RatingAverages:85` and `LectureResponseMapper:73` exclude a `DELETED` user's ratings, so
  **their ratings return to the public averages**.
- `UserDirectoryService:119` excludes them from `GET /users`, so the account **leaves the
  panel's `status=DELETED` list** and rejoins the ordinary one.

So a third party can undo somebody's decision to delete their account and put their name back
into the public app. If the person deleted the account because they wanted to be forgotten, that
is the whole of what they asked for, reversed by someone else.

**Mitigation that exists today:** the request-code rate limits (`request-email` 3,
`request-ip` 20 per 15-minute window) bound how many addresses one source can do this to. They
bound the rate, not the effect — one call is enough for one account.

**Not fixed, by instruction, and the behaviour is deliberate in origin:** commit `bf86ee6`,
"Deleted User can login again", added the revival on purpose. What was never decided is that it
happens before anyone proves they hold the address. **Pinned as characterization** by
`AuthApiIntegrationTests.anAnonymousLoginCodeRequestBringsBackAnAccountThatDeletedItself`,
`LoginCodeServiceTests.requestLoginRevivesADeletedAccountBeforeTheCodeIsEvenIssued` (the
ordering — the account is back before a code even exists) and
`LoginCodeServiceTests.theRevivalOfADeletedAccountOutlivesAFailureToSendTheCode`. All three
invert rather than delete when this is answered, per
[ADR-0007](adr/0007-characterization-and-inversion.md).

**The decision is a product one. Three options, costed.**

| | What changes | Cost | Client-visible? |
| --- | --- | --- | --- |
| **A. Move the flip to a completed login** | `AccountReactivator.reactivate` is called from `SessionIssuer` after the code is verified instead of from `requestLogin`. `requestLogin` must then issue and send a code to a `DELETED` address while leaving the status alone — today it returns early without issuing, so that early return has to learn the difference between "blocked" and "deleted". | **~4–6 h.** Two files plus the branch in `SessionIssuer` that currently refuses a `DELETED` account: `SessionIssuerTests:516-525` pins *"a `DELETED` account cannot complete a login"* and would be **inverted**, which is the assertion to look at before estimating this at less. | **Yes.** `POST /auth/login` starts succeeding for a deleted address. `CHANGELOG`. |
| **B. Remove the revival** | The branch goes. A deleted account stays deleted, and its owner cannot bring it back — deletion from the app becomes as final as deletion by an admin, which is what [ADR-0015](adr/0015-deletion-not-split-from-anonymisation.md) already decided for the administrative path. | **~1–2 h.** Delete the branch, invert the three characterizations, and `AccountReactivator` goes with it. | **Yes**, and it is the largest behaviour change of the three: an app feature that exists today disappears. `CHANGELOG`. |
| **C. Keep it, behind a confirmation** | The address is mailed a link or a second code that performs the revival, so the account comes back only when somebody demonstrably reads mail at that address. | **~1–2 days.** A new one-time-password purpose or a new route, its own rate limit, its own expiry, and a delivery template. | **Yes**, plus a new route or a new request shape. |
| **D. Do nothing further** | The behaviour stands, and is now recorded: every revival writes `USER_SELF_REACTIVATED` with `callerAuthenticated: false`, and an operator can see it on `GET /audit-logs`. | **0.** Done. | No. Already shipped under `10.09 (20)`. |

**Recorded inclination, not a decision — Alparslan, 10 September: A.** Written down so the
decision after submission starts from a position rather than from a blank page, and marked as an
inclination so nobody reads it as the call having been made.

> B destroys a feature that exists in the app today. C is two days. D is "we recorded it and did
> not fix it". **A is the existing intent implemented in the right place** — the commit that
> added this behaviour is called *"Deleted User can login again"*, so signing in undoing your own
> deletion is what was meant; it was simply applied one step too early, at the request rather
> than at the login. Doing it means inverting `SessionIssuerTests:516-525`, which currently pins
> that a `DELETED` account cannot complete a login.

**This is an inclination and the decision is still open.** Nothing has been implemented, and the
other three rows stay on the table with their costs.

**A is the shape the existing code is closest to** — the revival was clearly *meant* to be
"signing in undoes your own deletion", and A is that sentence implemented — but it is not free,
and the `SessionIssuer` assertion it inverts exists for a reason worth reading first. **B is what
ADR-0015 implies** if deletion is meant to be final on both paths. **C buys ownership proof
without giving up the feature**, at several times the cost of either.

**Deliberately not decided here.** Recorded for the decision after submission; the three options
are costed so the decision is taken on numbers rather than on the first idea.

## Waiting on other people

| # | What | Where |
| --- | --- | --- |
| 23 | ~~The panel migrating to `/admin/**` — and then the legacy surface can be deleted~~ **Withdrawn 10 September, not deferred.** The deployed host routes `/admin/` to the panel's own container, so the migration would have aimed every panel call at a static file server (F-43), and the decision taken was to leave the routing as it is. The unprefixed paths are the production contract permanently and **the legacy surface is not being deleted** — `docs/adminweb-tasks.md` marks both migration tasks withdrawn, and `docs/admin-api.md` now says so where it used to promise the opposite. Nothing is waiting on the panel here any more. | [Waiting on other people](#waiting-on-other-people) |

## One thing that is not a task

The 9 September work landed on **`main`**, not on a branch: the local checkout was moved to
`main` partway through and eleven earlier commits had already been pushed to `origin/main`
directly. `refactor/single-responsibility` still points at `6edde54` and is now behind. Nothing
is lost and nothing needs fixing in the code — but if this project is supposed to go through
merge requests, that is the convention to put back, and it is easier to do now than after the
next branch is cut from either one.

# TODO — Backend tests & Kontrollphase

Last updated: 2026-09-08
Owners: Alparslan (testing side), Luis (parallel batch)

## Pending — today

- [x] Were the batch 1 test files + `docs/test-plan.md` committed? (they were sitting in the working tree)
- [x] Check whether `warnIfTokenMissing()` throws an NPE on a null token
      → it is `@PostConstruct`, so an NPE means the application never starts at all
      → the token is `@Value("${ipinfo.token:}")` with a default, so it cannot be null
- [ ] Is `IPINFO_TOKEN` set on the VM? (ssh, not a repo job)
      → probably the root of the "Approximate location funktioniert nicht mehr" bug
      → `GET /system/status` now answers this: `locationLookupEnabled` (F-8)
- [x] Tell the team: use `./mvnw clean verify` for any coverage number you intend to quote.
      Not for the reason this line used to give — `jacoco:report` is on the `test` phase and
      `jacoco:check` on `verify`, so `test` does refresh the report. The real trap is that the
      agent **appends**: `target/jacoco.exec` accumulates across runs, so a `verify` after a
      targeted `-Dtest=` run reports more coverage than the suite earns on its own. That is
      P-5, and it is what made the numbers below wrong.
- [x] Split the work with Luis: revert handlers (7 classes) vs StudentService/AccountService

## Tests — all four layers: done

Seven unit batches, the three API/role sweeps, the contract layer, the integration layer and
six end-to-end journeys. Every finding they raised is closed.
**975 tests, 0 failures**, 96.65% LINE / 93.28% BRANCH on `./mvnw clean verify`. The detail
lives in [test-plan.md](test-plan.md) — this file only tracks what is left.

The figure under *8 September* below says 876 and is left alone: it is a record of that day, not
a claim about today. This line is the one that has to keep up.

- [x] Build: `jacoco:report` on the `test` phase, `jacoco:check` on `verify`
- [x] `jacoco:check` ratcheted 0.85 → 0.90 LINE (mandatory) with a 95 advisory tier in CI,
      and BRANCH to 0.87
- [x] `com.pse.audit.revert` — 7 handlers, `StudentService`, `AccountService`,
      `SocialService` votes, the moderation services, the GitLab adapter, the security path
- [x] **API / role-permission matrix tests** (MockMvc + `@SpringBootTest`) — three sweeps,
      route list read from Spring's own mapping rather than kept by hand
      → the IDOR sweep found nothing; the negative result is written up
- [x] Close every open finding — P-3 is still the only one closed as a decision rather than a fix
- [x] **Batch 7** — `AuditLogService` (which had no unit test class at all), the warning
      lifecycle in `ModerationUserService` (which had none either) and `SocialService`'s
      response builders. 39 tests; BRANCH 89.15% → 90.61% and the pom floor 0.87 → 0.88

## Tests — next steps (in priority order)

- [x] **SonarCloud** setup — Maven plugin + CI, let it consume the JaCoCo XML
      → done on the repo side: `sonar-maven-plugin` is in the pom, pointed at the same
        JaCoCo XML `jacoco:check` reads, with the same two exclusions
      → the `sonarcloud` job runs **only when `SONAR_TOKEN` exists**, so nothing changes
        until somebody creates the project. `allow_failure: true` for now, deliberately:
        the first scan raises hundreds of issues and none of them are regressions
      → **needs a person, but only for the account:** create the SonarCloud project, then set
        `SONAR_TOKEN`, `SONAR_PROJECT_KEY` and `SONAR_ORGANIZATION` in GitLab → Settings →
        CI/CD → Variables. Documented in [deployment.md](deployment.md#gitlab-cicd-variables)
      → **the analysis itself is verified (9 September).** The job's `SONAR_HOST_URL` default
        makes it point anywhere, so it was run against a SonarQube in a local container: the
        pom's configuration works, and Sonar reads the same JaCoCo XML `jacoco:check` does —
        it reported 95.8% against our 96.65%, the gap being Sonar's own exclusions. So the
        manual step is now only "create the project": nothing is waiting to be debugged after
        somebody does it.
      → **and the first scan's numbers are already known**, which is what the "hundreds of
        issues" warning below was guessing at: 6 bugs, 1 vulnerability, 0 hotspots, 350 code
        smells, 0.7% duplication. All seven bugs and vulnerabilities were triaged — none is a
        defect, two were worth documenting, and the reasoning is in
        [F-30](test-findings.md#f-30--a-rate-limiter-that-is-only-correct-because-a-clock-in-another-file-is-utc).
        Of the 350 smells, 55 were dead imports and those are gone; the rest are test-style
        opinions (111 `isZero()`, 87 assertion lambdas) and are the team's call, not a
        regression among them.
- [x] **Contract**: generate an OpenAPI schema + validate the responses against it
      → springdoc v3 (the line that supports Spring Boot 4) + Atlassian's
        `swagger-request-validator-mockmvc`, not the restassured one — this suite is MockMvc
      → found three things: the schema was being served anonymously, the route sweep was
        blind to any dependency's routes, and six response fields were declared non-nullable
        that are null by design. See test-plan.md, "The contract layer"
- [x] **Integration**: was waiting on a team/tutor decision about Testcontainers
      → not needed. The pipeline already runs a real `postgres:16` as a GitLab CI *service*,
        and a service needs no Docker socket and no dependency. `PostgresTestDatabase` is
        that setup generalised; tests opt in with `@PostgresIntegrationTest` and skip
        locally, so `./mvnw verify` is still Docker-free
      → six classes, 24 tests, **one shared Spring context** — the CI job now fails unless
        exactly one context started, because two would reset the schema under each other
      → three results: BUG-1's timestamp hypothesis ruled out, then BUG-1 itself ruled out on
        all four cursor endpoints, and no second F-9 across 47 readable routes
      → one finding of its own: P-5, the coverage numbers above were measured on a dirty
        `jacoco.exec`
- [x] **E2E**: six journeys, over real HTTP on a random port against the same PostgreSQL
      → signup+login / student main flow / admin CRUD+audit+revert / unauthorised access /
        which login decides the session clock / bug report reaching the tracker
      → the client is `RestClient`: `TestRestTemplate` is **not on this classpath** (Boot 4
        reorganised the test starters) and `WebTestClient` would drag in WebFlux
      → runs as a **second Maven invocation** of `server:postgres-integration`, because it needs
        a different Spring context and two contexts against one database wipe each other
      → journey 5 replaces the two-minute deployment experiment: the property now lives in a
        test instead of a setting somebody has to remember to change back
      → no defect found. Two design facts pinned instead: requesting a login code creates no
        account (redeeming it does), and an anonymous refusal is not audited

## Known risks / decisions

- [x] BRANCH — was 72.1% and deliberately not tracked; it is 89.33% and gated at 0.87 now
- [x] `LoginLocationService` broad `catch (Exception)` — narrowed (F-8), so 401/403, 429 and
      a timeout are distinguishable in the log
- [x] If the token is revoked mid-life the startup warning misses it — `locationLookupEnabled`
      in `GET /system/status` covers the whole life of the process (F-8)
- [x] Test style inconsistency: JUnit Assertions vs AssertJ
      → closed. 479 call sites across 32 files converted; no `org.junit.jupiter.api.Assertions`
        import and no JUnit assertion call remains in `src/test/java`
      → the risk in a conversion like this is silent: `assertEquals(expected, actual)` becoming
        an inverted `assertThat(expected).isEqualTo(actual)` still **passes**, and only misleads
        whoever reads the failure later. A green suite proves nothing about it, so all 305
        order-sensitive sites were checked structurally against the original — old
        `(expected, actual)` had to appear as new `(actual, expected)` — and three files were
        additionally mutation-checked to read the failure text

## Outside testing — until Sunday (13.09)

**Funktionalität**
- [x] Lecture rating title: "SS26 Algorithmen 1" → "SS26 Algorithmen 1 —
      Peter Sanders, Übungsleiter 1, Übungsleiter 2..."
      → the data was already in the payload; what was broken is that it had no order.
        `LectureResponse.professors` is built from a `Set` and came back in a different
        order between two requests, so any title built from it reshuffled — F-21, the read
        side of BUG-3. Sorted now: last name, first name, id.
      → `LectureResponse` also carries `semesterLabel` ("SS26", "WS25/26") and the finished
        `title`, so the winter-semester format lives in one place instead of in three
        clients. `semesterYear` on a winter row is the year it **starts** in — say so if the
        data means otherwise.
      → **one part is not backend-doable:** the example puts the lecturer first and the
        Übungsleiter after. `lecture_professors` records only that a person teaches a
        lecture, so nothing can tell them apart. The order is alphabetical. A role column on
        the join table would fix it — migration + admin API, so it is the team's call.
- [x] TestCases (backend) [Luis, Alparslan] ← unit + API/role layers done, findings closed

**Bugs**
- [x] A lecture with no ratings crashes the app
      → `GET /ratings/own/{lectureId}` answered `ratings: null` for a lecture the student
        has not rated — which is every lecture with no ratings. Fixed: an empty list, and
        an unknown lecture id is a 404 like the categories route beside it.
      → the integration test already drove this exact request and asserted `success` and
        `message` but never `ratings`, which is the field the app dereferences
- [x] Does the cursor work in the backend?
      → BUG-1. **Yes**, on all four cursor endpoints, against a real Postgres, including with
        every row forced into the same microsecond and in both sort directions.
      → the sweep was checked against a deliberately broken predicate first, so its green is
        worth something. Not reproducible on the backend; the panel side is what is left.
- [x] Approximate location does not work → backend (the `IPINFO_TOKEN` check above)
      → F-8. `GET /system/status` reports `locationLookupEnabled`, and a revoked token now
        logs its status instead of one anonymous sentence. Still to check whether
        `IPINFO_TOKEN` is actually set on the VM — that is the ssh item above.
- [x] No logout after 2 days
      → **not a backend defect.** Session lifetime belongs to the endpoint: `/auth/login`
        with no `sessionType` mints an *admin* session (so the panel works) on the *app*
        schedule — a year. `/admin/auth/login` mints one day. The panel has not migrated.
      → written up in [adminweb-tasks.md](adminweb-tasks.md); pinned by
        `AdminApiPathSplitTests.appLoginKeepsYearLongSessionForAnAdminAccount`
- [x] Admin website: "Signed in as: Unknown User"
      → most likely `GET /admins/validate`, which answers a bare `{message, success}` with
        no identity in it. `GET /admin/auth/me` carries id, username, kitEmail, role.
      → an inference from the backend side — if the panel is already on `/admin/auth/me`
        and still shows a placeholder, that *is* a backend bug and we want to hear it

**Sonstiges**
- [ ] Format the wikis — [Wiki.md](../Wiki.md) is the source now; what is left is pasting it
      into GitLab → Wiki and deciding whether the wiki or the repository is authoritative.
      Only one of them can be. **[owner: ?]**
- [ ] Clarify who owns which item. Marked below wherever the repository actually records an
      owner, `[owner: ?]` wherever it does not — which is most of them, and is the point of
      this item. The header of this file says *Alparslan (testing side), Luis (parallel
      batch)*; that is the whole of what is written down anywhere.
      - Testing, findings, docs, CI — Alparslan
      - The unit batches split by class boundary — Alparslan and Luis
      - Everything under *My next steps* that needs GitLab or the VM — **[owner: ?]**, and it
        is the one that keeps stalling: a manual deploy job and two CI/CD variables have been
        waiting on "somebody"

## Präsi

A draft of both exists as `docs/presentation.md`, which is **gitignored on purpose** — it is
a local working note like `CLAUDE.md`, not repo history. It is on Alparslan's machine only.
These stay open until the team has agreed what is actually being said.

- [ ] The testing story. The material is all in [test-findings.md](test-findings.md), whose
      "At a glance" section now carries the counts: **50 findings, 44 fixed**, 2 closed as a
      decision (P-3, P-7), 1 existing protection put under test (F-13), 1 documented rather
      than changed (F-30), 2 open (F-29, which needs a rotation rather than a commit, and
      F-34, which needs a product decision), and which layer found how many. **Two** of the 50
      are the ones a user reported.
      → this line has been re-counted twice. "22 findings, 21 fixed" predates the contract and
        E2E layers; "37 findings, 33 fixed" predates the Kontrollphase sweep and the consumer
        contract layer, which together added F-31 to F-41. Read the count from
        `test-findings.md`, not from here — the same rule the coverage numbers are under.
- [ ] Process retrospective: what went well / badly with agile, how it would have gone with
      waterfall. Three questions in it need the team rather than the repository — batch size,
      whether the split with Luis avoided conflicts, and whether a written API contract up
      front would have been worth its cost
<!-- Write freely here. Nothing below this section depends on it. -->

-
-
-

## My next steps

- [ ] **Rotate seven leaked credentials. This is the largest open item on the page.**
      Secret scanning was added to CI on 8 September and its first run over the history found
      five, not one; custom rules on 9 September found seven, not five. Editing a credential out
      of a file **does not unpublish it** — every value below is in the git history and in every
      clone, so each has to be rotated at its provider. The current `application.properties` and
      `TestClient.java` are clean; that is not the same as safe.

      That the count has gone up twice is the point, not a detail. Each time it did, the reason
      was that somebody looked in a way nobody had looked before — first by reading the file the
      scanner could not read, then by giving the scanner rules for the values it could not see.
      **Seven is the number found so far, not the number that exists.**

      **The rotation table.** `R1`–`R7` are referenced from `.gitleaksignore`, where each
      fingerprint carries the row it retires — so uncommenting a line is unambiguous about
      which credential it claims was rotated. Rotate at the provider **first**, then uncomment
      the fingerprint with the date, then tick the row here.

      | # | Credential | Provider / where it lives | Where the rotation is done | Owner | Target | Rotated |
      | --- | --- | --- | --- | --- | --- | --- |
      | R1 | App bearer token — a session token the server issued | this backend's own `tokens` table | Revoke that session server-side (`POST /auth/logout-all` as that account, or delete the row). There is no external provider. `TestClient` reads `TEST_CLIENT_TOKEN` from the environment since `e120103` | Alparslan | **before submission (13.09)** | ☐ |
      | R2 | SMTP password — `spring.mail.password` | the mail account behind `SPRING_MAIL_USERNAME` | Change it at the mail provider, then update the `SPRING_MAIL_PASSWORD` CI/CD variable. **Both must happen**: the deploy job is where it reaches the server | Alparslan | **before submission (13.09)** | ☐ |
      | R3 | ipinfo API **token** | ipinfo.io | Revoke and reissue in the ipinfo dashboard, then update `IPINFO_TOKEN` in GitLab → CI/CD → Variables. `GET /system/status` reports `locationLookupEnabled`, which is how to confirm the new one took | Alparslan | **before submission (13.09)** | ☐ |
      | R4 | ipinfo.io **account password** | ipinfo.io | Change the account password. **Not the same as R3** — rotating the token leaves this valid, and this one can mint new tokens | Alparslan | **before submission (13.09)** | ☐ |
      | R5 | Geoapify API key **and** account password | Geoapify | Revoke the key and issue a new one, update `GEOAPIFY_API_KEY`, and change the account password separately. A rotation that stops at the dashboard takes the login-mail map down until the variable is updated | Alparslan | **before submission (13.09)** | ☐ |
      | R6 | Database password — `spring.datasource.password` | the PostgreSQL server | `ALTER ROLE … PASSWORD`, then update `SPRING_DATASOURCE_PASSWORD` and redeploy. Covers **both** leaked copies (R6 and R7 are the same credential in two commits) | Alparslan | **before submission (13.09)** | ☐ |
      | R7 | Database password again, a bare value | same as R6 | Retires with R6 | Alparslan | **before submission (13.09)** | ☐ |

      Where each one leaked, kept separate from what to do about it:

      | # | Where it leaked | Commit |
      | --- | --- | --- |
      | R1 | `testclient/TestClient.java` | `8bc81ef`, `c592ae5`, `bf4a476` |
      | R2 | `application.properties` | `245174c`, **`8bc81ef`** |
      | R3 | `application.properties` | `245174c` |
      | R4 | `application.properties`, in a `//` comment | `245174c` |
      | R5 | `application.properties`, key also in a comment | `245174c` |
      | R6 | **`BackEndStructure.md` — a document** | **`214ff090`** |
      | R7 | **`application.properties`**, a bare value rather than a `${...}` placeholder | **`d4cdbde`** |

      **Owner and target date are filled in on all seven, and the mapping to
      `.gitleaksignore` was re-checked on 9 September**: twelve fingerprints, each labelled
      with the row it retires, each carrying the reason it is there, and all twelve still
      commented out — so nothing claims a rotation that has not happened. The rotation *plan*
      was written for all seven before an owner existed, so whoever picks a row up does not
      have to reconstruct what to do.

      **Nothing in this repository can close any of them**, which is the whole point of the
      target date: the work is a visit to six provider dashboards, not a commit. If a row
      slips past 13.09 it should be re-dated here rather than left to read as done. Board
      item 8 ("say who owns what") is now closed *for this table* and still open for the rest
      of the page.

      **The last three entries are new (9 September): the list is seven, not five.** They were
      found by `.gitleaks.toml`, written to close the `secrets:new` gap described below — rules
      keyed on this project's property names report **twelve** history findings where the
      defaults report five. Written up as
      [F-29](test-findings.md#f-29--three-more-leaked-credentials-nobody-had-found).

      The `BackEndStructure.md` row is the one to read twice. Everything anybody had looked for
      was in `application.properties` or `TestClient.java`, because those are the files you
      think to check. That one is in a **design document**, where the password went in as an
      illustration of the configuration, and no amount of re-reading the properties file would
      have found it. The SMTP password also turns out to have been public from an earlier commit
      than this table recorded.

      Only the first was previously known. gitleaks reports five findings — the bearer token in
      three commits, and two lines of `245174c` — and the rest were found by reading that file.
      **So the scanner is a floor, not a ceiling** — do not treat a green `secrets:history` as
      proof there is nothing left.

      Why it misses them is worth writing down, because the earlier version of this line had it
      backwards. It is *not* that a secret in a `//` comment is invisible to the
      `generic-api-key` rule: one of the two findings in `245174c` is a comment
      (`//ipinfo.io password`, line 24), and the plain `ipinfo.token=` assignment two lines
      above it is one of the misses. What decides is the value. Reproduced against
      gitleaks v8.30.1 with synthetic values, in a throwaway repository:

      | Value | Flagged |
      | --- | --- |
      | 14 alphanumeric characters, Shannon entropy 3.18 | no |
      | 14 characters, entropy above 3.5 | yes |
      | 12 characters containing `+ * (` | no |
      | 12 characters, same shape, no punctuation | yes |
      | high entropy, inside a `//` comment | yes |

      Which accounts for all three misses, and the comment syntax accounts for none of them:
      the SMTP password contains punctuation the rule's value pattern cannot span, and the
      ipinfo token (entropy 3.18) and the Geoapify account password (3.02) are under its
      entropy floor. The consequence is not about this list, which is already written down — it
      is about `secrets:new`: **a password of that shape committed tomorrow would not be caught
      either.** Closing that needs a `.gitleaks.toml` rule keyed on the property names this
      project actually uses. → **done, 9 September.** It is in the repository root, with
      `[extend] useDefault = true` — without that line a config *replaces* the default rules
      rather than adding to them, which would leave four narrow rules as the whole rule set:
      nearly blind, and reporting green. Verified the way the table above was, under podman
      against v8.30.1: synthetic values in a throwaway repository (defaults catch 2 of 6, these
      rules catch all 6), then against this working tree, which must stay silent. The first
      draft reported nine times and every one was a false positive; what each taught is written
      next to the rule it broke. It also found three real credentials nobody knew about, which
      are the three new rows above.

      One more thing that version of the file published, not in the table because it is not
      certainly live: `spring.datasource.password` carried a **default** in the placeholder
      (`${SPRING_DATASOURCE_PASSWORD:…}`), so a readable database password reached the history
      too. The deploy job refuses an empty `SPRING_DATASOURCE_PASSWORD`, so production is not
      on it — worth one `psql` check that no local or dev database still is.

      The `secrets:history` job stays red until these are rotated. That is deliberate and it is
      `allow_failure`, so it cannot block a pipeline or drown out `health:check`. Each rotation
      retires one line of `.gitleaksignore`, where all twelve fingerprints are listed
      commented out with what to rotate and where — twelve rather than seven because a
      fingerprint is per rule *and* per commit, so one credential can need several. The job
      goes green when the last line is uncommented.
- [x] ~~**Push `integration-batch`**~~ — pushed. The line this replaces also claimed the
      branch had no upstream, which was not true either; it had one and was eight commits
      ahead of it.
- [ ] Run the deploy job (it is manual) and confirm an admin login now expires in a day —
      the experiment that made it two minutes is over, see "Done recently". The property itself
      no longer depends on the deployment: journey 5 of the E2E layer asserts it every run.
- [ ] Send [adminweb-tasks.md](adminweb-tasks.md) to whoever owns the panel repository.
- [ ] **Watch the next `dependency:scan` pipeline.** The first one is in: it went red, and on
      the failure this item did *not* name. Trivy's own database downloaded fine; the scan then
      died fetching `spring-integration-bom-7.0.6.pom` from Maven Central — `429`,
      `Retry-After: 1800`, from an IP the runner cluster shares. The premise underneath was
      wrong, not the tool: the job assumed `needs: server:test` plus a shared `.m2` cache meant
      a warm repository, and the runner log says `No URL provided, cache will not be downloaded
      from shared cache server`. There is no distributed cache, so a cache lives on the node
      that wrote it and two jobs are only on one node by luck.
      The job now scans an **SBOM** that `server:test` publishes as an artifact
      (`target/classes/META-INF/sbom/application.cdx.json`, cyclonedx-maven-plugin) and resolves
      no Maven coordinates at all. Verified locally both ways — same package list as the old
      `trivy fs` scan plus `lombok`, 0 findings; and still exits 1 with 13 HIGH when rebuilt
      against parent 4.0.6. **What is left is watching it in CI**, where the artifact handover
      is the new thing that can fail. Trivy's database download is still a shared-IP fetch and
      still the first thing to rule out in a red log.
- [ ] **Read the Spring Boot 4.0.6 → 4.0.8 bump.** It closes every known CVE in the tree
      (57 → 0) with 977 tests green, but it is a framework upgrade and no test suite is a
      substitute for somebody looking at it. Two overrides ride with it: `tomcat.version`
      11.0.25 and `bcprov-jdk18on` 1.84.
      → **and a reminder that expires:** drop the `tomcat.version` override once a Boot release
        pins 11.0.25 or later. An override that outlives its reason stops being a fix and
        starts being a way of holding a dependency back, silently.
- [ ] Check GitLab → Settings → CI/CD → Variables. There is still **no**
      `AUTH_ADMIN_SESSION_TTL` variable, which is why the value in `.gitlab-ci.yml` is what
      production gets. If someone adds one later it silently wins over the file.
- [ ] Is `IPINFO_TOKEN` set on the VM? Still the one open item from *Pending — today*, and
      still an ssh question rather than a repo one. `GET /system/status` now answers it from
      outside: `locationLookupEnabled`.

### Closed since this list was written

- ~~**Push the `TestCases2` fix (MR !151)**~~ — nothing to push. `origin/TestCases2` is an
      ancestor of `origin/main`, so it is merged, and the commit this item named (`85c01f8`)
      does not exist in this repository at all.
      The `/auth/me` deletion it worried about was **deliberate** — the route was believed to
      be a test placeholder. **That belief was wrong and the route is served again**: the panel
      was still calling it, and the deletion locked every administrator out of the panel until
      [F-39](test-findings.md#f-39--get-authme-was-unmapped-and-the-admin-panel-could-not-sign-anybody-in)
      found it. It is back as a time-boxed legacy alias — see
      [ADR 0014](adr/0014-legacy-auth-me-alias.md) and `CHANGELOG` 9.09 (15). The cleanup this
      line describes was real and stands; what did not stand is "gone for good".
      `GET /admin/auth/me` is the identity endpoint and is untouched.
- ~~Branch `apis` is one commit behind `origin/main`~~ — no local `apis` branch exists.
- ~~Commit the work~~ — the working tree is clean; the work is committed. It is the *pushing*
      that is left, which is the first item above.

---

## Done recently

### 8 September — the remaining board items

Seven commits, `write the changelog with every change` through
`stop publishing a bearer token in the test client`.
**876 tests, 0 failures**, 95.91% LINE / 90.61% BRANCH on `./mvnw clean verify`.

- **The lecture rating title** — see *Funktionalität*. `professors` has a guaranteed order
  now (F-21, the read side of BUG-3), and `LectureResponse` carries `semesterLabel` and
  `title` so the `WS25/26` rule lives in one place. Also corrected: `admin-api.md` documented
  `"semesterSeason":"WINTER"`, which the enum rejects with a `400`, and omitted
  `averageRating`.
- **`GET /auth/me` was deleted here, and came back.** Kept as written because it is the record
  of a decision that was reversed: the route was taken for a test placeholder and deleted
  deliberately in `7b2eea6`, and the panel had never migrated off it —
  [F-39](test-findings.md#f-39--get-authme-was-unmapped-and-the-admin-panel-could-not-sign-anybody-in),
  [ADR 0014](adr/0014-legacy-auth-me-alias.md). What follows was true of the deletion: what was
  left behind was a dead
  `SecurityConfig` rule, a row in the legacy path table promising it still answered, a
  migration task on the panel's list, three javadoc references meaning the admin route, and
  five tests in `AdminApiIntegrationTests` using it to check a session was dead. Those five
  now ask `GET /account/information`, which exists — they passed either way, because
  `BearerTokenAuthenticationFilter` answers `401` before routing, but a probe aimed at a route
  that is not there proves the filter only by accident.
- **P-6.** The sweep covering that route asserted a student session was "not refused", which
  a `404` also satisfies. That is fixed independently of the route: it asserts `200` now, so a
  route that stops answering fails the sweep.
- **The CHANGELOG is a standing obligation, not a final pass.** The rule is in `CLAUDE.md`,
  and the file has been backfilled: F-2, F-3, F-4, F-5, F-8, F-10, F-14, F-15, F-16, F-17 and
  the `ratings: []` fix had all shipped without an entry. That is ten client-visible changes
  the app and panel teams were never told about.
- **[Wiki.md](../Wiki.md) is new** — the domain vocabulary and the three-repository map, the
  two things `README.md` and `docs/` do not cover.
- **"At a glance" at the top of [test-findings.md](test-findings.md)** — 28 findings by the
  kind of work that found them.
- **`testclient/TestClient.java` was publishing a bearer token.** It sat in a tracked file as
  a string literal, along with the deployment's hostname. It reads `TEST_CLIENT_TOKEN` from
  the environment now — but **editing it out does not unpublish it**: it is in the history
  from commit `5589073` onwards, so that token has to be **revoked on the server** — now the
  first item under *My next steps*, because a consequence buried in a "done" paragraph is not
  a thing anyone does. Its
  `me()` method called `/auth/me` and was removed with the route — the route is back (F-39),
  the method was not, and nothing needs it; `TestClient.class`, a compiled
  artifact that predates `*.class` in `.gitignore`, is no longer tracked.
- **Three things the suite structurally could not do, now done.** A concurrency class
  (`ConcurrencyPostgresTests`, four races against real PostgreSQL — no defect, but each
  protection now fails a test when removed), secret scanning in CI (which found the five
  credentials above), and mutation testing behind a `pitest` profile (67% on its first run).
  The concurrency work is written up as a negative result and the mutation work produced
  [P-7](test-findings.md#p-7--a-surviving-mutant-is-not-a-finding), a reading rule: a surviving
  mutant under this profile is a candidate, not a finding. Three tests written against the
  first report's most alarming survivor were **deleted again** once it turned out two
  `AdminApiIntegrationTests` already covered it.
- **The two refactors, which the earlier version of this line said were held on purpose.**
  They are done: the unreachable role check is out of `LectureService.addLecture`, and the
  four create failures answer `409`/`404` instead of `200 {"success": false}` — F-5's
  direction, finished, with `CHANGELOG ///// 8.09 (5)` and the four characterization tests
  inverted rather than deleted. `admin-api.md` promised "not an HTTP `409`" and is corrected.
- **The `CHANGELOG` rule is in `README.md` now**, under "Changing the API", instead of in
  gitignored `CLAUDE.md` where it bound nobody.
- **`GET /social/sync/comments` has a functional test**, which it never had: the app's sync
  route was covered only by the authorization sweep and the schema validator, so what it
  actually answered was untested. Six app-facing tests in total; both hidden-content filters
  were watched fail against a deliberately broken version first.

### The two-minute admin session experiment is over

`.gitlab-ci.yml` writes `AUTH_ADMIN_SESSION_TTL=P1D` again, and the `TEMPORARY` comment is
gone. Nothing else in the repository ever carried `PT2M`.

**What it proved.** The session lifetime follows the endpoint, not the account. With the admin
lifetime cut to two minutes, an administrator logging in through the app API still got a year —
same account, same moment, two different lifetimes. That is pinned by
`AdminApiPathSplitTests.appLoginKeepsYearLongSessionForAnAdminAccount`, so ending the experiment
loses nothing: the property is in a test rather than in a deployment setting.

The next deploy puts the panel back on a one-day admin session. The app API is untouched and
stays at 365 days. `docs/adminweb-tasks.md` no longer carries the "while the experiment runs"
caveat.

### The admin API is now separate from the app API

The bug: an administrator logging in from the Android app was given an admin session,
because the lifetime was chosen from the account, not from the endpoint. Admins were logged
out of the app on the panel's schedule.

- Session type and lifetime are now decided by which endpoint was called.
  `POST /admin/auth/login` — administrators only, admin session, one day.
  `POST /auth/login` — everyone, app session, 365 days. **No app release was needed.**
- Every administrative route also answers under `/admin` + its old path. Old paths still
  work, so the panel is not broken; they are deleted once the panel has moved.
- `POST /admin/data/lectures` and `POST /admin/data/professor` are refused with a real `403`
  instead of the old `200 {"success": false}`.
- The login response carries `expiresAt`, so the panel can see a short session coming.
- `ADMIN_LOGIN` audit entries carry `metadata.api`, which is how we will know the panel has
  finished migrating.
- Tests went 144 → 165. `AdminApiPathSplitTests` mints its sessions through the real login
  endpoints, because the older helper writes tokens with no session type and would stay
  green with the admin path broken.

Also fixed on the way: `GET /activity-logs` returned **500** whenever a refusal
(`LOGIN_REFUSED`, `ACCESS_REFUSED`) was on the page. Those entries carry no target id and the
revertibility pass asked an empty immutable map for a null key. It predates this work, but
the new `/admin/**` rule writes a refusal every time a student probes an admin path, so it
would have started firing constantly.

### CI/CD

- **The coverage gate could kill the build over a missing comparison point.** It handed
  unverified refs to `git merge-base` — a force-pushed `CI_COMMIT_BEFORE_SHA`, or a branch
  whose fetch failed — and any git error exited the job with no coverage output. Verified
  empirically: old code `exit 1` with `Git command failed: git merge-base`, new code falls
  through to the next candidate. This was the most likely cause of a red pipeline with no
  obvious message.
- `gitSucceeds()` could never return `false` — the helper it called exits the JVM before it
  can throw. Replaced with one that reports failure.
- `server:test` had no `rules`, so it also ran in the nightly scheduled pipeline,
  contradicting the comment at the top of the file and the deployment doc. A red nightly
  build buries the `health:check` signal the schedule exists for.
- The merge-request fetch had no `|| true`, so a protected or renamed target branch killed
  the job before Maven started.
- Every job re-downloaded the same 9 MB Maven zip: `mvnw` unpacks under `MAVEN_USER_HOME`,
  which was outside the cache. Now cached.
- The deploy wrote `SPRING_DATASOURCE_PASSWORD` and `AUTH_RATE_LIMIT_HMAC_KEY` empty when
  their CI/CD variable was missing, and died four steps later inside `docker compose pull`
  with a message naming neither. It now stops immediately and names the variable.
- A secret containing `$` was mangled by Compose interpolation of the `.env`. Escaped now.
- Health poll after deploy: 60 s → 120 s. The container alone gets a 30 s start period and
  Flyway migrates before the app serves.
- One pipeline per push instead of two (`push` + `merge_request_event` both ran).
- `.env.example` published the API on `0.0.0.0:8080`, bypassing nginx and TLS, contradicting
  the CI comment that calls the loopback prefix load-bearing.
- `docker-compose.prod.yml` still defaulted `SPRING_FLYWAY_BASELINE_ON_MIGRATE` to `true`,
  which the deployment doc argues at length must be `false`.
- `scripts/docker-smoke-test.sh` is a **local** helper — no CI job has called it since the
  DinD job was replaced. The docs said otherwise. Also LF-pinned now, the CRLF-shebang trap
  `scripts/ops/README.md` documents.

Verified locally with `./mvnw -o -B -ntp verify` (165 tests) plus the coverage gate against
the real report: line 96.97%, functionality 94.12%, decision 100%, against a threshold of 30.

### Docs

- `docs/admin-api.md` — every path prefixed, a path-migration table, and the rule that the
  endpoint decides the session.
- `docs/deployment.md` — the two session variables, the CD note that a CI/CD variable
  overrides everything in the repo, and corrected job descriptions.
- `docs/adminweb-tasks.md` — new, the panel's work list.
- `CHANGELOG` — an entry for the app and panel teams.

---

## Backlog

- **The heavier refactoring is done** — seven commits on
  `refactor/single-responsibility`, no interface changed, no `CHANGELOG` entry.
  [test-plan.md](test-plan.md#the-single-responsibility-refactor) has the old-to-new class
  mapping and what the work found. Everything below is what it deliberately did **not** fix,
  each one named rather than quietly carried.

- ~~**`LectureService.getLecture` answers `200 {"success": false}`**~~ — **done, 9 September**,
  and the sentence this line used to carry was wrong. It said *"every other read in the module
  answers `404`"*; `ProfessorService.getProfessor` did not, and had the identical defect. Both
  answer `404` now, with the `CHANGELOG` entry and the `admin-api.md` correction that were the
  reason for holding it. [F-22](test-findings.md#f-22--the-two-catalogue-detail-reads-answer-200-for-a-row-that-is-not-there).
  A third instance turned up in the same pass — `RatingService.submitRating` — and is fixed as
  [F-28](test-findings.md#f-28--submitting-a-rating-for-an-unknown-lecture-answers-200).

- ~~**`POST /social/comments` answers `500` for a malformed lecture id**~~ — **done,
  9 September**, and wider than this line described. A *missing* id was a 500 too:
  `UUID.fromString(null)` throws `NullPointerException`, not `IllegalArgumentException`, so
  catching only the latter would have fixed half of it. None of the four DTOs had any validation
  and none of the four handlers had `@Valid`, while the two vote routes beside them had both.
  [F-24](test-findings.md#f-24--the-four-social-submit-routes-answer-500-for-a-bad-or-missing-id).
  The parse rule now lives once, in `com.pse.shared.util.Uuids`, instead of in three private
  copies. Two more unvalidated bodies were found the same way and fixed as
  [F-25](test-findings.md#f-25--two-more-unvalidated-request-bodies).

- **`orElse(null)` against `orElseThrow`, split by package.** `moderation` and `audit` throw;
  `social`, `lecture`, `rating`, `professor` and `user` return null and check. Not a defect,
  but it is why the two halves of the codebase read differently, and it is the reason the
  wrapper-and-boolean pattern the social split removed existed at all.

- **The social listing is N+1.** `SocialResponseMapper` resolves the caller's own vote and
  counts up- and down-votes per row, so `GET /social/comments/{id}` costs three queries per
  comment plus three per answer. `moderation` solved the same problem with grouped counts
  (`IdCount`); `CommentVoteRepository` and `AnswerVoteRepository` would need the same
  `countGroupedBy*` queries. A performance change, not a refactor.

- **`ModerationCommentService` and `ModerationAnswerReportService` are still near-duplicates.**
  Merging them was in the plan and was measured instead: thirteen abstract methods to save a
  hundred and twenty lines, separating the algorithm from its data. The rule that could
  silently diverge is extracted (`ReportOutcome.visibilityFor`); the rest is plumbing the
  compiler keeps honest. **This is a judgement call and worth a second opinion** — if the two
  drift in review, the answer is to merge them after all.

- **A login and a logout disagree about what "administrative" means.** `SessionAuditWriter`
  records a login as administrative when the *session* it minted is `ADMIN`, and a logout when
  the *account* holds an `admins` row. An administrator signing in through the app API opens
  an `APP` session, so opening it is a student event and closing it is an admin event. That is
  inherited behaviour, pinned by tests, and left alone — but it looks like a bug and somebody
  should decide whether it is one.

- **Two `List<Object[]>` queries remain**, both in `AuditLogRepository`
  (`findActorSnapshots`, `findReversalsOf`). They are a different shape from the five that
  became `IdCount` projections, so they need their own treatment rather than the same one.

- ~~**Two classes have no unit test**~~ — **done, 9 September.** `StudentLifecycleService`
  (10 tests) and `AdminBootstrapRunner` (6). What they pin is the part an HTTP assertion cannot
  see: that a delete anonymises rather than removes, that the audit label is captured *before*
  the scrub, that an already-blocked account writes nothing, and that the bootstrap only ever
  adds — never demoting or reactivating. Both classes were mutated by hand afterwards to check
  the tests can actually fail: moving the label capture after the scrub and removing the
  idempotence guard each turn them red.

- ~~**Refactor**~~ — **done**, both halves, and no longer held for the heavier refactoring:
  they turned out to be self-contained, and holding a client-visible status-code fix behind an
  unscheduled refactor was costing the panel team a wrong contract in `admin-api.md`.
  - `LectureService.addLecture`'s unreachable role check is gone, and with it
    `LectureService`'s only use of `ModerationUserService`. The javadoc now says why there is
    no check there, so nobody puts it back. `addLectureFailsWhenStudentIsNotAdmin` was deleted
    with the branch it tested — it pinned dead code, not behaviour — while
    `addLectureRecordsNothingWhenItRefuses` was **kept** and repointed at a refusal the
    service can still reach.
  - The four create failures answer `409` and `404` through `ApiException` now, the direction
    F-5 set. **Bodies are unchanged**, message text included, so a client reading `success`
    has nothing to do. `CHANGELOG ///// 8.09 (5)` carries it, in the same commit, and
    `admin-api.md:572` — which explicitly promised "not an HTTP `409`" — is corrected.
  - The four characterization tests were **inverted, not deleted**, and each was watched fail
    against a deliberately wrong status before being left green.
- **API tests** — the app-facing side is still thinner than the admin side (62 tests in
  `AdminApiIntegrationTests`), but the two holes worth naming are closed:
  `GET /social/sync/comments` had **no functional test at all** — the sweeps knew who could
  call it and the OpenAPI suite knew its body matched the schema, and neither knew what it
  answered — and the answer-vote route had only its bad-request case, so it was known to
  reject a bad vote and not to accept a good one. Six tests added;
  `SocialApiIntegrationTests` is 16. Both hidden-content filters were watched fail first.
  What is left is breadth on `/account` and `/ratings`, and it is thin rather than absent.
- ~~**Unit tests**~~ — done in batch 7. `AuditLogService` had no unit test class at all and the
  warning lifecycle in `ModerationUserService` had none either; both do now. What is left in
  those classes is mostly `Specification` lambdas, which need a database rather than a mocked
  `CriteriaBuilder` — see test-plan.md, "What this batch deliberately did not do".
- **Coverage** — the gate gives 30% on *changed* lines (`COVERAGE_THRESHOLD` in
  `.gitlab-ci.yml`). The thin areas it was waiting on are covered now, so it could go up —
  **left for the team to decide**, deliberately: unlike the bundle floors, this one gates
  *other people's* merge requests, and tightening it quietly is how a gate stops being believed.
  The bundle floors have moved: LINE 90 mandatory / 95 target, BRANCH 0.88.
- **Bugfixes** — seven fixed on 9 September, F-22 through F-28 in
  [test-findings.md](test-findings.md). Five of them were not on any list: a revert of a refusal
  answering 500 (**F-23**, the one to read first — the panel shows that button), `ratingCount`
  always 0, two more unvalidated bodies, and the rating categories coming back in a
  JVM-dependent order. **No known open ones.**

  Two of the five are worth a sentence of their own, because they are the same mistake twice:
  both are **regressions of a fix this repository had already made**. F-23's null guard exists
  twenty lines above the crash, with a comment explaining it, and was never mirrored into the
  method beside it. F-26's neighbour `averageRating` was moved to recompute on read during the
  refactor and `ratingCount` was left reading a column nothing writes. A defect that has been
  fixed once is worth grepping for.

- **A dead column.** `professors.rating_count` is written by nothing and now read by nothing
  either (F-26 counts on read instead). Dropping it is a migration; named here rather than
  quietly carried.

- **`professors.average_rating` is the same shape** — the write was removed on 9 September
  because nothing had read it since `36a1f01`. Same migration, if anyone writes it.
- ~~**README**~~ — written. What the service is, `./mvnw verify`, the local Compose run, the
  coverage tiers, and a table pointing at everything in `docs/`.
- ~~**The CHANGELOG rule lives in `CLAUDE.md`, which is gitignored**~~ — moved. It is
  `README.md`, under **"Changing the API"**: what counts as client-visible, that the entry
  goes in the *same commit* rather than a final pass, and how an entry is shaped. The
  `CLAUDE.md` section is now a pointer at it, so there is one copy and it is the tracked one.
- ~~**Wiki**~~ — decided and written: [Wiki.md](../Wiki.md). It holds the domain vocabulary and the
  three-repository map, and points at `README.md` and `docs/` for everything else.
- ~~**Dependency vulnerability scanning**~~ — **done, 9 September**, and the blocker was not
  what it looked like. This entry said Trivy "resolves the Maven parent-pom chain over the
  network and was rate-limited (429) from a shared IP". True, but the cause is a **cold** `.m2`:
  with a warm cache the parent chain resolves locally and nothing is fetched. It read `pom.xml`
  first try and reported **57 vulnerabilities, 7 CRITICAL**.

  Then it was acted on rather than filed: `spring-boot-starter-parent` 4.0.6 → **4.0.8** clears
  52 of the 57 on its own, and the last five need a `tomcat.version` override (11.0.25, three
  CRITICALs against `tomcat-embed-core` that 4.0.8 still pins 11.0.24 for) and `bcprov-jdk18on`
  1.81 → 1.84. **57 → 0**, with 975 tests green and both coverage gates met.

  `dependency:scan` is in `.gitlab-ci.yml`, and deliberately **not** `allow_failure` and not
  schedule-only, unlike `secrets:history` and `pitest`: those two are expected to be red, this
  one starts green, and a gate whose red is expected is a gate people learn to ignore. It fails
  on HIGH or CRITICAL only.

  **Corrected on the first CI run.** "With a warm cache the parent chain resolves locally" is
  true and was useless here: this runner has no shared cache server, so the `.m2` cache the job
  counted on lives on whichever Kubernetes node wrote it, and `needs:` does not place two jobs
  on one node. The run got a cold repository and a `429` from Maven Central. Trivy now scans an
  SBOM handed over as an artifact and never resolves a coordinate; see the job comment and
  item 10 in [Watch once, then close](#watch-once-then-close).

  OWASP dependency-check stays unshipped. It still needs an `NVD_API_KEY`, and with Trivy green
  there is nothing left for it to add.

- ~~**Triage the mutation report once**~~ — **done, 9 September.** 1123 mutants, 826 killed
  (**74%**, up from the 67% first run), 222 with no coverage, **75 survivors**, test strength 92%.

  Read the way [P-7](test-findings.md#p-7--a-surviving-mutant-is-not-a-finding) says to, and P-7
  held: **most of the 75 are artefacts of the profile, not gaps.** `targetTests` is scoped to
  service unit tests, so every `VoidMethodCallMutator` on a setter — `setKitEmail`, `setHash`,
  `setExpiresAt`, 40-odd of them — survives because what it breaks is asserted at the
  integration layer, which PIT never runs. Removing the two `rateLimitService.ensureAllowed`
  calls in `SessionIssuer` survives for the same reason: `AdminApiIntegrationTests` and
  `AdminApiPathSplitTests` both assert a real 429.

  **One real gap, and it is a boundary.** `WarningService` rejects a message longer than 2000
  characters, in two places, and the `ConditionalsBoundaryMutator` survived on both: a test
  pinned 2001 as refused and nothing pinned **2000 as accepted**, so `>` could become `>=` and
  the suite stayed green. Two tests added, and each was watched fail against the mutated source
  before being left green. The pair the old entry named in `RateLimitService` are both still
  there and both still artefacts — the propagation-behaviour one cannot be killed by a unit test
  at all.

  **A threshold is still not set**, for the reason this entry gave originally: 222 no-coverage
  mutants come from classes the profile targets but whose tests it does not run, so any number
  would be measuring the profile's scope rather than the suite's strength. Fix the scope first.

- **Fix the `pitest` scope, then set a threshold — in that order.** `targetClasses` covers
  `com.pse.*.service.*` and five more patterns; `targetTests` covers only test classes in
  service and mapper packages. Everything targeted whose tests live elsewhere — the API
  integration classes, the sweeps — contributes mutants that can never be killed, which is what
  the 222 "no coverage" are. Widening `targetTests` makes the run slower and the number
  honest; the pom comment explains why it was narrowed in the first place, so read that before
  widening it. **The number to set a threshold from does not exist yet.**

- **The SonarQube backlog: ~295 code smells.** 350 on the first scan, minus the 55 dead imports
  already removed. 111 are `isZero()` suggestions and 87 are assertion lambdas with more than
  one invocation point — both test-style, both mechanical, neither a regression. Worth one pass
  by whoever cares about the Sonar rating; worth nobody's evening otherwise. The bugs and the
  vulnerability were triaged on 9 September and none was a defect —
  [F-30](test-findings.md#f-30--a-rate-limiter-that-is-only-correct-because-a-clock-in-another-file-is-utc)
  has the reasoning, including the two that were worth writing into the code.

- **Docs** — `GET /admins/validate` is still undocumented, deliberately: it is being retired.

---


## Waiting on other people — the older copy, kept as a record

> **Both migration bullets below were overtaken on 10 September and are kept only as a record of
> what was planned.** The deployed host routes `/admin/` to the panel's own static container, so
> the migration would have aimed every panel call at a file server; the decision taken was to
> leave the routing alone. **The unprefixed paths are the production contract permanently and the
> legacy surface is not being deleted** — see item 23 above, `docs/adminweb-tasks.md`, which marks
> both migration tasks withdrawn, and `docs/admin-api.md`, which no longer promises the removal.
> Nothing here is waiting on the panel any more.

- ~~**The panel migrating to `/admin/**`**~~ — [adminweb-tasks.md](adminweb-tasks.md). Until it
  lands, the panel keeps year-long admin sessions from the app API. **Withdrawn**: it is not
  landing, and the year-long session is instead handled on the root paths.
- ~~**Then, the legacy surface can be deleted**~~ — one string per controller, the two creates
  in the app controllers, `LoginRequest.sessionType`, `resolveSessionType`, `AdminController`
  and `AdminService`, roughly 50 lines of `SecurityConfig`, and a `V2` migration backfilling
  `tokens.session_type` to `APP`. Preconditions: no `ADMIN_LOGIN` with `"api": "APP"`, and
  nothing calling `/admins/validate`. **Withdrawn with the line above** — the preconditions can
  no longer be met, because nothing is migrating.
- ~~**Audit gap**~~ — closed as F-20. `LECTURE_CREATED` and `PROFESSOR_CREATED` are written now,
  as lifecycle events (`exists: false -> true`) and therefore not revertible, following the
  precedent `USER_WARNING_CREATED` already set. Client-visible, so it is in the `CHANGELOG`.
