# Wiki

The source for the project wiki. It holds the two things that have no home anywhere else —
**what the words mean** and **how the three repositories fit together** — and points at the
files that own everything else.

It deliberately does not repeat `README.md` or `docs/`. A wiki that restates them is a second
copy, and the second copy is the one that goes stale without anybody noticing.

---

## Where everything actually lives

| You want | Read |
| --- | --- |
| What this service is, how to build and run it | [README.md](README.md) |
| The admin API contract, every route and body | [docs/admin-api.md](docs/admin-api.md) |
| What is tested, at which layer, and why | [docs/test-plan.md](docs/test-plan.md) |
| What the tests found, and what was decided | [docs/test-findings.md](docs/test-findings.md) |
| Pipeline, CI/CD variables, deployment | [docs/deployment.md](docs/deployment.md) |
| Interface changes, for the app and panel teams | [CHANGELOG](CHANGELOG) |
| The panel's work list | [docs/adminweb-tasks.md](docs/adminweb-tasks.md) |
| What the team is working on | [docs/TODO.md](docs/TODO.md) |

The OpenAPI schema is generated from the controllers, not written by hand, and is served at
`/v3/api-docs` to authenticated administrators.

---

## The three repositories

```
   Android app  ──┐
                  ├──►  backend  ──►  PostgreSQL
   Admin panel  ──┘        │
                           └──►  GitLab issues   (bug reports)
                           └──►  ipinfo.io       (approximate login location)
                           └──►  SMTP            (login codes, login notifications)
```

**backend** — this repository. One Spring Boot service, one database. It serves both clients
over one HTTP API that is split by path:

- `/auth/**` and the app routes serve the **Android app**. A session from `POST /auth/login`
  lasts a year.
- `/admin/**` serves the **admin panel**. A session from `POST /admin/auth/login` lasts a day
  and is refused to any account without an `admins` row.

**Which session you get is decided by the endpoint you called, not by your account.** An
administrator logging in from the app gets an app session; the same person logging into the
panel gets an admin session. This is the single most misunderstood thing about the API and it
is why the split exists — see [docs/admin-api.md](docs/admin-api.md).

**Android app** and **admin panel** are separate repositories with their own teams. The
contract between us and the panel is `docs/admin-api.md`; work we need from them goes on
`docs/adminweb-tasks.md`. Anything a client must react to goes in the `CHANGELOG` — that file
is written for them, not for us.

Browser tests belong in the panel's repository, against a deployed backend. There is no
frontend here for one to open.

---

## Vocabulary

The words that mean something specific here, in the order they make sense.

**Lecture** — a course in one semester, held by one or more people. `SS26 Algorithmen 1` is
a lecture; the same course next year is a different one. A lecture is `LECTURE_ONLY` or
`LECTURE_AND_EXERCISE`, and that choice decides which rating categories it offers.

**Semester** — `SS` (summer) or `WS` (winter) plus a year. Written `SS26`, and `WS25/26` for
winter, which spans two calendar years. The stored `semesterYear` on a winter lecture is the
year it **starts** in. The API returns the finished label as `semesterLabel` so no client has
to get this right on its own.

**Professor** — anybody who teaches a lecture. The table is called `professors`, but an
exercise leader is a row in it too: nothing in the schema distinguishes them, so a lecture's
teaching staff is one flat, alphabetically ordered list.

**Rating** — one student's verdict on one lecture. A student has at most one rating per
lecture, and it is made of **rating topics**.

**Rating topic** — one score in one **rating category**. `PROFESSOR_ENGAGEMENT: 4`.

**Rating category** — the thing being scored, and its weight. Some are about the lecture
(`LECTURE_PACE`), some about the person (`PROFESSOR_COMMUNICATION`, `TUTOR_FEEDBACK`), some
about the setting (`ROOM`). Which ones a lecture offers comes from its `LectureType`.

**Overall rating** — the `OVERALL` category, not an average of the others. When you see one
number for a lecture, it is the mean of its ratings' `OVERALL` topics.

**Credibility score** — upvotes minus downvotes across everything a student has written. It
is **derived on read**, not stored — except that an administrator can adjust the stored column
deliberately, which is why a read must never write it back (F-9).

**Comment / answer** — a question on a lecture, and a reply to one. Both can be voted on and
both can be reported.

**Report** — a student flagging a comment or an answer for moderation. Not to be confused with
a **bug report**, which is a student telling us the app is broken and becomes a GitLab issue.

**Warning** — an administrator's formal notice to a student. Three of them is the usual road
to a block. A warning is created, never edited.

**Block** — a student account taken out of use. Their sessions are revoked immediately and
their login stops working; nothing they wrote is deleted.

**Administrator** — a student account with a row in `admins`. There is no separate account.

**Elevated operator / superadmin** — an administrator who may also moderate *other*
administrators. It is deliberately **not** a role: the panel's role union stays
`STUDENT | ADMIN`, and elevation is a separate `isSuperAdmin` flag on `GET /admin/auth/me`.

**Audit entry** — a record of one administrative action: who, what, which row, and the
before/after of every field they changed.

**Revert** — undoing one audited change by writing the `before` values back. It is refused if
the row has changed since (`VALUE_CHANGED`), if the action has no inverse, or if the actor is
not allowed to touch that target. Creations and deletions are **lifecycle events** and are not
revertible: the inverse of a creation is a deletion, which is its own audited action.

**Cursor** — how the long admin lists are paged. Not an offset: a cursor names the last row
you saw, so rows inserted while you page do not shift the ones you have not seen yet.

---

## Conventions worth knowing before you change anything

- **Every client-visible change goes in the `CHANGELOG`, as part of the work that makes it.**
  A status code, a field, a route, a message somebody matches on. That file is the app and
  panel teams' only warning.
- **Commit messages are one lowercase line**, no body, under ~65 characters. The reasoning
  goes in the `docs/` file that owns the subject.
- **`./mvnw clean verify`, not `test`** — the coverage gate is bound to `verify`, and without
  `clean` JaCoCo appends the previous run's data and reports a number the suite did not earn.
- **A test that pins current behaviour on purpose is inverted when the behaviour changes, not
  deleted.** The point is that somebody reads what changed.
- **Sweeps read the route list from Spring**, never from a list kept by hand — except one,
  which asks which routes the *app* needs, a question Spring cannot answer.
