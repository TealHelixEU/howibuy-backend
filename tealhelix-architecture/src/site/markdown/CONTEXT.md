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
One of the five fixed facets of food sustainability a user is asked about: Ecological, Social,
Economic, Health, Animal welfare. A closed, fixed set. Not to be confused with the four Scored
Sustainability Dimensions that product assessment measures — see that entry for the mapping.
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

## Product Sustainability Assessment

The bounded context that answers, for a product a user has scanned, how sustainable it is and what
better thing they could buy instead. It reads the user's compass results but owns none of them.

**SAFAD Taxonomy**:
The fixed three-level classification of foods the assessment is built on: twenty broad Level 1
categories, subdividing into Level 2, subdividing into Level 3. Every Archetype Product hangs off a
Level 3 category. Level 2 is the level at which Substitutability is expressed.
_Avoid_: food tree, product hierarchy, classification, L1/L2/L3 as standalone nouns

**Archetype Product**:
A representative food, standing for every real product of its kind, carrying the measured indicator
values the assessment scores. A scanned product is assessed by matching it to an archetype; it is
never scored directly. Leaf of the SAFAD Taxonomy.
_Avoid_: reference food, generic product, proxy, template

**Reference Product**:
The Archetype Product the scanned product was matched to — the thing an Alternative is offered
against, and the baseline any improvement is measured from. A role, not a kind: any archetype is the
reference product of the assessment that matched it.
_Avoid_: original, source product, input product

