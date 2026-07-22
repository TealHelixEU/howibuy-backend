# SFC responses are a history of immutable Attempts

Each user's engagement with the compass is a sequence of **Attempts**. An Attempt has two states,
`IN_PROGRESS → COMPLETED`, and a user has at most one `IN_PROGRESS` attempt at a time. Answers
(ordinal 1–5) are keyed by `(attempt, question)` and are freely editable while the attempt is
`IN_PROGRESS` — this is the pause/continue and revisit behaviour. Completion is a strictly explicit
action that validates every question is answered (rejecting with the unanswered question IDs when
not), then stamps `completed_at` and **freezes the attempt immutably** — a `COMPLETED` attempt is
never re-opened. Once `completed_at + stabilityWindow` has elapsed the user may start a new, **blank**
attempt; this eligibility is computed on read, with no scheduler flipping state. The stability window
is a static Quarkus config `Duration` (`sfc.stability-window`).

## Considered Options

- **History of immutable Attempts (chosen).** An external scientific model (built by other project
  partners) consumes the answers and needs them stable for a period, and trend/history is wanted.
  Immutable completed attempts give both: a stable artifact to consume and a longitudinal record.
- **Single mutable Response per user (no history).** Simpler, but discards history and gives the
  scientific model no stable, versioned artifact. Rejected once history was a real need.
- **One evolving result, re-opened for editing after the window and snapshotted into history.**
  Rejected in favour of independent immutable attempts: cleaner semantics, and each attempt is a
  genuine independent measurement rather than an edited-forward copy.
- **A scheduler that flips completed attempts back to "unlocked".** Rejected; unlock eligibility is
  a pure function of `completed_at + stabilityWindow`, so it is computed on read.

## Consequences

- A new attempt starts blank, not seeded from the previous one, so each round is answered
  independently (good longitudinal data) and progress tracking stays meaningful.
- The stability window gates *starting* a new attempt, not editing — completed attempts are already
  immutable, so nothing needs re-locking.
- How the scientific model / product assessment consumes completed attempts is out of scope here.
