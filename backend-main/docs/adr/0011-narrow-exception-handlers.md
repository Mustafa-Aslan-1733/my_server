# 0011 — Narrow `@ExceptionHandler`s over `ResponseEntityExceptionHandler`

## Decision

`GlobalExceptionHandler` declares **one exception type at a time** and does **not** extend
Spring's `ResponseEntityExceptionHandler`. Every error body is the project's
`BasicResponse` — `{message, success}` — or `ApiErrorResponse`, which is the same plus an
optional `reason` omitted when null.

## Context

Three findings in a row were "an unhandled exception reached the catch-all and answered 500
where the request was at fault": F-14 (a missing query parameter), F-15, F-17 (a body in an
unreadable media type, on all 37 body-reading routes), and later
[F-38](../test-findings.md#f-38--a-body-over-the-upload-size-limit-answered-500) (a body over
the size limit). Extending the base class is the first fix that comes to mind for all of them.

## Alternatives considered

**Extend `ResponseEntityExceptionHandler`.** Tried, measured, and wrong for three separate
reasons — verified against this project's Spring Framework 7.0.9 (resolved by
`spring-boot-starter-parent` 4.0.8; the reasons were first checked against 7.0.7 and re-checked
after the bump):

1. **The application does not start.** The context fails with
   `IllegalStateException: Ambiguous @ExceptionHandler method mapped for …
   HttpRequestMethodNotSupportedException`, because the project already defines its own handler
   for that type ([0010](0010-405-over-404-for-a-wrong-verb.md)) and the base class's bulk
   handler maps it a second time.
2. **Deleting that handler to "solve" it would silently close three findings at once.** The base
   class maps `HttpMediaTypeNotSupportedException` (F-17), `HttpRequestMethodNotSupportedException`
   (F-16) and `MissingServletRequestParameterException` (F-14) *together* — F-16's contract
   would change without anyone asking for it.
3. **The error body changes on every path.** The base class produces a `ProblemDetail`
   (`application/problem+json`), not `BasicResponse`. The Android client's error parsing depends
   on that shape.

Reason 1 still stands today. Reasons 2 and 3 have moved: F-16 and F-14 have since been corrected
to the codes the base class would have produced, so that argument is spent — but reason 3 is
not, and it is the one that would be discovered by a client rather than by a build.

## Rationale

A narrow handler claims exactly one exception type. It cannot change a neighbouring answer as a
side effect, and it cannot change the body shape.

## Consequences

- **New exception types have to be claimed one at a time, and something has to notice.** That is
  the cost, and it is real: F-38 was open from F-17's writeup until it was measured.
- **The status sweeps read only status codes, so each narrow handler needs a body test beside
  it.** Without one, replacing the handler with `ResponseEntityExceptionHandler` keeps the status
  green while the body silently becomes `ProblemDetail`.
  `unsupportedMediaTypeCarriesTheProjectErrorBody` and
  `TransportLimitTests.theSizeRefusalCarriesTheProjectErrorBody` exist for exactly that.
- A handler is only added for a state that can actually be **reached**. No general
  `MultipartException` handler was added: every route here reads a JSON body, so content
  negotiation answers 415 before any parser runs. Measured over a real container, not assumed.
- **The pin-then-flip method.** For F-17 the handler was added *first*, with the sweep's constant
  still at `500`; the red run then named all **37** body-reading routes as having moved — more
  than the 26 the finding first counted, because legacy and `/admin` paths are separate
  mappings. That red is the evidence the handler reaches every route rather than the one probed
  by hand. Only then was the constant flipped.

## Sources

[F-17](../test-findings.md#f-17--an-unsupported-media-type-returns-500-instead-of-415),
[F-38](../test-findings.md#f-38--a-body-over-the-upload-size-limit-answered-500);
`GlobalExceptionHandler`'s javadoc.