**Scored Sustainability Dimension**:
One of four facets an Archetype Product is measured and scored on: Environment, Animal Welfare,
Social, Health. Deliberately **not** the compass's five Sustainability Dimensions — the compass
additionally elicits Economic, which is scored nowhere because no economic indicator exists in the
product data. Ecological (compass) and Environment (here) name the same facet under two names. The
two sets are distinct types and must never share one enum without an explicit mapping.
_Avoid_: sustainability dimension (unqualified — that is the compass's term), axis, pillar, aspect,
criterion; and never write "the five dimensions" in this context

**Indicator**:
One measured quantity of an Archetype Product contributing to a single Scored Sustainability
Dimension — an environmental impact category, an animal welfare index, a social indicator. Health
has none: it is derived from the product's Nutri-Score.
_Avoid_: metric, factor, variable, impact (bare)

**Single Score**:
One Scored Sustainability Dimension of one Archetype Product reduced to a single number, by
weighting its Indicators and summing. Expressed in the dimension's own units and direction, so
scores of different dimensions are not comparable and a higher one is not necessarily better.
_Avoid_: raw score, aggregate, subtotal, sum

**Normalised Score**:
A Single Score rescaled against the whole corpus of Archetype Products so that the four dimensions
become comparable, and inverted so that higher always means more sustainable. Only ever meaningful
relative to the corpus it was computed over, which is why that corpus is always the complete one.
_Avoid_: scaled score, index, percentage, rating

**Weighting Profile**:
The set of weights that reduces a product's four Normalised Scores to one overall score. Two exist:
the **scientific** profile, WP3's expert weights, identical for everyone; and the **personal**
profile, derived from a user's completed compass Attempt. Every product is scored under both.
_Avoid_: weights (bare), preference vector, user profile, settings

**Overall Score**:
An Archetype Product's four Normalised Scores combined under one Weighting Profile — the single
number products are ranked by. A product has one per profile, so "the overall score" is never said
without naming the profile.
_Avoid_: final score, total, sustainability score

**Substitutability**:
The directional judgement that one Level 2 category's foods may stand in for another's on the
plate, graded by how readily. Established by WP3 and fixed; not derived from the scores, and not a
statement that the substitute is better.
_Avoid_: similarity, compatibility, interchangeability, swappability

**Substitutability Level**:
How far a substitution is allowed to stray — Small, Medium, or Large — by setting how strong the
Substitutability must be to qualify. A larger level admits more distant categories.
_Avoid_: radius, distance, tolerance, strictness, threshold

**Alternative**:
A Retailer Product, from an active Listing in the user's Catalogue, offered in place of the scanned
product. It is chosen from the Archetype Product that is substitutable for the Reference Product, no
worse on the scientific Overall Score, and the best one the Catalogue stocks under one ranking
criterion. Three are produced per assessment: best personal, best scientific and best combined.
When the best archetype is the Reference Product itself, no Retailer Product is offered: the outcome
says "what you have is already a good choice" instead.
_Avoid_: substitute, suggestion, recommendation, swap, replacement

## Retailer Catalogue

The bounded context that receives partner Retailers' product data, keeps it current, and has every
product classified ahead of time, so that assessment looks a scanned product up instead of
interpreting it.

**Retailer**:
A partner company whose products HowiBuy assesses, such as Rimi or Kritis. One Retailer may sell in
several countries, through one Catalogue per country.
_Avoid_: partner, chain, client, shop

**Catalogue**:
One of a Retailer's e-shops, serving one country with its own assortment and prices. Every
assessment happens within exactly one Catalogue. Each Catalogue has a **Primary Language**: every
product it lists is named in it, and names fall back to it.
_Avoid_: e-shop, store, market, country (as the name of this concept)

**Retailer Product**:
A physical product as the Retailer identifies it, by the Retailer's own product id. The same id in
two Catalogues is the same Retailer Product. Different ids are different Retailer Products, even
for physically identical goods. It has a name per language, and characteristics and tags that are
the same in every language.
_Avoid_: SKU, item, article

**Listing**:
A Retailer Product on sale in one Catalogue, at that Catalogue's price. A product sold in only one
country has one Listing.
_Avoid_: offer, availability, stock entry

**Inactive**:
A Listing that is missing from its Catalogue's latest Snapshot Feed or was named in a Deletion Feed.
It is never offered as an Alternative, but it is still assessed when scanned, and it keeps its
Classification so that it can come back without being classified again.
_Avoid_: deleted, discontinued, out of stock

**Last Seen**:
When a Listing last appeared in a Feed. An Inactive Listing not seen for long enough is removed for
good.
_Avoid_: last updated, last modified

**Feed**:
The product data a Retailer sends for one Catalogue. A **Snapshot** Feed is the complete current
assortment, so a Listing missing from it becomes Inactive. A **Delta** Feed carries only what
changed, so a missing Listing means nothing. A **Deletion** Feed explicitly names Listings to make
Inactive.
_Avoid_: import, upload, export, file, dump

**Ingestion Run**:
The processing of one Feed, from its receipt to the Retailer Products and Listings it changes,
with its outcome and counts.
_Avoid_: import job, batch, sync, load

**Product Change**:
The complete current state of a Retailer Product and its Listings, or the fact that it was
removed, as read in order from Ingestion's change cursor. A product changed several times between
two reads appears once. Unchanged products produce none.
_Avoid_: event, message, delta, update

**Catalogue Sync**:
The operator command that carries Product Changes from Ingestion into HowiBuy. It never
classifies.
_Avoid_: import, replication, classification run

**Classification**:
The match of a Retailer Product to an Archetype Product, made ahead of time by any Classifier. A
Retailer Product has at most one Classification, whatever languages it is named in. It may conclude
that no archetype matches.
_Avoid_: recognition, identification, categorisation, assessment (that is the request-time step)

**Classification Attempt**:
One classifier's match of a Retailer Product, made from the product's name in one language. The
product's Classification is resolved from its attempts, and attempts that disagree are flagged
for review and resolved by the Retailer's classification language. Each attempt records its
classifier and classifier version; there is no other record of a classification run.
_Avoid_: try, guess, run

**Classifier**:
Who made a Classification Attempt, one of three, in order of precedence. **Manual**: one of us,
such as an operator correcting a Classification or settling one flagged for review. **Expert**: a
domain source we trust but do not run, such as WP3. **LLM**: the language model HowiBuy runs. A
higher one's attempts outweigh a lower one's.
_Avoid_: source, WP3 (for the classifier), person (an expert is one too)
