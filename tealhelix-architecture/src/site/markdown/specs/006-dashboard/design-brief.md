# Dashboard — Design Brief

Status: input to the visual design. The spec of the dashboard (`spec.md` in this directory: the read endpoint and the
howibuy-front screens) is written once the design is settled.

Domain vocabulary follows the project glossary (`tealhelix-architecture/src/site/markdown/CONTEXT.md`, sections
"Product Sustainability Assessment" and "Basket Assessment"). The rules behind every number are those of
`../005-basket-gamification/spec.md`.

## How the Design Is Made

1. A **Design System** artifact on claude.ai, built from howibuy-front's code: the tokens of `src/index.css` (the
   `@theme` brand colours and the daisyUI theme `tealhelix`), the type, the chrome (top bar, title band, footer strips)
   and the components already there.
2. A **Design** canvas of the dashboard on that system, at desktop and phone width.
3. Comments on the canvas; the design is revised until settled.
4. The settled design becomes `spec.md` here: the contract of the read endpoint and the issues of both repositories.

The design is made from a Claude Code session in `howibuy-front`, whose `CLAUDE.md` carries the visual rules. This
file carries what that session cannot know from the front's code.

## Who and Where

- **Who:** a retailer's customer, signed in to HowiBuy with their own login or through a session their retailer handed
  over (`handoff/`). Retailers' customers reach it from the retailer's site, so the phone matters as much as the
  desktop.
- **Where:** the route `/dashboard` of howibuy-front, in the top bar's navigation. It is the page a user lands on once
  signed in, by their own login or a retailer's handover. `src/dashboard/DashboardPage.tsx` is a placeholder that
  renders nothing; the title band above it already says "Dashboard".
- **When:** D5.3 ("AI tools", public, due at the end of December 2026) describes the dashboard, so it must be working by
  then.
- **Languages:** the pilots are in Greece, Lithuania, Latvia and Estonia. The front has English only today; the badge
  labels arrive translated from the API. Layouts must survive longer words than English's, and Greek script.

## What the Dashboard Shows

Every item below has a source; the ones that need the read endpoint, which does not exist yet, say so. Numbers are on
the 0–1 scale of the Normalised and Overall Scores unless stated.

### XP Total

The user's running total of XP (stored, `TH_USER_PROFILE.xp_total`). A Purchased Basket earns 10 XP for every product
whose scientific Overall Score is above 0.8, plus a bonus when it has at least 5 scored products: 150 if their average
scientific Overall Score is above 0.8, otherwise 50 if above 0.6.

Source: WP5.3's spreadsheet "TH WP5.3 GAMIFICATION FORMULAS.xlsx", built in spec 005. Needs the read endpoint.

### XP Percentile

Where the user's XP total stands among the users' (e.g. "ahead of 70% of shoppers"): D5.3's "benchmarking against
other consumers".

Only users with at least one Purchased Basket count. A profile is created at a user's first login,
so users with 0 XP and no basket are many, and counting them would flatter everyone else's percentile.

Caveat: XP accumulates with every purchase, so the percentile measures how much and how long someone has shopped as
much as how sustainably. Needs the read endpoint.

### Streak

The number of consecutive Purchased Baskets, in the order they were registered, each with at least one product whose
scientific Overall Score is above 0.8; a basket without one resets it to 0 (stored, `TH_USER_PROFILE.streak`).

Source: the spreadsheet, built in spec 005. Needs the read endpoint.

### Badges, with the Progress to the Next One

Twelve badges, four series of three levels. A badge is earned for buying 50, 100 and 200 products (levels 1, 2, 3)
whose Normalised Score of one dimension is above 0.8; every product of every Purchased Basket counts once per basket.
Earned badges are stored, with the time they were earned and the basket that earned them, and are kept even if the
numbers are raised later.

| Series         | Codes                         | English labels                                |
|----------------|-------------------------------|-----------------------------------------------|
| Environment    | `ENVIRONMENTAL_HERO_1`..`_3`  | Environmental Hero Level 1..3                 |
| Animal Welfare | `ANIMAL_WELFARE_HERO_1`..`_3` | Animal Welfare Hero Level 1..3 (placeholder)  |
| Socio-economic | `SOCIO_ECONOMIC_HERO_1`..`_3` | Socio-economic Hero Level 1..3 (placeholder)  |
| Health         | `HEALTH_HERO_1`..`_3`         | Health Hero Level 1..3 (placeholder)          |

The progress to the next badge of a series is the user's count of such products against that badge's number (e.g.
163 of 200). The count per dimension is already computed for the purchase; the listing needs the read endpoint.

There are no badge images: the API gives a code and a translated label, and each client draws its own icons, keyed by
the code. HowiBuy's icons are therefore part of this design. The names of nine badges are placeholders awaiting WP5.3,
so a design should not depend on their wording.

### Monthly Progress per Dimension

