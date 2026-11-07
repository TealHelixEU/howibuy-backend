# Dashboard

Status: ready-for-agent

Domain vocabulary follows the project glossary (`tealhelix-architecture/src/site/markdown/CONTEXT.md`, sections
"Sustainable Food Compass", "Product Sustainability Assessment" and "Basket Assessment"). The rules behind every number
are those of `../005-basket-gamification/spec.md`.

The design was made from `design-brief.md` in this directory and settled in review. This spec carries what the
implementation needs from it: the behaviour, the wording and the measurements.

## Problem Statement

A retailer's customer earns XP, a Streak and Badges with every Purchased Basket, but sees them only at the retailer's
checkout, one basket at a time. Nothing shows them where they stand: their total, how it compares with other shoppers',
how their shopping moves from month to month, how far they are from the next badge. D5.3 describes such a dashboard,
due at the end of December 2026. In howibuy-front, `/dashboard` is an empty placeholder, and the page a user lands on
is a developer's welcome page.

## Solution

The dashboard becomes the page a user lands on. Top to bottom it shows:

1. an invitation to the compass, until the user has completed an attempt, with the progress of the attempt in progress;
2. three tiles: the XP total with the percentile, the Streak, and the Badges held with the closest next one;
3. "This month": per Scored Sustainability Dimension, the average Normalised Score of the products bought this month
   against last month's, and the change;
4. the Badges: per series, the three levels, which of them are earned, and the progress to the next.

A user without a Purchased Basket sees the same page at zero, with the rule that earns each counter.

The API gains one read, `GET /dashboard`. The compass overview tells a retake in progress apart from a first attempt.
The purchase registration keeps the offset the retailer sends, so a purchase counts in the month of its date at the
shop. These changes go into API version 0.4.0, which is not released yet. The front's home page is dropped.

## User Stories

1. As a retailer's customer, I want to see my XP total, my Streak and my Badges in one place, without buying anything.
2. As a retailer's customer, I want to know how my XP compares with other shoppers'.
3. As a retailer's customer, I want to see whether the products I buy this month score better than last month's, per
   dimension.
4. As a retailer's customer, I want to see how many more products I need for the next badge of each series.
5. As a retailer's customer, I want to be told in plain words, when I ask, how XP and the Streak are earned.
6. As a retailer's customer who has not completed the compass, I want to be invited to, and to see how far I have got,
   so that my baskets are matched to my own priorities.
7. As a retailer's customer who has bought nothing yet, I want the dashboard to tell me what my first purchase starts.
8. As a retailer's customer, I want a purchase made late on the last evening of a month to count in that month, as it
   did at the shop.
9. As a retailer, I want to state the time of a purchase as my shop's clock read it, and to be refused when I leave it
   out.
10. As a researcher, I want the time of every purchase stored as the shop's local time with its offset, so that neither
    the moment nor the local date is lost.

## Implementation Decisions

### The Design, Settled

The open decisions of `design-brief.md`, as settled in review:

1. **Display scale:** 0–1 with two decimals. The monthly change is relative, in percent: 0.84 → 0.87 is +3.6%.
2. **The month:** a purchase counts in the month of its date at the shop, read from the offset the retailer sends with
   `purchasedAt`, which becomes required. "This month" is the user's month, in their browser's time zone.
3. **A month without purchases:** nothing bought yet this month keeps last month's averages on screen; nothing bought
   last month shows no change. See "Front: The Screen".
4. **The percentile's users:** every user with at least one Purchased Basket, at any Retailer.
5. **The percentile stays,** under the XP total, shown only from 20% up.
6. **The empty dashboard:** the compass invitation first, then the three counters at zero, each with the rule that
   earns it; every badge shows what it takes.
7. **Home dropped.** The navigation reads Dashboard · SFC.

And:

- **The compass invitation** shows until an attempt is completed, also while an attempt retakes an earlier completed
  one, with the progress of the attempt in progress.
- **The Socio-economic mark** is Font Awesome's handshake: fair dealing between people, covering the social and the
  economic half.
