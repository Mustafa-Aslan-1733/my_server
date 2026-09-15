# 0015 — Deletion is not split from anonymisation

## Decision

`DELETE /admin/users/{id}` goes on anonymising the account **in the same transaction that marks
it `DELETED`**. There is no grace window, no delayed scrub and no restore endpoint.

Soft-deleted accounts became **visible** instead — `GET /admin/users?status=DELETED`, `CHANGELOG`
`10.09 (18)` — which is a read, and reads nothing back into the row.

## Context

The admin panel asked for deleted accounts to be listable **and restorable**
([adminweb-tasks.md §6](../adminweb-tasks.md)). Listing them is two hours. Restoring them is not
possible against the current row, and the measurement is what settles it:

`StudentLifecycleService.deleteStudent` overwrites `username` and `kitEmail` with UUID-derived
placeholders, empties `biography` and nulls `blockedReason`. `id`, `createdAt`,
`credibilityScore` and every relation survive. The **only** surviving copy of the identity is
`audit_logs.target_label`, a display string built before the scrub; `biography` and
`blockedReason` have no copy anywhere.

So a restore that only flips the status produces an `ACTIVE` account with no identity that its
owner cannot sign into, because nobody receives mail at `@invalid.local` — a row that lies about
its own state, in the one list an operator trusts. That option was refused outright. A real
restore requires that the data still exist at restore time, which means not destroying it at
delete time: **deletion split from anonymisation**, a `DELETED` mark now and a scrub after a
window.

## Alternatives considered

**Recover the identity by parsing `audit_logs.target_label`.** It is one string,
`username + " (" + kitEmail + ")"`, built by a mapper for humans. Splitting it back into two
fields is F-4's shape exactly — parsing a formatted message to recover data that was typed
before someone formatted it — and it breaks on any username containing `" ("`. It also cannot
return `biography`, which no copy exists of, and re-applying the original address can collide
with the `unique` constraint on `kit_email`.

**Split deletion from anonymisation** — a new `students.anonymized_at`, a job that scrubs past
the window, a restore endpoint and a `USER_RESTORED` action. Roughly six files in `src/main`,
two migrations, a new controller, and seven characterization tests inverted. Estimated 1.5–2
days.

## Rationale

**The blocking reason is not the engineering cost — it is what the window means.** For its whole
length the account is *not* anonymised: a user has asked to be deleted and their real name and
real address stay in the table. That is a data-protection posture, and it is not a decision a
commit message gets to make on a KIT project. Nothing in the record established that posture, so
nothing here could implement it.

**The cost is real too, and it is infrastructure rather than code.** There is no `@Scheduled` and
no `@EnableScheduling` anywhere in `src/main`. A delayed scrub would be the first scheduled job
this service has ever run, which is a new operational question (what happens when it does not
fire?) and a new test question (which of the five layers of [ADR-0001](0001-five-test-layers.md)
owns a job that fires on a clock?) on top of the feature.

**And F-46 is a precondition, not a detail.** `LoginCodeService:76-79` flips a `DELETED` account
back to `ACTIVE` when a login code is requested for its address. Today that branch is unreachable
for admin deletions, because the address was scrubbed to `@invalid.local` in the same
transaction — **the scrub is what is containing it**. Under a grace window the real address
stays in the table, and the branch becomes a silent, unaudited restore of any account, available
to anyone who can ask for a login code at a known address. Building the window before fixing
F-46 would open that hole deliberately.

**What the panel actually asked for is served without any of this.** The audit log already
carries the identity, the timestamp and the acting administrator, and outlives the account —
`audit_logs` has no foreign key to `students` and no retention job. Joining `USER_DELETED` on
`target.id` against the now-listable row gives a truthful screen: this account existed, this was
its name, this admin deleted it on this date, its content is still here. Visibility was the
requirement; restoration was the proposed mechanism.

## Consequences

- **Deletion stays irreversible**, and the API says so in one place rather than two:
  `admin-api.md` states it under `PATCH /admin/users/{id}` and repeats it under the new filter.
- **Visibility is read-only.** `ModeratedStudents.findMutable` was deliberately left alone, so
  every mutating route on a deleted account still answers `404`. The listing change cannot grow
  into a restore by accident.
- **The panel joins two endpoints** to render one row, and must treat `target.label` as a
  display string. If it ever needs the name and address as separate values, the fix is typed
  fields on the audit entry, not a `split()` in the panel.
- **F-46 is now load-bearing in two directions.** It is a defect on its own terms, and it is the
  first thing to close if this decision is ever revisited. **Closed on 10 September** as far as
  the record goes — see [ADR-0016](0016-self-service-lifecycle-recorded-not-authorised.md) — but
  the reason it was load-bearing here was never the missing entry. It was the revival, and that
  is now **F-48**, still open: an unauthenticated caller can put a deleted account back to
  `ACTIVE`. **That is what has to close before this decision is revisited**, because a scrub-later
  window would give such a caller something to undo.
- **Self-deletions are outside all of it.** The app's own `PATCH /account/deleteAccount` does not
  anonymise, so a self-deleted account appears in the new listing under its real name. It does
  now carry an audit entry — `USER_SELF_DELETED`, not `USER_DELETED`, which is the constant to
  join against.

## Sources

[adminweb-tasks.md §6](../adminweb-tasks.md) — the measurement of all four obstacles and the
three costed options; F-46 in [TODO.md](../TODO.md); `CHANGELOG` `10.09 (18)`;
[ADR-0007](0007-characterization-and-inversion.md) for the two inverted tests.
