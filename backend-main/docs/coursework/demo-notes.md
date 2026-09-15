# Demo notes

What to show, in what order, and the three switches not to touch while showing it. Written
against the code on **9 September** and **re-checked line by line on 10 September**; every claim
here names the file it was read from, because a demo note that has drifted is worse than none.
Several line numbers below moved between those two dates and were corrected — which is the
argument for citing them rather than against it.

---

## Do not touch: the `active` switch on a lecture or professor

**In the catalogue form, leave `active` alone** — not because anything is lost, but because
**the panel cannot bring the row back**. Get the reason right if it is asked about, because the
obvious reading of it is wrong:

- **Nothing is deleted and nothing is irreversible.** The row keeps its id, its ratings, its
  comments and its history. `active = false` is what you use *instead* of deleting.
- **The API can undo it.** `PATCH /data/lectures/{id}` and `PATCH /data/professor/{id}` accept
  `{"active": true}` (`LectureModerationService:127-129`, `ProfessorModerationService:93-95`),
  and `GET /data/lectures/all` / `GET /data/professor/all` return deactivated rows on purpose —
  `ModerationCatalogController:37-40` says so in as many words: *"the public reads filter to
  `active = true`: a deactivated lecture or professor would otherwise be unreachable from the
  panel to correct or reactivate."*
- **The panel does not call those two routes.** It reads only the active-only
  `GET /data/lectures` and `GET /data/professor`; the `/all` pair is recorded in
  `docs/adminweb-consumer-contract.md` as **not called**. So the deactivated row leaves every
  screen the panel has, and the `PATCH` that would restore it needs an id that is no longer
  displayed anywhere.
- **The routes it would have to call already exist, unprefixed, and already answer in
  production.** Measured on 10 September: `GET /data/lectures/all` answers `401` JSON from this
  backend, while `GET /admin/data/lectures/all` answers `200 text/html` from the panel's own
  nginx (F-43). So the fix needs no backend change at all — **decided 10 September, option A**,
  `docs/adminweb-tasks.md` §2b — and the panel work is scheduled after submission. Nothing about
  that changes what to do during the demo.
- **Nothing on the deployed data is deactivated today**, checked the same day: 92 lectures and
  132 professors, not one with `active: false`. So the demo starts from a clean state on this
  and the only way to break it is to use the switch.

**So it is one-way from the panel, not one-way in the system.** In a demo that is the same
inconvenience — there is no screen to undo it on — which is why the switch stays untouched.

**Show the form warning instead, and say why it is there.** This is a decision worth narrating,
and it narrates well precisely because **neither side is at fault**: the API deliberately keeps
a deactivated row readable so it can be corrected, the panel deliberately reads only the active
list because that is what its screens are for, and the defect lives in the gap between two
reasonable choices. That gap is invisible to every test on either side and is exactly what the
consumer contract layer exists to find. Recorded rather than fixed — **F-42**, on the board as
`docs/TODO.md` item 32 and as task 2b in `docs/adminweb-tasks.md`; the panel side is scheduled
after submission.

Deleting is a separate and louder thing: `DELETE /data/lectures/{id}` takes every comment,
answer and rating filed under the lecture with it (`LectureModerationService:180-190`). Not part
of the demo either.

## Do not spend: the login rate limit

One 15-minute window, four budgets, and the demo only ever spends the first two
(`RateLimitProperties`, `application.properties:58-63`):

| Budget | Per window | Property |
|---|---|---|
| Login codes requested **per e-mail address** | **3** ← the one that bites | `AUTH_RATE_LIMIT_REQUEST_EMAIL` |
| Login codes requested **per IP** | **20** | `AUTH_RATE_LIMIT_REQUEST_IP` |
| Failed logins **per e-mail address** | 10 | `AUTH_RATE_LIMIT_LOGIN_EMAIL` |
| Failed logins **per IP** | 30 | `AUTH_RATE_LIMIT_LOGIN_IP` |

Window: `PT15M` (`AUTH_RATE_LIMIT_WINDOW`).

**Three is the number that bites.** It is codes *requested*, not codes *entered wrong* — so
every rehearsal of the login screen on the same address spends one, and a rehearsal plus a
retry plus the live run is the whole budget. Rehearse on a **different address** from the one
being demonstrated, or rehearse more than 15 minutes before.

If it does trip, it is a `429` with a `Retry-After`, not a broken login. Say so rather than
reloading.

## Do not press: "create issue" on a bug report

