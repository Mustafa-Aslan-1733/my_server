# 0010 — 405 with `Allow` instead of 404 for a verb a path does not map

## Decision

A path that exists, called with a verb it does not map, answers **405** with an `Allow` header
naming the verbs that do map — on all 122 routes. It answered 404 before.

## Context

`GlobalExceptionHandler` had no handler for `HttpRequestMethodNotSupportedException`, so those
requests fell through to the same 404 as a path that does not exist. "No such path" and "the
path exists, the verb does not" were **indistinguishable from the response**, and a client that
cannot tell them apart cannot retry correctly.

Found by `ApiProtocolContractTests`, which sweeps every mapped path with every verb it does not
declare.

## Alternatives considered

**Leave it at 404 and document it.** This is where it sat first: the finding was *pinned rather
than fixed*, because it changes a contract and had **two documented dependents**.

## Rationale

The decision turned on what those two dependents actually were. Both were examined, and **both
turned out to be documentation _of_ the defect rather than uses of it** — `admin-api.md`
describing the 404 because that is what the code did, not because anything relied on it. Once
that was established there was no dependent left to break, and RFC 9110 requires the `Allow`
header on a 405 anyway.

The pin is what made the change cheap: flipping `ApiContract.WRONG_VERB_STATUS` from 404 to 405
carried **93 refusals across two suites** with no other edit, which is exactly what the constant
was introduced to do.

## Consequences

- `POST /admin/reports` now answers `405` with `Allow: GET` rather than 404 — the one path in
  the API deliberately without an administrative twin, and now the clearest example of the
  distinction.
- The 404 and 405 answers must not collapse back into each other. Both are asserted separately,
  and `wrongVerbCarriesTheProjectErrorBody` pins the body as well as the status.
- This is one of the four client-visible contract changes the suite made deliberately, with the
  others being F-3, F-5 and F-20.
- It is also the first of the three reasons **not** to extend
  `ResponseEntityExceptionHandler` — see [0011](0011-narrow-exception-handlers.md).

## Sources

[F-16](../test-findings.md#f-16--the-wrong-http-method-returns-404-instead-of-405);
`com.pse.support.ApiContract`; `CHANGELOG`.
