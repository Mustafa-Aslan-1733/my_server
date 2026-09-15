# 0007 — Characterization tests, inverted rather than deleted

## Decision

When behaviour is found that looks wrong but is not being fixed yet, it is **pinned** by a test
that asserts what the code does today. When the behaviour is later corrected, that test's
assertion is **inverted — never deleted.**

## Context

The suite was written against a running system, so most of what it found was pre-existing. A
finding that cannot be fixed immediately — because it changes a client contract, or because the
decision belongs to someone else — still has to be recorded somewhere that cannot go stale.

## Alternatives considered

**Delete the old test and write a new one.** The usual move. It loses the only durable record
that the old behaviour existed and was deliberate, and it makes the fix's diff look like new
work rather than like a correction.

**A `@Disabled` test describing the wanted behaviour.** A disabled test is not a test; it rots
without anyone noticing.

**Only comments.** Prose in a document is not checked by anything.

## Rationale

An inverted assertion is a **diff that shows the contract changing**, in the same file, with the
old value visible in the history. It is also proof the test was reading the thing it claimed to
read: a test that keeps passing after the behaviour is reversed was never asserting it.

The clearest return is BUG-3. Both halves were written **deterministically** — a
`LinkedHashSet` in a known-wrong order rather than a real `HashSet`, because `Professor`
overrides neither `equals` nor `hashCode` and real iteration order varies per run, so the
flakiness would have ended up in the test instead of in the defect. Because they did not depend
on real order, they pin the fix **exactly as reliably** as they pinned the defect. Four
assertions were inverted for F-22, one for F-28, three for F-36, three for F-37, two for F-31.

## Consequences

- The test name is part of the contract and gets renamed with the inversion
  (`…_mapsOnlyTheStatus` became `…_mapsTheStatusAndTheCommentVisibility`), so a stale name
  cannot outlive the behaviour it described.
- A characterization test asserts what **is**, not what **ought to be**, so reading one without
  its javadoc is misleading. Each carries a comment saying it is a pin and what would change it.
- **`ConcurrencyPostgresTests` is a deliberate, documented exception.** A race that does not
  happen produces no failure, so "one of these two requests fails" is flaky by construction and
  would pass on a fixed system *and* on a broken one that simply did not collide. Those tests
  assert the invariant that must hold either way. The latch that makes them collide was itself
  checked: removing it makes all four pass with zero collisions — the silent failure mode of
  the whole category.
- Pinned-and-not-fixed entries stay visible: F-34 and `GET /ratings/own/{lectureId}` are both
  open product questions with a test waiting to be inverted.

## Sources

`CLAUDE.md`; [BUG-3](../test-findings.md#bug-3--professor-assignment-order-silently-refuses-a-revert);
[test-plan.md](../test-plan.md) — *Two requests at once*, and *The 9 September defect pass*.
