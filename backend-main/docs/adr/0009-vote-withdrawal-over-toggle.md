# 0009 — `VoteType.NONE` rather than a toggle

## Decision

A vote is withdrawn by sending `voteType: "NONE"`, which **deletes** the vote row. Sending the
same direction twice rewrites the vote and does **not** remove it.

## Context

`VoteType` had two values, `UP` and `DOWN`, and there was no way to take a vote back. A second
vote in the same direction simply rewrote the existing row.

## Alternatives considered

**A toggle** — sending `UP` on a comment already voted `UP` removes the vote. The obvious
design, and what most clients do.

## Rationale

**A toggle changes what an existing client's retry does, without asking it.** A client that
re-sends `UP` after a timeout — not knowing whether the first request landed — would today
re-affirm the vote and under a toggle would silently remove it. That is a behaviour change to
code already deployed, introduced by a server-side edit those clients never see.

`NONE` is a **withdrawal, not a third direction**: it deletes the row rather than storing a
neutral value, so the read path is unchanged and nothing has to learn a new state.

## Consequences

- `NONE` is a new enum value that can appear in a **request**; the `CHANGELOG` names it, because
  a new enum value is client-visible by this project's own rule.
- It cannot appear in a **response** — there is no row to report.
- The vote path picked up three other corrections at the same time, all recorded separately:
  votes now write an audit entry (F-1 — vote manipulation did not appear in the moderation
  trail at all), `voteType` is validated at the boundary with `@NotNull` (F-2 — a `null` reached
  `setVote(null)` and surfaced as a 409 at flush time where a client expects 400), and both vote
  methods are `@Transactional` so a vote cannot commit while its audit entry does not.

## Sources

[F-3](../test-findings.md#f-3--a-vote-cannot-be-withdrawn), and
[the four gaps on the voting path](../test-plan.md#four-gaps-on-the-voting-path--all-four-closed);
`CHANGELOG`.
