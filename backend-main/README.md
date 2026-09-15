# Backend

The server behind a KIT lecture-rating app: students rate lectures and professors, ask and
answer questions, and report content; administrators moderate all of it through a separate
admin panel. Spring Boot 4 on Java 21, PostgreSQL with Flyway migrations.

The API is split in two. `/auth/**` and the app routes serve the Android client and hand out
year-long sessions; `/admin/**` serves the admin panel and hands out one-day sessions. Which
session you get is decided by the endpoint you called, not by whether your account is an
administrator — see [docs/admin-api.md](docs/admin-api.md).

**Deployed, the panel calls the unprefixed routes, not `/admin/**`.** One nginx serves
`https://ratemyprofessor.dev`: `/admin/` is the panel's own static container, everything else is
this backend with the path unchanged. The `/admin/**` twins exist in the application and the
suite exercises them, but on that host the panel's static server shadows them. This is settled,
not a migration in progress — [docs/deployment.md](docs/deployment.md) has the reasoning.

## The one access address, and why the panel's API base URL is relative

`https://ratemyprofessor.dev` is the address. The panel is built with an **empty**
`VITE_API_BASE_URL` so that every call it makes is same-origin, which is what keeps CORS out of
the request path entirely — a same-origin caller never reaches the allowlist.

Do not give the panel an absolute backend URL. That is what was deployed, and it made the panel
impossible to log into: the browser saw a second hostname, preflighted, and the backend
answered `403` because that origin was not allow-listed. `/api` is not a substitute either —
nothing on the host strips such a prefix, so the request arrives here intact and `404`s.

`ADMIN_FRONTEND_ORIGINS` still matters, for callers that are *not* same-origin. It holds exact
origins only; wildcards are rejected at startup.

A second hostname (`…ka.bw-cloud-instance.org`) resolves to the same server and still serves the
API. It is not an access address and is not in any allowlist, but it must stay up: the released
Android client is compiled against it. See [docs/deployment.md](docs/deployment.md).

## Build and test

```bash
./mvnw verify
```

`verify`, not `test`: the JaCoCo coverage gate (`jacoco:check`) runs in the `verify` phase, and
`test` alone will not enforce it. **The local build needs no Docker.** The suite runs against
in-memory H2, and the tests that require a real PostgreSQL skip themselves unless
`POSTGRES_SMOKE_JDBC_URL` is set — CI provides it through a `postgres:16` service.

The suite has five layers: unit, API/role, contract, integration and end-to-end. The last two
need a real PostgreSQL and run in CI; everything else runs anywhere. The integration layer also
holds the concurrency tests, which run two requests at once against a database that can actually
make them collide.

To run those PostgreSQL tests locally, point them at any PostgreSQL 16:

```bash
POSTGRES_SMOKE_JDBC_URL=jdbc:postgresql://localhost:5432/postgres \
POSTGRES_SMOKE_USERNAME=postgres POSTGRES_SMOKE_PASSWORD=your-password \
./mvnw -Dtest='*PostgresTests,PostgreSqlMigrationSmokeTests' test

# the end-to-end journeys, which need their own Spring context and so their own run
POSTGRES_SMOKE_JDBC_URL=jdbc:postgresql://localhost:5432/postgres \
POSTGRES_SMOKE_USERNAME=postgres POSTGRES_SMOKE_PASSWORD=your-password \
./mvnw -Dtest=EndToEndJourneyTests test
```

They drop and recreate the `public` schema, so give them a database you do not mind losing.

### Mutation testing

```bash
./mvnw -Ppitest test-compile org.pitest:pitest-maven:mutationCoverage
```

Roughly two minutes, and the report lands in `target/pit-reports/`. It runs nightly in CI as
well. Coverage tells you a line ran; this tells you whether any assertion would have failed had
the line been wrong.

Read `SURVIVED` carefully: the profile runs only the service unit tests, so a survivor may still
be caught by the API, integration or end-to-end layers. It is a candidate to investigate, not a
defect — `docs/test-findings.md` records under P-7 what happens when that distinction is missed.

### Coverage

Bundle line coverage is gated in two tiers, and both numbers live in `.gitlab-ci.yml`:

| Tier | Value | Effect |
| --- | --- | --- |
| `COVERAGE_LINE_MIN` | 90% | Mandatory. Below it the build fails, locally and in CI. |
| `COVERAGE_LINE_TARGET` | 95% | Advisory. Below it `coverage:line-target` goes yellow and the pipeline still passes. |

Branch coverage has a single mandatory floor of 88%. The floor in `pom.xml` and
`COVERAGE_LINE_MIN` are kept in step by hand — move one, move the other.

## Running it locally

```bash
docker compose up --build
```

Brings up PostgreSQL 16, a mail catcher, and the server on `localhost:8080`, with development
credentials already set in `docker-compose.yml`. `GET /health` answers once Flyway has migrated.

