# TealHelix — Domain glossary

The ubiquitous language for the TealHelix project, grouped by bounded context. This file is a
glossary and nothing else — no implementation details.

## Sustainable Food Compass (SFC)

The bounded context for a multilingual questionnaire that captures a user's attitudes across the
facets of food sustainability. Its per-user results feed HowiBuy product assessment.

**Sustainable Food Compass (SFC)**:
The questionnaire as a whole — the fixed set of categories and questions a user works through.
_Avoid_: survey, quiz, poll

**Sustainability Dimension**:
One of the five fixed facets of food sustainability: Ecological, Social, Economic, Health, Animal
welfare. A closed, fixed set.
_Avoid_: axis, pillar, aspect

**Category**:
A themed grouping of questions that a user works through together. Each category addresses exactly
one sustainability dimension (today one category per dimension, though a dimension may hold several)
and carries its own localized name, rich description, and links (a video link and a link to a
detailed description). Questions belong to a category.
_Avoid_: section, topic, group

**Question**:
A single text prompt the user answers by picking one option from the fixed scale shared by every
question. Belongs to one category and has a fixed position within that category. Its text is
localized.
_Avoid_: item, prompt

**Scale Option**:
One of the five fixed, ordered points (1–5) on the Likert scale shared by every question, carrying a
localized label. The same five options apply to all questions.
_Avoid_: choice, rating

**Attempt**:
One round of the compass by one user — the container for that user's answers to every question. A
user works through at most one in-progress attempt at a time (pause and resume freely); completing
it validates that every question is answered, then freezes it as an immutable historical record and
begins a stability window before a new, blank attempt may be started.
_Avoid_: submission, session, response, try

**Answer**:
A user's selected scale option for a specific question within an attempt. Its presence marks the
question answered in that attempt; its absence marks it unanswered. Language-independent (an ordinal
1–5).
_Avoid_: reply

**Frontier**:
The question a user is guided to next within a category — the first one, in position order, they have
not answered. Being defined by the answers rather than by where the user has been, it stays at the
earliest unanswered question even after an earlier answer is revised, and a fully answered category
has none. Its mirror, the question a user steps back to, is the last one they have answered.
_Avoid_: current question, cursor, position

**Stability Window**:
The configured period after an attempt is completed during which no new attempt may begin, so the
completed answers stay stable for the external scientific model that consumes them.
_Avoid_: lock period, cooldown