- **How XP and the Streak are earned** (the explanation) sits behind an (i) on their tiles, in words without thresholds
  ("scores high for sustainability", not "above 0.8"). The open popup dims the rest of the page.
- **Badge icons** are HowiBuy's own medallions, one per badge code.
- **One read:** everything the dashboard shows, the rule numbers included, comes from `GET /dashboard`.

### API (version 0.4.0)

`JaxRsAppHowiBuyV1` stays at version 0.4.0. That version is not released yet, so its contract can still change, even in
a breaking way: `purchasedAt` becomes required (see "The Purchase Time").

```
GET /api/howibuy/v1/dashboard?month=2026-11&lang=el
  Token: the end-user's: their own login, or a session a Retailer handed over
  → Dashboard
```

- `month`: required, `YYYY-MM`, the month the front calls this month, in the user's time zone. The previous month is
  the one before it. Missing or malformed is `400`.
- `lang`: the language of the badge labels, an ISO 639-1 code. Left out or blank, English; a language without a
  translation falls back to English, as in the basket endpoints.
- `403` when a service account presents the token: the dashboard belongs to an end-user
  (`TealHelixAuthorization.requirePerson`, as the compass does).
- `DashboardResource` in `howibuy-jaxrs`, package `eu.tealhelix.howibuy.jaxrs.v1.dashboard`, under a tag of its own.

The front sends its month rather than its time zone: the front knows its own month, the server then needs neither a
clock nor a zone for this read, and testing a month's turn needs no fake clock.

`Dashboard` and its parts are new Immutables interfaces in `howibuy-model`, package `v1.model`:

```json
{
  "xpTotal": 1440,
  "xpPercentile": 70,
  "xpPerProduct": 10,
  "streak": 8,
  "purchasedBasketCount": 8,
  "monthlyProgress": [
    { "dimension": "ENVIRONMENT", "currentAverage": 0.87, "previousAverage": 0.84, "changePercent": 3.6 }
  ],
  "badgeSeries": [
    {
      "dimension": "ENVIRONMENT",
      "productCount": 193,
      "levels": [
        { "badge": "ENVIRONMENTAL_HERO_1", "label": "Environmental Hero Level 1", "requiredProducts": 50, "earned": true },
        { "badge": "ENVIRONMENTAL_HERO_2", "label": "Environmental Hero Level 2", "requiredProducts": 100, "earned": true },
        { "badge": "ENVIRONMENTAL_HERO_3", "label": "Environmental Hero Level 3", "requiredProducts": 200, "earned": false }
      ]
    }
  ]
}
```

(One dimension shown of four in each list.)

- `Dashboard`:
  - `getXpTotal()`, `getStreak()`: as stored on the profile, as the basket outcome reports them.
  - `getXpPercentile()`: `@Nullable Integer`. Of the other users with at least one Purchased Basket, the percentage
    whose XP total is below the user's, rounded down. A tie is not behind. `null` when the user has no Purchased
    Basket, or no other user has one. Sent whatever its value: hiding a low one is the front's choice.
  - `getXpPerProduct()`: the XP of every product that is not a Problematic Product, the number the explanations of XP
    quote. It comes from `BasketRules`, which makes it public as `xpPerProduct()`, as `BadgeRules` does its threshold.
  - `getPurchasedBasketCount()`: the user's Purchased Baskets, those with nothing to assess included. 0 is the empty
    dashboard.
  - `getMonthlyProgress()`: a `DimensionProgress` per `ScoredSustainabilityDimension`, in the enum's order.
  - `getBadgeSeries()`: a `BadgeSeries` per `ScoredSustainabilityDimension`, in the enum's order.
- `DimensionProgress`: `getDimension()`; `getCurrentAverage()`, `getPreviousAverage()`, `getChangePercent()`, each a
  `@Nullable Double` (see "Monthly Progress").
- `BadgeSeries`: `getDimension()`; `getProductCount()`, the user's purchased products above the threshold in the
  dimension, counted as the badges count them; `getLevels()`, a `BadgeLevel` per badge of the dimension, in the order
  of `Badge`.