For each of the four Scored Sustainability Dimensions (Environment, Animal Welfare, Socio-economic, Health), the
average Normalised Score of the products the user bought in the current calendar month, against the previous month, as
a change in percent: `100 × (current − previous) / previous`. With no purchases in the previous month there is no
change to show.

A basket counts in the month of its `purchasedAt`, the time the retailer states for the purchase, not of its
`recordedAt`, the time HowiBuy registered it. A basket registered late therefore changes a past month's figure.

Every purchased product stores its four Normalised Scores and both Overall Scores as they were at the purchase, so the
averages need no recomputation. Source: the spreadsheet. Needs the read endpoint.

### Invitation to the Compass

A user who has not completed the Sustainable Food Compass is matched against the scientific Weighting Profile, so their
Basket Match says nothing yet about their own priorities. The dashboard invites them to take the compass, or to finish
the attempt they have started.

The compass overview the front already loads (`GET /sfc/overview`, in `sfc/sfc.ts`) gives the attempt's status and
progress. Whether it tells a new attempt in progress apart from one started over an earlier completed attempt is checked
in the spec.

## Not on the Dashboard

- The outcome of a single basket (its match, XP, problematic products, streak effect, badges): the retailer shows it at
  the checkout, from the basket endpoints.
- Leaderboards, a sliding weekly or monthly average, the intervention message: out of scope since spec 005.

## Open Decisions

The design may show variants for these:

1. **Display scale.** The spreadsheet asks itself whether scores are shown as 0–1, as a percentage or as 0–20.
2. **The month's time zone.** The containers set none (most likely UTC). CET was suggested or the server's default;
   Claude's position is EET, the zone of every pilot country. It matters only for purchases near midnight at the turn
   of a month.
3. **A month without purchases.** No change in percent can be computed; what the dashboard shows instead is open, and
   needs discussion.
4. **The percentile's users:** those of all retailers, or those of the retailers the user shops at. A user's
   baskets may come from several retailers, so "the user's retailer" is not one by itself. Nor is a retailer a
   country: Rimi alone covers Lithuania, Latvia and Estonia.
5. **Whether the percentile stays at all**, given the caveat above.
6. **The empty dashboard** of a user without a Purchased Basket: what it shows and what it invites them to do. As the
   landing page, it is the first thing a new user sees.
7. **Today's home page** (a welcome, the signed-in email, the API server): dropped now that the dashboard is the landing page.

## Constraints from howibuy-front

The visual rules are in howibuy-front's `CLAUDE.md` ("Visual design"): the brand colours read from tealhelix.eu, Roboto
(Barlow lacks Greek), the 66px top bar, the title band, the column of 80% up to 1200px. Specific to the dashboard:

- The title band already renders the page's title; the page has no `<h1>` of its own.
- Components to reuse where they fit: `components/ProgressBar` (daisyUI `progress`), daisyUI `card`, and the compass's
  `sfc/components/DimensionIcon`.
- **The dimensions differ from the compass's.** The compass asks about five Sustainability Dimensions (Ecological,
  Social, Economic, Health, Animal Welfare); scoring, the badges and the monthly progress use four Scored Sustainability
  Dimensions, Socio-economic covering Social and Economic together. `DimensionIcon` has a mark for each of the compass's
  five (leaf, people, coins, heart, cow), none for Socio-economic. Environment takes the leaf; Socio-economic needs a
  mark of its own, made in this design. The four follow the compass's order: Environment, Socio-economic, Health,
  Animal Welfare.

## Example Data for Mock-ups

Rough figures for a plausible user: weekly baskets of 30 scored products, with the shares of products above 0.8
measured on the archetype corpus under v15 (unweighted by what people buy, so only indicative). After 8 weeks:

- **XP:** about 180 per basket (13 products above 0.8 make 130, plus the 50 bonus), about 1,440 in total.
- **Streak:** 8. Almost every basket has a product above 0.8.
- **Badges** (products above 0.8 per dimension):
  - Environment: 193, so Levels 1 and 2 are held, 7 to go to Level 3.
  - Socio-economic: 182, Levels 1 and 2, 18 to go.
  - Animal Welfare: 163, Levels 1 and 2, 37 to go.
  - Health: 57, Level 1, 43 to go to Level 2.
- **Monthly progress:** small changes of a few percent either way, e.g. Environment 0.84 → 0.87 (+3.6%). These values
  are illustrative.

## Sources

- `../005-basket-gamification/spec.md`: the rules, the stored data, the labels.
- WP5.3's "TH WP5.3 GAMIFICATION FORMULAS.xlsx" (outside the repository): XP, streak, badges, monthly progress, the
  display-scale question.
- D5.3's HowiBuy part (outside the repository): the XP percentile; the dashboard due by the end of December 2026.
- howibuy-front's `CLAUDE.md` and code: the visual rules, the chrome, the components.