**The mail catcher is there because signing in is impossible without it.** A login is a one-time
code delivered by e-mail and there is no other way in — no password, no seeded session. Point the
server at nothing and `POST /auth/request-login` answers `500 "Could not send login code"`, which
is a running API that nobody can authenticate against. So the local stack runs
[Mailpit](https://mailpit.axllent.org/) and the server mails the code to it instead of to the
internet. Nothing to configure and no SMTP account to obtain.

**Signing in, end to end:**

1. Ask for a code. Any address on the bootstrap list works — see the next section:

   ```bash
   curl -s localhost:8080/auth/request-login \
     -H 'Content-Type: application/json' \
     -d '{"email":"admin@student.kit.edu"}'
   ```

2. **Open <http://localhost:8025>** and read the code out of the message. That is Mailpit's
   inbox; nothing leaves the machine.
3. Exchange it for a session:

   ```bash
   curl -s localhost:8080/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"email":"admin@student.kit.edu","loginToken":"THE-CODE"}'
   ```

   The `authToken` in the response is a bearer token: `Authorization: Bearer <token>`.

**Only the local stack does this.** `docker-compose.prod.yml` mails through a real SMTP server,
and the deployed instance always has. Nothing here changes that.

For a deployment rather than a local run, `docker-compose.prod.yml` and `.env.example` are the
starting points, and [docs/deployment.md](docs/deployment.md) is the description.

## Becoming an administrator

**Being able to log in is not the same as being able to use the admin panel**, and getting this
wrong looks like a broken product rather than a missing configuration line. An account that is
not an administrator signs in perfectly well and then collects `403` on every panel screen.

Administrators are made from a list, not from a flag on an account:
`app.auth.admin-bootstrap-emails` (`ADMIN_BOOTSTRAP_EMAILS`). An address on it gets an `admins`
row **at startup**, and also on its first login if it was added while the server was already
running. The entry is `email` or `email:Display Name` — the name is optional and is only used if
the account has to be created.

**Locally there is nothing to do**: `docker-compose.yml` sets the list to
`admin@student.kit.edu`, which is the address used in the commands above. Sign in with it and you
are an administrator.

To use **your own** address instead, extend the list on the `backend` service:

```yaml
    environment:
      ADMIN_BOOTSTRAP_EMAILS: admin@student.kit.edu,your.address@student.kit.edu
```

then `docker compose up -d --force-recreate backend`.

**On a deployment**, set the `ADMIN_BOOTSTRAP_EMAILS` CI/CD variable and redeploy —
[docs/deployment.md](docs/deployment.md) has the variable table and the pipeline.

Then sign in with that address exactly as above: **the list alone does nothing until the account
exists**, and the account is created by the first login, whose code still has to come out of
Mailpit.

`ADMIN_SUPERUSER_EMAILS` is a different thing and is not what makes an administrator — it decides
which administrators may moderate *other* administrators.

## Changing the API

**Every change a client can observe gets a `CHANGELOG` entry, in the same commit that makes
it.** Not at the end of the work, and not in a tidy-up pass afterwards: the `CHANGELOG` is not
a record for us, it is the only thing the app and panel teams read. A change they discover from
a failing request has already cost them a debugging session.

What counts as client-visible:

- a new or removed route, or a changed path
- a field added to or removed from a response
- a changed status code
- a changed message a client matches on
- a new enum value that can appear in a response

Internal refactoring, tests and CI do not count, and **a change that alters no interface gets
no entry** — padding the file with internals is how it stops being read, and then a real entry
is missed. When unsure, ask what a client would have to change, and write that.

Write what they must do, not what we did. Give `Was:` and `Now:` on the record, the status code
both before and after, and one line saying whether they have anything to change. Entries go
under a dated header (`///// 8.09`, with `(2)` and `(3)` when a day has more than one), newest
at the bottom. Record shapes are pasted whole with `+++`, `++` and `--` marking the changed
lines. The file has been written this way since July — match it rather than inventing a shape.

## Documentation

| File | Holds |
| --- | --- |
| [docs/test-plan.md](docs/test-plan.md) | What is tested, at which layer, and why |
| [docs/test-findings.md](docs/test-findings.md) | What the tests found; every entry ends in a decision |
| [docs/admin-api.md](docs/admin-api.md) | The admin API contract |
| [docs/adminweb-tasks.md](docs/adminweb-tasks.md) | The work list handed to the admin panel repo |
| [docs/deployment.md](docs/deployment.md) | Pipeline, CI/CD variables, deployment |
| [docs/TODO.md](docs/TODO.md) | Team working notes |
| [Wiki.md](Wiki.md) | The wiki source: domain vocabulary, and how the three repositories fit together |
| [CHANGELOG](CHANGELOG) | Hand-maintained interface changes, newest at the bottom |

The OpenAPI schema is generated from the controllers rather than written by hand, and is served
at `/v3/api-docs` to authenticated administrators.