- `BadgeLevel`: `getBadge()`; `getLabel()`, translated; `getRequiredProducts()`; `isEarned()` (JSON `earned`), whether
  the user holds it. A held badge stays earned when its number has since been raised above the count; a badge whose
  lowered number the count already reaches is not earned until the next basket awards it.

The front shows the dimensions in the compass's order, not the enum's.

### Monthly Progress

- A month's average, per dimension, is the mean Normalised Score of the stored products of the user's Purchased
  Baskets whose `purchased_at`, the shop's local date-time, falls in that month. Every product counts once per basket,
  as for the badges. Only scored products are stored, so a basket with nothing to assess adds nothing.
- Every stored product has all four Normalised Scores, so a month has all four averages or none: `null` when the user
  bought no scored product in it.
- The averages are rounded to two decimals (half up), the display scale, and the change is computed from the rounded
  averages: `100 × (current − previous) / previous`, rounded to one decimal (half up). So what the user reads adds
  up: 0.84 → 0.87 is +3.6%, and two equal averages are 0.0%, never +0.6%. `null` when either average is `null`, or
  the previous one is 0.00.
- The month a purchase counts in is the shop's; the month the user calls "this month" is the user's. They differ only
  for a user away from their shops' time zone, around midnight at the turn of a month.

### Badges

- `BadgeRules` makes public what awards a badge: its `Requirement` (dimension, number of products) and
  `requirementOf(Badge)`. The dashboard reads the numbers from there, never from a copy.
- `productCount` comes from `PurchasedBasketDao.retrieveProductCountsAbove(em, user,
  badgeRules.normalisedScoreThreshold())`, `earned` from `findBadges`. The labels come from the `BadgeLabels` bundle,
  through one `Labels<Badge>` shared with `BasketAssessmentServiceImpl` rather than declared a second time.

### The Purchase Time

Decision 2 changes the purchase registration of `../004-basket-assessment/spec.md`:

- `PurchasedBasketAssessmentRequest.purchasedAt` is required: `400` when it is missing, and the purchase's `400`
  description names it. Its description asks for the date and time at the shop with the shop's offset from UTC (e.g.
  `2026-11-06T18:30:00+02:00`), and says that the offset decides the month the purchase counts in. The other `400`s
  stay: a time without an offset, a bare date, anything unreadable, a time in the future.
