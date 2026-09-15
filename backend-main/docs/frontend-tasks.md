# Android tasks

What the Android client repository has to do, from reading its own audit
([frontend-consumer-contract.md](frontend-consumer-contract.md),
[frontend-consumer-findings.md](frontend-consumer-findings.md)) against this backend.

Ordered by what a user is losing today. Everything here is a change in **your** repository;
where the backend changed instead, it says so and there is nothing for you to do.

## 1. Reporting an answer has never worked — and the backend already fixed it

`APIService.java:247` sends `POST /answers/report`. This API has only ever served
`POST /social/answers/report`; the unprefixed path matched `/answers/{id}` on the moderation
controller, which maps `PATCH` and `DELETE` and no `POST`, so every report was answered **405**.

Your audit filed this under naming inconsistency (F-3.8) because from your side it looks like
one. It was a dead route.

Nobody saw it because `logic/CommentHelper.java:86-88` shows `bug_report_submitted` and dismisses
the dialog immediately after `enqueue`, before the response arrives. Every report a user has ever
filed against an answer failed, and every user was told it succeeded.

- [x] **Backend: `POST /answers/report` is now served** and answers exactly what the `/social`
      path answers. An installed app starts filing reports as soon as this deploys. Nothing to
      release.
- [ ] Move to `POST /social/answers/report`, where the other social routes already are, and tell
      us — the alias exists only until you have, and then it goes.
- [ ] Show the outcome instead of announcing it. This one is worth doing on its own merits: it is
      the reason a route could be dead for the life of the product without a single report.

## 2. `GET /account/ratings` can crash the list

`Fragments/ListOfMyRatingsFragment.java:107-110` dereferences `response.body().ratings()` with no
status check, no null check on the body and none on the array. Two things this API guarantees,
now under test, and one it does not:

- **`ratings` is always present on a 2xx** and is an array — empty, never null.
- **`ratings[].lecture.professors` is always present** and is an array, and its order is stable:
  last name, first name, id. So `.get(0)` opens the same professor on every load.
- **`professors` can be empty.** A lecture with no staff on record is legitimate and this API
  serves `[]` for it. `Adapters/ListOfMyRatingsAdapter.java:56` calls `.get(0)` on it, which
  throws `IndexOutOfBoundsException`.

- [ ] Guard the body and the status before reading `ratings`. Any 4xx makes `response.body()`
      null and the NPE lands on the callback thread.
- [ ] Guard `professors` for emptiness before `.get(0)`.

The array's own order was undefined until now and is **newest first** from this release
(`CHANGELOG`, 9.09 (17)). You re-sort ratings client-side already, so nothing should change.

## 3. Adding a rating category will crash the rating screen

`Adapters/RatingAdapter.java:89,98` call `category().getDisplayName()` with no null check, and
the categories path at `RatingsFragment.java:561` does not filter nulls the way the averages path
does. Gson maps an unrecognised enum value to `null`, so **one new `RatingCategory` constant on
this side takes down the rating screen** for any lecture that offers it.

- [x] Backend: the 26 constants are pinned by `ConsumerEnumContractTests`, whose failure message
      says a category cannot be added before this client ships. We cannot add one by accident.
- [ ] Filter nulls on the categories path, so the guarantee stops being load-bearing.

`VoteType.NONE` exists on this side and you do not declare it. That one is safe and needs
nothing: you never send it, and no response carries a vote type — only the `upVotes` and
`downVotes` counters.

## 4. An empty notification id

`Fragments/NotificationFragment.java:246` calls `markNotificationsAsSeen` before the emptiness
guard at `:253-254`, so a payload missing `notificationID` produces
`PATCH /social/notifications/` with a trailing empty segment.

That answers **404 `Not found`** — not a 500, and it does not reach the collection route, which
answers 405 for a `PATCH`. Pinned by `ApiProtocolContractTests`. Harmless, and still worth moving
the guard above the call.

## 5. Two routes you declare and never call

`POST /auth/validate` and `GET /social/sync/comments`. Both are served and neither is called from
`app/src/main`.

- `/auth/validate` would answer the question `Activities/StartActivity.java:33-43` currently
  answers from local storage alone — which is why an expired session is only ever discovered by
  whichever call happens to fail first (your F-2.3).
- `/social/sync/comments` is kept on this side, with its six tests. It is a live public route and
  those tests are the only thing checking it; deleting coverage of a served route because one
  client stopped calling it would leave the route and lose the check.

## Not a task, recorded so it is not rediscovered

`GET /ratings/own/{lectureId}` does **not** collide with `GET /ratings/{lectureId}` — three
segments against two, and `own` is never captured as a lecture id. Your F-3.11 marks this
undetermined from your side; it is now pinned here.
