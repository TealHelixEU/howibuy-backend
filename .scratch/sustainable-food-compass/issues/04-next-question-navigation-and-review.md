# 04 — Category navigation ("next question") + review with answers

Spec: `.scratch/sustainable-food-compass/spec.md` · ADR 0004.

**What to build:** A user working through a category can ask for the next question and be handed the
first one they haven't answered yet; when none remain, they are told the category is complete and are
never pushed into another category. They can also review a category's questions — or all questions —
together with the answers they have given so far.

**Blocked by:** 03.

**Status:** ready-for-agent

- [x] Requesting the next question in a category returns the lowest-position unanswered question in that category.
- [x] When every question in the category is answered, the next-question read returns a "category complete" signal and never a question from another category.
- [x] Reviewing a category returns its ordered questions each paired with the user's current answer (or none); the all-questions review does the same across all categories.
- [x] Revisiting is unrestricted and independent of the frontier: after changing an earlier answer, asking for "next" still routes to the earliest remaining unanswered question.
- [x] The end-of-category signal and the revisit-then-next behaviour are covered at the DB-detail seam.
