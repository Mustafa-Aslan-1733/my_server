# Consumer contract — `admin-web`

What the KIT admin panel actually sends to the backend, and what it actually reads
back. Written by reading `src/` only; every claim carries a file and line.

This file is meant to be read by a sweep in the backend repository. The endpoint
sections are uniform: same headings, same order, one section per path+verb.

- **Repository:** `admin-web` (React 19 + TypeScript, Vite 8)
- **Read at:** commit `b49a84b`, branch `hard-refactoring`, 2026-09-09
- **Endpoints called:** 42
- **Source of truth:** call sites in `src/api/**`, field reads in `src/features/**`,
  `src/components/**`, `src/context/**`. Type declarations were **not** used to
  populate the endpoint list — see [Defined but never called](#defined-but-never-called).

---

## Path resolution

Every path in this document is the **complete path the backend sees**. Nothing is
prepended by the client at runtime.

`ApiRequestBuilder.url()` concatenates the base URL and the literal path with no
normalisation (`src/api/client/apiRequest.ts:70-72`). The base URL comes from
`VITE_API_BASE_URL`, defaulting to `http://localhost:8080`
(`src/api/client/ApiClient.ts:28`, `:138`).

| Environment | `VITE_API_BASE_URL` | What the backend receives |
| --- | --- | --- |
| Development | `/api` (`.env:5`) | The Vite dev proxy forwards `/api/**` to the backend and **strips** `/api` (`vite.config.ts:50-56`, `rewrite: (path) => path.replace(/^\/api/, '')`). |
| Production (CI) | `https://<backend-host>` (`.gitlab-ci.yml:13`, `Dockerfile:14`) | Origin only, no path segment. |

**In both cases the backend sees the literal path written in the service file.**

### There is no `/admin` prefix

This client sends **no** `/admin` prefix on any request. All 42 paths below are the
legacy, unprefixed ones. This is deliberate and already recorded on the backend's
side as `H2` in `docs/backend-tasks.md:221-241` and as `F-005` in
`docs/testfindings.md:15`.

### Path parameters are not escaped

Ids are interpolated raw into template literals — for example
`` `/users/${id}` `` (`src/api/users/UserAccountsService.ts:49`). There is no
`encodeURIComponent` anywhere in `src/`. An id containing `/`, `?`, `#` or a space
reaches the backend as a differently-shaped path, not as an escaped segment.

---

## How the client decides success and failure

**This is decided in exactly one place for every endpoint**:
`ApiResponseParser.parse` (`src/api/client/apiResponse.ts:86-115`). No endpoint
overrides it. Per-endpoint sections below therefore repeat the same answer, and only
note what each one adds on top.

Order of evaluation:

1. **HTTP status first.** `!response.ok` (status outside 200–299) throws a typed
   error and the body is never treated as a payload
   (`src/api/client/apiResponse.ts:87-89`).
2. **`204 No Content`** resolves to `undefined` without reading a body
   (`src/api/client/apiResponse.ts:95`).
3. **2xx with an empty body** throws `ContractError`
   (`src/api/client/apiResponse.ts:97-100`).
4. **2xx with a non-JSON body** throws `ContractError`
   (`src/api/client/apiResponse.ts:117-125`).
5. **2xx with `body.success === false`** throws `RefusedError`
   (`src/api/client/apiResponse.ts:27-30`, `:106-112`). Strict `=== false`; a missing
   `success` key is **not** a refusal.
6. Otherwise the parsed body is returned as the payload.

**So, for every endpoint in this document, the answer to "how does the client know it
succeeded?" is: BOTH — the HTTP status is checked first, and the body's `success`
field is checked second.** There is no endpoint that looks only at `success`, and
none that looks only at the status.

### Status → error type

`src/api/client/apiErrors.ts:183-194`, via `responseErrorFor`
(`src/api/client/apiErrors.ts:206-216`):

| Status | Error type | `transient` (retryable) |
| --- | --- | --- |
| 400 | `BadRequestError` | no |
| 401 | `UnauthorizedError` | no |
| 403 | `ForbiddenError` | no |
| 404 | `NotFoundError` | no |
| 409 | `ConflictError` | no |
| 429 | `RateLimitedError` (carries `retryAfterSeconds`) | no |
| any other < 500 | `ResponseError` | no |
| >= 500 | `ResponseError` | **yes** (`src/api/client/apiErrors.ts:102-104`) |
| — (fetch threw) | `NetworkError` | yes (`src/api/client/ApiClient.ts:123-129`) |
| 2xx, `success: false` | `RefusedError` | no (`src/api/client/apiErrors.ts:171-176`) |
| 2xx, unparseable | `ContractError` | no |

**405 and 415 have no dedicated type.** They arrive as a plain `ResponseError`
carrying `status: 405` / `415`. Nothing in the client reads `.status` off a
`ResponseError`, so the two are indistinguishable from any other 4xx at every call
site. The `Allow` header on a 405 is **never read** — response headers are read only
for `Retry-After` (`src/api/client/apiResponse.ts:37-42`).

### What is read out of a failing response

- **Message:** `body.message`, if it is a non-blank string; otherwise the fallback
  `Request to {path} failed with status {status}`
  (`src/api/client/apiResponse.ts:127-138`, `:16-19`). A body that is not JSON falls
  through to the same fallback.
- **Refusal code:** `body.reason`, if it is a non-blank string; otherwise `null`
  (`src/api/client/apiResponse.ts:21-24`). Carried on `ApiError.reason`
  (`src/api/client/apiErrors.ts:39`).
- **`Retry-After`:** parsed as an integer number of seconds. The HTTP-date form is
  treated as absent rather than parsed (`src/api/client/apiResponse.ts:37-42`).
  Attached only to `RateLimitedError`.

### Global side effects

- **401 ends the session.** A `401` on a request sent *with* a bearer token calls the
  session-expiry listener (`src/api/client/ApiClient.ts:116-118`), which
  `AuthContext` wires to clearing the stored session and returning to the login
  screen (`src/context/AuthContext.tsx:37-40`, `:64`). A `401` with **no** token set
  does not end anything (`src/api/client/apiRequest.ts:38-40`).
- **Retries.** Queries retry a failure at most twice, and only when the error reports
  `transient` (`src/query.ts:27-30`). In practice: 5xx and network failures are
  retried; every 4xx, every refusal and every contract violation is not. Mutations
  are never retried (`src/query.ts:43`).

### Request headers

- `Content-Type: application/json` is sent **only when a body is present**
  (`src/api/client/apiRequest.ts:106`). Bodyless `POST`/`PATCH` calls
  (`JSON.stringify(undefined) === undefined`) therefore send **no** `Content-Type`
  and no body at all. That affects `POST /auth/logout`,
  `POST /audit-logs/{id}/revert`, `POST /reports/{id}/gitlab-issue`,
  `PATCH /users/{id}/block` and `PATCH /users/{id}/unblock`.
- `Authorization: Bearer <token>` is sent on **every** request once a session token is
  installed (`src/api/client/apiRequest.ts:108-109`), including the public `/data/*`
  catalogue reads.
- No other header is set. No `Accept`, no cookies, no credentials mode.

### Query-string construction

`ApiRequestBuilder.withQueryString` (`src/api/client/apiRequest.ts:85-94`) drops any
parameter whose value is `undefined`, `null` **or the empty string**, and stringifies
the rest with `String(value)`. When nothing survives, the path is sent with no `?` at
all.

---

## Endpoints

### `POST /auth/request-login`

- **Call site:** `src/api/auth/LoginCodeService.ts:17`
- **Reached from:** `src/features/auth/hooks/useLoginFlow.ts:65`
- **Body:**

  | Field | Type | Required | Example |
  | --- | --- | --- | --- |
  | `email` | string | yes | `"unzhz@student.kit.edu"` |

  The address is trimmed by the caller (`src/features/auth/hooks/useLoginFlow.ts:79`)
  and is not lower-cased before sending.
- **Query parameters:** none
- **Response fields read:** none beyond the envelope. `message` is read only on the
  failure path, by the shared parser.
- **Expected success status:** any 2xx with a JSON body that does not report
  `success: false`.
- **Error statuses handled:**
  - `429` → replaced with a fixed message, "Too many login codes have been requested.
    Wait a minute, then ask for a new one."
    (`src/features/auth/authenticateAdmin.ts:45-46`, `:59-61`). `Retry-After` is
    parsed onto the error but **never displayed**.
  - Every other failure → the backend's own `message`, or the fallback
    "Could not send login code"
    (`src/features/auth/authenticateAdmin.ts:62`, `useLoginFlow.ts:27`, `:120-122`).
    A `403` for a non-admin address is **not** specially handled here; the panel's own
    allow-list refuses those before the request is made
    (`src/features/auth/hooks/useLoginFlow.ts:89-92`).
- **Success determined by:** status **and** body `success`.

### `POST /auth/login`

- **Call site:** `src/api/auth/TokenLoginService.ts:23`
- **Reached from:** `src/features/auth/authenticateAdmin.ts:88`
- **Body:**

  | Field | Type | Required | Example |
  | --- | --- | --- | --- |
  | `email` | string | yes | `"unzhz@student.kit.edu"` |
  | `loginToken` | string | yes | `"A1B2C3"` |

  No `sessionType` field is sent.
- **Query parameters:** none
- **Response fields read:**
  - `authToken` — string, **required by the client**
    (`src/api/auth/TokenLoginService.ts:24-26`). A 2xx that omits it, or carries a
    blank one, raises `ContractError` with "The server accepted the login but returned
    no token." No other field of the response is read; `expiresAt` is **not** read
    anywhere in `src/`.
- **Expected success status:** any 2xx carrying `authToken`.
- **Error statuses handled:**
  - `403` → "Login succeeded, but this account is not authorized for the admin panel."
    (`src/features/auth/authenticateAdmin.ts:74-79`, `:8`)
  - Every other failure → the backend's `message`, or the fallback "Login failed"
    (`src/features/auth/authenticateAdmin.ts:7`, `:78`).
  - On any failure the token is cleared from the client
    (`src/features/auth/hooks/useLoginFlow.ts:75`).
- **Success determined by:** status, body `success`, **and** the presence of
  `authToken`.

### `POST /auth/logout`

- **Call site:** `src/api/auth/AuthSessionService.ts:14`
- **Reached from:** `src/context/AuthContext.tsx:51`
- **Body:** none sent — bodyless `POST`, so no `Content-Type` header either.
- **Query parameters:** none
- **Response fields read:** none.
- **Expected success status:** any 2xx.
- **Error statuses handled:** **all failures are swallowed**
  (`src/context/AuthContext.tsx:50-57`). The panel signs out locally regardless of
  what the backend answers, including a `401`, a `404` or a network failure. The
  operator is never told.
- **Success determined by:** nothing — the outcome does not change behaviour.

### `GET /auth/me`

- **Call site:** `src/api/auth/AuthSessionService.ts:26`
- **Reached from:** two places —
  `src/context/AuthContext.tsx:73-74` (on every page load while a stored token
  exists) and `src/features/auth/authenticateAdmin.ts:36` (immediately after a
  successful login).
- **Body:** none
- **Query parameters:** none
- **Response fields read** (all via `toAuthUser`,
  `src/api/auth/authUserMapper.ts:24-36`):

  | Path | Use |
  | --- | --- |
  | `user.id` | **Decides whether the response is usable at all.** Trimmed; if blank or non-string, the mapper answers `undefined` and the whole payload is discarded (`authUserMapper.ts:27-28`). |
  | `user.username` | Display name, first choice (`authUserMapper.ts:32`) |
  | `user.name` | Display name, fallback when `username` is absent |
  | `user.kitEmail` | Address, first choice (`authUserMapper.ts:33`) |
  | `user.email` | Address, fallback |
  | `user.role` | Stored; rendered in the sidebar (`authUserMapper.ts:34`) |
  | `user.isSuperAdmin` | Compared strictly against `true`; anything else becomes `false` (`authUserMapper.ts:35`). Gates whether the operator may moderate other admins (`src/features/users/model/access.ts:44`). |

  `user.status` is **not** read. Neither is any top-level field besides `user`.
- **Expected success status:** any 2xx carrying a `user` object with a non-blank
  string `id`.
- **Error statuses handled:**
  - **On page load** (`src/context/AuthContext.tsx:88-93`): only `401` is acted on —
    it clears the session and shows "Your session ended because the server no longer
    accepts it." **Every other failure, including `404`, is silently ignored** and the
    stored session is kept.
  - **After login** (`src/features/auth/authenticateAdmin.ts:32-42`): the `catch` is
    bare — every failure becomes `null`, which raises `IdentityUnavailableError` and
    **fails the whole sign-in** with "Signed in, but your admin account could not be
    read. Try again in a moment." (`authenticateAdmin.ts:14-15`, `:26-30`).
- **Success determined by:** status, body `success`, **and** the presence of a usable
  `user.id`. A 2xx naming no id is treated as "nobody is signed in", not as an error.

### `GET /users`

- **Call site:** `src/api/users/UserAccountsService.ts:24`
- **Reached from:** the users table (`src/features/users/hooks/useUsersPage.ts:77`),
  the dashboard's "Newest members" panel
  (`src/features/dashboard/hooks/useDashboardData.ts:46`), and `getAll()` with no
  parameters at all (`src/api/users/UserAccountsService.ts:39-41`) — which is
  currently reachable only through `UsersService.getAll`, itself unused by any screen.
- **Body:** none
- **Query parameters** (`src/api/users/UserAccountsService.ts:24-31`; absent ones are
  omitted, not sent empty):

  | Name | Type | Values actually sent |
  | --- | --- | --- |
  | `limit` | number | `50` from the users table (`DEFAULT_PAGE_SIZE`, `src/api/client/paging.ts:17`); `4` from the dashboard (`RECENT_MEMBER_COUNT`, `src/features/dashboard/dashboardModel.ts:11`) |
  | `cursor` | string | The opaque value from the previous page's `nextCursor` (`src/features/users/hooks/useUsersPage.ts:77-79`). Never sent on the first page. |
  | `q` | string | Trimmed search text; a blank search is dropped (`UserAccountsService.ts:27`, `src/features/users/model/filters.ts:54`) |
  | `status` | string | `ACTIVE`, `INACTIVE`, `BLOCKED` only. `DELETED` is deliberately not offered (`src/features/users/model/filters.ts:9-13`) |
  | `role` | string | `ADMIN`, `STUDENT` (`src/features/users/model/filters.ts:16`) |
  | `sort` | string | `newest`, `oldest` (`src/features/users/model/filters.ts:19`). The users table always sends one of the two — `newest` is the default and is sent explicitly (`filters.ts:41`). |

- **Response fields read:**

  | Path | Notes |
  | --- | --- |
  | `users` | **Required to be an array**, else `ContractError` (`UserAccountsService.ts:33`, `src/api/client/apiResponse.ts:61-69`) |
  | `users[].id` | Row key, profile lookup, self-comparison |
  | `users[].username` | Rendered; edit-form baseline |
  | `users[].kitEmail` | Rendered; also read by the admin allow-list (`src/utils/adminAccess.ts:57`) |
  | `users[].email` | Read only as the allow-list's first choice, `subject.email ?? subject.kitEmail` (`src/utils/adminAccess.ts:57`). Not declared on `User`. |
  | `users[].role` | Rendered as a badge; **overwritten to `ADMIN`** for the six hard-coded addresses (`src/utils/adminAccess.ts:56-59`, `UserAccountsService.ts:33`) |
  | `users[].status` | Badge; drives the block/unblock action |
  | `users[].joined` | Date column; also re-sorted client-side on the dashboard |
  | `users[].lastOnline` | Date column |
  | `users[].biography` | Profile modal; edit-form baseline |
  | `users[].warnings` | Count badge |
  | `users[].reports` | Count badge |
  | `users[].credibilityScore` | Profile field; editable |
  | `nextCursor` | `?? null`; absent and `null` are the same thing (`UserAccountsService.ts:34`) |

- **Expected success status:** any 2xx carrying a `users` array.
- **Error statuses handled:** none by status. Every failure is rendered as its own
  `message` with the fallback "Failed to load users"
  (`src/features/users/hooks/useUsersPage.ts:134`). A `400` — `A cursor requires a
  limit`, `Invalid user status` — is shown verbatim above the table, deliberately
  (`useUsersPage.ts:132-134`).
- **Success determined by:** status, body `success`, **and** the presence of the
  `users` array.

### `GET /users/{id}`

- **Call site:** `src/api/users/UserAccountsService.ts:49`
- **Reached from:** the profile modal (`src/features/users/hooks/useUserProfile.ts:25`)
  and the re-read after every profile write
  (`src/features/users/hooks/useUserProfileActions.ts:63`).
- **Body:** none
- **Query parameters:** none
- **Response fields read:** the account is read **from the top level of the response**,
  not from a `user` key. Same field list as `users[]` above: `id`, `username`,
  `kitEmail`, `role`, `status`, `joined`, `lastOnline`, `biography`, `warnings`,
  `reports`, `credibilityScore`, plus `email` as the allow-list's first choice
  (`src/utils/adminAccess.ts:57`).
- **Expected success status:** any 2xx whose body *is* the account.
- **Error statuses handled:**
  - `404` → **not an error.** Resolves to an empty profile, `{ user: null, warnings:
    [] }`, so the modal says the account is gone
    (`src/features/users/hooks/useUserProfile.ts:29-32`).
  - Everything else → the backend's `message`, fallback "This profile could not be
    loaded." (`useUserProfile.ts:9`, `:78`).
- **Success determined by:** status **and** body `success`. Nothing checks that the
  account's fields are actually present.

### `PATCH /users/{id}`

- **Call site:** `src/api/users/UserAccountsService.ts:59`
- **Reached from:** `src/features/users/hooks/useUserProfileActions.ts:86`
- **Body** — only changed fields are sent, and an empty object is never sent
  (`src/features/users/model/userEdit.ts:77-94`, guarded at
  `useUserProfileActions.ts:85`):

  | Field | Type | Required | Example |
  | --- | --- | --- | --- |
  | `username` | string | no | `"ada"` (trimmed) |
  | `biography` | string | no | `""` |
  | `status` | `"ACTIVE"` \| `"BLOCKED"` | no | `"BLOCKED"` |
  | `role` | `"STUDENT"` \| `"ADMIN"` | no | `"ADMIN"` |
  | `credibilityScore` | number | no | `80` |

  `kitEmail` is never sent. `INACTIVE` and `DELETED` are never sent as a `status`
  (`src/features/users/model/userEdit.ts:8`, `:19`).
- **Query parameters:** none
- **Response fields read:** none. The account is re-read with `GET /users/{id}`
  afterwards (`useUserProfileActions.ts:87`, `:61-70`).
- **Expected success status:** any 2xx.
- **Error statuses handled:** none by status. A failure keeps the edit form open and
  shows the backend's `message` (`src/features/users/hooks/useUserProfile.ts:78`).
  A `403` for moderating a protected account gets no dedicated wording; the panel
  hides the control instead (`src/features/users/model/access.ts:42-53`).
- **Success determined by:** status **and** body `success`.

### `DELETE /users/{id}`

- **Call site:** `src/api/users/UserAccountsService.ts:69`
- **Reached from:** `src/features/users/hooks/useUserProfileActions.ts:132`
- **Body:** none
- **Query parameters:** none
- **Response fields read:** none. The row is removed from the cached list
  (`useUserProfileActions.ts:133`, `src/features/users/hooks/useUsersPage.ts:96-98`).
- **Expected success status:** any 2xx, `204` included — a `204` resolves to
  `undefined` and is treated as success (`src/api/client/apiResponse.ts:95`).
- **Error statuses handled:** none by status. The confirmation dialog reports the
  backend's `message` in place.
- **Success determined by:** status **and** body `success`.

### `PATCH /users/{id}/block`

- **Call site:** `src/api/users/UserAccountsService.ts:77`
- **Reached from:** `src/features/users/hooks/useUserProfileActions.ts:128`
- **Body:** none sent — bodyless `PATCH`, so **no `Content-Type` header is sent**.
- **Query parameters:** none
- **Response fields read:** none. The account is re-read with `GET /users/{id}`
  (`useUserProfileActions.ts:105`).
- **Expected success status:** any 2xx.
- **Error statuses handled:** none by status; the message is shown as the backend
  wrote it.
- **Success determined by:** status **and** body `success`.

### `PATCH /users/{id}/unblock`

- **Call site:** `src/api/users/UserAccountsService.ts:85`
- **Reached from:** `src/features/users/hooks/useUserProfileActions.ts:129`
- **Body:** none sent — bodyless `PATCH`, no `Content-Type`.
- **Query parameters:** none
- **Response fields read:** none; the account is re-read afterwards.
- **Expected success status:** any 2xx.
- **Error statuses handled:** none by status.
- **Success determined by:** status **and** body `success`.

### `GET /users/{id}/warnings`

- **Call site:** `src/api/users/UserWarningsService.ts:26`
- **Reached from:** the profile modal (`src/features/users/hooks/useUserProfile.ts:26`)
  and the re-read after every warning write
  (`src/features/users/hooks/useUserProfileActions.ts:52`).
- **Body:** none
- **Query parameters:** none
- **Response fields read** (mapped by `toWarning`,
  `src/api/users/userWarningMapper.ts:24-33`):

  | Path | Notes |
  | --- | --- |
  | `warnings` | **Required to be an array**, else `ContractError` (`UserWarningsService.ts:27`) |
  | `warnings[].id` | `?? null`. A `null` id hides the edit and withdraw controls (`src/features/users/hooks/useUserProfileActions.ts:117`, `:123`) |
  | `warnings[].userID` | Used to address the edit/withdraw call — the path is built from **this** value, not from the id in the URL that fetched it (`useUserProfileActions.ts:120`, `:126`) |
  | `warnings[].message` | Rendered; edit baseline |
  | `warnings[].createdAt` | Rendered; part of the React key when `id` is null (`src/features/users/components/WarningHistory.tsx:41`) |
  | `warnings[].createdFrom.id` | `?? ''`; answers "did I issue this?" |
  | `warnings[].createdFrom.name` | Trimmed; falls back to the literal `"Unknown"` |

  The list count is also read (`warnings.length`) to update the account's warning
  badge without re-reading the account (`useUserProfileActions.ts:54`, `:56`).
- **Defined but not read:** `warnings[].createdFrom.createdAt`
  (`src/api/users/userWarningMapper.ts:9`).
- **Expected success status:** any 2xx carrying a `warnings` array.
- **Error statuses handled:** none by status. A `404` on **this** call, when paired
  with a successful `GET /users/{id}`, is caught by the shared `NotFoundError` branch
  in `fetchProfile` and renders the whole profile as "account gone"
  (`src/features/users/hooks/useUserProfile.ts:24-32`).
- **Success determined by:** status, body `success`, **and** the presence of the
  `warnings` array.

### `POST /users/{id}/warnings`

- **Call site:** `src/api/users/UserWarningsService.ts:17`
- **Reached from:** `src/features/users/hooks/useUserProfileActions.ts:77`
- **Body:**

  | Field | Type | Required | Example |
  | --- | --- | --- | --- |
  | `message` | string | yes | `"Please keep comments on topic."` |

  Trimmed; an empty message short-circuits before any request
  (`useUserProfileActions.ts:75-76`). The issuer is taken from the token and the
  target from the path — neither is in the body.
- **Query parameters:** none
- **Response fields read:** none. The warning list is re-read afterwards
  (`useUserProfileActions.ts:78`).
- **Expected success status:** any 2xx.
- **Error statuses handled:** none by status.
- **Success determined by:** status **and** body `success`.

### `PATCH /users/{id}/warnings/{warningId}`

- **Call site:** `src/api/users/UserWarningsService.ts:36`
- **Reached from:** `src/features/users/hooks/useUserProfileActions.ts:119-121`
- **Body:**

  | Field | Type | Required | Example |
  | --- | --- | --- | --- |
  | `message` | string | yes | `"Corrected wording."` |

- **Query parameters:** none
- **Response fields read:** none; the warning list is re-read.
- **Expected success status:** any 2xx.
- **Error statuses handled:** none by status.
- **Success determined by:** status **and** body `success`.

### `DELETE /users/{id}/warnings/{warningId}`

- **Call site:** `src/api/users/UserWarningsService.ts:41`
- **Reached from:** `src/features/users/hooks/useUserProfileActions.ts:125-127`
- **Body:** none
- **Query parameters:** none
- **Response fields read:** none; the warning list is re-read.
- **Expected success status:** any 2xx, `204` included.
- **Error statuses handled:** none by status.
- **Success determined by:** status **and** body `success`.

### `GET /comments`

- **Call site:** `src/api/moderation/ContentService.ts:24-26`, with `resource =
  "comments"` (`src/api/moderation/CommentsService.ts:23`)
- **Reached from:** `src/features/comments/CommentsPage.tsx:26`
- **Body:** none
- **Query parameters:** none — the whole list is fetched unpaged, and searching and
  status filtering are done client-side
  (`src/features/moderation/model/content.ts:135-158`).
- **Response fields read:**

  | Path | Notes |
  | --- | --- |
  | `comments` | **Required to be an array**, else `ContractError`. The key is the resource name (`ContentService.ts:26`). |
  | `comments[].id` | Row key; addresses `PATCH`/`DELETE /comments/{id}` |
  | `comments[].content` | Rendered; searched; edit baseline |
  | `comments[].status` | `VISIBLE` \| `HIDDEN` \| `DELETED`; badge and filter |
  | `comments[].author.id` | Opens the profile modal |
  | `comments[].author.name` | Rendered; searched |
  | `comments[].author.role` | Chip colour |
  | `comments[].author.warnings` | Chip badge |
  | `comments[].author.status` | Chip badge (`BLOCKED` only) |
  | `comments[].postContext` | Rendered; searched |
  | `comments[].answers` | Rendered as "N answers" (`content.ts:45`) |
  | `comments[].reports` | Count badge; drives the "reported" stat |
  | `comments[].createdAt` | Rendered |

- **Defined but not read:** `comments[].lectureId`
  (`src/api/moderation/commentTypes.ts:20`) — declared and never touched by any
  screen.
- **Expected success status:** any 2xx carrying a `comments` array.
- **Error statuses handled:** none by status; message with fallback "Failed to load
  comments" (`src/features/comments/CommentsPage.tsx:50`).
- **Success determined by:** status, body `success`, **and** the presence of the
  `comments` array.

### `PATCH /comments/{id}`

- **Call site:** `src/api/moderation/ContentService.ts:31`
- **Reached from:** `src/features/comments/CommentsPage.tsx:36`
- **Body** — only changed fields, never empty
  (`src/features/moderation/model/contentEdit.ts:40`):

  | Field | Type | Required | Example |
  | --- | --- | --- | --- |
  | `content` | string | no | `"edited text"` |
  | `status` | `"VISIBLE"` \| `"HIDDEN"` | no | `"HIDDEN"` |

  `DELETED` is never sent as a status (`src/domain/moderation.ts:40`).
- **Query parameters:** none
- **Response fields read:** none — both lists are invalidated and re-read
  (`src/features/moderation/hooks/useModerationActions.ts:37-41`, `:84-85`).
- **Expected success status:** any 2xx.
- **Error statuses handled:** none by status; message with fallback "The change could
  not be saved." (`useModerationActions.ts:66`).
- **Success determined by:** status **and** body `success`.

### `DELETE /comments/{id}`

- **Call site:** `src/api/moderation/ContentService.ts:36`
- **Reached from:** `src/features/comments/CommentsPage.tsx:37`
- **Body:** none
- **Query parameters:** none
- **Response fields read:** none; both lists are re-read.
- **Expected success status:** any 2xx, `204` included.
- **Error statuses handled:** none by status; fallback "The content could not be
  deleted." (`useModerationActions.ts:67-68`).
- **Success determined by:** status **and** body `success`.

### `GET /comments/reported`

- **Call site:** `src/api/moderation/ReportsService.ts:23-25`
- **Reached from:** `src/features/comments/CommentsPage.tsx:31`
- **Body:** none
- **Query parameters:** none — unpaged; filtering is client-side
  (`src/features/moderation/hooks/useModerationReports.ts:32-37`).
- **Response fields read:**

  | Path | Notes |
  | --- | --- |
  | `comments` | **Required to be an array.** The list key is the *resource* name, **not** `reports` (`ReportsService.ts:25`). |
  | `comments[].id` | The **report's** id — addresses `PATCH`/`DELETE /comments/reported/{id}` |
  | `comments[].commentId` | The **content's** id — a different address (`src/features/moderation/model/reports.ts:33`) |
  | `comments[].commentContent` | Rendered; searched |
  | `comments[].postContext` | Rendered |
  | `comments[].reportText` | Rendered; searched |
  | `comments[].reportedUser.id` / `.name` / `.role` / `.warnings` / `.status` | Chip |
  | `comments[].reporter.id` / `.name` / `.role` / `.warnings` / `.status` | Chip |
  | `comments[].reason` | One of the eight in `src/domain/moderation.ts:12-21`; badge and filter |
  | `comments[].status` | One of `OPEN`, `REVIEWED`, `DISMISSED`, `ACTION_TAKEN`; select value and filter |
  | `comments[].date` | Rendered |

- **Expected success status:** any 2xx carrying a `comments` array.
- **Error statuses handled:** none by status; fallback "Failed to load reported
  comments" (`src/features/comments/CommentsPage.tsx:51`).
- **Success determined by:** status, body `success`, **and** the presence of the
  `comments` array.

### `PATCH /comments/reported/{id}`

- **Call site:** `src/api/moderation/ReportsService.ts:33`
- **Reached from:** `src/features/comments/CommentsPage.tsx:38-39`
- **Path parameter:** the **report's** id, not the comment's
  (`src/features/moderation/model/reports.ts:30`).
- **Body:**

  | Field | Type | Required | Example |
  | --- | --- | --- | --- |
  | `status` | `"OPEN"` \| `"REVIEWED"` \| `"DISMISSED"` \| `"ACTION_TAKEN"` | yes | `"ACTION_TAKEN"` |

- **Query parameters:** none
- **Response fields read:** none; both lists are re-read. The client expects
  `ACTION_TAKEN` to hide the underlying content as a **side effect on the backend**
  and does not send a second request for it
  (`src/api/moderation/ReportsService.ts:29-31`).
- **Expected success status:** any 2xx.
- **Error statuses handled:** none by status. The call is fire-and-forget with no
  rollback — the select keeps showing whatever the re-read returns
  (`src/features/moderation/hooks/useModerationActions.ts:92-98`). Fallback message
  "Failed to update the report. The change was not saved."
- **Success determined by:** status **and** body `success`.

### `DELETE /comments/reported/{id}`

- **Call site:** `src/api/moderation/ReportsService.ts:38`
- **Reached from:** `src/features/comments/CommentsPage.tsx:40`
- **Path parameter:** the **report's** id.
- **Body:** none
- **Query parameters:** none
- **Response fields read:** none. Only the reports list is re-read — the content list
  deliberately is not, because withdrawing a report is expected to leave the content
  untouched (`src/features/moderation/hooks/useModerationActions.ts:54-57`).
- **Expected success status:** any 2xx, `204` included.
- **Error statuses handled:** none by status; fallback "The report could not be
  withdrawn."
- **Success determined by:** status **and** body `success`.

### `GET /answers`

- **Call site:** `src/api/moderation/ContentService.ts:24-26`, with `resource =
  "answers"` (`src/api/moderation/AnswersService.ts:15`)
- **Reached from:** `src/features/comments/CommentsPage.tsx:26`
- **Body / query parameters:** none
- **Response fields read:**

  | Path | Notes |
  | --- | --- |
  | `answers` | **Required to be an array** |
  | `answers[].id` | Row key; addresses `PATCH`/`DELETE /answers/{id}` |
  | `answers[].content` | Rendered; searched |
  | `answers[].status` | Badge and filter |
  | `answers[].author.id` / `.name` / `.role` / `.warnings` / `.status` | Chip |
  | `answers[].commentId` | **Used to nest the answer under its parent comment** (`src/features/moderation/model/content.ts:73-75`). An answer whose `commentId` matches no loaded comment is silently dropped from the table. |
  | `answers[].commentPreview` | Rendered as "Re: …" (`content.ts:63`) |
  | `answers[].postContext` | Rendered; searched |
  | `answers[].reports` | Count badge |
  | `answers[].createdAt` | Rendered |

- **Expected success status:** any 2xx carrying an `answers` array.
- **Error statuses handled:** none by status. Because this and `GET /comments` are
  awaited together with `Promise.all`
  (`src/features/comments/CommentsPage.tsx:26`), a failure of either fails the whole
  Comments screen.
- **Success determined by:** status, body `success`, **and** the presence of the
  `answers` array.

### `PATCH /answers/{id}`

- **Call site:** `src/api/moderation/ContentService.ts:31`
- **Reached from:** `src/features/comments/CommentsPage.tsx:36` (routed by
  `item.kind === "answer"`, `CommentsPage.tsx:14-17`)
- **Body:** same shape as `PATCH /comments/{id}` — `content` and/or `status`.
- **Query parameters:** none
- **Response fields read:** none; both lists are re-read.
- **Expected success status:** any 2xx.
- **Error statuses handled:** none by status.
- **Success determined by:** status **and** body `success`.

### `DELETE /answers/{id}`

- **Call site:** `src/api/moderation/ContentService.ts:36`
- **Reached from:** `src/features/comments/CommentsPage.tsx:37`
- **Body / query parameters:** none
- **Response fields read:** none.
- **Expected success status:** any 2xx, `204` included.
- **Error statuses handled:** none by status.
- **Success determined by:** status **and** body `success`.

### `GET /answers/reported`

- **Call site:** `src/api/moderation/ReportsService.ts:23-25`
- **Reached from:** `src/features/comments/CommentsPage.tsx:32`
- **Body / query parameters:** none
- **Response fields read:**

  | Path | Notes |
  | --- | --- |
  | `answers` | **Required to be an array.** Key is the resource name, not `reports`. |
  | `answers[].id` | The **report's** id |
  | `answers[].answerId` | The **answer's** id (`src/features/moderation/model/reports.ts:54`) |
  | `answers[].answerContent` | Rendered; searched |
  | `answers[].commentContent` | Rendered as the parent-thread context (`reports.ts:56`) |
  | `answers[].postContext` | Rendered |
  | `answers[].reportText` | Rendered; searched |
  | `answers[].reportedUser.id` / `.name` / `.role` / `.warnings` / `.status` | Chip |
  | `answers[].reporter.id` / `.name` / `.role` / `.warnings` / `.status` | Chip |
  | `answers[].reason` | Badge and filter |
  | `answers[].status` | Select value and filter |
  | `answers[].date` | Rendered |

- **Defined but not read:** `answers[].commentId`
  (`src/api/moderation/answerTypes.ts:29`) — declared as the parent comment's id and
  never used; `toAnswerReport` maps `answerId` instead
  (`src/features/moderation/model/reports.ts:50-65`).
- **Expected success status:** any 2xx carrying an `answers` array.
- **Error statuses handled:** none by status.
- **Success determined by:** status, body `success`, **and** the presence of the
  `answers` array.

### `PATCH /answers/reported/{id}`

- **Call site:** `src/api/moderation/ReportsService.ts:33`
- **Path parameter:** the **report's** id.
- **Body:** `{ "status": "OPEN" | "REVIEWED" | "DISMISSED" | "ACTION_TAKEN" }`
- **Query parameters:** none
- **Response fields read:** none.
- **Expected success status:** any 2xx.
- **Error statuses handled:** none by status; fire-and-forget, no rollback.
- **Success determined by:** status **and** body `success`.

### `DELETE /answers/reported/{id}`

- **Call site:** `src/api/moderation/ReportsService.ts:38`
- **Path parameter:** the **report's** id.
- **Body / query parameters:** none
- **Response fields read:** none.
- **Expected success status:** any 2xx, `204` included.
- **Error statuses handled:** none by status.
- **Success determined by:** status **and** body `success`.

### `GET /data/lectures`

- **Call site:** `src/api/catalog/LectureCatalogService.ts:13`
- **Reached from:** `src/features/catalog/hooks/useCatalogLists.ts:21`
- **Body / query parameters:** none. This is the **public, active-only** read; the
  panel deliberately does not call the `…/all` variant
  (`src/api/catalog/CatalogService.ts:29-31`). An `Authorization` header is still sent
  when a session exists (`src/api/client/apiRequest.ts:108-109`).
- **Response fields read:**

  | Path | Notes |
  | --- | --- |
  | `lectures` | **Required to be an array** (`LectureCatalogService.ts:14`) |
  | `lectures[].id` | Row key; addresses `PATCH`/`DELETE /data/lectures/{id}`; joined against `Rating.lectureId` and `Professor.lectureIds` |
  | `lectures[].name` | Rendered; searched; edit baseline |
  | `lectures[].code` | Rendered; searched; edit baseline |
  | `lectures[].semesterYear` | Rendered as "Winter 2026"; edit baseline |
  | `lectures[].semesterSeason` | `SUMMER` \| `WINTER` (`src/domain/catalog.ts:4`) |
  | `lectures[].active` | Badge; edit baseline |
  | `lectures[].lectureType` | `LECTURE_ONLY` \| `LECTURE_AND_EXERCISE` (`src/domain/catalog.ts:12`) |
  | `lectures[].professors[].id` | Builds `professorIds` for the edit form (`src/features/catalog/model/edit.ts:41`, `:80`) |
  | `lectures[].professors[].firstName` | Rendered; searched (`src/features/catalog/model/catalog.ts:6-8`) |
  | `lectures[].professors[].lastName` | Rendered; searched |
  | `lectures[].commentCount` | Rendered; summed into the catalogue stat row |
  | `lectures[].ratingCount` | Rendered; summed into the catalogue stat row |

- **Defined but not read:** `lectures[].professors[].active`
  (`src/api/catalog/catalogTypes.ts:13`).
- **Expected success status:** any 2xx carrying a `lectures` array.
- **Error statuses handled:** none by status; fallback "Failed to load lectures"
  (`src/features/catalog/hooks/useCatalogLists.ts:49`). Fetched with `Promise.all`
  alongside `GET /data/professor`, so either failure takes the catalogue screen down
  (`useCatalogLists.ts:19-26`, `:34`).
- **Success determined by:** status, body `success`, **and** the presence of the
  `lectures` array.

### `PATCH /data/lectures/{id}`

- **Call site:** `src/api/catalog/LectureCatalogService.ts:23`
- **Reached from:** `src/features/catalog/hooks/useCatalogActions.ts:29`
- **Body** — only changed fields, never empty
  (`src/features/catalog/model/edit.ts:76-91`, `:119-126`):

  | Field | Type | Required | Example |
  | --- | --- | --- | --- |
  | `name` | string | no | `"Programmierparadigmen"` (trimmed) |
  | `code` | string | no | `"24515"` (trimmed) |
  | `semesterYear` | number | no | `2026` (form-clamped to 1900–2999, `src/features/catalog/model/catalog.ts:59`) |
  | `semesterSeason` | `"SUMMER"` \| `"WINTER"` | no | `"WINTER"` |
  | `active` | boolean | no | `false` |
  | `lectureType` | `"LECTURE_ONLY"` \| `"LECTURE_AND_EXERCISE"` | no | `"LECTURE_ONLY"` |
  | `professorIds` | string[] | no | `["uuid-a","uuid-b"]` — replaces the assignment wholesale; `[]` clears it. Sent only when the **set** differs, so reordering is not an edit (`edit.ts:88`). |

- **Query parameters:** none
- **Response fields read:** none; both catalogue lists are invalidated
  (`useCatalogActions.ts:22-25`).
- **Expected success status:** any 2xx.
- **Error statuses handled:** none by status. The failure rejects, which keeps the
  edit modal open with the backend's `message`; fallback "The lecture could not be
  saved." (`useCatalogActions.ts:53-54`, `:69-73`).
- **Success determined by:** status **and** body `success`.

### `DELETE /data/lectures/{id}`

- **Call site:** `src/api/catalog/LectureCatalogService.ts:28`
- **Reached from:** `src/features/catalog/hooks/useCatalogActions.ts:34`
- **Body / query parameters:** none
- **Response fields read:** none; the whole catalogue cache, ratings included, is
  invalidated (`useCatalogActions.ts:22-25`, `:35`).
- **Expected success status:** any 2xx, `204` included.
- **Error statuses handled:** none by status; fallback "The lecture could not be
  deleted."
- **Success determined by:** status **and** body `success`.

### `GET /data/professor`

- **Call site:** `src/api/catalog/ProfessorCatalogService.ts:13` — **singular** path
  segment, plural response key.
- **Reached from:** `src/features/catalog/hooks/useCatalogLists.ts:25`
- **Body / query parameters:** none. Public, active-only read.
- **Response fields read:**

  | Path | Notes |
  | --- | --- |
  | `professors` | **Required to be an array** (`ProfessorCatalogService.ts:14`) |
  | `professors[].id` | Row key; addresses `PATCH`/`DELETE /data/professor/{id}` |
  | `professors[].firstName` | Rendered; searched; edit baseline |
  | `professors[].lastName` | Rendered; searched; edit baseline |
  | `professors[].active` | Badge; edit baseline (`src/features/catalog/components/ProfessorTableRow.tsx:59-60`) |
  | `professors[].lectureIds` | Resolved against the lecture list to render the assignment (`ProfessorTableRow.tsx:32-34`); seeds the edit form (`src/features/catalog/CatalogPage.tsx:126`) |
  | `professors[].averageRating` | Rendered to one decimal, or a dash when `ratingCount` is 0 (`src/features/catalog/model/catalog.ts:47-50`) |
  | `professors[].ratingCount` | Rendered; **decides whether `averageRating` is shown at all** |

- **Expected success status:** any 2xx carrying a `professors` array.
- **Error statuses handled:** none by status; fallback "Failed to load professors".
- **Success determined by:** status, body `success`, **and** the presence of the
  `professors` array.

### `PATCH /data/professor/{id}`

- **Call site:** `src/api/catalog/ProfessorCatalogService.ts:23`
- **Reached from:** `src/features/catalog/hooks/useCatalogActions.ts:40`
- **Body** — only changed fields, never empty
  (`src/features/catalog/model/edit.ts:101-116`):

  | Field | Type | Required | Example |
  | --- | --- | --- | --- |
  | `firstName` | string | no | `"Ada"` (trimmed) |
  | `lastName` | string | no | `"Lovelace"` (trimmed) |
  | `active` | boolean | no | `true` |
  | `lectureIds` | string[] | no | `["uuid-a"]` — replaces the assignment wholesale; `[]` detaches from every lecture. Compared as a set (`edit.ts:113`). Note the spelling: **`lectureIds`**, lower-case `d`. |

- **Query parameters:** none
- **Response fields read:** none.
- **Expected success status:** any 2xx.
- **Error statuses handled:** none by status; fallback "The professor could not be
  saved."
- **Success determined by:** status **and** body `success`.

### `DELETE /data/professor/{id}`

- **Call site:** `src/api/catalog/ProfessorCatalogService.ts:28`
- **Reached from:** `src/features/catalog/hooks/useCatalogActions.ts:45`
- **Body / query parameters:** none
- **Response fields read:** none.
- **Expected success status:** any 2xx, `204` included.
- **Error statuses handled:** none by status; fallback "The professor could not be
  deleted."
- **Success determined by:** status **and** body `success`.

### `GET /ratings`

- **Call site:** `src/api/catalog/RatingReadService.ts:19`
- **Reached from:** `src/features/catalog/hooks/useRatings.ts:25`
- **Body:** none
- **Query parameters** (`RatingReadService.ts:19-23`):

  | Name | Type | Values actually sent |
  | --- | --- | --- |
  | `limit` | number | Always `50` from the ratings tab (`useRatings.ts:26`) |
  | `cursor` | string | The previous page's `nextCursor`; not sent on the first page |
  | `lectureId` | string | A lecture id when the tab's filter is set; dropped when it is `null` (`useRatings.ts:28`) |

- **Response fields read** (mapped by `toRating`,
  `src/api/catalog/ratingMapper.ts:15-23`):

  | Path | Notes |
  | --- | --- |
  | `ratings` | **Required to be an array** (`RatingReadService.ts:25`) |
  | `ratings[].id` | Row key; addresses `DELETE /ratings/{id}` |
  | `ratings[].lectureId` | `?? null`; resolved against the lecture list to render the lecture label (`src/features/catalog/components/RatingsTable.tsx:85`) |
  | `ratings[].author.name` | Trimmed; falls back to the literal `"Unknown"` (`ratingMapper.ts:19`) |
  | `ratings[].createdAt` | `?? ''` |
  | `ratings[].scores` | `?? {}`. Read as an **open map** of category → number; the client never enumerates the expected category names (`src/api/catalog/ratingTypes.ts:1-13`). |
  | `nextCursor` | `?? null` |

- **Defined but not read:** `ratings[].author.id`
  (`src/api/catalog/ratingTypes.ts:39`).
- **Expected success status:** any 2xx carrying a `ratings` array.
- **Error statuses handled:** none by status; fallback "Failed to load ratings"
  (`useRatings.ts:43`).
- **Success determined by:** status, body `success`, **and** the presence of the
  `ratings` array.

### `DELETE /ratings/{id}`

- **Call site:** `src/api/catalog/RatingDeletionService.ts:8`
- **Reached from:** `src/features/catalog/hooks/useRatings.ts:39`
- **Body / query parameters:** none
- **Response fields read:** none; the ratings query for the current lecture filter is
  invalidated (`useRatings.ts:40`).
- **Expected success status:** any 2xx, `204` included.
- **Error statuses handled:** none by status; fallback "The rating could not be
  deleted."
- **Success determined by:** status **and** body `success`.

### `GET /audit-logs`

- **Call site:** `src/api/logs/AuditLogReaderService.ts:17`
- **Reached from:** `src/features/audit-logs/hooks/useAuditLogs.ts:54-57`
- **Body:** none
- **Query parameters** (built by `toLogQueryParams`,
  `src/api/logs/logQueryParams.ts:16-32`):

  | Name | Type | Values actually sent |
  | --- | --- | --- |
  | `limit` | number | **Always `50`** — hard-wired as the function's default and never overridden (`logQueryParams.ts:19`) |
  | `cursor` | string | The previous page's `nextCursor`. The first page is fetched through a **one-argument** call so no `cursor` key exists at all (`useAuditLogs.ts:54-57`) |
  | `q` | string | Trimmed free-text search; a blank one is dropped |
  | `actorId` | string | An id chosen from `meta.actors` |
  | `actorType` | string | **Never populated.** The parameter is built (`logQueryParams.ts:26`) but no UI writes it — `FilterDraft` has no `actorType` field (`src/features/audit-logs/auditLogModel.ts:21-28`), so it is always `undefined` and always dropped. |
  | `action` | string | An action chosen from `meta.actions` — validated against the **backend's** vocabulary, not the compiled-in list (`auditLogModel.ts:49-51`) |
  | `targetType` | string | A target type chosen from `meta.targetTypes` (`auditLogModel.ts:58-63`) |
  | `from` | string | ISO 8601 instant. Built from a `yyyy-mm-dd` date picked on the **operator's local calendar**, at 00:00:00.000 **local time**, then `.toISOString()` (`src/domain/auditLog.ts:196-215`). Example, in CEST: `2026-08-19T22:00:00.000Z` for "20 Aug". |
  | `to` | string | Same, at 23:59:59.999 local time. Expected to be an **inclusive** upper bound (`src/domain/auditLog.ts:193-194`). |

- **Response fields read** (via `toAuditLog`, `src/api/logs/auditLogMapper.ts:13-20`):

  | Path | Notes |
  | --- | --- |
  | `auditLogs` | **Required to be an array** (`AuditLogReaderService.ts:19`) |
  | `auditLogs[].id` | Row key; addresses `POST /audit-logs/{id}/revert` |
  | `auditLogs[].action` | Badge label and colour; unknown values render with a generic label and the default colour (`src/constants/colors.ts:172-177`, `src/utils/enumLabel.ts:2-7`) |
  | `auditLogs[].createdAt` | Rendered with time |
  | `auditLogs[].actor.name` | Rendered in the row and the detail header |
  | `auditLogs[].actor.email` | Rendered in the row and the detail header |
  | `auditLogs[].actor.role` | Rendered in the detail header |
  | `auditLogs[].target` | Null-checked before use (`src/features/audit-logs/components/AuditLogRow.tsx:53`, `details/AuditLogDetails.tsx:35-41`) |
  | `auditLogs[].target.label` | Rendered |
  | `auditLogs[].target.type` | Rendered |
  | `auditLogs[].target.id` | Rendered in the detail view only |
  | `auditLogs[].changes` | An **open map** of field name → `{before, after}`. Enumerated with `Object.keys` / `Object.entries` (`AuditLogRow.tsx:26`, `details/AuditChangeList.tsx:30`). |
  | `auditLogs[].changes.*.before` | Rendered; `null` → `—`, boolean → Yes/No (`src/features/audit-logs/auditLogModel.ts:97-101`) |
  | `auditLogs[].changes.*.after` | Same |
  | `auditLogs[].metadata` | An **open map** of key → scalar; enumerated and rendered as key/value pairs (`details/MetadataList.tsx:13`) |
  | `auditLogs[].revertible` | `?? false`; hides the undo button |
  | `auditLogs[].revertBlockedReason` | `?? null`; mapped to one of five sentences, with a generic fallback for an unknown code (`auditLogModel.ts:108-130`) |
  | `auditLogs[].revertedByAuditId` | `?? null`; when set, the entry renders as "already undone" and the id is displayed |
  | `nextCursor` | `?? null` |

- **Defined but not read:** `auditLogs[].actor.type` and `auditLogs[].actor.id`
  (`src/domain/auditLog.ts:72-78`).
- **Expected success status:** any 2xx carrying an `auditLogs` array.
- **Error statuses handled:**
  - `404` → **reinterpreted**, not shown as "not found". It renders "The configured
    backend does not support audit logs yet. Deploy the audit API endpoints, then try
    again." (`src/features/audit-logs/auditLogModel.ts:14-15`, `:153-155`).
  - Everything else → the backend's `message`, fallback "Failed to load audit logs".
- **Success determined by:** status, body `success`, **and** the presence of the
  `auditLogs` array.

### `GET /audit-logs/meta`

- **Call site:** `src/api/logs/AuditLogMetaService.ts:16`
- **Reached from:** `src/features/audit-logs/hooks/useAuditLogs.ts:46`
- **Body / query parameters:** none
- **Response fields read:**

  | Path | Notes |
  | --- | --- |
  | `actors` | **Required to be an array** (`AuditLogMetaService.ts:18`) |
  | `actors[].id` | The `actorId` query value the filter sends (`components/AuditSelectFilters.tsx:28`) |
  | `actors[].name` | Option label, left half |
  | `actors[].email` | Option label, right half |
  | `actions` | **Required to be an array** (`AuditLogMetaService.ts:19`). Used as the filter's option list **and** as the validator for the `action` query parameter (`auditLogModel.ts:49-51`) |
  | `targetTypes` | **Required to be an array** (`AuditLogMetaService.ts:20`); same double role |
  | `actorTypes` | **Optional** — `?? []` (`AuditLogMetaService.ts:21`). Read into the model and then **never rendered or sent**. |

- **Defined but not read:** `actors[].role` (`src/domain/auditLog.ts:160`).
- **Expected success status:** any 2xx carrying `actors`, `actions` **and**
  `targetTypes` as arrays. A missing `actorTypes` is fine.
- **Error statuses handled:** shares the reader's handling — a `404` here also renders
  "The configured backend does not support audit logs yet."
  (`useAuditLogs.ts:67`, `:107-112`).
- **Success determined by:** status, body `success`, **and** the presence of three
  named arrays.

### `POST /audit-logs/{id}/revert`

- **Call site:** `src/api/logs/AuditLogRevertService.ts:11`
- **Reached from:** `src/features/audit-logs/hooks/useRevertAuditEntry.ts:19`
- **Body:** none sent — bodyless `POST`, **no `Content-Type` header**.
- **Query parameters:** none
- **Response fields read:** none. The log is re-read instead, because the reversal's
  own entry exists only there (`useRevertAuditEntry.ts:20`, `:12-16`).
- **Expected success status:** any 2xx.
- **Error statuses handled:**
  - `409` → **`error.reason` is read** and mapped to one of five sentences
    (`src/features/audit-logs/auditLogModel.ts:140-143`, `:108-116`). The reason codes
    the client understands are `ACTION_NOT_REVERTIBLE`, `WINDOW_EXPIRED`,
    `VALUE_CHANGED`, `ALREADY_REVERTED`, `TARGET_MISSING`
    (`src/domain/auditLog.ts:127-133`). An unrecognised code falls back to "This
    change cannot be undone." — it is not shown raw.
  - Everything else → the backend's `message`, fallback "The change could not be
    undone."
  - Note: the `404` reinterpretation used for the **reads** does not apply here; a
    `404` on a revert shows its own message.
- **Success determined by:** status **and** body `success`.
- **Reason codes expected on failure:** `reason` is the only machine-readable failure
  field the client reads from any endpoint.

### `GET /system/status`

- **Call site:** `src/api/system/SystemStatusService.ts:23`
- **Reached from:** three places, with three different failure policies —
  the dashboard (`src/features/dashboard/hooks/useDashboardData.ts:40`, polled every
  15 s), the users table's counter row
  (`src/features/users/hooks/useUsersPage.ts:37`), and the bug-report screen's GitLab
  flag (`src/features/feedback/hooks/useFeedbackReports.ts:17`).
- **Body / query parameters:** none
- **Response fields read** (mapped by `toSystemStatus`,
  `src/api/system/systemStatusMapper.ts:39-49`):

  | Path | Notes |
  | --- | --- |
  | `counts` | Read as an open map; `null`/absent stays `null`, and `null` is explicitly **not** zero (`systemStatusMapper.ts:19-27`) |
  | `counts.users` | Dashboard "Total users"; users-table total |
  | `counts.activeUsers` | Dashboard "Active accounts"; users-table active |
  | `counts.admins` | Dashboard "Admin accounts"; users-table admins |
  | `counts.openBugReports` | Dashboard "Open reports" |
  | `gitlabEnabled` | `?? false`. **Decides whether the "Create issue" button exists at all** (`src/features/feedback/components/BugReportIssueControl.tsx:35`) |

- **Mapped but never rendered:** `status`, `checkedAt`, `startedAt`, `uptimeSeconds`,
  `database` (and its `reachable`, `latencyMs`, `error`), `lastWrite` (and its `at`,
  `ageSeconds`, `action`, `actorName`). All are carried through
  `systemStatusMapper.ts` and typed in `systemStatusTypes.ts`, and no screen reads
  them — the System Status screen was removed (ADR 0003). The dashboard prints a
  *fixed* `DEGRADED` sentence when `counts` is null rather than reading `status`
  (`src/features/dashboard/dashboardModel.ts:111-115`).
- **Expected success status:** any 2xx. A database that is down is expected to be
  reported **inside** the payload rather than as a failing status
  (`SystemStatusService.ts:18-21`).
- **Error statuses handled:** none by status, and the failure policy differs per
  caller:
  - Users table: **swallowed entirely**; counters render as dashes
    (`useUsersPage.ts:35-41`).
  - Bug reports: **swallowed entirely**; `gitlabEnabled` becomes `false` and the
    escalation button disappears (`useFeedbackReports.ts:15-21`).
  - Dashboard: reported with the backend's `message`, fallback "The dashboard could
    not be refreshed.", while the last good numbers stay on screen
    (`useDashboardData.ts:15`, `:72`).
- **Success determined by:** status **and** body `success`.

### `GET /reports`

- **Call site:** `src/api/feedback/BugReportsService.ts:13`
- **Reached from:** `src/features/feedback/hooks/useFeedbackReports.ts:35`
- **Body / query parameters:** none — unpaged; the open/closed split is client-side
  (`src/features/feedback/model/reports.ts:9-21`).
- **Response fields read:**

  | Path | Notes |
  | --- | --- |
  | `bugReports` | **Required to be an array.** Note the key is `bugReports` while the path is `/reports` (`BugReportsService.ts:14`) |
  | `bugReports[].id` | Row key; addresses `PATCH`/`DELETE /reports/{id}` and the GitLab escalation |
  | `bugReports[].title` | Rendered; edit baseline |
  | `bugReports[].description` | Rendered; edit baseline |
  | `bugReports[].severity` | `LOW` \| `MEDIUM` \| `HIGH` \| `CRITICAL` (`src/domain/moderation.ts:26`); badge and select value |
  | `bugReports[].status` | `OPEN` \| `REVIEWED` \| `DISMISSED` \| `ACTION_TAKEN`; badge, select value, and the open/closed split |
  | `bugReports[].reporterName` | Rendered |
  | `bugReports[].reportedAt` | Rendered with time |
  | `bugReports[].issueUrl` | Truthiness-checked; when set, a link replaces the "Create issue" button (`components/BugReportIssueControl.tsx:24-33`) |

- **Defined but not read:** `bugReports[].issueState`
  (`src/api/feedback/feedbackTypes.ts:25`) — declared as `NONE` / `CREATED` /
  `FAILED` and never inspected; the presence of `issueUrl` is what the UI branches on.
- **Expected success status:** any 2xx carrying a `bugReports` array.
- **Error statuses handled:** none by status; fallback "Failed to load bug reports"
  (`useFeedbackReports.ts:7`, `:49`). A failure **after** the first successful load is
  deliberately not shown as a load error (`useFeedbackReports.ts:43`, `:49`).
- **Success determined by:** status, body `success`, **and** the presence of the
  `bugReports` array.

### `PATCH /reports/{id}`

- **Call site:** `src/api/feedback/BugReportsService.ts:19`
- **Reached from:** `src/features/feedback/hooks/useFeedbackReportActions.ts:47`
- **Body** — the two triage selects each send a single field; the edit form sends only
  what changed and never an empty object
  (`src/features/feedback/model/edit.ts:22-34`):

  | Field | Type | Required | Example |
  | --- | --- | --- | --- |
  | `status` | `"OPEN"` \| `"REVIEWED"` \| `"DISMISSED"` \| `"ACTION_TAKEN"` | no | `"REVIEWED"` |
  | `severity` | `"LOW"` \| `"MEDIUM"` \| `"HIGH"` \| `"CRITICAL"` | no | `"HIGH"` |
  | `title` | string | no | `"Crash on submit"` (trimmed; may not be emptied) |
  | `description` | string | no | `""` (trimmed; may be emptied) |

- **Query parameters:** none
- **Response fields read:** none. The whole queue is **refetched with
  `throwOnError`** so that a failed read-back can be told apart from a failed write
  (`useFeedbackReportActions.ts:35-44`).
- **Expected success status:** any 2xx.
- **Error statuses handled:** none by status; fallback "Failed to update the report.
  The change was not saved." (`useFeedbackReportActions.ts:7`). A **successful** write
  whose read-back fails is reported separately as "The change was saved, but reading it
  back failed…" (`src/utils/getErrorMessage.ts:45-47`).
- **Success determined by:** status **and** body `success`.

### `DELETE /reports/{id}`

- **Call site:** `src/api/feedback/BugReportsService.ts:27`
- **Reached from:** `src/features/feedback/hooks/useFeedbackReportActions.ts:63`
- **Body / query parameters:** none
- **Response fields read:** none; the queue is refetched.
- **Expected success status:** any 2xx, `204` included.
- **Error statuses handled:** none by status. A refusal is reported by the
  confirmation dialog rather than by the hook, deliberately
  (`useFeedbackReportActions.ts:71-74`).
- **Success determined by:** status **and** body `success`.

### `POST /reports/{id}/gitlab-issue`

- **Call site:** `src/api/feedback/GitlabIssueService.ts:11`
- **Reached from:** `src/features/feedback/hooks/useFeedbackReportActions.ts:58`
- **Body:** none sent — bodyless `POST`, **no `Content-Type` header**.
- **Query parameters:** none
- **Response fields read:** **none.** The declared `GitlabIssueResponse` has
  `issueUrl`, `issueIid` and `issueState`
  (`src/api/feedback/feedbackTypes.ts:41-47`), and the client reads none of them —
  it refetches `GET /reports` and takes the report's own `issueUrl` instead
  (`useFeedbackReportActions.ts:52-60`).
- **Expected success status:** any 2xx.
- **Error statuses handled:** none by status. A `503` — the documented answer when
  GitLab is not configured — is shown with the backend's `message`, fallback "The
  issue could not be created. Nothing was opened on the board."
  (`useFeedbackReportActions.ts:8`). A `503` is `transient`, so a **query** would
  retry it; this is a mutation and is not retried (`src/query.ts:43`).
  The button is normally hidden before this can happen, on `gitlabEnabled` from
  `GET /system/status`.
- **Success determined by:** status **and** body `success`. The call is assumed
  idempotent — a second press is expected to return the existing issue rather than
  open a second one (`src/api/feedback/GitlabIssueService.ts:7-8`).

---

## Defined but never called

Derived by comparing the type declarations and documentation in this repository
against the actual call sites listed above.

| Path / verb | Where it is mentioned | Status |
| --- | --- | --- |
| `GET /activity-logs`, `GET /activity-logs/meta` | `src/domain/auditLog.ts:5-7` (comment only) | **Not called.** The Activity Log screen was removed; a routing test asserts the route is gone (`src/App.integration.test.tsx:194-212`). |
| `GET /data/lectures/all`, `GET /data/professor/all` | `src/api/catalog/CatalogService.ts:29-31` (comment only) | **Not called yet — this row is scheduled to go.** Reading the active-only endpoints instead was reasonable while nothing could deactivate a row; the catalogue form changed that, and the cost is F-42: a deactivated lecture or professor leaves every screen the panel has, taking the id that `PATCH {"active": true}` needs with it. Decided 10 September 2026 that the panel moves its catalogue list onto these two (`docs/adminweb-tasks.md` §2b), on the **unprefixed** paths, with the bearer token. They are admin-only, they return every row with `active`, and they answer today. When the panel ships it, the two routes move into the `consumer-contract` block below and this row is deleted. |
| `POST /data/lectures`, `POST /data/professor` | Not referenced in `src/` at all | **Not called.** The panel has no catalogue-creation UI. |
| `POST /auth/logout-all` | Not referenced in `src/` at all | **Not called.** |
| `GET /admins/validate` | Not referenced in `src/` at all | **Not called.** This answers the open question in `docs/adminweb-tasks-from-backend.md:135` — the backend may delete it. |
| `POST /reports` (student bug submission) | Not referenced in `src/` at all | **Not called.** The panel reads and triages reports; it never files one. |
| `GET /data/lectures/{id}`, `GET /data/professor/{id}`, `GET /data/professor/id` | Not referenced in `src/` at all | **Not called.** |
| Any vote endpoint | Not referenced in `src/` at all | **Not called.** A case-insensitive search for `vote` across `src/` returns no hits. |
| Any `/admin`-prefixed path | Not referenced in `src/` at all | **Not called.** See [Path resolution](#path-resolution). |

### Declared response fields that no code reads

Present in a TypeScript interface, absent from every call site:

| Endpoint | Field | Declared at |
| --- | --- | --- |
| `GET /comments` | `comments[].lectureId` | `src/api/moderation/commentTypes.ts:20` |
| `GET /answers/reported` | `answers[].commentId` | `src/api/moderation/answerTypes.ts:29` |
| `GET /data/lectures` | `lectures[].professors[].active` | `src/api/catalog/catalogTypes.ts:13` |
| `GET /ratings` | `ratings[].author.id` | `src/api/catalog/ratingTypes.ts:39` |
| `GET /users/{id}/warnings` | `warnings[].createdFrom.createdAt` | `src/api/users/userWarningMapper.ts:9` |
| `GET /audit-logs` | `auditLogs[].actor.type`, `auditLogs[].actor.id` | `src/domain/auditLog.ts:72-78` |
| `GET /audit-logs/meta` | `actors[].role` | `src/domain/auditLog.ts:160` |
| `GET /audit-logs/meta` | `actorTypes` | Read into the model (`src/api/logs/AuditLogMetaService.ts:21`) and never rendered or sent |
| `GET /reports` | `bugReports[].issueState` | `src/api/feedback/feedbackTypes.ts:25` |
| `POST /reports/{id}/gitlab-issue` | `issueUrl`, `issueIid`, `issueState` — the entire response | `src/api/feedback/feedbackTypes.ts:41-47` |
| `GET /system/status` | `status`, `checkedAt`, `startedAt`, `uptimeSeconds`, `database.*`, `lastWrite.*` | `src/api/system/systemStatusTypes.ts:11-68` |
| `POST /auth/login` | everything except `authToken`; `expiresAt` in particular | `src/api/auth/authTypes.ts:23-26` |
| `GET /auth/me` | `user.status` | Documented by the backend, not declared or read here |

---

## Table 1 — Every endpoint

| # | Verb | Path (as the backend sees it) | Calling module | Line |
| --- | --- | --- | --- | --- |
| 1 | POST | `/auth/request-login` | `src/api/auth/LoginCodeService.ts` | 17 |
| 2 | POST | `/auth/login` | `src/api/auth/TokenLoginService.ts` | 23 |
| 3 | POST | `/auth/logout` | `src/api/auth/AuthSessionService.ts` | 14 |
| 4 | GET | `/auth/me` | `src/api/auth/AuthSessionService.ts` | 26 |
| 5 | GET | `/users` | `src/api/users/UserAccountsService.ts` | 24 |
| 6 | GET | `/users/{id}` | `src/api/users/UserAccountsService.ts` | 49 |
| 7 | PATCH | `/users/{id}` | `src/api/users/UserAccountsService.ts` | 59 |
| 8 | DELETE | `/users/{id}` | `src/api/users/UserAccountsService.ts` | 69 |
| 9 | PATCH | `/users/{id}/block` | `src/api/users/UserAccountsService.ts` | 77 |
| 10 | PATCH | `/users/{id}/unblock` | `src/api/users/UserAccountsService.ts` | 85 |
| 11 | GET | `/users/{id}/warnings` | `src/api/users/UserWarningsService.ts` | 26 |
| 12 | POST | `/users/{id}/warnings` | `src/api/users/UserWarningsService.ts` | 17 |
| 13 | PATCH | `/users/{id}/warnings/{warningId}` | `src/api/users/UserWarningsService.ts` | 36 |
| 14 | DELETE | `/users/{id}/warnings/{warningId}` | `src/api/users/UserWarningsService.ts` | 41 |
| 15 | GET | `/comments` | `src/api/moderation/ContentService.ts` | 24 |
| 16 | PATCH | `/comments/{id}` | `src/api/moderation/ContentService.ts` | 31 |
| 17 | DELETE | `/comments/{id}` | `src/api/moderation/ContentService.ts` | 36 |
| 18 | GET | `/comments/reported` | `src/api/moderation/ReportsService.ts` | 23 |
| 19 | PATCH | `/comments/reported/{id}` | `src/api/moderation/ReportsService.ts` | 33 |
| 20 | DELETE | `/comments/reported/{id}` | `src/api/moderation/ReportsService.ts` | 38 |
| 21 | GET | `/answers` | `src/api/moderation/ContentService.ts` | 24 |
| 22 | PATCH | `/answers/{id}` | `src/api/moderation/ContentService.ts` | 31 |
| 23 | DELETE | `/answers/{id}` | `src/api/moderation/ContentService.ts` | 36 |
| 24 | GET | `/answers/reported` | `src/api/moderation/ReportsService.ts` | 23 |
| 25 | PATCH | `/answers/reported/{id}` | `src/api/moderation/ReportsService.ts` | 33 |
| 26 | DELETE | `/answers/reported/{id}` | `src/api/moderation/ReportsService.ts` | 38 |
| 27 | GET | `/data/lectures` | `src/api/catalog/LectureCatalogService.ts` | 13 |
| 28 | PATCH | `/data/lectures/{id}` | `src/api/catalog/LectureCatalogService.ts` | 23 |
| 29 | DELETE | `/data/lectures/{id}` | `src/api/catalog/LectureCatalogService.ts` | 28 |
| 30 | GET | `/data/professor` | `src/api/catalog/ProfessorCatalogService.ts` | 13 |
| 31 | PATCH | `/data/professor/{id}` | `src/api/catalog/ProfessorCatalogService.ts` | 23 |
| 32 | DELETE | `/data/professor/{id}` | `src/api/catalog/ProfessorCatalogService.ts` | 28 |
| 33 | GET | `/ratings` | `src/api/catalog/RatingReadService.ts` | 19 |
| 34 | DELETE | `/ratings/{id}` | `src/api/catalog/RatingDeletionService.ts` | 8 |
| 35 | GET | `/audit-logs` | `src/api/logs/AuditLogReaderService.ts` | 17 |
| 36 | GET | `/audit-logs/meta` | `src/api/logs/AuditLogMetaService.ts` | 16 |
| 37 | POST | `/audit-logs/{id}/revert` | `src/api/logs/AuditLogRevertService.ts` | 11 |
| 38 | GET | `/system/status` | `src/api/system/SystemStatusService.ts` | 23 |
| 39 | GET | `/reports` | `src/api/feedback/BugReportsService.ts` | 13 |
| 40 | PATCH | `/reports/{id}` | `src/api/feedback/BugReportsService.ts` | 19 |
| 41 | DELETE | `/reports/{id}` | `src/api/feedback/BugReportsService.ts` | 27 |
| 42 | POST | `/reports/{id}/gitlab-issue` | `src/api/feedback/GitlabIssueService.ts` | 11 |

`/comments/**` and `/answers/**` are the same six generic paths with the resource name
substituted (`src/api/moderation/CommentsService.ts:23`,
`src/api/moderation/AnswersService.ts:15`). Renaming either noun changes both the path
segment **and** the expected response key.

## Table 2 — Fields read, by endpoint

Bold marks a field whose absence raises an error rather than degrading.

| Endpoint | Fields read |
| --- | --- |
| `POST /auth/request-login` | — (envelope only) |
| `POST /auth/login` | **`authToken`** |
| `POST /auth/logout` | — |
| `GET /auth/me` | **`user.id`**, `user.username`, `user.name`, `user.kitEmail`, `user.email`, `user.role`, `user.isSuperAdmin` |
| `GET /users` | **`users[]`**, `users[].id`, `.username`, `.kitEmail`, `.email`, `.role`, `.status`, `.joined`, `.lastOnline`, `.biography`, `.warnings`, `.reports`, `.credibilityScore`, `nextCursor` |
| `GET /users/{id}` | `id`, `username`, `kitEmail`, `email`, `role`, `status`, `joined`, `lastOnline`, `biography`, `warnings`, `reports`, `credibilityScore` (top level) |
| `PATCH /users/{id}` | — |
| `DELETE /users/{id}` | — |
| `PATCH /users/{id}/block` | — |
| `PATCH /users/{id}/unblock` | — |
| `GET /users/{id}/warnings` | **`warnings[]`**, `warnings[].id`, `.userID`, `.message`, `.createdAt`, `.createdFrom.id`, `.createdFrom.name` |
| `POST /users/{id}/warnings` | — |
| `PATCH /users/{id}/warnings/{warningId}` | — |
| `DELETE /users/{id}/warnings/{warningId}` | — |
| `GET /comments` | **`comments[]`**, `comments[].id`, `.content`, `.status`, `.author.id`, `.author.name`, `.author.role`, `.author.warnings`, `.author.status`, `.postContext`, `.answers`, `.reports`, `.createdAt` |
| `PATCH /comments/{id}` | — |
| `DELETE /comments/{id}` | — |
| `GET /comments/reported` | **`comments[]`**, `comments[].id`, `.commentId`, `.commentContent`, `.postContext`, `.reportText`, `.reportedUser.{id,name,role,warnings,status}`, `.reporter.{id,name,role,warnings,status}`, `.reason`, `.status`, `.date` |
| `PATCH /comments/reported/{id}` | — |
| `DELETE /comments/reported/{id}` | — |
| `GET /answers` | **`answers[]`**, `answers[].id`, `.content`, `.status`, `.author.{id,name,role,warnings,status}`, `.commentId`, `.commentPreview`, `.postContext`, `.reports`, `.createdAt` |
| `PATCH /answers/{id}` | — |
| `DELETE /answers/{id}` | — |
| `GET /answers/reported` | **`answers[]`**, `answers[].id`, `.answerId`, `.answerContent`, `.commentContent`, `.postContext`, `.reportText`, `.reportedUser.{id,name,role,warnings,status}`, `.reporter.{id,name,role,warnings,status}`, `.reason`, `.status`, `.date` |
| `PATCH /answers/reported/{id}` | — |
| `DELETE /answers/reported/{id}` | — |
| `GET /data/lectures` | **`lectures[]`**, `lectures[].id`, `.name`, `.code`, `.semesterYear`, `.semesterSeason`, `.active`, `.lectureType`, `.professors[].id`, `.professors[].firstName`, `.professors[].lastName`, `.commentCount`, `.ratingCount` |
| `PATCH /data/lectures/{id}` | — |
| `DELETE /data/lectures/{id}` | — |
| `GET /data/professor` | **`professors[]`**, `professors[].id`, `.firstName`, `.lastName`, `.active`, `.lectureIds`, `.averageRating`, `.ratingCount` |
| `PATCH /data/professor/{id}` | — |
| `DELETE /data/professor/{id}` | — |
| `GET /ratings` | **`ratings[]`**, `ratings[].id`, `.lectureId`, `.author.name`, `.createdAt`, `.scores` (open map), `nextCursor` |
| `DELETE /ratings/{id}` | — |
| `GET /audit-logs` | **`auditLogs[]`**, `auditLogs[].id`, `.action`, `.createdAt`, `.actor.name`, `.actor.email`, `.actor.role`, `.target`, `.target.label`, `.target.type`, `.target.id`, `.changes` (open map), `.changes.*.before`, `.changes.*.after`, `.metadata` (open map), `.revertible`, `.revertBlockedReason`, `.revertedByAuditId`, `nextCursor` |
| `GET /audit-logs/meta` | **`actors[]`**, `actors[].id`, `.name`, `.email`, **`actions[]`**, **`targetTypes[]`**, `actorTypes` (optional, unused) |
| `POST /audit-logs/{id}/revert` | — (only `message` and `reason` on failure) |
| `GET /system/status` | `counts`, `counts.users`, `counts.activeUsers`, `counts.admins`, `counts.openBugReports`, `gitlabEnabled` |
| `GET /reports` | **`bugReports[]`**, `bugReports[].id`, `.title`, `.description`, `.severity`, `.status`, `.reporterName`, `.reportedAt`, `.issueUrl` |
| `PATCH /reports/{id}` | — |
| `DELETE /reports/{id}` | — |
| `POST /reports/{id}/gitlab-issue` | — |

## Table 3 — Statuses the client handles, by endpoint

Every endpoint inherits the shared rules in
[How the client decides success and failure](#how-the-client-decides-success-and-failure).
This table lists only the statuses a **call site** treats specially.

| Endpoint | Status | What the client does |
| --- | --- | --- |
| `POST /auth/request-login` | `429` | Fixed wording; `Retry-After` parsed but never shown |
| `POST /auth/login` | `403` | "not authorized for the admin panel"; token cleared |
| `POST /auth/logout` | *any* | Swallowed; signs out locally regardless |
| `GET /auth/me` (page load) | `401` | Ends the session and returns to login |
| `GET /auth/me` (page load) | *any other* | **Silently ignored**; the stored session is kept |
| `GET /auth/me` (after login) | *any* | Fails the sign-in with "your admin account could not be read" |
| `GET /users/{id}` | `404` | **Not an error** — renders as "this account is gone" |
| `GET /users/{id}/warnings` | `404` | Same branch as above; renders the profile as gone |
| `GET /audit-logs` | `404` | Reinterpreted as "this backend has no audit endpoints" |
| `GET /audit-logs/meta` | `404` | Same |
| `POST /audit-logs/{id}/revert` | `409` | Reads `error.reason`; maps 5 known codes, generic fallback for the rest |
| `GET /system/status` (users table) | *any* | Swallowed; counters render as dashes |
| `GET /system/status` (bug reports) | *any* | Swallowed; `gitlabEnabled` becomes `false` |
| **every other endpoint** | *any* | No status branch. `message` is shown as the backend wrote it, or a per-screen fallback. |

Global, not per endpoint:

| Status | Where | Effect |
| --- | --- | --- |
| `401` on any authenticated request | `src/api/client/ApiClient.ts:116-118` | Session expiry listener fires; `AuthContext` clears the session |
| `>= 500` on any query | `src/query.ts:27-30` | Retried up to twice |
| `< 500` on any query | `src/query.ts:27-30` | Not retried |
| any status on any mutation | `src/query.ts:43` | Never retried |
| `405` | — | No special handling; `Allow` header never read |
| `415` | — | No special handling |
| `503` | — | No special handling; retried as a query, not as a mutation |

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
| POST | /auth/request-login | none | - |
| POST | /auth/login | none | authToken |
| POST | /auth/logout | bearer | - |
| GET | /auth/me | bearer | user.id, user.username, user.name, user.kitEmail, user.email, user.role, user.isSuperAdmin |
| GET | /users | bearer | users[], users[].id, users[].username, users[].kitEmail, users[].email, users[].role, users[].status, users[].joined, users[].lastOnline, users[].biography, users[].warnings, users[].reports, users[].credibilityScore, nextCursor |
| GET | /users/{id} | bearer | id, username, kitEmail, email, role, status, joined, lastOnline, biography, warnings, reports, credibilityScore |
| PATCH | /users/{id} | bearer | - |
| DELETE | /users/{id} | bearer | - |
| PATCH | /users/{id}/block | bearer | - |
| PATCH | /users/{id}/unblock | bearer | - |
| GET | /users/{id}/warnings | bearer | warnings[], warnings[].id, warnings[].userID, warnings[].message, warnings[].createdAt, warnings[].createdFrom.id, warnings[].createdFrom.name |
| POST | /users/{id}/warnings | bearer | - |
| PATCH | /users/{id}/warnings/{warningId} | bearer | - |
| DELETE | /users/{id}/warnings/{warningId} | bearer | - |
| GET | /comments | bearer | comments[], comments[].id, comments[].content, comments[].status, comments[].author.id, comments[].author.name, comments[].author.role, comments[].author.warnings, comments[].author.status, comments[].postContext, comments[].answers, comments[].reports, comments[].createdAt |
| PATCH | /comments/{id} | bearer | - |
| DELETE | /comments/{id} | bearer | - |
| GET | /comments/reported | bearer | comments[], comments[].id, comments[].commentId, comments[].commentContent, comments[].postContext, comments[].reportText, comments[].reportedUser.id, comments[].reportedUser.name, comments[].reportedUser.role, comments[].reportedUser.warnings, comments[].reportedUser.status, comments[].reporter.id, comments[].reporter.name, comments[].reporter.role, comments[].reporter.warnings, comments[].reporter.status, comments[].reason, comments[].status, comments[].date |
| PATCH | /comments/reported/{id} | bearer | - |
| DELETE | /comments/reported/{id} | bearer | - |
| GET | /answers | bearer | answers[], answers[].id, answers[].content, answers[].status, answers[].author.id, answers[].author.name, answers[].author.role, answers[].author.warnings, answers[].author.status, answers[].commentId, answers[].commentPreview, answers[].postContext, answers[].reports, answers[].createdAt |
| PATCH | /answers/{id} | bearer | - |
| DELETE | /answers/{id} | bearer | - |
| GET | /answers/reported | bearer | answers[], answers[].id, answers[].answerId, answers[].answerContent, answers[].commentContent, answers[].postContext, answers[].reportText, answers[].reportedUser.id, answers[].reportedUser.name, answers[].reportedUser.role, answers[].reportedUser.warnings, answers[].reportedUser.status, answers[].reporter.id, answers[].reporter.name, answers[].reporter.role, answers[].reporter.warnings, answers[].reporter.status, answers[].reason, answers[].status, answers[].date |
| PATCH | /answers/reported/{id} | bearer | - |
| DELETE | /answers/reported/{id} | bearer | - |
| GET | /data/lectures | bearer | lectures[], lectures[].id, lectures[].name, lectures[].code, lectures[].semesterYear, lectures[].semesterSeason, lectures[].active, lectures[].lectureType, lectures[].professors[].id, lectures[].professors[].firstName, lectures[].professors[].lastName, lectures[].commentCount, lectures[].ratingCount |
| PATCH | /data/lectures/{id} | bearer | - |
| DELETE | /data/lectures/{id} | bearer | - |
| GET | /data/professor | bearer | professors[], professors[].id, professors[].firstName, professors[].lastName, professors[].active, professors[].lectureIds, professors[].averageRating, professors[].ratingCount |
| PATCH | /data/professor/{id} | bearer | - |
| DELETE | /data/professor/{id} | bearer | - |
| GET | /ratings | bearer | ratings[], ratings[].id, ratings[].lectureId, ratings[].author.name, ratings[].createdAt, ratings[].scores, nextCursor |
| DELETE | /ratings/{id} | bearer | - |
| GET | /audit-logs | bearer | auditLogs[], auditLogs[].id, auditLogs[].action, auditLogs[].createdAt, auditLogs[].actor.name, auditLogs[].actor.email, auditLogs[].actor.role, auditLogs[].target, auditLogs[].target.label, auditLogs[].target.type, auditLogs[].target.id, auditLogs[].changes, auditLogs[].changes.*.before, auditLogs[].changes.*.after, auditLogs[].metadata, auditLogs[].revertible, auditLogs[].revertBlockedReason, auditLogs[].revertedByAuditId, nextCursor |
| GET | /audit-logs/meta | bearer | actors[], actors[].id, actors[].name, actors[].email, actions[], targetTypes[], actorTypes |
| POST | /audit-logs/{id}/revert | bearer | message, reason |
| GET | /system/status | bearer | counts, counts.users, counts.activeUsers, counts.admins, counts.openBugReports, gitlabEnabled |
| GET | /reports | bearer | bugReports[], bugReports[].id, bugReports[].title, bugReports[].description, bugReports[].severity, bugReports[].status, bugReports[].reporterName, bugReports[].reportedAt, bugReports[].issueUrl |
| PATCH | /reports/{id} | bearer | - |
| DELETE | /reports/{id} | bearer | - |
| POST | /reports/{id}/gitlab-issue | bearer | - |

<!-- consumer-contract:end -->
