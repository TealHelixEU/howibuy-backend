# SFC navigation is category-scoped with frontier-based "next question"

Categories are independent and unordered — a user works through them in any order — and only the
questions *within* a category are ordered (by position). The "next question" signal is category-
scoped and returns the **first unanswered question** in that category's order; when none remain it
returns a "category complete" signal and does **not** advance into another category. Revisiting is a
separate, unrestricted read (fetch a category's questions with their current answers, in any order).

## Considered Options

- **Frontier-based next (chosen).** Returning the first unanswered question makes the requirement
  "next requires all previous answered" hold by construction — there is no way to skip ahead, and no
  separate gate check or current-position input is needed.
- **Positional next (question after position P).** Rejected as the primary model: needs an explicit
  gate check and a current-position parameter for no added value on the forward path.
- **Auto-advance into the next category at end of category.** Rejected — the team is explicitly
  instructed not to advance between categories, and since categories are unordered "the next
  category" is not even well-defined.

## Consequences

- If a user revisits and changes an already-answered question, "next" still routes to the earliest
  remaining unanswered question, not to position+1. This is a deliberate default (forward progress ≠
  browsing) and is cheap to change if the product owners later want strict positional next.
- Whole-compass completeness is enforced only by the explicit completion action, independent of
  navigation order.
