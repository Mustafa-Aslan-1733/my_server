# Deployment Guide

This guide documents deployment behavior confirmed by repository files.

## Project Scope

This repository contains a Spring Boot backend service with PostgreSQL
persistence, Docker packaging, a Docker Compose local stack, GitLab CI/CD
configuration, API documentation, health/status endpoints, and optional SMTP,
GitLab issue, IPinfo, and Geoapify integrations.

## Prerequisites

| Tool or runtime | Confirmed version or usage |
| --- | --- |
| Java | Java 21 is configured in `pom.xml` and used by the Docker images. |
| Maven | The Maven wrapper uses Maven 3.9.15. |
| Spring Boot | `pom.xml` uses Spring Boot 4.0.6. |
| Docker | Used for image build, local Compose, and server deployment. |
| Docker Compose | Used by `docker-compose.yml`, `docker-compose.prod.yml`, CI deploy, and `scripts/docker-smoke-test.sh`. |
| PostgreSQL | Local Docker Compose uses `postgres:16`. |
| Mailpit | Local Docker Compose uses `axllent/mailpit:v1.31.1` as a mail catcher, so a local run can be signed into. Not used by any deployment. |
| Kaniko | CI image build uses `gcr.io/kaniko-project/executor:v1.23.2-debug`. |
| GitLab CI/CD | The pipeline is defined in `.gitlab-ci.yml`. |

The Maven wrapper is committed as `mvnw` and `mvnw.cmd`; the wrapper
configuration is in `.mvn/wrapper/maven-wrapper.properties`.

## System Components

