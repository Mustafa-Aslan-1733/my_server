# 0013 — Consumer expectations live in this repository, as a generated list under test

## Decision

Each consumer repository's contract — the routes it calls and the fields it reads — is kept
**here**, in `docs/adminweb-consumer-contract.md` and `docs/frontend-consumer-contract.md`, with
a delimited `consumer-contract` block that `ConsumerContractSweepTests` checks against the
running application in both directions. No contract-testing framework is introduced.

## Context

The suite proves that every route behaves correctly. It does not prove that the behaviour is
what the two clients read, and the gap is not theoretical: the suite was fully green while
`GET /auth/me` was unmapped and the admin panel could not sign anybody in, and while
`POST /answers/report` answered `405` to every report the Android app has ever filed. Both were
found by reading the two consumer audits by hand.

Neither failure was visible from either side. The panel converts a failed identity read into
"your admin account could not be read"; the app tells the user a report was submitted before the
response arrives. A route can be dead for a whole release and produce no signal anywhere.

The field half is worse, because it is silent by construction. The panel's `expectArray`
verifies that a field is an array and casts the elements without checking them, so a renamed
field inside a list renders as an empty cell. Gson leaves an unmatched field `null`. Nothing on
either side raises.

## Alternatives considered

**Pact, or another consumer-driven contract framework.** The mechanism it provides is a broker:
consumers publish expectations, the provider verifies them in CI, and the two pipelines exchange
results. This project has no shared pipeline — CI is a private GitLab that neither consumer
repository builds on, and this repository cannot read its results either (`docs/TODO.md`, item
1). Without the broker, Pact reduces to a JSON file in this tree plus a dependency. That is what
is being built here anyway, minus the dependency.

**Verify against the generated OpenAPI schema.** The schema knows the response types, which is
the half already covered by reflection. It does not know which fields a *client* reads, which is
the entire question. The same objection ADR 0012 records applies: the controllers declare no
`@ApiResponse`, so the schema is thin about everything except shape.

**Keep the consumer audits as prose and read them by hand.** They are excellent prose and they
found both breaks. They will also go stale the first time a route moves, and nothing will say
so — which is this repository's stated reason for never keeping a route list by hand.

**Parse the prose tables directly instead of adding a block.** Tried, and abandoned on evidence:
the two documents use a leading-dot shorthand for nested fields and resolve it differently — one
against the element root, one against the previous path. A parser has to guess, and it guessed
wrong on `ratings[].lecture.semesterSeason`. A block whose paths are written out in full removes
the guess.

## Rationale

It is ADR 0012's mechanism, applied a second time to a second document. The prose carries the
reasons and stays untouched; a delimited block carries the same claims in a shape a test reads;
and the block sits in the file the person editing the prose already has open.

Fields are resolved against the handler's **return type** rather than against a live response.
That reaches every field of every route without a fixture per endpoint — including nested ones
no probe would populate — and it is precise about the failure mode that matters, which is a
name, not a value.

## Consequences

- **It found two live defects in the admin panel on its first run**: the ratings tab reads
  `author` and `scores`, and this API has served `student` and `topics`, with a worked example
  in `admin-api.md`, since the endpoint existed. Both reads are null-guarded on the panel side,
  so the author column shows "Unknown" on every row and the scores column is always empty.
  Neither would ever have produced an error report.
- **A route a consumer calls cannot be deleted quietly.** `ConsumerContractSweepTests` names the
  consumer, because 42 routes belong to one client and 24 to the other and a removal decision
  needs to know which.
- **The blocks are a second place to edit**, with the same guard ADR 0012 uses: the count of
  routes each block declares is asserted per consumer, so a formatting slip that swallows rows
  fails rather than passing quietly. That guard earned itself immediately — it caught a
  separator row welded onto the first data row, which had been silently costing one route.
- **`READ_BUT_NOT_SERVED` is asserted as an exact set, not a floor.** A mismatch being *fixed*
  is as red as a new one appearing, so the list cannot fill up with entries nobody revisits.
- **What it does not check** is whether the client is right about its own behaviour, and whether
  a field's *value* is correct. The first belongs to the consumers' repositories; the second is
  what each endpoint's own tests are for.

## Sources

`docs/adminweb-consumer-contract.md` and `docs/adminweb-consumer-findings.md`;
`docs/frontend-consumer-contract.md` and `docs/frontend-consumer-findings.md`;
[ADR 0012](0012-documentation-under-test.md); `ConsumerContractSweepTests`.