- The offset survives the parsing: the record component carries
  `@JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)`. Without it, Jackson converts every
  `OffsetDateTime` it reads to UTC (checked against the backend's Jackson 2.21.7).
- `BasketAssessmentService.assessPurchasedBasket` takes a non-null `OffsetDateTime`. The future check compares instants:
  the purchase time against the time of recording plus `MAX_CLOCK_SKEW`.
- `PurchasedBasket.getPurchasedAt()` becomes an `OffsetDateTime`, the time as the retailer sent it.
- It is stored in two columns: `purchased_at` holds the shop's local date-time as sent (`toLocalDateTime()`, no longer
  converted to the server's zone), and the new `purchased_at_offset` its offset in seconds
  (`ZoneOffset.getTotalSeconds()`). The month is read from the local date-time as it stands. `recorded_at` stays the
  server's time.
- Offsets are not checked for plausibility: any offset Java accepts (±18:00) is stored as sent. Only the retailer knows
  where its shops are.

The pre-purchase assessment takes no time and is unchanged.

### The Compass Overview

The overview cannot tell a retake in progress from a first attempt: both are `IN_PROGRESS` with `eligibleAt` `null`.
A user retaking the compass is matched against their latest completed attempt (`PersonalWeightsProvider`, through
`findLatestCompletedAnswers`); a user on their first attempt is matched against the scientific profile. The
invitation's words differ accordingly.

- `CompassOverview` and `CompassOverviewDto` gain `hasCompletedAttempt`: whether the user has ever completed an attempt,
  true also while a later one is in progress.
- `CompassReadServiceImpl.attemptState` reads the latest completion in the in-progress branch too.
- The `@Schema` description of `eligibleAt` is corrected: it is `null` also while an attempt is in progress, not only
  until one is completed. The service's Javadoc already says so.

### Services

- `DashboardService` (`howibuy-service-interfaces`): `Uni<Dashboard> retrieveDashboard(User user, YearMonth month,
  String language)`. `DashboardServiceImpl` (`howibuy-services`) requires a person and makes its reads one after the
  other in one non-transactional persistence context. A purchase registered between two of the reads shows fully on
  the next load; the dashboard does not need a snapshot.
- The percentile, the rounding, the change and the badge levels are computed in the service. The front only formats.

### Persistence

New changelog `changelogs/v1.0.0/20261107_dashboard.xml` in `howibuy-dao-hibernate-reactive`, included by
`howibuy.db.changelog.xml`.

- `TH_PURCHASED_BASKET.purchased_at`: its remarks become "When the user bought the basket: the date and time at the
  shop, in the shop's local time, as the Retailer states it; its offset from UTC is purchased_at_offset".
- `TH_PURCHASED_BASKET.purchased_at_offset INT NOT NULL`: "The offset from UTC of purchased_at, in seconds, as the
  Retailer states it". It is added with the default 0, which is then dropped. The baskets stored so far are our own
  test calls, timed in the server's zone; the containers set none, so it is most likely UTC, and 0 is right for them.
- No new index. The averages and the count filter by user, through `IX_TH_PURCHASED_BASKET__USER_PROFILE_ID`; the
  standing reads every profile that has a basket, through the same index, which is fine at the pilots' scale.

New reads:

- `PurchasedBasketDao.retrieveAverageNormalisedScores(em, user, YearMonth month)`:
  `Optional<Map<ScoredSustainabilityDimension, Double>>`, empty when the user bought no scored product in the month.
  The range is `purchased_at` from the first day of the month at 00:00, inclusive, to the first day of the next month
  at 00:00, exclusive.
- `PurchasedBasketDao.countBaskets(em, user)`.
- `UserProfileDao.retrieveXpStanding(em, user)`: `XpStanding(long otherShoppers, long behind)`, a carrier in
  `howibuy-services-model` like `XpAndStreak`: how many other users have at least one Purchased Basket, and how many of
  them have a lower XP total than the user.

### Front: Routes and Navigation

- `/` redirects to `/dashboard` (`<Navigate replace>`). A login returns to `/`, and a retailer's handover link normally
  points there, so both land on the dashboard.
- `HomePage`, its test, the `home.*` and `nav.home` keys, and `apiServerHostAtom`, which only `HomePage` reads, are
  removed.
- `navItems`: Dashboard, then SFC. The logo links to `/dashboard`. The 404 page's way back reads "Back to the dashboard"
  and leads there.

### Front: Data and Loading

- `src/dashboard/dashboard.ts`: the types of the read, `fetchDashboard(month)` calling
  `/dashboard?month=…&lang=${currentLanguage()}`, and `currentMonth(now)`, the `YYYY-MM` of the browser's local date.
- `DashboardPage` loads the dashboard and the compass overview (`fetchOverview`) as one load: a centred `loading-lg`
  spinner while either is in flight, one failure with "Try again" if either fails.
- `sfc/useLoaded.ts` and `sfc/components/LoadFailure.tsx` now serve two features, so they leave `sfc/`:
  `LoadFailure` moves to `src/components/` and takes its message as a prop ("The dashboard could not be loaded just
  now"); `useLoaded` moves to `src/api/`. The "Data layer" section of `CLAUDE.md` follows them.

### Front: The Screen

The components live under `src/dashboard/components/`. The (i) popup imports nothing from a feature, so it goes to
`src/components/`. The screen follows the front's visual rules (`CLAUDE.md`, "Visual design"); the measurements below
are those the design fixed, and the theme's defaults apply elsewhere.

**Layout.** Every block is a stack or a wrapping flex row whose parts have a flex basis rather than a width, so at
phone width (390px) they stack without breakpoints of their own. Labels, the badge labels and the dimension names above
all, wrap and are never truncated: Greek ones can run twice as long as English ones. The blocks stack 40px apart: the
compass invitation, the tiles (or the empty dashboard's counters), This month, the badges. This month and the badges
open with a heading row: an `h2`, and at the other end, on the same baseline, a note in `text-sm` at 70% opacity,
which wraps under the heading when there is no room. Their parts are 12px apart.

**Big numbers** use the heading style (weight 500, `font-stretch: 75%`, letter-spacing 1px, line-height 1,
`brand-blue`) at 56px, followed by their unit at 18px, weight 600, `brand-blue`. Every number on the dashboard uses
tabular figures.

**The compass invitation** (`CompassInvitation`, from the overview) has one action, which links to `/sfc`:

| Overview                                   | Title                                                       | Text                                                                                                                             | Under it                                   | Action               |
|--------------------------------------------|-------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------|----------------------|
| no attempt                                 | Match your baskets to what matters to you                   | Until you complete the Sustainable Food Compass, your baskets are matched against an expert weighting, not your own priorities. | about 11 min in total                      | Take the compass     |
| `IN_PROGRESS`, `hasCompletedAttempt` false | Finish the compass to match your baskets to your priorities | Until you complete it, your baskets are matched against an expert weighting, not your own priorities.                           | progress bar, "12 of 43 answered · 28%"    | Continue the compass |
| `IN_PROGRESS`, `hasCompletedAttempt` true  | Finish the compass to update your priorities                | Until you complete it, your baskets are matched against the priorities you gave last time.                                      | progress bar, "12 of 43 answered · 28%"    | Continue the compass |
| `COMPLETED`                                | no invitation                                               |                                                                                                                                  |                                            |                      |

The estimate and the progress are worded as the compass words them: `estimatedMinutes`, `sfc.estimate.total` and
`sfc.progress.overall`.

The invitation is a box with a 1px `primary` border, `radius-box` corners and padding of 20px × 24px. Its parts sit in
a wrapping row with gaps of 16px × 24px:

- the compass mark, `FaRegCompass` (outlined: the app's one regular-style icon), at 40px in `primary`;
- the title, an `h2` at 22px with line-height 1.1, and its text, with a basis of 320px. In progress, the progress bar
  (`ProgressBar`, at most 420px wide) and its count in `text-sm` follow the text;
- the action. With no attempt, the estimate sits under the button, in `text-sm` at 70%.

**The tiles**, shown when `purchasedBasketCount` is above 0, sit in a wrapping row 16px apart. The XP tile has a basis
of 300px and twice the others' grow; the Streak and Badges tiles have a basis of 220px. Each is a `card` with a 1px
`base-300` border and 12px between its lines; its title is an `h2` at 18px.

- **XP:** "1,440 XP", then "Ahead of 70% of shoppers" at weight 600, over a bar 8px tall with rounded ends: a
  `base-300` track and a `primary` fill to that percentage. The bar is `aria-hidden`, because the text says the same.
  The (i), "How XP works", explains: "You earn 10 XP for each product you buy that scores high for sustainability, and
  a bonus when a whole basket scores well." and "The shoppers you are compared with are everyone who has bought at
  least once, at any shop that uses HowiBuy."
  - **A low percentile is hidden.** The line and its bar show only when `xpPercentile` is at least
    `PERCENTILE_SHOWN_FROM`, a named constant in `src/dashboard/`, 20 for a start. Below it, or `null`, the tile
    shows the total alone, and the popup leaves out its second sentence, since nothing on the tile compares the user
    with anyone. A user behind most shoppers is not told so; the line appears once they pass the cutoff.
- **Streak:** "8 baskets in a row". The (i), "How the streak works", explains: "A basket keeps your streak going when at
  least one of its products scores high for sustainability. A basket without one starts the count again."
- **Badges:** "7 of 12", then, in `text-sm`, "Closest: Environmental Hero Level 3, 7 products to go", the label at
  weight 600. The closest badge is the next badge with the fewest products to go, across all series; on a tie, the
  first in display order wins. The line goes once every badge is earned.

**The (i) popup** (`Explanation`) opens from a round 44px button in the tile's title row. The button is labelled by
what it explains and carries `aria-expanded` and `aria-controls`. Its icon, `FaCircleInfo`, is 20px in `primary`.
Negative margins pull the button into the card's corner, so the title row keeps its height. While the popup is open,
the button's background is `base-200`.

- **The box:** over the card, 60px from its top and 12px from its right, `min(340px, 100% − 24px)` wide. Padding 16px,
  8px between paragraphs, a 1px `base-300` border, `radius-box` corners, `base-100`, 14px text at line-height 1.5.
- **The dimming:** while the popup is open, the rest of the page dims. A layer covers the whole viewport, above the page
  and under the box, in the backdrop colour of daisyUI's modal, `oklch(0% 0 0 / 0.4)`, as in `ConfirmDialog`.
- Esc, a click on the dimmed page, or the button closes it. Only one is open at a time.

**The empty dashboard** (`purchasedBasketCount` 0) shows, instead of the tiles:

- "Your first purchase starts the count" (an `h2`), and 8px under it "Every purchase your shop registers with HowiBuy
  adds to this page.";
- 16px below, the three counters at zero, as cards laid out like the tiles. Their titles are `h3`s at 18px. Their
  numbers and units have the big-number sizes, in `base-content` at 60% opacity.

Each counter shows its rule:

- XP: "Earn 10 XP for each product that scores high for sustainability, and a bonus when a whole basket scores well."
- Streak: "Buy a product that scores high for sustainability in every basket to keep a streak going."
- Badges: "Earn one for 50, 100 and 200 products that score high in one dimension.", the numbers coming from the
  levels' `requiredProducts`.

**This month** (`MonthlyProgress`) is laid out top to bottom:

1. The heading row: "This month", with the note "November so far, against October".
2. "The average score, from 0 to 1, of the products you bought, per dimension."
3. A note at weight 600, when the table below gives one.
4. A card (1px `base-300` border, padding of 4px × 24px) holding one row per dimension, in the compass's order
   (Environment, Socio-economic, Health, Animal welfare), with a 1px `base-300` line between rows.
5. Under the card, in `text-sm` at 70%: "Bar: November so far. Tick: October. A purchase counts in the month of its
   date."

Each row wraps, with 16px of padding above and below and gaps of 10px × 24px. It holds three parts:

- **The name:** the dimension's mark in `primary`, 10px before its name, at 18px and weight 500. Basis 200px.
- **The bar:** basis 240px and three times the grow. It is 10px tall with rounded ends: a `base-300` track, a `primary`
  fill to the current average (0.87 fills 87%), and a tick at the previous average that is 3px wide, 20px tall,
  centred on the bar and `base-content`. The bar is an `img` labelled, for example, "Environment: November so far
  0.87, October 0.84".
- **The figures:** a fixed 250px, wrapping, on one baseline, with gaps of 6px × 12px:
  - the current average, at 30px in the heading style;
  - "October 0.84", in `text-sm` at 70%;
  - the change, in a pill: fully rounded, padding of 2px × 10px, 14px, weight 600, `base-content`, with a 12px arrow
    4px before the figure. Up is on `success` (`FaArrowUp`), down on `warning` (`FaArrowDown`), 0.0% on `base-200`
    (`FaMinus`).

| This month      | Last month | Above the rows                                                                                     | In the rows                                                     |
|-----------------|------------|----------------------------------------------------------------------------------------------------|-----------------------------------------------------------------|
| bought          | bought     | nothing                                                                                            | as above                                                        |
| nothing         | bought     | Nothing bought yet in November. October’s averages stay here until your first purchase this month. | an empty bar with the tick, "–", "October 0.84", no change      |
| bought          | nothing    | You bought nothing in October, so there is no change to show yet.                                  | the bar and the average, "No purchases in October", no change   |
| nothing         | nothing    | Nothing bought in October, nor yet in November.                                                    | an empty bar, "–", "No purchases in October", no change         |
| empty dashboard |            | nothing                                                                                            | one line at 70%, 16px above and below, instead of the rows: "Each month's averages show here from your first purchase." |

The fourth case is a user who has bought before, but in neither month.

**The badges** (`BadgeSeriesCard`, `BadgeIcon`) open with the heading row "Badges", whose note is "7 of 12 earned".
Under it: "Each badge counts the products you buy that score high in its dimension, every time you buy them." There is
one card per series, in the compass's order, in a wrapping row 16px apart, each with a basis of 400px, which puts two
in a row on desktop. A card's parts are 16px apart:

- the title, an `h3` at 22px with line-height 1.1: the series' mark at 20px in `primary`, 8px before the name, and at
  the other end "2 of 3 earned", in `text-sm` at 70%;
- the three levels, in three equal columns 16px apart. Each is centred, with 4px between its lines at line-height 1.3:
  the medallion at 56px, "Level 2" at weight 600, and "Earned" or "100 products" in `text-sm` at 70%;
- the next level, with 8px between its lines: "Next: Environmental Hero Level 3", the label at weight 600; a
  `ProgressBar`; and a `text-sm` line with "193 of 200 products" at one end and "7 to go" at 70% at the other.

When all three are held, "All three earned" at weight 600 replaces the next level. A count already past the next
level's number (a lowered number, not awarded yet) shows a full bar and "0 to go".

**The medallion** (`BadgeIcon`) is an inline SVG on a 48 × 48 viewBox. It shows the dimension's mark on a disc, and the
level as one, two or three arcs around it:

```html
<path d="M27.733 2.827A21.5 21.5 0 0 1 44.203 31.353" />   <!-- arc 1 -->
<path d="M40.470 37.820A21.5 21.5 0 0 1 7.530 37.820" />    <!-- arc 2 -->
<path d="M3.797 31.353A21.5 21.5 0 0 1 20.267 2.827" />     <!-- arc 3 -->
<circle cx="24" cy="24" r="16.5" stroke-width="1" />        <!-- the disc -->
<svg x="15" y="15" width="18" height="18">…</svg>           <!-- the mark -->
```

The arcs are unfilled, 3 wide, with round caps, on a circle of radius 21.5 around the disc. Level 1 colours arc 1,
level 2 arcs 1 and 2, and level 3 all three; the remaining arcs are `base-300`.

|                  | Earned                       | Not earned yet              |
|------------------|------------------------------|-----------------------------|
| The level's arcs | `primary`                    | #a3a3a3                     |
| The disc         | `brand-blue`, its border too | `base-200`, border #d0d0d0  |
| The mark         | white                        | #767676                     |

The three greys are not theme colours; they are constants of the icon. The medallion's accessible name is the label,
followed by "earned" or "not earned yet".

**The marks:** `ScoredDimensionIcon`, keyed by `ScoredSustainabilityDimension` as `DimensionIcon` is keyed by the
compass's dimensions. Environment is `FaLeaf`, Socio-economic `FaHandshake`, Health `FaHeartPulse`, Animal welfare
`FaCow`.

**Numbers** are formatted with `Intl.NumberFormat` in the UI's language (1,440; 0.87; +3.6%), and month names with
`Intl.DateTimeFormat` (`month: 'long'`).

**Words:** every string goes through `t('dashboard.…')`, including the dimension names (Environment, Socio-economic,
Health, Animal welfare). The badge labels come from the API.

**Rule numbers** come from the API, never from the front: the "10" of "10 XP", in the XP popup and in the empty XP
counter, is `xpPerProduct`; the badges' "50, 100 and 200" are the levels' `requiredProducts`.

## Testing Decisions

Backend:

- `DashboardServiceImplTest`, plain JUnit with the DAOs mocked, because the logic is the service's and the reads are
  simple:
  - the rounding and the change: 0.84 → 0.87 is +3.6; averages equal once rounded give 0.0; an empty month gives
    `null`, and so does a previous average of 0.00;
  - the percentile: rounded down; `null` without a basket or without other shoppers;
  - levels earned from the held badges, not from the count: a held badge under a raised number, and a count over a
    lowered number that is not held;
  - the order of the series and of the levels;
  - the language of the labels.
- DAO tests against the real DB:
  - the averages of a month: only the user's baskets; both ends of the range (the last second of the month before, the
    first second of the month after); a basket with nothing to assess changes nothing;
  - the basket count;
  - the standing: users without a basket left out, the user left out, a tie not counted as behind;
  - the offset stored and read back.
- `DashboardTest` (`@QuarkusTest`):
  - a user with purchases on both sides of a month's turn, sent with offsets: `2026-10-31T23:30:00+02:00` counts in
    October and `2026-11-01T00:30:00+02:00` in November, though in UTC both fall on October 31st;
  - the empty dashboard of a new user;
  - `400` for a missing or malformed month;
  - `403` for a service account.
- `BasketAssessmentTest`: the purchase sent with `+05:00` stores the local time as sent and an offset of 18,000
  seconds; a purchase without `purchasedAt` is `400`. `BasketAssessmentServiceImplTest` follows the new signature.
- `CompassReadServiceImplTest` and `CompassOverviewTest`: `hasCompletedAttempt` is false with no attempt and during a
  first attempt, and true once one is completed and during a retake.
- `OpenApiContractTest`: the dashboard path and its schemas, `hasCompletedAttempt`.

Front (Vitest, with the data-layer modules mocked):

- `dashboard.test.ts`: `currentMonth` for December and for a single-digit month; the URL.
- `router.test.tsx`: `/` lands on `/dashboard`; the order of the navigation.
- `DashboardPage.test.tsx`: loading; a failure of either read, and its retry; the empty dashboard instead of the tiles.
- The XP tile: the percentile line shown at `PERCENTILE_SHOWN_FROM` and hidden one below it and at `null`; the popup's
  comparison sentence shown and hidden with it; the XP of a product, in the popup and in the empty counter, is
  `xpPerProduct`.
- `CompassInvitation`: the four states of the overview.
- `Explanation`: it opens and sets `aria-expanded`; it closes on Esc, on the dimmed page and on the button.
- `MonthlyProgress`: the five cases of the table; the three directions of the change.
- The badges: the next level; all three earned; the closest badge across series, and none when every badge is held.

## Issues

Each issue's checklist is in `issues/`, under its number.

| #  | Repository    | What                                                                                                                         | Blocked by |
|----|---------------|------------------------------------------------------------------------------------------------------------------------------|------------|
| 01 | tealhelix     | The purchase time at the shop: `purchasedAt` required, the offset kept and stored, the changelog                             |            |
| 02 | tealhelix     | The compass overview's `hasCompletedAttempt`                                                                                 |            |
| 03 | tealhelix     | The dashboard read: the model, the DAO reads, the rule numbers of `BadgeRules` and `BasketRules`, the service, the resource  | 01         |
| 04 | howibuy-front | The dashboard as the landing page: the redirect, Home dropped, the navigation; `useLoaded` and `LoadFailure` shared          |            |
| 05 | howibuy-front | The dashboard screen: the data layer, the loading, the compass invitation, the tiles and their popups, the empty dashboard   | 02, 03, 04 |
| 06 | howibuy-front | This month                                                                                                                   | 05         |
| 07 | howibuy-front | The badges and their medallions                                                                                              | 05         |

## Out of Scope

- The outcome of a single basket: the retailer shows it at the checkout.
- Leaderboards, a sliding weekly or monthly average, the intervention message (as in spec 005).
- Any month before the previous one; a history of the XP or of the Streak.
- Translations of the front's own strings; the names of the nine placeholder badges (WP5.3).
- Checking a purchase's offset against where the shop is.

## Further Notes

- The percentile's caveat stays on record: XP accumulates, so the percentile measures how much and how long someone has
  shopped as much as how sustainably. With the cutoff, a new shopper will usually see no comparison for their first
  weeks.