| Component | Role | Confirmed source |
| --- | --- | --- |
| Backend API | Spring Boot application exposing authentication, account, catalogue, social, moderation, audit, health, and system status endpoints. | `src/main/java/com/pse`, `docs/admin-api.md`, `docker-compose.prod.yml` |
| PostgreSQL database | Runtime persistence for the backend. The app uses JDBC/JPA with PostgreSQL in normal runtime and H2 in tests. | `pom.xml`, `application.properties`, `docker-compose.yml` |
| SMTP server | Sends OTP login emails and successful-login notification emails. **A deployment needs a real one**: without it nobody can log in, because a login code is the only way in. | `HtmlMailSender`, `LoginCodeMail`, `application.properties`, `.env.example` |
| Mail catcher (local only) | Stands in for that SMTP server in the local Compose stack. The server mails the login code to it and a person reads the code out of its web inbox on port `8025`; nothing leaves the machine and no SMTP account is needed. | `docker-compose.yml`, `README.md` |
| Admin Web | The admin panel, served by its own nginx container under `/admin/` on the same host as this API. Its calls are therefore same-origin and do not use CORS. | `CorsConfig`, `.gitlab-ci.yml`, `docs/admin-api.md` |
| GitLab issue integration | Optional backend integration for creating GitLab issues from bug reports. | `RestClientGitLabClient`, `application.properties`, `.env.example` |
| IPinfo and Geoapify | Optional login-location enrichment for notification emails. | `LoginLocationService`, `.env.example` |
| GitLab Container Registry | Stores backend images built by CI. | `.gitlab-ci.yml` |
| BW Cloud/staging host | Deployment target reached by SSH from CI and updated with Docker. Answers to two hostnames on one address; see [Public address and request path](#public-address-and-request-path). | `.gitlab-ci.yml` |

## Configuration

The backend reads configuration from Spring Boot properties and environment
variables. `.env.example` lists expected variables. When running directly,
provide variables through the shell, process manager, or deployment environment.

Do not commit real secret values. `.env` is ignored by `.gitignore`.

### Backend Environment Variables

| Variable | Usage | Default or behavior confirmed in repository |
| --- | --- | --- |
| `SERVER_PORT` | Backend HTTP port. | Falls back to `PORT`, then `8080`. |
| `PORT` | Secondary backend HTTP port fallback. | Used only as fallback for `SERVER_PORT`. |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL. | Defaults to `jdbc:postgresql://localhost:5432/postgres`. CI staging defaults to `jdbc:postgresql://host.docker.internal:5432/postgres`. |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL username. | Defaults to `postgres`. |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL password. | Defaults to empty. Treat as secret. |
| `SPRING_FLYWAY_ENABLED` | Enables versioned Flyway migrations during backend startup. | Defaults to `true`. CI staging also defaults to `true`. |
| `SPRING_FLYWAY_BASELINE_ON_MIGRATE` | Allows the first Flyway deployment to baseline an existing non-empty database previously managed by Hibernate. | The application defaults to `true` for the pre-Flyway case. CI writes `false` for the deployed environment, which was baselined on 2026-09-05. |
| `ADMIN_FRONTEND_ORIGINS` | Comma-separated exact browser origins allowed by CORS. Does not apply to same-origin callers, which never reach the check. | Local default is `http://localhost:5173,http://127.0.0.1:5173`; CI deploys `https://ratemyprofessor.dev`. Wildcards are rejected. |
| `ADMIN_BOOTSTRAP_EMAILS` | Comma-separated `email` or `email:Display Name` entries promoted to admin on startup. | `application.properties` contains project defaults; `.env.example` contains an example admin email. |
| `ADMIN_SUPERUSER_EMAILS` | Comma-separated addresses of administrators that may also moderate other administrator accounts. | Must already be administrators; this list does not create them. Defaults to `unzhz@student.kit.edu`. Not a role: these accounts still report `ADMIN`. |
| `AUTH_RATE_LIMIT_HMAC_KEY` | HMAC key for rate-limit subject hashes. | Must contain at least 32 characters or startup fails. Treat as secret. |
| `AUTH_OTP_TTL` | OTP lifetime. | Defaults to `PT5M`. |
| `AUTH_ADMIN_SESSION_TTL` | Lifetime of a session issued by the admin API (`POST /admin/auth/login`). | Defaults to `P1D`. |
| `AUTH_APP_SESSION_TTL` | Lifetime of a session issued by the app API (`POST /auth/login`), for every account including administrators. | Defaults to `P365D`. `AUTH_STUDENT_SESSION_TTL`, the former name, is still read when this one is unset. |
| `AUTH_RATE_LIMIT_WINDOW` | Rate-limit window. | Defaults to `PT15M`. |
| `AUTH_RATE_LIMIT_REQUEST_EMAIL` | OTP request limit per email. | Defaults to `3`. |
| `AUTH_RATE_LIMIT_REQUEST_IP` | OTP request limit per IP. | Defaults to `20`. |
| `AUTH_RATE_LIMIT_LOGIN_EMAIL` | Login failure limit per email. | Defaults to `10`. |
| `AUTH_RATE_LIMIT_LOGIN_IP` | Login failure limit per IP. | Defaults to `30`. |
| `SPRING_MAIL_HOST` | SMTP host. | Defaults to `localhost`. |
| `SPRING_MAIL_PORT` | SMTP port. | Defaults to `25`; `.env.example` and CI use `587` as the deployment example/default. |
| `SPRING_MAIL_USERNAME` | SMTP username. | Defaults to empty. Treat as secret when used. |
| `SPRING_MAIL_PASSWORD` | SMTP password. | Defaults to empty. Treat as secret when used. |
| `SPRING_MAIL_SMTP_AUTH` | SMTP authentication toggle. | Defaults to `false`; CI staging defaults to `true`. |
| `SPRING_MAIL_STARTTLS` | SMTP STARTTLS toggle. | Defaults to `false`; CI staging defaults to `true`. |
| `MAIL_FROM_ADDRESS` | Sender email address. | Defaults to `no-reply@localhost`. |
| `MAIL_FROM_NAME` | Sender display name. | Defaults to `RateMyProfApp`. |
| `IPINFO_TOKEN` | IPinfo lookup token for login-location emails. | Empty value reports location as unknown. Treat as secret when set. |
| `GEOAPIFY_API_KEY` | Geoapify static map API key for login-location emails. | Empty value skips static map generation. Treat as secret when set. |
| `AUDIT_REVERT_WINDOW` | Time window for reverting administrative field edits. | Defaults to `P7D`. |
| `GITLAB_BASE_URL` | GitLab instance base URL for issue creation. | Empty value disables the integration. |
| `GITLAB_PROJECT_ID` | GitLab project id/path for issue creation. | Empty value disables the integration. |
| `GITLAB_TOKEN` | GitLab API token for issue creation. | Empty value disables the integration. Treat as secret. |
| `GITLAB_TIMEOUT` | GitLab connect/read timeout. | Defaults to `PT10S`. |

Notes:

- `ADMIN_FRONTEND_ORIGINS` must include the exact scheme, host, and port of each
  Admin Web origin.
- A CORS mismatch causes `403 Invalid CORS request` before controller logic runs.
- With any required `GITLAB_*` value empty, `GET /admin/system/status` reports
  `gitlabEnabled:false` and issue creation returns 503.

### GitLab CI/CD Variables

Deployment and scheduled maintenance jobs use these custom CI/CD variables:

| Variable | Purpose |
| --- | --- |
| `SSH_PRIVATE_KEY` | SSH key file used by the CI job for deployment. |
| `DEPLOY_HOST` | Deployment host. |
| `DEPLOY_PORT` | SSH port for the deployment host. |
| `DEPLOY_USER` | SSH user for the deployment host. |
| `DEPLOY_PATH` | Directory created and entered on the deployment host. Must be absolute: the value is used over SSH, where a relative path resolves against the login directory. The deploy job prepends a missing leading slash and says so in its log. |
| `BACKEND_PUBLISHED_PORT` | Optional host bind address and port for the Compose-managed backend. Defaults to `127.0.0.1:8080` in `docker-compose.prod.yml` and is also written that way by CI. |
| `SPRING_DATASOURCE_URL` | Optional override for the deployed container datasource URL. |
| `SPRING_DATASOURCE_USERNAME` | Optional override for the deployed container datasource username. |
| `SPRING_DATASOURCE_PASSWORD` | Deployed container datasource password. Secret. |
| `SPRING_FLYWAY_ENABLED` | Optional override for Flyway startup migrations. Defaults to `true` in CI. |
| `SPRING_FLYWAY_BASELINE_ON_MIGRATE` | Optional override for initial baselining of pre-Flyway databases. Defaults to `false` in CI. |
| `ADMIN_FRONTEND_ORIGINS` | Optional override for allowed frontend origins. |
| `ADMIN_BOOTSTRAP_EMAILS` | Optional override for admin bootstrap accounts. |
| `ADMIN_SUPERUSER_EMAILS` | Optional override for the administrators that may moderate other administrators. |
| `AUTH_RATE_LIMIT_HMAC_KEY` | Required deployed rate-limit HMAC key. Secret. |
| `AUTH_ADMIN_SESSION_TTL` | Optional override for the admin API session lifetime. Defaults to `P1D` in CI. |
| `AUTH_APP_SESSION_TTL` | Optional override for the app API session lifetime. Defaults to `P365D` in CI, falling back to `AUTH_STUDENT_SESSION_TTL` if that is what is set. |
| `SPRING_MAIL_HOST` | SMTP host. |
| `SPRING_MAIL_PORT` | SMTP port. |
| `SPRING_MAIL_USERNAME` | SMTP username. Secret when used. |
| `SPRING_MAIL_PASSWORD` | SMTP password. Secret. |
| `SPRING_MAIL_SMTP_AUTH` | SMTP authentication toggle. |
| `SPRING_MAIL_STARTTLS` | SMTP STARTTLS toggle. |
| `MAIL_FROM_ADDRESS` | Sender email address. |
| `MAIL_FROM_NAME` | Sender display name. |
| `IPINFO_TOKEN` | Optional IPinfo token. Secret when set. |
| `GEOAPIFY_API_KEY` | Optional Geoapify API key. Secret when set. |
| `SONAR_TOKEN` | SonarCloud analysis token. Secret. **Its presence is what enables the `sonarcloud` job** — until it is set, the job does not run and the pipeline is unchanged. Create it under My Account → Security on SonarCloud. |
| `SONAR_PROJECT_KEY` | SonarCloud project key, e.g. `kit-pse_backend`. Shown on the project's Information page after it is created. |
| `SONAR_ORGANIZATION` | SonarCloud organization key. Not the display name — the key under Organization → Administration. |
| `SONAR_HOST_URL` | Optional. Defaults to `https://sonarcloud.io`; set it only for a self-hosted SonarQube. |
| `AUDIT_REVERT_WINDOW` | Optional audit revert window override. |
| `GITLAB_BASE_URL` | Optional GitLab instance base URL for issue creation. |
| `GITLAB_PROJECT_ID` | Optional GitLab project id/path for issue creation. |
| `GITLAB_TOKEN` | Optional GitLab API token for issue creation. Secret when set. |
| `GITLAB_TIMEOUT` | Optional GitLab timeout override. Defaults to `PT10S`. |
| `BACKUP_PASSPHRASE` | Required by scheduled `backup:offsite`. Used to encrypt database backup artifacts before upload. Secret; configure as a masked CI/CD variable. |

GitLab built-in variables used by the pipeline include `CI_REGISTRY`,
`CI_REGISTRY_USER`, `CI_REGISTRY_PASSWORD`, `CI_REGISTRY_IMAGE`,
`CI_COMMIT_SHORT_SHA`, `CI_COMMIT_BRANCH`, `CI_DEFAULT_BRANCH`, and merge request
variables.

## Required Ports

| Port or variable | Direction | Purpose | Confirmed source |
| --- | --- | --- | --- |
| `8080` | Inbound to backend container/app | Backend HTTP server. Dockerfile exposes it and the app listens on it by default. Production Compose publishes it on the host loopback address by default through `BACKEND_PUBLISHED_PORT=127.0.0.1:8080`. | `application.properties`, `src/Dockerfile`, `docker-compose.yml`, `docker-compose.prod.yml`, `.gitlab-ci.yml` |
| `5432` | Inbound to PostgreSQL | Local Compose publishes PostgreSQL. CI staging defaults the backend datasource to `host.docker.internal:5432`. | `application.properties`, `docker-compose.yml`, `.gitlab-ci.yml` |
| `5173` | Browser origin | Local Admin Web origins in CORS defaults. | `application.properties`, `.env.example`, `CorsConfig` |
| `25` | Outbound from backend | Default SMTP port. | `application.properties` |
| `587` | Outbound from backend | SMTP port used in `.env.example` and CI staging default. | `.env.example`, `.gitlab-ci.yml` |
| `1025` | Inbound to the mail catcher | SMTP, local Compose only. The backend sends login codes here instead of to a real SMTP server. | `docker-compose.yml` |
| `8025` | Browser | The mail catcher's web inbox, local Compose only. This is where a login code is read. | `docker-compose.yml`, `README.md` |
| `DEPLOY_PORT` | Inbound SSH to server | CI deployment SSH port. | `.gitlab-ci.yml` |

## Public address and request path

**The panel's address is `https://ratemyprofessor.dev`.** That is the one to use, the one CI
health-checks, and the one in `ADMIN_FRONTEND_ORIGINS`.

One nginx on the host answers for both hostnames — they resolve to the same address — and
splits by path:

| Path | Goes to |
| --- | --- |
| `/admin/` and everything below it | the admin panel's own nginx container, which serves its static bundle and falls back to `index.html` |
| everything else | this backend, **with the path unchanged** |

Two consequences follow from that table, and both have been got wrong before.

**The panel's API base URL is empty, and must stay empty.** Because the panel is served from
the same host that proxies this backend, an empty base makes every one of its calls
same-origin: the browser sends them to `/auth/request-login` and CORS never runs. An absolute
URL breaks it — that is exactly what happened, the browser preflighted a second hostname and
this backend answered `403` because that origin was not allow-listed. `/api` breaks it too, for
a quieter reason: nothing on this host strips the prefix, so the request arrives here as
`/api/auth/login` and gets the backend's own `404`. Verify with
`curl -s https://ratemyprofessor.dev/api/health` — a `{"message":"Not found"}` body is this
backend answering, not nginx.

**The bw-cloud hostname must keep serving.** It is no longer a browser origin and no longer
appears in any default here, but it is not closed and must not be: the released Android client
has it compiled in (`RetrofitClient.java:31`, recorded in
[frontend-consumer-contract.md](frontend-consumer-contract.md)). Returning `444` would break
every installed copy, and a `301` would be worse — OkHttp downgrades a redirected `POST` to
`GET`, so logins would fail in a way that reads as a backend outage. Closing it is possible
only after a released Android client points somewhere else.

**The same-origin bypass depends on `server.forward-headers-strategy=framework`**
(`application.properties:3`). Spring decides "same origin" by comparing the `Origin` header
against the request URL it reconstructs, and behind this proxy that reconstruction is only
correct while the forwarded headers are honoured. Turn that off and same-origin calls start
failing CORS. The same note is on the property.


## Build Instructions

### Backend JAR

Run the full Maven verification build:

```bash
./mvnw verify
```

The Maven project artifact is `demo` version `0.0.1-SNAPSHOT`, so a package
build produces a jar under `target/`.

The Dockerfile builds the jar with:

```bash
./mvnw clean package -DskipTests
```

The runtime image starts it with:

```bash
java -jar app.jar
```

### Local Docker Compose Stack

Validate the Compose file:

```bash
docker compose config
```

Start the local backend and PostgreSQL stack:

```bash
docker compose up --build -d
```

Stop and remove the local stack:

```bash
docker compose down --remove-orphans
```

`docker-compose.yml` is the local development stack. It builds the backend image
from `src/Dockerfile`, starts `postgres:16` and `axllent/mailpit:v1.31.1`, waits for
PostgreSQL health, and publishes backend port `8080`, PostgreSQL port `5432`, and the
catcher's `1025` and `8025`.

**The mail catcher is what makes a local run usable rather than merely running.** Signing in
needs a one-time code delivered by e-mail, and there is no other way in — so with nothing
listening on SMTP, `POST /auth/request-login` answers `500 "Could not send login code"` and the
stack is an API nobody can authenticate against. The backend is pointed at the catcher with two
variables in its `environment:` block, `SPRING_MAIL_HOST` and `SPRING_MAIL_PORT`; the auth and
STARTTLS toggles already default to `false` in `application.properties`, which is what an
unauthenticated catcher wants. The code is then read from the web inbox at
`http://localhost:8025` — [README.md](../README.md) has the click-path.

The local stack also sets `ADMIN_BOOTSTRAP_EMAILS` to a single neutral address,
`admin@student.kit.edu`, so whoever starts it becomes an administrator. Left unset it falls back
to the project's own six addresses from `application.properties`, which is right for the
deployment and useless for anybody else: they would sign in successfully and then collect `403`
on every panel screen.

**None of this reaches a deployment.** `docker-compose.prod.yml` mails through a real SMTP server
and has no catcher; CI copies *that* file to the server, never this one.

`scripts/docker-smoke-test.sh` runs `docker compose config`, starts the stack,
checks `GET /health`, and tears the stack down on exit. It is a **local** helper: no CI
job calls it. The pipeline's equivalent is `server:postgres-integration`, which runs
the app test context against a `postgres:16` service instead of building the image. Run it
from the repository root, and note that it stops the stack and removes its volumes on
exit.

### Production Docker Compose Stack

`docker-compose.prod.yml` is the production deployment stack used by CI. It
keeps PostgreSQL outside Compose and manages only the backend application
container:

- Service: `backend`
- Container name: `app-server`
- Image: `${BACKEND_IMAGE}`, set by CI to
  `$CI_REGISTRY_IMAGE/server:$CI_COMMIT_SHORT_SHA`
- Restart policy: `unless-stopped`
- Published port: `${BACKEND_PUBLISHED_PORT:-127.0.0.1:8080}:${SERVER_PORT:-8080}`
- Host gateway: `host.docker.internal:host-gateway`
- Health check: `GET /health` inside the container
- Volumes: none

The production Compose service receives the same application environment
variables documented above. `SPRING_DATASOURCE_PASSWORD` and
`AUTH_RATE_LIMIT_HMAC_KEY` are required; optional integrations stay disabled when
their variables are empty.

## Database Setup and Schema Management

### Local Database

`docker-compose.yml` defines a local PostgreSQL service:

- Image: `postgres:16`
- Database name: `postgres`
- User: `postgres`
- Published port: `5432`
- Persistent volume: `postgres-data`
- Health check: `pg_isready -U postgres -d postgres`

The backend service in Compose connects to PostgreSQL at
`jdbc:postgresql://postgres:5432/postgres`.

### Deployed Database

The CI deployment starts the backend through `docker-compose.prod.yml` and passes
the datasource configuration through environment variables. PostgreSQL remains a
host or externally managed service, not a production Compose service. The
deployment job defaults the datasource URL to:

```text
jdbc:postgresql://host.docker.internal:5432/postgres
```

The deployed Compose service includes:

```text
extra_hosts:
  - "host.docker.internal:host-gateway"
```

### Schema Management

The runtime schema is managed by Flyway. Production migrations live under:

```text
src/main/resources/db/migration/postgresql
```

Test migrations for H2 live under:

```text
src/test/resources/db/migration/h2
```

The first migration, `V1__existing_schema_baseline.sql`, creates the current
schema for fresh databases. Existing non-empty databases from the pre-Flyway
period are baselined at version `1` on first startup. Flyway records applied
migrations in `flyway_schema_history`.

Hibernate no longer changes the schema at runtime:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

Startup fails if the Flyway-managed schema does not match the current JPA
entities. Before deploying against an existing database, take a database backup.

The deployed database was baselined on 2026-09-05 and has a `flyway_schema_history`
table, so CI writes `SPRING_FLYWAY_BASELINE_ON_MIGRATE=false`. A deployment aimed at a
database that has a schema but no history now fails instead of silently declaring that
schema to be version `1`. Set the variable back to `true` only for the single deployment
that adopts a new pre-Flyway database, then remove it again.

## Deployment to BW Cloud or Staging

The confirmed deployment path is the manual GitLab CI job
`server:deploy:staging`, available on the `main` branch for non-scheduled
pipelines.

The job:

1. Uses the image produced by `server:package`:
   `$CI_REGISTRY_IMAGE/server:$CI_COMMIT_SHORT_SHA`.
2. Installs `openssh-client` in an Alpine 3.20 CI image.
3. Uses `SSH_PRIVATE_KEY`, `DEPLOY_HOST`, `DEPLOY_PORT`, `DEPLOY_USER`, and
   `DEPLOY_PATH` to connect to the target server.
4. Creates `$DEPLOY_PATH` on the server.
5. Copies `docker-compose.prod.yml` to `$DEPLOY_PATH/docker-compose.yml`.
6. Writes the deployment `.env` file from CI/CD variables. Every variable in that
   block is written unconditionally, using the job's own fallback when the CI/CD
   variable is unset. A value set as a CI/CD variable therefore overrides both
   `application.properties` and the Compose defaults, and changing a default in the
   repository has no effect on the deployment until `.gitlab-ci.yml` changes with it.
7. Logs in to the GitLab Container Registry on the server.
8. Confirms `docker compose` is available on the server.
9. Pulls the image tag for the current commit through Compose.
10. Removes the legacy standalone `app-server` container if present, so the
    first Compose deployment can claim the same container name.
11. Starts or replaces the detached Compose service with
    `docker compose up -d --remove-orphans`. Flyway runs before the backend
    starts serving requests.
12. Polls `http://localhost:8080/health` inside the Compose service up to 30
    times. The health endpoint checks database reachability and verifies that
    Flyway has no failed or pending migrations.
13. Prints recent Compose service logs and fails the job if health never
    succeeds.

The deployment job defaults `ADMIN_FRONTEND_ORIGINS` to the BW Cloud/staging URL
used elsewhere in the repository and defaults the datasource URL to the Docker
host gateway PostgreSQL address listed above.

## CI/CD Pipeline Usage

The GitLab pipeline is defined in `.gitlab-ci.yml`.

### Which pipeline runs, and when

Two decisions happen in order, and conflating them is where "why did this commit run
different jobs than the last one?" comes from.

**First, `workflow:rules` decides whether a pipeline is created at all** and which
`CI_PIPELINE_SOURCE` it carries:

| What happened | Pipeline | `CI_PIPELINE_SOURCE` |
| --- | --- | --- |
| Push to a branch with **no** open merge request | one | `push` |
| Push to a branch that **has** an open merge request | one, belonging to the merge request | `merge_request_event`. The push pipeline is suppressed by `when: never` on purpose: without that rule every such push ran two full Maven builds and reported two statuses |
| Opening or updating a merge request | one | `merge_request_event` |
| A pipeline schedule fires | one | `schedule` |

The same commit therefore produces a differently-shaped pipeline depending on something that
is not in the commit — whether a merge request was open for its branch at the time.

**Then each job's own `rules` decide whether that job is in the pipeline.** Nothing here runs
everywhere, which is the whole design: the scheduled pipeline exists for the jobs that watch
the deployed server, and the push pipeline exists for the jobs that check the change.

| Job | `push` | `merge_request_event` | `schedule` |
| --- | --- | --- | --- |
| `server:test` | yes | yes | no |
| `coverage:line-target` | yes | yes | no |
| `sonarcloud` | only if `SONAR_TOKEN` is set | only if `SONAR_TOKEN` is set | no |
| `server:postgres-integration` | yes | yes | no |
| `secrets:new` | yes | yes | no |
| `dependency:scan` | yes | yes | no |
| `server:package` | `main` only | no | no |
| `server:deploy:staging` | `main` only, manual | no | no |
| `secrets:history` | no | no | yes |
| `mutation:nightly` | no | no | yes |
| `health:check` | no | no | yes |
| `backup:offsite` | no | no | yes |

`server:package` and `server:deploy:staging` test `$CI_COMMIT_BRANCH == "main"`, and that
variable is not set in a merge request pipeline at all — which is why a merge request never
builds an image, however green it is. The image is built when the merge lands on `main`.

Three further reasons two pipelines can differ without `.gitlab-ci.yml` changing:

- **A CI/CD variable can add or remove a job.** `sonarcloud` exists only while `SONAR_TOKEN`
  is set, and `backup:offsite` is `allow_failure` or not depending on whether
  `BACKUP_PASSPHRASE` is set. Both are deliberate, and both mean the settings page is part of
  the pipeline definition.
- **`needs:` makes the pipeline a DAG, not a queue.** `coverage:line-target` and `sonarcloud`
  start as soon as `server:test` finishes rather than when the whole `test` stage does, so the
  running order on screen differs between two pipelines that ran identical jobs.
- **A schedule carries its own ref and its own variables**, so a scheduled pipeline is not the
  nightly twin of the last push pipeline — it is a different set of jobs against whatever
  branch the schedule names.

### Stages and jobs

Stages:

- `test`
- `package`
- `deploy`

Jobs:

| Job | When it runs | What it does |
| --- | --- | --- |
| `server:test` | Push and merge request pipelines, never scheduled | Runs `./mvnw -B -ntp verify`, then `java scripts/CheckChangedCoverage.java --threshold "$COVERAGE_THRESHOLD"`, then `java scripts/CheckLineCoverage.java` against the two line-coverage tiers. Publishes JUnit and JaCoCo artifacts. The changed-code gate needs a comparison ref; when it cannot resolve one it says so and passes, rather than failing a build over a missing base commit. |
| `coverage:line-target` | Push and merge request pipelines, never scheduled | Reuses `server:test`'s JaCoCo artifact and re-checks bundle line coverage against `COVERAGE_LINE_TARGET`. Marked `allow_failure: true`, so missing the target is a **yellow warning that does not block the pipeline**. Costs a container start, not a rebuild. |
| `sonarcloud` | Push and merge request pipelines, **only while `SONAR_TOKEN` is set**, `allow_failure: true` | Reuses `server:test`'s JaCoCo XML through `needs: artifacts` rather than re-running the suite, so Sonar and `jacoco:check` report the same number by construction. Allowed to fail for now on purpose: the first scan of a codebase this size raises hundreds of issues, none of them regressions, and a red pipeline nobody can act on only teaches people to ignore it. Turn that off once a baseline has been read and a quality gate agreed. |
| `server:postgres-integration` | Push and merge request pipelines | Everything that needs a real PostgreSQL rather than H2, in **two Maven invocations** against one `postgres:16` CI service. The first runs the integration layer: the production Flyway migrations from `src/main/resources/db/migration/postgresql`, `/health` against them, keyset pagination on a real `timestamp(6)`, the `jsonb` round trip the revert feature reads its values back out of, and the read-only transaction sweep. The second runs the six end-to-end journeys, over real HTTP on a random port. They are separate invocations because they need different Spring contexts and two contexts against one database would reset the schema under each other; separate JVMs cannot interleave. Each invocation is checked on its own: the job greps its log and **fails unless exactly one context started**. Classes are **named** in `-Dtest=` rather than pattern-matched, so adding one is the moment to check which annotation it carries. A CI service is a plain TCP server, so none of this needs a Docker socket, a privileged runner or a Testcontainers dependency. |
| `server:package` | `main` branch, non-scheduled pipelines | Uses Kaniko to build `src/Dockerfile` and push `$CI_REGISTRY_IMAGE/server:$CI_COMMIT_SHORT_SHA`. |
| `server:deploy:staging` | Manual job on `main`, non-scheduled pipelines | SSHes to the deployment host and replaces the Compose-managed `backend` service. |
| `secrets:new` | Push and merge request pipelines, never scheduled | Runs gitleaks over **only the commits the push or merge request brings**, so it starts green and turns red when a credential is committed. Blocking, on purpose: that is while it is still cheap to fix. Which commits are new depends on the trigger and neither answer is trusted without checking — `CI_COMMIT_BEFORE_SHA` is all-zeros on a branch's first push and stale after a force-push, and a target branch may not be fetched. Each candidate is verified before use and the fallback is to scan nothing here and say so, deferring to `secrets:history`. |
| `secrets:history` | Scheduled pipelines only, `allow_failure: true` | gitleaks over the **whole history**, reporting credentials that still need rotating at their provider. Expected to be red until `docs/TODO.md`'s rotation list is done: editing a secret out of a file does not unpublish it. Deliberately **no baseline file** — a baseline would make this green while the credentials are still live. `.gitleaksignore` in the repository root is how a finding is retired instead: one line per finding, uncommented only once that credential has been rotated at its provider, so the job gets less red per rotation and green after the last one. |
| `dependency:scan` | Push and merge request pipelines, never scheduled | Trivy over the dependency tree, **failing the pipeline on `HIGH` or `CRITICAL`**; the full JSON report is an artifact either way. It scans an **SBOM**, not `pom.xml`: `server:test` writes `target/classes/META-INF/sbom/application.cdx.json` (cyclonedx-maven-plugin, declared in the pom) and hands it over as an artifact. Reading `pom.xml` directly makes Trivy resolve the Maven parent chain itself, over the network, and that is how the first run of this job died — `429 Too Many Requests` from Maven Central with `Retry-After: 1800`, from an IP the whole runner cluster shares. A warm `.m2` avoids the fetch, but **this runner has no shared cache server**: a cache exists only on the Kubernetes node that wrote it, and `needs:` orders two jobs without putting them on the same node. An artifact is the handover that cannot silently miss. Blocking rather than `allow_failure`, because it was green the day it landed. Trivy's own vulnerability database is still downloaded per run; if *that* is rate-limited the job is red without a finding, and the first lines of the log say which it is. |
| `mutation:nightly` | Scheduled pipelines only, `allow_failure: true` | Runs PIT through the `pitest` profile: mutates the service classes and re-runs the eighteen context-free service unit tests against each mutant, in roughly two minutes. Publishes the HTML report. No score threshold yet. **`SURVIVED` means those eighteen classes do not kill the mutant, not that the suite misses it** — the API, integration and end-to-end layers are not running in this profile. See P-7 in `docs/test-findings.md`. |
| `health:check` | Scheduled pipelines only | Curls the configured BW Cloud/staging `/health` URL and expects HTTP 200. |
| `backup:offsite` | Scheduled pipelines only | Copies the newest server-side PostgreSQL dump, verifies it, encrypts it with `BACKUP_PASSPHRASE`, and stores only the encrypted `.gpg` artifact. |

Coverage is configured in three variables, and they answer different questions:

| Variable | Value | Meaning |
| --- | --- | --- |
| `COVERAGE_THRESHOLD` | `30` | Minimum coverage of the lines a merge request *changed*. |
| `COVERAGE_LINE_MIN` | `90` | Mandatory bundle line coverage. Below it the pipeline is **red**. Kept in step with the `LINE` minimum in the pom's `jacoco:check`, which enforces the same floor on a local `mvnw verify` — move one, move the other. |
| `COVERAGE_LINE_TARGET` | `95` | The bundle line coverage the suite aims for. Below it `coverage:line-target` goes **yellow** and the pipeline still passes. |

Branch coverage has a single floor of `0.88`, in the pom only.

Scheduled `backup:offsite` is expected to fail when `BACKUP_PASSPHRASE` is not
configured. This prevents CI from uploading a readable dump that contains real
student email addresses.

**Three scheduled jobs are `allow_failure` and two of them are expected to be red.**
`backup:offsite` without its passphrase, and `secrets:history` until the rotations are done.
The reason they cannot be allowed to block is `health:check`, which shares the same scheduled
pipeline and exists to be believed when it goes red — the server was down for twelve days in
July 2026 before it was added. A predictable red must never be able to bury it.

## Verifying a Successful Deployment

Use the checks that are explicitly supported by the source and CI configuration:

1. Confirm the backend health endpoint:

   ```bash
   curl -fsS http://localhost:8080/health
   ```

   Expected response:

   ```json
   {"message":"API healthy","success":true}
   ```

   In CD this is the migration gate: the endpoint fails when the database is
   unreachable, when Flyway reports a failed migration, when Flyway has pending
   migrations, or when no migration has been applied or baselined.

2. Confirm the public address, which is what CI health-checks:

   ```bash
   curl -fsS https://ratemyprofessor.dev/health
   ```

   Then confirm the bw-cloud hostname still answers. It is not a browser origin any more, but
   the released Android client is compiled against it and closing it would break every
   installed copy:

   ```bash
   curl -fsS https://8a1babdc-cf6d-4fdc-80a7-dc585f5853ed.ka.bw-cloud-instance.org/health
   ```

3. For an authenticated admin deployment, use `GET /admin/system/status` from the
   Admin Web or an authenticated client. It reports overall status, database
   reachability, counts, last write metadata, and `gitlabEnabled`.

4. Confirm the two APIs hand out the sessions they should. Log in through
   `POST /admin/auth/request-login` and `POST /admin/auth/login` with an administrator
   address: the response `expiresAt` should be about a day out. The same address through
   `POST /auth/request-login` and `POST /auth/login` should come back with an `expiresAt`
   about a year out, and a non-administrator address on `/admin/auth/login` should be
   refused with `403 Admin access required`.

5. Verify the panel's own call reaches a controller. Note the path: `/auth/request-login`, the
   unprefixed one the panel actually calls, **not** `/admin/auth/request-login`, which the
   panel's static server shadows on this host.

   ```bash
   curl -i -X POST -H "Origin: https://ratemyprofessor.dev" -H "Content-Type: application/json" -d '{"email":"nobody@example.invalid"}' https://ratemyprofessor.dev/auth/request-login
   ```

   `400 Invalid KIT email` means the request reached controller validation. `403 Invalid CORS
   request` means it did not — the bundle is calling a different origin than the one serving
   it, so rebuild the panel with an empty API base rather than adding an origin here.

   **This costs nothing from the login-code budget.** `LoginCodeService.requestLogin` validates
   the KIT address and throws before either `rateLimitService.consume` call, so neither the
   per-email (3) nor the per-IP (20) bucket is touched by an invalid address.

   To check the allow-list itself rather than the panel, send an origin that is definitely not
   on it — `-H "Origin: https://evil.example.invalid"` — and expect `403`. A same-origin call
   never reaches the check at all, so it cannot tell you whether the list is right.

6. Check recent deployment logs. The CI deployment job prints the last 100 lines
   after a successful health check and the last 200 lines after a failed health
   check.

7. When you have direct database access, inspect Flyway's migration history:

   ```sql
   select installed_rank, version, description, type, success
   from flyway_schema_history
   order by installed_rank;
   ```

   On existing databases adopted by Flyway, version `1` may appear as a
   `BASELINE` row. On fresh databases, version `1` appears as a normal SQL
   migration. Future schema changes should appear as `V2`, `V3`, and so on.

## Updating and Restarting

### Through CI

The confirmed update path is:

1. Merge or push the desired commit to `main`.
2. Let `server:test` and `server:package` run.
3. Start the manual `server:deploy:staging` job.
4. Wait for the job's `/health` polling loop to succeed. If Flyway migration or
   Hibernate schema validation fails, the backend does not become healthy and
   the deployment job fails.

The deployment job updates the Compose-managed `backend` service from the
current commit image tag. The first Compose deployment removes the previous
standalone `app-server` container so the Compose service can reuse the existing
container name.

### Host reboot

`unattended-upgrades` installs kernel updates but cannot apply them, so
`/var/run/reboot-required` accumulates until the host restarts. The restart is short and
self-healing: all three containers are `restart=unless-stopped`, and
`app-server-netfix.service` reattaches the backend to the Docker bridge on the way up.

Measured on 2026-09-06, going from kernel `6.8.0-134` to `6.8.0-139`:

| Elapsed | Event |
| --- | --- |
| 0s | `systemctl reboot`; shutdown begins |
| 13s | New kernel starts. The host is unreachable only for these thirteen seconds. |
| 26s | Docker is up; `pse-postgres` and `admin-web-prod` are running |
| 31s | `app-server-netfix.service` finished, having restarted `app-server` |
| 36s | Flyway reports the schema up to date, no migration to run |
| 44s | Tomcat is listening on 8080 and the API answers again |

So the API is gone for **about 45 seconds**, not the several minutes a maintenance window
would suggest. Systemd itself reported `3.0s (kernel) + 20.6s (userspace)`.

Before rebooting, confirm the day's dump is in `/var/backups/postgres`. Afterwards check
`/health` over HTTPS, `/admin/`, and that `journalctl -u app-server-netfix.service -b 0`
shows a run that exited 0 — a failure there is the case where `app-server` comes back
without a working bridge attachment.

### Local Docker Compose

Rebuild and restart the local stack:

```bash
docker compose up --build -d
```

Stop the local stack:

```bash
docker compose down --remove-orphans
```

Inspect local container state and logs:

```bash
docker compose ps
docker compose logs backend
```

## Server Maintenance Units

Three systemd units run on the deployment host. None of them is part of an image or of this
pipeline. They are version-controlled in `scripts/ops/`, which also carries the install
commands; before that directory existed they lived only on the host, where a rebuilt
instance would have lost them without anything failing loudly.

| Unit | Schedule | What it does |
| --- | --- | --- |
| `pg-backup.timer` → `/usr/local/bin/pg-backup.sh` | Daily, 00:00 UTC + up to 15 min | `pg_dumpall` of the `pse-postgres` container into `/var/backups/postgres`. Written under a `.partial` name and published only after `gzip -t` and the cluster-dump completion marker both pass, so a truncated file never looks like a backup. Keeps 14 days. |
| `docker-image-gc.timer` → `/usr/local/bin/docker-image-gc.sh` | Weekly, Monday 00:00 UTC + up to 1 h | Removes images that no container references and that are older than `KEEP_HOURS` (72). Every deploy pulls another SHA-tagged image onto an 11 GB disk and nothing else deleted the old ones. Volumes are deliberately not pruned. |
| `app-server-netfix.service` | At boot | Reattaches `app-server` to the Docker bridge and restarts it. Its veth does not always come back cleanly after a host reboot. |

The scheduled `backup:offsite` job above is the off-machine half of the first row: every
dump `pg-backup.sh` writes lives on the same disk as the database it came from.

## Basic Troubleshooting

| Symptom | Confirmed cause or check |
| --- | --- |
| Application fails during startup with `AUTH_RATE_LIMIT_HMAC_KEY must contain at least 32 characters` | Set `AUTH_RATE_LIMIT_HMAC_KEY` to an unpredictable value with at least 32 characters. |
| Browser requests fail with `403 Invalid CORS request` | Set `ADMIN_FRONTEND_ORIGINS` to the exact Admin Web origin, including scheme and port. Wildcards are rejected. |
| `GET /health` returns 503 | The backend could not run `SELECT 1` against the configured database, or Flyway reports failed/pending migrations. Check datasource URL, username, password, PostgreSQL reachability, migration logs, `flyway_schema_history`, and port `5432`. |
| `GET /admin/system/status` reports database down or degraded | The database probe or follow-up reads failed. Check database availability and backend logs. |
| GitLab issue creation returns 503 | `GITLAB_BASE_URL`, `GITLAB_PROJECT_ID`, or `GITLAB_TOKEN` is unset, so the integration is disabled. |
| GitLab issue creation returns bad gateway | The backend could not reach GitLab or GitLab did not return the expected issue payload. Check `GITLAB_BASE_URL`, token permissions, network access, and `GITLAB_TIMEOUT`. |
| Admin API rejects every caller | `ADMIN_BOOTSTRAP_EMAILS` may be empty. The startup runner logs a warning when no administrator emails are configured. |
| `scripts/docker-smoke-test.sh` fails | Local helper only, not a CI job. It checks `GET /health`, the same endpoint used by the Dockerfile, CI deploy, and scheduled CI health checks. Check PostgreSQL reachability first because `/health` performs `SELECT 1`. |