**The integration is configured on the deployment, so the button is visible, and pressing it
shows an error.** Measured on 10 September against `ratemyprofessor.dev`: the issue **is opened
in GitLab** and the panel then reports `409 State conflict`. That is
[F-50](../test-findings.md#f-50--the-gitlab-issue-success-path-answers-409-state-conflict-and-the-issue-stays-open),
unfixed at submission.

**Pressing it twice is worse than pressing it once.** The failure leaves the report's
`issueState` at `NONE` and its `issueUrl` null, so the panel keeps offering the button — and each
click opens **another** GitLab issue and returns the same error. Do not retry it to see whether
it was a fluke.

**If it is pressed by accident**, the recovery is in the tracker, not the panel: close the issues
it opened. Nothing in this application points at them.

**If it is asked about**, the honest answer is the interesting one and it is short: the write to
the tracker succeeds and the write to our own database is refused by a constraint that **is not
in the schema this repository holds** — because the deployed database was baselined rather than
migrated, so nothing in the test suite has ever run against it. That is
[F-51](../test-findings.md#f-51--nothing-verifies-the-production-schema-and-it-is-not-the-schema-in-the-repository),
and it is the larger of the two findings. The end-to-end test covers this exact path against a
real PostgreSQL built from `V1` and passes.

**The one-configuration-change option, if the button must be gone.** Clearing any one of
`GITLAB_BASE_URL` / `GITLAB_PROJECT_ID` / `GITLAB_TOKEN` turns the integration off:
`gitLabClient.isEnabled()` requires all three (`RestClientGitLabClient:89-91`),
`GET /system/status` then reports `gitlabEnabled: false`, the panel **hides** the action, and
`POST /reports/{id}/gitlab-issue` answers a clean `503` if called anyway
(`application.properties:76-83`). **`GITLAB_TOKEN` is the one to clear** — all three have the
same effect, and clearing the token leaves the base URL and project id in place, so restoring the
integration afterwards is one variable. These are `@ConfigurationProperties`, read at startup, so
**it needs a redeploy**: clear the CI/CD variable, then re-run the deploy job — `.gitlab-ci.yml:330-333`
writes them into `.deploy.env` at deploy time. That contains the demo; it does not fix F-50.

**Not pressing it is the recommendation.** The disabled state is also a designed state worth
narrating — the flag is on `/system/status` precisely so a client can hide an action instead of
discovering it fails when pressed — but that story is weaker than saying plainly that the button
has a known defect with a number, a measurement and a fix that was not guessed at under a
deadline.

## Works now: the panel sign-in

**Show the login flow.** It was broken until 9 September and is worth showing *because* it was:
`GET /auth/me` had been deleted on the assumption the panel had migrated, and it had not, so the
panel minted a token and then failed the sign-in with "Signed in, but your admin account could
not be read". Every backend test was green throughout.

Fixed as a time-boxed legacy alias —
[F-39](../test-findings.md#f-39--get-authme-was-unmapped-and-the-admin-panel-could-not-sign-anybody-in),
[ADR 0014](../adr/0014-legacy-auth-me-alias.md), `CHANGELOG` 9.09 (15). If the story of the
consumer contract layer gets told, this is the example.

---

## Clean data state before starting

1. **An admin account that can log in.** The bootstrap list is
   `app.auth.admin-bootstrap-emails` (`application.properties:43`); an address on it becomes an
   administrator on first login. Confirm the one being used is on the list *before* the room is
   watching, and remember that confirming it costs one of the three code requests.
2. **A second, ordinary student account** — the moderation screens are dull with nothing to
   moderate, and warning your own account is refused.
3. **Content to act on**: at least one lecture with a rating and a comment, one comment with an
   answer, one reported comment, and one open bug report. Reports are the screen with the most
   to show.
4. **Mail reachable.** The login code arrives by e-mail; if `SPRING_MAIL_*` is not configured
   the code is not retrievable and there is no fallback. Check this first — it is the one
   failure that stops the demo at step one. **This is about the deployed instance, which mails
   through a real SMTP server.** It stopped being true of a local `docker compose up` on
   10 September: that stack now runs a mail catcher and the code is read from
   `http://localhost:8025`. If the demo is given off the local stack rather than the deployment,
   this item is free — but the rate limit below still applies, because it is counted per address
   either way.
5. **`GET /system/status` green**, and read `gitlabEnabled` and `locationLookupEnabled` off it
   so there are no surprises about which actions are visible.
6. **Nothing deactivated and nothing deleted** from a previous rehearsal. See the first section.

## Order to walk it

1. **Sign in.** Request a code, enter it, land on the panel. Name the F-39 story here while the
   screen is on the identity call.
2. **The catalogue.** Lectures and professors, the `semesterLabel`, the professors sorted by
   name. Edit a lecture's name — *not* its `active` flag — so there is an audit entry to revert
   later.
3. **A rating and its comments.** The read side, and the ordering that F-21, F-27 and F-41 all
   turned out to be about.
4. **Moderation.** Warn the second account, then look at the reported comment and resolve it.
5. **The audit log**, and **revert the name edit from step 2**. This is the strongest thing to
   show: the trail is not just a list, it undoes a field. The window is
   `app.audit.revert-window`, `P7D` by default.
6. **Bug reports.** The list only. **Do not press "create issue"** — see the section above; it
   opens the issue and then shows an error, and a second press opens a second issue.
7. **`GET /system/status`** as the closing slide if one is wanted: uptime, table counts, the
   last write, and the two capability flags.

An administrator session lasts **one day** (`app.auth.admin-session-ttl`, `P1D`), so a session
opened during setup is still good at demo time. It will not expire mid-demo.
