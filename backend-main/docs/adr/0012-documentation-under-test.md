# 0012 — The admin API document is tested against the code

## Decision

`docs/admin-api.md` carries an additive appendix — a table of `(METHOD, path) -> statuses`
between `<!-- contract-table:start -->` / `<!-- contract-table:end -->` markers — and
`AdminApiDocumentationDriftTests` checks it against the running application **in both
directions**. The prose is not generated and not touched.

## Context

The document the panel team treats as authoritative fell behind the code **four times**: F-5, a
status that had changed; F-16, a 404 that had become a 405; F-22, a body that no longer existed;
F-26, a field that had never been right. Each was corrected by hand.

This repository already refuses hand-maintained lists in its sweeps — a list goes stale silently
and then passes because it is looking at less than it thinks — and had not applied the rule to
its own documentation.

## Alternatives considered

**Generate the document from the OpenAPI schema.** Rejected outright: the prose carries the
*reasons*, and a schema cannot produce them. It cannot say that the `409` on
`PATCH /admin/users/{id}` is about not locking anyone out of an API with no other recovery path,
or that `GET /admin/system/status` answers 200 with `status: DOWN` because a page showing "the
API is up but the database is not" cannot do that if the request fails.

**Check the claims against the generated schema.** It cannot work: the controllers declare
**no** `@ApiResponse`, `@Operation` or `@ResponseStatus` across all 80 mappings, so springdoc
emits only the default `200`. `OpenApiContractTests` can check a route *exists* and nothing
more.

**Annotate all 80 mappings with `@ApiResponse`** so the schema does carry statuses. ~80 edits
for a documentation property; it states the contract twice, in the annotation and in the handler
that actually produces it; and springdoc still would not know which *condition* produces which
status.

**Keep the claims in a test fixture instead of the document.** The panel team reads the
document, not `src/test`. A contract they cannot see is not a contract.

## Rationale

Additive and separately delimited. The prose stays the authority on **why**; the table is the
same claims in a shape a test can read; and neither can drift without something going red.

The markers collide with nothing: the document had no HTML comments and no anchor convention
before this.

## Consequences

- **A new administrative route now fails the build until it is documented** — that is the point,
  and it is a real cost on whoever adds one.
- One named exemption, `GET /admins/validate`, carried with its reason (legacy, being retired in
  favour of `GET /admin/auth/me`) the way `PUBLIC_ROUTES` carries its own.
- **The table is a second place to edit.** The third check — asserting the *count* of claims the
  sweep cannot provoke — is what stops that second place from filling up with unchecked claims.
- **It found a documentation error on its first run:** `POST /admin/auth/login` was recorded as
  claiming 401/403 for the caller's session, when those are answers about the credentials in the
  body. The document's own preamble draws that line and the table had flattened it.
- **What it does not catch**, found by trying to make it fail: the 404 probe needs an id to
  invent, so a 404 claimed on a route with no path variable is never checked. Written into the
  test's javadoc rather than left to be rediscovered.

## Sources

[P-8](../test-findings.md#p-8--admin-apimd-drifted-five-times-and-nothing-noticed);
`AdminApiDocumentationDriftTests`; `docs/admin-api.md`, *The contract, as a table*.
