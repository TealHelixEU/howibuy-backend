# Product → archetype ground-truth data

This directory holds hand-compiled **product → SAFAD archetype** ground truth, used by
[`../scripts/assessment-eval.js`](../scripts/assessment-eval.js) to score the assessment pipeline's archetype
matching. `*_archetypes.csv` is the ground truth; `*_foodterm.csv` is a derived glossary (`TH_FOOD_TERM`) built from
the same products; `test_data_*_filled.csv` is the subset of the ground truth the eval is actually run against (see
*The eval test sets* below).

- `kritis/0*_kritis_*.csv` — kritis.gr, Greek product names.
- `rimi/et/` — rimi.ee, Estonian product names.
- `rimi/lt/` — rimi.lt, Lithuanian product names.

Every retailer gets its own subdirectory. A retailer that sells the same catalogue under several languages gets one
subdirectory per language below it (`rimi/et`, `rimi/lt`) — the product name is the eval's only input, so the
language is part of an item's identity, not a presentation detail, and the same product under two languages is two
ground-truth rows. Files are numbered per retailer category, and a single file may accumulate several site
subcategories (e.g. `rimi/et/05_meat_archetypes.csv` takes pork, then beef, then poultry).

## Columns

```
Product name,L1 cat,L2 cat,L3 cat,Archetype,Match reliability,Source URL,Comments
```

- **Product name** — verbatim from the retailer, in the retailer's language. Never translate it: the eval feeds this
  string, and only this string, to the model.
- **L1/L2/L3 cat + Archetype** — a row from the SAFAD database (below). `Archetype` is its `food_product` column.
  All four must form a tuple that actually exists in the database — the build scripts assert this.
- **Match reliability** — A/B/C, blank when no archetype fits. See *Reliability grades*.
- **When no archetype fits, keep the taxonomy as deep as it genuinely reaches** and blank the rest. If only
  the row is missing, L1 and L2 stay filled (the common case — a protein drink keeps
  `Milk and dairy products / Milk based beverages`, pressed yeast keeps
  `Herbs, spices and condiments / Baking ingredients`); if the L2 branch is wrong too, keep L1 only (packed
  cultivated mushrooms keep the vegetable L1); if the food is outside the taxonomy altogether, blank all
  four (carob flour is not a cereal). Never fill L3 without an archetype.
- **Comments** — optional. Write one when there is something to say: the cost of a compromise, a retailer data error,
  a cut or term that needed decoding, an attribute SAFAD has no axis for, a discrepancy between the pack and the
  archetype. Leave it blank when the mapping is self-evident — `Täispiim 3,6-4,2%` → `Milk, whole, pasteurised`
  explains itself, and a comment there is noise. The older files leave the plain cases blank; that is correct.

## The archetype source

`TH_WP3-FULL DATABASE-v2026-05-27.csv` (the SAFAD/WP3 database). It lives outside this repository; its path is
machine-specific, so ask for it or take it from `CLAUDE.local.md` in this directory if one is present.

- **Read it as `latin-1`, not `utf-8`** — it has 0xb0 bytes that break a utf-8 decode.
- Hierarchy: `SAFAD_L1_cat` / `SAFAD_L2_cat` / `SAFAD_L3_cat` / `food_product`. 2451 rows, 20 L1 categories.
- Nutrition lives in `H_energy_kJ_100g`, `H_total_fat_g_100g`, `H_SFA_g_100g`, `H_sugars_g_100g`,
  `H_protein_g_100g`, `H_salt_g_100g`. These are what the app consumes, so they decide ties.
- It is French/CIQUAL-derived and misses whole Baltic and Greek product classes. Expect gaps; record them.

## The governing rule: reachable from the product name alone

**The eval gives the model nothing but the product name, so an archetype the name cannot reach is worthless ground
truth.** Where the pack is silent on an attribute, follow the label rather than the closest composition.

Two decisions that set this rule:

- *"if the model has no way of knowing, let's revert to whatever the label says"* — `Margariin sinine light Keiju 50%`
  had been put on the `rich in omega 3` margarine archetype because its 13 g saturates matched. The pack makes no
  omega-3 claim, so it went back to the plain lightly-salted 50-63% row.
- *"It does not know its exact composition, just that it is 'Mayonnaise'. I believe it should characterize it as
  such."* — every mayonnaise whose pack does not say light takes `Mayonnaise (70% fat and more)`, even where the real
  product is 50% fat against the archetype's 74,5 g.

**The rule bars name-unsupported *qualifiers* (light, reduced-fat, omega-3, flavoured, aged), not tie-breaking
between equally plain alternatives.** Choosing between Gouda and Edam for an unnamed semi-hard cheese, or between the
20% and 25% fresh-cheese tubs, is a legitimate composition call — neither is a qualifier the name has to support.

### Dropping a product: on the shelf, and deliberately not in the file

Every file carries every product its shelf holds — that invariant is what the completeness proof rests on, and a
row that is hard to map is mapped anyway, at C, with the reason in Comments. **The one exception is a product that
is not food in the sense the taxonomy measures.** A chewing gum is the case that forced it: SAFAD names gum
nowhere — not the confection, not the base, not the resin — and the only rows that could take one model a sweet
that is *swallowed*. A gum is chewed and spat out, so its nutrition panel describes something nobody consumes, and
putting it on any row would attach a footprint to a food that was never eaten. That is a different thing from a
missing row, which is what C is for.

The mechanism keeps it a declaration and never a silence. A mapping module may define `DROP` (product id →
reason); `build_gt.py` removes those ids before it checks that the mapping covers the listing, refuses an id that
is in both `ID` and `DROP` or one the listing does not hold, prints each drop and its reason on every build, and
exempts exactly those URLs from the guard that otherwise refuses to lose a row a file already has. The scraped
`products.json` still records all 42 products of `SH2-24-134`, so the shelf's completeness proof is unaffected;
what changed is only what the ground truth claims to be able to score. Where a module drops, its own `check()`
should also assert that nothing matching the dropped kind reached the mapping, so the next such product fails the
build instead of being filed as something else.

### Which name? The listing card abbreviates, and 11% of the time it drops an axis

Every file records the name printed on Rimi's **listing card**, because that is what the listing scrape reads. The
product page carries the full name in `h1#product_name`, and the two differ for **66% of the 4525 cached products**.
Most differences are harmless — the card writes `Kiauš.` for `Kiaušininiai`, `Vyt.` for `Vytintos`, or keeps the fat
percentage while dropping the word `rieb.` — so the informative token survives as a prefix or as a number. **For 498
products (11%) it does not**: the card loses a word naming an axis SAFAD models, and no prefix of it remains. By kind:
274 flavour words, 66 *įdaru* (with filling), 41 smoked (`Š. r.` and `K. r.` are all the card keeps of *šaltai*/
*karštai rūkyta*), 35 gluten-free, 28 lactose-free, 15 salted, 14 wholemeal, 9 marinated, 6 processed-cheese,
4 heat-treated, 3 air-dried.

Two examples of the residue:

| card | product page |
|---|---|
| `Makaronai RIMI GOODLIFE PENNE RIGATE, 500 g` | `Pilno grūdo makaronai RIMI GREATLIFE PENNE RIGATE, 500 g` |
| `Š. r. atl. lašišų juostelės RIMI SMART, 200 g` | `Šaltai rūkytos atlantinių lašišų juostelės RIMI SMART, 200 g` |

The first also shows the card carrying a **stale brand** (GOODLIFE against the page's GREATLIFE).

This does not weaken the governing rule; it shows that *which* name the rule ranges over was settled implicitly and
is worth settling on purpose. **`lt/20` is the first file where it bit**: four wholemeal pastas whose card name drops
the whole-grain words take the wholemeal archetype — whole-grain semolina is their sole ingredient and their panels
land on that row — at an explicit **B**, with the reason in Comments. The mapping is certain; its reachability from
the recorded name is not, and those are two different things. Nothing has been rebuilt: moving the corpus to the page
name would change the eval's input for roughly a ninth of all products, which is a decision for Nikos and not a
cleanup. Measured 2026-09-11 while building `lt/20`; the four cases are listed in
`helperscripts/rimi/lt_ps_map.py`.

**`lt/23` is the strongest evidence yet, and it points the other way from `lt/22`.** Drift is category-dependent —
30,8% on grains, 37,1% on flour, **88,5% on instant meals** — and on instant meals it is not cosmetic. **55 of the
156 product pages open with `Greitai paruošiami` or `Greito paruošimo` (instant) where the listing card does not.**
The card leaves `Makaronai vištienos skonio MAGGI, 59,2 g`, which reads as chicken-flavoured *pasta*; the page says
`Greito paruošimo makaronai vištienos skonio MAGGI`. That is the difference between `Dried pasta, raw` in
`Grains / Pasta (Raw) / Pasta, wheat flour, without eggs` and `Asian noodles, flavoured, dehydrated` in the
neighbouring L3 — two rows, two footprints, and a miss the eval would score as a real error when the recorded name
never carried the word. The card is also what collapses three SUN YAN pairs onto one display name each; the pages
separate them with `pakelis` (sachet). On `lt/22` the drift cost no archetype; here it costs the axis on a third of
the shelf. Measured 2026-09-12 while building `lt/23`, directly from the cached pages —
`extract_det.py` copies the listing name into the detail JSON rather than parsing `h1#product_name`, so the detail
file cannot be used to measure this.

**`lt/24` is the counter-example, and it sharpens the question rather than settling it.** On the sauce shelf 108 of
180 cards (60%) are word-for-word the product page once the pack size is normalised — the card writes `480g` where
the page writes `480 g` — and of the 72 that really differ, almost all are abbreviation with the informative prefix
left standing (`pomid.`, `pad.`, `mak.`, `Šveln.`). **Five cards lose a word naming an axis, which is 2,8% against
`lt/23`'s 35%.** Three of the five are one case: `SUSLAVIČIAUS padažas su alyv. aliejumi`, `su mork. ir papr.` and
`su česn., al. aliej.` are all `SUSLAVIČIAUS padažas SALOTOMS su ...` on the page — sauce *for salad*, the word that
makes them dressings — so they are mapped as dressings from the ingredient list and from nothing in the recorded
name. A fourth, `Pomidorų padažas DAUMANTŲ KARALIŠKAS, 480 g`, is `... KARALIŠKAS, nepridėta cukrų` on the page: the
card drops a no-added-sugar claim, which is why that row grades C where its sweetened siblings grade B. So drift is
category-dependent in *kind* as well as degree — cosmetic on a condiment shelf, structural on an instant-meal one —
and the decision is about how much an occasional lost axis costs, not about a constant rate.

**`lt/25` is the third reading and the one that says what the cost actually is.** On the coffee shelf 113 of 200
cards (56,5%) are word-for-word the page after size normalisation, and of the 87 that differ almost all are a pure
expansion — `Kav. kaps.` to `Kavos kapsulės`, `AMM` to `A MODO MIO`, `Skrud.` to `Skrudinta`. **Six cards lose a
word, and two of the six change the archetype.** `Kav. kaps. DOLCE GUSTO CAFÉ INTENSO` is `DOLCE GUSTO CAFÉ AU LAIT
INTENSO` on the page, and `Kavos gėrimas MOKATE GOLD HAZELNUT` is `MOKATE CAPPUCCINO GOLD HAZELNUT`: both are
64-74% milk-powder packs whose recorded name says only "coffee", so on the card name alone the answer is `Coffee,
ground` and the truth is the white-coffee row. The other four lose *koncentruotas* (the three JACOBS bottles, which
is the whole of why they are not graded down for strength) and *MACADAMIA*. So the rate is low, as on `lt/24` — but
a 1% rate of lost axes is not a 1% cost when the lost word is the one that decides the row, and the two readings of
the corpus's drift can now be stated exactly: the *frequency* argument favours leaving the card name alone, the
*severity* argument does not. Measured 2026-09-12 while building `lt/25`.

**The tea half of `lt/25` (SH-2-2) is the worst rate measured and it changes nothing about the conclusion.** Only
85 of 189 cards (45%) survive normalisation — against `lt/24`'s 60% and the coffee shelf's 56,5% — because this
shelf's names are long strings of plant names that the card truncates wholesale (`Arbata mel. lap. liep. žied.
ŽOLYNĖLIS` for `Žolelių arbata su melisų lapais ir liepų žiedais`). **And again exactly two cards lose the word
that decides the row, and it is the same word both times**: `Arbata SELECTION BY RIMI GUNPOWDER` and `Svarainių ir
opuncijų sk. arbata LOYD` are `Žalioji arbata ...` and `... skonio žalioji arbata ...` on their pages, so on the
card alone both read as unspecified or fruit teas and both are green tea. Three further cards disagree with their
page on the sub-brand — RIMI BASIC on the card against RIMI SMART on the page (804290, 804291, 812078) — which is
a retailer inconsistency rather than drift. So across three shelves the severe cases run at two per shelf
regardless of the cosmetic rate, which is the number the decision should turn on. Measured 2026-09-13.

## Cross-cutting mapping rules

- **A banded family picks its row by the declared percentage — unless that band's only row carries qualifiers the
  name does not state, in which case the band is unavailable.** Mayonnaise has three rows, one per oil band, and the
  middle one (25-50% oil) is `Mayonnaise flavoured with garlic, chilli pepper and fish broth, prepacked`. A plain
  40% Lithuanian mayonnaise cannot take it without inventing three ingredients, so it falls to the nearer usable
  plain row on energy — the light row up to about 44% fat, the full-fat row above it. Half the Lithuanian mayonnaise
  shelf sits in that hole and grades C; that is the honest reading, not a mapping failure. Salad dressing has the
  same three-band shape with no such obstruction, so its bands are used as they stand.
- **The unavailable-band rule scales up: where *every* row of the family the name points at carries an ingredient
  qualifier the name does not state, the family is unavailable, not just one band.** The mayonnaise case above
  rejects one row of three. `lt/24` found the whole-family case: SAFAD has no plain tomato sauce — every row in
  `Savoury sauces / Vegetable sauce` names an added ingredient (onions, vegetables, cheese, mushrooms, olives, meat,
  sweet peppers, pizza base) — so a Lithuanian `pomidorų padažas` cannot reach any of them without inventing an
  ingredient. It goes instead to the nearest row that invents nothing, `Ketchup`, whose composition it matches cell
  for cell, **capped at B because the type word is not the pack's own**. The corroboration that makes this a
  reading of the market rather than a numeric convenience must be on the shelf itself: there, the same producers
  sell one recipe under both words, and the retailer files four `padažas` products in its ketchup and pasta-sauce
  children. Where that corroboration is absent, prefer the qualified row and take the C.

- **A name that declares no fat content takes the family's plain full-fat row, whatever the pack turns out to hold.**
  Three Pirmas Lietuviškas mayonnaises name no percentage and are in fact 35% fat: they take
  `Mayonnaise (70% fat and more)` at -51% on energy and grade C. The eval sees only the name, so the default
  mayonnaise is the answer the name supports; the C records what the pack really is.

- **Species wins** over brine, smoke, format and texture. Goat cheese in brine → `Cheese, Capra`, not Feta; smoked
  goat cheese → `Cheese, Capra`; ewe salad cheese → `Feta cheese, from ewe's milk`. Same for meat: raw pig trotters
  take the salt-cured *pork* row over the raw *calf* foot in the same L3, because species drives the footprint.
  It also wins over the *form* where SAFAD makes you choose: goat kefir takes the one goat yoghurt row rather than
  the species-blind `Kefir` one — confirmed numerically on rimi.lt, where an organic goat kefir at 302 kJ / 4,4 g fat
  lands within 3% of `Yogurt, goat's milk, plain, around 5% fat` while the cow kefir rows are 27-37% away. But the rule ranks the attributes **the name supplies** — where only the
  ingredient list gives the species, name-reachability comes first and the species is lost; see the goat-and-sheep
  rules.
- **A species named in an L3's own parenthetical is inside that group, so a sibling-species row there is B, not
  C.** `Giblets (chicken, turkey, duck, goose)` carries only `Gizzard, chicken, raw` and `Heart, chicken, raw`, so a
  duck gizzard and a duck heart take the chicken rows — the taxonomy has already said duck giblets belong in this
  group, and the substitution is within it. This is what separates them from the quail eggs in the eggs file, which
  are C because no egg L3 mentions quail (the `Goose eggs` L3 is the only non-chicken slot and its parenthetical
  names nothing). Read the L3 label, not just the product row: it is the taxonomy's own statement of scope.
- **Retail meat is always the `raw` archetype**, never cooked/grilled/roasted.
- **An exclusion in an L1's own title is a routing instruction, and it outranks the name's head noun.** SAFAD files
  coffee under `Non-alcoholic beverages (excepting milk based beverages)`, so a chilled *kavos gerimas* that is 75
  to 95% milk cannot take `Coffee with milk or white coffee or cappuccino` however plainly its name says coffee —
  the taxonomy has already said where milk-based drinks go. The 12 Lithuanian iced coffees therefore sit on
  `Milk and dairy products / Milk based beverages / Flavoured milk`, whose generic `Dairy drink or fermented milk or
  yogurt, flavoured, with sugar` row matches them to within 24% on energy where the coffee row is 164% out and
  unsweetened. Grade such rows B, not A: the name's head noun genuinely points at the row SAFAD forbids, so the
  mapping is an interpretation and an eval consumer should see that.
- **These are not axes SAFAD encodes — ignore them and say so in Comments:** marination, grating, slicing, smoking,
  pack size, lactose-free, organic, vitamin fortification *outside the breakfast-cereal branch* (see the next
  bullet), A2 milk, egg husbandry (cage/barn/free-range/
  organic — there is no distinction anywhere in the egg branch), and **probiotic strain** — with one exception
  worth knowing: SAFAD does carry three `… with bifidus` yoghurt rows and one `… with L Casei` drink row. The
  bifidus ones duplicate the plain, flavoured and with-fruits yoghurt rows and go unused; the L Casei row is used,
  because it models a whole product format (an Actimel-style shot) and not the culture. See the yoghurt rules.
- **Where SAFAD carries a type twice, once fortified and once not, take the NOT-fortified twin every time.**
  `Breakfast cereals` is the one branch that puts fortification in its row names: 37 of its 49 rows carry the
  clause, nine types are stored twice with a "(not fortified…)" and a "fortified…" row whose macros differ (a
  tenth pair is spelled `Cereal bar w fruit` against `Cereal bar with fruit, fortified…`), and 26 rows exist only
  in a fortified version. No product name says either word, so the axis cannot be read off the name — and the
  pack cannot settle it either: Rimi's ingredient text lists vitamins for 35 of `lt/17`'s 58 cereals but not for
  KELLOGG'S CORN FLAKE, which Kellogg's fortifies across the EU, so the declaration is incomplete rather than
  informative. Choosing per pack would give two own-brand cereals of the same recipe different archetypes for a
  reason no name-only model could recover, so the type is taken from the name and the twin is fixed by rule. Where
  a type exists only as a fortified row (puffed wheat with honey, a non-chocolate filling, the fibre rows), that
  row is used as it stands and the clause is ignored. **A type that is unique once the clause is set aside is
  `'f'`** — there was nothing to choose between, so the letter comes from energy alone. This is consistent with the
  soya rule ("use the calcium-fortified rows only when the name says calcium"), and the two together give the
  general form: *where a pair differs only by a fortification clause, the name decides and the plain row wins; where
  two rows differ in composition as well, composition decides.* The cost is real and should be stated when it
  bites — KELLOGG'S FROSTIES declares 37 g of sugars where the fortified sugar-iced twin stores 36,4 and the row
  this rule takes stores 23,4.
- **Two rows that are byte-identical in composition are chosen between by the product's own word.** SAFAD stores
  `Breakfast cereals, chocolate wheat grain flakes` (L3 `Cereal flakes`) and `Breakfast cereals, with chocolate,
  not filled` (L3 `Mixed breakfast cereals`) with the same six figures, so the choice costs nothing numerically: a
  pack calling itself *dribsniai* takes the flakes row and one calling itself *sausi pusryčiai* or *javainiai*
  takes the mixed row. That is the only axis a name-only model can see, and it keeps both L3s in use.
- **Match on the stored `H_*` values, not on the archetype's name.** Label-vs-data mismatches are systemic:
  `Uncured soft cheese, spreadable, around 20% fat, in a tub` stores 7,5 g fat; two margarine rows named "80% fat"
  store 70 and 72,1 g; the mayonnaise L3 brackets contradict their own compositions (`< 25% oil` holds 28,5 g fat,
  `25 - 50% oil` holds 59,8 g). Salad-dressing brackets, by contrast, do line up. Never read a bracket string as a
  product fact.
- **Where two rows both fit, take the one whose assertions the pack does not contradict; an axis the row is
  silent about is an omission, not a contradiction.** The frozen fish shelf is where this was needed. SAFAD carries
  `frozen` as its own `food_product` for exactly the foods a freezer cabinet sells — `Shrimp, frozen, raw` beside
  `Shrimp or prawn, raw`, `Hake, fillet, frozen, raw` beside `European hake, raw`, `Saithe, frozen, raw`,
  `Fish, breaded, frozen, raw` beside `Fish, breaded, fried` — and the frozen row is normally the truer one,
  since the freezing is a real footprint and the Lithuanian name states it (`šald.`). But the breaded row also
  asserts **raw**, and all fourteen Lithuanian fish-finger packs list rapeseed or sunflower oil in their
  ingredients: they are par-fried before freezing, and declare 830-1110 kJ where that row stores 687. The fried
  row asserts only the frying, which the pack confirms, and merely loses the freezing. Measured over the family,
  the fried row grades 7 A / 6 B / 1 C and the frozen-raw one 3 B / 11 C — but the letters are the consequence,
  not the argument. The same test leaves the shrimp, hake and saithe packs on their frozen rows, where nothing is
  contradicted.
- **A name with no processing word takes the unprocessed row — unless the name states a format that cannot exist
  unprocessed.** The shrimp shelf carries packs labelled `nevirtos` and `virtos`, so silence there is informative
  and means raw. A mussel is another matter: it is shelled or half-shelled by opening it with heat, so
  `midijų mėsa` has been cooked whatever the name says, and the numbers agree — the three silent packs sit
  +31 to +37% over `Mussel, common, raw` and within 13% of `Mussel, boiled/cooked in water`. Such a row takes the
  processed archetype with **the letter capped at B**: a mapping read off the format rather than off the name
  cannot be an A, on the same reasoning that denies A to a substituted species. Otherwise the pack that *states*
  its cooking grades worse than the ones that leave it to be inferred, which is backwards.
- **Where SAFAD carries a diet claim and the name states it, the claim row beats the flavour row.**
  **Fourteen** of the 2451 rows carry one outside the drinks branch: `Bread, gluten free`, `Dried pasta,
  gluten-free, raw` and `… cooked, unsalted`, `Biscuit (cookie), reduced sugar`, `Breakfast biscuit
  (cookie), reduced sugar`, `Dark chocolate bar, without sugar and with artificial sweeteners`,
  `Milk chocolate bar, without sugar, with sweeteners`, `Jam, reduced sugar`, `Fruits compote,
  miscellaneous, reduced sugar` and its `… refrigerated` twin, `Fruits puree, without sugar added`,
  `Muesli, flakes (Bircher-style), with fruits or dried fruits, without sugar`, `Condensed milk, without
  sugar, whole` and `Milk, semi-skimmed, reduced lactose`. (Another 34 sit in the coffee, tea, cocoa and
  soft-drink branches, where *without sugar* is the drink's default form rather than a claim.)
  `Svies.sausainiai be cukraus BOGUTTI` says both *sviestiniai* (butter) and
  *be cukraus*, and `Butter biscuit (cookie)` exists too; the sugar row wins, because a claim row is the
  more distinctive true statement and sugar is an axis a score actually moves on. `Sviestiniai sausainiai
  DIABLO` makes no claim in its own name — DIABLO is a brand — and keeps the butter row. Outside those
  fourteen the claim is invisible: a sugar-free jelly, caramel or wafer takes the sugared row and loses
  30-40% of its energy to the polyol, which is the honest reading and not a mapping failure.
  **The claim has to be in the name, though, and on a jam shelf it never is.** `Jam, reduced sugar` names
  no fruit, and no Lithuanian jar says *reduced sugar* — *uztepele* means spread, RIMI SMART is a value
  line, *ekologiškas* is organic — so on `lt/18` every jar takes its own fruit's row and the low-sugar
  band grades C: 15 of the 45 jams and marmalades, all of them between 452 and 776 kJ against fruit rows
  that store 987-1060. The general form: **where a family's rows
  are named by ingredient and one of them is named by a claim instead, the name decides which axis is
  reachable, and an ingredient beats a claim the pack does not make.**
- **A no-added-sugar or keto shelf breaks the automatic macro check, and the flags must be read by hand.**
  `extract_det.py` costs carbohydrate at 17 kJ/g. EU labelling costs polyols at 10, fibre at 8 and organic
  acids at 13, so an isomalt lollipop (98 g carbohydrate, 988 kJ), a psyllium husk, an oligofructose bar,
  a coconut flour and an effervescent citric-acid tablet all trip SUSPECT MACROS with correct panels. On
  `lt/15` the automatic check flagged 25 packs and **11 of them were sound**, in exactly this
  way. The same trap caught a brewed shelf: on `lt/16` the three plain KIKKOMAN shoyus are flagged because
  soy sauce carries 1,5-2,5% ethanol (29 kJ/g) and about 1% organic acids, and the check prices the four
  macros only. **Any energy source outside fat, carbohydrate and protein reads as a fault** — polyols,
  fibre, organic acids, alcohol — so on a no-added-sugar, keto, fermented or brewed shelf, read the
  ingredient list before calling it a retailer error. **A vinegar shelf is the pure case**, found on
  `lt/19`: acetic acid is *most* of a vinegar's energy, so the residual after the macros is not noise but
  the measurement. An organic cider vinegar declares 96 kJ on 1 g of carbohydrate — 17 kJ of macros, and
  the 79 kJ left over is 6,1 g of acid at 13 kJ/g, which is what a 5-6% cider vinegar holds. Aceto
  Balsamico di Modena IGP declares 435 kJ where its 20 g of carbohydrate and 1 g of protein buy 357,
  leaving the 6% acidity the IGP specification requires. The check reports these as +465% and +22%;
  both panels are exactly right, and SAFAD's own `Vinegar` row carries the same acid (97,1 kJ on 0,04 g
  of sugar and 0,04 g of protein), so the trap never reaches the mapping.
- **An oil panel declared per 100 ml under a per-100-g header, and how to recognise it.** This is not the
  Atwater trap — the arithmetic is sound and the *basis* is wrong. All 75 oils on `lt/19` publish a panel,
  and their fat figure is bimodal: 29 read 99,8-100 g and 46 read 91-93 g, with nothing in between, and
  every one of the 46 has an ingredient line saying 100% oil. 100 g of oil is 100 g of fat by definition, so the lower
  mode is not a composition: 92/100 is 0,92, the density of refined vegetable oil in kg/l. Energy follows
  the fat figure at 37 kJ/g on 52 of the 75 panels, so the -6 to -9% energy gap and the -8 g fat gap those
  packs show **are one fact and not two**, and neither is evidence about the mapping. The page labels every
  panel "per 100g", so nothing on it gives the basis away; the bimodal distribution with an empty middle is
  the tell. Two packs mix their bases and are real faults (a rapeseed oil pricing 92 g of fat at 3556 kJ
  where 37 kJ/g gives 3404; a grapeseed oil taking energy on the mass basis and fat on the volume one).
  Expect it on any oil, and on any other liquid sold by volume check the direction before the size:
  honey at about 1,4 kg/l would push a per-100-ml panel the other way. Read the ingredient line before
  taking an 8% deficit for a composition difference.
- **A seed or nut row is the kernel, and a pack sold in its hull describes a different 100 g.**
  `Sunflower seed` stores 2700 kJ and 55,5 g of fat and `Cucurbitacea, seed` 2560 and 49,1: those are kernels.
  Sunflower and pumpkin seeds are also sold whole, and the hull is inedible, spat out, and counted in the pack's
  declared 100 g — the packs declaring on that basis imply 45 to 56% of a sunflower seed's mass and about a third
  of a pumpkin seed's. On `lt/27` 28
  packs are capped this way and their declared energy spans 1131 to 2747 kJ, a 2,4-fold range over one seed: nine
  declare 1131 to 2116 kJ with macros that reproduce the figure to within 4,4%, so those panels are right and
  simply describe a different basis, eighteen declare kernel-basis figures, and the last is a genuine fault. Two
  RIMI own-brand sunflower packs, both unhulled by their own product pages, sit at 1404 and 2679 kJ, so the basis
  is a brand convention and not a property of the food. **The axis is readable
  off the name in one direction only**: *lukštentos* and *branduoliai* mark the kernel and nothing marks the whole
  seed (*nelukštentos* appears on the product page and never on the listing card). So grade a pack whose name
  states the kernel on its numbers and **cap every other one at B** — species and processing are certain, and what
  is uncertain is whether the pack's 100 g is the archetype's 100 g. Same reasoning as the mussel rule: a mapping
  read off the format rather than off the name cannot be an A.
- **Fibre is the other direction of the macro-check trap, and a whole shelf can sit in it.** The recorded polyol,
  acid and alcohol cases all make the declared energy come out *below* what the four macros buy. Fibre does the
  opposite: it costs 8 kJ/g under Annex XIV and is not one of the four, so a high-fibre pack declares *more*
  energy than the macros can account for. On `lt/27` ten of 253 panels do exactly that, by 141 to 492 kJ, and
  every one is chia, flaxseed or a freeze-dried berry — the deficit divided by 8 gives 18 to 62 g of fibre per
  100 g, which is what those foods hold. Two panels on the same shelf go the other way by 133 and 134 kJ and are
  a dried fig and a dried plum, both of which carry sorbitol. Rimi's panel prints no fibre line at all, on any of
  the 253 pages, so the arithmetic is the only evidence. Read the food before calling a flag a fault: the same
  carbohydrate line is mispriced in both directions on one shelf.
- **When a block of rows shares one LCA, the distinctions inside it cost nothing and the ones across it cost
  everything.** `Snack food` is 22 rows in seven L3s, and **eleven of them carry the identical footprint** —
  EF 0,21, climate 1,54, land 189, water 0,70 — across four L3s: both maize puffs, both puffed potato snacks,
  the unpuffed potato snack, all four `Pretzels` rows and both cheese rows. Three more share a second (all of
  `Potato crisps`: standard, `à l'ancienne`, reduced fat, at EF 0,20 / 1,40 / 226). So on `lt/28` the
  slice-versus-dough question moves the score and the cut question inside `Potato crisps` does not, and a wheat
  cracker, a maize puff and a potato-dough snack are the same food to the scorer. This is the `Saccharin
  sweetener` copied-LCA defect an order of magnitude larger, and it is worth checking the E_ columns of the
  candidate rows **before** spending a shelf's effort on a distinction: compare `E_EF_single_score`,
  `E_climate_change`, `E_land_use` and `E_water_use`, and if they are equal the choice is a nutrition choice.
- **A snack's *processing* is what SAFAD splits on and what the retail name never states, so the cap rule
  applies again.** Three potato rows are one food processed three ways — `Potato crisps` is sliced and fried,
  `Salty snacks, made from potato` is a dehydrated-potato dough pressed and fried, `Puffed salty snacks, made
  from potato` is the same dough extruded — and the Lithuanian name is *bulvių traškučiai* for all three. Where
  a brand settles it (PRINGLES, POM BEAR) grade on the numbers; where it does not, **cap at B**, exactly as the
  hull rule caps a seed. `lt/28` caps fifteen packs that way, and LAY'S is why: it sells a sliced crisp at
  2235 kJ / 32,6 g and a potato-flake one at 1854 / 14 whose **card names differ only in the pack size**.
  The same split runs through maize: an oil-fried puff at 23-32 g of fat, an unfried extruded stick at 1,2-2,2 g
  and a sweetened unfried stick are three different rows in three different L1s, and only the fat and sugar say
  which. Do not let an L3 called `Corn chips` collect all of them.
- **The copied-LCA rule cuts both ways, and the biscuit branch is the other side of it.** Where `Snack food`
  gives eleven rows one footprint, `Fine bakery wares / Biscuits (cookies)` gives 59 rows **33 distinct
  footprints**: climate 1,54 to 10,30 and land use 67,7 to 402, a sixfold spread inside one L3. Plain against
  chocolate-covered is 3,59 against 7,79 and 161 against 313; a cocoa batter against a jam one, in
  `Pastries and cakes`, is 8,36 against 4,63. So the same check — compare `E_EF_single_score`,
  `E_climate_change`, `E_land_use`, `E_water_use` before spending effort — says *spend it* here. Run the
  comparison on any new branch before deciding how careful to be; the answer is not a property of the taxonomy
  as a whole.
- **Read the `food_name_CIQUAL` column before trusting an abbreviated SAFAD row name.** Two rows on `lt/29`
  say the opposite of the food behind them. `Biscuit (cookie), with milk` is CIQUAL 24014, whose own name is
  "Biscuit (cookie), with a **chocolate bar covering, milk filling**" — so a condensed-milk biscuit must not go
  there and a chocolate-coated one with a milk cream must. `Biscuit (cookie) with nuts (**no chocolate** or
  lightly chocolate-flavoured)` is CIQUAL 24676, "Biscuit (cookie), with **chocolate and nuts**, prepacked" —
  the clause in the SAFAD name denies what its source food asserts, and the two best-fitting packs on it carry
  exactly that — MILKA's chocolate-and-hazelnut biscuit at 0% on energy and RIMI's NOUGATELLI at
  +0,4%. A row whose SAFAD name sounds like a catch-all can be one specific food the same way:
  `Candies, all types` is CIQUAL 31060, whose own name is `Jelly candy`, and the file carries `Jelly candy`
  separately on that same code — its 0,53 g of fat proves which of the two names is true, so nothing fatty may
  be filed on the apparently generic row. `lt/30`'s tablet shelf found a third: `Milk chocolate, filled` carries
  `Milk chocolate, filled with praliné, bar`, so the generic-sounding row is a praline row by its source. That
  one was **not** followed, and the exception is worth stating: where a narrower CIQUAL name is attached to a row
  that DUPLICATES another row's code (here `Milk chocolate bar, filled with praline`, also 31084), the sane
  reading is that SAFAD split one CIQUAL food into a specific and a generic node, not that it wrote the same
  food down twice — and the cost of being wrong is bounded, because the two carry the same footprint to within
  2%. Where the CIQUAL name contradicts a row that has no duplicate, follow the CIQUAL name. The column is
  populated on only some rows, but where it is populated it outranks the SAFAD name, because the numbers were
  computed for the CIQUAL food.
- **Duplicate rows are not always harmless: one pair disagrees about its own footprint.** `Wafer biscuit,
  crunchy (thin or dry), with chocolate, prepacked` and `Wafer biscuit, filled with chocolate, prepacked` are
  both CIQUAL 24311 with identical nutrition — and climate 2,86 against 7,66, land 161 against 313. One food,
  one composition, two footprints 2,7x apart. Prefer the one whose LCA is consistent with its own family (here
  the second: every other chocolate row in the branch carries 6,06-10,30), and record the pair. `Wafer cookie (1)`
  and `(2)` are a byte-identical duplicate of CIQUAL 24370, and three names share CIQUAL 24050.
  `lt/30` found the second instance, which makes it a pattern in the database rather than an accident:
  `Milk chocolate bar, filled with praline` and `Milk chocolate, filled` are both CIQUAL 31084 with identical
  nutrition and EF 0,79 against 0,87, climate 12,60 against 12,80. The harmless kind occurs there too —
  `Chocolate bar with biscuit` and `Chocolate snack bar, dairy filling with sponge cake` are both CIQUAL 31000,
  byte-identical in nutrition and footprint, so the choice between those two is descriptive only.
  **A shared footprint across DIFFERENT foods is the same defect wearing a disguise.** Three rows of the
  chocolate branch — `Dark chocolate bar, with fruits (orange, raspberries, pear)` (31069), `Chocolate bar with
  dried fruits` (31071) and `Toblerone milk cholcolate bar w nougat` (31098) — carry one footprint, EF 0,38 /
  5,32 kg CO2e / 181 m2a, and it is the lowest in the L2 by a factor of 2,4. `lt/31` found a **fourth member in
  another L1 entirely**: `Coconut bar, with chocolate coating` (31002), which SAFAD files under `Fine bakery
  wares`, carries the same three figures — so the cluster is not a property of one branch and cannot be an
  artefact of one branch's model. Neither reading survives contact
  with the recipe: a dark chocolate drops from 19,10 to 5,32 for adding 10% orange, and TOBLERONE is milk
  chocolate with 3% honey. Map onto the right row anyway and carry the cluster upstream — the alternative is
  choosing rows by their footprints, which is how a ground truth stops being one.
- **A coating or a filling the card does not name caps the letter at B**, the same rule as the unbranded
  potato dough. `lt/29` caps six: DZŪKIJA names its biscuits after wild flowers and rainbows and TASTE STORY
  after musical terms, and an 18-35% cocoa glaze or a 26-30% cream is invisible from the card. The mapping is
  not in doubt — the ingredient list declares the glaze — only its recoverability is, and that is what the
  letter is for. Where the numbers already give a B nothing more needs doing.
- **`X, plain` is a row, not a default.** `Biscuit (cookie)` (CIQUAL 24000) is the category average and
  `Biscuit (cookie), plain` (24001) is the petit-beurre; the plain row is taken only where the name says plain
  or classic, and a brand-only or flavour-only name takes the unqualified average. The same shape appears
  wherever a branch carries both an unqualified row and a `…, plain` one.
- **A panel that passes its own consistency checks is not thereby the pack's panel.** `lt/30`'s 50 g TWIX
  prints 2032 kJ, 23 g of fat, 9,1 g of saturates and 8,6 g of protein — the SNICKERS panel, cell for cell.
  It is internally consistent (485 kcal x 4,184 = 2029, and the macros buy 2020), so none of the arithmetic
  checks catches it; what catches it is that the same bar in two other listings, 5 x 50 g and Xtra, both
  publish 2081 kJ and 24,2 g. **Duplicate listings of one product are a second, independent check on its
  panel**, and worth running wherever a shelf sells the same bar in several formats.
- **A panel can be right about itself and wrong about what it is per.** `lt/30`'s three DIOREL compounds print
  12,4 g of fat, 25,2 g of carbohydrate and 3,3 g of protein — 41 g of the 100 g the header claims — and 218
  kcal. Multiply by 2,5 and every figure lands on a real confection (31 g of fat, 63 g of carbohydrate, 545
  kcal): it is a 40 g portion panel published as a per-100 g one. The macro-sum check is the one that sees this
  class, because a portion panel's macros will not add up to 100 g; the kJ-against-kcal and saturates-ratio
  checks cannot, since the figures are consistent with each other. Here the kJ cell had been filled with the
  kcal number as well, which is what raised the flag. A panel whose macros sum to far under 100 g deserves a
  look even when nothing else complains.
- **The noun a composite product is is its OUTER layer, not its inner one.** A bar whose chocolate is the body
  and whose wafer is the filling is `Chocolate confectionery, filled with wafer` in `Sugar and confectionary`;
  a pack whose wafer is the body and whose cream is the filling is one of the `Wafer biscuit, …` rows in
  `Fine bakery wares`. The same shape decides coated marzipan, glazed fruit bars and biscuit-cored bars. It is
  not a labelling preference: on `lt/30` the two homes are 10,50 kg CO2e / 463 m2a against 7,66 / 313, a 37%
  difference. The structural tell is in the ingredient list — every wafer-cored bar declares wheat flour, and a
  solid chocolate does not. On a shelf with no bars at all the same rule reads as **the coating keys the row and
  the centre does not**: `lt/31`'s M&M'S peanut and REESE'S cup both declare a peanut second in the list inside
  milk chocolate, and they are two different rows because one carries a polished, coloured sugar shell
  (`Sugar and chocolate coated peanut`) and the other does not (`Chocolate confectionery, filled with nuts
  and/or praline`). The tell there is the glazing agent — shellac, carnauba or beeswax, E901/E903/E904.
  **And on a shelf of boxed chocolates, where every piece is a shell round something, neither question arises
  and the FILLING keys the row** — a nut or nut filling anywhere in the list against no nut at all. Three
  shelves, three different layers doing the work, and the constant is that the answer is always the layer the
  *name* is least likely to mention.
- **Inside `Chocolate (Cocoa) products` the FORM picks the L3, not the filling.** The branch has one L3 for
  bars eaten from the wrapper (`Chocolate coated confectionery`) and separate L3s for blocks you break
  (`Milk chocolate`, `White chocolate`, `Bitter chocolate`, `Bitter-sweet chocolate`, `Chocolate with nuts or
  fruits`), so one filling has two homes: a milk cream in a countline is `Chocolate confectionery or bar, with
  dairy filling` at 7,25 kg CO2e and the same cream in a tablet is `Milk chocolate, filled` at 12,80. The
  77% gap is the right way round — a filled tablet is mostly chocolate and a filled countline is mostly not —
  which is why `lt/30`'s two shelves use two different sets of rows for the same words on the pack.
- **A trademark that denotes one recipe worldwide is reachable from the name; a decorative name inside a local
  range is not.** This is the line the reachability cap is drawn on, and `lt/30` is where it had to be stated:
  19 of its 71 names are a generic noun plus a mark and nothing else. BOUNTY is coconut, DAIM is almond
  brittle, KNOPPERS and GEISHA are hazelnut, TWIX has a biscuit and KIT KAT a wafer — on the wrapper and in
  every reference, so the name reaches the row and the letter stands. DZŪKIJA's wild-flower names and TASTE
  STORY's musical terms reach nothing but the ingredient list, so they are capped. `lt/29` already applied
  this without naming it, by admitting KNOPPERS and NOUGATELLI as nut evidence.
- **A declared "chocolate" coating is often a compound glaze with no cocoa butter in it.** Seven of `lt/30`'s
  71 packs declare a *glaistas* whose fat is hydrogenated palm kernel and whose cocoa is powder — one card
  calls it *pieninio šokolado* outright. SAFAD holds no compound-coating row, so these sit on cocoa-based rows
  and **their cocoa footprint is overstated**; the reverse case (a bar of vegetable oils and 1,8% cocoa powder
  on a milk-chocolate row) understates nothing but is equally unmodelled. Worth recording per pack, because the
  compound coatings are also where the impossible-looking saturates flags come from. `lt/31` adds four more and
  a second flavour of the same hole: two SKAWA dragées under a cocoa glaze of partly hardened palm, soya and
  rapeseed fat, and two RŪTA pretzel balls under a **yoghurt** or a **caramel** glaze with no cocoa in it at
  all. There is no row for a fat-and-sugar glaze of any colour, so all four sit on rows that assert chocolate.
  `lt/31`'s boxed half adds **five more, and the biggest of them are whole assortment boxes**: PERGALĖ LIETUVA
  and PERGALĖ DARK CLASSIC declare 9,9% and 11% low-fat cocoa powder over shea and palm fat and no cocoa butter
  or cocoa mass anywhere, while the MILK CLASSIC box beside them declares cocoa liquor and is a chocolate — so
  within one brand's own range the dark box is the compound and the milk box is not, which is the opposite of
  what the names suggest. The other three are a coconut-fat truffle, a walnut pack under a 49% cocoa glaze and
  a bird's-milk soufflé. **Nine rows across the two shelves now assert a chocolate the card does not declare.**
- **The condiment branch is a named-type taxonomy, so the cheese `'d'` rule applies to it.** SAFAD's
  `Condiment` L2 is a list of named sauces with one row each — barbecue, curry, soy, tabasco, tartare,
  ketchup, mustard, two vinegars — and carries no strength, dilution or sweetness axis anywhere. That is the
  same shape as the cheese branch, so a pack whose name states one of those types is graded **`'d'` → A**
  and the numeric gap goes in Comments. It is a consequential call: `lt/16`'s nine plain soy sauces sit on a
  170 kJ row at +91% to +634% and all take A, because "Sojų padažas KIKKOMAN" reaches `Soy sauce, prepacked`
  and nothing else. The tiering below it, worked out on that shelf, is what keeps `'d'` honest:
  the name states the type and the pack **is** that type → `'d'` A; the name states the type and the pack adds
  an axis the row has no column for (20-52 g of added sugar in a soy sauce) → explicit **B**; the name states
  the type and the composition **denies** it (HEINZ: glucose-fructose syrup second, soy beans fifth at 2,5%)
  → explicit **C**; the name states a type SAFAD has no row for at all → `'x'` or explicit C. A mapping module
  that uses `'d'` must carry a `DECLARES` table and a `check()`, or the A is just hand-typed.
- **So does the syrup and honey branch, and it is the third family to take `'d'`.** `Sugar and
  confectionary / Molasses and other syrups` is four L3s with one plant each — agave, maple, molasses,
  sugar-beet — and `Honey` is a single row under a single L3; there is no botanical, grade, colour,
  blossom-versus-honeydew or set-versus-liquid axis anywhere in either. *Klevų sirupas* therefore reaches
  `Syrup, maple` and nothing else, so on `lt/18` the two maple syrups take A at +31 and +36% on energy (the
  row stores a dilute 1140 kJ where real maple syrup is ~1500), and the eight honeys, three agave syrups and
  one chocolate dessert sauce take A as well. The **`'x'`/C tier below it still bites**: a salted-caramel
  sauce takes the one row in `Dessert sauces`, a chocolate one, at the right energy and the wrong flavour.
- **`'s'` is stricter than `'n'` on a fat-only miss, which is probably not what "capped at B" was meant to
  mean.** `grade.mech` takes `max(|Δenergy|, fat penalty)`, so a substituted mapping whose energy is within 3%
  but whose fat penalty is 52% grades **C** under `'s'` while the identical numbers grade **B** under `'n'`
  (which allows a fat penalty up to 100%). On `lt/16` that is what puts eight of the thirteen substituted
  seasoning mixes at C. Left as it stands rather than changed mid-tree — every existing file was graded under
  it — but it is worth a decision: the documented intent of `'s'` is "A is not available", not "be harsher
  about fat".
- **A word in CAPITALS counts as a food descriptor only when the ingredient list corroborates it.** rimi.lt
  prints brands in capitals, so a line name and a real descriptor look identical, and the frozen-gastronomy shelf
  established that the line name (VIČI TRADITIONAL, GYOZA, KHINKALI; RIMI MARE TORPEDO) is not the dish. The
  frozen-vegetable shelf supplies the other half of the test: `BAUER WOK THAI` and `WOKMIX ORIENT` carry bamboo
  shoots, mung sprouts, baby corn and wood-ear mushrooms, so `Vegetables pan-fried or stir-fried, Asian-style,
  frozen, raw` is confirmed by the pack and not merely by the line, and they take it. `HORTEX MEXICO`,
  `HORTEX VIP MIX` and `BONDUELLE 4 SEASONS` name nothing the pack confirms and take the plain mix row.
  **And a descriptor in a language that is not the shelf's does not count at all** — `BONDUELLE SPRING VAPEUR`
  does not reach `Spring vegetables, frozen, raw` where the RIMI pack's Lithuanian *pavasariškas* does, which is
  the `Vynuogės RED GLOBE` reading (see the grape colour rule) applied to a dish instead of a colour.
- **The L3 name can be the mapping key even when the `food_product` name misleads.** Processed cheese is the clean
  case: `Cheese, processed cheese, plain` / `processed, sliceable` / `processed, with condiments` decide plain vs
  sliced vs flavoured, regardless of the fat figures in the entry names.
- **A row's stored composition is not evidence about its own name, because rows are duplicated across the
  taxonomy.** `Veggie burger` stores the six `H_*` values of `Cheeseburger, double, from fast foods restaurant`
  exactly; `Tofu patty (vegetable steak)` stores those of `Plant-based patty or steak from soybean, cheese and
  vegetables`; `Vegetable Hot-dog sausage` and `Plant-based sausage with wheat or seitan` are the same row under
  two tuples. Match on the values, but do not infer from a good numeric fit that the row means what it says.
- **Two rows sharing one composition is common, and then only the name can choose.** `Vegetable fat (margarine
  type), 70% fat, unsalted` and its 80% sibling are identical; so are `Uncured soft cheese, spreadable, around 20%
  fat` and its 25% sibling, and `Fresh cream cheese, plain, creamy, around 8% fat` and `Petit-Suisse like fresh
  cream cheese, plain, around 9% fat`. Pick the one whose name matches the pack — a 9% curd takes the 9% row — and
  do not read the pair as a signal that either is more precise.
- **Some SAFAD rows are internally inconsistent.** `Semi-hard cheese, from ewe's milk` stores 1750 kJ against its own
  21,8 g fat / 16,3 g protein (feta's composition with a hard cheese's energy) — and it carries ~30 kritis rows plus
  7 rimi rows. Flag such rows in Comments; downstream nutrition from them is wrong.
- **Family consistency beats per-row nearest-neighbour.** Keep a product family on one archetype and let the
  reliability grade absorb the spread. One archetype per family is more learnable ground truth than 40 individually
  optimised rows.
- **No bracket fits → nearest bracket, reliability B, reason in Comments.** Baltic 2,0% and 2,5% milks match no EU
  fat bracket; 5% yoghurt sits between the plain (1,68 g) and with-cream (8,50 g) rows.
- **Shape is not an axis, and a shape word can be the name of a different food.** SAFAD's `Pasta (Raw)` L2 sorts
  only by flour, eggs and raw-vs-cooked; it names no die anywhere, so fusilli, penne, farfalle, spaghetti, capellini,
  lasagne sheets and cannelloni tubes are one row. The trap is that six shape or dish words on Rimi's pasta shelf name
  something else in the database: **GNOCCHI / GNOCCHETTI** (LA MOLISANA's are dried durum dies at 1469 kJ; SAFAD's
  gnocchi rows are boiled dumplings at 744 and 846), **ORZO** (a rice-grain die, not the Italian word for barley),
  **NOODLES / VERMICELLI** (European fine-pasta dies; SAFAD's noodle L3 is Asian noodles and vermicelli appears only
  inside soup rows), **TAGLIATELLE / FETTUCCINE** (conventionally egg, but three of the five packs bearing the names
  are durum and water — the egg axis is compositional, never inferred from the die), **LASAGNE / CANNELLONI** (dry
  sheets against assembled sauced dishes at 502-749 kJ) and **MAC & CHEESE** (LUBELLA prints it on a bag whose only
  ingredient is wheat flour). The ingredient list settles every one, so none of them costs a letter — but a model
  given only the name will reach for the homonym, which is a miss worth being able to tell from a real one.
- **Some rows hold a cooked food's nutrition under a dry food's name. Check the energy before trusting the name.**
  **Six** cases are now known. Three are in the grain and pasta branches and share one shape: the row's whole 25-field
  nutrition block is identical to a neighbouring *cooked* or *dehydrated* row, while its environmental columns
  differ — so these are modelled as distinct foods whose nutrition was copied from the wrong neighbour, not as
  accidental duplicates. The consequence is that the LCA layer can tell them apart and the nutrition layer cannot,
  so no numeric sanity check over nutrition alone will ever surface them.
  **`Oatmeal flakes`** (291 kJ) carries `Oat flakes, boiled/cooked in water`; dry rolled oats are ~1550 kJ, and the
  row that actually holds them is `Oat flakes, pre-cooked, raw` — the two names are exactly inverted, so the
  obvious name match for "avižiniai dribsniai" is wrong by 5,3×.
  **`Wheat, whole, raw`** (646 kJ) carries `Durum wheat pre-cooked, whole grain, cooked, unsalted`; raw wheat grain
  is ~1440 kJ, which the neighbouring `Durum wheat, whole, raw` holds correctly.
  **`Asian noodles, plain, cooked, unsalted`** carries `Asian noodles, flavoured, dehydrated`, salt included —
  1570 kJ and 0,99 g of salt on a row whose name says cooked and unsalted. `lt/23` pinned the mechanism: that row
  holds AGB code 9086 (`pasta. rice and grains. cooked`) but CIQUAL code **9863**, which is the *dry* entry's code
  and the one the dehydrated row also carries, and its own `food_name_CIQUAL` still reads "Asian noodles, **dry**,
  with wheat and egg". So the join key for nutrition was the wrong one while the environmental join was right —
  10 of its 17 `E_` columns differ from the dry row. That is worth knowing generally: **when a row's nutrition
  looks borrowed, compare `CIQUAL-code` against its neighbours' before deciding whether it is a real food.**
  **`Tomato sauce for pizza base` and `cream sauce for pizza base`** are the other two, found on `lt/24`, and they
  are the same defect with a different neighbour: they store 983 and 1100 kJ on 2,7 and 2,6 g of fat with **6,9 and
  7,7 g of protein**, which is a raw pizza *base* (about 270 kcal, 8 g protein, 2-3 g fat, 50 g carbohydrate) and
  not any sauce. The rows are named "<X> sauce for pizza base" and the nutrition joined on the base. The CIQUAL
  diagnostic names it again: both carry codes in the 37000 composite-dish range, while `Topping sauce for pizza` —
  the same food as the first of them, sharing its SAFAD code `I.16.08.010.009` — carries 25525 in the sauces range
  and a credible 174 kJ / 0,75 g fat / 2,04 g protein. The environmental join is right in both: 0,98 against 0,94 kg
  CO2e/kg and 67,4 against 67,7 of land use for the two tomato ones. **Where a broken row has a sound twin, use the
  twin** — `lt/24`'s one pizza sauce takes `Topping sauce for pizza` and lands at -1%.
  A product whose name reaches a broken row with no sound twin still takes it — that is the row that names the
  food — and the letter records the fault rather than any doubt about the mapping (`lt/21`'s one bag of wheat
  groats, **B**).
  **The sixth case shows the mechanism outright, and it is dilution rather than a wrong join.** `lt/25` found
  `Chicory, powder, instant, non rehydrated` and `Chicory, powder, instant, ready-to-drink` carrying **identical**
  nutrition — 1350 kJ, 2,5 g fat, 21,7 g sugars, 3,3 g protein — on the same CIQUAL code 18152, the same SAFAD code
  A.02.13.002 and the same `food_name_CIQUAL`. They differ in exactly one identifier: the AGB codes read `18152_1`
  and `18152_2`. And the environmental columns *were* diluted — 7,87 against 0,17 kg CO2e/kg, 151 against 3,04 of
  land use, a factor of 46 — so the ready-to-drink variant was derived from the powder with its LCA recomputed and
  its nutrition columns copied over untouched. **An `AGB_code` ending in `_1` / `_2` marks a row split into
  variants, and the split is a place to check whether the nutrition followed.** Here the powder row is the sound
  twin, the drink row should never be used, and the twin rule sends `lt/25`'s two instant chicories to the powder.
  **The seventh case turns the whole class into something countable.** `Oolong tea, brewed, without sugar` holds
  AGB code **18156** — its own LCA dataset — and CIQUAL code **18154**, which is black tea's, so its environment is
  oolong's and its nutrition is black tea's to the decimal. Generalising that one-line test over the database:
  **200 of the 2451 rows have `AGB_code` different from `CIQUAL-code`, 94 CIQUAL codes are shared by more than one
  row, and in 92 of those 94 groups every sibling carries byte-identical nutrition.** Most are harmless, but the
  margarine branch proves the class is a defect and not a design: `Vegetable fat (margarine type), 70% fat,
  unsalted` and the 80% row both declare **72,10 g of fat and 2670 kJ**, so the percentage in the name is invisible
  to every nutrition check. Before trusting a row's numbers, compare its two codes; where they differ, look for the
  sibling they were copied from. The `_1` / `_2` suffix is one shape of this, not the only one.
- **The inverse also happens: a set of rows can share one footprint and differ only in nutrition, in which case
  the axis that splits them is not worth agonising over.** SAFAD's seven wheat flour rows — type 55 (for bread),
  type 55 (for pastry), type 65, type 80, type 110, type 150 and self-raising — hold **identical values in all 17
  `E_` and all 14 `S_` columns**. Only nutrition separates them, and only a little: energy 1450-1510 (a 4% band),
  protein 8,44-11,40. So for a sustainability label the milling type is not a decision at all, and `lt/22` maps
  every white wheat flour on its shelf to one row rather than scattering them across six. Before splitting a
  product across near-synonymous rows, check whether the split changes anything downstream: compare the `E_`
  columns first, and if they are identical, prefer one consistent row and say why in the module docstring.
  **Measured over the whole database, a shared footprint is the norm and not the exception: 1274 of the 2451 rows
  share their `E_climate_change` / `E_land_use` / `E_EF_single_score` triple with a row carrying a different
  `AGB_code`, across 373 groups.** Most are Agribalyse aggregating a coherent family — thirty raw whitefish rows on
  one process, twenty-eight breakfast cereal rows on another — so sharing alone proves nothing. What is worth
  checking is how far apart the members of a shared group are, and ranking the 372 groups by the energy ratio
  between their extremes finds the pathological ones immediately: bottled water against a fruit soft drink
  (x7080), diet against sugared cola (x696), and then **`Saccharin sweetener` against `Sugar, white` at x79,8** —
  21,30 kJ and 1700 kJ on one identical footprint of 0,75 kg CO2e/kg and 31,90 of land use. That third one is the
  widest in the database that is not a 99%-water drink, and it matters because a tabletop sweetener exists to win
  exactly the comparison the database cannot express: 40 mg of saccharin replaces 4 g of sugar, and per kilogram
  the two are identical. `Cane molasses` is the same copy of `Sugar, brown` (1,04 / 90,70 / 0,34). Found on
  `lt/26`; the mapping cannot fix it, the scoring path can.

- **A dehydrated product's nutrition table may be printed for the prepared dish, and which one it is, is a brand
  convention rather than a property of the food. Never let it choose the archetype.** `lt/23` (instant meals) is
  where this becomes the whole shelf: 57 of its 156 packs declare 19-543 kJ per 100 g, the bowl after water, and
  the other 99 declare 721-2040 kJ, the contents of the packet. Nothing lands between 543 and 721, so the two
  populations separate without a judgement call, and salt says the same — of the bouillons that declare the cube,
  the twelve printing a salt figure at all run 18,7-58,9 g, while the ones that declare the broth run 0,8-1,1.
  The proof that it is not the food is inside the shelf: **two Rimi products sold under one display name**,
  `Gr. par. makaronai aštraus sk. SUN YAN, 65 g`, declare 2040 and 404 kJ; and two KNORR 61 g noodles (165628,
  165629) that **share one noodle block** — the same seven-ingredient sub-list at 85% of the pack, character for
  character — declare 1972 and 358, which a seasoning that is a seventh of the pack cannot explain. So the
  archetype is chosen from what the pack *is*, and the basis is then allowed to move only the letter — where the
  two bases agree the numbers decide
  it, and where they do not the row lands at C, which is the honest report that this label could not confirm the
  mapping. The same effect in miniature was already in `lt/20`, where the MAC&CHEESE dry kits sit on a cooked-pasta
  row at C because "the numbers are the water basis and not the food".
- **A row whose `food_product` is the poured drink or the prepared dish cannot grade above C for a pack sold dry,
  even when the mapping is forced and certain.** This is the rule `lt/23` applied to reconstituted soups and
  broths, stated once so it does not have to be rediscovered: a *basis* mismatch is a known material discrepancy
  and therefore a C by the definition of the letters, where a missing *axis* is a B and an axis SAFAD simply does
  not model leaves the letter alone. `lt/25` is where it decides a whole file. SAFAD holds roasted coffee as a
  solid (`Coffee, ground`, 1410 kJ) and holds nothing else on that shelf as one: instant coffee, decaffeinated
  coffee and every coffee-with-milk are stored as the cup at 6,95-90,5 kJ. So 123 roasted-coffee packs grade A on
  the solid row and 72 packs grade C on a drink row, with almost nothing in between — a bimodal file, and an honest
  one. The corollary worth keeping: **a "non rehydrated" or "powder" row is the one that can grade A**, and reading
  the `food_product` label for those two words is the fastest test of whether a dry pack has a home.
  **The tea shelf (SH-2-2, folded into the same file) forced the rule to be stated at its proper width, because
  there C would have been wrong.** SAFAD's entire tea vocabulary is five brewed infusions; there is no dry-leaf row
  anywhere, so nothing was compromised by putting 169 dry packs on a drink — there was no second option to pass
  over. The letter therefore records which of three situations the mapping is in:

  | the pack's state against the row's | letter |
  |---|---|
  | the row is stored in the state the pack sells | **A**, and the numbers decide it |
  | the taxonomy holds *no* row in the pack's state, so the prepared row is the food's only representation | **B**, one axis off |
  | the taxonomy *does* hold a row in the pack's state for that family, and the name's variant forces the prepared row anyway | **C**, a deliberate compromise |

  That keeps every letter in `lt/25`'s coffee half intact — roasted coffee has a solid row and 123 packs in the
  same file use it, so routing an instant or a decaf to the cup is the third case — and it reconciles the corpus
  with itself: `kritis/12` had already graded all thirty-two of its Greek dry infusions **B** on these same rows. The test to
  apply is not "is the row a drink?" but "**does the taxonomy hold this food in the pack's state at all?**".
- **A missing nutrition panel is not always a retailer gap — some foods are legally exempt, and on those shelves
  the mapping has to carry the grade alone.** Regulation (EU) No 1169/2011 Annex V exempts, among others, coffee
  bean whole or ground and its decaffeinated form, tea and tea extract, herbs and spices, salt, vinegar, and
  single-ingredient unprocessed foods. Every earlier file could treat an absent panel as a defect; on `lt/25`
  **112 of 200 packs (56%) print nothing**, which is the norm rather than the exception — 34 of 46 ground coffees,
  27 of 38 bean packs, 36 of 67 capsule packs. Class `'d'` is what makes such a shelf gradable at all, because it
  asks the name and not the numbers. **The tea shelf then went further: 151 of 189 packs (80%) print nothing**, and
  unlike the coffee jars every silent pack there is squarely inside the exemption, which names tea outright. Only
  38 tea packs declare anything at all. Expect the spice shelf (SH-2-16) to be higher still, and do not read those
  blanks as a scrape failure. Note the exemption's edges: it names coffee *bean* and *tea* extract, not coffee
  extract, so the 15 of 21 instant-coffee jars on `lt/25` that print nothing are outside it — recorded as an open
  question rather than explained.
- **`extract_det.py`'s SUSPECT MACROS flag false-positives on high-fibre foods, and SAFAD's own
  `H_fibers_g_100g` is how to acquit them.** The implied-energy estimate counts fat, protein and carbohydrate and
  says so; fibre at 8 kJ/g is invisible to it. On `lt/25` that flagged **33 of the 88 declared panels** — a ground
  coffee declaring 1417 kJ over macros worth 1011, an instant declaring 493 over 193 — and none is a fault:
  `Coffee, ground` stores **39,7 g of fibre per 100 g**, worth 318 kJ, and 1011 + 318 lands at 1329 against 1417.
  Before writing up a SUSPECT MACROS shortfall as a panel fault, look up the archetype's fibre column; the same
  will apply to bran, psyllium, dried legumes and chicory (29,2 g) when those shelves are built.
- **A word that partitions one shelf exactly may not partition the next one at all, so test the partition before
  writing the assertion.** `lt/22`'s `mišinys` (mixture) split its shelf 26 against 26 and became a biconditional
  in `check()`. The equivalent word on `lt/23` — `makaronai`, noodles — does not: six listing cards abbreviate it
  to `mak.`, three noodle cups name no noodle at all, and fourteen rows that are *not* noodles carry the word —
  nine durum pasta kits, three soups with pasta *in* them and two sauces *for* pasta, the last in the dative
  `makaronams`, which is itself a usable signal: a dative marks a sauce. `lt/23` asserts the four axes that do
  hold exactly (`sultinys` ↔ broth, `košė` ↔ potato flake, `makaronams` ↔ sauce, `ryž` ↔ rice noodle) and records
  in the docstring why the obvious one was not written. An assertion that cannot be made is worth documenting as
  loudly as one that can.

## Reliability grades

Purely an eval *reporting* dimension. `assessment-eval.js` reads the column only to bucket rows
(`assessment-eval.js:522`) and print `By match reliability (mean score / archetype hit-rate)` at
`assessment-eval.js:579`. `scoreRow` ignores it entirely, no Java/SQL/Liquibase reads it, and it does not move the
headline strict-match number. Its job is to answer *"when the model missed, was our ground truth solid?"*

- **A** — the name names the type and SAFAD has that exact type; composition confirms it.
- **B** — the type is right but one axis is off (a fat bracket, a species detail, a missing qualifier), or the answer
  needs domain knowledge a well-informed annotator would have.
- **C** — a deliberate compromise: the closest available archetype, with a known material discrepancy.
- **blank** — no archetype fits.

Grade the *confidence in the mapping*, not the size of SAFAD's gap. Egg husbandry is invisible to SAFAD, but there is
only one whole-hen-egg row, so those stay A with the gap in Comments.

**Where a category runs to well over a hundred rows, derive the grade from a stated rule instead of typing a letter
per row.** In `11_vegan` each product carries a *class* — forced (one row exists for the crop and form the name
states), normal (the row was picked from candidates), crop-or-form mismatch, or an explicit override — and
`vg_grade.py` turns that plus the mechanical comparison into the letter. 174 hand-typed grades are not auditable;
a class plus a rule is, and the handful of genuine exceptions then stand out as explicit overrides with the reason
in Comments. Two refinements that came out of it and generalise:

- **A fat difference under 1,0 g per 100 g is immaterial** — it moves neither a footprint nor a score, and a
  percentage on the 1,1 g of an oat-drink archetype decides nothing. Use the absolute gram gap there, not the ratio.
- **When the mapping is forced, grade on energy alone.** The single row per crop is calibrated to a different
  reference recipe and that shows up almost entirely as fat; energy is comparable across recipes. State the fat gap
  in Comments instead of letting it drive the letter.
- **A fat-only miss on a correct type is B, not C.** `12_bread_pastry` first graded it C and put 189 of its 527
  rows there, which misreads the evidence: across all 527 the median energy gap is -2% and the median fat gap +5%,
  with 286 rows over the archetype and 235 under, so the mapping is *centred* and the fat spread is the products',
  not the mapping's. Energy decides whether the type is right (it is dominated by flour and sugar); fat then decides
  A from B. Reserve C for a row of the wrong richness (energy out by more than 30%) or the wrong kind (fat out by
  more than a factor of two).
- **An energy floor looked necessary, was built and measured, and is not.** SAFAD's five brewed-tea rows carry
  1,70 to 21,70 kJ per 100 g, so a pack declaring 17 kJ is +217% on the black-tea row and **12 kJ**, three
  kilocalories, away from it — a difference the EU itself calls energy-free (a drink under 4 kcal/100 ml may be
  labelled so). `lt/25` recorded that the first file to grade a near-zero archetype mechanically would have to add
  an energy floor of the same shape as `FAT_FLOOR_G`. `lt/26` looked like that file — `Saccharin sweetener` is
  21,30 kJ and the twelve packs on it run from 4 to 1434 — and it is not, so the trigger is **retired**. A floored
  variant was run against all 5267 graded rows of the built Lithuanian corpus before being discarded: a 50 kJ floor
  with a 100 kJ denominator floor moves 42 letters, a 30 kJ floor moves 18, and a 200 kJ denominator floor on its
  own moves 28 — **and not one of the changes falls on `lt/25` or `lt/26`, the two shelves that motivated it.**
  `helperscripts/probe_energy_floor.py <floor> <base>` re-runs the measurement and prints every letter that moves.
  The reason none of them lands here is that the sweetener gaps are not measurement artefacts: a DIAMANT tablet
  really is 113,7 kJ/100 g above the row and a CANDEREL tablet 1412,7, and that is the lactose and dextrose the
  tablet is pressed from. What the floor would
  actually move is canned tomatoes (120 against 95,10 kJ: +26%, and six kilocalories) and plant drinks, and among
  the plant drinks it would grade an ALPRO coconut-almond drink **A** at double its archetype's energy. So the
  percentage-on-a-small-base problem is real but it is not the sweetener problem, and fixing it would re-grade
  finished files to buy a handful of upgrades of mixed quality. The probe is worth re-running before anyone acts on
  this; do not add the floor on the strength of the tea shelf alone.
- **Take the fat percentage against a denominator of at least 2,0 g.** The 1,0 g floor above has too sharp a cliff
  for near-fat-free archetypes: `01_milk_and_dairy` (lt) put five 2,3% flavoured milks at C purely because 1,4 g of
  fat over a 0,89 g archetype computes to +157%, while the same products sit at B in the Estonian file. Below about
  2 g per 100 g a ratio measures the base's smallness, not the difference, and the saturated-fat points are 0 either
  way. `grade.fat_penalty` implements it; the change only ever promotes C to B on archetypes under 2 g of fat, and
  leaves the seeded ryes on the 1 g rye row at C, where they belong (3 to 9,8 g over a 2,0 g base).
- **Where SAFAD has no axis the numbers could move the mapping along, absent or impossible pack data cannot lower
  the grade.** The class machinery defaults a forced mapping with no pack figures to B, on the reasoning that we
  could not confirm it; that reasoning fails where there is nothing to confirm. A carton of hen eggs has exactly
  one row it can take whatever its table says, so the four Lithuanian packs with no usable table carry an explicit
  A with the reason in Comments, while the seven that do declare one grade A under the rule unaided (+0,0% to
  +12,9% on energy) — which is the evidence that the name was enough.
- **A pack panel that contradicts itself is evidence about the retailer, not about the mapping.** `Kaimiški
  kiaušiniai RIMI` declares 817 kJ next to 0 kcal and 30 g protein against the raw-egg row's 12,8 — internally
  consistent at 30 g protein, so the whole table was computed from one wrong figure. Grade on the name, say in
  Comments that no figure in the table can be believed, and do not let a retailer's typo produce a B. The cheap
  tell is an energy pair with one side zero; the second is a macro that no product of that kind could hold.
- **Where the taxonomy is a list of named types, the grade is decided by what the *name* reaches, not by how
  close the numbers land.** SAFAD's cheese branch is 132 rows and almost every one is a named European cheese:
  there is no generic semi-hard cow cheese, no generic hard cheese. A Lithuanian shelf is mostly local block
  cheese under a dairy's brand, so `02_cheese` (lt) grades in three tiers, and the class carries the tier:
  - **`'d'` declared → A.** The name states a type SAFAD has and SAFAD has one row for it, so there is nothing to
    doubt: *Gouda*, *Čederio*, *Maasdam*, *Camembert*, *Brie*, *Mozzarella*, *Fetos*, *Parmigiano Reggiano*,
    *ožkų pieno* (goat). Any gap then measures an axis SAFAD does not carry — a grated low-moisture mozzarella is
    +41% on the fresh-mozzarella row, an aged goat cheese +43% on the single goat row — and belongs in Comments,
    not in the letter.
  - **`'B'` → B.** The name states a *category* that decodes to one row without naming it (*olandiškas sūris* →
    Gouda, *mėlynasis pelėsinis sūris* → the generic cow blue, *sūris salotoms* → the cow feta-type, *avių pieno
    sūris* → the ewe semi-hard), or names a type SAFAD has and adds a flavouring the row has no axis for.
  - **`'x'` → C.** The name is brand-only (Rokiškio, Dvaro, Svalia, Atleet, Germantas, Džiugas, Senojo Dvaro), or
    states a type SAFAD has no row for at all (Tilsit, Havarti, halloumi, Tête de Moine, Wensleydale, burrata,
    string cheese). The target row is then a specific *different* cheese, which is what C means.
  The B/C line is whether the target row is **generic for that family** — a specific blue on the generic blue row
  is B, a Tilsit on the Gouda row is C. That tiering put 108 A / 99 B / 133 C on a 346-row shelf, and it is the
  honest picture: a third of a Baltic cheese shelf has no archetype that names it.
- **The cheese `'d'` rule does not extend to meat cuts, and the reason is that trim is an axis SAFAD does
  model.** Fresh meat looks like the same situation — SAFAD's beef L3 is 48 rows of named cuts, so a pack saying
  *antrekotas* declares its row as surely as one saying *Gouda* — but the meat branch carries `lean` and
  `without rind, fat and bone` variants, upper- and lower-half shoulders, and the 5/10/15/20% mince brackets. A
  fat gap there means the row models a different trim, which is a real cost, where a cheese's age has no axis to
  be wrong about. So a named cut is class `'c'`: **A where the composition confirms it, B otherwise, a pack with
  no nutrition table included.** A Baltic pork belly at 53 g of fat stays B on the 20,5 g belly row, which is also
  the letter the Estonian file gave the identical product. A cut SAFAD does not carry at all is not this class:
  that is a substitution, and it takes an explicit letter — **B** where the parallel row is anatomically the same
  meat (pork neck → shoulder, beef shank → knuckle, lamb shank → leg), **C** where it is a different piece (beef
  brisket → chuck, pork flank → belly, pork bones → shank) or a different state (raw trotters → the salt-cured
  row). A name that gives no cut at all stays C, as it always has.
- **A generic row standing in for an absent species is capped at B, and that is a class now rather than a
  hand-typed letter.** `grade.py` carries `'s'` (substituted): the species the name states is absent from SAFAD
  and a *generically named* row carries it, so the archetype's name is still reachable from the product's —
  which is what separates it from `'x'` — while its inventory describes a different animal, which is why A is
  not available. B while the numbers agree at the A or B level, C beyond, and B where the pack publishes no
  table. The case that forced it is the whiteleg shrimp (*Litopenaeus vannamei*): ten Lithuanian packs on
  `Shrimp or prawn, raw` / `cooked`, seven of them inside 4% on energy, standing a tropical pond crustacean on a
  small cold-water European prawn fishery. The class reproduces all four letters `rimi/et/08_fish` typed by hand
  for the same shapes, which is the evidence that it is the rule those letters were reaching for.
  **The line is the production system, not the species name.** *Pandalus borealis* is missing from those two
  shrimp L3s as well, but it is the same cold-water capture fishery they are built on, so a Pandalus pack is
  graded `'n'` and keeps its A; it is the farmed-tropical-pond substitution that costs the letter. Same test for
  squid: Humboldt squid on the `Loligo vulgaris` rows is another wild-caught cephalopod, so `'n'`.
- **A class that means "the name says so" has to be checked, or it is just a hand-typed A.** `rimi/lt_ch_map.py`
  carries a `DECLARES` table of the type words as the packs spell them and a `check()` that asserts every `'d'`
  row's product name really contains one; `build_gt.py` calls it if a mapping module defines it. It caught two
  cheddars whose packs abbreviate the type to "čed." — which is a declaration, so the token was added — and it
  would catch a brand-only name silently graded A.
- **Reserve the word "against" in Comments for pack-versus-assigned-archetype, and write the claim so it can be
  checked.** `verify_nums.py` re-reads both numbers of every clause from the pack and the SAFAD row. The canonical
  form is `<value> <unit> <nutrient> against <value>` — "0,8 g salt against 0,11" is checked, "salted to 0,8 g
  against 0,11" is not, because the nutrient word precedes its number. The paired form "78 g fat and 3036 kJ
  against 74,5 g and 2850 kJ" is checked as two claims. A clause citing a *rejected* alternative must be phrased
  without "against" — "at 146 kJ to this pack's 171" — since the checker cannot know which other row was meant.
  It reports anything unparsed rather than skipping it, and counts rows whose pack data is absent. **A clause that
  parses but names no nutrient is reported too** (`NAMELESS`): per-*comment* reporting was not enough, because
  "24 g fat against 17,7 and halves the protein — 8 g against 16,5" parses its first clause and used to drop the
  second in silence. Name the nutrient inside every clause, and put it next to its own number — "protein to 16,6 g
  against 24,7" does not parse, "16,6 g protein against 24,7" does. Claims are
  checked **at the precision they state** — "5,6 g protein" against a stored 5,57 is honest rounding and passes,
  while 5,6 against 5,75 fails, because 5,75 would be written 5,8. `rimi/et/01` has now been verified this way:
  73 claims, 0 wrong (2026-09-10). Two of its comments had to be reworded first, both citing a *rejected*
  alternative with "against"; `helperscripts/selftest_grade.py` pins down the matcher and the grade rules.
  Which files have been checked, and what it costs to check the rest, is recorded in
  [`rimi/et/CLAUDE.md`](rimi/et/CLAUDE.md). **All thirty-two Lithuanian files are verified**: 7200 claims, 0 wrong,
  0 unparsed, 0 rows the checker could not see. A file that aggregates several site categories needs the
  per-category detail JSONs merged into one first — run against a single category's file, `lt/01` reported 32 rows
  whose pack data it could not find and checked 5 claims where the merged set checks 38.

## Working method

The scrape-and-map toolchain lives outside this repo, at `~/Sources/ICCS/TealHelix/helperscripts/`, so it survives
reboots: `db.py` (the latin-1 SAFAD loader), `extract_list.py`, `fetch_det.sh`, `extract_det.py`, `grade.py`,
`build_gt.py`, `verify_nums.py`, `terse.py`, `chk.py`, `probe_energy_floor.py`, `make_testdata.py`.
Per-category mapping tables sit beside them under `helperscripts/rimi/`. Cached detail pages go in
`helperscripts/rimi/det/` — a scratch directory under `/tmp` lost 1040 of them to a reboot.

1. **Scrape the category listing.** `curl '<category-url>?pageSize=100'` returns up to 100 products. Larger
   categories need `&currentPage=N` — plain `pageSize=100` silently caps at 100, so always check for
   `currentPage=` links in the HTML. Product name/id/subcategory come from `data-gtm-eec-product='{...}'`
   attributes (**single** quotes); the product URL is the following `href="/epood/en/products/.../p/<id>"`.
   The attribute's `category` is only an opaque code (`SH-12-5-28`); **the readable subcategory is in the product
   URL's own path** (`/products/fruits-vegetables-flowers/fruits/tropical-fruits/<slug>/p/<id>`), because Rimi
   loads its subcategory nav dynamically and it is not in the listing HTML.
   **But neither the URL path nor the gtm code tells you where a product is shelved.** Both give the product's
   *primary* home, not the category you are browsing: of the 105 products listed under
   `crispbread-rice-cakes/SH-6-4`, 32 carry a URL under `groceries/special-diet-products/keto-and-raw-snacks` and
   a gtm code of `SH-13-1-7`. Use the path to *read* a subcategory, never to reconstruct which category a product
   belongs to — and to check whether a subcategory scrape adds anything to a parent scrape, diff on **Source
   URLs**, which is exact. SH-6-4 turned out to be fully covered by the SH-6 scrape: 0 new products.
   **Enumerating the subcategories is the only way to prove a parent listing complete, and it takes probing.**
   Two obstacles. First, the browsable subcategory codes are not contiguous and not all codes are browsable:
   `SH-6` has exactly four subcategories in its nav — `SH-6-1`, `SH-6-3`, `SH-6-4`, `SH-6-6` — while its products
   carry gtm codes like `SH-6-2-10`, `SH-6-5` and `SH-6-7-21` that no listing URL answers to. Second, **the slug
   must match the code**: `bread-pastry-confectionery/c/SH-6-4` returns 0 products, not an error, and only
   `bread-pastry-confectionery/crispbread-rice-cakes/c/SH-6-4` works. **But the slug does not have to be guessed:
   a valid code under a wrong slug answers 301 with the canonical URL in `Location`, and a code that does not
   exist answers 404.** So the nav can be read off the redirects — sweep the code range with a junk slug and
   collect what comes back:
   ```bash
   for c in $(seq 1 32); do curl -s -o /dev/null -w "SH-9-8-$c %{http_code} %{redirect_url}\n" \
     "https://www.rimi.lt/e-parduotuve/en/products/meat-and-fish-/fresh-meat/zzz/c/SH-9-8-$c"; done
   ```
   That returned `fresh-meat`'s whole nav in one pass — lamb (20), beef (21), pork (22), offal (25), rabbit (26),
   veal (27) — where trial slugs had found four. Two lessons come with it. **A code inside a parent's numbering
   need not belong to that parent**: `SH-9-8-24` redirects to `fresh-poultry/ground-poultry`, a different
   category's subcategory sitting in the middle of this one's range. And **a subcategory in the nav can be empty**:
   lamb and veal both probed 0 products with a 200 and a full page, while a boneless veal leg sat on the shelf
   under `rabbit`. Do not read an empty subcategory as a scrape failure, or a populated parent as proof that its
   nav is fully populated. **The sweep is not a complete listing of a parent's children either, so cross-check its
   total against the parent scrape.** `SH-9-12` (fresh poultry) answered 404 for every code from 1 to 32 except
   `SH-9-12-5` (goose, and empty): its real children are `-38` duck, `-39` turkey and `-41` chicken, past the end of
   the range the fresh-meat sweep needed, while its fourth child `ground-poultry` carries the code `SH-9-8-24` from
   the *meat* numbering. **The authoritative source of a subcategory code is the listing itself** — every product's
   `data-gtm-eec-product` attribute carries the code it sits under, so `extract_list.py`'s `cat` field enumerates the
   populated children exactly, and the redirect sweep is for finding the *empty* ones the products cannot reveal.
   Read the codes off the products, sweep for the rest, and require the two to agree with the parent count: duck 6 +
   turkey 4 + chicken 36 = 46 = the parent listing, with goose and ground-poultry empty.
   **The sweep can overcount as well as undercount, and then the excess is not a finding.**
   `meat-and-poultry-products-/SH-9-24-1` has fifteen children in the redirect sweep totalling 432 products
   against a 429-product parent. The extra three are a seasonal shelf — `festive-processed-meat-products/SH-9-40`,
   two skilandukai and a truffled fuet — every one of which already sits in the parent listing under its own
   `SH-9-14` or `SH-9-22` code. A marketing shelf gathers products from its siblings the way a diet label does, so
   resolve a sweep total above the parent with a **URL diff**, not with the count: here the diff was 0 new
   products. And **a category's children need not carry its code at all**: these fourteen are `SH-9-2`, `-3`, `-5`,
   `-6`, `-9`, `-11`, `-13`, `-14`, `-17`, `-19`, `-20`, `-21`, `-22` and `-41`, numbered straight off the `SH-9`
   root beside their own parent `SH-9-24-1`, and the same range also holds `fish-products`, `fresh-meat`,
   `fresh-poultry` and a prepared-dishes shelf from another tree.
   **A code inside a parent's numbering can belong to another parent and not be rolled up at all.**
   `fresh-fish-and-seafood/SH-9-16` has three children in its products' own codes — `-10` seafood, `-50`
   all-the-fish, `-51` fish-fillet — and they sum to 13 + 11 + 16 = 40, the parent listing exactly. The redirect
   sweep also finds `SH-9-16-49`, which is real and holds 10 products, but its 301 sends it to
   `marinated-meat-and-fish/marinated-fish`: a marinated-fish shelf hanging off the fresh-fish numbering, and
   none of its products appear in the `SH-9-16` listing. Its real parent `marinated-meat-and-fish/SH-9-15` is
   now scraped as `lt/06`, which confirmed the reading from the other side: 79 products, four children
   summing exactly to the parent, and 0 of the 79 already held by any other Lithuanian file. So a shared code prefix proves nothing in either
   direction — read the canonical slug out of the redirect, and treat a foreign parent as a foreign category
   rather than as something the parent scrape missed. **Probe with
   `helperscripts/probe_subcats.sh`, which checks the HTTP status and body size before counting**: `curl -s | grep -c`
   reports 0 for a throttled request exactly as it does for an empty category, and a silent 0 reads as a structural
   finding. `sour-cream-/c/SH-11-2-1` probed 0 one minute and 24 the next. **And an empty subcategory does not
   mean the shop lacks that kind of product.** `fish-products/SH-9-1` has ten children in its nav and six with
   products in them; the four empty ones are `crab-sticks`, `salted-pickled-fish-fish-in-oil`, `dried-fish` and
   `fish-spreads-and-pates`, while the shelf carries twelve crab-stick packs, dozens of oil-packed salted
   fillets, thirteen dried snacks and seven spreads — all shelved under sibling codes. The nav name is a
   marketing intention; the products' own codes are the fact. **The probe lies at the other end too**:
   it asks for one page, so a subcategory holding exactly 100 products is truncated rather than counted, and the
   sweep total then falls short of the parent by whatever was cut off — `yogurt-with-condiments/SH-11-4-8` probed
   100 against 115 real, and 255 against a 270-product parent. The script now marks such a row `AT THE PAGE LIMIT`
   and reports the total as a lower bound; walk that subcategory's own `currentPage` before comparing. Worth doing
   once per category: `SH-6`'s four subcategories hold 136 + 105 + 209 + 77 = 527 products, exactly the parent
   scrape with nothing on either side, which is proof the `pageSize`/`currentPage` walk missed nothing. Do it
   **immediately after the parent scrape**, before any mapping — a subcategory that turns up new products is far
   cheaper to fold in then than after the rules table and the comments are written.
2. **A retailer category can hold non-food.** `fruits-vegetables-flowers/SH-12` carries 28 bouquets and potted
   ornamentals in its `flowers` subcategory. Drop them, assert the count you dropped, and assert no surviving
   Source URL contains the excluded path — do not leave it to a hand-filtered list.
3. **Diff Source URLs against every existing file for that retailer** before writing. Retailer categories overlap:
   9 kohupiimakreem rows in Rimi's dessert category were already in the milk file.
   **Some retailer categories are a diet label rather than a food type, and then they overlap by construction.**
   `vegan-products/SH-17` gathers 174 products from across the shop, and 11 of them carry a subcategory code from
   another tree entirely (10 tofu and tempeh under `soy-products/SH-13-1-6-2`, one pizza under
   `frozen-foods/SH-4-5`). It happened to collide with none of the ten existing rimi files, but that is luck, not
   structure: whichever file is written first owns the row, and the next scrape of `soy-products` or
   `frozen-pizza` will find its tofu and its vegan pizza already taken. The URL diff handles it; just do not read
   an empty overlap as evidence that the categories are disjoint.
   **Rimi reuses product ids across countries, so diff on the full URL and never on the id.** The four Protein Lab
   drinks carry ids 818201/818203/818220/818221 in both `rimi.ee` and `rimi.lt`; they are the same manufactured
   product on two shelves in two languages, which is two ground-truth rows, not a duplicate. A language's files are
   built independently — the Estonian and Lithuanian names share no vocabulary, so nothing is gained by correlating
   them, and an id-keyed diff would silently drop the second language's row.
   **Run the id diff anyway, as a read-only control rather than a filter.** It costs one pass and it is the only
   way to find the same manufactured product already mapped in the other language: the Lithuanian frozen-gastronomy
   shelf shares five ids with `rimi/et/11` and no URL with anything, and comparing the five turned up one row the
   Estonian file can improve (a vegan cordon bleu on the plain vegan-schnitzel row where SAFAD has a cordon-bleu
   row) and one place where the two files MUST differ (see the Gyoza note in the frozen-gastronomy rules). Never
   let it drop a row.
4. **Fetch each detail page.** Ingredients and nutrition sit in a JS `tabs[]` array, `identifier: 'details'`, as a
   JSON-escaped `html: "..."` string — unescape with `json.loads('"' + s + '"')`.
   **A cached page with no `tabs[]` at all is not a pack without a panel: Rimi sometimes answers a product URL
   with its own home page.** One frozen pizza came back as 106 KB of the shop front — HTTP 200, the generic
   `Rimi e-store | Daily Groceries…` title, no `tabs[]`, no `Nutrition`, no `Ingredients` — and
   `extract_det.py` reported it as `no details for 1`. Deleting the cached file and re-fetching the same URL
   returned the real page and a full panel. So treat every `no details` id as a re-fetch candidate before
   writing a `no nutrition table` comment: check for the generic `<title>` and the absence of `tabs[]`
   together, and re-fetch once.
   **rimi.lt is the same software with two differences that break a parser**: the shop path is `/e-parduotuve/`
   rather than `/epood/`, and decimals are written with a point (`3.2 g`) where rimi.ee writes a comma (`3,2 g`).
   Field labels are English in both. One thing rimi.lt does better for our purposes: its names lead with the head
   noun and put the brand in capitals — `Pienas MARGĖ, UAT, 3,2 % rieb., 1 l` — so the food word is the first
   token, where Estonian often buries it (`Täispiim Alma 3,6-4,2% 1l`).
5. **Classify from ingredients and nutrition, never from the retailer's own subcategory.** Rimi's is unreliable and
   sometimes inverted: its "milk" category held dulce de leche and pure flavoured-sugar straws; in `creams/SH-11-5`
   the "Creams" subcategory holds the sour creams and "Sour cream" holds the whipping creams; its "desserts"
   category holds 39 pot yoghurts and 5 rice puddings.
   It is occasionally right, and checking is cheap: in `grill-and-blood-sausages/SH-8-1` its four subcategories
   (`raw-sausages-oven-sausages`, `grill-sausages`, `blood-sausages`, `pre-cooked-meat`) agreed with the
   nitrite test on 61 of 65 products. Use it as a hypothesis to confirm, never as the answer.
6. **Distrust the retailer's nutrition table.** Recurring errors: 0 g saturates or salt on products that plainly
   contain them, 0 g fat on a 100%-pork cut, 43 g carbohydrate on a cream cheese, an energy value inconsistent with
   its own macros. Note the error in Comments rather than mapping to it.
   **Six of those are cheap to detect, and `extract_det.py` now reports all six rather than leaving them to be
   noticed while a comment is being written.**
   - **The panel's own kJ and kcal figures cross-check for free**: kJ = 4,184 × kcal, so a pair that disagrees by
     more than rounding is a broken cell. It catches the transposed pair (a beef shank printing "133 kJ/ 554 kcal"),
     the zeroed half (`0 kJ/ 152 kcal`, and the egg pack's `817 kJ/ 0 kcal`) and any panel whose two halves were
     computed from different recipes.
   - **The macros then say which half to believe** — 37 kJ/g fat, 17 kJ/g protein and carbohydrate. The shank's
     4,6 g of fat and 22,6 g of protein imply 554 kJ, so its units were swapped and the energy is recoverable;
     the Rimi brie's 1361 kJ matches its own macros and only its kcal side is wrong. State the recovered figure in
     Comments but **never feed it to the grader** — the letter is computed from what the retailer printed, and a
     silently repaired panel is worse than a flagged one. Where the printed figure would *move the letter*, the
     row takes an EXPLICIT letter instead, equal to what the sound half of the panel earns, and says so: see the
     fish shelf's sibling rule and its general form on `lt/32`, where six rows need it. That keeps the repair out
     of the grader while stopping a retailer's typo from being recorded as a fact about the mapping.
   - **The declared energy also cross-checks against the panel's own macros, and that catches what the kJ/kcal
     pair cannot** — a panel whose two halves are wrong *together*. Four counter-sold Lithuanian packs print an
     energy exactly 4,184 times too high, having read their own kJ figure as a kcal one and multiplied it: a
     hot-smoked belly at "3841 kJ/ 918 kcal" over 18 g of fat and 14 g of protein, a dried ham at 3565/852, a
     smoked chicken breast at 2084/498 and a boiled sausage at 3715/888. The pair agrees with itself in every one,
     so only the macros can see it. **The check says a cell is wrong, never which one**: with those four it is the
     energy, with a cured back fat at 3364 kJ over 38 g of fat and an 80% butter at 2980 kJ over 55 g it is the fat
     cell. Atwater models neither fibre nor polyols nor alcohol — fibre pushes the declared figure up, polyols pull
     it down — so the tolerance is wide, and it was set by measurement: over 1480 cached packs every gap at 40% or
     more was a broken panel and the widest honest one was 34%, a Greek yoghurt 84 kJ above its own macros. Below
     that threshold the band fills with dairy panels a few tens of kJ out on a small base, which cannot be
     adjudicated either way.
   - **The macro-energy check's floor belongs on the LARGER of the two figures, and putting it on the macros
     hid the opposite fault for a while.** The floor exists so a small absolute difference on a small base does not
     read as a contradiction — but applied to the macros alone it also skipped every panel that declares a *big*
     energy over *tiny* macros, where the ratio is measuring a real absolute gap. Three such panels had passed
     unremarked: a 200 g spinach pack at 598 kJ and a rocket pack at 602 over macros worth 125 and 133, and a
     garlic at 623 kJ printing 0,2 g of carbohydrate where garlic holds 28. Over 5010 cached packs the corrected
     floor adds exactly those three and nothing else — no false positive — and it also now catches by rule the
     cured pork loin declaring 2 g of protein that the sausage file had found by hand.
   - **A panel with the same non-zero figure in every cell is a placeholder, not data.** Rimi prints 1 for energy,
     fat, saturates, sugars, protein and salt on some counter-sold meat. All-zero is a real panel (bottled water),
     so the check requires the repeated figure to be non-zero.
   - **Arithmetic impossibilities**: more saturates than total fat, or over 100 g of macros in 100 g. These hold
     for every food, so they are decided rather than flagged. The sweep found one in 1051 cached Lithuanian packs —
     a Gorgonzola Dolce declaring **0 g fat beside 0,9 g of saturates and 19,4 g of carbohydrate**, on an energy
     pair that agrees with itself at 1346 kJ / 325 kcal.
   - **Saturates claiming over 90% of the total fat is a flag, not a verdict.** Coconut oil (86%) and palm kernel
     (82%) come close and a fully hydrogenated fat would pass it honestly, but milk fat runs about 65%, and every
     pack this has caught is a dairy panel whose saturates cell is repeating the total: a 10% coffee cream at
     9,9 g, a 35% UHT cream at 33,3 g, a curd snack printing 17,4 g in both cells, a smoked curd cheese at 20,5 g
     of 22. **On a coconut-fat shelf the check inverts**, and that is worth knowing before chasing its hits:
     nine of the ten it fires on the Lithuanian plant-based shelf are honest coconut oil at 90-96% — five cheese
     analogues, a condensed coconut cream, a coconut yoghurt, a hydrogenated-coconut dessert and a whippable
     plant fat blend that is *fully hydrogenated coconut and rapeseed*, which is the docstring's own
     "a fully hydrogenated fat would pass it honestly" case appearing for the first time. Only the tenth, an oat
     barista drink putting 2,8 g in 3 g of **rapeseed** oil, is a fault. So the flag's yield depends entirely on
     the shelf's fat: near 100% on dairy, near 10% here. **The ice cream shelf measures the middle of that
     range**: nine hits, of which the four that name coconut fat in the ingredient list are honest at 90-91% and
     the five that name only cream, milk or a palm blend are the cell repeating the total. So the discriminator is
     sharper than "which shelf" — it is **whether the ingredient list names coconut or palm-kernel fat**, which is
     a check the ingredient text can answer and the ratio alone cannot.
   - **The check's 2 g fat floor was measured and kept.** An almond-flavoured rice drink repeating its total fat
     as its saturates — 1,6 g in 1,6 g, on sunflower and safflower oil that runs about a tenth saturated — slips
     under it, which prompted the question. Lowering the floor would surface 21 panels across 4794 cached: that
     one, and twenty rounding artefacts at 0,1 g or the placeholder-adjacent 1,0 g (sauerkraut, curd snacks, a
     beef shank, a rabbit carcass). One real fault against twenty noise rows is the wrong trade, so the floor
     stays and the rice drink is recorded in its own row's Comments. The docstring's "fats under 2 g say nothing
     at any ratio" is right in aggregate and wrong in one place, which is the most a threshold can be.
   - **The ratio cannot be automated further than that.** A pork-and-beef mince declaring 12,6 g of
     saturates in 20 g of fat — 63%, where neither pork fat (39%) nor beef fat (50%) reaches it and the archetype
     itself stores 30% — passes every check in the file, because the threshold that would catch it would also
     condemn butter. That one was found by reading the row against its own siblings, which is still the method the
     automated checks only narrow.
   - **A saturates cell far too LOW is the seventh shape, and it is deliberately not automated.** The >90% check
     has no counterpart below, and the reason is that the flag would fire on 4,3% of the shelf: **71 of the 1668
     Lithuanian packs that declare 2 g of fat or more put under 5% of it in the saturates cell**, and 68 of those
     put exactly 0,0 — butter at 82 g of fat, President Brie at 32, a liver pâté at 28,6, herring fillets in oil
     at 38, marinated pork neck steaks at 18. No real food reaches that low (safflower oil, the leanest in
     saturates anywhere, is 6%), so all 71 are a cell Rimi never filled in rather than a figure that is wrong. A
     check firing 71 times would drown the six that fire once or twice, and it could not move a letter in any case,
     since saturates are not in the grade. So the rate is recorded here and the individual cases in their own row's
     Comments — the same call the 63% mince forced in the other direction.
   The rate is worth knowing: 10 flags across the 909 products of the nine finished Lithuanian dairy categories
   (1,1%), against **8 of the 69 on the fresh-meat shelf** — five self-contradicting pairs and three placeholders —
   **5 of 46 on fresh poultry**, **none of the 27 on ground meat**, **18 of the 429 in meat-and-poultry-products
   (4,2%)** — four units-squared energies, four swapped energy pairs, four packs declaring 0 g of salt on cured
   meat, one putting 2 g of protein on a cured pork loin and one repeating its fat cell as its protein — **8 of
   the 253 on fish-products (3,2%)** and **9 of the 79 on marinated-meat-and-fish (11%)**, which is now the worst
   shelf in the tree, **5 on fruits-and-berries — but out of the 17 packs there that publish a table at
   all, since 110 of its 127 carry none** — and **21 of the 89 tabled packs on vegetables-and-mushrooms
   (24%)**, which is the worst rate in the tree measured that way: seven potted herbs from one supplier
   with a broken energy pair apiece, two placeholders, one record carrying another product's ingredients
   and panel outright, and three that only the corrected macro-energy floor could see — and **4 of the 129 on
   plant-based-products (3,1%)**, where for once every pack publishes a table: a swapped unit pair, a rice
   drink's understated carbohydrate cell, a kcal cell 7% out, and the oat barista whose fault the Estonian pack
   of the same product carries identically, which makes it the supplier's — and **8 of the
   278 tabled packs on bread-products (2,9%)**: three sums over 100 g in 100 g (100,8 to
   101,5, too large to be rounding and all three on dry baked goods that must still hold
   water), a black bread declaring 1009 kJ against 313 kcal where its own macros compute
   1317, a butter croissant declaring 0,2 g of saturates in 21 g of palm-and-coconut fat, a
   hot dog in mustard sauce declaring 0,0 g of salt, and a savoury pastry declaring no
   protein and no salt at all in a wheat dough — and **14 flags on the 242 ice cream panels
   (5,8%), of which 10 are faults**: nine saturates hits of which five repeat the total fat
   and four are honest coconut, four kcal cells disagreeing with a sound kJ cell, and one
   pair-consistent energy off by exactly 4,184. Two more faults on that shelf were found by
   hand and by no check — a record carrying another product's ingredient block, and a panel
   only a sibling comparison condemns (see the ice cream rules) — and **3 flags on the 147
   frozen-gastronomy panels with 6 more found by hand, 9 faults in all (6,1%)**, which is
   now the worst rate on any Lithuanian shelf whose packs all publish a table. The three
   flagged are one units-squared energy (a koldūnai pack printing 3828 kJ over macros worth
   916) and two kJ cells contradicting a sound kcal cell. **The six the checks cannot see
   are all zeroed cells, and they cluster on one product family**: four meat-dumpling packs
   declare 0,0 g of saturates in 5 to 10,5 g of pork, beef and lard fat, four declare 0 g
   of salt in a dough whose own ingredient list names *valgomoji druska*, and one of those
   declares 0 g of FAT over a filling of smoked pork belly. That is the deliberately
   un-automated seventh shape (see above) firing six times in one category, which is what
   the 4,3%-of-shelf rate that kept it un-automated looks like when the shelf is a fatty
   one. Adding the two saturates checks put 5 more flags on the finished dairy files and the
   macro-energy check 2 more; of those 7, six were already written up in their own row's Comments when the file was
   built — which is the check confirming the hand work — and the seventh, an organic apple-and-grain yoghurt whose
   31,1 g of carbohydrate is 529 kJ on its own against a declared total of 417, is now recorded too. No flag has
   yet moved a letter: saturates are not in the grade, and a broken energy cell is graded as printed.
   **A product's own link has two URL shapes, and requiring the common one silently loses
   products.** `extract_list.py` pairs each gtm attribute with the first
   `href=".../p/<id>"` near it, and that pattern used to demand `/en/products/` in the
   path. A pack with no category path of its own is served as `/en/c/<slug>/p/<id>`
   instead, and **seven filled buns dropped off the Lithuanian bread shelf** — reported as
   `NO URL` on stderr, which is the only reason it was noticed at all. The fix loosened the
   pattern to `/en/…/p/<id>`; what keeps that safe is the product's own id at the end, so a
   card whose only nearby link belongs to another product still matches nothing and is
   still reported. Re-extracting all twenty saved Lithuanian listings with the fixed
   pattern recovered those seven and changed **nothing** in any other category, so no
   delivered file was short. Those same seven have no gtm category and no nutrition panel
   either: one stub cause, three symptoms.
7. **Above roughly two hundred products, drive the mapping from a reviewable rules table**
   rather than a per-product list. `12_bread_pastry` (527 rows) uses an ordered
   (name pattern → archetype key, class) table plus a per-product override file; every product
   must match a rule or an override, and the builder asserts no archetype key goes unused. Then
   review twice, because the two passes catch different faults: sorting by the **energy gap**
   finds the numeric misfits, and grouping **by archetype** finds the errors of kind the numbers
   hide — a wheat loaf sitting among the rye breads, roasted onion (*röstsibula*) read as toast
   bread (*röst*). Order the table narrow-before-broad: flavour words like *kaneel*, *sidruni*
   and *mee* appear in both buns and biscuits, so the biscuit patterns must come first.
   `lt/07_other_meat` (429 rows) is the second file built this way and adds three lessons.
   **Assert that every rule fires, not only that every product matched**: six rules there matched nothing, because
   the products they were written for had picked up an override, and a rule that never fires is either dead weight
   or a mapping that silently moved. **Normalise before matching.** Rimi abbreviates with full stops and drops
   Lithuanian diacritics unpredictably — `K.r.`, `Š.rūk.`, `Šalt. rūk.`, `Šal.rūk.`, `Š.r.` are all the same word
   pair — so fold the diacritics, turn `.` and `,` into spaces and collapse the runs, after which one pattern
   covers all five. **A rule ordered too early is invisible in the totals**: the dried-meat-snack pattern
   `nugarin|filė` was matching the cold-smoked loins two blocks below it and sending them to a jerky row, and it
   showed up not as a bad grade but as a *later* rule that never fired.
   **A shared rule comment must carry no pack figures.** One comment stands for a whole name family, so a number
   in it would be a claim about the wrong pack — put the numbers in the overrides, where `verify_nums.py` can
   check them against the pack they are about. That is why a 429-row file carries only 39 checkable claims where a
   270-row hand-listed one carries 232; the letter, not the comment, is what records the fit of each row.
8. **Build with an asserting script, not by hand.** Every generated file must be validated for: all four category
   columns forming a real SAFAD tuple; one row per listed product, keyed on the real product id; no duplicate or
   cross-file-duplicate Source URLs; reliability present iff an archetype is present. Do not assert on Comments —
   they are optional.
   **A blank-archetype row is checked to the depth it claims, never exempted.** `is_tuple_valid` was padding the
   missing levels out to four and looking that up, which matches nothing, so the assertion passed vacuously on all
   94 blank rows in the tree — a mistyped L1 or L2 on one of them would have gone straight through. It now trims
   the empty levels and checks the prefix, so `Milk and dairy products / Cheese` passes and
   `Milk and dairy products / Cheeses` fails; the 94 existing rows were swept and all hold.
   **Two rows may share a product name, but never with different archetypes.** Rimi lists two different 1 kg Dvaro
   curds under one display name; harmless while they agree, unscoreable if they disagree, since the name is all the
   eval is given. `build_gt.py` now fails on a conflict. Across all 36 files there are 7 duplicated names and no
   conflicts. Four are within one file, two cross two kritis files, and one — `Surimi sticks Rimi 300g`, which
   both shelves sell under its English name — is the only case of a name shared between the Estonian and the
   Lithuanian tree.
   **An assertion over a glob must also assert how many files the glob matched.** The cross-file URL check globbed
   `rimi/*.csv`; moving the files into `rimi/et/` left it matching nothing, so it kept passing while checking
   nothing. `build_gt.py --expect-peers N` makes that failure loud. The same trap sinks any wait loop that polls
   `pgrep -f <script>` — the pattern matches the waiting shell's own command line, so poll a captured PID instead.

## Known SAFAD gaps

Recorded so the same ground can't be re-litigated.

### Whole species that are absent

**Game is essentially missing from the database.** Do not go looking for these again — they are not there:

- **No deer, elk/moose, reindeer or wild boar anywhere.** The `Game mammals` L2 holds two rows, both wild
  rabbit (`Hare meat (Lepus europaeus)`: raw and cooked). That is the entire large-game vocabulary. `lt/32`'s elk
  *koldūnai* and venison *virtiniai* meet it again and lose nothing, because the stuffed-pasta rows name no
  species either — the gap only bites where the pack is the animal.
- `Game birds` is pheasant and quail, one row each — and **both are cooked-only**, so they collide with the rule
  that retail meat always takes the `raw` archetype.
- The only other game entries anywhere are `Liver, rabbit, raw` (`Edible offal, game animals`) and a
  `Game pâté` under `Pastes, pâtés and terrines / Terrine`.

That is six rows in total, and the search behind it was exhaustive: all 2451 rows, every column (not just the
category and `food_product` ones), matching English, Latin and French names — deer, venison, elk, moose,
reindeer, boar, bison, antelope, roe, Cervus, Alces, Rangifer, chevreuil, sanglier, cerf, renne, gibier. The
only other hit was `Taramasalata`, on fish roe.

Consequence: Estonian **hirveliha** (deer) and **põdraliha** (elk) products cannot be mapped species-first at all.
Map them on the product class instead — the game sausages take `Cooked smoked sausage / Morteaux sausage` — grade
**C**, and state in Comments that the species is lost and the row will carry a pork-and-beef footprint. Expect this
again in any fresh, frozen, minced or pâté category that stocks game. It is a species gap, not a cut gap, so
unlike a missing cut there is no nearer row to fall back on.

Blank-archetype gaps next:

- **Whey cheeses** — ricotta, the Greek anthotyros/myzithra/galomyzithra family, Norwegian brunost (which also
  carries 29-30 g sugars from concentrated lactose, unlike anything else in the Cheese branch).
- **Concentrated milk-protein drinks** — Protein Lab bottles, ProFeel milk-protein cocktails.
- **The whole of sports and weight-management nutrition.** No protein bar (22-36 g protein at 2-6 g sugars,
  where cereal bars carry 6-9 g protein and 20-33 g sugars); no protein powder; no isotonic or electrolyte
  drink, powder or tablet; no energy gel. The `Products for special nutritional use` L1 holds exactly five
  rows — dried spirulina and four meal replacements — and the four meal replacements store the **drink**, so
  even a shake powder misses by 400%. On `lt/15` that is **65 of 294 rows** — 50 protein bars and cookies, 6
  protein powders, a meal-replacement shake and 8 gels, tablets and isotonic powders. The bars take a
  `Cereal bars` row as a substitution (class `s`), the whey powders take `Milk, powder, skimmed, non
  rehydrated`, and the gels, tablets and isotonic powders are blank.
- **Fish roe and caviar of every kind** — no roe row exists anywhere in the 2451 rows (the one hit for "roe" is
  `Taramasalata`, a 53 g fat spread), and the fish branch has no offal L2 to hold one. Trout, pink-salmon,
  chum-salmon and lumpfish roe are all blank at L1 + `Fish products` depth. See the roe rules.
- **Imitation roe** — both the salmon-and-gelatine kind (võileivamari) and the seaweed-extract kind (meremari).
- **Mayonnaise-dressed prepared salads** — the `Prepared salads` L2 tops out near 8 g fat.
- **Brushtooth lizardfish** (*Saurida undosquamis*) — no Synodontidae anywhere in the database, and no
  principled nearest species; the one blanked `Fish meat` row.
- **Cultivated mushrooms** — button, brown, oyster, shimeji, enoki. The database holds **one** fungus row in 2451
  (`Morel, raw`, under `Fungi, wild, edible`) and no L2 that a cultivated mushroom could sit in, so these are blank
  at the vegetable L1 only. A *wild* mushroom keeps that L2 and takes `Morel, raw` at C instead.
- **Physalis** (cape gooseberry, *Physalis peruviana*) — nothing under either name; blank at
  `Fruit and fruit products / Miscellaneous fruits`.
- **Sea buckthorn** (*šaltalankiai*, *Hippophae rhamnoides*) — nothing under either name, and nothing else in
  `Berries and small fruits` is an oily-seeded berry to stand in for it. It is 25% of Rimi's frozen berry mix,
  which is most of why that pack is +50% on the mixed-berry row.
- **Edamame** — the immature green soybean. `Soybean, whole grain` is the only whole-bean row and stores the
  **dry** grain (1840 kJ over 36,5 g protein) under an L3 that reads `Soya beans, soaked/boiled/canned`, so a
  hydrated green bean lands 68-70% under it. Same species, so not a blank; nowhere else to go, so a C.
- **Nutritional yeast** (*maistinės mielės*, inactive *Saccharomyces cerevisiae* at 47 g protein) — the only
  occurrence of "yeast" in 2451 rows is inside the name of a French bread. Blank at
  `Vegetables and vegetable products (including fungi)` with no L2, as for cultivated mushrooms.
- **Psyllium husk** (*gysločių sėklų luobelės*, *Plantago ovata*) — no psyllium and no isolated fibre of any
  kind. Blank at `Products for special nutritional use / Dietary supplements`.
- **Konjac (glucomannan) noodles** — a hydrated fibre gel at 131 kJ against the cooked gluten-free pasta
  row's 690. Mapped there and graded C rather than blanked, because it is a pasta by every other test.
- **Legume, nut and seed flours other than soy and chickpea.** SAFAD holds exactly two, `Soya flour` and
  `Chick pea flour` — the latter with an L3 of its own, `Chick pea flour`, which is easy to miss because
  the taxonomy files it under `Legumes, beans, dried` and not under `Grain milling products` with the
  cereal flours. Coconut, pumpkin-seed and tiger-nut (*viksvuolė*, chufa) flour have no row, and the
  defatted ones are unreachable from the whole seed — RIMI's pumpkin-seed flour is -39% on energy and
  -35 g of fat against `Cucurbitacea, seed`, its coconut flour -53% and -57 g against
  `Coconut, kernel, dried`, because the oil has been pressed out of both. Graded `x`.
  Almond and peanut flour are *not* in this gap: they are simply ground nuts, and `Almond, peeled,
  unpeeled or blanched` and `Peanut` fit them to within 3,3% on energy and 3,9 g of fat (`lt/22`, three
  rows, all **A**).
- **Granola** — no row of its own; a baked oat-and-seed cluster is a `Muesli, crunchy, …`.
- **Seed-only keto crackers** — no flour at all, 48-51 g of fat against `Salty snacks, crackers, plain`'s
  23,8. The Estonian shelf's seeded crispbread is the same hole.
- **Haematogen** (*hematogenas*) — condensed milk and bovine albumin boiled to a chewy bar. No blood product
  outside black pudding; `Soft caramel candy` fits it to within 2% and is what it takes.
- **Coffee whitener or creamer, dairy or plant** — already recorded on the Estonian vegan shelf, and the
  Lithuanian functional shelf sells two more (BONA VITA's "instant soy drink" and "cream-flavour soy
  product", both glucose syrup and sodium caseinate).
- **Sprouts of any species but alfalfa, mung/soy and lentil** — pea, leek, sunflower, radish, broccoli and
  red-cabbage sprouts. Neither substitution is acceptable: the parent vegetable's row carries a whole field season
  of footprint a jar sprout does not have, and the three rows that exist name a different species. Blank at
  `Vegetables … / Leaf vegetables`, which is where all three sprout L3s sit. A *microgreen* is different — same
  species, harvested young out of substrate — and keeps its leaf row at B.
- **Wheatgrass** (*kviečių želmenys*, a living tray for juicing) — SAFAD carries no cereal grass; wheat appears
  only as grain, flour and bread, and the leaf is what is used. Blank at the same L2.
- **Aloe vera** (*alavijas*, the fresh leaf sold by the kilo) — no aloe anywhere, and no L2 that
  describes a succulent leaf eaten as a gel: the Leaf vegetables L2 is lettuces, spinach and sprouts and
  its one generic L3 holds a baby-leaf salad mix. Blank at the vegetable L1 only, as the cultivated
  mushrooms are.
- **Lemongrass and lemon balm** — the Herbs L2 has fresh rows for eleven named herbs and neither of these; blank at
  `Herbs, spices and condiments / Herbs`.
- **Sausage casings** — pork intestine, plain or salted. The offal L2 has no intestine L3 and the only intestine
  entries anywhere are French chitterling sausages, i.e. a finished sausage; blank at
  `Meat and meat products (including edible offal) / Edible offal, farmed animals`.
- **A breadcrumb of any kind** — panko, rusk crumb, a corn-flake breading. "Breadcrumb" and "chapelure" occur
  nowhere in 2451 rows. `Rusk` is the same food by construction (wheat bread baked twice and broken up) and
  *džiūvėsėliai* is the Lithuanian word for both, so panko takes it at B; a maize-flake breading has to fall back
  on `Breakfast cereals, corn flakes, plain`, which is the flake before it was seasoned and ground.
- **A starch ("glass") noodle** — mung bean, pea and maize starch, no cereal. `Dried pasta, gluten-free, raw` is
  the generic starch pasta and is what these are by construction, so they take it capped at B.
- **Rice paper**, and **Korean pressed rice cakes** (*tteok*) — both go to `Rice noodle, dried`, the only
  rice-flour pasta row, capped at B.
- **Teriyaki and every other Asian glazing sauce** — teriyaki, oyster sauce, hoisin, and the whole wok-sauce
  shelf. `Barbecue sauce, prepacked` is the only sweet dark savoury condiment in the database and is built the
  same way; 17 packs on `lt/16` take it and all are C, because the type the name states has no row at all.
- **Salsa, taco sauce and chutney** — see the world-cuisine rules for why the tomato-sauce rows cannot fit a
  salsa and the jam rows cannot fit a chutney.
- **A prepared horseradish or wasabi paste** — SAFAD carries horseradish only as the fresh root, so a 24-37%
  horseradish paste with oil, starch and sorbitol is +254% to +359% on it.
- **A seasoning blend that is not Provençal** — taco, fajita, burrito, enchilada, chili con carne, tom kha,
  panang, garam masala, tikka masala, raita, ranch. `Herb and spice mixtures` has three L3s: `Mixed herbs`
  (one row, `Provence herbs, dried`), `Curry powder` (one row) and `Garam masala` — whose only row is a
  prepacked **sauce**, so a garam masala powder cannot take the L3 that bears its name.
- **A pickled chilli** — jalapeños in brine are -39% to -57% on `Chili pepper, raw`, the only chilli row, and
  there is no canned or pickled form. (Botanically a jalapeño is *Capsicum annuum*, the sweet-pepper L3's
  species, but a sweet-pepper row asserts the opposite of what the pack is.)
- **A fried onion** — `Onion, dried` is the only onion product row and carries 0,46 g of fat against a crisp
  fried onion's 44.
- **Rice vinegar, coconut vinegar and spirit vinegar** — the vinegar L3s are wine and apple only, and the
  generic leaf `Vinegar` sits under the *wine* one, so any other substrate is a substitution (`'s'`): the
  leaf's name accepts the pack, the L3 above it does not accept the substrate. The letter then follows the
  data, which is the point of `'s'` — `lt/16`'s JADE BRIDGE white rice vinegar publishes 63 kJ against
  97,1, a -35% weaker brew, and grades C; `lt/19`'s organic DETOX rice and coconut vinegars publish no
  panel at all and grade B. A *maistine acto rugstis*, water plus E260, is not this case: no fermentation
  and no agricultural substrate, which is the one thing every vinegar row is built on, so `'x'` → C.
- **A balsamic glaze or cream (*kremas*, *glajus*)** — cooked grape must thickened with sugar and starch.
  Seven on `lt/19`, and the four that publish a panel run 987-1252 kJ against `Vinegar, balsamic`'s 391,
  i.e. 2,5 to 3,2 times the vinegar. `'x'` → C; it is a different product, not a flavoured vinegar.
- **A pickling marinade** — `Marinatas BAJORISKIU, 3%` is spirit vinegar, water, glucose-fructose syrup,
  salt, spice extracts and caramel. The `Condiment` L2 is a closed list of named sauces and holds no
  marinade, so the vinegar row is the base rather than the product. `'x'` → C.
- **Olive oil below extra virgin** — `Olive oil, extra virgin` is the only olive row in the database, and
  the EU recognises five grades. Nine of `lt/19`'s 37 olive packs are not extra virgin: five are the
  refined-and-virgin blend (CLASSIC, EXTRA LIGHT, unqualified) at **B**, four are *alyvuogiu isspaudu
  aliejus*, olive-pomace oil, at **C** — the bottom grade, solvent-extracted from the pressing residue,
  which an inventory database treats as an allocated by-product stream rather than an oil pressed from
  fruit. The panels are identical across all three grades, so nothing but the name carries this.
- **Walnut oil** — L3 `Walnut oil` exists and its only product row is `Hazelnut oil`. The walnut leaf is
  simply missing, although `Walnuts (Juglans regia)` carries two rows in the nut branch. A walnut oil
  therefore reaches the right L3 and the wrong nut, and composition cannot show it (7 g of saturates
  against 8,34), so it is C by the name alone.
- **Hemp oil** — cannabis is absent from the whole database, seed and oil alike. Blank.
- **Pumpkin-seed oil** — `Pumpkin seeds (Cucurbita pepo var. oleifera)` carries the seed and no oil pressed
  from it, and every other `Vegetable oil` L3 names a different plant. Blank.
- **A malt extract** — `Giros koncentratas GERA GIRA`, barley malt and hop extract for brewing kvass at
  home. No malt extract and no kvass anywhere. Blank at L1 only.
- **A dehydrated pasta-and-sauce kit** — `lt/20`'s three MAC&CHEESE bags are 71% dried pasta and 29% powdered
  cheese sauce, to be boiled. SAFAD's `Pasta with cheese sauce (spaghetti, tagliatelle…)` is the *prepared*
  dish at 681 kJ, so the bag's 1717 sits +152% over it — right about the food, wrong about the water by a
  factor of two and a half — and the `Pasta (Raw)` branch carries no sauced kit. Map onto the dish row,
  because that is the row a name reaches, and take **C**.
- **A plain dry Asian noodle** — `Noodle, wheat flour, without eggs` holds three rows and one of them is
  broken: `Asian noodles, plain, cooked, unsalted` stores the nutrition of `Asian noodles, flavoured,
  dehydrated` verbatim (1570 kJ, 3 g fat, 11,9 g protein, **0,99 g salt**), so a row labelled cooked and
  unsalted is priced as a dry salted one. In consequence there is no usable plain dry Asian noodle row at
  all. `Gnocchi, from semolina, cooked` and `... raw` are duplicated the same way, both at 846 kJ, which
  cannot be true of a dumpling that absorbs water.
- **A plain potato dumpling** — L3 `Potato dumpling, plain` (under Starchy roots and tubers) holds exactly one
  product row, `Fritter, filled with potatoes (filo pastry)`: the walnut-oil shape of gap, a leaf whose only
  occupant is a different food. Potato gnocchi are reachable instead through `Gnocchi, from potato, raw`
  under `Pasta (Raw)`, which is where `lt/20`'s LA MOLISANA potato dumplings go (**B**, -11% on energy).
- **A porridge of any kind** — the single largest gap found so far. No row anywhere in SAFAD contains the word
  porridge, kasha, congee or gruel, and `Cereal-based dishes` has only four L3s (grain patties, cooked pasta,
  pizza, sandwiches), none of which will hold one. `lt/21`'s 39-product "Instant porridges" child therefore has
  nowhere to go. What the products *are* decides the substitute: the 30 dry sachets are oat flakes 67-89% plus
  dried fruit, which is a Bircher muesli by composition, and take
  `Muesli, flakes (Bircher-style), with fruits or dried fruits` — the plain fruited row (1570 kJ) for the 25 that
  add sugar, the `without sugar` row (1580 kJ) for the 5 sweetened only with date powder. The three ready-to-eat
  ACTIVUS pots, water first on the list, take `Oat flakes, boiled/cooked in water` (291 kJ) and are the one place
  on that shelf where a cooked row is the right answer. 38 of the 39 grade **B** or **C**.
- **A protein-fortified breakfast cereal** — the AXA `baltyminė košė` sachets are 18% whey protein concentrate
  and 17% erythritol, 20 g of protein against the muesli row's 10,6 and 1863 kJ against 1570. No cereal row in
  SAFAD is fortified. **C**.
- **Wheat germ** — `Kviečių gemalai MALSENA`, the oil-rich embryo at 1652 kJ, 11,9 g fat and 33,4 g protein.
  SAFAD's only other wheat by-product is `Wheat bran`, which is the fibrous husk at 1120 kJ and 14,8 g protein —
  the opposite part of the grain. **C**.
- **Spelt semolina** — SAFAD holds spelt as `Spelt, raw` and `Spelt flour` and nothing between them, so
  `Spelta manai MALSENA` (1408 kJ, 1,2 g fat) sits on the flour row and loses on fat, the endosperm carrying less
  than the whole-grain flour. **C**.
- **A flake row for any grain but oats** — `Cereal flakes` L3 splits oats into grain and flakes, so form *is* an
  axis SAFAD models, but only for oats. Buckwheat and rice flakes have to take the whole-grain row
  (`Buckwheat, whole, raw`, `Rice, raw`), which rolling does not change compositionally. **B**, or **C** where
  the pack's own fat drifts from the groat's.
- **A dried-legume mix, and a cereal-only flake mix** — `Mix of cereals and legumes, raw` is the only "mix" row in
  the grain branch, and it is doubly awkward. It describes both halves, so a pure-cereal eight-flake blend must
  take a row that claims legumes it does not have (**B**); and a pure-legume soup mix must instead take its
  majority species (`Split pea, dried`). The row also carries **0,99 g of salt** where every other raw grain row
  reads 0,01-0,06, which is the same shape of defect as the `Asian noodles, plain, cooked, unsalted` row above.
- **A dry baking mix of any kind except bread** — the second-largest gap found, and the whole of `lt/22`'s
  26-product "Flour mixtures" child. SAFAD holds exactly one row acknowledging such a product,
  `Bread, home-made, with flour for home-made bread preparation`, and even that is the baked loaf (1090 kJ,
  1,45 g salt) rather than the powder. There is no cake mix, pancake mix, waffle mix or pizza mix. Eleven rows
  carry the word "mix" in their names and every one is a mix of *ingredients* ready to eat or cook (cereals and
  legumes, salted nuts and raisins, four spices, baby salad leaves), never a dry preparation you add water to;
  "premix" and "batter" return nothing. The substitute follows the powder, not the dish on the front:
  flour-first batter and dough mixes take `Wheat flour, type 55 (for bread)` and grade **B** or **C** in order of
  the dairy and egg fat they carry (1,1-3,0 g of fat → B, 3,5-6,7 g → C, the two bands not overlapping); sugar-rich cake mixes take the baked
  row (`Muffin, with blueberry or chocolate`, `Brownie (chocolate cake)`, `Pound cake, prepacked`), because no
  flour row can hold the 25-52,3 g of sugar they carry; the two bread mixes take the bread row at +24-26% on energy; and the two
  potato mixes take `Potato flakes, dehydrated, plain`, which is what they actually are. 25 of the 26 grade B or
  C — the exception is a 94%-potato-granule dumpling mix at **A**.
  **The dish name is the wrong axis, and the shelf proves it**: `Kepimo mišinys vafliams RIMI` (waffles) and
  `Mišinys širdiniams blynams MALSENA` (heart-shaped pancakes) declare the same seven ingredients in the same
  order and the same 3,5 g of fat, 22,7 g of sugar and 0,5 g of salt. One powder, two dishes.
- **Potato starch** — SAFAD mills `Maize/corn starch` and holds `Tapioca, raw`, and nothing from potato.
  `lt/22`'s `Bulvių krakmolas RIMI SMART` (1387 kJ) takes the maize row as a substitution, -11% on energy. **B**.
- **Oat flour** — `Grain milling products` has an oat branch containing exactly one row, `Oat bran`. There is no
  oat flour. The whole grain is the right answer instead, since milling does not change it: `lt/22`'s two
  wholegrain oat flours sit on `Oat, raw` at 1555 and 1544 kJ against 1590, with the fat matching to within a
  gram. Both **A**. Contrast the flake gap above — SAFAD models *form* for oats (grain vs flakes) but not
  *milling*, which is the opposite of what the flour shelf needs.
- **A soup or broth base** — `Soup, Asian-style with noodles` is a complete bowl, noodles included, so a ramen
  broth concentrate sold to have noodles added is 59% under it.
- **The bouillon cube, for every species except fish.** There *is* a dry-cube row — `Herbs, spices and condiments /
  Seasoning or extracts / Stock cubes (bouillon cube) / Aromatic stock cube, for fish, dehydrated`, 1080 kJ and
  47,5 g of salt — and it is the only product under that L3. It fits the sixteen `lt/23` bouillons that declare the
  cube (721-1381 kJ, 18,7-58,9 g of salt) far better than anything else in the database, and it is a *fish* cube.
  Chicken, beef, vegetable, mushroom and Thai-curry cubes therefore go to
  `Broth, stock or bouillon, poultry / beef / vegetables, reconstituted with water at consumer`, which name the
  right food on the wrong basis: 20-30 kJ per 100 g of liquid against a cube at 30-50 times that. The species word
  wins over the physical form, as everywhere else in this corpus, and the letter carries the cost.
- **A dehydrated cheese soup, pea soup or borscht.** The `Ready to eat soups` L2 models dehydration properly —
  eighteen of its rows are `... dehydrated and reconstituted` — but not for these three. Cheese soup and pea soup
  exist only as `prepacked, to be reheated` (a wet soup in a carton), and beetroot appears nowhere in the branch at
  all, so `lt/23`'s borscht sachet stands on `Soup, mixed vegetables, dehydrated and reconstituted`. Check whether
  the *dehydrated* variant of a soup exists before assuming the branch covers it; the wet variant is a different
  form and takes class `'x'`.
- **A dry seasoning preparation — the rub, the marinade base, the "ruošinys".** `Herb and spice mixtures` holds
  curry powder, garam masala and Provence herbs: blends of spices and herbs, nothing built on dried *vegetables*.
  `lt/23`'s `Ruošinys vištienai su česn., MAGGI IDEA` is 62,8% dried vegetables of which 31,8% garlic, and is the
  one blank row on that shelf — L1 and L2 filled, L3 and archetype empty.
- **A dry pasta kit or dry sauce sachet.** Recorded first at `lt/20` (the MAC&CHEESE bags) and confirmed across
  `lt/23`'s durum kits, its risotto kit and its four MAGGI IDEA sauce sachets: `Pasta, cooked` and
  `Savoury sauces` model only the plated dish and the wet prepacked sauce. Sixteen rows on that shelf take class
  `'x'` for a form mismatch — eight durum kits, the risotto, the four sauce sachets, the two cheese soups and the
  pea soup — while the three durum kits whose *sauce* has no row either (mushroom, cream-and-herb, cream-and-tomato)
  take `'s'` on the cheese-sauce pasta row instead. Note the
  contrast with the soups and broths above, whose rows *are* the dehydrated product and so are graded on their
  numbers — the question to ask of a row is not "is this the right dish" but "is this the right *form*".
- **A plain tomato sauce.** Every row in `Savoury sauces / Vegetable sauce` names an added ingredient, and the plain
  ones carry 2,68-4,81 g of olive or sunflower oil. There is nothing for an oil-free sweetened tomato condiment —
  which is what a Baltic `pomidorų padažas` is, all 34 of them on `lt/24`. See the unavailable-family rule above.
- **Prepared horseradish.** `Horseradish, fresh` (307 kJ, 0,45 g fat, 5,25 g protein) is the only horseradish row in
  the database and it is the raw root. Every Lithuanian jar is 24-65% grated root emulsified with sunflower or
  rapeseed oil and sweetened, at 410-890 kJ and 4,5-15 g of fat. Right species, absent form, class `'x'`.
- **Worcestershire sauce.** A thin fermented anchovy-and-tamarind condiment with no row. The nearest is
  `Nuoc mam sauce or fish sauce, prepacked` in an L3 actually called `Fish sauce` — same kind of food, within 20% on
  energy, and eight times the salt (22,2 g against HEINZ's 2,8).
- **A flavoured mayonnaise-type squeeze sauce.** FELIX, HESBURGER and HELLMANN'S sell rapeseed-oil-and-egg-yolk
  emulsions at 25-51 g of fat flavoured with chipotle, mango, sesame, dill and lemon or curry. Where the name states
  a type SAFAD holds — aioli, kebab, burger — the row exists and fits; where it states only a flavour, the banded
  mayonnaise rule applies with its documented hole and the pack lands on the light row as a substitution.
- **A honey mustard, and a mustard-and-oil dressing.** `Mustard` and `Mustard, with grains` carry 1,73 and 2,2 g of
  sugars, so a 9,7% honey mustard is an axis off (B); `Mustard sauce prepacked` (960 kJ, 22,8 g fat) is the only
  oil-borne mustard row and the two Lithuanian honey-mustard *sauces* are 70% and 113% over it.
- **A bruschetta topping, a vegetable spread with meat in it, and a walnut sauce.** `Savoury sauces / Oil-based sauce
  (Pesto, Aioli sauce)` holds pesto, pesto rosso, aioli and tapenade and nothing else, so a 50% red-pepper or 39%
  onion purée in sunflower oil with 6-7% smoked ham has no row (class `'x'`), and a 27% walnut-and-cashew sauce
  stands on the green pesto row at +82%.
- **Adjika.** The Caucasian pepper-and-tomato paste is absent, and `Harissa (hot spicy sauce), prepacked` is the
  right kind of condiment in its place: the row holds 318 kJ against the three Lithuanian jars' 271, 332 and 390.
  The parting is oil — harissa carries 2,9 g of fat and two of the three carry 0,5.
- **A caffeinated instant coffee powder.** The `Coffee beans and coffee products (Solid)` L2 has exactly three L3s
  — `Coffee beans, roasted and ground`, the same decaffeinated, and `Instant coffee, powder, decaffeinated` — so
  SAFAD names an instant coffee *powder* only in its decaffeinated form. The only caffeinated `Instant coffee`
  product anywhere is `Instant coffee, without sugar, ready-to-drink` (6,95 kJ) under `Coffee (Beverage) / Instant
  coffee, liquid`. Twenty jars on `lt/25` take it at C, class `'x'`. The one decaf jar is better served than they
  are, which is the whole of the defect in one sentence.
- **Two beverages are filed inside the solid coffee L2, and they are the only rows a decaffeinated coffee has.**
  `Decaffeinated not instant coffee, without sugar, ready-to-drink` (18,6 kJ) sits under the L3
  `Coffee beans, roasted and ground, decaffeinated`, and `Decaffeinated instant coffee, without sugar,
  ready-to-drink` (8,91 kJ) under `Instant coffee, powder, decaffeinated`. Both are internally consistent cups —
  their LCA columns are a cup's too, 0,68 and 1,63 kg CO2e/kg against the ground row's 8,4 — so this is a *filing*
  defect rather than the copied-nutrition one: two drinks in the solid branch. A decaf bean, ground or capsule pack
  therefore has one archetype available and its numbers are a brewed cup's; nine such packs on `lt/25` take it and
  grade C. **`kritis/12` grades the same mapping B** on its one Greek decaf; reconcile when the kritis files get
  their numeric pass.
- **A coffee-and-milk pack sold dry.** Café au lait, cappuccino, latte macchiato and flat white exist only as
  `Coffee with milk or white coffee or cappuccino, … ready-to-drink` (90,5 kJ). Eighteen capsule packs and
  fourteen 3-in-1 sachets on `lt/25` carry 5,5-89,9% milk powder — the capsules at 10-89,9%, the sachets at
  5,5-20,3% over a glucose-syrup-and-hydrogenated-coconut creamer — and the 23 of them that declare the dry pack
  run 1428-1945 kJ, so every one sits on the poured cup's row at up to twenty times its energy, class `'x'`. The row also says *without sugar*, which most of them are not.
- **A sweetened cocoa drink powder, and any flavoured drink powder that is not cocoa.** `Cocoa powder, without
  sugar, powder, instant, non rehydrated` (1610 kJ) is the only cocoa solid; sweetened cocoa lives in `Cocoa
  beverage / Cocoa drink` as the cup reconstituted with semi-skimmed milk (278 kJ, or 301 fortified). A NESQUIK or
  hot-chocolate powder at ~1630 kJ therefore lands +480%. For a *non*-cocoa drink powder there is nothing at all:
  the NESQUIK strawberry, banana and vanilla pouches are sugar with maize fibre, flavouring and colour, and take
  `Strawberry flavoured milk …` and the generic `Dairy drink or fermented milk or yogurt, flavoured, with sugar`
  — rows that assert both milk and dilution — at C.
- **A roasted-grain coffee substitute.** `Coffee imitates (Solid)` holds chicory and chicory-with-coffee mixtures
  and nothing built on roasted barley or rye. `lt/25`'s GALCA "chicory drink" is 83% roasted BARLEY extract with
  15% chicory, and the two MOKATE cappuccino sachets are 3,3% and 12,3% barley-and-rye grain coffee against 1% real
  coffee. The chicory row carries them on the name and the composition denies it.
- **A ready-to-drink sweetened or flavoured coffee.** All four `Coffee (Beverage)` rows say *without sugar*, and
  the only one with milk is the white-coffee row. `lt/25`'s two flavoured JACOBS bottles (water, sugar, 8,8%
  instant coffee, 20 g sugars) have nowhere to put the sugar and grade C on the black instant cup.
- **A DRY TEA LEAF — the single largest gap the corpus has found, and it is one corrupted cell wide.** SAFAD's
  whole tea vocabulary is `Non-alcoholic beverages / Tea (Infusion)`: `Black tea, brewed, without sugar` (5,36 kJ),
  `Tea, brewed, without sugar` (1,70), `Green tea, brewed, without sugar` (21,70), `Oolong tea, brewed, without
  sugar` (5,36) and `Infusion, brewed, without sugar` (3,40). All five are the liquid in the cup. A solid branch
  **does** exist beside the solid-coffee one — `Vegetables and vegetable products (including fungi) / Tea and herbs
  for infusions (Solid)` — and it holds exactly one L3, `Peppermint (Mentha × piperita)`, and one row under it
  (TH2433) whose `food_product` reads **`Chinese or Japanese artichokes, frozen, raw`**, with CIQUAL and AGB code
  20231, the artichoke's. The row's own `food_product_SAFAD` still says Peppermint, so the intent was a dried
  peppermint row and the payload came from the wrong food; the artichoke, meanwhile, exists nowhere else in the
  database, so it is filed under tea and nothing else. Fixing that one row would give the five peppermint packs on
  `lt/25` a dry home; its six chamomile packs would still need one of their own, and thyme already has
  `Herbs / Thyme, dried`. Until then all 169 leaf and herb packs there sit on a brewed row, 163 of them at **B**.
- **White tea, pu-erh and matcha.** SAFAD names black, green and oolong. `lt/25` has two white teas (Mao Feng and
  a 65% white blend), one pu-erh (`Arbata PUERO`) and two matcha powders; white and pu-erh fall to the unspecified
  `Tea, brewed` row and matcha to brewed green tea, all five at C. Matcha is the interesting one — the leaf is
  milled and *swallowed*, so it is not an infusion at all and the row it takes is the water an infusion's leaf was
  lifted out of.
- **Tea with milk, in any form.** Coffee has `Coffee with milk or white coffee or cappuccino …`; tea has nothing
  of the kind, and neither `Tea (Infusion)` nor `Milk based beverages` carries a chai or a tea latte. `lt/25`'s two
  MOKATE matcha-latte sachets (18% skimmed milk powder, 4% matcha) take the generic
  `Dairy drink or fermented milk or yogurt, flavoured, with sugar` at C.
- **An instant tea powder.** The same hole as instant coffee, one step worse: there is no tea *powder* row at all,
  decaffeinated or otherwise. The three EKLAND tins on `lt/25` — sugar and dextrose carrying 0,5% black tea extract
  — go to `Still soft drink with tea extract, flavoured, with sugar` (98,6 kJ) against their 1626, class `'x'`.
- **A cordial, squash or drink concentrate.** `Soft drinks` models only the poured glass, so a 520 ml bottle of
  60% juice and sugar meant to be diluted has no row: `lt/25`'s three ŽOLYNĖLIS concentrates run 763-906 kJ against
  the unspecified fruit soft drink's 194.
- **A sweetened fruit purée.** `Fruit, purèe / Fruits puree, without sugar added` (239 kJ) is the only purée row
  and its name carries an axis every sweetened purée contradicts, while the jam L3 names four fruits and a
  reduced-sugar variant. Eleven MASHIE and SKANOVĖ jars on `lt/25` (603-891 kJ, 30-53 g sugars) therefore take
  `Jam, raspberry` under `lt/18`'s unnamed-berry rule: two land at A, four at B and five at C for being lighter
  than a French jam.
- **A SUGAR ALCOHOL OF ANY KIND.** No xylitol, erythritol, sorbitol, maltitol, isomalt, mannitol or polydextrose
  anywhere in the 2451 rows. `Sugar and confectionary / Sugar substitutes` holds one L3,
  `Non-nutritive sweeteners (e.g., aspartam, sacccharine)`, and one row under it, `Saccharin sweetener` (21,30 kJ).
  A polyol is the *other* kind of sugar substitute — bulk rather than intense, dosed spoon for spoon against sugar,
  and costed by law at 10 kJ/g — so the branch's only L3 excludes it by its own wording. Four packs on `lt/26`
  (erythritol, xylitol, ZUSTO's polydextrose-and-isomalt filler, and an "ALVO stevia" blend that is 99,8%
  erythritol by its own label) are C for this, each a 300 g tub sitting on a row for a molecule used in milligrams.
- **Any non-nutritive sweetener that is not saccharin.** The same single row has to carry aspartame — which the L3
  names in its own example list and which has no row — plus acesulfame-K, sucralose, cyclamate, thaumatin and
  steviol glycosides. `lt/26` files five packs there at **B**; only three packs on that shelf, the two HUXOLs and
  the DIAMANT, actually contain saccharin, and those are the only sweeteners in the corpus that can reach A.
- **Coconut palm sugar.** Evaporated coconut palm sap. SAFAD carries coconut kernel, milk, water and oil, and palm
  oil, and no palm sap of any kind. `lt/26`'s two packs take `Sugar, brown`, which matches their energy to within
  4,3% and hands them sugarcane's 90,70 of land use: a different crop on a different continent, C.
- **A table syrup.** `Molasses and other syrups / Sugar beet syrup` holds four rows and not one of them is a sugar
  beet syrup: two are the canning liquid from tinned fruit, one is a cordial already diluted in water (133 kJ) and
  the fourth is that cordial's undiluted concentrate (1070 kJ). The three Nordic DAN SUKKER sirups on `lt/26` —
  light, golden and dark, which are inverted beet sugar and exactly what the L3 is named for — take the concentrate
  at +21% to +27% on energy, **B**. `Cane molasses` is numerically nearer to the dark one (1270 kJ against its
  1296) but names another food from another crop.
- **A flavoured sugar that is not vanilla.** `Sugars / Flavoured sugar` holds one row, `Sugar, vanilla flavoured`.
  `lt/26`'s coarse-crystal caramelised sugar is white sugar browned by heat with nothing added, so it matches
  `Sugar, white` to the decimal and takes it at **B** — the flavour axis exists in the taxonomy and has room for
  exactly one flavour.
- **A glazed cereal bar** — a yoghurt- or cocoa-glazed bar is a quarter to a third coating by weight and carries
  15 to 17 g of fat where `Cereal bar w fruit` stores 7,07. SAFAD's four bar types are all uncoated extruded bars;
  five glazed bars on `lt/17` are C for that one reason.
- **A flapjack** — a baked oat bar on palm and rapeseed oil. MA BAKER's 90 g bars are 19,5 to 22,7 g of fat on the
  same 7,07 g fruit-bar row.
- **A plain cereal bar, and a plain granola** — every bar row names a flavour (fruit, almond/hazelnut, chocolate,
  milky) and the crunchy-muesli rows name only chocolate, fruit and grain. A seed, nut or coconut granola therefore
  sits on a row asserting fruit it does not contain: map it there and give it an explicit **B**.
- **A protein cereal** — no row anywhere carries added gluten or pea protein. Two LION/CINI MINIS packs at 15 g and
  a pea-protein granola at 22 g sit on rows storing 8 to 9 g; explicit C, as for every protein-isolate rebuild.
- **A high-fat chocolate cereal that is not filled** — the two not-filled chocolate rows store 3,2 g of fat and the
  current oil-crunch generation (LION WILDCRUSH, KIT KAT, TRIPLE CRUNCHY, ŽALGIRIS OHO) runs 8,3 to 15,5 g. The
  only rows at that level assert a filling these packs do not have, so four rows on `lt/17` are C.
- **A cinnamon, marshmallow or fruit-flavoured shaped cereal** — `Breakfast cereals, mix of puffed or extruded
  cereals` is the only row that names the FORM rather than a flavour, so it carries the whole shaped-multigrain
  family (rings, loops, squares, churros); its 8,2 g of fat is above nearly all of them, which is why fourteen
  rows on `lt/17` sit on it and eleven are B. The alternative is the `Breakfast cereals, diet, …` block, and calling a
  children's honey ring a diet cereal is a worse claim than the fat gap.
- **Fish imitates** — SAFAD has a `Meat imitates` L2 and a `Milk and milk product imitates` L2 and nothing for the
  third kind. Vegan smoked-salmon slices (maize starch, konjac, carrageenan, rice protein) are blank at
  `Meat and meat products (including edible offal) / Meat imitates` depth: `Soy-based sliced` has the format but
  17,6 g protein against the pack's 1. Both markets sell the same pack and both files blank it.
- **Egg imitates** — the same gap, found on the Lithuanian plant-based shelf. `Eggs and egg products` holds fresh
  eggs, whites, yolks and three powders and no analogue, and the two imitate L2s are meat and dairy. A plant egg
  substitute (water, 12% soya, sunflower oil, soy protein isolate) is blank at `Milk and dairy products /
  Milk and milk product imitates` depth: `Tofu, plain` is the nearest composition at 8,5 g of fat to the pack's
  7,8 and asserts a coagulated curd where this is a pourable emulsion.

Gaps taken as C rather than blank, with the substitute in brackets:

- Cheese: Tilsit, halloumi and grilling cheese, mascarpone, burrata, pecorino, Wensleydale, Danablu, string cheese,
  smoked cheese, glazed curd bars (kohuke), reduced-fat feta, stracciatella, Havarti, Tête de Moine, brinza,
  **filled cheese** (milk protein with palm oil in place of the milk fat), dried or baked cheese snacks,
  marinated fresh cheese in oil, light brined cheese, light *flavoured* fresh cheese (the only flavoured
  fresh-cheese row is 30-40% fat), and **any generic semi-hard or hard cow's-milk cheese**.
- Dairy: quark/skyr (the fresh-cheese protein ceiling is ~10 g against Baltic kohupiim's 14-20 g), **plain
  whole-milk yoghurt** (nothing between the 1,68 g plain row and the 8,5 g with-cream one), **plain drinkable
  yoghurt**, a **fruit yoghurt sweetened without sugar that still carries fat** (both with-sweetener rows are
  fat free), **glazed curd bars** and their vegetable-fat and wafer-cone variants, high-protein
  yoghurt (fermented-milk branch tops out at 6,35 g protein), sweetened kefir, ryazhenka, ayran, goat butter,
  **pasteurised goat milk** (taken on the raw row), **goat or sheep kefir** and **flavoured or sweetened
  goat or sheep yoghurt** (both taken on the one plain row their species L3 offers - see the goat-and-sheep
  rules for why species wins over the form and the sugar),
  fat-free fruit yoghurt sweetened with sugar rather than sweetener, near-fat-free mayonnaise, drinkable-yoghurt
  richness above ~360 kJ.
- Ices and desserts: no **ice cream sandwich** row (taken on `Ice cream, cone (normal size)`, the only row that
  models ice cream inside a baked wheat carrier, and it fits within 4% on energy), no **mochi** row, no
  **reduced-sugar or sweetener-sweetened** ice cream row, and no **frozen plant-based dessert** row anywhere —
  `Milk and milk product imitates` reaches drinks, soya cheese, soya yoghurt and tofu and stops, and both markets
  put vegan frozen desserts on the `Sorbet` rows because sorbet is the one family in the branch that asserts no
  dairy. Also **no flavour axis at all** in `Ice cream, milk-based`: the 16 rows are formats, and there is no
  vanilla, chocolate or strawberry row to look for. See the ice cream rules.
- Frozen gastronomy: no **curd-fritter** row anywhere (*varškėčiai*/syrniki — `Cheesecake, refrigerated` is the
  only curd-based cake and it is a chilled dessert), no **garlic bread** (the French-loaf row carries the loaf and
  the garlic butter has no home — the bread file found the same hole), no **calzone or filled pizza pocket**, no
  **breaded cheese** row that does not also assert ham (`Cheese and ham, breaded`), no **pea-protein patty**
  (every plant-patty row names lentil, soybean or wheat), no **yeasted laminated dough** (the frozen dough rows
  split on butter, not on yeast), no **noodle-and-chicken ready meal** (all the ready-meal rows are rice-keyed),
  and no **starch-filled ravioli** — the ravioli L3's four fillings are meat, cheese, vegetables and
  cheese-with-vegetables, and potato is a starchy root rather than a vegetable in this taxonomy. See the frozen
  gastronomy rules.
- Other: quail and ostrich eggs (the `Goose eggs` L3 is the catch-all for non-chicken bird eggs and already mixes
  goose and turkey), liquid/pasteurised egg products, egg husbandry, egg size and class, water-based jelly,
  kissel, fruit-flavoured custard, pork neck, pork bones, pig tails, and **fresh or pressed baker's yeast** —
  the only leavening row anywhere is `Baking powder or raising agent` at 23 g salt and 0,1 g protein, a
  chemical leavener that models nothing about a live culture.
- Meat: lean ("delikatess") pork mince at 11-12 g fat against the 21,2 g mince row, reconstituted meat masses
  (80% meat bound with water, fibre and phosphates, so protein runs 30-37% low), minced turkey, beef shank, raw
  beef roast, beef escalope, veal leg/round/topside, lamb shank, every rabbit cut, and the strips and minute-steak
  formats. From the Lithuanian shelf: **beef brisket** and **beef tenderloin** (both absent outright — see the
  meat rules), **the pork flank**, **raw pig trotters** (the only pork row in that L3 is salt-cured) and
  **adult beef liver** (both bovine rows are filed under `Veal liver`). From the poultry shelf: **the neck of any
  bird** (beef, veal and lamb all have a neck row), **poultry bones** (a soup set of backs, the same gap as beef
  soup bones) and **poultry feet** — all three land on `Chicken, eviscerated without offal, raw` or the cut-less
  `Chicken, meat and skin, raw`, neither of which is a bone-in offcut. Pre-cooked pulled meat in sauce: see the Meat stew note in the grill-ready rules — the chicken
  version scrapes onto `Chicken, Basque style`, the pork version is the one blanked row in the meat files.
- Sausages: kabanos, cheese in a sausage, Krakov sausage, Italian salami, fuet, salchichón,
  spianata, **the Lithuanian skilandis**, **any dry poultry sausage** and any processing axis for poultry
  sausage. Game sausages are covered by the absent-species note above. Also: no unqualified raw pork sausage row
  (the `Fresh and lightly cooked sausage` L3 is six rows, every one carrying a region, a spice profile or a
  format), no blood dumpling, and no cereal-bearing blood sausage — boudin noir holds no groats, so Estonian
  verivorst comes out at half the archetype's fat.
  **Two entries have been removed from this list, because the rows exist outside the Sausages L2**: sardell and
  bockwurst are served by `Strasbourg sausage` and a plain boiled sausage by `Cooked sausage, pure pork`, both in
  the pork L3 of `Livestock meat`. Search the whole database by name before recording a sausage as absent.
- **Dried meat (jerky) is absent outright** — no jerky, biltong or viande des Grisons row anywhere, and the
  meat L1's protein ceiling is 39,2 g against these products' 27-81 g. Taken as C species-first; see the
  snack-sausage rules.
- **Canned meat is absent outright** — no luncheon meat, no corned beef, no meat-in-own-juice row. Taken as C
  on `Head-cheese pâté or brawn` for pork and on the species' cooked row for beef and chicken.
- Other absent foods met in `other-meat-products`: pulled meat in BBQ sauce (the one blanked meat row), pork
  meatballs and pork patties, breaded pork schnitzel, beef liver pâté, poultry aspic or brawn, poultry feet
  and carcass parts, adult beef liver, and Estonian saiakate (a rusk-and-tomato meat spread at 619 kJ where
  every row in the `Pastes, pâtés and terrines` L2 is 1110 kJ or richer). From the Lithuanian shelf: **a poultry
  meatball** (the meatball row is beef, so chicken *kukuliai* take `Poultry quenelle, raw`, a poached forcemeat
  ball, at +39%), **a boiled whole chicken** (both boiled poultry rows are the leg, so a boiled half bird takes
  the cut-less roasted row), **pig snout** outside `Pork snout in salad dressing sauce`, which is an aspic salad,
  and **a buckwheat-and-meat canned meal**, which borrows `Rice and meat meal / Couscous w meat` — a wheat
  semolina dish standing in for a buckwheat one, and inside 15% on both energy and fat.
- **Packing oil has no home in the fish L1**, whose fat ceiling is 22,9 g (`Mackerel, smoked`) — smoked sprats
  in oil, salted herring in oil and hot-smoked salmon belly all run 28-43 g. Taken as C on the species row.
  In-oil, drained rows exist only for anchovy, herring, sardine, mackerel and tuna.
- **Dried fish beyond cod**: `Cod, salted, dried` is the only dried row in the database. Dried-fish snacks run
  40-66 g protein against raw rows at 15-20 g; taken as C species-first, the jerky call.
- Fish: **tilapia is absent outright** — no tilapia, no *Oreochromis*, no cichlid in any of the 2451 rows, in
  any column; taken as C on `Nile perch, raw`, a lean white fish from the same river but a different family.
  **African catfish** (*Clarias gariepinus*) likewise, taken as C on `Brown bullhead, raw` — the only true
  catfish row and the only raw one — where farmed Clarias's 9,5 g fat exceeds everything in that L3.
  Also absent: *Litopenaeus vannamei* (use the generic `Shrimp or prawn` rows), any trim or presentation axis
  for salmon, a farmed variant of Arctic char, and a raw pangasius row.
  **Sturgeon is absent outright** — no sturgeon, no *Acipenser*, no caviar in any column, the same search that
  leaves roe unmapped — so a farmed Siberian sturgeon (*Acipenser baerii*) is taken as C on `Carp, raw, farmed`,
  the nearest pond-farmed freshwater fish and the other product of the same Lithuanian farm. `Trout, farmed, raw`
  fits the numbers better (558 kJ and 6,22 g of fat to this pack's 538 kJ and 6,6 g) but a carnivorous cold-water
  salmonid is a different farming system, and the letter is C either way because no row names a sturgeon.
  **The European smelt** (*Osmerus eperlanus*) has no row either: the `Smelt (Osmerus)` L3 holds a pond smelt
  (Osmeridae, the nearer relative, and the plain raw row, so that is the one taken) and a big-scale sand smelt
  (Atherinidae, and a to-fry row that happens to match the ungutted whole-small-fish format).
  **Silver warehou** (*Seriolella parosa*, Centrolophidae) is absent to the family, taken as C on
  `Atlantic herring, smoked, plain`, the nearest smoked oily schooling fish. Also confirmed absent on the
  Lithuanian shelf, each taken as C on the row named: a fried or breaded row for any species but whiting and
  plaice, an in-oil canned row for mackerel, a sauced canned row for salmon, a smoked or cooked row for redfish
  and freshwater bream, a dried row for anything but cod, and a crustacean or mollusc spread of any kind. **Crayfish** are absent outright — no
  crayfish, *Astacus* or *Procambarus* — taken as C on `Lobster, boiled/cooked in water`, the nearest Astacidea.
  Also taken as C: any in-oil or marinated form of a crustacean or mollusc, smoked mussels, canned squid in
  sauce, and rehydrated sea cabbage (on the dried-kombu row). From `processed-fish-products`: roach, a
  salt-cured-only salmon or trout row, sauced or sweetened herring of any kind, sauced
  canned salmon, smoked or cooked redfish and freshwater bream, dried anything but cod, fish balls, and every
  fish carcass cut (backbone, ribs, belly flaps).
- Produce: **no gherkin, sauerkraut, kimchi or pickled-onion archetype** (all taken as C on the vegetable's own
  raw row — the two rows SAFAD *calls* Sauerkraut are cooked composite dishes at 6,6-8,1 g fat, usable only for a
  genuinely braised one), no dried mango, no aged or fermented garlic, no dressed-beetroot salad (the
  `Prepared mixed vegetable salad` L3 is coleslaw, grated carrots and marinated mushrooms and nothing else),
  no fresh oregano or marjoram and no fresh herb *mixture* (dried spice rows only), no fresh turmeric, no raw
  kohlrabi (the L3's one row is boiled), no Savoy or pointed cabbage, no orange or white sweet pepper, no colour
  axis for tomatoes or onions, no size axis for carrots or tomatoes, no sugar-snap pea (snow pea only), no
  flesh-colour axis for watermelon or sweet potato, and no fresh-raw sweetcorn cob (cooked or frozen only).
  From the Lithuanian fruit shelf: **dried fruit beyond the nine rows** SAFAD carries (apple, apricot,
  banana, coconut, date, fig, peach, prune, raisin), so dried mango *and* dried lychee sit on their raw rows
  at three to five times the energy; **dragon fruit** (*Hylocereus*), taken on `Prickly pear, pulp and seeds,
  raw`, the only other Cactaceae fruit in the database; **longan** (*Dimocarpus longan*), taken on `Lychee,
  pulp, raw`, the same family and within 3% of it fresh; **a nut butter for any nut but peanut**, so the
  cashew creams keep the Cashew L3's salted snack-nut row; and **no black-grape row**, so blue and dark
  grapes take the red one. Three more are close enough to be a **B** rather than a C: the **Asian (nashi)
  pear** on the *P. communis* row and the **kiwi berry** (*Actinidia arguta*) on the kiwi row, both a single
  species off a generically named row, and **white grapefruit** on the yellow row, which is the same fruit
  under the trade's other word. Also missing: **a fresh date** row and **a cultivar-neutral melon** row.
  From the Lithuanian vegetable shelf: **no beetroot row in the `Prepared mixed vegetable salad` L3**, so a
  Korean beetroot salad and a beetroot carpaccio take `Beetroot, cooked` at C rather than the coleslaw row that
  fits them on energy; **no daikon** (the white winter radish takes `Radish, black, raw` at B as the nearer of the
  two); **no pattypan** (`Squash, all types, raw`); **no potato-dumpling dough** (the `Potato dumpling, plain`
  L3's only row is a filo-pastry fritter, so a raw *didžkukuliai* dough takes the peeled raw potato row at C);
  and **no mixed-legume row**, so a boiled chickpea-and-bean mix takes its majority species at C.
- Preserves, pickles and store-cupboard sweeteners, from `lt/18`: **one olive row for the whole species** —
  `Olive, green, stuffed (anchovy, sweet peppers, etc…)` is the only entry under `Table olives (Olea europaea)`,
  so there is no plain, black, Kalamata or oil-cured olive and 48 packs share it; **no caper** and no L2 a
  flower bud could sit in (four packs blank at the vegetable L1, the cultivated-mushroom call); **no date
  syrup** (taken on `Date, pulp and peel, dried`, the same species and within 7% of it); **no caramel sauce**
  (`Dessert sauces` is one L3 and one row, a chocolate sauce); **no biscuit spread** (`Speculoos biscuit` is
  the right food in the wrong form, at half the fat); **no cooked buckwheat** — bulgur and quinoa both have
  `boiled/cooked in water` rows and the ready meals built on them land within 20%, while buckwheat has only
  `Buckwheat, whole, raw` at 1530 kJ; **no plain mixed-nut butter**, since all three `Mixed nuts and dried
  fruits` rows assert raisins or dried fruit; **no jam for any fruit but apricot, cherry, raspberry,
  strawberry and orange marmalade**, so blackcurrant, lingonberry, bilberry, cranberry, plum, fig, mango,
  mulberry and an apple-pear jam all take a relative; and **no non-poultry meat soup** but `Soup, chorba frik,
  w meat and frik`, which carries a goulash, a meatball and two cabbage-and-ham soups at +47 to +115%.
  One row is worse than missing: **`Butter bean or yellow bean, canned, drained` is not a butter bean.** It
  stores 97,3 kJ and 1,2 g of protein — a yellow wax *pod* — against the three Lithuanian packs' 386-418 kJ
  and 5,9-6,2 g, one of which lists *limos pupelės* outright. It is kept because the name reaches it and
  nothing else in the L3 is called a butter bean, but `Lima bean, raw` in the same L3, at 444 kJ, is the row
  that actually holds a lima.
- Plant analogues: **no plant-based ice cream row of any kind** (the frozen L3 is literally `Ice cream,
  milk-based`, so all 15 vegan ice creams take `Sorbet`, at a fat gap of one to two orders of magnitude — the
  largest deliberate compromise in any of these files), **no fermented oat, almond or coconut dessert** and **no
  oat or coconut cream** (the branch is soya-complete and drinks-only for every other crop, so all of these
  borrow a row that names soya), **no coffee whitener or creamer** anywhere, dairy or plant, **no buckwheat
  drink** and no fermented plant drink at all, **no pea tempeh** (the one `Tempeh` row sits under an
  `Soya beans, soaked/boiled/canned` L3), **no coconut-oil cheese spread** (the spread row is the soya one),
  **no vegan cheesecake, vegan pizza or plant-sausage-with-cheese**, no flavoured tofu, no tofu spread, no tofu
  bar, no plant-protein filled pasta, no kebab format for a meat imitate, no dried-vegetable savoury snack, and
  no protein-enriched, Greek-style or barista variant anywhere in the imitates branch.
- Bread and bakery: **no pure rye bread, no sourdough and no pumpernickel row** — the whole
  database holds one rye bread row, `Rye bread, and wheat`, in a *mixed* wheat-and-rye L3, and it
  carries all 59 Estonian rye and peenleib rows; **no seeded bread row at all** (the only
  seed-bearing row in `Bread and rolls` is `Swedish toast, with linseeds`, a dry toast in the
  wheat L3), so 17 seeded rye breads at 4,0-10,8 g fat sit on a 1 g archetype; no seeded
  crispbread (the one crispbread row stores 3,6 g against up to 31,2); no rice-based snack row
  (rice crisps take the corn-chip row); no L3 for a small filled savoury pastry (*pirukas* takes
  the nearest filled-pastry row from whichever L1 carries its filling); no Estonian *sepik*,
  *rõngik* or *kringel* of their own — they take the bran/wholemeal sandwich, bagel and Viennese-
  pastry rows; and `Gingerbread` (1,15 g fat) describes nothing on this shelf, since Estonian
  *meekook* is a layered cream cake at 9-10 g.
- Mayonnaise and dressings: **the middle oil band is unusable for plain mayonnaise** (see the cross-cutting rules),
  so nothing serves a plain 35-50% mayonnaise; **no egg-free mayonnaise** (three vegan and soya packs sit on the
  egg-based rows); **no coffee-whitener-style flavoured mayonnaise** — mushroom, ham, wasabi, gherkin, dill and
  mango-chutney mayonnaises all take the plain row with the flavour noted; and the leanest mayonnaise sold (18% oil)
  is a third below the light row's own 28,5 g of fat. On the dressing side `Salad dressing, prepacked` holds 30,5 g
  fat for a 25-50% oil band, so a 48% sriracha mayo on it is 59% out on energy — the band's bottom-weighted
  composition is the same trap the 15-20% cream rows set.
- Fats: **no goat or sheep butter** — the butter rows name no species at all, so a goat butter loses the one
  attribute its name is about (it still lands within 4% on energy). **No vegetable spread below 30% fat**: the only
  20% row in the database is `Dairy fat 20% fat, light, spreadable, unsalted`, so a 20% margarine sits 70% below
  the lightest vegetable row. And **the blended-fat rows are vegetable-dominant** — Baltic butter-and-rapeseed
  spreads are 63-70% butter and carry 40-42,5 g saturates against the rows' 16-22, so they grade B on composition
  even when energy is close.
- Cream: **nothing between 8% and 15% fat, nothing between 20% and 30%, and nothing above 30%.** The Baltic shelf
  lives in those holes — 10% coffee creams (the file's largest C block, 7 rows), 12% and 25% soured creams, 35-38%
  whipping creams and 40% soured cream, which sits 10,1 g of fat above the 30% row at +33% on energy. Conversely
  `cream, light, 4 to 8% fat` has **no customer at all** on a 45-product Lithuanian cream shelf, because the
  lightest cream sold is 10%. Also **no coffee whitener and no cream/vegetable-fat blend**: three packs here are
  built on hydrogenated or palm fat (a Zott whitener whose saturates equal its total fat, a Varėnos cream-and-palm
  soured spread, a Cessibon aerosol topping on palm and coconut oil) and all three are C on a dairy row.
- Fermented milk: **no plain buttermilk row**, although a `Buttermilk` L3 exists — its single row is
  `Renneted milk, flavoured, refrigerated` at 466 kJ and 14,9 g sugars, a rennet-set flavoured drink, so plain
  *pasukos* take the skimmed fermented-milk drink from the `Kefir` L3 instead; **no ayran, lassi or any salted or
  water-diluted fermented drink** (the 2LIFE airan at 82 kJ and 0,8 g salt is 46% under the nearest row); **no
  riazenka** — SAFAD does not distinguish a baked fermented milk; and **nothing between skimmed and whole kefir**.
- Milk drinks: **no dairy-free milky tea** anywhere — a matcha or bubble-tea style drink built on coconut-drink
  powder has only `Still soft drink with tea extract, flavoured, with sugar` (98,6 kJ, 0 g fat) to fall back on,
  which is 115-137% out on energy for the two Yusaki packs because the row carries no fat at all; **no
  concentrated milk-protein drink** (four Protein Lab packs at 8,4 g protein and 0,5% fat are left blank in both
  the Estonian and the Lithuanian file); and **no coffee row a milk-based drink may use** — see the exclusion rule
  in the cross-cutting section.
- Dried fruit, nuts and seeds, from `lt/27`: **drying is modelled per fruit and not as a process, and the whole
  dried-fruit vocabulary of the database is ten foods in eleven rows** — `Apple, dried`, `Peach, dried`, `Raisin`,
  `Banana, pulp, dried`, `Date, pulp and peel, dried`, `Fig, dried`, `Apricot, pitted, dried`, `Prune`,
  `Coconut, kernel, dried`, and the tomato twice as `Tomato, dried` and `Tomato, dried, in oil`. Every other
  fruit exists raw and only raw, so a dried **cranberry, mango, pineapple, papaya, persimmon, melon, sour
  cherry, breadfruit, strawberry, raspberry, blueberry or mulberry** has no form
  to take: `'x'` → C, and on `lt/27` that is 27 packs sitting +322% to +885% over a raw row, the excess being
  entirely the water that was removed. Also **no goji** (*ožerškis*, *Lycium barbarum*) under any name in any
  column, and nothing in `Berries and small fruits` is a nightshade to stand in for it — blank.
  **No mixed-seed row** anywhere, so a seed mixture takes its largest declared seed and grades C when, as on this
  shelf, the name states no seed at all. **No candied or glazed nut**, so caramelised almonds and cashews at 70%
  nut take the plain species row and lose 33 to 38 g of sugars to it. **No roasted or fried legume or grain
  snack**: chickpea, broad bean and maize are each stored raw, cooked, canned and dried, and a fried snack of
  any of them carries the oil as a 130% to 1076% fat gap on the dry-crop row. One row is filed in the wrong
  place twice over: the L3 named `Dried prunes (Prunus domestica)` holds **`Peach, dried`** and nothing else,
  while the actual prune is `Prune` under `Stone fruits / Plums (Prunus domestica)` — so a dried plum and a dried
  peach each take a row whose L3 names the other fruit, and there is no way to write the tuple that reads right.
  And the only roasted-and-salted almond row in the database, `Almond, grilled, salted`, sits under
  **`Almond, bitter (Prunus amygalus amara)`** — a flavouring sold in gram quantities, not a snack — so a salted
  almond must not be routed there by its processing word; `lt/27` has none, but the next shelf may.
- **The three mixture rows all assert both halves, so a one-kind mixture has nowhere to go.**
  `Tree nuts / Mixed nuts and dried fruits` holds `Mix of salted grains/nuts and raisins`,
  `Mix of unsalted grains/nuts and raisins` and `Mix of unsalted grains/nuts and dried fruit`, and each names a
  nut-or-grain part **and** a fruit part. A mix that has both is a clean `'d'` — the name states nuts and fruit,
  the row states nuts and fruit, and salted/unsalted and raisins/fruit are read off the name — at the cost of the
  ratio, which is the one axis a single row cannot carry (24 such packs on `lt/27` run from 12 to 46,1 g of fat
  on rows storing 26,3 and 37,2). A mix of nuts only, or of dried fruit only, contradicts the other half and takes
  the nearest row at **C**. This is the general form of the already-recorded absence of a plain mixed-nut butter.
- Snacks, from `lt/28`: **no pulse-based snack row** anywhere — a fried lentil, pea or chickpea flour chip
  takes `Corn chips or tortilla chips`, the generic grain chip the rice crisps already take, at B.
  **No pork rind**: SAFAD names the rind exactly six times and every one is a cut it has been taken *off*
  ("rindless", "without rind, fat and bone"), so a fried pig-skin snack at 72,3 g of protein is blanked at
  `Meat and meat products / Livestock meat` — the species is there and the part is not.
  **No dry dip preparation**: `Herb and spice mixtures` is curry powder, garam masala and Provence herbs, and
  a sachet of glucose syrup, starch, whey permeate and MSG at 13-18 g of salt is blanked as `lt/23`'s MAGGI
  IDEA rub is. **No sweet maize snack**: a 25%-sugar corn stick has to cross into `Breakfast cereals` for
  `Breakfast cereals, puffed/popped corn, with honey (not fortified…)`, the one row carrying both the grain and
  the sugar. And `Pop-corn or popped maize, with caramel` stores 9,83 g of fat where every Baltic caramel
  popcorn carries 10,7 to 19,3, so the whole family grades B on fat alone.
- Biscuits, from `lt/29`: **no grain is named in any of the 59 biscuit rows.** There is no oat biscuit (eight
  packs here carry the word), no rye or spelt biscuit, no digestive, no wholegrain biscuit outside the four
  `Breakfast cereals / Cereal bars` breakfast rows, and no shortbread that is not butter-, fruit- or
  chocolate-qualified. The grain a biscuit is made of is simply not an axis, which is why the *avižiniai*
  packs fall to the category average and the wholemeal ones have to borrow the breakfast-biscuit row.
  **No wafer-cake row**: a Baltic *vaflinis tortas* is layered wafer and fat cream at 1875-2289 kJ and takes
  the filled-wafer row. **No stroopwafel**: five syrup waffles take `Wafer biscuit, crunchy (thin or dry),
  plain or with sugar, prepacked`, which fits them to within 8% on energy. **No spiced crisp biscuit besides
  `Speculoos biscuit`**, which is why a Swedish pepparkaka goes there rather than to `Gingerbread`.
- Chocolate bars, from `lt/30`: **no sesame confection.** Halva, pasteli and sesame brittle have no row; a 45%
  roasted-sesame bar takes `Nougat`, which carries half its fat, and the Greek shelves reached the same row for
  pasteli and graded it the same way, so both languages agree about where the hole is. **No white-coated wafer**
  and no white filled chocolate beyond the plain bar, so four white KIT KAT, BUENO and LION packs sit on a
  milk-chocolate-calibrated row. **No coated marzipan**: four packs are 74-80% marzipan under an 18-20% glaze
  and the whole of their +58 to +67% fat gap is the glaze. **No toffee bar and no condensed-milk iris**:
  `Soft caramel candy` is a European butter caramel at 9,98 g of fat where a Latvian *irisas* is 4,6. **No
  blood-product or albumin bar** for the four HEMATOGENAS packs. **The only filled dark chocolate row is
  specified as mint** (`Dark chocolate, filled with mint confectionery`), so any other filled dark bar borrows
  it. **No compound-coating row anywhere**, which is the cross-cutting bullet above.
- Chocolate tablets, from `lt/30`'s second shelf: **no aerated chocolate row.** Five *porėtas* / BUBBLY blocks
  go to the plain row of their type — the bubbles change the density and not the composition, so this gap costs
  nothing, unlike the rest. **No milk-chocolate-with-caramel-pieces row**: KARL FAZER's 12% caramel and its 17%
  dragee both fall back on `Milk chocolate bar`. **No filled WHITE chocolate row at all**, and the only white
  row with inclusions is `White chocolate bar, with dried fruits (nuts, almonds, raisins, praline)`, so two
  KALEV blueberry blocks sit on a nut-calibrated row and pay about 10 g of fat for it. **The filled-dark gap
  above has its second instance here** — PERGALĖ's mango-filled block takes the mint row, as ROSHEN's did.
  **No sugar-free tablet on this shelf**, but note that the branch does carry `Dark chocolate bar, without sugar
  and with artificial sweeteners` and `Milk chocolate bar, without sugar, with sweeteners` if one turns up.
- Sweets and dragées, from `lt/31`: **no hard caramel.** `Caramel, soft` is the only caramel L3 and four of the
  shelf's seven caramels are boiled, not chewed — WERTHER'S classic is 8,6 g of fat on a 9,98 g row and passes,
  but the row's inventory is a chewy European butter caramel throughout. **No pressed-sugar tablet**: PEZ takes
  the boiled sweet and carries 1,6 g of the hardened palm the press needs where the row has 0,18. **No
  sugar-and-chocolate coated ALMOND** to match `Sugar and chocolate coated peanut`, so three almond dragées
  under white chocolate go to `White chocolate bar, with dried fruits …` and lose the shell. **No coconut
  confection** outside `Coconut bar, with chocolate coating`, which is a Bounty and asserts a chocolate coat —
  RAFFAELLO and its KIDDY imitation have none, so they take `Wafer biscuit, filled with nuts …, with chocolate
  or not, prepacked`, which keeps the wafer, the almond and the absence of chocolate and loses only the
  coconut. **No glazed or chocolate-coated pretzel**, salted or otherwise. And **the dragée L3 holds one food
  and it is an almond**, which is where the file's only two C's come from.
- Sweets in boxes, from `lt/31`'s second shelf (SH-12-4-14): **no truffle row anywhere in SAFAD.** The word
  appears once in the whole database and it is a raw white pudding in the meat branch; PERGALĖ CLASSIC is a
  coconut-fat ganache at 45 g of fat with 40 g of it saturated, and the nearest row is
  `Chocolate confectionery or bar, with dairy filling`, calibrated on a chocolate shell round a cream.
  **No liqueur-filled chocolate**: five Pergalė packs are a sugar-syrup fondant with 3 to 3,6% ethanol inside
  dark chocolate, at 9,4 to 13 g of fat, and `liqueur` in SAFAD is a drink — so they take the filled-dark-is-
  mint row, which with the mango jelly, the berry jelly and `lt/30`'s ROSHEN brandy bar puts **eight rows on
  that one row**. The shelf does at least carry the first product the row was written for, RIMI ROYAL MINTS.
  **No bird's-milk or foamed-soufflé centre**: the agar-set butter foam takes the dairy-filling row, which is
  built on a fat cream. **No row for a box of assorted chocolates as such** — every assortment is mapped on
  what its declared fillings are, which is the split below.
- Other sweets, from `lt/31`'s third shelf (SH2-24-134): **no halva and no seed confection**, which `lt/30` first
  recorded for a sesame bar and the Greek shelves for pasteli. Seven RIMI packs are 57 to 62% peanut or
  sunflower-seed mass boiled into glucose syrup, and the nearest food in `Confectionery (non-chocolate)` is
  `Nougat` at 13,9 g of fat against their 32 to 39, so all seven grade C on the fat alone; three shelves in two
  languages now agree about where the hole is, and the row carries both the confection it is named for (the Greek
  *mandolato*) and the ones it stands in for. Routing the halva by species would be worse than keeping them
  together: it splits one shelf's halva across two L1s and puts the sunflower packs on `Sunflower seed` at 2700 kJ.
  **No chewing gum anywhere** — the word appears nowhere in the database, not the confection, not the base, not
  the resin. The shelf's three gums are therefore **dropped rather than mapped**, the first products in the corpus
  to be: the only rows that could take them model a sweet that is swallowed, and a gum is chewed and spat out, so
  any row would put a footprint on a food nobody consumed. **No pressed-fruit roll**: a bar that is fruit and
  nothing else has to go to a dried fruit of its head species, which `Dried fruits` holds for the apple and
  not for the mango. The four toy eggs are the compound-coating thread again in a milder
  form: KINDER JOY leads with sugar, palm and shea fat and declares cocoa at 4%, and every cocoa row SAFAD offers
  carries more cocoa than that — but cocoa mass and cocoa butter are both on the card, so it is not the cocoa-free
  coating `lt/30` and `lt/31` recorded.
- Cured meat: cured pork loin (lomo, välisfilee, seljafilee, *nugarinė*), cooked beef ham of any kind (the whole
  `Beef, dried` L3 is one row, Bresaola, air-dried at 5,15 g salt and 32,8 g protein — so a wet cooked beef ham
  at 340 kJ sits on a dried whole muscle at 725), any cut axis for cooked ham or for cured poultry, cured or
  cooked pork tongue, cured jowl, cured ribs, olives or cheese as an inclusion, and truffle as a flavouring.
  A **multi-component charcuterie tray** has no row for the blend either: map on the head component and grade C.
- The VIKIS farm store (`lt/32`, SH-18) adds the farm end of the range. **No coffee imitate but chicory**: no
  roasted acorn, no dandelion root, no burdock, and the two chicory rows carry identical figures for the powder
  and for the drink made from it, so a ground root sold to be brewed takes the powder. **No sea buckthorn**
  anywhere, berry or preserve, so a *šaltalankių tyrė* takes the nearest berry jam under the rule `lt/18` set for
  blackcurrant and lingonberry. **No onion confit, chutney or vegetable jam of any kind**, so a caramelised-onion
  condiment sits on `Onion, cooked` and its sugar and its oil — which are the whole of it — are unmodelled. **No
  unfried potato snack**: both potato-snack rows are fried, at 23 and 28,8 g of fat, so an extruded potato crisp
  joins the chickpea and maize ones on the puffed-cereal cake and crosses an L1 to do it. **No spit-baked cake**
  for a *šakotis*, which takes a pound cake and is richer than any cake row in the database. And **no pickled
  vegetable salad**, so a beetroot *mišrainė* keeps its species and loses the cure.
- The Honest Bite store (`lt/33`, SH-30) adds the free-from end of the range, and its gaps cluster in three
  places. **No polyol and no fibre isolate anywhere**, which `lt/26` already recorded for erythritol and xylitol
  and which this shelf meets again with inulin and psyllium husk — the two fibre powders are the only rows here
  left blank, under `Products for special nutritional use / Dietary supplements`. **No konjac and no shirataki**:
  a glucomannan noodle that is 96,6% water takes `Dried pasta, gluten-free, cooked, unsalted`, which is the
  nearest hydrated pasta, and the water is the whole gap — five packs here, after the two `lt/15` already had.
  **No MCT powder, no coffee creamer and no plant latte**: a matcha latte built on coconut fat cannot follow
  `lt/25`'s dairy matcha lattes to the flavoured dairy drink, because its base is not milk, so what is left is
  the green tea its name states and SAFAD holds only as the infusion. Also absent: any **almond oil** (the
  `Walnut oil` L3 holds `Hazelnut oil` alone, as `lt/19` found for walnut), any **grain-free granola** (every
  `Muesli` row is built on cereal flakes, so a seed-and-nut keto granola crosses to `Mix of unsalted grains/nuts
  and dried fruit`), and any **sweetened nut spread that is not chocolate-hazelnut** — SAFAD carries exactly two
  nut spreads, a plain peanut paste and `Chocolate spread with hazelnuts`, so a cashew-and-date cream takes the
  second for being sweetened and the row names two things the jar does not hold.

## Per-category rules

Kept compact; the per-row Comments carry the detail.

### Milk, cream and fats
- EU fat brackets: whole ≥3,5%, semi-skimmed 1,5-1,8%, skimmed ≤0,5%.
- Storage temperature gives the processing: max 6 °C ⇒ pasteurised, max 25 °C ⇒ UHT, even when the name omits it.
- Cream couples texture with processing (`Thick … refrigerated` = crème fraîche épaisse, i.e. soured; `Liquid … UHT`)
  and offers no liquid-refrigerated cream, so a pasteurised whipping cream keeps its texture and accepts the UHT
  mismatch. **Texture first, then the fat class in two steps: a percentage that falls inside a row's named band
  takes that row outright; only a percentage in the gaps goes to the nearest row by energy.** Lithuanian *grietinė*
  is soured and takes the thick rows, *grietinėlė* the liquid ones. The bands leave three holes — 9-14%, 21-29% and
  everything above 30% — and the shelf is full of them.
  **Nearest means nearest in percent, not in kilojoules.** The five Estonian 10% *kohvikoor* rows originally took
  `cream, light, 4 to 8% fat` on the reasoning that 500 kJ is closer to 306 than to 783 — true in absolute terms,
  and the wrong comparison: +64% against -36%. An absolute-distance tie-break systematically prefers the lower row,
  because the same gram of fat is a larger share of a leaner archetype. Both Baltic files now put 10% coffee creams
  on `Liquid cream, light, 15-20% fat, UHT` at C (Nikos approved realigning the Estonian rows, 2026-09-10), which
  leaves `cream, light, 4 to 8% fat` used by **no row in any of the 29 files** — nothing on either shelf is that
  light.
- Sweetened aerosol cream leaves the dairy branch entirely:
  `Snacks, desserts, and other foods / Ices and desserts / Custard / Whipped cream or Chantilly cream, under pressure, UHT`.
- Mascarpone also leaves it, for `Cream and cream products / Cream / Thick cream 30% fat, refrigerated` — it is cream
  plus acid at 0,1 g salt, and every fresh-cheese row above 30% fat is a flavoured one. Same treatment as Cretan staka,
  and as Lithuanian **kastinys**, a whipped soured cream with butter, garlic and salt: the thick 30% row matches its
  energy to 5% and carries 0,07 g salt against the pack's 1,5, since SAFAD has no seasoned cream spread.
- **Where SAFAD splits a row into home-made and prepacked, the pack's own form decides, not the nearest composition.**
  A jarred béchamel shelved with the creams takes `Herbs, spices and condiments / Savoury sauces / White sauce
  (Bechamel sauce, Cheese sauce) / Bechamel sauce, prepacked` and grades C at +36% on energy, even though the
  home-made row would match it to 4% — the product is a jar, and the eval reads the name of a jar.
- Fats and oils is the best-served L1 (25 of 37 rows graded A): butter at 82/80/60/40% × salted/unsalted, butter oil,
  lards, a full margarine grid. The Lithuanian butter shelf confirms it — 27 of 34 rows at A, thirteen 82% butters
  landing within 1,5% on energy and four *lydytas sviestas* (ghee) on `Butter oil or concentrated butter` within 1,5%.
- **Saturates are the tie-breaker *within* a fat bracket, not against energy.** Where energy and fat already single
  out one row, they decide and the saturates gap goes in Comments: a 40% light margarine matches
  `Vegetable fat (margarine type), spreadable, 30-40% fat, light, unsalted` almost exactly on both, while its 12 g
  saturates would point at the omega-3 row that is 20% out on energy.
- **For salted butter the salt axis outranks the fat percentage.** An 82% lightly salted butter takes
  `Butter, 80% fat, lightly salted`, not the 82% unsalted row: the fat gap is 2 g, where the unsalted row's 0,04 g
  of salt is twenty-five times out. Grade B, because the pack's 1 g of salt is still half the row's 1,9 (the only
  other salted row holds 2,52).
- **Two margarine rows are unusable as written.** `Vegetable fat (margarine type), 70% fat, unsalted` and its 80%
  sibling store *identical* nutrition (2670 kJ, 72,1 g fat, 32,6 g saturates), so only the name separates them; and
  `Vegetable fat (like margarine), 80% fat, salted` stores 70 g of fat, its name and composition disagreeing.
  Prefer the margarine-type rows and say which was avoided.
- Watch for names their composition betrays: `Spray margarine Keiju` is 100% fat / 8 g SFA (i.e. rapeseed oil);
  `Või Mlekovita` reports 99,8 g fat (i.e. concentrated butter, not 82% butter).
- **The Baltic fat percentages never match an EU bracket, and the lower bracket is not automatically right.** 2,0%
  and 2,5% milks take the semi-skimmed row (nearest on energy by a wide margin), and 3,2% takes the whole row —
  both at B, with the bracket named in Comments. A 1,0% milk is the one genuine coin-toss: the skimmed row is
  marginally closer on energy (146 kJ to the pack's 171) but holds 0,02 g fat, so the semi-skimmed row is the better
  reading of the fat the *name* declares. Decide on the attribute the name states, not on energy alone.
- **Lactose-free splits by fat class, because only the semi-skimmed row exists.** `Milk, semi-skimmed, reduced
  lactose` names no processing, so it absorbs pasteurised and UHT alike; a lactose-free *whole* milk has no row and
  falls back to plain whole milk at B. A2 milk (beta-casein variant) is not an axis SAFAD encodes either.
- **Sweetened condensed milk is one of the exact matches in the database** (1380 kJ, 7,87 g fat, 56,5 g sugars):
  four Lithuanian packs land within 1,4% on energy, the caramelised one included, since it is the same 55% milk and
  45% sugar. But a pack sold as **salted caramel** leaves the dairy branch for
  `Sugar and confectionary / Confectionery (non-chocolate) / Caramel, soft / Dulce de leche or confiture de lait`
  even though its macros are nearly identical — sugar leads its ingredient list, it declares 1,2 g salt against
  0,32 and 2,5 g protein against 6,84, and the head noun says caramel, not milk.
- **Flavoured milk names only chocolate and strawberry.** Every other flavour — banana, blueberry, vanilla, toffee,
  caramel — takes the generic `Dairy drink or fermented milk or yogurt, flavoured, with sugar` at B, and the flavour
  goes in Comments. Vitamin fortification is in two of the three rows' names, so a *fortified* pack on the matching
  row grades A while an unfortified one grades B. Reduced-sugar and protein-isolate rebuilds are C: the Arla
  reduced-sugar cocoa carries 5,6 g protein against 3,02 and 5,2 g sugars against 10,3.
- **The `Kefir` L3 holds two rows, skimmed and whole, and the Baltic standard is neither.** A 2,0-2,5% kefir takes
  the whole row at B (within 7% on energy, and its 3,20 g fat is only 0,7 g above a 2,5% pack) with the gap named in
  Comments, exactly as the plain milks do. But **the whole row stores 3,20 g fat**, so a 3,2% kefir is an exact
  composition match and grades A despite the row's "whole milk" label — read the row's numbers, not only its name.
- **Cream-enriched kefir leaves the kefir L3.** The *šaltibarščiai* kefirs and soured milks at 5-6% fat have nothing
  in `Kefir`, which tops out at 3,20 g, so they take
  `Fermented milk products / Yoghurt, cow milk, plain / Yogurt, fermented milk or dairy specialty, plain, w cream` —
  whose own name says fermented milk with cream, so this is not a move out of the family, only out of the L3 label.
- **Where two rows both name a fermented milk, composition picks between them.** `Dairy drink or fermented milk or
  yogurt, flavoured, with sugar` (0,89 g fat) takes the 0,3-1,8% kefir shakes and fruited buttermilks; `...with
  fruits, with sugar` (2,49 g) takes the 3,1% Activia. Neither choice is name-reachable over the other, which is
  what makes composition the fair tie-breaker rather than a violation of the governing rule.
- **Curd, cottage cheese and curd cheese are three products the shelf keeps apart and SAFAD does not.**
  *Varškė* (quark) is drained, so it carries 14-17,5 g protein where the whole `Fresh uncured cheese` L3 tops out
  at 9,9: pick the row by the fat class the name states (`Petit-Suisse like fresh cream cheese, plain, around 9%
  fat` for the standard 9%, the fat-free row for a 0,5% *liesa*) and report the protein gap. Lean curd is the worst
  served — almost pure protein, so its energy runs nearly half above the fat-free row, and all three grade C.
  The one pack that fits properly is a President 4% built with cream and milk powder rather than drained: 9,7 g
  protein against 9,71.
- **`Uncured soft cheese, spreadable, around 20% fat, in a tub` is the cottage-cheese row, and its name is wrong.**
  It stores 7,48 g fat, 10,8 g protein and 0,92 g salt — a cottage cheese in all but name, which is why the
  Estonian file already had 19 rows on it. The 7% Lithuanian *grūdėta varškė* lands within 9% of it on energy with
  salt matching to 0,02 g. Fruited cottage cheese leaves it for `Fresh cream cheese, with fruits, creamy, with
  sugar, around 7% fat`, which the five 6,1% fruit cups match to within 4%.
- **A pressed curd cheese leaves the fresh L3 once it passes the fresh family's fat ceiling.** *Varškės sūris* runs
  13-27,5% fat where plain fresh rows stop at 9,9 g. Below Feta-type's 22,6 g it stays fresh — `Mozzarella cheese,
  from cow's milk` for the mild ones, whose 0,1-0,2 g salt is the tell, and `Feta-type cheese from cow's milk` for
  the salted 22% ones. Above it, `Firm cheese, around 27% fat, Maasdam-type cheese` matches energy, fat, protein
  and salt at once and a pressed fryable cheese is firm whatever rind it lacks; stretching Feta by 31% on energy to
  keep the word "fresh" would be the worse error. Say in Comments that the pack is fresh rather than ripened.
- **Storage temperature is worth checking mechanically, not by eye.** Max 6 °C is pasteurised and max 25 °C is UHT;
  cross-checking all 62 rows against their archetype's own processing word caught one error — a lactose-free
  MARGĖ 3,2% filed as pasteurised because it sits on the pasteurised shelf, while its pack is ambient-stable.

### Cheese
- 132 archetypes over 52 L3 groups, nearly all named French PDOs, with no generic semi-hard cow's-milk cheese.
- **Estonian/Baltic semi-hard block cheeses all take `Cheese, Gouda / Gouda cheese, from cow's milk`** — Eesti,
  Atleet, Hollandi, Vene, Tilsit, Saaremaa, Saare Leet, Old Saare, Leibjuust, Hiirte, Bresto, Salami, Oltermanni,
  Kadaka, "täispiimast". They are Dutch-type washed-curd cheeses at 24-33 g fat against the archetype's 30,2. This
  follows the kritis precedent (Regato and the 4-cheese mix → Gouda). Reserve `Edam` for packs that say Edam. B when
  the name says a plain ripened type, C when brand-only.
- Fat percentages in Estonian cheese names are **fat in dry matter**, not as-sold fat: 45% FDM ≈ 25 g, 48% FDM ≈ 28 g.
- Light semi-hard → `Cheese, Maasdam / Firm cheese, around 14% fat, … reduced fat` (the leanest firm cheese there is).
- Unnamed hard grating cheeses (Valio Forte, Džiugas, "kõva juust 12/18/24 kuud") → `Grana Padano` on protein
  (33-35 g vs 33,9), not Parmesan (31,1). Parmesan is for packs saying Parmigiano Reggiano.
- Grilling cheese (grilljuust, praejuust, halloumi-style) → `Cheese, Saint Paulin / Saint-Paulin cheese, from cow's
  milk (semi-hard cheese)`, the only row whose own name declares it a generic semi-hard cow cheese. Deliberately not
  Gouda, since grilljuust does not melt.
- `Cheese, Manchego` holds **no Manchego** `food_product`; its generic ewe row is the intended slot (A when the pack
  says Manchego). Beware its broken stored data, above.
- Cream cheese: plain toorjuust shares the kodujuust tub row (the 20% and 25% variants are data-identical — always
  use the 20% one); flavoured → the 30-40% flavoured row, which matches both texture and flavouring; Light → `Fresh
  cream cheese, plain, creamy, around 8% fat`; 0,2% → `… plain, fat free` (A); sweetened → `… with fruits, creamy,
  with sugar, around 7% fat`.
- Salt separates cottage cheese (kodujuust, 0,4-1,2 g) from the Petit-Suisse family (0,10 g).
- **`Gouda` and `Grana Padano` are the two catch-alls, and between them they carry nearly a third of a Baltic cheese
  shelf.** On rimi.lt: 60 rows on Gouda (every *fermentinis*/*puskietis* block cheese, Tilsit, Havarti, the
  flavoured and fruited ripened cheeses) and 41 on Grana Padano (every *kietasis sūris* — Džiugas, Rokiškio Grand,
  Forte, Parnidžio — chosen on protein at 33-34 g against 33,9, never on energy). **Protein decides which:** a
  grating cheese runs 33-34 g, a block cheese 23-28 g. A 28 g protein "hard cheese" (Vilvi Memel) is a block
  cheese and goes to Gouda.
- **A grating *mix* is not the cheese it is named after.** Rimi's pizza and pasta mixes lead with Džiugas but are
  10-22% of it, so the block cheese in the blend decides; only the 98%-Džiugas pack takes the hard-cheese row.
- **String cheese, dried cheese and baked cheese pieces all fall on the mozzarella and hard-cheese rows at C.**
  *Plėšomos sūrio lazdelės* is pasta filata, so the mozzarella row is the only family it can join, at +31% energy;
  dried cheese balls (Džiugas, Granarolo) run 2296-2437 kJ and 43-48 g protein, above every hard-cheese row,
  because water removal is not an axis SAFAD carries.
- **Filled cheese is blank.** Where palm oil replaces the milk fat (a Tilsit-type product at 28,2% palm oil, a
  Delaktis cheese product at 22%), nothing fits: the plant-based cheese rows carry 0,25 g protein because they are
  dairy-free, and every ripened row carries milk fat. The fat source is exactly the axis a footprint turns on, so
  this is a blank, not a stretch. The tell is saturates far below half the total fat.
- **Mascarpone leaves the cheese L2 entirely** for `Cream and cream products / Cream / Thick cream 30% fat,
  refrigerated` — it is cream, milk and acid with no salt and 3,4-4,4 g protein. A 35% one lands at B, a 40% one at C.
- **Burrata and stracciatella are C, not B.** They take `Mozzarella cheese, from cow's milk` because it is the only
  pasta-filata row, but the cream halves the protein (8 g against 16,5), which is a material discrepancy and not a
  missing axis. The two `rimi/et/02_cheese` rows were graded B and have been aligned to C; the products are the same
  two, since Rimi shares product ids across countries.
- Lithuanian glossary: **sūris** = cheese, **kietasis sūris** = hard cheese, **puskietis** = semi-hard,
  **fermentinis sūris** = rennet (ripened) cheese, **lydytas sūris** = processed cheese, **sūrelis** = a small
  processed cheese, **tepamas/užtepėlė** = spreadable/spread, **varškė** = curd/quark, **varškės sūris** = pressed
  curd cheese, **šviežias sūris** = fresh cheese, **kreminis sūris** = cream cheese, **pelėsinis sūris** = mould
  cheese, **mėlynasis/melsvasis pelėsis** = blue mould, **baltasis pelėsis** = white mould, **olandiškas** =
  Dutch, **sūris salotoms / salotų sūris** = salad cheese, **ožkų pienas** = goat milk, **avių pienas** = sheep
  milk, **tarkuotas** = grated, **pjaustytas/raikytas/riekelėmis** = sliced, **rūkytas** = smoked, **brandintas**
  = matured, **plėšomos sūrio lazdelės** = string cheese sticks, **sūrio gaminys** = cheese product (i.e. filled),
  **užkepamas/kepamas/sūris kepimui** = cheese for baking or grilling, **be laktozės** = lactose-free,
  **rieb. s. m.** = fat in dry matter, **mėn.** = months, **išrūgos** = whey, **raugas** = starter culture.

### Eggs
- The whole `Eggs and egg products` L1 is 13 rows: whole/white/yolk chicken × raw/cooked, duck, a `Goose eggs` L3
  holding goose + turkey, and three powders. **No husbandry, size or class axis at all** — free-range, barn-laid,
  organic and cage, sizes M and L, class A: none of it is an axis, so every carton of fresh hen eggs collapses
  onto one row even though husbandry is the main claim on the box. The most certain mapping in the dataset and
  the least informative one.
- All hen-egg packs → `Whole egg, chicken / Egg, raw`, A. Quail → `Turkey egg, raw`, ostrich → `Goose egg, raw`, both C.
  The quail C is **not** about the numbers: quail at 12,6 g fat and 699 kJ is within 1% of the turkey row on
  energy, a better fit than most A grades in the file. A species with no row of its own is C regardless.
- Liquid egg-white masses → `Egg white, chicken / Egg white, raw`, A, even with preservative and gums.
- Expect non-eggs on the egg shelf. rimi.lt files pressed baker's yeast under `other-egg-products`; it stays in
  the file (the ground truth records the shelf) and is blanked at `Herbs, spices and condiments / Baking
  ingredients` depth, because SAFAD's only leavening row is a chemical one.

### Yoghurts and desserts
- The fermented-milk L2 is unusually well stocked (27 rows) and **its axes are name-visible**: plain /
  plain-with-cream / flavoured-with-sugar / flavoured-with-cream / with-fruits / with-fruits-with-cream /
  with-cereals / with-chocolate-shavings. "Koorene jogurt" and "Koorejogurt" mean the with-cream variants; a named
  fruit means the with-fruits rows, a named flavour the flavoured rows. Most A grades come from here.
- **Drinkable yoghurts are not in that L2** — they are
  `Milk based beverages / Flavoured milk / Dairy drink or fermented milk or yogurt, {with fruits | flavoured}, with sugar`.
- **Actimel has its own row**: `Fermented milk products / Acidophilus milk / … with L Casei`, matching it almost
  exactly. A for the plain one, B for the fruit ones.
- `Yogurt, Greek-style, plain` and `… on a bed of fruits` sit under the L3 `Yoghurt, sheep milk` although the entries
  name no species — use them for cow-milk Kreeka jogurt anyway.
- **High-protein fermented dairy (skyr, proteiinijogurt, protein puddings and mousses) goes to the Petit-Suisse rows
  in `Cheese / Fresh uncured cheese`**, matched on fat: `plain, fat free` below ~1 g, `plain, around 4% fat` above.
  Plain skyr matches `plain, fat free` on every axis (237/0,2/3,5/10 against 240/0,10/4,20/9,90), so B; flavoured
  ones are C, because every flavoured Petit-Suisse row carries 11-12 g added sugars against their 2-5 g.
- Kohuke (glazed curd bars) → `Grains and grain-based products / Fine bakery wares / Pastries and cakes /
  Cheesecake, refrigerated`, C. The cheesecake row matches energy, fat and sugars and is dairy-dominated like the
  product, where the chocolate-bar-with-dairy-filling rows run 1870-2300 kJ; its 5,44 g protein is half the
  product's. Same choice as kohupiimavorm.
- Kinder Pingui / Maxi King / Milk Slice and sponge bars → `Chocolate coated confectionery / Chocolate snack bar,
  dairy filling with sponge cake`, which describes Pingui exactly.
- **Milk puddings map on the named flavour**: `Ices and desserts / Custard` carries vanilla, chocolate, caramel and
  coffee refrigerated desserts, all within ~50 kJ of the Estonian puddings — a clean block of A grades. No fruit
  variant exists.
- Cream desserts → `Chocolate mousse / Mousse (chocolate, coffee, caramel or vanilla) topped with whipped cream,
  refrigerated`; A when the flavour is in the archetype's own list.
- Riisipuder / mannapuder → `Starchy pudding / Rice pudding, refrigerated` and `Semolina pudding, refrigerated`, A.
- **There is no whole-milk plain yoghurt row.** `Yogurt … plain` carries 1,68 g fat and `… plain, w cream` 8,5 g,
  with nothing between, so the Baltic 3,8-4,3% plain yoghurt sits in the hole and grades C at +38 to +41% on
  energy. A 2,5% one is A on the plain row and a 6% one is B on the with-cream row; only the middle is homeless.
- **A Greek claim in the name outranks the composition.** Every *graikiškas jogurtas* and every pack of the
  GRAIKIŠKA AMFORA range takes the Greek-style rows, including the leaner 2,4% line, which would otherwise sit
  within 15% of the ordinary fruit-yoghurt row (318 kJ against 375) instead of 39% under the Greek fruit row. The
  eval sees the name and the name says Greek; the grade carries the cost.
- **The bifidus rows are deliberately unused, and the L Casei row is deliberately used.** SAFAD has three
  `Fermented milk or dairy specialty, yogurt type, … with bifidus` rows that duplicate the plain, flavoured and
  with-fruits yoghurt rows at almost the same composition (369 kJ against 375). Splitting a brand's range by
  whether "bifido" survived Rimi's name truncation — ACTIVIA ships both spellings — would make the ground truth
  arbitrary, so all of them take the ordinary rows with a note. `Dairy drink … plain, with sugar, with L Casei` is
  different: there the row models Actimel's whole *format*, a small sweetened fermented-milk shot, not the strain.
  Both files agree on this.
- **A plain drinkable yoghurt has no row in the beverage L2** — those are all flavoured or fruited. It takes the
  species- and culture-neutral `Fermented milk drink, plain, whole milk` from the `Kefir` L3 instead (2,4 g fat and
  226 kJ against 3,2 g and 220 kJ), which is the same move as *pasukos* on the fermented-milk shelf.
- **Curd bars split in two.** *Varškės sūreliai*, glazed, are the kohuke gap: 55 rows on `Cheesecake,
  refrigerated`, class `'x'`, energy matching to within a few percent while the row carries 5,44 g protein where
  the family runs 10-13. The **unglazed** curd bars (RYTAS, A2, 7-8% fat) are a different product and take
  `Fresh cream cheese, with fruits, creamy, with sugar, around 7% fat` on fat, at B.
- Water-set fruit jelly (*želė*, galaretka) has no row: the whole `Gelatine dessert` L3 is milk jellies, so a
  dairy-free jelly at 0 g fat and 1,9 g protein takes `Milk jelly, flavoured, refrigerated` at C.
- Expect non-dairy on the yoghurt shelf: two organic fruit-purée pouches (SMUSHIE) sit among the plain yoghurts and
  take `Fruit and fruit products / … / Fruits puree, without sugar added`.
- `Ices and desserts / Custard` also holds **crème brûlée** and, in `Pastries and cakes`, **profiteroles with a
  custard filling** — both named exactly, so a Lithuanian dessert-aisle crème brûlée or profiterole pack is a
  clean mapping.
- Lithuanian glossary: **jogurtas** = yoghurt, **geriamas jogurtas / jogurto gėrimas** = drinking yoghurt,
  **graikiškas** = Greek, **natūralus / be priedų / be prieskonių** = plain, **su …** = with (real fruit),
  **… skonio** = …-flavoured, **su dribsniais** = with flakes, **su grūdais / su javainiais / granola** = with
  cereals, **varškė** = curd/quark, **varškės sūrelis** = glazed curd bar, **varškės kremas** = sweetened curd
  cream, **varškytė** = a small sweetened curd, **pasukų varškė** = buttermilk curd, **pudingas** = pudding,
  **putėsiai** = mousse, **desertas** = dessert, **želė** = jelly, **ryžių košė** = rice porridge,
  **batonėlis** = bar, **glaistytas** = glazed, **be laktozės** = lactose-free, **su saldintuvais / sald.** =
  with sweeteners, **less sugar / mažiau cukraus** = reduced sugar, **plombyras** = a rich ice-cream flavour,
  **baltyminis** = high-protein, **ekologiškas / ekol.** = organic.

### Goat and sheep dairy, and the Italian fresh cheeses
From `talu-toidab-dairy-products-eggs/SH-19-1`, a farmer's-market category: three small dairies (Nopri, Andri-Peedo,
Viinamärdi, Pajumäe) whose ranges are goat, sheep and Italian-style.

- **SAFAD does carry a species axis, but only where it happens to have put one, and it is shallow.**
  Goat: `Liquid milk / Goat milk` (whole raw, whole UHT, half-skimmed UHT), `Fermented milk products /
  Yoghurt, goat milk` — **exactly one row**, `Yogurt, goat's milk, plain, around 5% fat` — and a rich cheese
  set (`Cheese, Capra`, `Cheese, Chevres`, `Cheese, Buche de Chevre`, `Cheese, Pouligny-Saint-Pierre`, plus a
  goat row in the bloomy-rind L3). Sheep: `Liquid milk / Sheep milk` (one row), `Yoghurt, sheep milk` (three
  rows), and the Manchego / Roquefort / ewe-Camembert cheese rows. There is **no goat or sheep row anywhere
  else in the dairy branch** — no cream, no butter, no kefir, no quark, no flavoured yoghurt.
- **So a goat or sheep product usually has to trade form for species, and species wins.** SAFAD splits form at
  L3 (`Kefir` against `Yoghurt, goat milk`) and species within it, so goat kefir can keep one or the other:
  it takes `Yogurt, goat's milk, plain, around 5% fat`, because species is what moves the footprint and
  kefir-against-yoghurt is a starter culture. Same for flavoured goat and sheep yoghurt — the species L3s have
  no with-fruits or flavoured row, so the plain species row absorbs 10-13 g of sugars against its own 2,5-3,1.
- **The species-silent Greek rows are reserved for cow milk.** `Yogurt, Greek-style, plain` and
  `… on a bed of fruits` sit inside `Yoghurt, sheep milk` but name no species, and 04 already uses them for
  cow-milk Kreeka jogurt. A real sheep yoghurt therefore takes `Yogurt, Greek-style, ewe's milk`, the one row
  that names the species — even though the plain one's energy matches `Yogurt, Greek-style, plain` at 428
  against 427, and the three flavoured ones match `… on a bed of fruits` on energy and fat to within 2%.
- **No pasteurised goat milk row.** `Goat milk, whole, raw` matches a pasteurised 3,2% pack to the decimal
  (238 against 240 kJ, 3,2 against 3,20 g fat) and pasteurisation barely moves composition, while UHT is the
  more different process — so pasteurised goat milk takes the raw row at B, not the UHT one.
- **When only the ingredient list gives the species, name-reachability outranks *species wins*.**
  `Valgehallitusjuust Andri-Peedo 120g` is made from raw goat milk, and
  `Soft-ripened cheese with bloomy rind, from goat's milk` matches its energy at 1200 against 1209 — but the
  name names no species, and which dairy milks goats is brand trivia the eval cannot ask for. It takes the
  generic `Soft-ripened cheese with bloomy rind (Camembert-type cheese)` instead. *Species wins* ranks the
  attributes the **name** supplies; it does not license reading the species off the ingredient list. This is
  not the same as the three fish labels that **contradicted** their ingredient lists: following a false name
  would encode a falsehood, following a silent one only leaves an axis unset.
- **`Cheese, from goat's milk` carries the whole kitsejuust range**, from a young rennet-set farm cheese at
  17,3 g fat to a dry aged one at 36,9. `Cheese, dry, from goat's milk` (1830 kJ / 35,6 g fat / 30,5 g protein)
  matches the aged one within 8% on every axis, but nothing in `Kitsejuust Tenente` says dry or aged — the
  governing rule again, so both stay on the plain goat row and the spread goes into the grade.
- **No burrata and no stracciatella row.** Both take `Mozzarella cheese, from cow's milk`, the only pasta-filata
  row there is, at C: they are mozzarella plus cream, so energy runs 46-49% and fat 62-79% over it while burrata's
  protein still matches within 2%. `Uncured soft cheese, spreadable, around 30-40% fat, flavoured` matches
  stracciatella on almost every axis (1320 / 30,8 / 20,2 / 8,03 / 1,24) but is a flavoured spread.
  **Ricotta stays blank** — a whey cheese is a different process, and the whey-cheese gap is already recorded.
- **Panna cotta has its own row**: `Snacks, desserts, and other foods / Ices and desserts / Custard /
  Panna cotta, refrigerated`, and three of Nopri's four land within 15% on energy and 8% on fat — A grades.
  A "Panna Cotta Tiramisu" is still a panna cotta: the head noun decides, and `Tiramisu, refrigerated` describes
  a sponge-and-mascarpone dessert. A whey-protein reformulation ("Fitlap") is B — fat 44% under, protein triple.
- **Kissellimaius** = a curd cream under a starch-set fruit-kissel layer → the `Petit-Suisse … with fruits,
  2-3% fat` row, as the Alma and Emma kissel creams in 04.
- **A high-protein quark cream grades a step below a mainstream one on the same row.** Pajumäe's organic
  kohupiimakreem carries 11-12 g protein against the with-fruits Petit-Suisse row's 6,39 and lands 28-45% over
  it on energy, where Nopri's 6,6 g-fat creams match `Fresh cream cheese, with fruits, creamy, with sugar,
  around 7% fat` on energy and fat almost exactly. Same family, different grade, and the difference is real.
- **Small dairies publish one nutrition block per range.** All six Nopri Goudas and all five Nopri
  kohupiimakreem flavours carry byte-identical macros. Convenient for family consistency, but the numbers are a
  range average rather than a per-flavour analysis — say so rather than reading them as a product fact.
- **kcal in the kJ field, four more times**: Pajumäe's kohupiim (106), rasvata kohupiim (88) and mahejogurt (65)
  all print their kcal figure as kJ, and Andri-Peedo's kitsejuust prints 6067 kJ, which is nonsense either way
  (its macros give about 996). Recompute from the macros and say which figure was used.
- Estonian glossary: **kitsepiim** = goat milk, **lambapiim** = sheep milk, **toorkitsepiim** = raw goat milk,
  **pastöriseeritud** = pasteurised, **hapupiim** = soured milk, **keefir** = kefir, **kohupiim** = quark/curd,
  **kohupiimakreem** = sweetened quark cream, **kodujuust** / **talukodujuust** = (farm) cottage cheese,
  **valgehallitusjuust** = white-mould cheese, **kohoke** = Nopri's spelling of kohuke (glazed curd bar),
  **kissell** = starch-set fruit gel, **maius** = treat, **rõõsk koor** = sweet (unsoured) cream,
  **vahukoor** = whipping cream, **vadak** = whey, **laap** = rennet, **juuretis** = starter culture,
  **mahe** = organic, **rasvata** = fat-free, **vutimuna** = quail egg, **mahemuna** = organic egg,
  **murulauk** = chives, **karulauk** = ramson, **lambalääts** = fenugreek, **kreeka pähkel** = walnut,
  **kukeseen** = chanterelle, **must küüslauk** = black garlic, **digestive küpsis** = digestive biscuit.

### Fresh produce: fruit, vegetables, herbs and fungi
The best-served part of the database, and the easiest to get wrong at the top level. From `kritis` 15 and 16 and
Rimi's `fruits-vegetables-flowers/SH-12` (287 food products, 186 of them A).

- **The L1 is not where you would guess.** Four traps, all of them SAFAD's botany rather than the shop's layout:
  - **Melons and watermelons are cucurbits, so they sit under `Vegetables … / Fruiting vegetables`**, next to
    courgette and pumpkin — not under Fruit.
  - **Potatoes and sweet potatoes are `Starchy roots and tubers`**, with their own `Potatoes and potatoes
    products` and `Other starchy roots and tubers` L2s — not vegetables.
  - **Green beans, garden peas and chickpeas are `Legumes, nuts and oilseeds`.** Note the L3 names lie about
    state: `Garden peas, raw` lives under `Peas (dry), soaked/boiled/canned`, and `Green peas, puree` under
    `Beans, green, without pods (Phaseolus vulgaris)`. Edible-pod peas are the exception — they stay in
    `Vegetables … / Legume vegetables / Peas, with pods`.
  - **Fresh herbs are `Herbs, spices and condiments / Herbs`**, and spring onion shares one row with chives.
- **Fresh produce is where the A grades are**, because the archetypes are species rows and the name is the species.
  Cultivar, grade, size, organic certification, washed-or-not and pack format are not axes: `Õun Kanzi`,
  `Õun Champion` and `Õun Gala` all take `Apple, pulp and peel, raw`. The one cultivar-specific row in the fruit
  branch is `Apple, var. Golden, pulp and skin, raw`, so a Golden Delicious pack is an A on its own row.
- **Where a colour axis exists, use it; where it does not, do not invent one.** Grapes have `raw` / `white, raw` /
  `red, raw`, so *hele* and *punane* take the coloured rows and a cultivar-only or mixed pack takes the neutral
  one. Peppers have green, yellow, red **and** a `green, yellow or red` row — which is the right home for orange,
  white and unstated-colour packs. Tomatoes and onions have no colour axis at all, so a red onion and a yellow
  tomato are plain B's on the generic row.
- **Cultivated mushrooms have no archetype and no L2 to hold one.** The whole database has **one** fungus row in
  2451: `Morel, raw`, under `Vegetables … / Fungi, wild, edible / Morel (Morchella esculanta)`. Button, brown,
  oyster, shimeji and enoki are therefore blank at the vegetable L1 only (nine rows here, matching the kritis
  call). **A wild mushroom is different** — the L2 fits, so chanterelle and brined milk-caps take `Morel, raw` at
  C; a different genus, but compositionally close (chanterelle ~120 kJ against morel's 97,9) and the only
  principled option.
- **Pickled, fermented and salted vegetables keep their vegetable row.** There is no gherkin, sauerkraut, kimchi
  or pickled-onion archetype anywhere. So *soolakurk* and *hapukurk* keep `Cucumber, pulp and peel, raw` (C, with
  2,4-4 g salt against 0,01), *hapukapsas* and both kimchis keep the cabbage row of their colour, and marinated
  onion keeps `Onion, raw`. This is the jerky call transplanted: the product differs from its archetype in sugars
  and salt rather than water, and nothing nearer exists.
  **The trap is that SAFAD does contain the word Sauerkraut** — twice, in `Composite food … / Potato based
  dishes / Potatoes, meat, and vegetables meal`, at 6,6 and 8,1 g fat. Those are Alsatian *choucroute* dishes, not
  a jar of cabbage. Use them only for a genuinely braised sauerkraut: Rimi's `Hautatud hapukapsas` (rapeseed oil,
  tomato paste, bay) matches `Sauerkraut, without garnish, drained, cooked` on fat to 9%, which is the axis that
  separates the two products.
- **A dressed vegetable salad goes to `Prepared salads` only when the vegetable itself has a row there.** The
  `Prepared mixed vegetable salad` L3 is exactly three rows — `Coleslaw, w sauce, prepacked`, `Grated carrots`,
  `Greek-style marinated mushrooms` — and they are dressed-cabbage, dressed-carrot and marinated-mushroom
  preparations respectively. So *kapsasalat* → coleslaw (B, right on both vegetable and format) and Korean carrot
  salad → `Grated carrots` (C, more sugar). A beetroot salad has no such row: `Coleslaw` matched Rimi's
  `Peedisalat` on energy to within 2 kJ on the wrong vegetable, so it went to `Beetroot, cooked` instead and the
  dressing went into the grade — a sauced vegetable keeps its species row, as a sauced fish does.
  An **undressed** shredded mix is not a coleslaw even when the pack says Coleslaw: `Coleslaw segu` declares 0,1 g
  fat against the row's 8,1, and *segu* means mix.
- **Dried fruit is a short list**: apple, apricot, banana, coconut, date, fig, peach, prune, raisin. No dried
  mango — that takes `Mango, pulp, raw` at C, five times under on energy. `Banana, pulp, dried` is a real row and
  is not the fried-chip kind.
- **Fresh-only and dried-only herbs.** The Herbs L2 carries a *fresh* row for basil, chervil, chives, coriander,
  dill, mint, parsley, rosemary, sage, tarragon and thyme — and nothing else. Oregano and marjoram exist only as
  the dried spice at 1100-1170 kJ, so a potted fresh oregano is a C. `Provence herbs, dried` is the only
  herb-mixture row in the database, so a fresh mixed-herb pack lands there at C on a tenfold water difference.
  Turmeric is the same shape: powder only, ~3× the fresh rhizome, and species beats form — `Ginger, raw` would
  match a fresh rhizome but not the species the name states.
- **Salad-leaf mixes**: `Mesclun or salads, mix of baby leaves` is the only mix row. Genuine baby-leaf mixes are A;
  the chicory-led Italian mixes (escarole, frisée, radicchio, Lollo rosso) are B, landing within 15% of it anyway;
  a mix with 30% beetroot in it is C. Individual leaves are well covered — iceberg, cos, generic lettuce, curly
  endive, red endive, lamb's lettuce, rocket, watercress, sorrel, dandelion, chard, young spinach — so frillice
  and Lollo rosso are just `Lettuce, raw`, and frisée is `Curly endive, raw`.
- **Ready-to-eat cooked vegetables are better served than you would expect**: `Beetroot, cooked`,
  `Potato, steamed, vacuum-packed`, `Sweet corn, on the cob, cooked`, `Chick pea, cooked`, `Haricot bean, cooked`
  all exist and all match their Estonian vacuum packs closely. For a **fresh raw** corn cob both cob rows carry a
  wrong qualifier (one cooked, one frozen); prefer the cooked one, since freezing is the bigger difference for a
  footprint database. Same call as the kritis fresh sweetcorn.
- **Watch the pack for a name that is not the product.** `Keedetud aedoad` reads as garden/green beans but declares
  10,5 g protein and 23,5 g carbohydrate — white haricot beans, mapped in the dried-bean branch.
  `Hapukapsasalat` is acidified with citric acid and sorbate, not lactofermented. Three packs' nutrition tables
  are unusable outright (1 kJ on both steamed potatoes and the organic sauerkraut, 0 kJ on the dried mango).
- Estonian glossary: **õun** = apple, **pirn** = pear, **ploom** = plum, **virsik** = peach, **aprikoos** =
  apricot, **viinamari** = grape (**hele** = light/white, **tume** = dark, **seemneteta** = seedless),
  **arbuus** = watermelon, **melon** = melon, **viigimari** = fig, **datlid** = dates, **granaatõun** =
  pomegranate, **füüsal** = physalis, **kiivimarjad** = kiwi berries, **põldmari** = blackberry, **mustikas** =
  bilberry (**kultuurmustikas** = cultivated blueberry), **vaarikas** = raspberry, **maasikas** = strawberry,
  **kookospähkel** = coconut, **kuivatatud** = dried; **kartul** = potato (**varajane** = early, **pestud** =
  washed), **bataat** = sweet potato, **peet** = beetroot, **porgand** = carrot, **kaalikas** = swede,
  **juurseller** = celeriac, **varsseller** = celery stalk, **pastinaak** = parsnip, **redis** = radish,
  **sibul** = onion (**šalott** = shallot, **porrulauk** = leek, **salatisibul** = salad onion), **küüslauk** =
  garlic, **peakapsas** = white cabbage, **lillkapsas** = cauliflower, **nuikapsas** = kohlrabi,
  **hiinakapsas** = Chinese cabbage, **lehtkapsas** = kale, **koonuskapsas** = pointed cabbage, **brokoli** =
  broccoli, **kõrvits** = pumpkin (**suvikõrvits** / **kabatšokk** = courgette, **muskaatkõrvits** =
  *C. moschata*), **kurk** = cucumber, **baklažaan** = aubergine, **paprika** = sweet pepper, **tšillipipar** =
  chilli, **spargel** = asparagus, **fenkol** = fennel, **hernes** = pea (**suhkruhernes** = sugar snap),
  **türgi uba** = broad-podded green bean, **aedoad** = haricot beans, **kikerherned** = chickpeas, **idud** /
  **idandatud** = sprouts, **seened** = mushrooms (**šampinjon** = button, **kukeseen** = chanterelle,
  **riisikas** = milk-cap, **austerservik** = oyster mushroom, **metsaseened** = forest mushrooms); **till** =
  dill, **petersell** = parsley, **basiilik** = basil, **koriander** = coriander, **murulauk** = chives,
  **roheline sibul** = spring onion, **piparmünt** = mint, **rosmariin** = rosemary, **tüümian** = thyme,
  **salvei** = sage, **sidrunmeliss** = lemon balm, **sidrunhein** = lemongrass, **kurkum** = turmeric,
  **maitseroheline** = fresh herbs; **hapukapsas** = sauerkraut, **soolakurk** / **hapukurk** = salted/pickled
  cucumber, **marineeritud** = marinated, **soolvees** = in brine, **keedetud** = boiled, **aurutatud** =
  steamed, **hautatud** = braised, **riivitud** = grated, **kissell** = starch-set fruit gel, **mahe** / **öko**
  = organic, **potis** = in a pot, **kimp** / **vars** = bouquet / stem (flowers — excluded from this file).

#### Lithuanian fruit and berries: graded by the name, because 110 of 127 packs publish no table

`lt/10_fruits_vegetables` starts with `fruits-and-berries-/SH-15-3` (127 products). The sweep is the cleanest in
the tree: thirteen children, every one populated, summing exactly to the parent, and every other code from 1 to 30
answering 404 — no empty child, no foreign parent. The other half of `SH-15` is
`vegetables-and-mushrooms/SH-15-1` (with `mushrooms/SH-15-2` beneath it) and is not in the file yet.

- **This shelf is graded by name, and that is not a shortcut.** Fresh produce sold by weight carries no nutrition
  table at all: only 17 of the 127 packs publish one, and 15 of those are dried, cut or ground goods. So 94 rows
  are class `'d'` — the archetypes are species rows, the Lithuanian name is the species, and cultivar, size grade,
  organic certification, packing and cutting are axes SAFAD does not carry. `'n'` would have been wrong for the
  whole shelf: it returns **C** when there is no energy figure to compare, which would have put a plain bag of
  Gala apples at C for want of a table no fresh-fruit pack has ever carried.
- **`'d'` is checked, not asserted.** `rimi/lt_fb_map.py` carries a `DECLARES` table of the Lithuanian token that
  reaches each row — `obuol|\bob\b` for apples, because Rimi abbreviates `Ob.GRANNY SMITH`; `silauog` for
  blueberries; `\bzal` for the green grapes — and `check()` asserts every `'d'` row's own product name contains
  it. It is the cheese file's discipline applied to a shelf where nine tenths of the letters are A.
- **`žaliosios citrinos` is a false friend and it is the shop's only lime.** The name reads "green lemons"; the
  fruit is the Tahiti lime. Both packs come from Brazil and Colombia, nothing on the shelf is called *laimas*, and
  a literal reading lands on `Lemon, pulp, raw` at 105 kJ — a different species from the lime row's 166. The
  Lithuanian idiom *is* the declaration, so both are A.
- **`šilauogė` is the cultivated blueberry, which inverts the Estonian problem.** *Vaccinium corymbosum* is
  exactly what the L3 names, so the Lithuanian blueberries are A; Estonian *mustikas* is the wild bilberry
  *V. myrtillus* and only its *kultuurmustikad* pack matched the row. Same shelf, same archetype, opposite
  letters, and the reason is the language and not the fruit.
- **The grape colour rule, restated for Lithuanian**: *žalios* (green) → `Grape, white, raw` and *raudonos* (red)
  → `Grape, red, raw`, both A, because green-versus-white is the trade's own synonym pair; ***mėlynos*** (blue)
  → the red row at **B**, since there is no black-grape row; a **cultivar-only name → the colour-neutral
  `Grape, raw` at B**, never a colour inferred from the cultivar. The last one has a worked case: `Vynuogės RED
  GLOBE` carries the English word *red* inside a cultivar name in an otherwise Lithuanian name, and the Estonian
  pack of the same cultivar is called `viinamari **punane** … Red Globe` and does reach the red row. A colour word
  must be the shelf's own language to count. Four Lithuanian grape packs — red, green and two cultivar-only —
  share one templated panel of 301 kJ, which is `Grape, raw` to the kilojoule, so the numbers could not have
  decided a single one of these rows.
- **A cultivar-named melon is a B, and the reason is that the two melon rows differ 2,7-fold.** Honeydew is
  93,9 kJ and cantaloupe 253, so `Melionai PIEL DE SAPO` and `Melionai GALIA` (both green-fleshed, both
  cultivar-only names) take honeydew at B, while `Geltonieji melionai Honey Dew` and `Melionai CANTALOUPE` name
  the archetype's own type and are A. Same reading as the Estonian shelf, and the melons stay under
  `Vegetables … / Fruiting vegetables` where SAFAD files the cucurbits.
- **Four fruits are absent to the genus and one is absent to the family.** *Hylocereus* (**kertuotis**, the dragon
  fruit) has no row, and `Prickly pear, pulp and seeds, raw` is the only other Cactaceae fruit in the database, so
  fresh and dried dragon fruit take it at C. **Longan** (*Dimocarpus longan*) has none either, and lychee is its
  closest relative — same family, and within 3% of it fresh — so dried longan carries both a wrong species and a
  wrong form. **Physalis** (*dumplainis*) has no relative anywhere in the fruit L1, because SAFAD files every
  Solanaceae fruit as a vegetable, so it stays blank. And **aloe vera** (*alavijas*, sold by the kilo as the fresh
  leaf) has no row and no L2: the Leaf vegetables L2 is lettuces, spinach and sprouts, and its one generic L3
  holds a baby-leaf salad mix, so it keeps the vegetable L1 and nothing below it — the cultivated-mushroom call.
- **Two same-genus substitutions, both explicit B.** The **Nashi** is *Pyrus pyrifolia* against the Pear L3's
  *P. communis*, and **kiwi berries** (*kivi uogos*, *Actinidia arguta*) are a grape-sized whole-eaten fruit
  against a large peeled one. In both the row is generically named, so the name still reaches it and the species
  is the single axis off — the `'s'` shape, reached by hand because there is no table to let the class decide.
- **`Miscellaneous fruits` is the L2 that exists for the fruit the other L2s do not describe**, which is what
  makes it the right home for a blank row. Physalis goes there rather than to `Berries and small fruits`, whose
  members are a genus list of temperate soft fruits plus grapes, not a morphological class.
- **SAFAD carries a nut butter for one species only.** `Animal and vegetable fats and oils / Vegetable fat /
  Peanuts butter` exists; no other nut has one. So the two FAMILY GARDEN cashew creams (100% roasted cashew) keep
  the Cashew L3, whose single row is a salted snack nut, and the letter records that the format is an axis the
  database models for peanut and not for cashew. Composition says the grinding costs nothing — 45 g of fat against
  49,5 — while the row's 1,15 g of salt is on a product that declares none.
- **Three qualifiers on this shelf are a B rather than a C, because the step is small**: white grapefruit on the
  *yellow* row (the same fruit under the trade's other word), fresh dates on the only (dried) date row — retail
  "fresh" dates are the soft rutab stage, and the two Medjoul packs here declare 1151 and 1160 kJ to that row's
  1210 — and a coconut whose name states no maturity, where the immature and ripe fresh rows sit 40 kJ apart.
  Contrast the dried fruits, where drying is a fivefold step and the letter is C.
- **A mixed punnet is graded on what the mixed row names.** SAFAD's one mixed-berry row is `Red berries
  (raspberries, strawberries, red currants, black currants), raw`, so a raspberry-and-blueberry punnet is B on it
  (it names one of the two fruits, and its 200 kJ sits between raspberry's 201 and blueberry's 244) while a
  blueberry-and-physalis punnet is C on `Blueberry, raw` — the mixed row names neither of its fruits and physalis
  has no row at all.
- **Panel faults, 5 of the 17 packs that publish a table — and the fabricated-cell shape turns up twice more.**
  Freshly cut pineapple declares 31 g of carbohydrate where pineapple holds 11 to 13, and its 514 kJ / 123 kcal
  agrees with that figure to 1 kJ, so every check passes it; one of the three dried mangoes declares 14 g of
  sugars inside 84 g of carbohydrate where its siblings declare 45 and 68 in the same 84 to 85. The other three
  are cells never filled in — cut watermelon printing 0,0 g of both carbohydrate and sugars, cut melon printing
  0,0 g of sugars beside 6,1 g of carbohydrate — and **the 0 kJ on the dried mango, which the Estonian shelf
  carries on its dried mango too**: a spec-level error in Rimi's product record, not a per-market typo.

#### Lithuanian vegetables and mushrooms: the prepared block is where the work is

`vegetables-and-mushrooms/SH-15-1` (245 products) completes `lt/10_fruits_vegetables` at 372 rows. Fifteen
children, all populated, summing exactly to the parent — and `mushrooms/SH-15-2` is a **child** of this category
rather than a sibling of it, which the bare code does not tell you. One pack's gtm attribute carries the parent
code `SH-15-1` while the salads child lists 32 against the 31 the parent attributes to it: that pack is shelved in
salads and its *primary* home is the parent. A URL diff of the child against the parent scrape returns 0 new
products, which is the check that settles it.

- **This is the second file built from a rules table** (`helperscripts/rimi/lt_vg_map.py`, 96 rules + 43
  overrides for 245 products) and it repeated the ordering lesson exactly: the broad `pupel` rule for green beans
  was swallowing `daigintos pupeles` — sprouted beans — and the symptom was not a wrong grade but the
  *sprout* rule reporting that it had never fired. The fix is to put the whole sprout block above the legume
  block. **156 of the 245 packs publish no table**, so `'d'` carries most of the file again, and `DECLARES` +
  `check()` guard it as on the fruit half. Two Lithuanian stems caught the patterns out and are worth recording:
  *gražgarstė* declines to **gražgarsčių** (so `grazgarst` alone misses the genitive plural), and **porai** (leeks)
  needs `\bpor(ai|u|as)\b` rather than a character class.
- **The prepared block is 50 of the 245 and it is all governed by one standing rule**: there is no gherkin,
  sauerkraut, kimchi or pickled-vegetable archetype anywhere, so a ferment keeps its own vegetable's raw row and
  takes C. That covers eight sauerkrauts, five pickled-cucumber packs, six kimchis, a soured garlic, a marinated
  onion and soured green tomatoes — and the last of those is the one that lands on a row built for it,
  `Tomato, green, raw`, with only the fermentation left to pay for.
- **Braised sauerkraut is the exception SAFAD serves, and the fat is what proves it.** Both Rimi jars of
  *troškinti rauginti kopūstai* carry 6,0–6,2 g of fat against `Sauerkraut, without garnish, drained, cooked` at
  6,6, where a plain jar carries 0,1–0,2. The letter is held to **B** because that row sits in a
  `Composite food / Potato based dishes / Potatoes, meat, and vegetables meal` L2 and L3 that describe an Alsatian
  choucroute, not a Baltic jar — the archetype names the food and the tuple does not.
- **The Korean salads split on which of the three `Prepared mixed vegetable salad` rows exists for their
  vegetable.** Korean carrot → `Grated carrots` (C, more sugar, the Estonian call); Korean cabbage and the plain
  coleslaw → `Coleslaw, w sauce, prepacked` (B, both inside 15% on energy); Korean beetroot and a beetroot
  carpaccio → `Beetroot, cooked` at C, because **that L3 has no beetroot row at all** and `Coleslaw` would fit
  such a pack on energy while naming the wrong vegetable. That is the same trap the Estonian *peedisalat* set.
- **Sprouts are served for three species and no more, and neither substitution is acceptable.** `Alfalfa seeds,
  sprouted, raw`, `Mung bean, sprouted or soy spouts, raw` and `Lentils, sprouted` are the whole vocabulary, so
  pea, leek, sunflower, radish, broccoli and red-cabbage sprouts have no row: the parent vegetable's row carries a
  whole field season of footprint a jar sprout does not have, and the alfalfa and mung rows name a different
  species. Seven blanks at `Vegetables … / Leaf vegetables`, the L2 that holds all three sprout L3s. **A
  microgreen is not a sprout**: rocket microgreens are the same species harvested young out of substrate, so they
  keep `Roman rocket, raw` at B.
- **Fourteen cultivated mushrooms are blank and one chanterelle is a C**, exactly as on the Estonian shelf —
  button, brown button, portobello, shiitake, oyster, yellow oyster, king oyster, white and brown shimeji, enoki,
  enoki gold and a grill mix all blank at the vegetable L1; *voveraitės* are wild, so the `Fungi, wild, edible` L2
  is right and `Morel, raw` is the only row in it.
- **Lithuanian names that decide a row on their own**: *gražgarstė* and *rukola* are two words for the same
  *Eruca sativa*, so all five rocket packs are A; *laiškinis česnakas* is chives (A on
  `Chive or spring onion, fresh`) while *svogūnų laiškai* is spring onion (B on the same row, the Estonian
  reading); *salierų šaknys* is celeriac and *salierų stiebai* the stalk, two different L3s; *ropinis kopūstas*
  is kohlrabi, whose only row is boiled — the one brassica with no raw form; *baltieji ridikai* is the daikon,
  which takes `Radish, black, raw` at B as the nearer of the two radish rows; *patisonai* is the pattypan, which
  takes `Squash, all types, raw`; and *spagetinis moliūgas* lands on a spaghetti-squash row SAFAD actually has.
- **`šviežios` is not `varajane`.** Estonian *varajane* states the season the `New potatoes` L3 encodes and earns
  that row; Lithuanian *šviežios* means fresh, which every potato on a shelf claims to be, so all twelve
  Lithuanian potato packs take `Potato, peeled, raw` — the main-crop row — and the PAROL pack's early variety is
  not something the name reaches.
- **Panel faults: 21 of the 89 packs that publish a table, 24% — the worst rate in the tree by that measure.**
  Three findings inside it:
  - **Seven potted herbs from one supplier all print an energy pair that disagrees with itself** — basil,
    coriander, parsley, mint, thyme, dill and lemon balm, every pot in the range. That is a supplier-level fault
    rather than a per-pack typo, and it is the clearest case yet for grading a certain mapping on its name.
  - **One record belongs to a different product entirely.** `Rauginti agurkai, 600 g` carries an ingredient line
    of carrots, soy flakes and soy sauce and a panel of 2264 kJ, where its four sibling jars of soured cucumber
    sit at 28 to 89 kJ. Not one wrong cell — the whole record.
  - **Three faults were invisible until the macro-energy check was corrected** (below): spinach at 598 kJ and
    rocket at 602 over macros worth 125 and 133, and garlic at 623 kJ with 0,2 g of carbohydrate where garlic
    holds 28.
- **A rules-table file carries almost no checkable claims, and that is the design.** The 245 vegetable rows add
  **3** figures to `verify_nums.py` against the 127 fruit rows' 29, because a shared rule comment must not carry
  a pack figure — the letter, not the comment, is what records each row's fit. The three are per-product
  overrides written for broken panels, which is the one place a figure belongs in a rules-table file.

#### Frozen fruit, vegetables and potatoes (`lt/10`, SH-13-5)

`frozen-foods/frozen-vegetables-fruits-and-berries/SH-13-5` (56 products) takes `lt/10` to 428 rows. Four
children — frozen berries 16, frozen mushrooms 1, frozen potatoes 14, frozen vegetables and their mixes 25 — and
the four `categoryNamePath` facets returned **exactly the unfiltered parent's 56 ids**, so nothing on the shelf
was outside the filter. The children are pairwise disjoint with a 56-id union, and no id here is held by any of
the 39 peer files. One pack (`Figūrinės bulvių lazd. RIMI SMART,2,5kg`) carries the bare parent code SH-13-5 in
its gtm attribute while its URL and the child listing both put it under frozen potatoes. The listing 500s
intermittently at `pageSize=100` — three retries were needed for the parent's page 2 — and a 500 must never be
read as an empty page.

**This is the shelf SAFAD's frozen axis was built for, and it is the best-served frozen category in the tree:
23 A / 16 B / 16 C and one blank.** The database carries a `frozen` row for nearly every vegetable a freezer
cabinet sells. Seven come as a raw-and-cooked PAIR — `French bean, frozen, raw` / `…, cooked`, and the same for
garden peas, broccoli, cauliflower, Brussels sprouts, carrot and spinach — while sweetcorn, courgette, leek and
onion have the frozen raw row only. **The retail bag is always the RAW one**: the blanching every frozen
vegetable is given before freezing is not what the `cooked` rows mean — those are the state after the consumer
boils it, which is why the pairs exist at all. No plain vegetable name on this shelf states a cooking, so the
raw row is never in doubt here; the fish shelf's contradiction test comes back only on the sweet-potato fries
below.

**Where the frozen axis stops is the finding, and it is arbitrary.** In `Berries and small fruits` it covers
exactly three fruits — blackberry, blueberry, raspberry — while strawberry, cherry, mango and cranberry have
their plain raw rows only. So half this shelf's berries sit on a row that says frozen and half on a row that does
not, for no reason visible from the product. Those rows are still certain mappings and are graded `'d'`: a plain
bag of frozen cauliflower is `Cauliflower, frozen, raw` whatever the numbers say, and here they say +59%, because
the RIMI own-brand broccoli (163 kJ) and cauliflower (167) panels sit 48 and 59% above their rows where the peas
of the same range agree to 4% and the cut beans to 12%. That is a sibling-comparison fault, not an automated
flag.

**SAFAD keys a vegetable MIX on the dish the mix is for, and these names state the dish.** The
`Mixed vegetables, boiled` and `Mixed vegetables, fried` L3s plus `Ratatouille` hold rows for soups, couscous,
ratatouille, spring, Asian stir-frying, country-style pan-frying and Mediterranean grilling, and the shelf
answers with *mišinys sriubai*, *ukrainietiškiems barščiams*, *pavasariškas*, *Vid. jūros stiliaus*, WOK THAI,
WOKMIX ORIENT and *kepti su rytietiškais pagardais*. Ten of the 17 vegetable mixes therefore land on a dish
row, and an eleventh on the `Garden peas and carrots, frozen, raw` pair SAFAD writes out by name. This is
the frozen-gastronomy lesson repeated and it is the cleanest confirmation of it yet: whether a category can be
mapped is mostly decided by whether the retailer's naming convention and SAFAD's keying axis agree, before any
judgement is made. Two readings inside it:

- **A borscht is a soup**, so `Darž. mišinys ukrain. barščiams BAUER` reaches `Mixed vegetables for soups,
  frozen, raw` (+4,9%, A) alongside the RIMI pack that says *sriubai* outright. SAFAD's soup mix is a
  carrot-and-leek julienne; the beetroot shows only in the sugars.
- **`Vegetables pan-fried without mushrooms, frozen, raw` is usable, and the absence in its name is why.** A row
  that names an axis the pack *denies* is contradicted; a row that names an absence the pack *confirms* is not.
  Both HORTEX *kepimui* packs are mushroom-free and take it.

**The six mixes that name no dish are where the C's are.** `Mixed vegetables, frozen, raw` carries 2,65 g of fat
and 0,6 g of salt that no plain Lithuanian mix comes near (0,2-0,8 g fat, 0-0,1 g salt), and its 271 kJ is at the
top of a family running 118 to 236 kJ once the rice-based Hawaiian mix below is set aside. Five of the six grade
C on that row and only HORTEX MEXICO reaches B. The alternative would have been `Mixed diced vegetables,
frozen` (262 kJ, 0,5 g fat), whose composition is sane but which asserts a cut most of these packs are not;
family consistency on the plain row and honest letters is the better trade. The same choice put
`Šald. havajiet. daržovių mišinys` — **55% white rice** — on the vegetable row at C rather than on
`Risotto, w vegetables` (503 kJ to the pack's 414): a *havajietiškas mišinys* is rice-based
by convention, but the name says only *daržovių mišinys*, and this is the mayonnaise call — the model is given
the name, not the composition.

**The potato block: there is no unqualified frozen chip.** The French fries L3 splits five ways — `frozen, raw,
intended to be` deep-fried (1020 kJ, 12,1 g fat) / microwaved (932, 10,2) / roasted-baked (595, 4,73), and
`frozen, deep-fried` (1080) and `frozen, roasted/baked` (896) for the finished chip. A retail bag is the uncooked
state, so the three `intended to be` rows are the band, and the choice inside it is composition: ten of this
shelf's chips carry 2,4 to 5,3 g of fat, which is the oven row and nothing else. **The intended cooking method is
a plain alternative, not a name-unsupported qualifier**, so tie-breaking on composition is legitimate here where
choosing "light" or "omega-3" would not be — and the two FARM FRITES packs state *kepti orkaitėje* and reach the
row by their own words. Three more:

- **`Mashed potato balls pre-fried, frozen, raw` and `Potato, pre-fried into cubes, frozen, raw` both name a cut,
  and a Lithuanian name that states a different one caps the letter at B.** AVIKO *kamuoliukai* (balls) fits the
  first outright and is an A; AVIKO *figūrėlės* (shapes) fits it to -6% and RIMI *griežinėliai* (round slices)
  fits the cube row to -0,5%, and both are held to B by hand. A fit cannot confirm a form the name denies — the
  FELICIANA SPECIALE reading one step on, since there the name merely failed to state a type and here it states
  a different one.
- **Species beats format, so the sweet-potato fries leave the chip L3 entirely.** *Saldžioji bulvė* is
  *Ipomoea batatas* with its own L3 under `Other starchy roots and tubers`, whose three rows are all whole tuber.
  The **cooked** one is the row the pack does not contradict (it is par-fried in sunflower oil, so `raw` is false
  of it), and the frying is then unmodelled: +94% on energy and 4,8 g fat against 0,15. C, and the right C.
- Seasoning is not an axis: the spiced wedges and the garlic-and-herb chips take the plain oven row and the
  difference appears in the salt (0,5 and 0,8 g against 0,36) and nowhere else.

**Chocolate-coated fruit is confectionery.** The two BOUJEE packs are 70% chocolate to 30% berry and the banana
pack 55% to 45%, so all three leave the Fruit L1 — there is no chocolate-coated fruit row anywhere in it — for
`Chocolate (Cocoa) products / Chocolate with nuts or fruits`, whose fruit rows all say *dried*. The split inside
that L3 is by chocolate type and the names decide it: *avietės/braškės **šokolade*** states no type and takes the
type-neutral `Chocolate bar with dried fruits` (B, -8 and -10%), while *bananai **pieniškame** šokolade* states
milk and takes the milk row (C, -33%, because 45% of it is banana). That is the grape-colour rule applied to
chocolate.

**Two false friends and a mixed-fruit note.**

- ***vyšnia* is the SOUR cherry** *Prunus cerasus* → `Morello cherry, raw`. The trap is `Cherry, pitted, raw`,
  which is *P. avium* under `Sweet cherry` and names the very pitting both Rimi packs state (*be kauliukų*).
  Take the species; pitting is not an axis.
- ***saldžioji bulvė* is not a potato** — a different crop, L2 and footprint, per the fries note above.
- **`Fruit salad, raw` is the only mixed-fruit row and both frozen fruit mixes land on it.** The tropical mix
  (pineapple 52 / mango 23 / papaya 15 / passion 10, no fruit named in the name) is `'f'` and reads -17%, B. The
  raspberry-and-mango mix is **capped at B by hand**: the name states both fruits and both have rows of their
  own, so the mix row is a choice, and `Mango, pulp, raw` sits at 302 kJ — identical to the pack — which is
  exactly what the cap resists. A berry-only mix is different and keeps the berry row: `Red berries (…)` names
  the redcurrant in Rimi's five-berry mix (C at +50%, sea buckthorn) and one of the two fruits in the
  raspberry-and-blueberry assortment (B at +18%), which is the fresh shelf's punnet reading unchanged.

**The mushroom mix is blank, and the name is why.** `Šaldytas grybų mišinys HORTEX JULIENNE` lists cultivated
*pievagrybiai*, *miško grybai* and onions. The standing call is that a cultivated mushroom is blank at the
vegetable L1 while a mushroom the NAME calls wild takes `Morel, raw` at C; this name states no species and no
habitat, so neither branch is reachable — the same blank as the fresh shelf's `Grybų rinkinys BBQ&GRILL`.

**Panel faults, 8 of 56 (14%), and not one is an automated flag.** Every kJ/kcal pair agrees and every macro sum
checks. They are: the 1 kg strawberry bag's kilojoule cell (175 beside 35 kcal, which is 146, on macros implying
159 — and the 400 g bag of the same berries prints 146 on identical macros, which is what settles it); **five
HORTEX packs printing 0,0 g of sugars inside 4,5 to 11,2 g of carbohydrate**, five of the six HORTEX packs on the
shelf; and the two RIMI brassica panels above. Also on the shelf but not a fault: `Sojos pupelės NICE N EASY,
400 g` is listed twice, as ids 4002210 and 4002193, under one display name and one panel — harmless because both
rows take the same archetype, which is the condition `build_gt.py` checks.

### Meat
- Estonian cut glossary: **sisefilee** = tenderloin → `Pork filet mignon, raw`; **välisfilee** = trimmed boneless
  loin eye → `Prok, eye of shortloin, raw` (the typo is SAFAD's) and **not** the three data-identical loin rows,
  which all store 987 kJ / 18,8 g fat against a real välisfilee's 5-10 g; **kaelakarbonaad** = neck →
  `Pork, shoulder, raw` (no neck cut exists — same as the Greek laimós); **abaliha** = blade → `Pork, shoulder, raw`;
  **küljeliha / triibuliha** = belly → `Pork, belly, raw`; **koot** = shank → `Pork, knuckle or shank, raw`;
  **ribi** → `Pork, spare-ribs, raw`; **guljašš** = stewing cubes → `Pork, shoulder, raw`; **grill-liha** with no cut
  named → `Pork, shoulder, raw` (C — the archetype carries no marinade salt).
- **Marination is not an axis, but "šašlõkk"/skewer in the name is**: those take `Pork on skewer, raw`, the only pork
  row carrying a marinade (1,10 g sugars / 0,81 g salt) though much leaner at 6,50 g fat than Estonian šašlõkk cut
  from the neck. When the name gives both format and cut, the format wins. Kritis set both halves of this rule.
- **"Peekonilõigud" is a trap**: sold as bacon slices, but 100% pork at 0,1 g salt — uncured raw belly. Only packs
  with nitrite and 2+ g salt are really cured, and those take `Pork belly salt-cured` (in the Pork L3) rather than
  the Bacon L3, whose entries are all lardoons.
- **The Beef L3 is the best-served meat branch in SAFAD** — 48 rows with a real raw cut hierarchy (bolar-blade,
  cheek, chuck, flank steak, hanger steak, knuckle, neck, oxtail, rib steak, rib, round, rump steak, short ribs,
  shoulder, sirloin steak, thin flank, topside) plus use-based `stewing meat` and `steak` rows. Estonian beef cuts:
  **antrekood** = entrecôte → `Beef, rib steak, raw`; **välisfilee** = striploin → `Beef, sirloin steak, raw`;
  **romsteek** and **tagaosasteik** are the same cut under two names → `Beef, rump steak, raw`; **minutisteik** →
  `Beef, topside, raw` (no minute-steak format exists; the pack's 0,9 g fat is leaner than every raw beef row);
  **Denver steik** → `Beef, chuck, raw` (Denver is the chuck's serratus ventralis, so it runs much fattier than the
  whole chuck); **kaelaliha** = neck → `Beef, chuck, raw`, because the only beef neck row is braised — the same
  substitution pork kaelakarbonaad makes onto the shoulder; **klops vasardatud** = a pounded escalope → the
  underlying muscle, since neither pounding nor the escalope format is an axis and beef has no escalope row.
- **Cuts whose name gives no cut go to a cut-less or use-based row, not to a guessed cut**: `strooganov` →
  `Beef, stewing meat, raw` (C) and `veisepraad kondita` → `Beef, round, raw` (C), even where a specific cut matches
  the composition better — `Beef, bolar-blade, raw` is within 1% of the strooganov's energy but names a cut the label
  does not. Same principle as pork grill-liha → shoulder.
- **`Beef, knuckle, raw` and `Veal, roast, raw` are broken rows**: both store cooked-meat protein on the raw row
  (31,0 and 27,3 g, which no raw muscle reaches) at an energy identical to or within 1% of their own cooked twin.
  They are SAFAD's two use-based joint rows and they are the only two defensible homes for **koot** (beef shank —
  no beef shank row, but SAFAD names the same cut "knuckle or shank" in both the pork and veal L3) and for
  **vasika tagatükk kondita** (no veal leg, round or topside row). Kept with the defect flagged, per the duck-leg
  precedent.
- **Lamb has no shank and no plain rib row**: `lamba koot` → `Lamb, leg, raw` (the shank is the distal leg), C;
  `lamba ribi` → `Lamb, rib chop, raw` rather than the much fattier `Lamb, cutlet, raw` (30,1 g), which is the same
  French côtelette.
- **Rabbit: `küülik` is farmed rabbit → the `Rabbit meat (Lepus cuniculus)` L3, never the `Hare meat` L3** whose two
  entries are also named "Rabbit" but are wild rabbit at 2,32 g fat against the farmed row's 11,6. The L3 has three
  whole-animal rows and no cut axis, so forelegs take the same row as the whole animal.
- **Minced meat is scattered over four L2s and none of them is the obvious one.** `hakkliha` = mince,
  `hakklihasegu` = mince mix, `kotletisegu` = cutlet mix, `-mass` = a water-and-fibre-bound meat mass,
  `delikatess` = the lean grade, `kodune` = homestyle (which in Estonia means the pork-and-beef mix, not a
  recipe). Where each kind goes:
  - **Pure pork** → `Meat specialities / Pork meat loaf / Sausage meat, pure pork, raw`. The Pork L3 has 43 rows
    and no minced one; this entry is CIQUAL's *chair à saucisse*, i.e. plain raw minced pork, and its
    1070 kJ / 21,2 g fat / 16,9 g protein / 0,14 g salt sits mid-family. Prefer it over `Pork, 80/20 trimming, raw`,
    which is a wholesale trim spec, and note that it keeps the family parallel to the mixed row below.
  - **Seasoned or rusk-bearing pork mince** (kotletisegu) → `… / Sausage meat, raw`, the generic seasoned row;
    its 1,39 g salt is the tell, and like the product name it declares no species.
  - **Pork + beef** → `Mixed meat / Mixed beef and pork meat / Sausage meat, pork and beef, raw` (the kritis
    precedent). Its 1,70 g salt is seasoning no plain Estonian pack carries, a systematic offset across the family.
  - **Beef** → `Livestock meat / Beef meat / Beef, minced steak, 15% fat, raw`. SAFAD grades this one at
    5/10/15/20% fat but Estonian names never state a fat grade, so **the whole veisehakkliha family sits on the
    single 15% bracket** rather than being spread over brackets the name cannot reach; the grade absorbs a
    13-20 g spread. Same call as kritis. **Lithuanian names do state it, and then the bracket is used**: see the
    ground-meat rules.
  - **Chicken and turkey** → `Poultry / Chicken meat / Poultry, minced meat`, the only minced-poultry row; the
    Turkey L3 has none, so turkey takes the same species compromise as the turkey šašlõkk. **Format beats cut
    here**: `broileririnnafilee hakkliha` goes to the mince row even though `Chicken, breast, without skin, raw`
    matches its composition almost exactly (C, with the cost recorded).
  - **Burger patties** → `Composite food (including frozen products) / Meat-based meals / Meat burger /
    Burger, beef based, 15% fat, raw` — the one raw burger row, and the only reason the meat file leaves the meat L1.
- **Two Rimi products share a head term and contradict each other on species**: `Delikatesshakkliha M&M 300g` is
  50/50 pork and beef, `Delikatesshakkliha Matsimoka 300g` is 100% pork. Neither name reaches its own ground truth;
  both are graded C and flagged rather than quietly mapped.
- **Lithuanian cut glossary** (rimi.lt `fresh-meat/SH-9-8`, 69 products, all of it unmarinated single-cut meat):
  **antrekotas** = entrecôte, the rib eye → `Beef, rib steak, raw`; **nugarinė** = the loin eye →
  `Beef, sirloin steak, raw` for beef and `Prok, eye of shortloin, raw` for pork, the same decoding Estonian
  *välisfilee* gets in both species; **nugarinė su kaulais** = loin sliced through the bone, i.e. chops →
  `Pork, chop, raw`; **išpjova** = tenderloin → `Pork filet mignon, raw`, and for beef nothing (see below);
  **sprandinė** = neck or collar → `Beef, chuck, raw` / `Pork, shoulder, raw`; **mentė** = shoulder blade →
  `Beef, bolar-blade, raw` (beef has a blade row of its own) / `Pork, shoulder, raw` (pork does not);
  **kumpis** = the whole leg → `Beef, round, raw` / `Pork, way leg, without rind, fat and bone, raw`, while
  **kumpio kepsnys** — a steak off it — → `Beef, rump steak, raw`, which one pack's own ingredient line confirms
  by calling itself *pasturgalio kepsnys*; **kulninė** = shank → `Beef, knuckle, raw`; **karka** = hock →
  `Pork, knuckle or shank, raw`; **šoninė** = belly; **šonkauliai** = ribs → `Beef, short ribs, raw` (the rack
  sold for braising, not the `Beef, rib, raw` roasting joint) / `Pork, spare-ribs, raw`; **papilvė** = thin flank,
  the plate → `Beef, thin flank, raw`; **paslėpsnys** = flank steak, the bavette → `Beef, flank steak, raw`
  (the FLANK STEAK pack's ingredient line is the evidence); **guliašui** / **troškinimui** = for goulash / for
  stewing → `Beef, stewing meat, raw`; **liežuvis** tongue, **kepenys** liver, **inkstai** kidneys, **ausys** ears,
  **kojos** feet, **žarnos** casings. Modifiers, none of them SAFAD axes: **atšaldyta** chilled,
  **atitirpinta** thawed, **brandinta** aged (**sausai brandinta** dry-aged, which concentrates every macro),
  **be/su kaulu** without/with bone, **be lašinių** without back fat, **su oda** skin-on.
- **Two beef cuts are missing from SAFAD outright, and one of them is a surprise.** There is no **brisket** row
  anywhere — no brisket, no poitrine, no plate, no navel — and **no beef tenderloin**: pork has filet mignon, veal
  both a tenderloin and a fillet, lamb a chop fillet, and the 48-row beef L3, the best-served meat branch in the
  database, has none. *Krūtininė* takes `Beef, chuck, raw` (the forequarter braising block, the same substitution
  the neck makes) and *išpjova* takes `Beef, sirloin steak, raw` (the adjacent muscle in the same primal, and the
  closest composition on the shelf), both C.
- **The pork flank has no row either**, and SAFAD acknowledges the cut only by naming a `Pork, belly, flank
  removed, raw` row: *paslėpsniai* → `Pork, belly, raw`, C. Note that the same Lithuanian word is a premium
  grilling steak in beef and a cheap stewing cut in pork.
- **Sausage casings are blank, not offal-adjacent.** The offal L2 covers tongue, liver, kidney, heart, brain,
  thymus, trotters and the beef stomach; the only intestine entries in the whole database are French chitterling
  sausages, which are a finished sausage. Both Rimi casing packs (5 m coils, and salted *vėdarinės žarnos* for
  vėdarai) are blank at `Edible offal, farmed animals` depth.
- **There is no adult-beef liver L3.** Both bovine liver rows sit under `Veal liver`, one calf and one
  *young cow*; **galvijų kepenys** takes `Liver, young cow, raw`, which is the adult animal and confirms itself at
  571 kJ against the row's 573. The calf row would be 505 kJ. Liver's 3-4 g of sugars is glycogen, not a
  retailer error.
- **Pig ears are B, not C, even though the only row is salt-cured**: `Pork ear sat-cured` carries 0,48 g of salt
  against a fresh pack's 0,3, so the curing in that row is nominal and the part is exact — unlike
  `Pork trotters salt-cured` at 2,37 g, which is why raw trotters stay C.
- Greek cuts (kritis): graviera, kefalotyri, tyrozouli and the ewe semi-hards → `Cheese, Manchego / Semi-hard cheese,
  from ewe's milk`; breaded schnitzel → the underlying raw cut, C; minced/burger → `Sausage meat, pork and beef, raw`
  for mixed and `Poultry, minced meat` for chicken.

#### Fresh poultry

- **Lithuanian poultry glossary** (rimi.lt `fresh-poultry/SH-9-12`, 46 products): **viščiukų broileriai** /
  **viščiukas broileris** = broiler chicken and **vištiena** = chicken meat (the two are used interchangeably on
  this shelf); **kalakutai** turkey, **antys** / **ančiukai** duck / duckling, **žąsis** goose.
  **krūtinėlių filė** = breast fillet, always skinless, and plain **filė** the same →
  `Chicken, breast, without skin, raw`; **vidinė filė** = the inner fillet, i.e. the tender, → the same row, since
  SAFAD has no tender and the tender is breast at the same composition; **filė gabaliukai** / **kepsneliai** =
  fillet pieces / steaks → the same row again, portioning being no axis. **šlaunelių** / **šlaunų mėsa** = thigh
  meat off the bone → `Chicken high leg, meat, raw`, SAFAD's *haut de cuisse*; **šlaunelė** = the bone-in thigh;
  **blauzdelė** = drumstick → `Chicken, drumstick, raw` (**be sąnario** = with the joint removed, no axis);
  **kulšelė** = the whole leg → `Chicken, leg, meat and skin, raw` / `Duck, leg, meat and skin, raw`;
  **sparnelis** = wing → `Chicken, wing, meat and skin, raw`, with **sparnelių vidurinės dalys** / **viduriukai**
  the mid-sections, **peteliai** the drumettes and **viršūnėlės** the tips; **ketvirčiai** = quarters;
  **puselė** = half a bird → `Chicken, eviscerated without offal, raw`; **kaklai** necks;
  **rinkinys sriubai (nugarėlės)** = a soup set of backs; **kepenėlės** liver, **širdys** / **širdelės** hearts,
  **skilviai** (chicken) / **skrandukai** (duck) gizzards; **guliašui** = for goulash and **gabalėliai** = pieces,
  both of which map on the underlying cut. Modifiers, none of them axes: **be antibiotikų** raised without
  antibiotics, **ekologiškas** organic, **be odos** skinless, **be kaulo** boneless, **A klasė** class A.
- **The Chicken L3 is nearly as well served as beef** — 31 rows with breast, leg, high leg, drumstick, wing and
  carcass, each in a *meat* and a *meat and skin* variant, plus free-range, white-race, stewing, hen and capon
  rows. That trim axis is what decides most of this shelf, so named cuts grade class `'c'` exactly as the
  fresh-meat cuts do.
- **Skinless meat takes the meat-only row even where a cut row exists for the skin-on piece.** Chicken thigh meat
  declared *be odos* goes to `Chicken high leg, meat, raw`, not to the skin-on leg row, and Rimi's *ančių kulšelių
  mėsa be odos* — boneless skinless duck leg meat — goes to `Duck, meat, raw` rather than to the duck-leg row,
  which is a *meat and skin* row. Skin is worth a factor of two in duck (531 kJ against 1130), which is more than
  the cut is worth. SAFAD understates thigh fat throughout, in both species: the high-leg row's 4,12 g against
  6,0-10,1 g declared, and the boneless turkey leg's 2,50 g against 5,6-8,1 g.
- **Three families are substitutions rather than named cuts, so they take an explicit B and not class `'c'`:**
  - **wing sections.** There is one wing row and no sectioning axis, so wingettes, drumettes and tips are all the
    same substitution on the whole wing. Rimi templates one panel (702-725 kJ / 11,0 g fat / 18 g protein) across
    its sections, so the numbers cannot separate them anyway — and its own name and ingredient line disagree on one
    pack, *sparnelių peteliai* against *viršūnėlės*. Whole wings **are** the named cut and grade `'c'`.
  - **the bone-in thigh and the leg quarter.** `Chicken, leg, meat and skin, raw` is thigh *plus* drumstick, so
    both are an adjacent piece of the same meat. Rimi never says which quarter a *ketvirtis* is; leg quarters are
    what the word means on this shelf, and the fat confirms it (9,2 and 15,0 g, far past any breast row).
  - **the whole turkey breast.** The Turkey L3's only breast entry is `Turkey, escalope, raw`, a thin slice cut
    from it, so *kalakutų filė* is B while *krūtinėlės filė pjausniai* — actual slices — is A on the same row and
    the same declared panel. The alternative `Turkey, meat, raw` is a whole-bird average, not the breast.
- **Two poultry parts have no row anywhere, both C.** There is **no neck row for any bird** (beef, veal and lamb
  all have one), and **no poultry bone or carcass-part row** — the only broth entries are ready-to-eat soups, the
  same gap the beef soup bones hit. Both go to `Chicken, eviscerated without offal, raw`, the nearest bone-in
  skin-on entry for the species; the neck's 14,4 g fat and 782 kJ sit close to that row but its 14,2 g protein
  against the row's 18,6 gives the bone away. This is the Estonian file's *broilerijalad* / *seljatükid* decision
  reached from the other side: it sent feet and backs to the cut-less `Chicken, meat and skin, raw`, which is
  deboned meat and understates bone even further.
- **The giblets L3 names four species and carries rows for one**, so a duck heart and a duck gizzard take the
  chicken rows and grade B — see the parenthetical-species rule under cross-cutting rules. The turkey heart rows
  are filed under `Turkey liver`, not under giblets. Only the livers are complete: chicken, duck, goose and turkey
  each have one, and the generic `Liver, poultry, raw` (563 kJ / 4,75 g) exists for a pack that names no species.
- **`Duck, leg, meat and skin, raw` is a broken row** at 438 kJ and 3,10 g of fat — leaner than SAFAD's own
  skinless `Duck, meat, raw` (531 / 5,95) and impossible for a skin-on leg, which the packs declare at 1130 kJ and
  23,0 g. Skin-on duck legs keep the cut row with the defect flagged (the precedent the beef-knuckle and veal-roast
  rows follow); anything downstream that reads nutrition from it will be wrong by nearly a factor of three.
  `Duck, breast fillet` has the opposite problem: at 1390 kJ and 29,4 g of fat it is right for skin-on breast while
  Rimi declares 752 kJ and 12,0 g, a figure that looks measured on trimmed meat.
- **This shelf has the worst panel-error rate yet: 5 of 46.** Three breast-fillet packs print 484 kJ beside
  102 kcal (which is 427, and the macros say 430), and the chicken quarters and the turkey goulash both print 0 kJ
  beside a live kcal. The kcal half is the sound one every time. Grade on what the retailer printed and record the
  recovery in Comments — and note that the Estonian file had independently caught the same 0 kJ on the same turkey
  goulash spec, so these are spec-level errors in Rimi's data, not per-market typos.

#### Ground meat

- **Lithuanian mince glossary** (rimi.lt `ground-meat/SH-9-24-2`, 27 products, a flat shelf with no
  subcategories): **faršas** and **smulkinta mėsa** both mean mince and Rimi uses them interchangeably;
  **maltinis** is the formed patty, **mėsainiams** "for burgers", **paplotėlis** a flat patty, **kukuliai**
  meatballs, **kebabai** kebabs and **iešmeliai** skewers; **rieb. 18%** / **20%** / **30%** is the fat grade,
  **be E** "without E-numbers", **petukai** thigh pieces. Where each goes is the Estonian mince table above —
  pure pork to `Sausage meat, pure pork, raw`, the mix to `Sausage meat, pork and beef, raw`, chicken and turkey
  to `Poultry, minced meat`, beef to a `Beef, minced steak` bracket — with the differences below.
- **The fat grade is in the Lithuanian name, and beef is the only species SAFAD brackets, so here the bracket is
  used.** This is the one place the Estonian and Greek rule inverts: there the grade is never named and the whole
  minced-beef family therefore sits on the 15% row, while `Atšaldyta smulkinta jautiena AGARAS, 20%` takes
  `Beef, minced steak, 20% fat, raw` because the number is in the name and the eval can reach it. The letter then
  records what the pack really is — 16,2 g of fat against that row's 20,0 — since these percentages are maxima,
  not contents. Two qualifications:
  - **A percentage SAFAD has no bracket for falls back to the composition.** Rimi's 18% beef is qualified on the
    pack as *riebumo procentas mažesnis negu 18 %*, and its declared 14,5 g lands on the 15% row almost exactly.
  - **Pork and the pork-and-beef mix have no brackets at all**, so a stated grade changes nothing for them: the
    family stays on its one row and the grade absorbs a 14-40 g spread. That is why the 30% pork mince is the
    worst-fitting row on the shelf — 40 g of fat against 21,2 — and why no other choice could have helped it.
- **Format wins over cut, but a shape is not a format.** *Smulkinta kiaulienos kumpis* names the leg and takes the
  mince row; *faršas iš vištienos filė* names the breast fillet and takes the poultry mince row. But **`kebabai`
  is a shape**, so aged minced beef kebabs stay on the mince bracket, while **`kebabų iešmeliai` — on a skewer —
  takes `Beef on skewer`**, the same call the Estonian šašlõkk packs make. That row is a lean cubed brochette at
  700 kJ and 4,0 g of fat, so a seasoned minced kebab on a stick overshoots its fat fourfold and grades C; keeping
  the format is still right, because a skewer is what the name sells.
- **`Beef, meat balls, cooked` and `Burger, beef based, 15% fat, raw` are byte-identical**, so the choice between
  them is one of identity and changes nothing downstream. The split: **balls take the meatball row, patties the raw
  burger row**, which also keeps the Estonian *pihv*/*burgeripihv* rows where they are. There is no pork meatball,
  pork patty or mixed burger row, so a pork-led ball loses its species as well as its fat, and a beef-and-pork
  burger goes to the beef row on the majority species.
- **A bound meat mass is C on a plain-meat row and B on the mixed one.** Three of these specs are water-and-fibre
  bulked — the beef *faršas* at 80% meat, the pork one at 81% with transglutaminase, the turkey *petukų faršas* at
  50,1% — and no SAFAD row carries such a mass, so each is C on its species' plain row. The mixed
  `Kiaul. ir jautienos faršas`, also 50,1% meat, is **B**: `Sausage meat, pork and beef, raw` is itself a seasoned
  sausage meat at 1,70 g of salt and 26,5 g of fat, so a bulked mixed mince fits it in composition and in
  character. The fit is the archetype's seasoning rather than a match of the food, and the Estonian twin reads the
  same way. All four specs appear on both shelves with identical panels.
- **Grade two variants of one product together, not on the threshold between them.** The two Vilniaus paukštynas
  chicken minces differ by 0,3 g of declared fat, which is enough to put one either side of the fat-penalty line.
  A letter that splits them there records the threshold and not the mapping, so both carry an explicit B. This is
  not licence to hand-grade a family: where two packs differ by a real compositional step — the mixed mince at
  16,5 g against the 20% one at 20,0 — the mechanism decides and the letters may differ.

#### Lithuanian marinated meat and fish: the skewer rule, and where it stops

`06_marinated_meat_fish` (rimi.lt `marinated-meat-and-fish/SH-9-15`, 79 products) is the seasoned counterpart of
the fresh-meat, fresh-poultry and fresh-fish shelves, and the Lithuanian twin of the Estonian `seasoned-pork` and
`seasoned-poultry` subcategories. Four children, summing exactly to the parent listing: marinated poultry 28,
marinated pork and beef 27, fresh sausages 14, marinated fish 10. **Not one of the 79 ids or names appears in any
other Lithuanian file** — the shelf never repeats a pack from `05`, `07` or `08`; the closest thing to a collision
is a name pair differing by the token `mar.` (`Atšald. skr. atlant. lašišos kepsn.` in `08` against
`Atšald. mar. skr. atl. lašišos kepsneliai` here), and both take `Salmon, raw, farmed`. **The whole shelf is raw**:
every pack is chilled meat or fish plus a marinade, and not one of the 14 sausages carries nitrite, so the
raw/cooked axis is settled before the mapping starts — and it decides the two calls below.

- **The skewer rule holds because each meat skewer row sits inside its own species L3.** `Pork on skewer, raw`
  (549 kJ / 6,50 g fat), `Beef on skewer` (700 / 4,00) and `Poultry on skewer` (700 / 15,0) are all in the Pork,
  Beef and Chicken L3s, so *šašlykas* or *iešmelis* in the name buys the format at no cost to the species. That is
  what makes "format wins over cut" cheap, and it is why the same rule sends a *kumpio šašlykas* (leg) and a
  *mentė* pack in mayonnaise marinade to the skewer row rather than to their own cuts.
- **It stops at the fish counter.** `Fish on skewer` is a `Composite food / Fish and seafood based meals / Fish and
  vegetables meal` row naming neither a species nor a fish, so taking it would cost the whole fish L1 for a pack
  that is 94% catfish fillet and 6% marinade. **A fish skewer keeps its species row.** Generalised: the format wins
  only while the format row stays inside the species branch; where it does not, the species wins, because species
  is what drives the footprint. It costs nothing to state here — the Clarias packs are C either way — which is
  precisely why it is worth stating before a salmon skewer turns up.
- **Marinated pork skewers are class `'f'`, and half of them land at C for the right reason.** SAFAD holds exactly
  one pork skewer row, so nothing was chosen and the letter is read off energy alone. That row is a lean French
  brochette; Lithuanian *šašlykas* is cut from the neck and runs 640-1380 kJ against its 549, so six of eleven are
  "a row of the wrong richness" and five are inside 60%. The lean end is real, not an artefact: a kefir-marinated
  leg šašlykas at 643 kJ and a Rimi neck one at 640 sit almost on the row.
- **A raw marinated wing keeps the raw wing row, not the marinated one.** `Chicken, marinated wing, roasted/baked`
  is SAFAD's only marinated poultry row and the Estonian file takes it for a *pooltiivad* pack — but that pack sits
  on the **pre-cooked** shelf, and this row is *roasted*. Cooking is a SAFAD axis and marinating is not, so ten
  chilled raw wing packs stay on `Chicken, wing, meat and skin, raw`. It is also the better fit for the majority of
  them (-10% on energy for the lean ones, where the marinated row is -24%).
- **Wing sections keep the explicit B the fresh-poultry shelf gave them**, whole wings the named-cut class — so a
  mid-section pack lands at B even at -10% on energy, where a named cut would have been A.
- **A duck šašlykas loses its species and says so.** SAFAD has no duck skewer and no poultry skewer outside the
  Chicken L3, so `Antienos šašlykas` takes `Poultry on skewer` with an explicit C, exactly as `Kalkuni šašlõkk`
  does in `rimi/et/05_meat` — and note that turkey *does* have a plain `Turkey, meat, raw` row, so that file chose
  the format over the species knowingly. The cost here is visible: `Duck, meat, raw` fits this pack to 9%.
- **The rest of the duck shelf keeps the species.** Skin-on duckling breast fillet → `Duck, breast fillet` (`'c'`);
  skinless duck thigh meat → `Duck, meat, raw`, the fresh-poultry call, and the one A among the meats. **A whole
  stuffed duckling has no row**: the duck L3 carries no eviscerated-carcass entry, so `Duck, meat and skin, raw`
  takes a bone-in bird stuffed with apples, which is a substitution and an explicit B.
- **A whole marinated bird is the eviscerated-carcass row and grades `'c'`** — *viščiukas*, *tabaka* (the bird
  flattened for the pan) and the pear-marinated broiler are all one mapping. Flattening is not an axis, and the
  10,9-13,8 g of protein against the row's 18,6 measures how much of each pack is brine.
- **The fresh sausages are the `Chipolata slim sausage, raw` family again, and *kapotos* does not buy Toulouse.**
  Twelve pork packs on the chipolata row, with the Estonian reservation held: `Toulouse sausage, raw` is kept for a
  name stating belly or fat, and *kapotos* (coarsely chopped) is a grind, not a recipe. The cost is two C grades at
  1453 kJ against 1050 where Toulouse's 1330 would have fitted — the same "label wins, the letter carries the cost"
  trade the boiled `virta dešra` family makes. **`kumpinė dešra` shares the row**: the fresh L3 has no calibre axis,
  so a large-calibre leg-meat fresh sausage sits with the *dešrelės*, and its 13,8 g of fat is the lowest of the
  twelve, which is the leg showing. `Ham sausage` is a boiled sausage and this one is raw.
- **A fresh chicken sausage keeps the bird and a turkey one pays for it.** 70,4% chicken plus pork fat →
  `Poultry sausage`, not the fresh-pork chipolata, because species drives the footprint. Turkey has no sausage row
  anywhere, so the turkey griller is an explicit C on the chicken L3 row — and it is numerically the *best*-fitting
  poultry sausage on the shelf, which is the same trap the 97%-turkey dried sausage sets in `07`.
- **Six ids on this shelf are also in the Estonian files, and all six agree completely** — archetype and letter,
  across `et/05` (the mayonnaise pork šašlykas, the garlic-marinated belly slices), `et/06` (two Rimi fresh pork
  sausages and the herbed chicken one) and `et/08` (the Žuvėja Clarias fillet). Every Estonian letter there was
  typed by hand before `grade.py` existed and every Lithuanian one is computed from the class, so on identical
  panels the rule reproduces the judgment six times out of six. That is the counterweight to the 23 divergences in
  the fish files: see [`rimi/et/CLAUDE.md`](rimi/et/CLAUDE.md).
- **This is the worst shelf in the tree for panel faults: 9 of 79, 11%.** One pack carries three at once (336 kJ
  beside 139 kcal, and 28,5 g in *both* the fat and the protein cell of a marinated seabream). Two more are
  internally consistent and still unbelievable — a pork griller declaring 6,4 g of protein on 79% pork, where
  37x19 + 17x7 is exactly the 828 kJ printed, and a chicken sausage declaring 29,0 g of fat where its own
  ingredient line reconstructs to about 4 g — which is the KAIJA fabricated-cell shape from `08` twice over. One
  panel is templated across a breast-fillet pack and a wing-section pack, where 16 g of fat fits only the wing.
  The remaining three are zero- or near-zero-filled cells: 0,0 g of salt beside an ingredient line listing salt
  twice, and the saturates cases in the note under working-method rule 6.

### Sausages, ham and other cured meat
- **Processing, not species, is the primary axis in the sausage branch** — the reverse of every fresh-meat rule
  above. SAFAD's five sausage L3s grade by how wet the sausage is, and the Estonian name states exactly that,
  so map the processing term first and let the species go where it can:
  - **keeduvorst** (emulsified cooked: lastevorst, doktorivorst, juustuvorst, lemmikvorst) →
    `Semi-dry sausage / Mortadella`. The L3 literally named `Cooked sausage` holds only blood pudding, liver
    sausage and raw beef blood, and doktorivorst is the Soviet analogue of bologna, so this is a type match and
    not just a compositional one. **But it is not the only cooked-sausage row: see the five that sit outside the
    Sausages L2 below, and use `Cooked sausage, pure pork` where the name says only "cooked sausage".** These
    Estonian rows are left as they are rather than re-graded from here — see the note in `rimi/et/CLAUDE.md`.
  - **poolsuitsuvorst / p/s / suitsuvorst / kuumsuitsuvorst** → `Cooked smoked sausage / Montbeliard sausage`.
  - **täissuitsuvorst** → `Cooked smoked sausage / Morteaux sausage` (the drier of the two).
  - **Keep `Frankfurter sausage` free for the viinerid** — it is the obvious-looking home for a smoked sausage
    and would collide with that category later.
  - **külmsuitsuvorst / toorsuitsuvorst / salaami** → the `Dry sausage` L3. **Salt is the discriminator**:
    SAFAD's dry sausages carry 3,9-4,75 g, Estonian täissuitsuvorst only 2,2-3,4 g, so a 4 g salt reading is
    what confirms a sausage is genuinely dried rather than heavily smoked.
  - **keedusalaami / maitsesalaami** are cooked sausages despite the salami name — 2,4 g salt settles it.
- **The Sausages L2 is not the whole sausage vocabulary: five more rows sit in the pork L3 of `Livestock meat`,
  and three of them are the best home for a name no Sausages row serves.** Searching the branch by L2 misses them
  entirely, which is how the Estonian file came to record sardell and bockwurst as having no archetype at all.

  | row | kJ | fat | protein | salt | what it is |
  |---|---|---|---|---|---|
  | `Cooked sausage, pure pork` | 1330 | 29,0 | 13,8 | 1,90 | saucisson cuit: the plain boiled pork sausage |
  | `Strasbourg sausage` | 1200 | 25,9 | 12,4 | 1,99 | the knack, a short thick frankfurter |
  | `Smoked Alsatian sausage or Landjäger` | 1240 | 24,9 | 19,0 | 2,75 | the semi-dried smoked hunter's sausage |
  | `Garlic sausage` | 1140 | 23,6 | 15,0 | 2,00 | saucisse à l'ail, boiled |
  | `Sausage in a brioche crust, cooked` | 1260 | 17,4 | 14,7 | 1,38 | saucisson en brioche |

  Their L3 is `Pork / piglet meat (Sus scrofa)`, so they name the species where the Sausages rows do not — which
  is a reason to prefer them for a pork sausage and a reason not to use them for anything else. **`Garlic sausage`
  is a trap on a Baltic shelf**: the Lithuanian *česnakinė dešra* is a cold-smoked dried sausage at 1639 to
  2089 kJ, where this row is a boiled one at 1140, so the flavour word matches and the processing does not.
  The rest of the sausage vocabulary really is absent, and that search was exhaustive: no kabanos, no krakowska,
  no bockwurst, no cheese-bearing sausage, no skilandis, and the only chitterling rows are finished French
  sausages under `Stomach (beef)`.
- **Named types beat the processing term when both are in the name** (same rule as pork šašlõkk): **servelaat**
  → `Semi-dry sausage / Saveloy or cervelat` (A, name-exact), **sinkvorst** → `Preserved meat / Cooked Ham,
  Pork / Ham sausage` (a sausage row filed outside the Sausages L2), **chorizo** and **pepperoni** → the
  `Dry sausage / Spicy pork sausage with red pepper` rows, which are precisely that food — Rakvere's chorizo
  lands within a few percent on all five macros. **Krakov** (kiełbasa krakowska) has no row; it joins Montbeliard.
- **Estonian sausage names carry no species at all, and the same head term is a different animal at different
  producers.** Valla's and Wõro's lastevorst and juustuvorst are broiler-meat-mass-led while Rakvere's and M&M's
  are pork; Oskar's lemmikvorst is beef-led and M&M's is pork. Map species-first to
  `Poultry / Chicken meat / Poultry sausage` where the recipe is poultry-led, grade **C**, and say in Comments
  that the name cannot reach it. This is a ceiling on the whole category, not a per-row defect.
- **The Chicken L3 has no processing axis for sausages** — `Poultry sausage` and `Poultry sausage, delicatessen
  style` are data-near-identical (use the plain one), so a hot-smoked poultry sausage and a boiled one land on
  the same row.
- **Cheese in a sausage is invisible**: juustuvorst, suitsujuustuvorst and the cheese-bearing täissuitsuvorstid
  have no row. `Mortadella with pistachios` is SAFAD's only inclusion variant and pistachios are not cheese.
- **Cured meat is the best-served branch after Beef.** `Preserved meat` has 38 rows over six L3s and the
  Mediterranean charcuterie maps almost one-to-one: **Jamón Serrano** → `Serrano dry-cured ham`,
  **prosciutto di Parma** → `Parma dry-cured ham`, **prosciutto crudo** → `Cured ham, raw` (which is jambon
  cru, the same food), **jamón curado** → `Dry-cured ham`, **pancetta** → `Pancetta, dried`, **prosciutto
  cotto** → `Cooked ham, choice`. Most land within a few percent, so this is where the A grades are.
- **Cooked ham is graded by French quality marks with no unqualified row** (choice / superior quality ×5 /
  Parisian-style). Take **`Cooked ham, choice`** for plain Estonian **sink** and **`Cooked ham, smoked`** for
  **suitsusink** — smoke is the one axis the Estonian name reliably states. The L3 has **no cut axis**, so a
  cured loin and a cured leg land on the same row.
- **`Cooked pork shoulder, choice`** is the cured-meat home for **kaelakarbonaad**, matching the fresh-meat rule
  that sends the neck to the shoulder.
- **Estonian peekon is streaky belly → `Bacon / Smoked lardoons, raw`.** `Bacon, back` exists but stores 2,60 g
  fat, below any real back bacon, so even *inglisepärane* (English-style) back bacon fits the lardoon row better.
- **Cured poultry has one row and no cut axis**: smoked chicken breast, thigh, leg, drumstick and wings all take
  `Poultry / Chicken meat / Salt curing roast poultry, cooked`, whose 2,00 g salt is what marks it as the cured
  row rather than a plain cooked cut. Poultry *ham* is separate and better served —
  `Preserved poultry / Chicken cooked ham, in slices`, `Ham, turkey / Turkey cooked ham, in slices`, and
  `Poultry ham in cube, grated or minced` for **kanasuitsuliha kuubikud** (a name-exact format match).
- **`Pekk` (cured sliced back fat) leaves the meat L1** for
  `Animal and vegetable fats and oils / Animal fat / Pork lard (Schmaltz) / Pork fat, raw`, which matches its
  88,7 g fat and 2,9 g protein almost exactly. `Pork, back fat, rindless, raw` names the cut but stores only
  80,60 g.
- **Other cured-meat homes worth knowing**: **lepasuitsupõsk** → `Pork, jowl, rindless, raw`; smoked **ribi** →
  `Pork, spare-ribs, raw`; smoked **seakeeled** → `Tongue, pork, raw`; **keele-želeevorst** →
  `Meat specialities / Meat in aspic`, whose two entries are both salad-dressing preparations and neither is
  tongue; smoked pork-bone soup bundles → `Pork, knuckle or shank, raw`, following the fresh soup-bones precedent.
- **Multi-component assortment trays** (singivalik, "Hispaania liha valik" — ham *plus* salami *plus* coppa in
  one pack) have no archetype for the blend. Map on the head term the name gives, grade **C**, and name the
  components in Comments. Do not blank them: SAFAD has rows for every component, so the gap is the bundle, not
  the food.
- **Keep one archetype per name family even when a second row fits one member better.** Two products called
  Chorizo sit on `Spicy pork sausage with red pepper, no precision` although the `in large slices` row matches
  Rakvere's on all five macros — "in large slices" is a format no Estonian name states, and splitting a name
  family across two rows puts a distinction the eval cannot reach into the ground truth.

#### Lithuanian sausages and cured meat: the name states the processing, and often the species

The Lithuanian shelf inverts the Estonian problem. There the name carries neither the processing nor the species
and the mapping had to be recovered from the ingredient list; here `virta`, `karštai rūkyta`, `šaltai rūkyta`,
`vytinta`, `mažai rūkyta`, `sūdyta`, `kepamos`, `troškinta` and `lėtai virti` are on the front of the pack, and
so, often, is `vištienos`, `kalakutienos`, `jautienos` or `kiaulienos`. **So the processing term picks the L3 and
the species term picks between the pork rows and `Poultry sausage`.**

| Lithuanian | processing | archetype |
|---|---|---|
| `virtos dešrelės` | boiled small links, the Wiener | `Cooked smoked sausage / Frankfurter sausage` |
| `virtos sardelės` | boiled, short and thick | `Livestock meat / Pork / Strasbourg sausage` |
| `virta dešra`, `daktariška` | boiled large calibre | `Livestock meat / Pork / Cooked sausage, pure pork` |
| `dešra su kumpiu` | boiled, with ham | `Cooked Ham, Pork / Ham sausage` |
| `medžiotojų dešrelės` | hot-smoked hunter's | `Livestock meat / Pork / Smoked Alsatian sausage or Landjäger` |
| `karštai rūkyta dešra/dešrelės/saliamis` | hot-smoked | `Cooked smoked sausage / Montbeliard sausage` |
| `servelatas` | scalded | `Semi-dry sausage / Saveloy or cervelat` |
| `bratwurst`, `bavariškos ... kepimui` | raw, to fry | `Fresh and lightly cooked sausage / Chipolata slim sausage, raw` |
| `šaltai rūkyta dešra`, `vytinta dešra`, `skilandukas` | cold-smoked, dried | `Dry sausage / Dry sausage` |
| `šaltai rūkytas saliamis`, `vytinta ... saliamis` | dried salami | `Dry sausage / Salami` |
| `vytintos dešrelės`, `kabanos`, `lazdelės`, `salamini` | dried snack sticks | `Dry sausage / Sausage, dried` |

Three of those rows are the ones filed outside the Sausages L2, and each of them fills a hole the Estonian file
had to work around. **`sardelė` is a knackwurst and `Strasbourg sausage` is a knackwurst** — the gap that file
lists as unserved. **`Landjäger` is the Alsatian hunter's sausage and `medžiotojų dešrelės` are hunter's
sausages**: the same food under two names, and the fit says so, with five of ten packs inside 8% on energy where
Montbeliard leaves the family 20 to 30% low.

- **The boiled family follows the label and pays for it, and that is the right way round.** `virta dešra` says
  "cooked sausage", so it takes `Cooked sausage, pure pork` — but the Lithuanian sausage is a third leaner than
  the French one (a 940 kJ / 19 g family mean against 1330 / 29,0), so sixteen of the daktariška rows grade C on a
  mapping whose type is exactly right. `Ham sausage`, at 982 kJ and 17,5 g of fat, fits the family within 4% and
  would turn most of those C grades into A and B — and it is still the wrong row, because these names do not say
  ham. It is used for the one pack that does, `Dešra su kumpiu`, at 25% ham and an A. **Where fit and label
  disagree, the label wins and the letter carries the cost**; the alternative is a ground truth that no answer
  derived from the name could reach.
- **`saliamis` alone means the dry sausage; a processing word in front of it overrides that.** `Šaltai rūkytas
  saliamis` and `vytintas saliamis` are the real dried article and take `Salami`, but `karštai rūkytas saliamis`
  is a hot-smoked cooked sausage (Montbeliard) and `virtas saliamis` a boiled one, which its 2,9 g of salt
  confirms against the dry sausages' 3,9 to 4,8. The Valstietis pair makes the point twice: two byte-identical
  packs, one named `Virtas saliamis` and one only `Saliamis su juodaisiais pipirais`, and putting the second on the
  dry row because its name omits `virtas` would give one product two archetypes.
- **`dešra` and `dešrelės` are a real distinction in the dried branch too.** A dried `dešra` takes `Dry sausage`
  (1740 kJ, 24,2 g protein) and dried `dešrelės`, `lazdelės`, `šiaudeliai` and every kabanos take `Sausage, dried`
  (1910, 27,3), which is where the snack sticks belong: 21 of 33 land inside 11%, the best-fitting family in the
  file. **`SALAMINI` is a trade name, not a type word** — at 30 to 37 g of protein those sticks sit with the
  snacks and not on `Salami`'s 17,7 g.
- **A poultry-led recipe behind a silent name is still mapped to the poultry row, and the letter is an explicit
  C.** Twenty-six packs here are led by chicken or mechanically separated chicken behind nothing but a brand name
  — Klaipėdos, Lietuvos standartas, Pajūrio, Hot-Dog, Pusrytinė, and a `Virta DAKTARIŠKA dešra` that is 52%
  chicken. Species drives the footprint, so the mapping follows the recipe; the eval sees only the name, so the
  letter records that it cannot be reached. This is the one Estonian sausage rule the Lithuanian shelf inherits
  unchanged, and it is worth knowing how much of the shelf it covers: 43 of the 429 rows are on `Poultry sausage`
  and 33 of those are C.
- **Turkey has no sausage row anywhere**, so a turkey cervelat, a 53%-turkey frankfurter and a 97%-turkey dried
  sausage are all C on the chicken L3's `Poultry sausage` — the same species gap as minced turkey. The 97% one is
  the trap: at 908 kJ against the row's 978 it looks like an A and misses both the species and the drying.
- **`skilandis` has no row.** The Lithuanian cured sausage stuffed into a stomach or bladder and cold-smoked joins
  the generic `Dry sausage`, the same call Krakov sausage gets in the Estonian file; both skilandukai land 19 to
  22% under it.
- **In the cured branch the smoke word says whether the meat was cooked.** `Karštai rūkyta` is hot-smoked, i.e.
  cooked, so a hot-smoked ham, loin or fillet is a **`Cooked ham, smoked`** — and the Cooked Ham L3 has no cut
  axis, so all three land on one row. `Šaltai rūkyta`, `mažai rūkyta` and `sūdyta` leave the meat raw, so those
  take the `Ham, pork` L3: **`Cured ham, raw, smoked`** for a ham and **`Cured ham, raw, smoked, reduced fat`**
  for the lean loin, cured pork loin having no row of its own. The cold-smoked hams are the best fits on the
  shelf — a Brandenburg at -4% and a `Juodasis kumpis` at +2% on both energy and fat.
- **The hot-smoked hams split into two groups the one row cannot straddle**: a lean set at 2 to 7 g of fat, built
  on 15 to 25% added water, and a fatty set at 13 to 18 g. `Cooked ham, smoked` carries 5,8 g, so the lean ones
  grade A and B and the fatty ones C at 62 to 77% over on energy. Nothing better exists — `Braised ham on the
  bone` at 12,3 g fits them numerically and names a process the pack does not.
- **`šoninė` is the streaky belly and takes the lardoon rows, hot-smoked cooked and cold-smoked raw** — the only
  axis separating two rows 20 kJ apart. **`lašiniai` is cured back fat and leaves the meat L1** for `Pork fat,
  raw`, three of four inside 9%. Both are graded `'c'`: the cut is named exactly and the trim still votes, which
  is what puts a 49 g belly at B rather than A.
- **Cured poultry keeps its species in three different places.** Smoked chicken cuts — breast, fillet, drumstick,
  thigh, wing, drumette, quarter — take `Salt curing roast poultry, cooked`, whose 2,00 g of salt marks it as the
  cured row; its 1,73 g of fat is a lean skinless breast, so twelve of fourteen grade C once skin is in the pack.
  **Turkey breast, hot- or cold-smoked, takes `Ham, turkey / Turkey cooked ham, in slices`** — SAFAD's only
  cured-turkey row, so a whole 1 kg breast loses the format and keeps the species, and six of nine grade A.
  **A formed chicken ham or roll takes `Preserved poultry / Poultry ham in cube, grated or minced`**, not
  `Chicken cooked ham, in slices`: the sliced row is a lean whole-muscle ham at 1,79 g of fat where these carry 7
  to 9,4, and a *vyniotinis* is minced breast and leg bound with starch, which is what "in cube, grated or
  minced" describes.
- **`slėgtainis` is brawn** and `ausų ir liežuvių vyniotinis` — an ear-and-tongue roll — is brawn too, at -2% on
  energy. Canned pork, chopped ham and `turistų pusryčiai` (luncheon meat) take the same row, following the
  absent-canned-meat rule; canned beef keeps its species on `Beef, stewing meat, cooked`, and so does canned
  **game**, where the species is lost outright.
- **Two Lithuanian names are misleading and the ingredient list catches both**: `Vytinta pjaustyta filė` is 97%
  pig snouts, not fillet, and `Vytinta kalakutienos dešra FUET` is pork-led at 65 g of pork per 100 g with the
  turkey behind it. Map the food and grade C for the name.

#### Grill, raw and blood sausages

- **`Sausages / Fresh and lightly cooked sausage` is the home for the raw grilling sausages** (**toorvorst**,
  **toorvorstike**, **toored** …), and the generic row is **`Chipolata slim sausage, raw`**. Its
  1050 kJ / 20,6 g fat / 15,8 g protein land within 5% of the 21-product Rimi family mean
  (1101 / 21,5 / 15,8 — protein to the decimal). `Toulouse sausage, raw` looks like the more obvious "coarse
  pure-pork fresh sausage" but runs 21% hotter, 37% fattier and 16% lower on protein, so keep it for the one
  case the name earns it: **triibuliha** (streaky belly) in the name licenses the fattier row. Chipolata's
  "slim" is not name-reachable either, but it is the least-qualified row in the L3 — the others are a region
  (Toulouse), a spice profile (merguez) or a truffled boudin blanc.
- **Nitrite is the raw/scalded discriminator, and the name only half-reaches it.** **toorvorst** and **toored**
  say raw outright; **grillvorst** says only "for grilling" and covers both technologies — 20 of Rimi's 27 carry
  nitrite plus phosphates, starch and rind, but Matsimoka's and Linnamäe's carry no phosphate and run 16-21 g
  protein, i.e. fresh sausages under a grill name. Do not try to split `grillvorst` on composition; it is one
  name family.
- **Scalded **grillvorst** → `Semi-dry sausage / Saveloy or cervelat`.** Cervelas *is* the scalded link sausage
  that gets boiled or grilled, and the family mean (1185 kJ / 23,5 g fat / 1,88 g salt) sits within 6% of the row
  on energy, fat and salt; protein runs 23% high, the one axis that misses. It beats `Mortadella` (a sliced
  cold cut, +22% salt) and `Montbeliard` (adds smoke the name does not state). `Frankfurter sausage` fits best of
  all numerically and is still **reserved for the viinerid** — hold that line. This puts grillvorst on the same
  row as **servelaat**, which reaches it by name; two name families sharing a row is fine, as romsteek and
  tagaosasteik already do.
- **The `Cooked sausage` L3 finally gets its natural tenant**: **verivorst** → `Black pudding (blood sausage),
  refrigerated`, the only blood row. Estonian verivorst is **barley-led** (groats are the first ingredient, 15 g
  carbohydrate) where boudin noir carries no cereal, so the archetype holds more than double the fat — 27,1
  against 12 g. Grade C. **Verikäkk** is a blood *dumpling* with no casing and no row of its own; it takes the
  same archetype.
- **A sausage named šašlõkk stays in the sausage branch** — the exact opposite of the fresh-meat rule that sends
  **šašlõkk** to `Pork on skewer, raw`. The head noun decides: **šašlõkivorst** is a sausage seasoned
  shashlik-style, not a skewer. Rimi has six, and three of them carry an explicit second token
  (`šašlõki toorvorstid` raw, `šašlõkigrillvorst` scalded, `šašlõkivorstid broilerilihast` chicken) while three
  are bare. **The three bare ones go on one row** even though `Šašlõkivorst Ehe Rannarootsi` is demonstrably
  scalded and matches `Toulouse sausage, raw` within 12% — splitting a bare name family across the raw/scalded
  axis puts a distinction no Estonian name states into the ground truth. Cost: one C at 41% on energy.
- **A name family may split when the name itself carries the token.** `Chorizo grillvorstid juustuga Nõo` stays a
  fresh scalded sausage at 1296 kJ instead of joining the dry `Spicy pork sausage with red pepper` rows at
  1760 kJ / 4,24 g salt that carry the other Estonian chorizos, because **grillvorstid** is in the name. Contrast
  the two plain `Chorizo` products, which had to be forced onto one row.

#### Pre-cooked and grill-ready meat

- **Ribs split on a name-reachable axis**: plain marinated **ribi** / **grill-ribi** → `Pork, spare-ribs, raw`
  (both Rakvere packs land inside 2% on energy — the only A grades in the category, since marination is not an
  axis); **eelküpsetatud** / **kauaküpsetatud** → `Pork, spare-ribs, braised`.
- **Pre-cooked rib racks declare nutrition on the bone.** Rannarootsi's three read 429-460 kJ at 8,5-9 g protein
  against ~18-20 g for rib meat, and all three share one templated block. The archetype is meat-only and is right
  for the food; the retailer's basis is the mismatch, so grade B and say so rather than mapping to the number.
- **Pulled pork with no cut in the name → `Pork, roast, cooked`**, the cut-less cooked row (energy inside 7%),
  not `Pork, shoulder, cooked` even when the pack is 93% shoulder — the same call as `veisepraad kondita` →
  `Beef, round, raw`. Shredding is not an axis.
- **`Pork filet mignon` is the real tenderloin family**, so cooked **sisefilee** → `Pork filet mignon, cooked`.
  `Pork tenderloin, lean, raw` is one of the three broken loin-numbered rows (987 kJ / 18,8 g fat) the cut
  glossary already warns about.
- **Cooked cured hock**: **küpsetatud / eelküpsetatud tagakoot** → `Preserved meat / Cooked Ham, Pork /
  Knuckle of ham, cooked` — jambonneau cuit, the same food, with protein and salt landing close. Keep this
  distinct from the *raw* smoked pork-bone bundles, which take `Pork, knuckle or shank, raw`.
- **Marinated wings take the marinated row even though it is roasted.** **broileripooltiivad** at 1,4 g salt →
  `Chicken, marinated wing, roasted/baked` (1,69 g); `Chicken, wing, meat and skin, raw` carries 0,22 g and
  would understate salt six-fold. Same logic as pork šašlõkk taking the only marinade-bearing pork row.
- **Nitrite-cured cooked chicken breast slices → `Salt curing roast poultry, cooked`**, the same row as the
  smoked chicken cuts; its 2,00 g salt against the plain cooked breast's 0,14 g is the tell.
- **Pulled meat in sauce has almost no home.** `Meat-based meals / Meat stew` is the right L3 but every row is a
  vegetable-diluted 326-605 kJ dish naming a species and a garnish. Pulled chicken in tomato sauce can take
  `Chicken, Basque style` (the poulet basquaise dish class) at C, understating the footprint by ~40%; **pulled
  pork in BBQ sauce gets nothing** — the L3's only pork rows are a sausage-and-cabbage stew and salt-cured belly
  with lentils, both naming vegetables the pack lacks and both ~45% below its energy, while the meat L1 fails the
  other way (`Pork, roast, cooked` carries 30,5 g protein against 16 g, because half the pack is sauce).

#### Frankfurters, snack sausages and jerky

- **`Frankfurter sausage` is the viinerid row, and holding it free through three sausage files was right.**
  **viiner** is the Estonian Wiener, so it is a translation match, and the 17-product pork family mean
  (1021 kJ / 20,0 g fat / 13,9 g protein / 1,89 g salt) sits within 17% of the row on energy and fat and within
  2% on protein and salt.
- **sardell shares that row.** It is a short thick frankfurter — the same scalded emulsified technology, differing
  only in calibre, which SAFAD does not encode — and there is no sardell or bockwurst row. Its 7-product mean is
  within 3% of Frankfurter. Do **not** send **suitsusardell** to `Montbeliard sausage` under the smoked-sausage
  rule: Montbeliard fits energy marginally better but is coarse-ground and misses protein by 36-42%, and a
  frankfurter is itself a smoked sausage, so the smoke needs no separate row.
- **Kabanos, snäkkvorst and the other dried snack sausages → `Dry sausage / Sausage, dried`**, the generic dried
  row; the best of them land inside 3% on energy, fat and protein. **Salt is the family-wide miss** — Estonian
  dried snack sausages carry 2,1-3,8 g against the French dry sausages' 4,2-4,8 g, the mirror image of the
  discriminator that separates täissuitsuvorst from salami. Named types still win: **chorizo** and **pepperoni**
  → the red-pepper rows (Nõo's chorizo snack is inside 1% on energy and fat, the closest chorizo match in any
  rimi file), **fuet** → `Salami` per the eleven fuet/salchichón/spianata rows in file 06, **salaami** →
  `Salami`, and **õllesigar** (beer cigar) → `Beer sausage`, the only beer-named row.
- **Jerky has no archetype anywhere, and the ceiling is protein.** The highest-protein row in the whole meat L1 is
  `Beef, cheek, braised or boiled` at 39,2 g; above that the database holds no meat at all, only dried gelatine
  (86,9 g) and egg-white powder (81,1 g). Estonian **vinnutatud** and **kuivatatud** snacks run 27-81 g. Map them
  species-first onto the nearest dried or cured row of the same animal and grade the whole family **C**:
  beef → `Beef, dried / Bresaola`, pork → `Ham, pork / Dry-cured ham`, chicken → `Chicken meat / Salt curing
  roast poultry, cooked`. Chick&Go's dehydrated chicken breast at 75-81 g protein is 3,4× its archetype — the
  most extreme pair of rows in the rimi set.
- **A dried *poultry* snack sausage is doubly unserved**: the Chicken L3 has one sausage row at 978 kJ and no dry
  variant, so a poultry-led kabanos at 2191 kJ lands 124% over it. Species still wins, because species drives the
  footprint. Poultry-led is decided on the **combined** poultry share (a pack at pork 35% / turkey 30% /
  chicken 20% is poultry-led).

#### Meatballs, patties and breaded cutlets

- **`Beef, meat balls, cooked` is the generic cooked-meatball row** and `Meat burger / Burger, beef based, 15%
  fat, raw` stores **byte-identical** nutrition (906 kJ / 13,3 g fat / 4,80 g SFA / 2,00 g sugars / 18,5 g
  protein / 1,13 g salt), so choosing between them changes nothing downstream. There is no pork meatball or pork
  patty row, so **lihapall, pihv and kotlet** (a formed fried patty, not a chop) all take the meatball row and
  the grade absorbs the species.
- **All formed and breaded *chicken* products → `Meat croquettes / Chicken, nugget, breaded croquette`** —
  nagitsad, friikad, pulgad, kanapallid, kana-juustupihvid, paneeritud kanafileešnitsel, Järlepa and Požarski
  kotlet. Species and breading are both right and **the shape is not an axis**. `Poultry nuggets` in the same L3
  is data-near-identical (1030 v 1050 kJ); prefer the croquette row because it names the species.
- **Kiievi kotlet** joins them: SAFAD has no butter-filled row, and `Escalope cordon bleu` names no species, so
  the chicken croquette row keeps species and breading. Kikas's margarine-rich version is 98% over on fat.
- **Breaded *pork* schnitzel has no row.** `Milanese-style turkey escalope or breaded veal escalope` is the nearer
  of the two breaded-escalope rows and at least names a livestock mammal rather than a bird — C, species lost.
- **`Escalope cordon bleu` is a real one-to-one match** for **fileetasku juustu-singitäidisega** (a loin pocket
  stuffed with cheese and ham): inside 3% on energy and 2% on fat, and the archetype names no species, which
  suits a dish name that does not either.

#### Pâté, brawn, aspic and canned meat

- **maksapasteet → `Pate, pork liver / Pork liver mousse`, not `Pork liver pâté`.** Estonian has one word for both,
  so this is a legitimate tie-break between two plain rows (the Gouda-vs-Edam case), and the mousse is much the
  nearer: the 10-product family mean (938 kJ / 17,9 g fat) sits 27% under the mousse but 57% under the pâté.
  **kreemjas** in the name supports the mousse outright.
- **There is no beef liver pâté row** — the Pate L3s are pork, chicken and goose liver only. Estonian
  veisemaksapasteet is beef-liver-led but half its meat is pork, so it joins the pork liver family at C.
- **Species-exact pâté rows that do exist**: **hanemaksapasteet** → `Goose liver pâté` (name-exact, but the
  archetype is a foie gras preparation at 1900 kJ against a 29%-liver Estonian pack's 913 kJ),
  **kanamaksapasteet** → `Poultry liver pâté`, and **pardimaksapasteet** → `Terrine / Duck mousse`, since the
  Duck liver L3 holds only raw liver and two foie gras blocks.
- **sült → `Meat in aspic / Pork snout in salad dressing sauce`**, following the keele-želeevorst precedent.
  Estonian sült is gelatine-and-water extended at 225-469 kJ where both aspic rows are salad-dressing
  preparations at 494-712 kJ, so the archetype carries several times the fat. **Chicken sült has no poultry
  aspic or brawn row**, so it loses the species too.
- **`Head cheese (Brawn)` is the canned-meat row, not the sült row.** Its 766 kJ is far closer to canned braised
  pork and **turistieine** (luncheon meat, 887-1213 kJ) than to sült. SAFAD has **no canned-meat branch at all**:
  every cooked Livestock row is a lean trimmed joint at 26-36 g protein where canned meat runs 8-17 g, missing
  protein by half and fat by 140-430%, while brawn — a cooked pressed pork product set in its own jelly — lands
  within 10% on protein. Use it for canned pork and for luncheon meat. Canned **beef** keeps its species instead
  (**hautatud** = stewed → `Beef, stewing meat, cooked`, C) and canned **chicken** takes
  `Chicken, meat and skin, roasted/baked`.
- **Check what a turistieine actually contains before mapping it**: Rannarootsi's is 40% liver and belongs with
  the pâtés, and its ingredient list will not even name the species — it reads *sea- või veisemaks*, pork **or**
  beef liver, so no eval answer can be right about the animal.

#### Offal

- The offal branch is well served and produced this file's closest match: chilled raw **seakeeled** against
  `Tongue, pork, raw` is 914/17,2/16,3 against 922/17,2/16,3 — exact on fat and protein, **and it confirms that
  the smoked tongue's 498 kJ in file 06 is a producer error**.
- **broilerimaks** → `Liver, chicken, raw`; **broilerisüda** → `Heart, chicken, raw`; **broileri lihasmagu** →
  `Gizzard, chicken, raw` (SAFAD names the gizzard exactly); **seamaks** → `Liver, pork, raw`; **veisesüda** →
  `Heart, beef, raw`; **seakõrvad** → `Pork ear sat-cured` (also name-exact).
- **There is no adult beef liver row** — the `Veal liver` L3 holds calf and young cow only, so **veisemaks** takes
  `Liver, young cow, raw`.
- **No poultry feet or carcass-part rows.** **broilerijalad** and **broileri seljatükid** go to the cut-less
  `Chicken, meat and skin, raw`; species wins over cut, even though `Calf, foot, raw` would match the feet within
  14%. No chicken row anywhere reaches the backs' 30 g fat (the fattiest, `Hen, meat and skin, raw`, stops at
  20,3 g).

### Fish and seafood

**The species problem inverts here.** Every fresh-fish pack declares its Latin binomial, and SAFAD's fish L3s are
Latin-keyed too, so the species is *verifiable* — the opposite of the meat categories, where Estonian sausage names
carry no species at all. Read the binomial off the pack and match the genus; do not work from the Estonian name.

- The fish L1 is **218 rows over 5 L2s**: `Fish meat` (179 rows, 27 L3s), `Water molluscs` (18), `Fish products`
  (11), `Crustaceans` (8), `Amphibians, reptiles, snails, insects` (2).
- **The Latin L3 names are not taxonomy.** `Lophiiformes (Pediculati)` is named for the anglerfish order but is a
  catch-all holding dentex, garfish, John dory, gurnard, salema, scorpionfish and **golden redfish** across at
  least two orders. `Sea catfish and wolf-fish (Anarhichas)` holds anglerfish, brown bullhead, **pangasius** and
  spotted wolffish. Search the `food_product` column for the species, then accept whatever L3 it sits in.
- Estonian fish glossary: **lõhe** = Atlantic salmon (*Salmo salar*) → `Salmon, raw, farmed`; **vikerforell** =
  rainbow trout → `Rainbow trout, raw, farmed` (`Trout, farmed, raw` in the same L3 is a near-duplicate — 558 v
  553 kJ — so prefer the rainbow-named row); **arktika paalia** = Arctic char → `Arctic char, raw`; **karpkala**
  = carp → `Carp, raw, farmed`; **kuldmerikoger** = gilt-head bream → `Gilthead seabream, raw, farmed`; **kuldne
  meriahven** = *Sebastes norvegicus* → `Golden redfish, raw`; **ruske soomuslest** = *Limanda ferruginea* →
  `Common dab, raw` (*Limanda limanda*, the same genus, in a Flounder L3 that is otherwise soles and turbot);
  **rannakarbid** = blue mussel; **viburhännak** = shrimp.
- **Trim and presentation are not axes.** All five Rimi salmon packs — two boneless fillets, the B- and C-trims
  and the whole gutted fish — share one declared block (932 kJ / 16 g fat / 20 g protein), so the retailer does
  not distinguish them either. Norwegian farmed salmon runs 29% fattier than the archetype.
- **`Arctic char, raw` is a broken row**: 2,20 g SFA against 2,70 g total fat is 81% saturates, which no fish
  approaches, and it is a wild fish where the Estonian product is farmed at 13,1 g fat — 385% over. Flag it.
- **Glazed white fish reads below what a fish fillet can contain.** Rimi's pangasius declares 8 g protein and its
  tilapia 13 g; both are water-glazed before freezing. Map to the species and say so rather than chasing the
  number. A 1,4 g salt reading on an unseasoned fillet is the tell for phosphate-and-brine treatment.
- **`Pangasius, filets, cooked` is cooked-only**, so a raw retail pangasius fillet has to take a cooked row
  against the standing retail-is-raw rule. The same L3's only raw catfish row is `Brown bullhead, raw`.
- **Marinated fish keeps the species row**; marination is not an axis, exactly as for meat. Saare Kala's mango
  marinade pushes trout 47% over on energy and its salt from 0,11 to 1,5 g.
- **Mussels are well served and the sauce is name-reachable**: `Mussels, in a shallot and white wine broth` is
  within 3% of BANTRY's white-wine pack. The garlic-butter pack takes `Mussels, filled (fat, parsley and
  garlic…)` — the only garlic-and-fat mussel row — even though *moules farcies* are stuffed half-shells at
  1170 kJ against a 451 kJ sauced pack, so the two packs keep the sauce distinction their names make.
  **That archetype's stored name ends in a stray 0x85 byte** (a mojibake ellipsis); the build script needs the
  exact string, so write it as `'…garlic\x85)'` in Python.
- **A fish burger patty goes to `Fish products / Fishcakes`**, whose single row is named `Seafood terrine, with
  or without fish` — the processed-cheese precedent of mapping on the L3 when the `food_product` name misleads.
  Rimi's is 92% fish, so protein runs 86% over the terrine while energy runs 41% under; `Haddock, raw` would
  match protein exactly but ignores the added fat and the composite format.
- **Shrimp**: *Litopenaeus vannamei* is absent — the two shrimp L3s are `Crangon crangon` (brown shrimp) and
  `Palaemon serratus` (common prawn), both small cold-water European species. Use the generically named
  `Shrimp or prawn, raw` / `Shrimp or prawn, cooked` pair, which carries the raw/cooked axis the Estonian names
  state (**keedetud** = cooked). Salt runs 3-5× the archetype because the packs are salted and glazed. Rimi
  declares the *same* block for its raw and cooked vannamei packs, so only the name distinguishes them.

#### Lithuanian fresh fish: the binomial decides the row, the name decides only the form

`lt/08_fish` is the whole of `fresh-fish-and-seafood` — 40 products over three subcategories (11 whole fish,
16 fillets and steaks, 13 shellfish), 19 archetypes, 4 A / 23 B / 13 C. Only four packs carry a recipe at all:
a herb-butter salmon fillet, a herb-and-garlic shrimp marinade and the two sauced BANTRY mussel packs.

- Lithuanian glossary, species first: **lašiša** = Atlantic salmon; **vaivorykštinis upėtakis** = rainbow trout;
  **arktinė palija** = Arctic char; **auksaspalvė dorada** = gilthead seabream; **paprastasis vilkešeris** =
  European seabass; **juodasis paltusas** = Greenland halibut; **paprastasis karpis** = carp; **jūrinis ešerys**
  = golden redfish (*ešerys* alone is the perch); **sidabrinė jūrinė lydeka** = silver hake; **europinė stinta**
  = European smelt; **sibirinis eršketas** = Siberian sturgeon; **nilinė tilapija** = Nile tilapia;
  **gelsvauodegis tunas** = yellowfin tuna; **midijos** = mussels; **didžiosios austrės** = Pacific oysters;
  **baltakojės blyškiosios krevetės** = whiteleg shrimp.
- **Two words are false friends.** `lydeka` alone is the pike (*Esox lucius*); only `jūrinė lydeka`, sea-pike,
  means hake. And `šamas` covers three unrelated fish: `ryklinis šamas` (shark-catfish) is the pangasius,
  `afrikinis šamas plėšikas` is *Clarias gariepinus*, and neither is the wels a Lithuanian means by šamas alone.
- **Form words, none of which move a row**: filė fillet, kepsnys/kepsneliai steak, pjausniai slices,
  skrosta/neskrosta gutted/ungutted, su galvomis with heads, be galvos headless, su oda skin-on, be odos be
  kaulų skinned and boned, smulkinta minced, atšaldyta/atvėsinta chilled, atitirpinta/atšildyta thawed, šaldyta
  frozen, šviežia fresh. The one form word that does decide a row is on the shrimp: **virtos** cooked against
  **nevirtos** raw.
- **Where SAFAD files one species under two English names the name-literal row wins; where it files it under one
  name with a wild/farmed suffix the farmed row wins.** Those are not in tension. A silent `Gilthead seabream`
  pack takes the farmed variant because both rows name the species and every European seabream is a cage fish,
  so provenance decides a suffix the name is silent on. But *paprastasis vilkešeris* keeps `European bass, raw`
  rather than `Mediterranean bass, raw, farmed` — the same *Dicentrarchus labrax*, filed three times by
  provenance — because there the choice changes the species descriptor the model would have to produce, and the
  name reaches only the literal one. No letter is bought either way: the Turkish cage fish is 9,4 g of fat
  against a 0,70 g row and a 4,25 g row, C on both.
- **The fit is best where the row shares the product's production system, and farmed retail fish now sits above
  the farmed rows.** `Carp, raw, farmed` against a Lithuanian pond carp is +1% on energy with a 0,74 g fat gap;
  `Arctic char, raw`, a wild row, against a Latvian farmed char is +88% and +10,4 g. Turkish seabass at 9,4 g,
  dorada at 9,6-12 g against a 5,1 g farmed row, Norwegian salmon at 16 g against 12,4, farmed Clarias at 9,5 g
  against 1,27: three of this file's thirteen C grades are that effect alone. Both rows that grade A on a wild
  fish are lean ones — Greenland halibut (778 kJ / 14 g against 700 / 12,7) and silver hake (376 / 1,9 against
  349 / 1,35).
- **A counter pack with no nutrition panel keeps the letter its packaged sibling earns.** Two products here
  publish no table at all — gutted salmon steaks and a skin-on trout fillet, both sold by weight — and the
  standing rule that absent pack data cannot lower a grade where no axis was in question applies: both are B,
  the letter every other salmon and trout pack on the shelf earns. The forced class produces that B unaided for
  the salmon; the trout needs an explicit B, because four raw trout rows exist so its class is `'n'`.
- **Prefer the species-named row over a numerically identical generic one** — `Pacific oyster, raw` over
  `Oyster, raw` (the same figures to the decimal), `Rainbow trout, raw, farmed` over `Trout, farmed, raw` — since
  the species name is what the product name reaches.
- **A live oyster reads low on protein and high on carbohydrate**: 6 g against 8,64, with 4 g of carbohydrate
  from the glycogen oysters store. That is the flesh-to-liquor ratio of a shell sold live, not a wrong species.
- **The two markets share product ids, so the two fish files can be compared row for row.** Across the whole of
  `lt/08` and `et/08` **90 ids appear in both and 65 agree completely**; of the 25 that do not, 23 agree on the
  archetype and differ only in the letter — 18 of them a better letter on the Lithuanian side, where every letter
  is computed and the Estonian ones were typed by hand. Two archetypes differ, both for good reasons: one pack is
  named cooked in Lithuania and left unqualified in Estonia, and one is named for two different Carangidae in the
  two markets. This is the sharpest evidence yet for the deferred Estonian re-grade, and it is recorded with the
  worked examples in [`rimi/et/CLAUDE.md`](rimi/et/CLAUDE.md).
- **The salt cell is the tell for a panel that is not the product's.** Both Turkish farmed fish declare 1,3 and
  1,6 g of salt on an unprocessed whole fish, where the gutted sibling of one of them declares 0,1 g, and one of
  them prints 0 g of saturates on 12 g of fat. A single-ingredient fresh fish above about 0,5 g of salt has
  borrowed its table from somewhere. Deliberately *not* a seventh check in `extract_det.py`: salt drives no
  grade, and deciding that a product is unprocessed needs the ingredient list rather than the panel.

#### Roe, surimi, preserved seafood and sea cabbage

- **SAFAD has no roe or caviar row.** Searching all 2451 rows in every column for roe, caviar, ikura, sturgeon,
  lumpfish and *Cyclopterus* returns one hit — `Taramasalata, prepacked`, an oil-emulsified spread at 2140 kJ /
  53 g fat / 6,11 g protein, five and nine times a roe jar. The 27 `Fish meat` L3s are all species groups and
  hold no non-muscle tissue at all: **the fish branch has no offal L2 the way the meat branch does.**
  So roe is **blank**, at L1 + `Fish products` depth (the residual fish L2, where a roe row would land).
- **Do not map roe to the species' muscle row**, tempting as the Latin binomial makes it. The line against the
  jerky precedent is tissue: jerky differs from its archetype only in water content, roe is a different organ
  with a different yield per animal, and offal was never mapped onto muscle in the meat files. `Oncorhynchus`
  appears nowhere in the database anyway — the `Salmon and trout` L3 is keyed to *Salmo* spp. — so trout, pink-
  salmon and chum-salmon roe have no species row either.
- **Imitation roe is blank too, in two different ways.** *Võileivamari* is farmed salmon gelled with egg and
  gelatine (386 kJ / 6,1 g fat / 9,4 g protein): the roe-spread concept exists (Taramasalata) but is 5,5× the
  energy, and `Seafood terrine, with or without fish` matches protein within 3% while running 105% over on
  energy. *Meremari* is not a fish product at all — first ingredient seaweed extract, 62 kJ / 0,2 g protein /
  4,2 g salt, a salted alginate gel coloured with carmine. Keep it in `Vegetables … / Sea weeds`, which does
  hold an extract (`Seaweed, agar, raw`, 121 kJ / 0,02 g salt), but blank the row.
- **Surimi has exactly one plain row** — `Fish meat / Cod and whiting (Gadus spp.) / Surimi, on sticks, in
  slices or grated, crab flavour` (492 kJ / 4,33 g fat / 7,54 g protein / 1,77 g salt) — and the whole crab-stick
  family sits on it. Rimi's own stick matches it to the kilojoule. But the family spans 259-553 kJ and 0,5-4,9 g
  fat: the archetype is built on 4-5 g fat sticks, so the near-fat-free Viči and Rimi Smart formulations (0,5-1,8 g
  fat, 10 g protein) land at **C** on fat alone. Surimi noodles and chunks are none of the three presentations the
  name enumerates; they stay on the row anyway, one grade down.
- **Brine dilution is the dominant number problem in preserved seafood, and it is a packaging artefact, not a
  mapping error.** Jars declare "300 g / 140 g" (net / drained) and most producers declare nutrition on the *net*
  weight, so energy and protein come out 40-55% under the archetype; Viči declares on the drained weight instead
  and lands within 4%. Map the species and preparation, grade on the shortfall, and say which weight the pack used.
- **In-oil rows exist only for oily fish** — anchovy, herring, sardine, mackerel and tuna have `in oil, canned,
  drained` rows; no crustacean or mollusc does. Oil-marinated shrimp, mussels and crayfish therefore blow up fat
  by 500-1800% against their species row. Keep the species row: the Estonian names state the seasoning
  (paprika-küüslauk, tšilli-küüslauk, tilliga), never the oil, so an oil-bearing archetype would not be
  name-reachable either.
- **The only mussel row that carries fat is `Mussels, filled (fat, parsley and garlic…)`** at 24,8 g, so every
  oil-packed mussel product takes it — including smoked mussels in 31% rapeseed oil, where it is in fact the
  closest mussel row on every axis (+17% energy, +21% fat, +2% salt) while being a stuffed-shell format the name
  contradicts. Plain mussels in brine take `Mussel, canned, drained`.
- **Crayfish are absent** — no crayfish, *Astacus* or *Procambarus* anywhere. Of the four crustacean archetypes the
  lobster row is the right relative: crayfish and lobsters are both Astacidea, shrimp and prawns are Caridea.
- **Squid has one L3, `Loligo vulgaris`**, and *Dosidicus gigas* takes it. Of its three rows only `Squid, fried or
  pan-fried with fat` carries the oil a canning sauce brings; `Squid, boiled/cooked in water` is the more
  name-reachable preparation but declares 32,5 g protein, twice what a sauced pack holds.
- **merekapsas = kombu = *Laminaria japonica***, which is the archetype's own binomial, so a plain sea-cabbage
  salad takes `Kombu or Japanese kelp (Laminaria japonica), dried or dehydrated` even though the archetype is the
  dried seaweed and the product is rehydrated and dressed with 17% rapeseed oil (fat +591%, protein -91%, energy
  the one surviving axis at -22% because the oil replaces the lost dry matter). `Greek-style marinated mushrooms`
  matches the format but throws away the species the name states.
- **A sea-cabbage salad with a named animal component is a composite dish, not a seaweed** — 40% seaweed plus
  9% smoked salmon, or 34% seaweed plus 30% mayonnaise and 9% squid — and goes to `Composite food … / Prepared
  salads / Prepared mixed egg/meat/fish/vegetable salad`. **SAFAD's prepared salads top out near 8 g fat**
  (Coleslaw 8,1; potato salad piémontaise 8,3), so a mayonnaise-dressed salad at 19 g has no home in the branch.
- Estonian glossary: **mari** / **kalamari** = roe (vikerforellimari = trout roe, gorbuušamari = pink-salmon roe,
  ketamari = chum-salmon roe, merivarblane = lumpfish); **meremari** = seaweed-extract caviar substitute (also a
  brand); **võileivamari** = "sandwich roe", imitation caviar; **surimipulgad / -tükid / -nuudlid** = surimi
  sticks / chunks / noodles; **lumekrabi** = snow crab (a flavour, not an ingredient); **krevetid** and
  **garneel** = shrimp (harilik süvameregarneel = *Pandalus borealis*, absent from both shrimp L3s); **vähisabad**
  = crayfish tails; **kalmaarid** = squid; **rannakarbid** = mussels; **merekapsas** = sea cabbage, kombu;
  **soolvees** = in brine; **omas tindis** = in its own ink.

#### Lithuanian preserved fish: the Estonian rules hold, and two new failure modes

`lt/08_fish` folds `fish-products` (SH-9-1, 253 products) in beside the fresh shelf, so the file is 293 rows,
61 archetypes, 43 A / 106 B / 123 C / 21 blank. It is the Lithuanian twin of rimi.ee's
`processed-fish-products`, and **every ceiling recorded there reproduced without amendment**: the 22,9 g fat
ceiling puts every oil-packed tin at C (sprats at 32-40 g, herring fillets at 30-41 g, hot-smoked salmon belly
at 43 g), roe has no row, `Cod, salted, dried` is the only dried row, and net-versus-drained weight explains the
uniform shortfalls. 68 herring packs sit on `Atlantic herring, marinated, or rollmops` — 18 A, 15 B, 35 C, the
C's being the oil-packed ones — and the Rimi surimi stick again matches its row to the kilojoule.

- Lithuanian glossary: **silkė** = Atlantic herring, **strimelė** = Baltic herring; **šprotai** = sprat;
  **skumbrė** = mackerel; **ančiuviai** = anchovy; **sardinės** = sardine; **menkė** = cod, **menkių kepenėlės**
  = cod liver; **karšis** = freshwater bream; **vilkžuvė** = wolffish; **stauridė** = horse mackerel;
  **selarai** = scad; **merlangai** = whiting; **jūrinė lydeka** = hake; **jūrinė plekšnė** = plaice;
  **stinta** = smelt; **kalmarai** = squid, **galvakojų čiuptuvai** = squid tentacles; **ikrai** = roe;
  **jūros kopūstai** = sea cabbage; **paštetas / užtepėlė / kremas** = pâté / spread / cream; **žuvies
  kukuliai** = fish balls. Processing: **sūdyta** salted, **mažai** / **silpnai sūdyta** lightly salted,
  **marinuota** marinated, **karštai rūkyta** (k. rūk., k. r.) hot-smoked, **šaltai rūkyta** (š. rūk., š. r.,
  s. r.) cold-smoked, **vytinta** air-dried, **kepta** / **apkepti** fried, **savo sultyse** in its own juice,
  **sūryme** in brine, **aliejuje** in oil, **alyvuogių aliejuje** in olive oil, **smulkintas** flaked,
  **nesmulkintas** whole-piece, **papilvės** belly flaps, **nugarkauliai** backbones, **išklotinė** butterflied.
- **A fabricated carbohydrate cell is the one panel error the six checks cannot see.** KAIJA's 250 g
  cold-smoked mackerel declares 55 g of carbohydrate and 6,3 g of protein on a product whose ingredients are
  mackerel, salt and E211 — and 37x30 g of fat plus 17x61,3 g of carbohydrate-and-protein is exactly the 2185 kJ
  it prints, so the energy-versus-macros check passes it. The tell is not arithmetic but the ingredient list: a
  single-ingredient fish cannot hold carbohydrate at all. Its own 1 kg twin declares 613 kJ and 6 g of fat.
  Not worth a seventh check for the same reason as the salt tell — deciding "single-ingredient" needs the
  ingredient text, not the panel — but worth knowing that internal consistency is not evidence of a good panel.
- **A broken panel inherits its sibling's letter.** Three packs here contradict themselves in a way that would
  otherwise decide the grade: 174 kJ beside 727 kcal on a salted salmon fillet whose 300 g twin declares 804,
  the KAIJA mackerel above, and a units-squared 3623 kJ over macros worth 866. Each takes an explicit letter
  equal to the one its properly-declared twin earns, which is the same rule the zeroed-energy salmon fillet on
  the fresh shelf uses.
- **Species resolved here, beyond the Estonian list**: **amerikinis polakas** = saithe (*Pollachius virens*) →
  `Pollack, raw`, its congener — `Alaska pollock, smoked` is the only smoked gadoid row but a different genus,
  and species beats preparation; **šiauriniai žydrieji merlangai** = blue whiting (*Micromesistius poutassou*)
  → `Whiting, raw`; **geltondryžiai selarai** = yellowstripe scad (*Selaroides leptolepis*) → `Bigeye scad, raw`,
  the only scad row and filed in a Bonito L3; **peruvinė stauridė** = *Trachurus murphyi* → the horse-mackerel
  rows, which come in a lean and an oily seasonal pair with no smoked row (a hot-smoked pack at 9 g of fat is
  the oily one, and picking between them by composition is the same call the wild/farmed suffixes take);
  **didysis jūrinis ešerys** = *Sebastes mentella* → `Golden redfish, raw`, its congener; **argentininė jūrinė
  lydeka** = *Merluccius hubbsi* → the Hake L3, braised when hot-smoked and raw when dried.
- **Silver warehou is absent outright.** *Seriolella parosa* (baikštusis slapukas) is a Centrolophidae with no
  relative anywhere in the database — not the species, genus or family. Hot-smoked, so the nearest row is a
  smoked oily schooling fish, and `Atlantic herring, smoked, plain` is closer on all three axes than
  `Mackerel, smoked`, whose salt is a third of the pack's. Class `'x'`, C.
- **The dried-cod row finally gets its own species.** MSDM's dried salted Pacific cod slices (*Gadus
  macrocephalus*) are the one dried snack on any Baltic shelf that `Cod, salted, dried` was built for: 12,5 g of
  salt against its 12,3 and 40,7 g of protein against 47,6, still a C only because drying leaves the energy 32%
  over. Every other dried snack — squid, blue whiting, scad, plaice, hake, bream, trout, salmon jerky — takes
  its own species' raw row and runs +89% to +288% on energy.
- **The same brushtooth lizardfish is the one blanked `Fish meat` row in both markets.** MSDM's "dried smelt"
  (*Saurida undosquamis*) is on the Lithuanian shelf too, with the same contradiction between name and
  ingredient and the same absence of any relative in the database.
- **Two more labels contradict their ingredient lists**, both mapped on the ingredient and flagged as poor eval
  items: LEMBERG's *menkių ikrų kremas* ("cod roe cream") contains no roe — it is rapeseed oil with 27% cod
  **liver** and 14% shrimp, and takes `Taramasalata, prepacked`, the oil-emulsified fish-offal spread its name
  also reaches; and DESĖ's *menkė su daržovėmis* is saithe and Greenland cod, not cod.
- **A sandwich spread is not a canned fish.** Kėdainiai's *Pikantiška lašiša* and *Lašiša sumuštiniams* lead
  with water and rapeseed oil and carry 10,2% cold-smoked salmon and 6,9% cold-smoked trout, at 33 g of fat and
  under 4 g of protein: they take the generic `Rillette, fish`, the shrimp-spread precedent, since taramasalata
  is reserved for roe. Their tuna siblings (43% tuna in an oil-and-corn emulsion) take `Rillettes, tuna` rather
  than a canned-tuna or prepared-salad row, the rillette being the only spread-shaped tuna row.
- **Canned mackerel has no in-oil row** — the canned mackerel rows are brine, mustard, tomato and white wine —
  so an oil tin takes `Mackerel, canned in brine, drained` and the oil is stated in Comments. A japonicus tin at
  884 kJ lands 4% off it; the KAIJA fillets at 1272 kJ are 38% over, which is the oil.
- **Fried breaded fish keeps its species, not its format.** Fried breaded smelt goes to `Pond smelt, raw` and
  not to `European plaice, breaded, fried`, which matches the numbers within 14% but asserts a different
  species — the same call the tuna rule makes for species-neutral rows.

#### Preserved fish: salted, smoked, canned and dried

The `processed-fish-products` category is 177 products and the richest branch of the fish L1 by a wide margin —
56 distinct archetypes across the file. Herring and tuna are genuinely well served; everything else runs into one
of three ceilings.

- **The fish L1's fat ceiling is 22,9 g** (`Mackerel, smoked`). Above that the whole L1 holds only mussels, snails,
  pâté and taramasalata. So **packing oil has nowhere to go**: smoked sprats in oil (32-36 g), salted herring
  fillets in oil (30-37 g), hot-smoked salmon belly (43 g) and salmon in oil with lemon (28 g) all exceed every
  fish row there is. SAFAD does carry `in oil, canned, drained` rows — but only for anchovy, herring, sardine,
  mackerel and tuna, and each is a *drained* figure. Map the species and preparation, grade C, say the oil has no
  home. Do not go looking for a fattier row.
- **Drained versus net weight is the second recurring gap, and it is a packaging artefact.** Jars declare
  "300 g / 140 g"; producers who declare on the net weight come out 40-55% under the archetype while those who
  declare on the drained weight land within a few percent. Viči declares drained, Rimi Smart and Marwi mostly
  do not. Read a uniform shortfall across energy *and* protein as brine, not as a wrong archetype.
- **`Cod, salted, dried` is the only dried-fish row in the database, and it is cod-specific.** Dried-fish snacks
  run 40-66 g protein where the raw rows run 15-20, so every non-cod dried species takes its own raw row and
  carries the drying as a delta — the jerky call from the meat files, on the same reasoning: the archetype differs
  from the product in water, not in tissue. That is also the line that separates this from roe: **liver and roe are
  different organs and get blanked; dried muscle is still muscle.**

**Herring** (`Clupea harengus`, and `Clupea harengus membras` for Baltic räim/silk — both belong in the same L3):

- `Atlantic herring, marinated, or rollmops` is the **only salted-or-pickled herring row** and the whole Nordic
  soolaheeringas family sits on it — plain salted fillets, oil-packed fillets, rollmops, and every sauced jar
  (mustard cream, horseradish, sour cream, mayonnaise, tomato, sweet onion marinade). There is no salted-but-
  unmarinated row and no herring row carries a sauce. Unoiled fillets land inside 15% on energy and fat.
- **Salt is where these packs always part company with the archetype**: a Baltic cure runs 3-5,5 g against the
  archetype's 2,18, and a spiced silgufilee reaches 9 g. That is what keeps otherwise A-grade fits at B.
- `Atlantic herring, fried` is the row for **praetud räim** — Baltic herring floured and fried before canning, an
  Estonian staple. Energy and fat land within 20%; protein halves because half the tin is tomato sauce.
- The three smoked-herring rows (plain / fillet / in oil) all sit at 9,9-11 g fat, and **the entire Herring L3 tops
  out at 13,8 g**, so a 20 g-fat cold-smoked herring exceeds the branch on the fish alone, with no oil added.
- **A sauced fish keeps its species row.** SAFAD does this itself — `Mussels, in Catalan-style sauce or marinade
  (tomatoes), canned, drained` sits in the `Mytilus edulis` L3 — so herring in mayonnaise stays in the Herring L3
  even when the sauce is 65% of the jar. The head noun decides, as with the šašlõkk sausages: `heeringafileetükid`
  keeps the fish row, `salat` goes to `Prepared salads`.

**Sprat** (`Sprattus sprattus`, Estonian kilu; sprotid on the tin):

- **One row, `European sprat, raw`**, and it carries every Estonian sprat product — smoked, fried, in oil, in
  tomato sauce. Smoked-in-oil tins land +45% to +105% on energy and +80% to +206% on fat; tomato-sauce tins come
  back inside 10%. There is no smoked, fried or canned sprat row and no Baltic-sprat subspecies row.
- **Two labels refuse to name the species**: Rannaküla's and Kaija's tins read *suitsutatud KILU või RÄIM*
  (`Sprattus sprattus balticus / Clupea harengus membras`) — sprat *or* Baltic herring, interchangeably. Map on
  the head noun (sprotid → sprat) and flag it, as with `Turistieine` in the meat files.

**Tuna** — the best-served branch in the whole dataset, 12 rows:

- Every pack here but two is **skipjack** (`Katsuwonus pelamis`), whose only row is raw. Use the
  **species-neutral canned rows** and let the pack medium choose: `Tuna, plain, canned, drained` for brine and own
  juice, `Tuna, in sunflower oil, canned, drained` for oil, `Tuna, flaked, in oil, canned, drained` for
  *purustatud* (flaked), `Tuna, in Catalan-style or in tomato sauce, canned` for any sauce. Flaked-in-oil is the
  batch's best match anywhere: energy identical, fat +8%.
- **Prefer a species-neutral row over a species-specific one when the species does not match.** `Yellowfin tuna,
  canned in brine, drained` is arithmetically closer to a brine-packed skipjack than `Tuna, plain, canned, drained`
  is, but asserting the wrong species costs more than 24% on energy. Use the yellowfin rows only for actual
  *Thunnus albacares*.
- The **one exception is olive oil**: the only tuna-in-olive-oil row is albacore-specific, and the oil medium moves
  the footprint more than the tuna species does, so an olive-oil pack trades species for oil. Fat matches almost
  exactly.

**Salmon and trout**:

- **SAFAD has no salt-cured-only row.** The cured rows are the smoked ones, and their salt (2,86 g for salmon,
  3,10 g for trout) matches a Nordic light cure far better than any raw row's 0,1-0,2 g does. So *soolatud*,
  *õrnsoola*, *graavilõhe* (gravlax) and *külmsuitsu* fillets all share one row per species, with the smoking as
  the attribute the name does not support — the salted packs therefore grade a step below the smoked ones on the
  same row. `Salmon carpaccio, w marinade` matches gravlax on energy to 1% but is a raw marinade with 30% less
  protein, so it is not used.
- `Salmon, smoked (packaging fixed)` covers **cold and hot smoking alike**; `Trout, farmed, smoked` does the same
  for vikerforell. Both sit under their Estonian packs on fat by 5-60% — the farming, not the smoking, exactly as
  for the fresh fillets.
- `Salmon, canned, drained` is the canned row, and Norwegian farmed salmon runs **two to three times its 4,5 g of
  fat** while matching its protein to the decimal. Sauced canned salmon (mustard cream, tomato, soy) has no row of
  its own — SAFAD gives tomato-sauce rows to mackerel, sardine and tuna but not to salmon.
- Salmon **carcass cuts have no axis**, as in the meat files: backbone, ribs and belly flaps all take the plain
  smoked row, and the belly at 43 g fat is nearly twice the whole L1's ceiling.

**Hot smoking cooks the fish, so prefer a cooked row where the species has one.** `European hake, braised` fits
hot-smoked hõbeheik to within a percent on energy and exactly on protein, where `European hake, raw` would be 34%
out. Cold smoking is not cooking — cold-smoked Greenland halibut takes `Greenland halibut, raw`, not the steamed row.

**Species resolved in this batch** (the Latin-L3-names-are-not-taxonomy warning keeps earning its place):

- **latikas** = `Abramis brama` → `Freshwater bream, raw`, which sits inside the `Bream (Charax)` L3 among sea
  breams. **särg** (roach, `Rutilus rutilus`) has no row and takes the same one as the nearest Cyprinid.
- **kogre** (`Carassius carassius`) → `Carp, raw, farmed`; **koha** (zander, `Sander lucioperca`) →
  `European perch, raw`, same family; **hõbeheik / merluus** (`Merluccius hubbsi`) → the Hake L3;
  **merilest** (`Pleuronectes platessa`) → `European plaice, raw`, exact; **süvalest** (`Reinhardtius
  hippoglossoides`) → `Greenland halibut, raw`, exact; **angersäga** = wolffish → `Spotted wolffish`, the L3 being
  named `Sea catfish and wolf-fish (Anarhichas)` outright.
- **`Oncorhynchus` appears nowhere in the database by name**, yet the `Salmon and trout (Salmo spp.)` L3 holds
  rainbow trout — so Pacific salmonids do belong in it. Dried pink salmon (`O. gorbuscha`) takes the generic
  `Salmon, raw, farmed` for want of a wild or dried row.
- **Anchovy: the salt decides.** `Anchovy, in salt (semi-preserved)` carries 14,4 g salt and
  `Anchovy, fillets, in oil, semi-preserved, drained` only 2,52, because the oil row is drained and desalted. A
  13 g-salt fillet in olive oil is the salt-cured product whatever the pack medium says.

**Roe spreads are the one place `Taramasalata, prepacked` works.** It is roe *emulsified with oil* — 53 g fat,
6,11 g protein — which is five to nine times wrong against a jar of salted roe (see the roe rules) and within a
third of an oil-based roe pâté tube. Cod-roe spread (*Kalles Kaviar*) is its own species too. Shrimp spreads have
no crustacean-spread row and take the generic `Rillette, fish`, even though taramasalata would be closer on the
numbers, because they contain no roe.

**Cod liver is mapped, unlike roe.** `Tursamaks omas õlis` is 99% `Gadus morhua` liver at 60-65 g fat. The fish
branch holds no liver row — no fish offal at all — but the database carries the organ's own product,
**`Cod liver oil`** in `Animal and vegetable fats and oils / Fish oil`, at 3700 kJ / 100 g fat. A 65 g-fat jar is
two thirds of the way there (-33% energy, -35% fat), the species and organ are exact, and *tursamaks* reaches the
row by name. That is the difference from roe, where nothing anywhere in the 2451 rows derives from roe.

**Labels that contradict their own ingredient list** — three in this batch, all mapped on the ingredient (species
moves the footprint) and all flagged as poor eval items, because the eval only ever sees the name:

- `Grillitud sardiinid omas mahlas` — the name says sardines, the ingredient is `Sprattus`, Baltic sprat.
- `Räimed tomatikastmes suitsutatud` — the name says smoked, the ingredient says *praetud*, fried.
- `Meritint kuivatatud` — the name says sea smelt, the ingredient is `Saurida undosquamis`, brushtooth lizardfish,
  a Synodontidae with **no relative anywhere in the database** — not the species, not the genus, not the order.
  Unlike tilapia, which had a lean white river fish to fall back on, there is no principled nearest species, so
  this is the file's one blanked `Fish meat` row.

Estonian glossary: **heeringas** = Atlantic herring; **räim** / **silk** = Baltic herring; **kilu** = Baltic sprat
(**sprotid** on the tin); **tursk** = cod, **tursamaks** = cod liver; **skumbria** / **makrell** = mackerel;
**anšoovis** = anchovy; **latikas** = bream, **särg** = roach, **kogre** = crucian carp, **koha** = zander,
**heik** / **merluus** = hake, **merilest** = plaice, **süvalest** = Greenland halibut, **angersäga** = wolffish,
**meriahven** = redfish; **soolatud** / **soolaheeringas** = salted, **õrnsoola** / **kergsoola** /
**vähesoolane** = lightly salted, **graavilõhe** = gravlax, **vürts-** = spiced, **marinaadis** = in marinade,
**külmsuitsu** = cold-smoked, **kuumsuitsu** = hot-smoked, **suitsumaitseline** = smoke-*flavoured* (not smoked),
**praetud** = fried, **kuivatatud** / **vinnutatud** = dried / air-dried, **omas mahlas** = in its own juice,
**purustatud** = flaked, **õlita** = without oil, **selgroog** = backbone, **kõhuääred** = belly flaps,
**pasteet** / **määre** = pâté / spread.

#### Frozen fish, fish fingers and frozen seafood (`lt/08`, SH-13-3-7..10)

Fifty-five products added to `lt/08_fish_archetypes.csv` from the frozen cabinet — `frozen-fish` 13,
`fish-fingers` 18, `frozen-seafood` 12, `frozen-shrimps` 12 — bringing the file to 348 rows. No product id is
shared with the fresh shelf (SH-9-16) or the processed one (SH-9-1), or with any of the other 39 files.

- **This is the shelf that needed the frozen axis**, and the rule that decides when it wins is written up under
  *Cross-cutting mapping rules*: take the row whose assertions the pack does not contradict. Shrimp, hake fillet
  and minced saithe keep their frozen rows; the fourteen par-fried breaded packs take `Fish, breaded, fried`.
- **`Fish, breaded, fried` is the fish-finger workhorse and it holds the whole name family**, from ESVA's 50%-fish
  finger to a battered fillet in cheese sauce. `Fish, in sauce, frozen` is the composite alternative for the
  sauced ones, and those packs run 51-67% over it because it carries no batter — the batter is where the energy
  is. The one genuinely unbreaded pack, fillet portions under a 5% herb butter, is the row's proper use and grades
  B. `Fish, croquette, fritter or nuggets, fried` takes the minced patties (*maltinukai*) and matches them to 3%.
- **Two corrections to rows already in the file, both found by this shelf:**
  - `Karštai rūkytas amerikinis polakas` moved from `Pollack, raw` to **`Saithe, cooked`**. Its comment claimed
    "the genus has only a raw row"; the `Cod and whiting` L3 in fact holds `Saithe, raw`, `Saithe, frozen, raw`
    and `Saithe, cooked`, and saithe **is** *Pollachius virens*, the binomial the pack prints. 448 kJ against 423
    replaces 448 against 320. **Search the `food_product` column for the English species name as well as the
    Latin one** — the miss here was looking for *Pollachius* and not for *saithe*.
  - `Šald. nev. blyšk. krevetės (70/80)` moved to **`Shrimp, frozen, raw`**, so the one frozen raw shrimp already
    in the file reads the same axis as the eleven added here. Its *atitirpintos* (thawed) twin keeps the unfrozen
    row. Neither letter changes.
- **Lithuanian glossary this shelf adds**: *aliaskinė rudagalvė menkė* = Alaska pollock (*Gadus chalcogrammus*, so
  a *menkė* by both name and current binomial); *amerikinis polakas* = saithe; *ilgoji jūrinė lydeka* =
  *Merluccius productus*; *strimelė* = Baltic herring; *matjes silkė* = salt-matured herring; *ilgapelekis tunas*
  = albacore; *žuvies piršteliai* = fish fingers; *žuvų maltinukai* = minced fish patties; *kalmarų žiedai* =
  squid rings, *čiuptuveliai* = tentacles; *aštuonkojai* = octopus; *banguotosios pafijos* = *Paratapes* clam;
  *jūros gėrybių rinkinys / kokteilis / mišinys* = seafood assortment.
- **Gaps this shelf adds**: **no octopus row anywhere** (the nearest is the squid L3, which is how the file already
  handles the one octopus cocktail on the processed shelf); **no clam or cockle row**; **no row for a seafood
  mix**, so the four assortments take the assortment-tray rule — map on the leading component, grade C, name the
  components; **no frozen cooked shrimp row**, so *virtos* costs the freezing axis; no fish-and-chips row; no
  fried-surimi row; and **no salted-but-uncooked herring row**, so matjes takes the marinated one, which its
  2 g of salt against 2,18 confirms.
- **`Squid fritter, Roman-style` is calamari alla romana** and takes battered squid rings to within 9%. It is the
  only row in the `Seafood-based meals` L3.
- **Panel faults: 7 of 55 (13%), and not one is an automated flag.** Every kJ/kcal pair agrees, every macro sum
  checks, nothing is a placeholder. All seven are the seventh, deliberately un-automated shape — a cell zeroed
  where the ingredients say it cannot be: 0,0 g saturates in 6,5 g of herring fat, 6,6 g of fried batter, 4,0 g of
  pangasius and 2,0 g of mussel; 0 g salt on five packs whose own ingredient lines name *druska* (nine zeroed
  cells over seven packs, two of which zero both). **Five of the seven are NOWACO**, which imports 13 of the 55 —
  a zeroed cell is a property of the label printer, not the food.
- **Three more that no check can reach**, all found by reading siblings against each other:
  - Two mussel packs from one importer, one in garlic butter and one in white wine, print **one byte-identical
    panel** (322 kJ / 2,5 g fat / 0,6 g saturates / 13 g protein / 0,3 g salt). The RESTERA pattern from the
    frozen-gastronomy shelf, on a second importer.
  - A raw shrimp pack and its *virt.* (cooked) sibling do the same across a processing difference
    (226 kJ / 0,6 / 0,2 / 12 / 1,2 on both), which a raw and a boiled shrimp cannot both produce.
  - The three RIMI own-brand whiteleg packs all declare **12 g of protein on 85-90% shrimp**, where the R SEAFOOD
    packs at 96% declare 16 and 22 g. Six points of shrimp cannot halve the protein: the RIMI panels look to be
    calculated on the glazed weight and the others on the drained. It is why those three are the worst-fitting
    shrimp rows in the file.
- **The marketing-line rule holds again.** `Šald. krevetės TORPEDO RIMI MARE` is 50% shrimp and 50% breadcrumb,
  and `Shrimp fritters` at 1090 kJ / 10 g fat is what the numbers pick — but TORPEDO is a product line in capitals
  beside the brand, not a food word, so the row is the plain frozen shrimp one at +118% and C. A good numeric fit
  cannot confirm a type the name never states; the same call as the five *Speciale* pizzas.

### Plant-based analogues (vegan)

**SAFAD's plant-analogue vocabulary is real but scattered across five L1s, and the same product type appears in
three of them.** Find the branch before arguing about the row:

- `Milk and dairy products / Milk and milk product imitates` — the dairy analogues. Seven L3s: `Almond drink`,
  `Oats drink`, `Rice drink` (**one row each**), `Soya drink` (4), `Soya cheese` (3), `Soya yoghurt` (4),
  `Tofu` (5).
- `Meat and meat products (including edible offal) / Meat imitates` — two L3s: `Meat imitates` (one row, `Seitan`)
  and `Textured soy protein` (10).
- `Composite food (including frozen products) / Beans-based meals / Beans and vegetables meal` — the patties,
  schnitzels, balls and one hot-dog sausage (16 rows).
- `Composite food (including frozen products) / Cereal-based dishes / Grain patties` — wheat and seitan patties,
  nuggets and one sausage (6 rows).
- `Legumes, nuts and oilseeds / Legumes, beans, dried / Soya beans, soaked/boiled/canned` — `Tempeh`.

Consequences that decide rows:

- **`Textured soy protein` is the generic plant-protein L3, not a soya-only one.** Three of its ten rows name no
  crop at all (`Plant-based ham`, `Plant-based pate`, `Veggie burger`). Estonian plant meat is mostly *pea*
  protein and its names say only *taimne* / *taimevalgu*, so the L3 is not contradicted — the silent-name rule,
  not the species rule.
- **Plant sausages split three ways by base, in three different L1s**: tofu → `Plant-based sausage with tofu
  (vegan)` (dairy imitates), wheat or seitan → `Plant-based sausage with wheat or seitan` (Grain patties),
  anything else → `Vegetable Hot-dog sausage` (Beans-based meals), which is the species-neutral one and the right
  home for pea protein since pea is a legume. Its 27,1 g protein is 2-3× any real pack.
- **Several rows in this branch are the same six `H_*` values stored twice, and two of the duplicates cross
  tuples.** `Vegetable Hot-dog sausage` (Beans and vegetables meal) == `Plant-based sausage with wheat or seitan`
  (Grain patties), and `Tofu patty (vegetable steak)` (dairy imitates) == `Plant-based patty or steak from
  soybean, **cheese** and vegetables` (Beans-based meals) — a tofu row carrying a cheese patty's composition.
  Within one L3, `Vegetable ball (without meat)` == `Plant-based ball with wheat and/or soybean, prepacked`.
  So the tuple you pick is a real choice; the nutrition often is not, and a row's composition is not evidence
  about its own name.
- **`Veggie burger` stores the exact composition of `Cheeseburger, double, from fast foods restaurant`** — all six
  values identical, 1210 kJ and 16,7 g fat. That is why it is the wrong row for a real vegan burger, and it is the
  clearest single example of why the mapping must be checked against the stored values and not the row name.
- **SAFAD encodes a vegan / not-vegan axis explicitly**, on four pairs: the soy-and-wheat schnitzel, the
  soy-and-wheat patty, the soy-and-wheat nuggets and the tofu sausage. In a vegan category it always resolves to
  the vegan side, and the name (*vegan*, *taimne*) supplies it. The corollary bites once: there **is** a
  `Schnitzel, soybean, wheat and cheese-based, cordon bleu-style` row, but cheese is the point of it, so a vegan
  cordon bleu takes the plain vegan schnitzel and loses the form.
- **`Veggie burger` is the name-exact row for a burger and the wrong one on composition**, for the reason above:
  Estonian vegan burgers run 655-890 kJ and 2,5-11,6 g fat. Use the `Beans-based meals` patty rows, chosen on
  base: wheat+soya, lentil-soya-and-vegetables (the vegetable-led ones), or wheat/seitan.

**Coconut is the one plant milk filed outside the dairy imitates.** Almond, oat, rice and soya drinks are all
`Milk and milk product imitates`; the coconut drink is `Vegetables and vegetable products / Vegetable products /
Coconut milk (Cocos nucifera)`, next to `Coconut milk or coconut cream` and `Coconut water`. That L3 is also the
right home for a coconut cooking cream (18% fat matches `Coconut milk or coconut cream` to 6%) and for sweetened
condensed coconut cream — which keeps the coconut row rather than `Condensed milk, with sugar, whole`, because
species drives the footprint and the 60 g of added sugar is the lesser loss.

**The plant-dairy branch is soya-complete and drinks-only for every other crop.** `Soya yoghurt` carries plain,
flavoured, with-fruits and a `Soy "cream" preparation`; `Soya drink` carries plain/flavoured × calcium/none; but
oat, almond and coconut have a *drink* row each and nothing else. So:

- A fermented oat, almond or coconut pot has to borrow a row that names soya — **C**, crop lost.
- A whipped oat dessert on hydrogenated coconut oil (11-12 g fat) is off the top of the plant-dessert rows, which
  stop at 2,3 g, so it takes `Soy "cream" preparation` (14,7 g) instead — the only high-fat row in the branch.
- `Soy "cream" preparation` is the database's **only** plant cream, and it is filed under `Soya yoghurt`, not
  under any cream L3. It carries the soya cooking cream (an A), the oat cooking cream (C, crop), the whippable
  coconut cream (C, 26 g fat) and the plant coffee creamer — **no coffee whitener or creamer row exists anywhere**,
  dairy or plant.
- Use the calcium-fortified soya rows **only when the name says calcium**. `Sojajook suhkruvaba Oddlygood` is
  fortified but its name says sugar-free, so it takes the plain unfortified row. Where a crop has no fortified
  variant at all (oat), fortification is simply not an axis and is ignored.

**Vegan cheese is well served, and the axis is format.** `Soya cheese` holds `Plant-based cheese, without soybean,
prepacked, shredded` and `... sliced` — **byte-identical in composition** — plus `Plant-based spread-cheese type,
with soybean`. The coconut-oil-and-starch cheeses that dominate the shelf (Violife, Gourmet Green, Salling Veggie)
match the without-soybean rows to within 15% on energy, fat and saturates *and* on their near-zero protein: the
file's cleanest A grades. Traps:

- The **spread** row is the soya-based one (11,7 g protein, 1,87 g saturates). A coconut-oil spread keeps the
  format and breaks the base — C. A genuine soya spread (`Tofupuder sibulaga`, 51% tofu with onion) matches it to
  within 1% on energy and fat; only the word *cheese* is wrong.
- Cheddar and gouda in these names are **flavourings**, not cheeses. So is "Kreeka valge" — vegan feta, a brined
  29% coconut-oil block, which fits neither format and is the fattiest of them.
- A sunflower-oil pizza topping has a tenth of the archetype's saturates at a similar total fat.

**No plant-based ice cream row exists.** The frozen L3 is literally `Ice cream, milk-based`, and `Sorbet` is the
only dairy-free frozen dessert in the database. The milk-based rows fit these packs *well* — `Ice cream, in box`
is within 3% of an oat tub, `Ice cream on stick, with chocolate coating` within 5% on energy, 10% on fat and 0%
on saturates of Magnum Vegan — but taking one asserts cow milk, and dairy fat is the single largest thing an ice
cream footprint turns on. **Species wins; all 15 go to `Sorbet, in box` or `Sorbet, on stick` at C**, with the
alternative's numbers stated per row so the cost is on the record. There is no sorbet cone row, so the tub row
absorbs a wafer cone too. This is the file's largest deliberate compromise; revisit it if the footprint model
ever prefers the closer composition to the true species.

**Where the base has no row anywhere, a species-neutral L3 is fair game.** The distinction that decides it:

- A glazed **tofu** bar is the vegan kohuke, and kohuke goes to `Cheesecake, refrigerated` — but tofu *has* a row,
  so the base keeps priority and the bar takes `Tofu, plain` at C (energy +136%, 25 g sugars against 0,57).
- A layered biscuit-and-rice-extract dessert has **no** base row, so it takes `Cheesecake, refrigerated`, whose
  L3 `Pastries and cakes` at least names no species.
- Vegan **cakes** likewise: `Pastries and cakes` is species-neutral and a cake's footprint is flour, sugar and
  fat, so the dairy bakery rows are usable. Vegan bakery then runs 61-77% low on **saturates** against them,
  because the butter and cream are rapeseed oil and a plant drink; total fat can go either way (the two cocoa
  sponges are 36-43% under, a peanut-and-chocolate cake 26% over).
- A vegan **salami pizza**: `Pizza, chorizo or salami` is within 2% on energy and 4% on fat but asserts pork, so
  `Pizza, vegetables or pizza 4 seasons` — the only row in the L3 naming neither meat nor a cheese. Every pizza
  row still assumes dairy cheese, and the protein runs about half.
- A **vegan mayonnaise** is unproblematic: the dressing branch has no egg axis, so the standing mayonnaise rule
  applies unchanged (nothing says light → `Mayonnaise (70% fat and more)`, C at 50% oil).
- A 75% plant fat spread sold as *määre* is **margarine** in EU terms → `Vegetable fat (like margarine), 80% fat,
  salted`, matching to 7% on energy and fat; the name not saying margariin is what keeps it off an A.

Reliability in this category follows one rule, because hand-typing 174 letters is not auditable:

- **Forced** — SAFAD offers exactly one row for the crop and form the name states, so there is no mapping choice
  to be wrong about. Grade on **energy alone**: A within 15%, B within 60%, C beyond. Fat is excluded because that
  is precisely where the single row per crop is calibrated to a different recipe — the oat archetype stores 1,1 g
  against 1,5-3,5 g in all 19 Estonian oat drinks (added rapeseed oil), the almond archetype 3,2 g against
  1,0-1,7 g (they are 2-3% almond). The fat gap goes in Comments.
- **Normal** — the row was picked from candidates, so the mechanical grade stands: A if energy and fat are both
  within 15%, B within 30%, else C, with **fat differences under 1,0 g per 100 g treated as immaterial** (a
  percentage on a 2 g base decides nothing, and below 1 g it moves neither a footprint nor a score).
- **Crop or form mismatch** — C by definition.
- Explicit overrides only for a name-supplied axis that has no home anywhere: protein-enriched, Greek-style,
  flavoured tofu, an unresolved sliced/shredded format.

Other things this category taught:

- **No fish imitate exists.** SAFAD has meat imitates and dairy imitates and nothing for the third kind. Vegan
  smoked-salmon slices built from maize starch, konjac, carrageenan and rice protein are the file's one blank,
  kept at `Meat and meat products (including edible offal) / Meat imitates` depth — `Soy-based sliced` has the
  format but 17,6 g protein against the pack's 1.
- **Pea tempeh vs soya tempeh.** One `Tempeh` row, and its L3 is `Soya beans, soaked/boiled/canned`. Pea tempeh
  keeps the right L1 and L2 (pea is a legume, dried) and loses only the crop — C. Soya tempeh is B on the same row.
- **No buckwheat drink and no fermented plant drink.** Fermented raw-buckwheat drinks take the oat row: both are
  whole-grain drinks and grain-versus-legume is the footprint axis, even though the soya rows are closer on
  protein — which is added cashew and bean protein, not the base.
- **A juice-and-oat drink is a nectar, not a plant milk.** 11-22% fruit puree against 6-10% fermented oat, and
  70% over the oat row on sugars, so `Mixed fruits nectar`.
- **Pelmeenid are filled pasta; gyoza are not.** `Fresh pasta, stuffed with vegetables (e.g. ravioli), raw` for
  pelmeenid, `Chinese specialty or dumplings` for gyoza. The three filled-pasta rows are stuffed with cheese,
  meat or vegetables — an extruded pea-protein filling is none of those.
- **`Vegetable Bolognese sauce` exists** and matches a pea-protein bolognese across the board (the file's easiest
  A). One `Curry sauce, prepacked` row, and it is a thin condiment: a coconut-cream curry sauce doubles its energy.
- Small producers publish one nutrition block per range here too — `Taimevalgu haks` and `Pihv taimsest valgust
  Thormi` are identical to the decimal and differ only in salt, one left as mince and one formed into patties.
- **Label-vs-ingredient errors, two:** `Sojavorstid Like Meat` contains no soya at all (pea protein isolate),
  mapped on the ingredient and flagged as a poor eval item; `Kookosjook riisiga Alpro` has the wrong ingredient
  list pasted on the page (almond, no coconut or rice), but its saturates equal its total fat, which is coconut,
  so there the *name* is right and Rimi's list is wrong.
- **One unusable nutrition block:** `Kaerajook barista Tere Deary` declares 2,8 g saturates where the total fat is
  only 3 g, and 12,3 g carbohydrate where every other oat drink carries 6,2-8 g. Graded on the type.
- Not axes anywhere in this branch: barista, organic, gluten-free, lactose-free, eggless, sweetener-instead-of-
  sugar, high-protein, Greek-style, smoke on a vegan cheese.
- Estonian glossary for this category: **kaerajook** = oat drink, **mandlijook** = almond drink, **riisijook** =
  rice drink, **kookosjook** = coconut drink, **sojajook** = soya drink, **tatrajook** = buckwheat drink,
  **toortatar** = raw buckwheat, **köögikreem / köögikaste** = cooking cream, **kohvivalgendaja** = coffee
  whitener, **vahustatav** = whippable, **toidukreem** = culinary cream, **kondenseeritud** = condensed,
  **taimne** = plant(-based), **taimevalgu** = plant-protein, **hernevalk** = pea protein, **põldoa valk** =
  fava-bean protein, **nisuvalk / nisugluteen** = wheat protein / gluten, **haks** = mince, **pihv / kotlet** =
  patty, **nagitsad** = nuggets, **pallid** = balls, **viiner** = frankfurter, **vorst** = sausage,
  **juusuvorst** = cheese sausage, **šašlõkivorst** = shashlik sausage, **rebitud** = pulled,
  **kanatu** = "chicken-less", **suitsu-** = smoked, **määre** = spread, **võileivakate** = sandwich topping,
  **võileivaviilud** = sandwich slices, **riivitud** = grated, **pitsakate** = pizza topping,
  **puder** = mash, **dessert / puding** = dessert / pudding, **jäätis** = ice cream, **pulgajäätis** = ice
  lolly on a stick, **vahvlikoonus** = wafer cone, **kaerapõhine** = oat-based, **magusainega** = with sweetener,
  **suhkruvaba / ilma suhkruta** = sugar-free, **magustamata** = unsweetened, **maitsestamata** = plain,
  **hernetempeh** = pea tempeh, **kollane hernes** = yellow pea, **pelmeenid** = filled dumplings,
  **suupiste** = snack, **kook / tort** = cake / layer cake.

#### Lithuanian plant-based products: the Estonian rulebook reproduced 76 of 78 shared mappings

`plant-based-products/SH-77` is 129 packs, and 78 of them share a product id with `et/11_vegan_archetypes.csv` —
by far the largest cross-market overlap in the tree. Mapping the Lithuanian shelf from the rules above and then
diffing gave **76 of 78 the same archetype (97%)**, which is the strongest evidence yet that these rules are the
Estonian file's reasoning and not its accidents. **Read the two divergences as name findings, not mapping
findings** — both are the Lithuanian listing describing the pack differently:

| id | Lithuanian name says | Estonian name says | consequence |
|---|---|---|---|
| 4003972 | PLANT PROTEIN | calcium and vitamins | `flavoured, with sugar` vs `flavoured, enriched in calcium` |
| 987588 | mango-**flavoured** | Greek-style, mango | `Soy dessert, flavoured` vs `Soy dessert, w fruits` |

The second one is a real distinction Rimi keeps in Lithuanian: `Ferm. soj. produktas su mango **vaisiais**` (with
mango *fruit*) and `Ferment. mango **skonio** sojos produktas` (mango-*flavoured*) sit on the same shelf, so the
with-fruits and flavoured rows are both reachable and the name says which. Both land on B either way.

**`et/11` is "the first vegan file" whose hand-typed letters prompted `grade.py`, so it is a control on archetype
choice and not on the letter.** 72 of the 78 letters agree, and each of the six that do not has a reason: two
juice-and-oat nectars this file puts at B where the row's beverage family is right and its numbers agree to
7-13% (only the unmodelled oat fraction keeps them off A); a tofu bolognese `et/11` typed B at +57% on energy,
where the mechanical rule gives C; a caramel-coffee soya drink `et/11` held back as a protein drink, which only
the Estonian name calls one; and a cheddar slice pack, below.

What this shelf added:

- **A fruit-led plant dessert belongs in the fruit branch, not the dairy imitates.** The standing rule — an
  oat, almond or coconut pot borrows a row that names soya and loses the crop — is right for a *fermented pot
  imitating yoghurt* and wrong for a fruit puree. The three VERY RICE pouches are 43-48% fruit puree over a rice
  puree that is 10% rice, and `Fruit and fruit products / Other fruit products / Fruit compote /
  Fruits dessert, all types` names no species at all, where every soya-dessert row asserts a legume the pack does
  not contain. B at -21% on energy, against a C for the crop. **Test the question by weight**: what the pack
  mostly is decides the branch, even when the name leads with the other ingredient.
- **A pack on the plant-based shelf need not be vegan.** `Kepti sojų šniceliai Gourmet Green` lists **egg
  white**, and SAFAD encodes the vegan axis explicitly on this very schnitzel pair, so it takes
  `Schnitzel, soybean and wheat-based (not vegan)` — which fits it to 4% on energy and 8% on fat where the vegan
  row is 10% and 50% out. Plant-based is a shelf; vegan is a claim; and here the retailer's shelf is the thing
  that is wrong. The Lithuanian name makes no vegan claim, so nothing in it contradicts the not-vegan row.
- **There is no egg imitate anywhere, and this is the second gap of that exact shape.** `Eggs and egg products`
  holds fresh eggs, whites, yolks and three powders; the only imitate branches in the database are
  `Meat imitates` and `Milk and milk product imitates`. The organic plant egg substitute — water, 12% soya,
  sunflower oil, soy protein isolate — is this file's second blank, kept at `Milk and dairy products /
  Milk and milk product imitates` depth. `Tofu, plain` is the nearest composition (7,8 g of fat to its 8,5) and
  asserts a coagulated curd where the pack is a pourable emulsion. Same shape as the vegan smoked salmon, which
  this shelf carries too and blanks identically.
- **Almond is the second base to break the one soya spread row.** The rule was written for coconut oil; the two
  Tartare tubs are a **64% almond base with 16% almond puree**, and an oat spreadable cream is a third. All three
  keep the format and break the base — C. The saturates tell the bases apart at a glance: the almond tubs match
  the soya row at 1,7 g against 1,87 (the fat is rapeseed), where every coconut spread here is ten times over it.
- **The format axis on the two without-soybean cheese rows can only be settled by the name, because the rows are
  byte-identical.** A pack whose name states slices takes the sliced row and can reach A; a topping, a brined
  cube, a blue-cheese block and a name that says only "vegan plant product" all leave the axis open and are
  held to B. That last one, `Vegan. augal. produktas Gourmet Green`, is where `et/11` has an A: the Estonian
  name says cheddar-flavoured and the Lithuanian one says nothing at all, so the archetype is right for the pack
  and unreachable from the name — mapped correctly and flagged a poor eval item, which is the standing handling.
- **Three of the fourteen vegan ice creams carry nothing in the Lithuanian name that says dairy-free** — the
  sandwich multipack, a green-smoothie ice and a Ben & Jerry's tub. Only the shelf places them. The letters do
  not change (every plant ice cream is C on `Sorbet` regardless), but they are the file's weakest eval items and
  are marked as such.
- **The empty leaves matter.** Eight of SH-77's sixteen leaf codes hold nothing, including all three
  fats-and-mayonnaise leaves, so the vegan margarine and vegan mayonnaise that `et/11` carries have **no
  Lithuanian shelf at all** — the two mayonnaise rows and the margarine row in that file are Estonian-only.
- `vegan-ice-cream/SH-77-22` is a **child** of this tree whose fourteen packs all carry `SH-13-1-1`, the dairy
  ice-cream shelf, as their gtm primary category. The listing's own codes therefore do not sum to the parent, and
  the sweep is what shows the shelf is complete.
- Lithuanian glossary for this category: **gėrimas** = drink, **avižų** = oat, **migdolų** = almond,
  **ryžių** = rice, **kokosų** = coconut, **sojos / sojų** = soya, **grikių** = buckwheat, **žalių grikių** =
  raw green buckwheat, **anakardžių** = cashew, **avinžirnių** = chickpea, **žirnių** = pea, **kviečių** = wheat,
  **daržovių** = vegetable, **ispaninis šalavijas** = chia (*Salvia hispanica*, which the Lithuanian name calls
  "Spanish sage"), **bolivinė balanda** = quinoa, **nesaldintas** = unsweetened, **be cukraus / be prid. cukr.**
  = sugar-free / no added sugar, **natūralus** = plain, **skonio (sk.)** = flavoured, **su ... vaisiais** = with
  (real) fruit, **desertas** = dessert, **pudingas** = pudding, **tyrelė** = puree, **fermentuotas (ferm.)** =
  fermented, **užtepėlė** = spread, **tepamasis kremas** = spreadable cream, **kremas kepimui** = cooking cream,
  **pagrindas maisto ruošimui** = cooking base, **riebalų mišinys plakimui** = whipping fat blend, **sūrio sk.**
  = cheese-flavoured, **riekelės (riek.)** = slices, **užpilas** = topping, **graikiško stiliaus kubeliai
  sūryme** = Greek-style cubes in brine, **čederio skonio** = cheddar-flavoured, **mėlynojo pelėsio sk.** =
  blue-mould-flavoured, **kumpio sk.** = ham-flavoured, **dešra / dešrelės** = sausage / small sausages,
  **šniceliai** = schnitzels, **kepsneliai** = cutlets, **maltinukai** = patties, **piršteliai** = fingers,
  **užkandis** = snack, **kepti** = baked or fried, **rūkyta (rūk.)** = smoked, **griežinėliai** = slices,
  **ledai / valgomieji ledai** = ice cream, **sumuštinio formos** = sandwich-shaped, **batonėlis (baton.)** =
  bar, **glazūruotas (glaz., gl.)** = glazed, **spanguolė (spang.)** = cranberry, **pyragas** = cake,
  **sausainių įdaras** = cookie dough, **kiaušinių pakaitalas** = egg substitute, **prieskoniai** = spices,
  **augalinis / aug. kilmės** = plant(-based), **veganiškas (vegan.)** = vegan, **ekologiškas (ekol., eko.)** =
  organic, **be laktozės** = lactose-free, **pieno baltymų (pien. b.)** = milk protein, **UAT** = UHT,
  **pasterizuotas (paster.)** = pasteurised, **maisto gaminimui** = for cooking.

### Bread, pastry and confectionery

At 527 products this is the largest category so far, and the first where the archetype space is
*richer* than the shelf: `Grains and grain-based products` holds 374 rows, 190 of them in
`Fine bakery wares` (59 biscuits, 131 pastries and cakes) and 50 in `Bread and rolls`. The work
is therefore assignment, not compromise — 407 of the 527 rows land within 15% of their archetype
on energy and 513 within 30%.

**Because of the volume, the mapping is driven by a reviewable table rather than 527 individual
calls**: `bp_rules.py` is an ordered list of (Estonian name pattern → archetype key, class),
first match wins, and `bp_over.py` carries the per-product exceptions. Every product must match a
rule or an override, and the builder asserts no archetype key goes unused. The table is the
documentation; a keyword that fires on the wrong product is a visible, fixable line rather than a
buried judgement. Two passes of review were needed and both mattered: sorting all 527 by their
energy gap caught the numeric misfits, and grouping them *by archetype* caught the errors of
kind that the numbers hid — a wheat "Kiviahju päts" sitting among the rye breads, roasted onion
(*röstsibula*) read as toast bread (*röst*), a cinnamon-roll cheesecake filed as a cinnamon bun.

#### Bread: one rye row for the whole Estonian shelf

**There is exactly one rye bread row in the database** — `Rye bread, and wheat`
(1100 kJ / 1 g fat / 2,4 g sugars / 8,27 g protein), in an L3 named `Mixed wheat and rye bread
and rolls`. Searching all 2451 rows in every column for rye, pumpernickel, sourdough, levain and
*seigle* returns that row, three rye flours and the raw grain, and nothing else. So **every**
rukkileib, peenleib, must leib, koorikleib, põrandaleib, vormileib, rukkipala and rukkitasku —
59 rows here — takes it. It is a good fit for the plain ones (energy within a few percent) and it
is the only option regardless.

- **No seeded bread row exists.** The only seed-bearing row in the whole L2 is `Swedish toast,
  with linseeds`, a dry 1700 kJ toast in the *wheat* L3 — wrong grain and wrong form. So 17
  seeded rye breads at 4,0-10,8 g fat sit on a 1 g archetype: the single largest gap in the file,
  and the reason most of its C grades exist.
- **The grain the name states wins over the format.** A rye *toast* bread (*rukkiröst*, *saib*)
  keeps the rye row rather than `Sandwich loaf`, even though the sandwich rows fit its added oil
  far better — the same species-first rule as everywhere else, since the L3s in this L2 *are* the
  grain axis. But where the name names **no** grain and seeds come first in the ingredient list
  (*seemneleib*, *uudseleib*, seeds at 19-33%), the head noun is the seed and the row is
  `Sandwich loaf, multigrain`.
- Estonian bread vocabulary, in the order it decides rows: **leib** = (rye) bread, **sai** =
  white wheat bread, **sepik** = wheat bread with bran or wholemeal (`Bran grain bread` or
  `Sandwich loaf, wholemeal`, never the rye row), **röst / röstsai** = toast bread
  (`Sandwich loaf`), **kukkel / pehmik / muhkel** = soft roll, **kringel / stritsel** = sweet
  plaited yeast pastry, **pirukas** = small filled baked pastry, **päts** = loaf,
  **korvike** = tartlet shell, **tasku** = filled pocket, **pala** = piece.
- `Wheat bread and rolls` is 30 rows and heavily French, which is fine: *sai* → `Sandwich loaf`,
  *ciabatta* and Bocatta → `Panini bread` (within 2%), *prantsuse sai* → the baguette rows,
  *lavašš* and *tortilja* → the wheat tortilla wrap, Georgian *puri* → `Pita bread`, burger and
  hot-dog buns → `Rolls for hamburger/hotdog (buns), prepacked` (several clean A grades).
  Baguettes are the one place the French rows bite: they store 1,3-1,4 g fat and the Estonian
  versions carry rapeseed oil, up to 10,7 g in a tomato-basil baguette.

#### Crispbread, croutons and puffed cakes are four different rows

The `Unleavened bread, crisp bread and rusk` and `Other bread` L3s cover this shelf precisely,
and telling them apart is most of the work:

- **One crispbread row**, `Crispbread, extruded and grilled` (3,6 g fat, 7,8 g sugars), for all
  29 *näkileib*. The plain Wasa and Finn Crisp ryes are leaner than it on both axes; the
  seed-and-flavour variants run to 31,2 g fat, nine times it, and there is nothing else.
- ***leivakrõpsud*, *küüslauguleivad* and *bruschetta* are croutons, not crisps.**
  `Crouton with garlic, herbs or onions, prepacked` (2030 kJ / 22,9 g fat) matches Estonian fried
  rye bread chips to within 1% on energy (their fat runs up to a fifth higher) — one of the best-fitting families in the file. Plain
  *krutoonid* take `Croutons, plain, prepacked`.
- ***galetid* and *vahvlid* are puffed grain cakes**, and `Other bread` holds one row for each
  base: `Puffed cereals textured bread` for maize, buckwheat, lentil and multigrain,
  `Puffed rice textured bread, wholemeal` for rice. Both fit to within a few percent.
  **A rice *crisp* is a different product** — 74-80% rice fried in 16-18% oil, 10-26 g fat —
  and there is **no rice-based snack row anywhere**, so those take
  `Corn chips or tortilla chips`, the generic grain chip.
- **A glazed rice cake is confectionery, not bread.** 53% yoghurt or chocolate glaze puts it on
  `Biscuit (cookie), chocolate covering` or `... snack with dairy or vanilla filling`, both
  within a few percent; the puffed-rice row would be 25% out on energy and 570% on fat.
- ***rõngik* / *rõngassai* is a bagel** — boiled then baked — and `Bagel` fits to within 5%.
- ***kuivikud* are rusks**, and the `Rusk` row family is nine rows deep.
- ***grissinid* and *leivapulgad* → `Grissini or bread stick`**, matching to within 1%.

#### The cake shelf splits by richness, and the name says which

Estonian cakes and tortes span 577-2460 kJ, so no single sponge row carries them. The
discriminator is in the name: **a cake paired with a dairy cream or a named fruit** (*hapukoore*,
*jogurti*, *vahukoore*, *kohupiima* + a berry) **is the light kind at 900-1200 kJ** →
`Sponge cake w fruit mousse`; **the plain, glazed, coffee and nut cakes run 1400-1950 kJ** →
`Soft cake, plain, sponge cake type`. Getting this backwards was the single biggest source of
error in the first pass — nine tortes sat 31-41% under a row that was simply too rich.

- ***kook* vs *tort* also separates the quark cakes**: a baked *kohupiimakook* takes
  `Cheesecake, refrigerated`, a layered *kohupiimatort* is much lighter and takes the fruit-mousse
  sponge.
- **Estonian bakery names that are archetypes in their own right**: *marmorkeeks* →
  `Marble cake` (0% on energy), *keeks* → `Pound cake, prepacked`, *brownie* → `Brownie`,
  *ekleer* → `Eclair`, *besee* and *pavlova* → `Meringue` (1648 kJ / 0 g fat against
  1680 / 0,52 — the file's cleanest A grades), *napoleon* → `Mille-feuille pastry`,
  *sõõrik* → `Doughnut filled with jam`, *muffin* and *red velvet* → `Muffin`,
  *tosca* → `Soft cake with nuts`, *amarettiinid* → `Macaroon`, *vahvel* → `Soft waffle`.
- **Napoleon is the one name-exact row that does not fit.** Estonian Napoleon runs 1023-2033 kJ
  and 14-33 g fat against the mille-feuille row's 1230 / 11,3; the chocolate ones are 64% over.
  Kept there anyway, because the name names the pastry.
- ***meekook* is not gingerbread.** `Gingerbread` stores 1,15 g fat — a lean spiced loaf. An
  Estonian honey cake is a layered cream cake at 9-10 g, and Marlenka's honey balls are
  12-20 g; both go to cream-cake rows and `Gingerbread` ends up unused by this category.
- ***sefiir* is a marshmallow layer, not a meringue pastry.** `Pastry biscuit with meringue`
  (2250 kJ / 31,5 g fat) describes a dry meringue biscuit; a *sefiiritort* is 1101-1462 kJ, so it
  belongs with the light cream tortes. Reserve the meringue-pastry row for an actual
  meringue-and-cream cake (*beseekook*, *moorapea*).
- **Cream-filled pastries need the choux rows, not the bun rows.** *vahukreemikringel* and
  *sotsnik* at 25 g fat go to `Pastry cream puff`; mini eclairs with whipped cream go to
  `Chou pastry filled with whipped cream` (the plain `Eclair` row is half their fat);
  *kreemisai* goes to `Chou pastry, sugar coated`.
- **Layered cinnamon pastries are palmiers.** *kaneelitorud* and *kaneelikõrvakesed* at
  2300-2400 kJ are puff-pastry biscuits, not soft cinnamon buns — `Biscuit ( puff pastry),
  palmier, from bakery`. This is why the biscuit rules must come **before** the flavour words
  they contain: *kaneel*, *sidruni*, *martsipani* and *mee* all appear in both buns and biscuits.

#### Savoury pastry: *pirukas* has no L3 of its own

SAFAD has no L3 for a small filled savoury pastry, so each one takes the nearest filled-pastry
row from whichever L1 carries that filling — and the fits are surprisingly good:

- **meat → `Meat in puff pastry`** (`Composite food / Meat-based meals / Pressed meat snacks`),
  which matches *lihapirukas* to within 6% on energy, 3% on fat and 1% on protein — the best
  single fit in the file, and worth remembering because nothing about the row's name suggests it.
- cheese → `Cheese in puff pastry` (`Snack food / Cheese puffs`), or
  `Small cheese chou-pastry puff` for a *juusturull* at 25 g fat
- ham and cheese → `Croissant filled with ham and cheese`; bacon and egg → `Quiche Lorraine`
- vegetable → `Vegetables tart`; chicken and spinach → `Pastilla, filled with chicken (pie)`,
  the only chicken-pie row, which is a thin filo pie where these are enriched dough
- cheese biscuits and sticks → `Salty snacks, crackers, garnished or filled with cheese`;
  a *soolapulk* at 41,6 g fat is a butter puff pastry, not a pretzel stick
  (`Salty snacks, mini pretzels or sticks` stores 3,1 g)
- baked tartlet shells (*korvike*) → `Pastry, short crust, baked`

#### Reliability: energy decides the type, fat decides A from B

The rule from `11_vegan` carries over, with one refinement this category forced. Grading a
fat-only miss as C put 189 of 527 rows at C, which misreads what happened: across all 527 the
median energy gap is **-2%** and the median fat gap **+5%**, with 286 rows over the archetype and
235 under — the mapping is *centred*, and a fat gap on a correct type is exactly the "one axis is
off" that **B** is defined as. So for a row picked from candidates:

- **A** — energy within 15% and fat within 15% (or under 1,0 g absolute)
- **B** — energy within 30% and fat within a factor of two
- **C** — energy off by more than 30%, or fat off by more than a factor of two: the row is the
  wrong richness or the wrong kind of product

That gives 196 A / 275 B / 56 C, and every one of the 56 C grades traces to a named gap — seeded
bread, seeded crispbread, Estonian Napoleon, the oiled baguettes — rather than to doubt about
which row was meant.

#### Retailer data errors, five

- `Must vormileib Eesti Pagar 280g` — 2333 kJ where its own 47,6 g carbohydrate and 1 g fat
  compute to about a thousand; the 600 g sibling of the same recipe declares 1117.
- `Vaarika-jogurtikook Reval Kondiiter` — 22,9 g saturates where total fat is 7,7 g.
- `Röstsai mitmevilja Rimi Smart` — 0,9 g protein and 0 g salt in a bread.
- `Röstsepik täistera Tosta` — 0 g protein and 0 g salt.
- `Pikk sai juustuga Rimi pagaritelt` — **the name contradicts the pack**: sold as a long loaf
  with cheese, the ingredient list is a sweet yeast plait with a 23% marzipan filling and no
  cheese, which its 37,5 g sugars confirm. Mapped on the ingredient, flagged as a poor eval item.

#### Lithuanian bread: the shelf names bread by COLOUR, and a colour is not a grain

`bread-products/SH-3` is 285 products, the largest Lithuanian category, and its four real
children sum exactly to it: bread 128, bread substitutes 82, buns and doughnuts 48,
fresh-baked 27. A fifth code, **`SH-3-4`, is a foreign node rather than a child** — its
canonical URL is under `-rimi-confectionery-and-prepared-dishes`, and none of its 87 cakes
and pastries is listed under bread-products. The thirteen packs here that carry an
`SH-3-4-11` or `SH-3-4-13` gtm code (breadcrumbs, wafer sheets, pastry cases) are all
shelved in bread substitutes; those codes are their primary home, not this shelf.

**Estonian separates the grains by name and Lithuanian does not.** Estonian gives *leib*
(rye), *sai* (white wheat) and *sepik* (bran), each a different L3. Lithuanian gives
*juoda* (black), *tamsi* (dark), *šviesi* (light) and *balta* (white) — and the ingredient
lists show that **all four are rye-and-wheat, rye first**: `Šviesi raikyta duona Rimi`,
`SOSTINĖS šviesi duona su raugu`, `RUPI šviesi duona`, `MOČIUTĖS šviesi ruginė duona`,
`TIKRA šviesi plikyta duona` and `Balta duona su sėklomis Jorė` all lead with RUGINIAI
miltai. So **`šviesi` is not `sai`**: it is the pale end of the rye range, and 63 loaves —
juoda, tamsi, šviesi, balta, ruginė and rupi alike — take `Rye bread, and wheat`, whose L3
is literally `Mixed wheat and rye bread and rolls`. The genuinely wheat breads on this
shelf are named by **form** instead: *batonas*, *sumuštinių duona*, *skrudinimo duona*,
*forminė duona*, *ciabatta*, *focaccia*.

**The corollary, and it bites three times: a colour is not a grain *claim*, so where the
ingredient list has no rye at all the colour cannot carry the row.** `Šviesi bemielė
NAMINĖ duona` is wheat flour, linseed and sesame with no rye; `Forminė duona` and
`Kaimiška MARTELĖS duona` are wheat and water. The governing rule tells you to follow the
label where the pack is *silent*, and on the grain question a colour word says nothing at
all — so there is no label claim to follow and the pack decides. Those three go to the
multigrain sandwich, the plain sandwich and `Country-style bread, home-made` respectively.

Words on this shelf that mean more than one food, each resolved by the pack:

- ***traškučiai* splits three ways.** The word means "crisps" and covers (a) genuine
  crispbread — dry rye or wheat flour, 1,3-2,9 g of fat — `Crispbread, extruded and
  grilled`; (b) **extruded oat-flour chips fried in flavoured rapeseed oil at 9-14 g** →
  `Corn chips or tortilla chips`, the generic grain chip, by the same reasoning that sends
  rice crisps there; (c) fried *bread* chips and bruschetta at 14-30 g → the crouton rows.
  `et/12` puts Rimi's oat chips on the crispbread row at A; on the Lithuanian panels that
  row is **289% out on fat**.
- ***džiūvėsėliai* is two different foods and a flavour word separates them.** Plain
  (`Džiūvėsėliai Rimi`, `Tradiciniai džiūvėsėliai City Taste`) are **breadcrumbs for
  coating** at 1,3-1,9 g of fat; with a flavour word (*su vanile*, *su razinomis*,
  *PIENIŠKI*, *Arbatiniai*) they are sweet **rusks** at 8,2-9,4 g.
- ***plikyta* is two techniques.** On bread it is the scalded-rye method, which changes
  nothing SAFAD models. On a biscuit (`Plikyti sausainiai`) it is **choux** — water, flour,
  egg and margarine — so the chou-pastry rows.
- ***duonelės* is the crispbread word here**, not a roll: Wasa's `Kvietinės duonelės su
  sezamo sėklomis` is sesame crispbread. It is also the roll word in `porcijinės duonelės`
  and `sumuštinių duonelės`. Only the rest of the name tells you which.
- ***sausučiai* are sweet dry rusks**, not crispbread: wheat flour, sugar and palm fat at
  6,3-9,6 g.
- ***riestainiai* are bagels** — boiled then baked — as *rõngik* is in `et/12`.
- **A *batonas* is the Baltic white wheat tin loaf** → `Sandwich loaf`; all nine lead with
  wheat flour and carry a little milk powder.

Gaps and clusters this shelf establishes:

- **No breadcrumb row exists anywhere.** The four coating packs take `Rusk` — dried bread
  is what they are made of, and grinding is not a footprint axis — held to B for the
  5,43 g of fat a rusk carries and a crumb does not.
- **No buckwheat, spelt or garlic-bread row.** `Grikių duona` is wheat flour first with
  buckwheat flakes added, so it is honestly a multigrain wheat loaf; a spelt loaf loses the
  species to the same row (SAFAD has spelt *grain* and spelt *flour* and no spelt bread);
  and a French loaf with a garlic-butter filling keeps the baguette row while the butter
  has no home at all.
- **The lean-Italian-bread gap is this file's largest C cluster**, and it is the Estonian
  baguette finding again: `Panini bread` stores 1,1 g of fat and every Lithuanian ciabatta
  and focaccia carries olive oil, 3,4 to 12,1 g. Five of the six are C on that alone.
- **Seeds beat the grain only when the name leads with them.** `Sėklų duona` and
  `Saulėgrąžų duona` list seeds first at 23-29% → `Sandwich loaf, multigrain`. A *rye*
  bread that merely carries seeds keeps the rye row and pays for it in fat, against an
  archetype that stores 1 g.

**Seven products are catalogue stubs** — no gtm category, no category path in their URL,
and no nutrition panel at all — and all seven are bakery-counter filled buns. They are
graded on the type with an explicit letter, because class `'n'` returns C when there is no
energy figure, and **a row chosen from a name must not be condemned by data that does not
exist**; that is the same principle the `'c'` and `'s'` classes already encode.

**`et/12`'s reliability column is pre-`grade.py` too.** 22 of the 29 shared product ids
take the same archetype (76%), but only 16 of 29 take the same letter — and all seven
letter divergences on an *agreed* archetype are `et/12` grading A where the fat gap is
2,0-5,2 g on archetypes storing 3,6-10,5 g. Its own stated rule is "A — energy within 15%
and fat within 15% (or under 1,0 g absolute)", which those rows fail, so the letters were
typed rather than computed. Of the seven archetype divergences, six are the Lithuanian name
saying something different (see [`rimi/et/CLAUDE.md`](rimi/et/CLAUDE.md)) and one was mine.

### Functional food: a diet-claim shelf on a food-keyed taxonomy (`lt/15`, SH-2-20 + SH-2-21)

294 products over five children of `groceries/functional-food` — `gluten-free` 65, `egg-free` 1, `superfood` 120,
`no-sugar-added-products` 43 and `fitness-nutrition` 65. The last carries the bare sibling code **SH-2-21** in its
gtm attribute while living under the functional-food path; the five sets are pairwise disjoint and their union is
exactly the 294 ids of the unfiltered parent listing.

**Rimi sorted this shelf on the claim printed on the pack; SAFAD is keyed on the food.** That is the whole
category in one sentence, and it makes this the ice-cream shelf's problem rather than the frozen-vegetable
shelf's: the axis the retailer chose is one the taxonomy carries seven times in 2451 rows (see the cross-cutting
diet-claim rule). Everywhere else the claim is invisible and each pack has to be re-read as the food it is —
a bread, a pasta, a biscuit, a bar, a flour, a chocolate. The result is **60 A / 156 B / 67 C / 11 blank** — 20% A,
against 56% on the frozen produce shelf — and almost none of the shortfall is doubt about which food the
pack is.

**Two Lithuanian words two letters apart decide two different rows, and the shelf itself gets them wrong.**

- ***trapučiai*** is a puffed grain cake: `Puffed rice textured bread, wholemeal` for rice, `Puffed cereals
  textured bread` for maize, buckwheat, lentil and multigrain. Both store about 3 g of fat.
- ***traškučiai*** is a crisp — the same grain fried or sprayed in oil. There is no rice-snack row anywhere, so
  the family takes `Corn chips or tortilla chips`, the generic grain chip, exactly as the Estonian file does.
- ***krekeriai*** is a cracker and ***užkandis*** is a neutral "snack" that states no format at all.

Twelve plain *trapučiai* land on the puffed-cake rows, ten of them inside 6% on energy and seven at A. But
**six RIMI packs say *trapučiai* and carry 10,0-15,9 g of fat**, and the Estonian listing calls four of those
six *krõpsud*, crisps, and puts them on the chip row. **The ground truth here follows the Lithuanian name**,
because the Lithuanian name is the only thing the eval gives the model; all six stay on the puffed-cake rows,
the four mini packs matching their energy to within 2,4% and the two 25 g packs to 16%, and grade C on
fat.
say *užkandis*, state nothing, and take the chip row on their own 25-26 g of fat at A.

**The bars are the largest single family in the tree with no archetype at all.** 45 rows of protein bar, protein
powder, gel, isotonic powder and electrolyte tablet, and SAFAD has none of them (see the gaps). The bars are
graded `s` on a `Cereal bars` row — a substitution, so B while the numbers agree and C beyond, never A, because
a whey-and-casein bar has no cereal in it. Within that L3 the row still has to be the one the pack does not
contradict:

- chocolate-coated (39 of 44) → `Chocolate cereal bar`, 1890 kJ / 14,9 g fat, which the family straddles at
  -22% to +6%;
- white- or yoghurt-glazed with no chocolate at all (5) → `Breakfast biscuit (cookie), reduced sugar`, the one
  `Cereal bars` row that names no flavouring ingredient and whose reduced sugar is true of them;
- the FIZI and TREATFULS bars, whose Lithuanian names are `Batonėlis <ENGLISH LINE>` and state no ingredient
  whatever, are capped at B by hand for that reason.

**The whey powders are the one place where a wrong-looking row is the right one.** `Milk, powder, skimmed, non
rehydrated` stores 1530 kJ and 0,7 g of fat; ICONFIT's whey concentrates declare 1657-1674 and 7,0-7,1. Energy
agrees to within 10%, the dairy family is right, and the grade never sees that the row holds 53,1 g of sugars
against their 6 and 35,3 g of protein against their 77. ISOSTAR's vanilla recovery powder actually *is* skimmed
milk powder with glucose syrup — its first ingredient is the row — and grades B.

**SAFAD stores a powder as the drink it makes, except when it does not.** Milk powder is the dry powder;
`Soy drink, plain, prepacked` (177 kJ), `Soy "cream" preparation` (628 kJ) and all four
`Meal replacement low calorie …` rows are reconstituted. This shelf sells three such powders at 1680-2172 kJ, so
they land +243% to +1127% and grade C. The gap is the taxonomy's and the mapping is still the right one.

**A gluten-free flour blend names no grain, so it takes the milling row of its leading ingredient, capped at B.**
BALVITEN MIX FLOUR and SCHAR MIX IT lead on maize starch and fit `Maize/corn flour` to within 4%; RIMI FREEFROM's
universal mix leads on rice flour. A dry *baking* mix is a different problem — SAFAD holds no mix, only the baked
good — so the chocolate muffin mix sits on `Muffin, with blueberry or chocolate` with 1,3 g of fat against 19,9,
which is the egg and oil the box tells you to add, and the seeded bread mix on
`Bread, home-made, with flour for home-made bread preparation` at +58%.

Rows worth knowing, all of them judgement calls:

- **Gluten-free bread and pasta are the best-served families on the shelf** — 8 of 11 breads and 9 of 10 pastas
  grade A, because for once the name states the claim and SAFAD holds it. The legume pastas (lentil, chickpea,
  pea) take the gluten-free row too: they name no claim, but a lentil pasta has no gluten by construction and
  `Pasta, gluten free` is the L3 built for exactly that.
- **A rice cake that is 55% chocolate is confectionery** — both RIMI glazed packs leave `Other bread` for
  `Biscuit (cookie), with chocolate, prepacked` at -1,7% and -5,8%, the same call the Estonian file makes.
- **Fruit gums made only of juice and puree take `Fruit jelly`, not `Jelly candy`.** The four RIMI gums are 96-98%
  fruit concentrate and the VERI BERI strips are a fruit leather; `Fruit jelly` sits in the fruit L1 where the
  footprint belongs and matches within 4%, where the confectionery row would put a fruit product in
  `Sugar and confectionary`. A gum made of maltitol goes the other way and takes `Jelly candy`.
- **Keto is not an axis anywhere.** Keto bread (19 g fat), keto bagel (38 g), keto pizza base (35 g) and the two
  seed-only cracker packs (48-51 g) all take the wheat-or-flour row their name reaches and grade C on fat.
- ***riestainis*** is the ring, and the Estonian *rõngik* precedent makes it a `Bagel`.
- ***šiaudeliai***, salted straws, are bread sticks: `Grissini or bread stick` at -6,7% where the mini-pretzel row
  is 3,13 g of fat against the pack's 7,6.
- **JERRY HONEY RINGS shows the capitals rule's edge.** The line is English, so by the `BONDUELLE SPRING VAPEUR`
  reading it reaches nothing; but the ingredient list names 3% honey independently, and the honey row matches on
  energy to 0,4%. Where a foreign line word points at the same row the ingredient list does, nothing turns on the
  word — and the row still grades C, on 3,9 g of fat over a 1,41 g base.

**Panel faults: 18 of 294 (6%), and the automatic check got 11 of its 25 flags wrong and missed
four.**
carbohydrate, which is the one assumption a no-sugar and keto shelf breaks (see the cross-cutting rule). The real
faults: six of the ten FIZI bars print a kcal cell equal to kJ × 0,4 (535 kcal beside 1338 kJ) — the kJ side is
sound, so the mapping is unaffected; four MISSO bars declare 19-44% more energy than their own macros can
produce; ADONIS's vanilla keto powder declares 5,4 g of protein where its chocolate sibling declares 52,0;
MYPROTEIN BR. declares 39 g of salt; ME2U's protein wafer declares 2,9 g of protein on a pack whose ingredient
list opens with a 29% protein blend; ME2U's MULTILAYER COOKIES declares 9,7 g of saturates in 10 g of fat where
its vanilla sibling declares 10 in 19; the ISOSTAR isotonic tablets print 359 kJ beside 2 kcal with the saturates
cell repeating the carbohydrate one; and the sorghum groats' and KORNI protein cubes' kcal cells disagree with
their own kJ. Also noted, not a fault: `MCT C8 aliejus` declares 100 g of saturates in 100 g of fat, which is
what caprylic acid is.

### World cuisine: sixty sauces on fourteen condiment rows (`lt/16`, SH-2-15)

Eight children, all disjoint, union identical to the parent's 214 ids: japanese-cuisine SH-2-15-93 (26),
chinese-cuisine -94 (7), food-from-other-countries -95 (10), coconut-milk -96 (9), pasta -97 (21),
mexican-cuisine -98 (80), soy-sauces -99 (13), thai-cuisine -100 (48). **59 A / 75 B / 77 C / 3 blank**, 45
archetypes, 388 claims verified. Not one of the 214 ids appears in any other file in the tree.

**The shape of the problem.** Two thirds of this shelf is condiment, and SAFAD's `Condiment` L2 is 14 rows while
`Savoury sauces` is nine French-keyed L3s. The shelf sells about sixty different sauces. Where the branch names
the type it names it once, which is why the `'d'` tiering in the cross-cutting rules had to be settled here; where
it does not name the type at all — teriyaki, oyster sauce, wok sauce, salsa, chutney, sriracha — there is one
nearest row and the letter is C by definition. The grain half of the shelf is the opposite story: tortillas,
tortilla chips, rice noodles, egg noodles, instant noodles and sushi rice all have rows written for them and land
inside a few percent.

- **Salsa is not a tomato sauce, and the reason is the archetype's oil.** Nine taco and salsa sauces take
  `Tomato sauce, w vegetables, prepacked`; eight match it on sugars, protein and salt and miss it by 39-53% on
  energy. Every
  prepacked tomato sauce in SAFAD carries 2,7-4,2 g of olive or sunflower oil and a Mexican salsa carries none,
  so the gap is single, structural and the archetype's. The one that reaches the row is RIMI's TROPICAL SALSA,
  and only because 23% mango, papaya and pineapple put the sugars back.
- **`Asian noodles, plain, cooked, unsalted` carries the dehydrated row's numbers.** It holds 1570 kJ / 3,0 g fat
  / 11,9 g protein / 0,99 g salt — cell for cell identical to `Asian noodles, flavoured, dehydrated`. The row
  SAFAD is missing is the plain *dehydrated* one, and the row that bears its figures is labelled "cooked". So
  dried udon, soba and ramen land on it within 8%, and the one genuinely cooked pack on the shelf (SAMLIP's fresh
  vacuum udon) is 61% under a row whose own name says it should fit. Graded B by hand: the mapping is not in
  doubt, the row is.
- **The `Garam masala` L3 holds no garam masala** — its single row is a prepacked Indian *sauce*. A garam masala
  or tikka masala **powder** therefore cannot take the L3 that bears its name and goes to `Curry, powder` in the
  next L3 along, which is a different named blend and so cannot reach A. The jarred cooking sauces do take the
  Indian sauce row, and all five are C: it is a thin tandoori-style base at 1,7 g of fat where a tikka masala,
  korma or butter chicken is built on cream at 10-17 g.
- **`Tabasco sauce` is an L3 whose only row is harissa.** The shelf sells actual TABASCO — vinegar, 19% red
  pepper, salt — which lands 79% under a thick North African pepper paste. Sriracha goes the same way at +43% to
  +53%. The L3 name is the most precise thing in the branch and the row inside it is a different food.
- **Sweet chilli takes `Sweet and sour sauce, prepacked`, capped at B; a pack that says *saldžiarūgštis* takes it
  at A.** Seven sweet chilli sauces are within 22% of the row on energy but SAFAD does not name them, so they are
  substitutions; six packs say sweet-and-sour in the name and are `'d'`.
- **No seaweed snack.** SAFAD has one laver row (`Laver (Porphyra sp.), dried or dehydrated` — *Porphyra* is
  nori, so the species is exact) and the plain dried sheets land on it at +16% (B) and +41% (C) - the row is a
  wetter sheet than either. The eight oil-roasted snack packs carry 25-53 g of fat against its 1,77 and are all
  C. *Jūros dumbliai* on its own reaches the whole 16-row sea-weed branch across five genera; it is the nori
  sheet that pins it to *Porphyra*.
- ***Džiūvėsėliai* is both breadcrumb and rusk, and SAFAD has only the rusk.** Panko is crustless wheat bread
  baked, dried and flaked, `Rusk` is wheat bread baked twice and broken up, and the Lithuanian word covers both.
  Energy matches to 0,2%; the fat is the gap, because a rusk is enriched and panko is not.
- **Mango chutney is built like a jam and is not one.** Sugar first, 46% mango, salt and vinegar — the structure
  is a jam almost exactly (941 and 1100 kJ against `Jam, apricot`'s 1050) and the species is wrong, which is the
  Tilsit-on-Gouda shape and therefore C. `Mango, pulp, raw` was rejected: it would count 100 g of mango where the
  jar holds 46 and would drop the sugar entirely.
- **Coconut milk has one row at 19,2 g of fat and the market has three bands.** The 17-24% pressings land within
  20%; the 8% "light" ones and the 40%-extract cooking milks are 59-62% under it and C. HENG GUAN's declares
  3,5 g of fat in an 85% coconut product and 1110 kJ where its own macros imply 512, so nothing in that panel is
  usable.
- **Two squeeze-sauce calls worth knowing.** SANTA MARIA's drizzle range is rapeseed oil, water and egg yolk at
  43-44 g of fat, so the banded mayonnaise rule puts it in `Mayonnaise, 25 - 50% oil` at B — except the garlic
  one, which is `Aioli sauce (garlic and olive oil mayonnaise), prepacked`, a garlic mayonnaise by definition, and
  reaches A. And a "guacamole" with 1,4% avocado does **not** get a free pass on the guacamole row: `Guacamole,
  prepacked` is an avocado purée at 17,2 g of fat, both Baltic dips are 5-7 g, and both are C.
- **Fried onions have no row at all.** `Onion, dried` carries 0,46 g of fat; AJI's crisp fried onions carry 44,
  76% onion fried in palm oil. The species is right and the frying has nowhere to go.
- **Rimi lists two different SALLING rice noodles under one display name.** They differ in recipe (one is flour
  and salt, the other adds water and tapioca starch) and both take `Rice noodle, dried`, which is what the
  same-name check requires. `verify_nums.py` keys on the display name, so only the second of such a pair can be
  checked automatically — worth remembering whenever a shelf repeats a name.
- **Panel faults: 13 certain of 214 (6%) plus one probable, and 7 of the automatic check's 18 flags are wrong.**
  Certain: KIKKOMAN's reduced-salt 150 ml bottle declares 450 kJ where the 975 ml bottle of the same recipe
  declares 377; AJI's sushi soy sauce declares 1247 kJ where its macros imply 272; HENG GUAN's coconut cream
  (above); RIMI's dark soy sauce declares 47,5 g of salt per 100 g; FUDO's egg noodles declare 15 g of fat beside
  0,0 g of saturates, where the shelf's three other egg noodles declare 1,8-3,5 g — graded A on the name, with
  the fat cell disbelieved; AJI's 15 g seaweed snack declares fat, protein and salt adding to 100,7 g; three
  SANTA MARIA sauces repeat the kJ figure in the kcal cell; KIKKOMAN's pickling sauce prints 939 kJ beside
  93 kcal; DIAMOND's dried mushrooms declare 779 kJ on 1,2 g of carbohydrate; and two packs declare more
  saturates than total fat. Probable: KANOKWAN's tom yum paste declares 92% of its fat as saturated on a list
  that names palm oil. The false flags are the four coconut milks (coconut fat really is 92-96% saturated) and
  the three plain KIKKOMAN shoyus (ethanol and organic acids, see the cross-cutting rule).

### Cereals, granola and bars: the one shelf the branch was written for (`lt/17`, SH-2-5)

146 products in three children — muesli-bars SH-2-18-113 (44), breakfast-cereals -114 (58), granola -115 (44),
pairwise disjoint and identical to the parent's id set. **19 archetypes, 56 A / 73 B / 17 C, 261 claims verified.**
The URL code and the gtm code disagree here: the listing is `/cereals-and-bars/c/SH-2-5` and every product in it
carries a `category` of `SH-2-18-11x`, so the children had to be resolved by redirect rather than read off the
parent's own code. (A wrong slug on a valid code 301s to the right one; an invalid code 404s. That is how every
shelf code under `groceries` was mapped without a nav link existing in the HTML.)

**The branch was built for a shelf like this one.** `Breakfast cereals` is 49 rows in five L3s — cereal flakes
(13, three of them porridge oats), mixed breakfast cereals (16), muesli (8), popped cereals (2), cereal bars (10,
four of them breakfast biscuits) — and nearly every pack here has a row for its type. The letters therefore
measure recipe drift rather than guesswork, which is why the file is 38% A with only 12% C.

- **The type comes from the name, and only then the numbers.** Corn flakes, sugar-iced corn flakes, puffed wheat
  with honey, chocolate not filled, filled, bran, muesli, bar. Within a type the not-fortified twin is fixed by
  rule (see the cross-cutting section) and a type that is unique once that clause is set aside is `'f'` — graded
  on energy alone. Nine rows are `'f'` and every one of them is within 4,4% on energy.
- **`Muesli, crunchy, …` IS granola.** The word "granola" appears nowhere in 2451 rows; crunchy muesli is the same
  food: 41 of the 44 granola packs sit on its two rows (chocolate, fruit), 37 of them within 9% on energy. The
  *untoasted* mixes — a raisin muesli, an organic flake-and-seed mix — take the `Muesli, flakes (Bircher-style)`
  rows instead, and the Lithuanian name says which: *skrudinti* or *traški* is toasted, plain *dribsniai su
  vaisiais* is not.
- **The corn-flake pair is decided by salt, not sugar.** `corn flakes, plain (not fortified)` stores 1,82 g of salt
  and its fortified twin 1,01; Rimi's own-brand plain flakes declare 2 g. The sugars are useless as a
  discriminator, being 4,12 against 7,59 with the packs at 5,9 to 8,8 in between.
- **The `Breakfast cereals, diet, …` block is the Fitness/Special-K type, not a synonym for plain.** NESTLE FITNESS
  and RIMI GOODLIFE take it and land within 0,8%; a children's honey ring does not, however well its numbers fit,
  because "diet" is a claim the pack does not make.
- **Bars route by the flavour in the name**: chocolate or a chocolate/cocoa coating → `Chocolate cereal bar`
  (21 packs, 11 of them A); fruit → `Cereal bar w fruit`; nuts → `Cereal bar with almonds or hazelnuts`. The nut
  row is the only one in the branch, so a **peanut** bar takes it as `'s'` — peanut is a legume SAFAD files under
  `Legumes, nuts and oilseeds` and no bar row names it. Three of the five `'s'` rows land C, two of them on a fat
  gap past 100% that would fail under `'n'` as well and one only because `'s'` refuses a fat-only B — the
  asymmetry recorded under Reliability grades.
- **`Milky cereal breakfast bar` goes unused.** Its 3,2 g of fat is a quarter of what the Nestlé breakfast bars
  carry, so they take the chocolate bar row at -11 to -14% on energy instead.
- **One display name, two different bars.** Rimi sells a fruited and a chocolate FITNESS bar as
  `Dribsnių batonėlis NESTLE FITNESS, 23,5 g`. The eval is given only the name, so both take the fruit row — the
  nearer of the two bar rows on energy for each — and only one of the two may carry checkable figures, because
  `verify_nums.py` keys pack data by display name.
- **Not one panel fault in 146 packs, and the single flag the check raises is a false positive.** MUSLI's
  blackcurrant bar declares 14% less energy than its own macros imply because its glaze is sweetened with maltitol,
  and the four-macro check prices every gram of carbohydrate at 17 kJ. Two other polyol packs (a glycerol/sorbitol
  corn-flake bar, a maltitol cocoa cereal) balance, because their labels declare the polyols outside the
  carbohydrate figure. No arithmetic fault, no impossible panel, no kJ/kcal mismatch anywhere on the shelf.

### Canned products: one shelf, eight L1 categories (`lt/18`, SH-2-10)

`groceries/canned-products/c/SH-2-10` is 369 products in 17 children and it is not a food
family at all — it is a shelf of jars and tins. A strawberry jam, a tin of chickpeas, a
Kalamata olive, a peanut butter, a maple syrup, a canned pilaf and a jar of pickled
cucumbers are all "canned products" to Rimi, and they land in **eight different SAFAD L1
categories**: fruit, vegetables, legumes/nuts, grains, sugar/confectionary, composite food,
fats and oils, and fish. 76 archetypes over 369 rows, 129 A / 99 B / 133 C / 8 blank. So
there is no single rule for the shelf; what follows is per family.

**Completeness came from the retailer's own arithmetic, not from refetching the children.**
The listing page publishes a count beside each of the 17 `categoryNamePath` facets; they sum
to exactly 369 and each one equals the number of products the scrape assigns to that child.
That is cheaper than the disjoint-children proof used on `lt/17` and at least as strong,
because it is the retailer counting its own shelf. Two children carry gtm codes from other
trees — `canned-olives` is `SH-19` and `syrups` is `SH-22-51` — so the codes were not used
for anything.

- **A jam's name states the fruit and never the sugar level.** See the cross-cutting rule:
  `Jam, reduced sugar` names no fruit, no jar on the shelf claims the reduction, so every
  one takes its own fruit's row and the modern low-sugar band grades C — 45 jams and
  marmalades at 20 A / 10 B / 15 C, the shelf running continuously from 452 to 1164 kJ.
  20 of the 45 go to `Jam, raspberry`, the branch's berry row and the only home for
  blackcurrant, lingonberry, bilberry, cranberry, a berry mix and a black mulberry;
  `Jam, apricot` is the catch-all for the non-berries (plum — the same genus — plus fig,
  mango and an apple-pear).
- **A fruit purée splits on how many fruits the name states.** One fruit named → that
  fruit's own row, which for apple means `Apple compote` (the Lithuanian *tyrė* and the
  French *compote de pommes* are the same food, and the row is the sweetened version, so an
  unsweetened organic purée is -52%). Several fruits named → `Fruits puree, without sugar
  added`, the only fruit-blind purée row; five of the six 100%-fruit pouches land on it
  inside 9% and the densest, a plum-and-apple one, at +31%.
- **One olive row for 48 olives**, and the grading is by hand because the row's single
  assertion is a stuffing: the 21 genuinely stuffed packs are `'n'` and 12 of them land inside
  10% (the row's own "etc" covers a lemon, prawn, lobster, tuna, salmon, cheese, ham, almond or
  garlic filling), the 19 plain green, black and seasoned ones are explicit **B** (a black olive
  is the blue-grape call — a colour word that is not the row's), and the 8 Kalamata, Kalamon and
  dark-Greek ones are explicit **C**, a named variety SAFAD does not carry; five of those eight
  are +40 to +47% on energy with the fat to match, and the other three range down to -29%.
- **Pickled is not an axis, and this shelf is where that call gets its volume.** 24 rows on
  `Cucumber, pulp and peel, raw`, 7 on `Beetroot, cooked`, 3 on `Tomato, raw`, 3 on
  `Tomato, cherry, raw`, 2 each on `Onion, raw` and `Sorrel, raw`, one on `Garlic, fresh` and
  one on `White cabbage, raw` — 43 explicit C's, which is where a third of the file's C count
  comes from. The added sugar is what makes it hurt: a honey-cured cucumber is +283% on the
  raw row and a salted one is -39%, on the same archetype. **Where the vegetable does have a
  canned row and the pack is genuinely that**, the canned row is used and the mapping is an
  ordinary `'n'`: sweetcorn, garden peas, sweet peppers, artichokes and tinned tomatoes.
- **Tinned tomatoes take `Tomato pulp, canned`, including the ones whose name says peeled.**
  `Tomato, peeled, canned, drained` names the peeling but asserts *drained*, and every tin on
  the shelf is packed in juice — which is exactly the +36% it would cost. The pulp row asserts
  nothing about peeling, and an axis a row is silent about is an omission and not a
  contradiction (the frozen-fish rule). It carries 13 rows, from -3 to +36%.
- **Canned pulses are the best-served family in the file** — `Chick pea, canned, drained`,
  `Red kidney bean, canned, drained`, `Haricot bean, canned, drained` and `Lentil, seasoned,
  canned, drained` take 23 rows at 15 A / 8 B and not one C. Black and brown beans go to the kidney row:
  they are *Phaseolus vulgaris*, the L3's own binomial, so only the colour differs.
  `Haricot beans with tomato sauce, canned` then carries every sauced bean — baked beans,
  chilli beans, curry beans and two Mexican bean stews — at 6 A / 2 B / 3 C.
- **A nut paste keeps its own nut.** Thirteen 100%-peanut creams are A on `Peanut butter or
  peanut paste` (2532-2751 kJ against 2670); the almond, cashew and cashew-almond pastes sit
  on whole-nut rows at B, the format being the only difference and the composition confirming
  that grinding a nut changes nothing. Nine of the ten Nutella-style spreads are A on `Chocolate spread
  with hazelnuts`, the tenth a maltitol-sweetened one at -16%. Two are neither: a 40%-pistachio confectionery spread is C (60% of it is
  sugar, fat and milk powder) and an unnamed hazelnut-cashew cream is C on the mixed-nuts row,
  which asserts dried fruit it does not carry.
- **SAFAD's ready soups are French potages and a Lithuanian canned soup is a one-pot meal.**
  The rows sit at 90-270 kJ; the KKF and RIMI EXPRESS block declares 376-1036, which is +47 to
  +289%, so 23 of the 29 soups grade C on energy alone with the type entirely right. The organic
  ACTIVUS range is the control — thin soups at 153-368 kJ, five of the eleven inside 28% of their
  rows. State the reason in Comments and do not reach for a stew row: the name's head noun is
  *sriuba*, and `Beef stew with carrots` would have fitted the goulash soup at +14%.
- **A ready grain meal takes the grain's own cooked row where SAFAD has one.** `Wheat bulgur,
  cooked, unsalted` carries a whole BONDUELLE bulgur-and-vegetable salad at -1,4%, and
  `Quinoa, boiled/cooked in water, unsalted` two quinoa meals at -19% and +13%. Buckwheat has
  no cooked row, so its two meals go to the composite branch instead.
- **Panel quality: a hand scan flags 41 of the 369 packs and 35 of them are sound.** A canned
  pulse or a pickled vegetable carries 3-7 g of dietary fibre, which EU labelling prices at
  8 kJ/g and a four-macro check prices at nothing, so the declared energy runs 15-35% above the
  macros for essentially every tin of beans, peas and capers on the shelf — 34 of the 41 flags,
  and the documented Atwater trap in its fibre form rather than the polyol one. A honey-fungus
  jar reads -62% for the mirror reason: mushroom mannitol is carbohydrate on the label but
  10 kJ/g. Sorrel is a partial third case, its oxalic acid counted as carbohydrate and not
  metabolised. **Six faults are real**: four kJ-versus-kcal mismatches (a jalapeño jar — whose
  1,4 g of carbohydrate implies 41 kJ beside a declared 158 and a printed 66 kcal, so no two
  figures in it agree — a marinated cherry tomato, an EL FARO lemon olive and an ACTIVUS curry
  stew), one impossible panel (I LOVE ECO dark agave syrup, 104 g of carbohydrate in 100 g),
  and one jam that understates itself — SPILVA's lingonberry declares 481 kJ where its own 39 g
  of carbohydrate implies 668, on an ingredient list of nothing but fruit, sugar, water and
  pectin. Three more packs declare a carbohydrate figure with 0 g of sugars, which two jams and
  a honey cannot be.

### Oil and vinegar: a named-plant taxonomy, and the panels are declared by volume (`lt/19`, SH-2-1)

`groceries/oil-and-vinegar/c/SH-2-1` is 107 products in 8 children — olive oil 37, vinegar and
lemon juice concentrate 21, balsamico vinegar 11, other oils 11, sunflower oil 11, coconut oil 6,
rapeseed oil 5, grapeseed oil 5 — and the facet counts sum to exactly 107 and match the scrape
child by child. One wrinkle worth knowing for the next shelf: `Saulegrazu aliejus RIMI, 1l` has
**no child segment in its URL** and carries the shelf's own gtm code `SH-2-1`, so the sunflower
slug counts 10 where the facet counts 11. A product can sit directly on a parent node.

15 archetypes over 107 rows, **77 A / 10 B / 15 C / 5 blank**, across three L1 categories (fats
and oils 75, condiments 28, juices 3) plus the one malt concentrate. At 72% it has the highest A
rate of any file with more than 50 rows, and that is real rather than lax: these are
single-ingredient commodities named by their plant, and SAFAD has a row per plant.

**The oil branch is a named-plant taxonomy with no grade axis.** `Vegetable oil` holds one L3 per
plant — sunflower, rapeseed, grape seed, corn, sesame, linseed, peanut, palm, soybean, coconut,
olive, cottonseed, walnut — each with a single product row, so a pack whose name states the plant
has nothing to choose: `'f'`, and every one lands inside 10% on energy. The single exception is
coconut, which carries `Virgin coconut oil` under `Vegetable oil` and `Coconut oil (coprah)` under
`Vegetable fat` — the only refining axis anywhere in the branch — and the Lithuanian names use it
(*rafinuotas* / *nerafinuotas*), so those six are a real choice and graded `'n'`. The one pack
whose name states no grade takes B: either coconut row is reachable from "Kokosu aliejus", and the
ingredient line settles it for the product but not for a reader of the name.

**Olive has no grade axis and the EU recognises five grades**, so all 37 olive products sit on
`Olive oil, extra virgin` and the letter carries the distance — see the gaps register for the
ladder. What makes this call cost nothing mechanically and everything by hand is that the panels
are identical across the three grades: three of the four pomace bottles declare the same 3404 kJ
and 92 g of fat as the extra virgin bottles beside them. Five of the 28 A rows have names that
state no grade at all (GRANDE ULIVETO, BONO ITALIAN SELECTION, GRANFRUTTATO and two GRECALE
bottles), one puts a flavour claim where a grade would go (MILD TASTE), and one contradicts itself
in this row's favour — 817775 is named *grynas*, pure, and its ingredient line says *ypac grynas*,
extra virgin. Two of the four pomace bottles contradict themselves the other way, declaring 97%
refined olive oil and 3% extra virgin, which is the blend grade above pomace; they are graded on
the name, because the name is what the eval sees, and the disagreement is recorded.

**Flavoured oil is not a grade.** Three SELECTION BY RIMI bottles are 97-98% extra virgin with
herbs, lemon or basil, the row is silent about flavouring, and their panels are identical to the
plain bottles — so silence is omission (the frozen-fish rule) and they keep A. The same reading
gives the two spiced DETOX cider vinegars their A. A balsamic *kremas* or *glajus* is the opposite
case and the contrast is the useful part: there the addition is sugar and starch until the energy
is 2,5 to 3,2 times the vinegar's, which is a different product rather than a flavoured one.

**The vinegars split by substrate, not by strength.** `Condiment` names three — `Vinegar, cider`
under `Vinegar, apple`, `Vinegar` and `Vinegar, balsamic` under `Vinegar, wine` — and condiments
are an established `'d'` family, so the 15 packs naming one of those three take A outright. The
balsamics need it: a single row has to cover must proportions from 18,5 to 22 g of sugar (435,
423 and 502 kJ against 391), which is the axis SAFAD does not carry rather than a bad mapping. Where
the substrate is something else the `'s'` / `'x'` tiering in the gaps register applies.

**The lime is the one place species loses to form.** *Zaliuju citrinu sultys* is 99,97% lime juice;
SAFAD carries limes as `Lime, pulp, raw` and the only lime-naming juice row is `Lemon or lime base,
for beverage, without sugar (to be diluted)` at 79,5 kJ, a dilutable base this is not. So it takes
`Lemon juice, pure juice` at 117 kJ against 124 and grades **B** for the species — the opposite
direction from the milk-species-wins rule, and marked as such.

**Panel quality: 7 automatic flags on 87 panels, 6 of them sound.** All six coconut oils flagged
SUSPECT SFA at 91-94% saturates are correct — coconut is the one oil that is nearly all saturated,
and SAFAD's own coconut rows say 85,4 and 86,5. The check's one true catch is I LOVE ECO's coconut
oil, which declares 100 g of fat with 0,5 g of carbohydrate and 0,5 g of protein in 100 g — 101 g
in 100 — and 3766 kJ that follows from none of it. Two further faults it cannot see were found by
hand, both mixed-basis panels, which is the weakness of a checker that only tests internal
arithmetic against a basis it has to assume. 20 of the 107 packs publish no nutrition table at
all, and every one of them is in the vinegar block: 20 of its 28 rows.

### Pasta: the retailer sorts by shape and SAFAD does not sort by shape at all (`lt/20`, SH-2-11)

88 products, 5 archetypes, **78 A / 7 B / 3 C**, 94 claims verified. The facet counts agree child by child —
shaped 61, Spaghetti & Linguini 12, full grain 7, egg noodles 5, cannelloni and lasagna 3 — and every product
carries a child segment in its URL, so none of SH-2-1's sitting-on-the-parent behaviour appears here.

**The two taxonomies are orthogonal.** `Pasta (Raw)` sorts by flour (wheat, wholemeal, gluten-free, rice, mixed),
eggs or no eggs, and raw or cooked. Rimi sorts by die, and three of its five children are shape buckets. So
**70 of the 88 rows land on `Dried pasta, raw`** — 80% of the file, the second-heaviest concentration in the tree
after the 69 of 80 Cretan rusks in `kritis/07`. That is a property of the shelf and not a shortcut: the 70 run
1346-1808 kJ with a median of 1511 against the row's 1540. Read the file's **88,6% A rate — the highest of any
file over 50 rows** — with the concentration in mind: it will flatter any aggregate that averages over files.

**The class is `'f'` throughout, as on the oil shelf, and for the same reason**: SAFAD holds exactly one row per
flour-and-egg combination, so once the combination is known there is nothing to choose. Setting aside the three
mac-and-cheese kits and one misprinted kJ figure, every pack is within 12,6% of its row on energy.

- **Egg pasta: the name declares it, the percentage decides the letter.** `Dried egg pasta, raw` is a pasta
  all'uovo recipe — 4,72 g of fat and 14 g of protein imply roughly a fifth egg by weight, the EU standard. Five
  packs say so and measure so (RIMI PLANET at 19,4 g of egg per 100 g on three dies, PRINCIP at 28%, DALLA COSTA
  at 19,36%): **A**. Two GINTARINIAI packs say *su kiaušiniu* on the front and carry **1% egg powder**, a
  colourant dose; the row is still the one their name names, but their fat is 1,5 g against its 4,72, so **B**.
  "With egg" on Baltic pasta is not the Italian category word. Conversely `Makaronai BARILLA TAGLIATELLE` is
  shelved under egg noodles with no egg in it at all, and takes the plain row at A — the shelf's child is wrong
  and the name claims nothing.
- **The homonym rule** (see the cross-cutting bullet) was established here: GNOCCHI, ORZO, NOODLES, VERMICELLI,
  TAGLIATELLE, FETTUCCINE, LASAGNE, CANNELLONI and MAC & CHEESE all name other foods in the database, and the
  ingredient list settles each without costing a letter.
- **A dried-vegetable colourant is an omission.** The tricolor and SELECTION BY RIMI dies carry dehydrated
  tomato, spinach, black carrot, beetroot, rosemary or turmeric at 0,2-2% each, and the row is silent about
  them: **A**, the flavoured-oil reading. Note that hand-typed `kritis/14` gave a vegetable fusilli **B** for
  exactly this; the kritis files pre-date `grade.py` and are on the unverified list, so reconcile them there
  rather than here.
- **Panel quality: 2 faults in 88**, both energy misprints caught by their own
  kcal twin. LUBELLA *auselės* Nr. 40 declares 1808 kJ / 356 kcal, and 356 kcal is 1490 kJ (its macros imply
  1486) — it takes an explicit **A** with the arithmetic in Comments, since under `'f'` the misprint alone would
  have cost the letter. Spagečiai PASAKA declares 1346 where the identical macros on its GINTARINIAI twin read
  1494 and imply 1520. No mass-over-100 faults, and — unlike `lt/19` — no basis problem: pasta is sold by weight,
  so the per-100-ml panel does not arise.

### Grains: the one shelf where the retailer and SAFAD agree, and two rows that lie (`lt/21`, SH-2-6)

185 products across nine children, and the facet counts agree with the URL slugs child by child: rice 49, instant
porridges 39, flakes 33, buckwheat 16, leguminous vegetables 14, other groats and meal mixtures 13,
semolina/bulgur/couscous 10, wheat and barley groats 8, bran 3. Every product carries a child segment.

**SH-2-19 and SH-2-17 are children of this shelf, not siblings — do not build them again.** Both codes resolve
(`/groceries/grains/rice/c/SH-2-19`, `/groceries/grains/leguminous-vegetables/c/SH-2-17`) and return exactly their
facet counts, 49 and 14. They were on the unbuilt-shelf list by mistake; `lt/21` subsumes them. Related: the GTM
`cat` attribute on the cards is not evidence of membership — 42 of these products carry codes in the SH-2-5
(cereals-and-bars) space, but the intersection of this shelf's 185 URLs with that shelf's 146 is **empty**, so
`lt/17` and `lt/21` do not overlap.

**This is the shelf where the two taxonomies agree.** Grain species is the first axis on both sides and SAFAD
carries a row for nearly every species Rimi stocks, so 99 of the 185 rows take a `Grains for human consumption`
row and most name the same grain the pack does. The A rate is **67%** (124 A / 43 B / 18 C) — lower than the pasta
shelf's 88,6% not because the mapping is harder but because a third of the file is porridge, which SAFAD cannot
hold at all. Strip the 39 porridges and the remaining 146 run 123 A / 18 B / 5 C, 84%. Two L1 categories: grains
169, legumes/nuts/oilseeds 16.

**Species wins over form, and form wins over variety.** Brown basmati takes `Rice, brown, raw` rather than
`Basmati rice, raw` — the bran layer is the larger compositional difference. Basmati *sella* is parboiled basmati
and takes whichever axis the name leads with. Jasmine and sushi rice have no rows of their own and take
`Rice, raw`; colour has no rows at all, so tricolour quinoa and tricolour pearl couscous take the plain row.

**The barley split is name-carried and is the one thing worth a self-check.** `Miežinės kruopos` (hulled groats)
takes `Barley, whole, raw` at 1410 kJ and `Perlinės kruopos` / `Perliukai` (pearled) takes `Pearled barley, raw`
at 1460; the map module asserts the pearl word is present for one and absent for the other.

**Pearl couscous is pasta and it does not matter.** `Perlinis kuskusas` (ptitim) is extruded from wheat *flour* —
the ASIF and YAMNA ingredient lists say `KVIEČIŲ miltai` and nothing else — where real couscous is steamed durum
*semolina*. It takes the couscous row its name reaches; `Couscous …, raw` is 1550 kJ and `Dried pasta, raw` 1540,
ten kJ apart, so nothing turns on it. Recorded only so a miss here can be told from a real one.

**A name can declare an ingredient the pack has 0,15% of.** `Grik. dribsnių košė su vištiena ACTIVUS` says "with
chicken"; the list is 95% buckwheat flakes and chicken meat appears below the salt and the sugar. It takes the
buckwheat row. The mirror of `lt/20`'s BARILLA TAGLIATELLE, shelved as an egg noodle with no egg in it — the name
is not always evidence about the food, and the percentage settles it in both directions.

**The dry kit has now hit the wall on three shelves.** `lt/20`'s mac-and-cheese bags, and here two
`Greitas … patiekalas` sachets at 84% instant rice. Where the non-grain matter is small the grain row is the
better answer (these take `Rice, raw`, 1490 and 1527 against 1480) and the salt — 2,0 g against 0,01 — is what the
archetype cannot carry; where it is a third of the pack, the prepared-dish row at C is right instead.

**Panel quality: 5 faults in 185 (2,7%).** `Ryžiai BASMATI TAJ MAHAL` declares 0,0 g of protein on a
78 g-carbohydrate rice; `Trijų grūdų dribsniai HAPPY FAMILY` declares 1222 kJ where its macros imply 1478 *and*
its kcal agrees with the wrong kJ; `Gr. paruoš. avižinių dribsniai MALSENA` declares 1344 kJ against its own
367 kcal (= 1536), caught by its kcal twin; `Ekol. pilno grūdo avižų dribsn. DOBELE` declares 11,0 g of sugar where
its three DOBELE siblings read 1,1-1,2 (a decimal slip; sugar is not graded, so no letter moves); and
`Raudonosios pupelės SKANĖJA` carries no panel at all and is taken on its name declaring red beans. A further five
packs overstate energy against their own macros by 6,5-8,1%, which rounding plus the EU
carbohydrate-excludes-fibre convention explains. The wheat-bran pack is the sharpest false positive: its macros
imply 787 kJ against 1159 declared, a 32% shortfall, but the 372 kJ gap is 46 g of fibre at 8 kJ/g and SAFAD puts
wheat bran at 42,9 g — the pack does not print a fibre line, so an energy-versus-macros check that ignores fibre
will flag every bran on the shelf. Not a fault.

**Two genuine retailer double-listings**, each the same product under two ids and URLs with identical panels:
`Pilno grūdo avižų dribsniai DOBELE, 500 g` (183737, 181642) and `Rudieji ryžiai SKANĖJA, 500 g` (146702, 146713).
Both are real listings and both are kept; they take the same archetype, so no name conflict arises.

**Listing-name drift here is mild and costs nothing.** 57 of 185 card names (30,8%) differ from the product page's
`h1#product_name`, against 66% corpus-wide: four drop a whole-grain word, six drop "greitai paruošiami", two drop
"su saldikliu". Unlike `lt/20`, none of them changes an archetype — SAFAD has no wholemeal/refined split for oats
(oat flakes are wholegrain by construction) and no porridge rows to split on sweetener. The stale-brand behaviour
recurs: `Ilgagrūdžiai ryžiai RIMI BASIC` is `… RIMI SMART` on its own page. The abbreviation is severe enough to
have broken the first-pass classifier used to draft this file, which read `Ekol. skald. raud. lęšiai SALLING EKO`
as an unspecified lentil because it matched on "raudon" and the card says "raud.".

### Flour: two taxonomies that agree on species and disagree on everything else (`lt/22`, SH-2-12)

62 products, 19 archetypes, **30 A / 20 B / 12 C**, 110 claims verified. The shelf publishes 73 products and four
facet counts summing to exactly 73 — wheat flour 22, other flour and starch 14, flour mixtures 26, gluten free
mixtures of flour 11 — matching the URL slugs child by child, on one listing page, with page 2 empty.

**Eleven of the 73 are deliberately not in this file, and this is a new structural trap.** The "Gluten free
mixtures of flour" facet does not point at a child of SH-2-12: its products live at
`/groceries/functional-food/gluten-free` with GTM codes in the SH-2-20 space, and all eleven are already rows in
`lt/15`. **A facet can borrow a node from a different parent shelf, and when it does the product URL says so.**
This is the mirror of the grains shelf's trap: there the GTM `cat` attribute suggested an overlap the URLs
disproved, here the URL path reveals an overlap the facet hid. Check the URL path segments of every product
against the shelf you think you are building, not just the facet counts — the counts will agree either way.
Writing the eleven again would have put eleven duplicate URLs in the corpus and double-weighted eleven names in
the eval.

**The type number on the pack is German and the type number in SAFAD is French.** Eight of the 22 wheat flours
print a milling type — 405 D, 550 D, 550 C, a bare 550. The number is the German ash grade in mg per 100 g of dry
matter (405 = 0,405% ash); SAFAD grades on the French scale, and the two convert by ash: German 405 is French
T45, 550 is T55, 812 is T80, 1050 is T110, 1600 is T150. SAFAD has no T45, so the 405 packs fall to the nearest
row, T55; the 550 packs convert to T55 exactly. **Every white wheat flour here therefore takes
`Wheat flour, type 55 (for bread)` whether or not its name carries a number**, and the bread/pastry sub-split is
left at bread because no pack names a use. Two things make that safe rather than lazy: the seven wheat flour rows
share every `E_` and `S_` value (see the cross-cutting note above), so the type number changes nothing
downstream; and the shelf itself shows the number is not part of the name — `Kvietiniai miltai MALSENA, 2 kg` and
`Kvietiniai miltai MALSENA, 550 D, 1 kg` have identical panels (1446 kJ) and identical ingredient lists
("tipas 550 d"), one flour in two pack sizes with the larger one dropping the number. The axis these names *do*
resolve is white against wholemeal: `viso grūdo` and `pilno grūdo` take `Wheat flour, type 150`.

**The 26 dry mixes are the shelf's real problem** and are written up in the gaps list above. Two retailer facts
came out of them. `Mišinys picai MALSENA` and `Kepimo mišinys picai RIMI` are identical on every panel line
(1399 kJ) and list the same four ingredients in the same order — whatever the two supply chains are, the
formulation is one formulation. And `Miltinis mišinys baltai duonai MALSENA`, sold for **white** bread, lists
sifted **rye** flour first, ahead of the wheat, with caraway; its black-bread stablemate has the same ingredients
in nearly the same order and differs only in which rye malt it uses. The colour word on the front distinguishes
the malt, not the grain. Both take the same archetype so nothing turns on it here, but a model reading
"balta duona" as a wheat bread is reading the marketing.

**One pack's name and its ingredient list name different grains.** `Kvietiniai miltai picai DURUM LAMOLISANA`
says wheat flour on the front and lists `kietųjų KVIEČIŲ manų kruopos` — durum semolina — as its only ingredient.
It is *semola rimacinata*, the re-milled durum the Italian pizza trade uses, and takes `Durum wheat semolina, raw`
(**A**). Its sibling `Kvietiniai miltai LAMOLISANA` lists soft wheat and takes the ordinary T55.

**A rye row to be wary of.** `Rye flour, type 170` is correct for wholemeal rye by ash and matches these packs on
energy to within 4%, but its 15,90 g of protein and 23,80 g of fibre sit far above SAFAD's own `Rye, whole, raw`
(10,30 and 15,10) and above every pack here (8,4-8,5 g of protein). All 25 of its nutrition fields are its own —
it is not a duplicate of anything — so this is flagged, not claimed as the copied-nutrition defect above.

**Panel quality: 3 faults in 62 (4,8%).** `Kepimo mišinys vaniliniam keksui RIMI` declares 52,3 g of sugars while
listing wheat flour *ahead* of sugar; ingredient order is by weight, so even setting flour and sugar equal and
counting the whey powder as pure lactose the ceiling is about 50 g, before the egg powder takes any share.
`Miltinis mišinys čeburekams MALSENA` declares 9,3 g of sugars on an ingredient list of wheat flour, salt and
heat-treated wheat flour — flour carries about 1,5 g, and the heat-treated flour is capped at 2,2% by the salt
above it, so it cannot supply the difference; the figure equals what its sugar-containing stablemates declare.
`Kviet. TRADICINIAI miltai KAUNO GRŪDAI, 0,9kg` declares 0,0 g of fat where its 1,75 kg stablemate declares 1,3
and every other white flour here reads 0,9-2,4. None changes an archetype. Separately, `Kokosų miltai ALVO` trips
the saturates check at 8 g in 8,5 g of fat (94%) and that is **correct** — coconut fat is about 90% saturated, and
the check has no way to know which fats are allowed to be that saturated.

**Listing-name drift is 37,1% here (23 of 62) and costs nothing.** Every one of the 23 is the card abbreviating:
`stor.` for `storiems`, `amerikiet.` for `amerikietiškiems`, `Aukšč. rūš.` for `Aukščiausios rūšies`, `405 D`
squeezed to `405D`. Two are worth noting anyway. `Pilno gr. kviet. miltai KAUNO GRŪDAI` is `Viso grūdo dalių
kvietiniai miltai` on its own page — two different Lithuanian phrases for wholegrain on one product, which is the
axis the archetype turns on. And `Mišinys keksiukams MALSENA GALAXY` is `Miltinis mišinys keksiukams` on its page:
the card drops the very word, `miltinis`, that says the thing is a flour mix.

### Instant meals: a whole shelf of dehydrated food, and SAFAD models dehydration three different ways (`lt/23`, SH-2-4)

156 products, the entire shelf. Five children, and for once the facet counts, the GTM `category` attribute and the
product URL paths all agree: broths 25 (SH-2-4-22), instant porridges 8 (SH-2-4-25), instant noodles 104
(SH-2-4-26), instant sauces 5 (SH-2-4-27), instant soups 14 (SH-2-4-28) — 156, with two listing pages holding
100 + 56 and page 3 empty. Nothing is borrowed from another shelf and no URL is already in the corpus, which is
why this became `23_instant_meals_archetypes.csv` rather than rows folded into `lt/15`. The children are not clean
partitions of the *food*, though: the noodle child holds a potato mash, a risotto, a tomato soup and eleven durum
pasta kits, and the soup child holds two ramen packets. Rows follow the pack, not the facet.

**The organising problem is that every product here is dehydrated and SAFAD models dehydration three ways.**
Ask of each row not "is this the right dish" but "is this the right *form*":

| what SAFAD holds | example | how `lt/23` grades it |
|---|---|---|
| the dry product itself | `Asian noodles, flavoured, dehydrated`, `Rice noodle, dried`, `Potato flakes, dehydrated, plain` | on its numbers |
| the dehydrated product *as reconstituted* | `Soup, mushrooms, dehydrated and reconstituted`, `Broth, stock or bouillon, poultry, reconstituted with water at consumer` | on its numbers; the basis gap shows up as C |
| only the finished dish or the wet sauce | `Carbonara-style pasta`, `Carbonara sauce, prepacked`, `Soup, split peas, prepacked, to be reheated` | class `'x'`, C by definition |

That produces 31 A / 46 B / 78 C / 1 blank over 22 archetypes. Exactly half the rows are C, which puts the file
among the most C-heavy in the corpus (`rimi/et/08` fish is 52,0%, `rimi/et/07` 51,2%), and honestly so. See the
cross-cutting rule on panel basis for the other half of the story: 57 of the 156 packs print their table for the
prepared bowl rather than the packet, which is a brand convention and not a property of the food.

**One archetype for eighty-nine noodle packets, and `sriuba` is not an axis.** SAFAD holds both
`Asian noodles, flavoured, dehydrated` and `Soup, Asian-style with noodles, dehydrated and reconstituted`, and
twelve of the 104 names in the noodle child say *sriuba* (soup), as do the two ramen packets sitting in the soup
child, which looks like the axis until the shelf contradicts it: MAGGI's
three 59,2 g packets are two `Makaronai` and one `Makaronų sriuba` with panels of 1868, 1866 and 1867 kJ; NISSIN
calls its cup a soup and SUN YAN calls the identical object noodles. The word tracks the brand. Every wheat instant
noodle takes the noodle row, which also keeps the convention that an archetype names the product in the state it is
sold. Rice noodles split off onto `Rice noodle, dried` — that is the one grain distinction the name does carry.

**SAFAD's one dehydrated-noodle row is not fried.** It carries 1570 kJ and 3,0 g of fat, while all 89 wheat packets
list palm or sunflower oil inside the noodle block and the ones declaring the packet run 9,3-21 g. There is no
second row, so the mapping is forced and the corpus rule for a forced mapping applies — grade on energy alone,
state the fat gap in Comments. Energy then sorts them by how much of the packet is noodle rather than by how fried
it is: of the 59 packs that declare the packet, the 18 inside 15% carry 12-19 g of fat and the 41 outside it carry
9,3-21, so fat is not what separates them; the B rows below the row are cups and yakisoba packs diluted by a wet
sauce sachet, and those above are plain dry sachets.

**Two defects found in the `Ready to eat soups` branch.** `Soup, Asian-style with noodles, dehydrated and
reconstituted` carries `E_climate_change` 9,50 kg CO2e/kg, the highest in the branch — above the crustacean soup at
8,94 and 68 times the chicken-and-vegetable soup at 0,14. Adding water cannot raise a per-kg footprint and the dry
noodle it is made from is 1,68; for the soup to reach 9,50 its seasoning, about 1% of the finished mass, would have
to sit near 900 kg CO2e/kg, thirty times SAFAD's own raw beef. Flagged, not used. The second is
`Asian noodles, plain, cooked, unsalted`, whose mechanism is now pinned — see the copied-nutrition rule above.

**Panel quality is the worst measured: 18 of 156 (11,5%) against 4,8% on flour.** Eleven declare 0,0 g of salt on a
product whose first ingredient is salt (five bouillons, five cup soups, one noodle packet); four declare 0,0 g of
saturates over 8,4-19 g of palm-oil fat; two NISSIN cups declare 0,0 g of protein on a 91% wheat-noodle product;
one prints 1884 kJ beside 488 kcal (= 2042 kJ); one prints a prepared-bowl energy beside a dry-basis fat line, its
own macros coming to 1358 kJ against a declared 485; one prints 12 g of saturates inside 3,9 g of fat. **One false
positive worth recording**: RIMI SMART sells the same 60 g noodle with 2,3 g of saturates and with 10 g, which
reads as a fault until the ingredient lists show sunflower oil in one pair and palm oil in the other. Check the oil
before calling a saturates figure wrong.

**A tooling defect this shelf exposed.** `verify_nums.py` indexed the detail JSON by product *name*, so where two
Rimi products share a display name — three pairs here — every claim on one of them was silently checked against
the other's pack. It now resolves by the product id in the Source URL and falls back to the name only for files
whose URLs carry no `/p/<id>`. Re-running all twenty-two Lithuanian files after the change found no
pre-existing wrong claim (3974 claims, 0 wrong), but it could have hidden one.

### Sauces and condiments: the best-served shelf so far, and one word with no row (`lt/24`, SH-2-14)

Ten children, disjoint, union identical to the parent's 180 ids: spreads SH-2-14-10 (2), adjika -11 (3), mustard
-85 (13), ketchup -86 (35), horseradish -87 (6), sauces-for-cooking-and-pasta -88 (25), pesto-sauces -90 (9),
tomato-sauces-and-pasta -92 (39), other-sauces -93 (34), and **bbq-sauces, which carries `SH-20-6`** — a code from a
different top-level node — on all fourteen of its products even though they sit under this shelf's URL path.
**82 A / 59 B / 39 C**, 26 archetypes, 357 claims verified, 75 rows class `'d'`. Not one of the 180 ids appears in
any other file in the tree.

**The shape of the problem is the opposite of `lt/16`'s.** World cuisine sold sixty sauces SAFAD had never heard of;
this shelf sells the ones it was written for. Ketchup, barbecue sauce, mustard, mustard with grains, pesto, pesto
rosso, aioli, burger sauce, kebab sauce, sweet-and-sour sauce and tomato paste all exist as their own rows and are
named on the packs, which is why 75 of 180 rows are `'d'` and 46% of the shelf is A. What the shelf then exposes is
a single hole in the middle of the branch.

- **`Kečupas` and `pomidorų padažas` are one food, and SAFAD has a row for only one of them.** The Lithuanian tomato
  table sauce is water, 10-52% tomato paste, sugar, starch, salt and vinegar with no oil: 167-514 kJ, 6-23 g of
  sugars, 0,9-2,8 g of salt, and 33 of the 34 declare 0 to 0,3 g of fat. The 33 packs calling themselves ketchup run
  185-459 kJ, 0-0,5 g of fat, 4,4-23 g of sugars. The populations coincide, and the shelf proves the identity three
  ways. **The two ingredient lists are the same list**: KĖDAINIŲ's ketchup is "tomato paste 48%, water, sugar, salt,
  pea fibre, acetic acid, spices" and its tomato sauce "tomato paste 49%, water, sugar, salt, acetic acid, spices";
  DAUMANTŲ's are the same eight ingredients in the same order at 31% and 25% paste; SUSLAVIČIAUS's the same six with
  starch added to the sauce. **One factory sells one flavour under both words at once** (SUSLAVIČIAUS's shashlik
  sauce, 293 kJ as a ketchup and 335 as a *padažas*). And **Rimi files two `pomidorų padažas` products in its own
  ketchup child** and two more in its pasta-sauce child. All 34 take `Ketchup` at **B** under the
  unavailable-family rule in the cross-cutting section.
  The cost is stated rather than hidden: on `Tomato sauce, with onions, prepacked` all 34 would be C, and the two
  readings differ downstream — `Ketchup` carries 1,19 kg CO2e/kg and 26,3 of land use where that row carries 1,47
  and 69,1. **This is the file's one open call and it is worth a second opinion.**
- **The Italian sauces on the same shelf are a different food and do take those rows.** Crushed tomatoes plus 1 to
  4,5 g of oil, 2-7 g of sugars, about 1 g of salt, and names that say so: `makaronams`, MARINARA, NAPOLETANA,
  ARRABBIATA, `su bazilikais`, `picų padažas`. Twelve rows, 2 A / 9 B / 1 C on the plainest tomato-sauce row.
- **Two capitalised brand names are descriptors and change the archetype.** The frozen-gastronomy test —
  corroborate the capitals against the ingredient list — admits `TIKRAS ITALAS` ("true Italian": 85% crushed
  tomatoes, olive oil, Mediterranean herbs) and `DARŽO CHORAS` ("garden choir", *daržas* being the vegetable garden:
  carrots, peppers, onions, parsnips, courgettes, leeks, landing within 9% of the vegetable row). It rejects
  `PONAS POMIDORAS`, `KARALIŠKAS`, `GURMANIŠKAS`, `YPATINGAS` and `SENŲJŲ TRADICIJŲ`, which corroborate nothing.
  The same test catches the reverse trap, where a name promises an ingredient the jar does not hold:
  `Pomidorų kečupas HEINZ SMOKEY BACON` has no pork in it at all, only smoke flavouring, so importing a meat
  footprint on the strength of the word would be far the larger error. `Padažas FELIX PESTO` is 4,55% pesto,
  `Česnakinis padažas HESBURGER` 2% garlic, and `Bolonijos padažas makaronams FELIX` — a bolognese — contains no
  meat whatsoever, which is why it takes `Vegetable Bolognese sauce` and not the beef-bearing row beside it.
- **Four packs state a type their composition denies, which is the condiment tiering's C tier doing its job.**
  `BBQ padažas SM GARLIC & BLACK PEPPER` is a 40 g-fat garlic mayonnaise in a barbecue range (the row carries 0,6 g
  of fat); `Angliškos garstyčios SPILVA` (30 g fat) and `Pranc. SUSLAVIČIAUS garstyčios su sald.` (42 g fat, egg
  yolk, saccharin, 7% mustard powder) are oil emulsions on an 11,2 g mustard row; `Padažas FELIX PESTO` is water,
  sugar and rapeseed oil with 4,55% pesto at 454 kJ where the pesto row holds 1530. Its neighbour `SUSLAVIČIAUS
  padažas salotoms PESTO` says *padažas salotoms* in its own name, takes a dressing row, and reads PESTO as the
  flavour — the clearest pair in the corpus for **the head noun decides and the qualifier follows**.
- **The A/B line of the condiment tiering, visible inside one factory's range.** `SALDŽIARŪGŠTIS ananasų pad.
  KĖDAINIŲ K.` (427 kJ) and `Saldž.pad.su anan. KĖDAINIŲ KONSERVAI` (423 kJ) share an ingredient list word for word.
  The first says *saldžiarūgštis* and takes `Sweet and sour sauce, prepacked` at A; the second says only *saldus*
  and is graded B for the identical mapping.
- **Panel quality is the best on any shelf that has a fault at all: 4 of 180 (2,2%).** A tomato sauce declaring 44 g
  of protein; a HELLMANN'S sauce printing 29,3 g of carbohydrate and 29,3 g of protein, the same figure twice,
  beside 599 kJ where those macros buy 1022; a HESBURGER sauce declaring 2416 kJ where its macros buy 1358 (its fat
  figure is credible, so it is banded on fat with an explicit letter); and a FELIX ketchup printing 310 kJ beside
  80 kcal. One further flag was checked and cleared — HEINZ ZERO's 185 kJ against 123 implied is the documented
  no-added-sugar trap, fibre and the vinegar's acids rather than an error. `lt/17`'s 146 cereals remain the only
  shelf with none at all.

### Coffee, cacao and tea: two shelves in one file, and a taxonomy that only models the cup (`lt/25`, SH-2-8 + SH-2-2)

Six children, disjoint, union identical to the parent's 200 ids, and the URL slugs and GTM codes partition the shelf
identically: cacao SH-2-8-12 (8), chicory-drinks -41 (3), coffee-in-capsules -43 (67), coffee-beans -44 (38),
ground-coffee -45 (46), instant-coffee -46 (38). Each child was re-fetched alone and returned exactly those six
numbers; two listing pages hold 100 + 100 and page three is empty. The coffee half is **117 A / 11 B / 72 C**,
and — the number that characterises the file — **10 archetypes for 200 rows**, twenty rows apiece, which ties
`lt/13`'s ice cream for the tightest ratio in the tree (`lt/20` and `kritis/07` put more of a file on a single row,
80% and 86%, but over far fewer archetypes).
Nothing overlaps the fifty peer files.

**The file is bimodal, and the split is the physical state SAFAD chose to store.** 123 of the 200 packs are roasted
coffee — beans, ground, or ground and sealed in a capsule — and `Coffee, ground` is the only solid coffee row in
the database. Its L3, `Coffee beans, roasted and ground`, names beans and grounds together, and a capsule is
packaging rather than a property of the food, so all 123 sit there and 115 of them are class `'d'`.

The row is well calibrated — and the packs disagree with each other along a line that is **not the food**. Lavazza
and Illy print 1307-1448 kJ with 14,7-17,0 g of protein on beans, grounds and capsules alike, against the row's
1410 and 13,9; every pack declared by Nestlé (Dolce Gusto, Nescafé, the Starbucks capsules) prints 1008-1150 kJ
with 6,6-8,1 g of protein — a quarter less energy and half the protein for the same food — and the two Starbucks
bean packs made by Nestlé Brasil sit between at 1171 and 13,0. So the -18% to -29% energy gaps on the Nestlé
capsules are a house convention rather than anything about capsules, and Lavazza's own capsules prove it by sitting
with the Lavazza beans. Worth remembering generally: **where a single-ingredient food's panels split cleanly by
declaring company, the spread is a declaration artefact and must not be read as product variation.**

The remaining 77 rows split once more. Five sit on a solid row — the two cocoa powders and the three chicories,
the only other powders SAFAD stores as powders — and the other **72 sit on a row whose `food_product` is a drink**:
instant coffee, decaffeinated coffee, cappuccino and latte packs, sweetened cocoa and flavoured drink powders. The
letter then follows the row's physical state and almost nothing else. Cross-tabulated:

| | A | B | C |
|---|---|---|---|
| on a solid row (128) | 117 | 10 | 1 |
| on a drink row (72) | 0 | 1 | 71 |

The single C on a solid row is the GALCA "chicory drink" that is 83% barley; the single B on a drink row is the one
bottle on the shelf that really is ready to drink. **199 of 200 letters are decided by whether SAFAD stored the
food in the state the pack sells it in**, which is the clearest statement of the prepared-basis rule the corpus has
produced. See the cross-cutting rule.

- **SAFAD's solid coffee L2 contains one solid and two beverages.** The three L3s are `Coffee beans, roasted and
  ground` (→ `Coffee, ground`, 1410 kJ), the same decaffeinated (→ a row at 18,6 kJ) and `Instant coffee, powder,
  decaffeinated` (→ a row at 8,91 kJ). The second and third are internally consistent cups — their environmental
  columns are a cup's as well, 0,68 and 1,63 kg CO2e/kg against 8,4, and 25,2 and 52,7 of land use against 417 —
  so nothing here is broken in the copied-nutrition sense; two drinks have simply been filed in the solid branch.
  The consequence is that a decaffeinated coffee has exactly one archetype available and its numbers are a brewed
  cup's, at nine rows on this shelf.
- **And there is no caffeinated instant-coffee powder L3 at all**, so the twenty instant jars take
  `Instant coffee, without sugar, ready-to-drink` at C. The corollary is worth stating because it is real: the one
  *decaffeinated* jar is better served by the taxonomy than the twenty caffeinated ones, purely because decaf was
  given a powder L3 and regular instant was not. Both still grade C.
- **112 of 200 packs (56%) publish no nutrition table, and for most of them that is lawful.** Roasted coffee bean,
  whole or ground, is on the EU's exemption list. 34 of 46 ground coffees, 27 of 38 bean packs and 36 of 67 capsule
  packs print nothing; the brands that do are volunteering it. No earlier file has had to grade a majority of its
  rows with no pack data, and class `'d'` is what makes it possible — the name states the type, one row exists for
  it, and the numbers never had a vote. The exemption names the bean and not the extract, yet 15 of the 21 instant
  jars print nothing either (every brand but NESCAFE), which is recorded as an open question, not explained away.
- **Thirty-three SUSPECT MACROS flags, and thirty-two of them are not faults.** Roasted coffee is about
  two-fifths dietary fibre, which the implied-energy check does not count and which no pack here declares — but
  `Coffee, ground` does, at **39,7 g per 100 g**, worth 318 kJ. See the cross-cutting rule; this is the first shelf
  where the flag had to be dismissed wholesale, and the one flag that survives is the CAFE AU LAIT panel below.
- **Three real panel faults (3,4% of the 88 that declare) and eleven prepared-cup panels, which are different
  things.** The faults: `Kavos kaps. DOLCE GUSTO CAFE AU LAIT` declares 1670 kJ over macros worth 88 on a pack that
  is 73,7% whole milk powder; two of the three JACOBS 3in1 boxes declare 0 g of saturates in 15 g of fully
  hydrogenated coconut oil where the third declares 15 g. Separately, eleven dry packs print the made-up cup —
  `PAULIG CLASSIC CREMA` at 0 kJ, `PAULIG CLASSIC CREMOSO` at 13, `AROMA GOLD GRANDE` at 14, and eight milk-coffee
  packs at 102-198 — the same brand convention `lt/23` measured, and three of those keep their A because class
  `'d'` does not let a panel basis decide a mapping.
- **A flavour word in a coffee name is sometimes an ingredient and sometimes a tasting note.** `LAVAZZA A MODO MIO
  PASSIONALE`, `NOTA VIOLA`, `FIORI LUNGO` and `CREMA E AROMA` name nothing that is in the pack; `STARBUCKS SMOOTH
  CARAMEL`, `CREAMY VANILLA` and `BY NESPRESSO CHOCO` are coffee plus *natūrali kvapioji medžiaga*, as are the
  three GURMAN'S grounds. Only the ingredient list separates them — and one pair will not resolve: `LAVAZZA GOURMET
  CARAMEL` and `GOURMET CHOCOLATE` list "100% arabica" and "arabika ir robusta" while carrying the same panel to
  the decimal, so one list is wrong and neither confirms a flavouring. All eight flavoured coffees grade B: an
  added flavouring is an axis `Coffee, ground` has no column for, and a name that states one cannot be an A on a
  row that denies it.
- **NESQUIK is filed under cacao and three of its five pouches there contain none.** The strawberry pouch is sugar,
  rice flour, beetroot red and flavouring; the banana and vanilla pouches are sugar, soluble maize fibre, flavouring
  and carotenes. A model reading the brand will answer cocoa; the ground truth reads the pack. The two that *are*
  cocoa are also fortified with vitamins C and D, and SAFAD carries a fortified cocoa-drink row — but nothing in the
  name reaches it, so they take the plain sweetened one. That is the not-fortified rule and the name-reachability rule
  agreeing for once.
- **Two "coffee drinks" that are mostly grain.** The MOKATE cappuccino sachets carry 3,3% and 12,3% roasted BARLEY
  and RYE extract against **1% real coffee**, and GALCA's "chicory drink" is 83% roasted barley extract with 15%
  chicory. `Coffee imitates (Solid)` holds chicory and chicory-with-coffee and nothing built on roasted grain, so
  the name carries all three and the composition denies it.
- **The chicory pair is the sixth copied-nutrition case and the one that shows the mechanism** — same CIQUAL code,
  AGB codes `18152_1` and `18152_2`, LCA diluted by 46× and nutrition copied across untouched. Written up under the
  copied-nutrition rule; the powder row is the sound twin and carries all three chicory packs — the GALKA instant
  extract at A, the roasted ground root at B, the 83%-barley one at C.
- **The two cards that lose the word deciding the archetype** are `DOLCE GUSTO CAFÉ INTENSO` (the page says `CAFÉ
  AU LAIT INTENSO`) and `MOKATE GOLD HAZELNUT` (the page says `MOKATE CAPPUCCINO GOLD HAZELNUT`). Both are
  milk-powder packs whose recorded name says only "coffee". See the name-drift section — this shelf is where the
  severity of that drift, as against its frequency, becomes visible.

#### The tea shelf, folded into the same file (SH-2-2, 189 products)

Five children, disjoint, summing to exactly 189 — black-tea SH-2-2-11 (46), other-teas -12 (23), fruit-tea -14
(38), green-tea -15 (47), herbal-teas -16 (35) — with the URL slug and the GTM code agreeing product for product
and each child returning the same number when re-fetched alone. Two listing pages of 100 and 89, page three empty,
nothing overlapping the fifty peer files. **3 A / 167 B / 19 C over 10 archetypes**, taking the file to 389 rows.

**Where coffee was bimodal, tea is flat, and for the opposite reason.** The coffee half divides because SAFAD holds
roasted coffee as a solid and everything else on that shelf as a cup. The tea half does not divide at all: there is
no solid tea row to divide against. 169 of the 189 packs are leaves, dried herbs or dried fruit and every one of
them lands on a brewed infusion, so the file's two halves now demonstrate the two ends of the prepared-basis rule
side by side — an instant coffee at C because a solid row existed and was passed over, a tea at B because none
does. See the cross-cutting rule for the three-way ladder this produced.

**The packs agree with the rows, which is why the mapping is certain.** Only 38 of 189 print a table, and the 19 of
those that sit on an infusion row all declare the *brewed cup*: TWININGS 5 kJ, LIPTON 17, TEEKANNE 3, APSARA 9,
against the rows' 5,36, 21,70 and 3,40. Dry tea leaf is around 1000 kJ per 100 g, so **not one pack on the shelf declares what
is in the box** — manufacturer and taxonomy describe the same liquid, and the state is the only thing wrong with
the mapping. Those percentages are meaningless at this scale (17 against 5,36 is +217% and is 12 kJ), which is
where the energy-floor note under *Reliability grades* comes from.

- **The solid tea branch exists and its one row is an artichoke.** Written up under Gaps; `Tea and herbs for
  infusions (Solid) / Peppermint (Mentha × piperita)` carries `Chinese or Japanese artichokes, frozen, raw` on
  CIQUAL 20231. It is the single cell that costs this shelf its A grades.
- **`Oolong tea, brewed` carries black tea's nutrition** on AGB 18156 against CIQUAL 18154 — the seventh
  copied-nutrition case, and the one that made the class countable across the whole database. See that rule.
- **The retailer swapped two teas between children, and the names caught it.** `Žalioji Ceilono arbata IMPRA`
  (1022404) is filed in black-tea and `Juodoji Ceilono arbata EARL GREY IMPRA` (1022403) in green-tea. Both names
  and both ingredient lists are unambiguous, so the shelf was mapped from the name and the pair swaps back. No
  other card on the shelf contradicts its child — which is also why the child is never used as evidence.
- **Nineteen of the 23 products in `other-teas` are not infusions.** Eight MASHIE berry purées and three SKANOVĖ
  fruit-and-sugar jars go to `Jam, raspberry`, three ŽOLYNĖLIS concentrates and three EKLAND instant tins have no
  row for their form, and two MOKATE sachets are milk powder with 4% matcha in them. All are in Gaps.
- **The SKANOVĖ jars are where the name rule bites the other way.** `Šaltalankių arbata SKANOVĖ, 300 g` calls
  itself *tea* and is 53% sea-buckthorn purée with sugar at 831 kJ. The ground truth is the jam and the name is
  recorded as misleading — the same call `lt/25` made for the NESQUIK pouches that contain no cocoa. Note what was
  rejected: `Fruits puree, without sugar added` matches the word *tyrė* on the MASHIE packs and carries an axis all
  eleven contradict, sugar being their second or third ingredient.
- **`Kav. gėrimas MOK. MATCHA MANGO` is sold as a coffee drink and has no coffee in it** — sugar, whey permeate,
  18% skimmed milk powder, 4% matcha, 0,1% mango. The product page says *Kavos gėrimas* too, so this is the
  manufacturer's word and not a listing-card truncation, and no reading of the name can reach the right row.
- **`Čiobrelių žolė ŽOLYNĖLIS, 30 g` is the shelf's only A and the only pack that does not say *arbata*.** It is
  dried thyme herb, and `Herbs, spices and condiments / Herbs / Thyme, dried` (1180 kJ) holds it as a solid. Name
  and physical state both match, which is the top rung of the ladder — and it is on this shelf exactly once.
- **All five infusion rows carry one environment: 0,04 kg CO2e/kg and 4,02 of land use**, because they are a
  kilogram of hot water. A 100 g box of leaves scored against that gets 0,004 kg CO2e where grown, withered and
  dried tea is on the order of 15 kg CO2e/kg. That is a factor of several hundred — worse than the coffee half's
  problem rather than the same size — and it is an argument about the scoring path, not about the mapping, which
  has no alternative to be wrong about.

### Sugar and sweeteners: the one shelf the taxonomy was already shaped for (`lt/26`, SH-2-9-50)

44 products, 6 archetypes, **24 A / 14 B / 6 C**, 48 claims verified. Three children — white-sugar/SH-2-9-60 7,
brown-sugar/SH-2-9-61 14, sweeteners-and-other-sugar/SH-2-9-62 23 — whose URL slug and GTM code agree product for
product, each re-fetched alone returning the same count and the same id set, union exactly the parent's 44.
None of the 44 URLs appears in any of the 51 peer files. `SH-2-9-50` is a child of the confectionery-additives
shelf `SH-2-9`, and it does not appear in that parent's own listing: `SH-2-9` lists 104 further products in three
other children — baking-spices/`SH-2-9-47` 59, cake-decorations/`SH-2-9-49` 21, jelly-and-kissel/`SH-2-9-52` 24 —
with zero id overlap with these 44. So building `SH-2-9` later means those 104, and a parent listing is not a
reliable enumeration of its children.

- **Rimi's three children and SAFAD's L3s line up almost one to one.** `Sugar and confectionary / Sugars` keeps
  `White sugar`, `Brown sugar`, `Fructose` and `Flavoured sugar` as separate L3s, and 27 of the 44 packs sit on the
  first two. The 25 of those that are actually cane or beet sugar land within **2,4%** of their row's energy — 2,0%
  across the nine white and 2,4% across the sixteen brown — and the nine on `Sugar, white` are the tightest
  agreement between packs and an archetype anywhere in the built corpus, the next being `Basmati rice, raw` at 3,2%
  over twelve. Sucrose is sucrose; the point is that on this shelf a letter below A is never the numbers' doing.
- **Brown is always marked and white never is, so an unmarked name is white.** Every brown pack on the shelf
  carries *rudas*, *rudasis*, *tamsusis*, *nerafinuotas*, *muscovado*, *Demerara* or *golden*, which is what makes
  a bare `Cukrus RIMI, 1 kg` readable as white sugar rather than ambiguous. That asymmetry is asserted in the
  module's `check()`, so it fails loudly if a later shelf breaks it.
- **Three brown packs are B because their only colour word is one the card does not translate.**
  `Cukrus DANSUKKER FARINAS` (*farin* is Nordic for soft brown sugar), `Cukrus DAN SUKKER DAMERARA` (Demerara,
  misspelled on both the card and the product page) and `Ekologišk.cukranendrių cukrus I LOVE ECO` (organic cane
  sugar, no colour word at all — and cane sugar is sold white as well as brown). The general form: **a variety or
  trade name is a colour word only for a reader who already knows it, and grading it A assumes knowledge the eval
  is not given.**
- **Date sugar is ground dates, and it goes to the fruit branch.** `Ekologiškas datulių cukrus AMRITA` is
  dehydrated dates milled to powder, one ingredient, with a panel that reads like fruit — 67 g of sugars inside
  77 g of carbohydrate, a ten-gram gap that is fibre, where every other sugar on the shelf that declares its sugars
  keeps the two lines within two grams. It takes `Date, pulp and peel, dried` (1210 kJ, +17,7% for the extra
  drying) and not `Sugar, brown`, because the crop word is in the name, the food *is* the crop, and dates carry
  2,89 kg CO2e/kg where brown sugar carries 1,04. **Where a "sugar" is made by drying and grinding a named crop,
  map the crop.**
- **Two packs declare 0 g of sugars for pure sugar.** `Cukrus uogienėms DAN SUKKER` and `Rudasis gabalinis cukrus
  DAN SUKKER` both print 99 g of carbohydrate and 0 g of which sugars. Retailer error; the grading reads energy
  and fat, so nothing downstream moved, but a later pass looking at the sugars column would read two sugar packs
  as sugar-free.
- **The xylitol panel is right and the macro check is wrong, for the first time with a declared polyol.**
  `Saldiklis ksilitolis ALVO` declares 1000 kJ where 99,8 g of carbohydrate implies 1697. Polyols cost 10 kJ/g
  under Annex XIV of Regulation 1169/2011, not carbohydrate's 17, and 99,8 × 10 is 998. `extract_det.py` says in
  its own docstring that it does not model polyols, so the SUSPECT MACROS line is the check working as documented.
- **Annex V explains the silence exactly.** Six of the 44 packs print no nutrition table and all six are table-top
  sweeteners, exempted by name at Annex V point 6 — beside salt at 5, coffee at 7 and tea at 8. The other six
  sweeteners declare voluntarily, and all 32 sugar and syrup packs declare, so nothing on the shelf is silent that
  should have spoken.
- **Name drift costs nothing here, for the first time.** 22 of 44 cards match the product page exactly (50%); of
  the 22 that differ, fifteen differ only in whitespace and three only in an abbreviation. Four lose a word — two
  drop the sub-brand BILLINGTONS, one drops *cukrinių runkelių* (of sugar beet) and one *cukranendrių* (of sugar
  cane) — and **none of the four changes the archetype**, because both crop words sit beside a colour word that
  already settles the row. The running count of archetype-changing losses per shelf is now 2, 2, 2, 2, **0**, and
  still unrelated to the cosmetic percentage. Note what the crop words would have been worth: `Sugar, white` is
  0,75 kg CO2e/kg on 31,90 of land use and `Sugar, brown` is 1,04 on 90,70, so beet against cane is a threefold
  land-use difference the card drops and the colour word happens to restore.
- **Saccharin carries white sugar's entire footprint** — the copied-LCA finding, measured across the database.
  See the shared-footprint rule above.

### Dried fruits and nuts: the best-served food in the database beside the worst-served one (`lt/27`, SH-2-3)

253 products, the whole shelf, in four children that partition it exactly: prepacked nuts 125 (SH-2-3-19), dried
fruits in packages 74 (SH-2-3-20), seeds 55 (SH-2-3-21) and one loose-weight pistachio (SH-2-3-17). Those sum to
255 because two packs are listed in two children at once, so the union is 253 — exactly what the parent returns
over three pages of 100 + 100 + 53 with page 4 empty. **Neither the GTM code nor the URL slug partitions this
shelf and they disagree with each other**: thirteen products have no child segment in their URL at all and six
carry the bare parent code SH-2-3 while being listed in a child. Membership was taken from the child listings and
checked both ways. **44 archetypes over 253 rows, 142 A / 58 B / 51 C / 2 blank** — 56,1% A, behind only the
oil (72,0%), grain (67,0%) and Estonian produce (64,8%) shelves among files over 100 rows, and for the same
reason: these are single-ingredient commodities named by their species.

- **The two halves of the shelf are at opposite ends of the taxonomy's quality.** `Legumes, nuts and oilseeds`
  gives one L3 per species — almond, Brazil, cashew, chestnut, coconut, hazelnut, macadamia, pecan, pine,
  pistachio, walnut, peanut (over in `Legumes, beans, dried`), plus linseed, poppy, pumpkin, sesame and sunflower
  under `Oilseeds`. Every nut and every seed this shelf sells has its own row, and 141 of the 253 packs land
  inside 15% of theirs on both energy and fat. Dried fruit has ten rows for the entire category; see the gaps register.
- **`Coated peanuts` is a row and coated anything else is not.** Twenty packs here are 46-56% peanut inside a corn
  or potato starch and wheat flour shell, and **all twenty** land inside 30% on both energy and fat — the
  best-served snack on the shelf. There is no equivalent for any other nut and none for a sugar glaze.
- **The kernel/hull cap is what the B block mostly is.** 23 of the 58 B rows are sunflower packs whose name does
  not say *lukštentos* or *branduoliai*, and five are pumpkin. See the cross-cutting rule.
- **A mixture's letter is the ratio, not the mapping.** 24 nut-and-fruit mixes take `'d'` and the 12-to-46,1 g
  fat spread across them is the axis a single mix row cannot carry; nine nut-only mixes, two fruit-only mixes and
  one seed-and-almond mix with no fruit take C, because the row each must use asserts a half it does not have.
- **Three mixes and one snack name no food at all**, which is where the ground truth stops being recoverable from
  the name: *Mišinys salotoms ruošti* (a salad seed mix), *Sėklų mišinys duonai*, *Mišinys DIAMOND* (all nuts) and
  *Barbekių skonio riešutai JĖGA* (peanuts). The first three take their largest declared component at C; the
  JĖGA pack takes `Coated peanuts` capped at B, which is the gluten-free-flour-blend rule. Rimi's own product page
  does not rescue the JĖGA one either — it says *riešutai* there too.
- **Panel coverage is 253 of 253** — complete, as it is on fourteen other Lithuanian shelves — and one panel is a
  real fault: `Juodos sūdyt.kepin.saulėgrąžos BAZARO` declares 1848 kJ where its own 58 g of fat, 13 g of carbohydrate
  and 22 g of protein buy 2741. The twelve other macro-check flags are all sound, and split fibre-up/sorbitol-down;
  see the cross-cutting rule.
- **`ispaninis šalavijas` (Spanish sage) is chia**, already recorded from the instant-meals shelf; one RIMI pack
  sells it as plain *šalavijų sėklos*, dropping the word that makes it chia, on the card **and** on the product
  page. Its ingredient line says *Salvia hispanica*. **`traškioje luobelėje`** = in a crisp shell, the coated-peanut
  marker; **`be luobelės`** on an almond or hazelnut is the opposite word and means the skin has been taken off.
- **Name drift is the worst measured and, for the first time, it costs archetypes.** Only 79 of 253 card names
  (31%) match `h1#product_name` exactly — 75 more differ only in whitespace or the space before `g`, and 40 are a
  pure abbreviation expansion. But four losses are archetype-changing: three TAFFEL packs are *Sūdyti riešutai* on
  the card and *Sūdyti **žemės** riešutai* on the page, and *Mišinys DIAMOND* is ***Riešutų** mišinys DIAMOND*
  there. Four more are class-changing, all of them the word ***nelukštentos*** (in the hull), which the page
  carries and the card never does — the axis the kernel cap exists for. Per-shelf severe counts now read
  2, 2, 2, 2, 0, **4**.

### Snacks: the branch that was written for this shelf, and eleven rows with one footprint (`lt/28`, SH-2-23)

236 products over seven children, and for once the gtm code and the URL slug agree product for product:
potato-chips 108, other-snacks 53, corn-snacks-and-popcorns 42, flatbreads 23, bread-chips 9,
vegetable-chips 6, dip-mixes 4. Those sum to 245 because the nine buckwheat, lentil and chickpea *trapučiai*
are listed under both flatbreads and other-snacks, so the union is the parent's 236 exactly. **Twelve ids are
shared with `rimi/et/12`** — the Baltic catalogue reuses ids across countries and the two language corpora are
separate files — and the Estonian mapping agrees with this one on all twelve.

`Snacks, desserts, and other foods / Snack food` is the one L2 in the database written for a retail shelf:
22 rows covering potato crisps in three cuts, potato-dough snacks puffed and unpuffed, maize puffs with and
without peanuts, tortilla chips, three popcorns, four cracker-and-pretzel rows, two cheese rows and prawn
crackers. 191 of the 236 packs land inside it and the file scores **144 A / 86 B / 2 C / 4 blank** — 61,0% A,
which four shelves beat, but **0,8% C, which none does**: the branch leaves almost nothing without a home.

- **All 71 sliced potato crisps grade A**, the largest single-archetype clean family in the corpus (the pasta
  shelf's 70 `Dried pasta, raw` is next): every one opens its
  ingredient list on *bulvės*, and they run 2079-2264 kJ against `Potato crisps`' 2220 and 28-35 g of fat
  against 32,7.
- **The dough question is the shelf's real work**, and it is written up as a cross-cutting rule above:
  17 PRINGLES take `Salty snacks, made from potato` on the recipe (not on the numbers — seventeen of them
  grade A on the sliced row as well), 15 unbranded dough packs take it capped at B, and 4 POM BEAR-recipe
  packs take `Puffed salty snacks, made from potato`.
- **`Potato crisps, "à l'ancienne"` is a trap and the three ESTRELLA KETTLE packs are not put there.** The row
  is the one named for batch frying, but it stores 36,9 g of fat, 4,2 g *above* the standard crisp row, while
  the KETTLE packs carry 28 g, 4,7 g *below* it — which is what a thicker slice does. The LCA is identical to
  the standard row's, so nothing is bought by following the process word into a row the pack contradicts.
  Keep `à l'ancienne` for a product that actually is the fattier French style.
- **A maize puff is three rows and the fat says which** — see the cross-cutting rule. The four unfried MORKŪNAS
  sticks are the instructive case: 1,2-2,2 g of fat against the maize-*snack* row's 32,6 and 0,4 g of salt
  against its 1,61, so they take `Puffed cereals textured bread` (1640 / 2,9 / 0,21), whose land use is 35,1
  against the snack row's 189 — and the difference is exactly the frying oil they do not contain.
- **Popcorn is the only snack food with a footprint of its own.** The three popcorn rows carry climate
  0,94-1,73 and land 63,6-92 where every other row on this shelf carries 1,54 and 189, so popcorn-versus-puff
  is the one call here that moves a score by more than a rounding.
- **The `lt/15` and `et/12` grain rules reproduce without exception**: *trapučiai* is a puffed cake,
  *traškučiai* is a crisp, and a cake that is half glaze is confectionery. The two RIMI lentil *trapučiai*
  carry 11,4-11,5 g of fat against the cake row's 2,9 and are the file's only two C grades — the same two
  letters `lt/15` records for the same line.
- **Two words mean "stick" and they are two different rows, and a snack-aisle *riestainis* is not a bagel.**
  `Sūrios lazdelės RIMI` is a thin salt stick (1622 kJ / 4,6 g fat / 4,0 g salt) and `Salty snacks, mini
  pretzels or sticks` matches all three (1590 / 3,13 / 4,43); `Lazdelės su sezamų sėklomis RIMI` is a sesame
  breadstick (2021 / 12,0 / 2,0) and `Grissini or bread stick` matches all three (1800 / 10,5 / 1,94). The word
  decides nothing; the sesame and the numbers do. And `Sūrūs riestainiai RIMI` at 1595 kJ and 3,1 g of fat is a
  dry pretzel ring, not the boiled `Bagel` the bakery-shelf rule assigns to *riestainis* — **that rule needs
  the shelf as well as the word.**
- **Panel faults: 7 of 236 (3%), the cleanest rate measured, and six are one corrupted TUC table.** All six TUC
  packs transpose the saturates and carbohydrate cells and repeat the fat figure in the protein cell —
  "23 g fat, 65 g saturates, 2,2 g carbohydrate, 23 g protein". Reading it as fat 23 / saturates 2,2 /
  carbohydrate 65 / protein 8 reproduces the declared energy to within 2% on all six. Energy and fat are sound,
  so no mapping depends on it. The seventh is CHRUSTIK's sweet maize at 350 kJ where its macros buy about 1490
  and an identically-formulated sibling declares 1618. Two more the macro check cannot see: `Juodos duonos
  traškučiai su česnaku BON CHANCE` carries **another product's ingredient list and panel** (carrot granules
  and candied lemon peel, 6,7 g of fat where its eight siblings carry 26,9-31,1), graded on the name because
  the panel is not the pack's; and `Krekeriai GAIDELIS GRIETINĖ IR SVOGŪNAS, 72 g` appears twice with different
  protein and salt because the listing card drops the word *MINI* from one of them.
- **Name drift is the worst measured: only 27% of cards match the `h1`, and six losses change the archetype.**
  Four LAY'S packs lose *kepti*, the only word separating a baked potato-flake crisp from the sliced one sold
  beside it under the same card name; `Juod.d.traškučiai` abbreviates *juodos duonos* past *duona*, the word
  that makes it a crouton; and `Trapučiai su aviečių gabaliukais KUPIEC` loses both *ryžių* and *belgišku
  šokoladu*. Per-shelf severe counts now read 2, 2, 2, 2, 0, 4, **6**.

### Biscuits and wafers: 59 rows, 33 footprints, and two row names that contradict their source (`lt/29`, SH-2-101)

240 products over two children, and for the first time the children are a true partition — nothing cross-listed,
nothing left over: `biscuits` / SH-12-5 holds 192 and `waffles` / SH-12-9 holds 48, summing to exactly 240 with
no product in both. The gtm code and the URL slug agree product for product for the second shelf running. No id
here appears in any of the 54 files already in the tree, so there is no cross-language check to run this time.

**This shelf is the copied-LCA finding inverted, and that is the headline.** Where `lt/28`'s `Snack food` gave
eleven rows one identical footprint, `Biscuits (cookies)` gives 59 rows 33 distinct ones — the cross-cutting
rule above records the numbers. Every distinction here is paid for, so the file's work is the distinctions, and
the four that matter are chocolate-and-in-what-form, wafer filling class, cereal-against-confectionery, and
biscuit-against-cake. **46 archetypes over 240 rows** — nearly three times the snack shelf's 17 on the same
number of products — and **125 A / 114 B / 1 C**.

- **Chocolate is four rows and the Lithuanian name draws the line.** Baked-in pieces (`… with chocolate drops`,
  6,27 / 262), a coating (`… chocolate covering`, 7,79 / 313), a cream filling (`… snack w chocolate filling`,
  6,60 / 332) and a cocoa-flavoured dough with no chocolate in it (the plain rows, 3,59 / 161) differ by up to
  a doubling. *su šokolado gabalėliais* is pieces, *aplietas* / *glaistytas* is a coating, *su … kremo įdaru*
  is a filling, and ***šokolado skonio* is a flavour claim and nothing more** — SELGA's chocolate-flavour
  biscuit declares 0,3% chocolate and belongs on the category-average row.
- **A filled biscuit is placed by the word in front of its filling, and an OREO card carries both words.**
  `Kakav. saus. su vanil. įd.` is a *cocoa biscuit with a vanilla cream*: cocoa qualifies the biscuit, vanilla
  qualifies the filling, and it is the second that chooses the row. Seven OREO variants take the dairy-filling
  row on that reading and `Kakav. saus. su kakav. įd. OREO BROWNIE` takes the chocolate-filling row.
- **The wafer rows split by filling class, not by the flavour on the pack.** Lemon, cranberry, coconut and
  salted-caramel wafers are all plain cream wafers with a flavouring and all take `Wafer biscuit, plain or
  vanilla flavoured, prepacked`; only `Wafer biscuit, with fruits` (1510 kJ / 0,8 g of fat) is a real fruit
  wafer, and nothing on this shelf is one. A wafer *roll* is not a row: `Wafer cookie` is CIQUAL 24370, a plain
  crepe dentelle, so the five cream-filled *vafliniai vamzdeliai* take the filled-wafer rows their filling
  names and the tube shape is simply not an axis.
- ***meduoliai* fits `Gingerbread` and *imbieriniai sausainiai* does not.** The row stores 1,15 g of fat: a lean
  spiced honey loaf. The nine Lithuanian *meduoliai* run 1512-1778 kJ against its 1350 and 3,5-7,8 g of fat, so
  the type is right and the recipe is richer — exactly the case `'f'` exists for, and the letter comes from
  energy alone. A Swedish pepparkaka is a different food: ANNAS's ginger biscuits are thin, crisp and
  caramelised at 1991 kJ / 19,1 g, and `Speculoos biscuit` (2000 / 18,1) is SAFAD's only crisp spiced biscuit.
  `et/12` found the same row failing from the other direction, where an Estonian *meekook* was a cream cake.
- ***trapučiai* is a third thing again.** In the snack aisle it is a puffed grain cake (`lt/15`, `lt/28`); here
  `Tradic. prancūz. trapučiai ST. MICHEL` is a French butter galette at 18% butter. The shelf decides, as it
  does for *riestainis*.
- **Cereal biscuit against confectionery, and the name says which.** *grūdiniai*, *viso grūdo*, *7 GRŪDAI*,
  DIGESTIVE and BELVITA take the four `Breakfast cereals / Cereal bars` breakfast-biscuit rows (15-16,4 g of
  fat), and within them **the declared chocolate percentage decides, not the chocolate word**: three packs carry
  3,1-4% chips and stay on the plain row, the two carrying 12,6% and 12,9% do not. MILKA CHOCO GRAINS is the
  counter-case — its name carries the grain word and its 24 g of fat does not, so composition decides and it
  stays a chocolate cookie. The not-fortified twin rule applies unchanged; the chocolate row has no twin.
- **A digestive has no row.** `Breakfast biscuit (cookie)` (1900 / 16,3 / 21 g sugars / 1,19 g salt) is the only
  cereal-biscuit row in the database and GULLON DIGESTIVE sits on it at +2,6% on energy with 17 g of sugars and
  1,6 g of salt — the best available answer, and worth knowing it is a borrowing.
- **Panel faults: ten of 240 carry a cell that cannot be right, and only one of them matters.** `Sausainiai
  SALLING JUNGLE ANIMAL` prints 1961 kJ where its own 380 kcal and its own macros both buy about 1600 — the
  only fault in a cell a letter depends on, so its letter is typed to the B the honest figure gives. The other
  nine are harmless: a stroopwafel's kcal cell (586 against its own 1857 kJ), a croissant's carbohydrate (17 g,
  a quarter short of what its energy needs), two JAVINĖ packs blanking saturates, sugars and salt on a product
  whose first ingredient is sugar, two wheat biscuits declaring 0 g of protein, two declaring 0 g of saturates
  over palm oil and cocoa butter, and one whose macros sum to 100,6 g. **Four packs tripped the saturates check
  and are not faults**: a glaze of fully hydrogenated coconut or palm-kernel fat really is 93-98% saturated,
  and the check has to allow it.
- **Name drift: 125 of 240 cards (52%) match the `h1` exactly, twice the snack shelf's rate, and four losses
  change the archetype.** `Sviestiniai vafliai SELECTION BY RIMI` drops WAFFLE CRISPS, the word separating a
  crisp waffle biscuit from a soft Brussels waffle; `Sausainiai RIMI SFOGLIATINE` drops *sluoksniuotos tešlos*,
  leaving an Italian word to carry the puff pastry; `Gl. kukurūzų lazdelės` abbreviates *glaistytos* to *Gl.*,
  and without "glazed" a maize stick belongs on `lt/28`'s puffed-maize bread row rather than in confectionery;
  `Sausainiai KINDER KINDERINI` drops *su pienu ir kakava*. One card prints a pack size of "1,35 g" for a 135 g
  pack and one still carries the retired RIMI BASIC brand where the page says RIMI SMART. Per-shelf severe
  counts now read 2, 2, 2, 2, 0, 4, 6, **4**.
- **`check()` stops where the card stops.** Flipping every id to every other key and class — 32 880 mutations —
  is caught 99,2% of the time, and all 271 slips are one shape: a swap inside the chocolate family, or between
  a filled biscuit and a plain one. That is the same boundary the reachability caps are drawn on, so it is a
  limit of the evidence rather than of the check.

### Chocolate: two shelves behind one English slug (`lt/30`, SH-12-1 and SH-12-8)

`lt/30` carries **172 rows over 27 archetypes, 108 A / 60 B / 4 C**, from two site categories whose English
URLs are identical but for the code: `/sweets/chocolate-bars/c/SH-12-1` is *sokoladiniai batonėliai*,
countlines, and `/sweets/chocolate-bars/c/SH-12-8` is *šokolado plytelės*, tablets. The Lithuanian slugs
separate them exactly, so **the English path is the ambiguous one and the shelf code is what to trust.** The
two share no product and no URL. They also share almost no rows, for the reason the cross-cutting bullet
gives: the form picks the L3.

#### Countlines: the shelf where the brand is the recipe (SH-12-1)

71 products on a leaf — no children, no subcategory link on the page but its own and its parent's, every card
declaring `category` SH-12-1, so the gtm code and the URL slug agree product for product for the third shelf
running. 71 distinct ids and URLs, none shared with any of the 55 files already in the tree. **17 archetypes
over 71 rows** and **37 A / 33 B / 1 C**.

**This is the first shelf where the product name is mostly a trademark, and that is the headline.** Nineteen of
the 71 names carry no type, ingredient or flavour word at all — "Batonėlis KIT KAT CHUNKY COOKIE DOUGH",
"Batonėlis GEISHA", "Batonėlis 3BIT Nut" — and the generic nouns that remain (*batonėlis*, *šokoladinis*,
*plytelė*) are shared by nearly the whole shelf, so they separate nothing. The cross-cutting rule above records
the line that was drawn: a mark denoting one recipe worldwide reaches the row, a decorative name inside a local
range does not. Fifteen of the nineteen are global marks and keep their letter; four are capped.

- **The cocoa share is the footprint, and it is the axis every distinction here rides on.** `Chocolate (Cocoa)
  products` holds 29 rows with **14 distinct footprints**, climate 5,32 to 19,30 and land 181 to 726 — a
  3,6-fold spread inside one L2, so this branch sits with `lt/29` and not with `lt/28`. `Coated chocolate bar
  without biscuit` is 6,18 where `Milk chocolate bar` is 12,60, because a Mars-type bar is mostly nougat and
  caramel. Every wafer-against-biscuit-against-dairy-against-praline call is therefore worth about a factor of
  two, and the four `E_` columns were compared before each was drawn.
- **Twenty-eight of the 71 are chocolate-bodied wafer bars**, every KIT KAT, LION, PRINCE POLO, KINDER BUENO
  and 3 BIT plus POESIA and RIMI's own, and they take `Chocolate confectionery, filled with wafer` rather than
  the `Wafer biscuit, …` rows `lt/29` gave the SELGA and ROSHEN wafer packs. TWIX is the counter-case: it
  declares 17% wheat flour and goes to `Chocolate bar with biscuit`, a third home and a third footprint (7,25
  and 298). The structural rule that separates them is in the ingredient list, and it is sharper than the
  names: **among floured bars, the one whose nut is declared BEFORE its flour is a praline bar with a wafer
  layer; the one whose flour comes first is a wafer bar with a nut cream.** KNOPPERS and both GEISHA declare
  the nut first; every KINDER BUENO declares the flour first.
- **SAFAD's coconut bar is filed under bakery wares.** `Coconut bar, with chocolate coating` (CIQUAL 31002) is
  `Grains and grain-based products / Fine bakery wares / Pastries and cakes` — a bar with no grain in it at
  all, and the row BOUNTY fits to +3% on energy and +9% on fat. Worth knowing before searching the
  confectionery branch for it.
- **The named fruit is a flavouring and the real fruit is apple — a whole product line this time.** The three
  CHUVS bars name blackcurrant, strawberry and raspberry; all three are 0,9% of concentrated juice over the
  same 35,4% apple purée, under a 12,7% compound glaze with 3,5% peanuts. `lt/29` found the trick once
  (RIMI's cherry filling was apple purée); here it is the whole range, which is why `check()` asserts the
  named fruit against the 0,9% clause rather than against the base.
- **Three more cards say less than the ingredient list, and one says the opposite.** "Šokoladas KINDER" is 40%
  milk chocolate round a milk cream and says only "chocolate"; "Pien. šokoladas van.sk.pien.įd., MILKA" carries
  an OREO biscuit neither the short name nor the `h1` mentions; POESIA's card calls a 34% compound glaze
  *pieninio šokolado* when the list has no cocoa butter in it anywhere; and PRINCE POLO's *riešutų skonio
  įdaras* is an aroma with no nut behind it, the same construction as `lt/29`'s SELGA chocolate-flavour
  biscuit at 0,3% chocolate.
- **Not one of the 71 panels trips any arithmetic check** — kJ against kcal, macros against energy,
  placeholder rows, the saturates ratio: all 71 pass, where `lt/29` had ten failures in 240. **And the shelf
  still carries a fault.** The 50 g TWIX prints the SNICKERS panel cell for cell, internally consistent and
  therefore invisible to every one of those tests; the two other listings of the same bar are what expose
  it. The cross-cutting bullet above states the rule. The letter is A on either set of figures, so it
  costs nothing here, and the comment records it.
- **Name drift: 22 of 71 cards (31%) match the `h1` exactly, 38 (54%) once spacing and case are ignored** —
  most of the difference is "50g" against "50 g". Six drops change the archetype: ROSHEN's `h1` says the bar
  has a crème-brûlée filling and the card does not; RIMI's wafer `h1` says it is glazed in milk chocolate,
  which is what moves it out of the bakery branch; the three CHUVS `h1`s say *su obuolių tyre*, the whole base
  of the product; and PRINCE POLO's nut-flavour bar loses the word *vaflinis*. Per-shelf severe counts now read
  2, 2, 2, 2, 0, 4, 6, 4, **6**.
- **`check()` re-derives instead of reading back.** `family()` derives all 71 keys again from the card name and
  the ingredient list in the order the calls were made, and the assertion is that the derivation agrees with
  what is written; flipping every id to every other key and every class letter to every other letter is 1704
  mutations and **all 1704 are caught**. The first draft, which asserted evidence family by family, caught 58%
  — worth recording, because the gap is entirely the families a per-key assertion never separates.

#### Tablets: the shelf where the type word is everything (SH-12-8)

101 products on a second leaf — no children, every card declaring `category` SH-12-8, so the gtm code and the URL
slug agree product for product for the fourth shelf running. 100 on page one, 1 on page two, nothing on page
three; 101 distinct ids and URLs, none shared with the 71 countlines or with any of the 55 other files.
**14 archetypes over 101 rows** and **71 A / 27 B / 3 C**.

**Where the countline shelf turned on the brand, this one turns on the type word.** 70 of the 101 names carry
*juodasis*, *tamsus*, *pieninis* or *baltasis*, and the row follows: dark over or under 70% cocoa, milk, white,
and for each an inclusion variant. The other 31 are read from the ingredient list — cocoa mass first with no
milk is dark, cocoa butter with no cocoa mass anywhere is white — and that is exactly where the caps fall.

- **Nine of 101 are capped, and four of the nine are Rimi's 44-character card throwing away the decisive
  word.** "Šokoladas su kanap.ir braškėmis RŪTA" is *Juodasis šokoladas 75 %…* on its own product page,
  "Šokoladas su pek. karijomis ir kava RŪTA" is *Baltasis*, and "Pien. šokoladas nesk.šok., RITTER SPORT" is the
  whole-hazelnut bar with the nut abbreviated out of existence. The `h1` would lift exactly those four and no
  others, which is the cleanest measurement yet of what recording the card name costs.
- **Fourteen rows, ten distinct footprints, climate 5,32 to 19,30 and land 181 to 726** — the same 3,6-fold
  spread the countline shelf found, so every call is worth about a factor of two. Two of the three rows in the
  branch's implausible cheap cluster are used here (the cross-cutting bullet above): seven fruit-bearing dark
  bars and both TOBLERONEs.
- **TOBLERONE has a row of its own**, `Toblerone milk cholcolate bar w nougat`, and it is the only brand-named
  row in the branch. It fits the pack to 0,9% on energy and 1,0 g on fat, and it carries a footprint 2,4x below
  `Milk chocolate bar` for what is milk chocolate with 3% honey and 1,6% almonds. Right row, wrong number.
- **Eleven filled tablets go to `Milk chocolate, filled` and two to `Milk chocolate bar, filled with praline`**
  — toffee, strawberry, coconut, cappuccino, yoghurt-cherry and cheesecake on the first, GEISHA's 40% hazelnut
  cream and MILKA's 36% hazelnut filling on the second. Both rows are CIQUAL 31084 and the CIQUAL name is the
  praline one; the reading taken, and why, is in the `food_name_CIQUAL` bullet above.
- **Four cards disagree with themselves.** RŪTA's 75% bar is sold as orange peel, named on its page as orange
  peel, cherries and pineapple, and lists cherries, pumpkin seed and cashew — only the cherries survive all
  three. GEISHA is sold as *su pien.įdaru*, a milk filling, and its own list opens *PIENIŠKAS šokoladas su
  LAZDYNO RIEŠUTŲ kremu (40%)*. RŪTA's pecan-and-coffee bar declares two chocolates with identical lists and no
  cocoa mass in either, so both read as white while the card claims 55% cocoa solids. DIOREL's hazelnut name has
  no hazelnut behind it. None of the four changes the row.
- **Only three panels trip any arithmetic check, and they are one recipe under three names** — the DIOREL
  compounds, whose portion-basis fault is the cross-cutting bullet above. They take C on two counts: an
  ungradeable panel and a product that is not chocolate (sugar, fully hydrogenated palm oil, 3% cocoa powder,
  no cocoa butter and no cocoa mass anywhere).
- **28 of 101 lists put a vegetable fat second or third or declare a *kakavos sviesto pakaitalas* outright**,
  four ROSHEN blocks by name. Where the fat is the filling the chocolate row still holds; where it is the body —
  the five KIT KATs, the three DIOREL — the cocoa footprint the row carries is overstated. Same finding as the
  countline shelf's seven compound glazes, at four times the count.
- **Name drift: 33 of 101 cards (33%) match the `h1` exactly, 41 (41%) once spacing and case are ignored** —
  normalising recovers almost nothing here, because these cards abbreviate whole words (*nesmulkintas* to
  *nesm.*, *lazdyno riešutai* to *lazd. rieš.*) rather than just squeezing the spacing, which is what the 54%
  on the countline shelf was mostly made of. Four drops change the archetype's reachability, all four listed
  above. Per-shelf severe counts now read 2, 2, 2, 2, 0, 4, 6, 4, 6, **4**.
- **`check()` re-derives instead of reading back**, as on the countline shelf: `family()` derives all 101 keys
  again from the card name and the ingredient list, and the class letter is tied to named evidence — B to the
  nine ids listed with their reason, C to the packs whose lists hold neither cocoa butter nor cocoa mass and
  whose panels print kJ = kcal. **2121 mutations, all 2121 caught.** Two bugs it found are worth repeating: the
  ingredient splitter was cutting "vyšnių milteliai 0,5%" in half at the decimal comma and losing the share the
  whole mapping turns on, and the nut-paste exclusion was matching "kakavos masė" on a card that omits the comma
  after its almonds.

### Sweets and dragées: no bars, so the coating decides (`lt/31`, SH-12-4)

`lt/31` carries **56 rows over 19 archetypes, 13 A / 41 B / 2 C**, from
`/sweets/sweets-and-dragees-/c/SH-12-4`, the third shelf under SH-12 this corpus has taken. A leaf: the pages
name no shelf code but SH-12-4 and its parent SH-12, every card declares SH-12-4 as its own category — so the
gtm code and the URL slug agree product for product for the fifth shelf running — and 56 on page one, nothing
on pages two or three. The Lithuanian slug, *saldainiai-ir-draže*, says what the English one says for once.
56 distinct ids and URLs, none shared with `lt/30`'s 172 chocolate rows or with any of the 56 peer files.

**This is the first confectionery shelf that does not sit inside one L2.** Its 19 rows are spread over four:
14 in `Chocolate (Cocoa) products`, 3 in `Confectionery (non-chocolate)`, 1 in `Fine bakery wares` and 1 in
`Snack food`. Nothing on the shelf is a bar, so the countline-versus-tablet question that decided the L3 on
both chocolate shelves never arises, and what separates one row from another is the **outside** of the sweet —
see the outer-layer bullet, which this shelf is the second and clearest instance of.

- **The dragée L3 holds exactly one food and it is an almond.** `Dragée, sugar coated` → `Sugar-coated almond`
  is the only place SAFAD names the form, and none of the three cards that need it is the sugared almond it was
  written for: two RŪTA packs are **70% almond** where the row is built on about a quarter that much (14,7 g of
  fat, which is roughly 28% almond), and a SKAWA cream dragée has a wafer core where the row has a nut. They
  are filed there anyway — the L3 names the food exactly and no other row names the form at all — and the two
  RŪTA packs are the file's only two C's, both earned on a 156% fat gap that is entirely the almond share.
- **A jelly centre is not an inclusion.** LAIMA's blueberry pack declares a **37% fruit preparation** (apples,
  glucose syrup, sugar, rice flour, blackberry and blueberry juice, pectin) inside a dark chocolate. At that
  share the fruit is a centre, which puts it on `Dark chocolate, filled with mint confectionery` — the only
  filled-dark row SAFAD carries — and not on `Dark chocolate bar, with fruits`. That row is kept for the packs
  where the chocolate is 89% or 92% of the weight and the fruit is scattered through it. `lt/30` filed a mango
  jelly the same way, so the **filled-dark-is-mint gap now has its third instance**. The boxed half of the
  same file adds five more in one go — see the liqueur-fondant bullet below.
- **Thirteen of 56 capped, and the reasons cluster differently from the chocolate shelves.** Five are brand or
  range names over a food the card never describes — BON PARI and four ZOZOLE packs are boiled sugar and the
  card says only *saldainiai*. Three are Ferrero and KIDDY boxes whose filling is the whole of the mapping and
  is not named. One is the KIT KAT ball, whose name promises wafer where the list declares crisp flakes of
  flour, gluten and malt. One is WERTHER'S hard caramel, sold as a cream. One is the SKAWA peanut dragée, whose
  coating is a compound. One is a four-product assortment under a single panel. **And exactly one is Rimi's
  44-character card again**: "Lazdyno riešutai šokolade LAIMA" drops the *pieniškame* the product page keeps,
  and that word chooses between the milk and the dark nut rows.
- **The panels are the cleanest of any confectionery shelf so far.** Two of 56 overrun 100 g of macronutrients
  and both by less than a gram; no kJ/kcal ratio is off, no saturates figure exceeds its own fat, no sugar
  figure exceeds its own carbohydrate. After `lt/30`'s DIOREL portion panels this is worth recording: a bad
  panel is a property of the manufacturer, not of the category.
- **Name drift: 21 of 56 cards (38%) match the `h1` exactly, 29 (52%) once spacing and case are ignored**, and
  exactly one drop changes the archetype — the LAIMA card above. Per-shelf severe counts now read
  2, 2, 2, 2, 0, 4, 6, 4, 6, 4, **1**.
- **`check()` re-derives instead of reading back.** `family()` derives all 56 keys again from the card name,
  the ingredient list and the fat cell, in this order: popped maize, declared pretzels, coconut with no cocoa
  anywhere, a fat cell under 2 g (boiled sugar carries none), then the no-cocoa-butter branch (glazed or not,
  peanut or not), then the chocolate branch. The class letter is tied to named evidence — `'B'` if and only if
  the id is in `_CAPPED` — and a chocolate row over a cocoa-free list must be one of the four named compounds.
  **1456 mutations, all 1456 caught.**

### Sweets in boxes: no bar and no shell, so the filling decides (`lt/31`, SH-12-4-14)

The same file's second shelf, `/sweets/sweets-in-boxes/c/SH-12-4-14`, carries **56 rows over 12 archetypes,
7 A / 49 B / 0 C** — bringing `lt/31` to 112 rows over 22 archetypes, 20 A / 90 B / 2 C. **The code reads like
a child of SH-12-4 and the site does not treat it as one**: the listing pages name SH-12-4-14 and SH-12 and
never SH-12-4, and the URL puts it directly under `/sweets/` beside the dragée shelf rather than inside it.
Worth knowing before trusting a `SH-a-b-c` code to mean a descendant of `SH-a-b`. A leaf otherwise, every card
declaring SH-12-4-14 — the sixth shelf running where the gtm code and the URL slug agree product for product —
56 on page one and nothing after, 56 distinct ids and URLs, none shared with the 56 rows already in the file,
with `lt/30` or with any peer. The Lithuanian slug is *saldainiai-dėžutėse* and the Russian
*konfety-v-korobkah*, both saying what the English one says.

**Where the two chocolate shelves were decided by the shape of the block and the dragée shelf by the coating,
a box of chocolates is decided by the filling.** Every piece is a chocolate shell round something, so neither
earlier question arises, and SAFAD splits the filled-chocolate confectionery rows exactly once — on the nut:

| the list names | the row |
| --- | --- |
| a nut or a nut filling anywhere | `Chocolate confectionery, filled with nuts and/or praline` |
| no nut at all | `Chocolate confectionery or bar, with dairy filling` |

That is the whole rule for 36 of the 56 cards. The two rows sit at 2330 kJ against 2300 and 34,7 g of fat
against 34,2, so the split costs almost nothing in numbers and buys the one distinction SAFAD actually models.
**The trace warning has to come out of the list first** — *gali būti RIEŠUTŲ pėdsakų* is not an ingredient, and
two cards carry it over a filling with no nut in it.

- **A nut in the coating is not a nut in the filling, and a bare sugar entry is what tells them apart.** Three
  cards are a nut with chocolate on it rather than chocolate with a nut in it — 33% almonds under 64% white
  chocolate, 32,5% almonds under 61% milk chocolate, 51% walnuts under a 49% cocoa glaze — and they take the
  chocolate-type nut rows the dragée half established. FERRERO ROCHER declares its nut at 28,5% too and is not
  one of them, because its list *continues*: sugar, palm oil, wheat flour. **A bare sugar or a bare vegetable
  fat standing beside the nut is the praline; a card that names the chocolate, the nut and nothing more has no
  filling at all.** ROCHER then lands on the one row SAFAD wrote for that shape,
  `Chocolate and nut confectionery filled with praline`, where the nut is the confection and not only the
  cream — the row `lt/30` reached from the other end, for a crushed almond brittle.
- **A percentage inside a bracket belongs to the filling, not to the pack.** SECRET GARDEN declares a 6,2%
  hazelnut filling whose own nut content is 50%, and ČIURLIONIS a 6% marzipan that is 52% almond. Reading
  either number as a pack share would put a filled bonbon on a nut-coating row. Any rule with a percentage
  threshold in it has to split the list on depth first.
- **The least reachable shelf the corpus has taken: 37 of 56 cards name a brand, a box and a weight and
  nothing more.** ROCHER, TOFFIFEE, MERCI RED, RŪTA 1913, SECRET GARDEN, KARL FAZER SELECTION — and on a box of
  chocolates the filling *is* the mapping. **Twenty-three rows would have graded A on their numbers and are
  held at B** because the name does not carry the thing the row is named for; only seven keep an A, and every
  one of them has the food in its name. Two names actively mislead: GEISHA CARAMEL & SEA SALT is a hazelnut
  cream with a caramel flavour, and the FAZER blueberry box is a cream with 0,4% dried blueberries in it.
- **One panel prints an energy its own kcal denies.** LAIMA Lietuva 360 g prints **1500 kJ beside 465 kcal**;
  465 kcal is 1946 kJ, its own macros give 1947, and the VILNIUS 360 g box carries the same ingredient list and
  the same six macros and prints 1945. Taken literally the typo drives the row to C on a 35% energy gap that
  does not exist. The row is graded on the corrected energy with an explicit letter and the Comment says so —
  **the corpus still records what the pack prints**, and `check()` asserts both that the printed figure gives C
  and that the corrected one gives B, so the override cannot rot silently.
- **The panels otherwise: four of 56 carry something wrong.** Besides the energy above, two packs overrun
  100 g of macronutrients by 2,0 g and 1,0 g, and the RŪTA 170 g box prints **0 for both saturates and sugars**
  beside 36,4 g of fat and 45,5 g of carbohydrate. Neither touches the grade, which is taken on energy and fat.
- **Name drift: 8 of 56 cards (14%) match the `h1` exactly, 26 (46%) once spacing and case are ignored** — the
  worst of any shelf so far — and **four drops change the archetype**: KOTRYNA loses *su migdolais … ir
  pieniniu šokoladu*, BARBORA loses *su graikiniais riešutais ir kakaviniu glaistu*, RIGA BLACK BALSAM loses
  *su kreminiu* and the FAZER box loses *triufelių įdaru*. Per-shelf severe counts now read
  2, 2, 2, 2, 0, 4, 6, 4, 6, 4, 1, **4**.
- **`check()` re-derives instead of reading back.** `family()` derives all 56 keys again from the fat cell, the
  card's own noun and the ingredient list, in this order: a fat cell under 2 g, the one card whose noun is
  *šokoladukai* and not *saldainiai*, a card that declares no composition, peppermint oil under 20 g of fat, a
  sugar-syrup body carrying spirit, coconut as the head of the first entry, a declared wafer, a top-level nut
  share of 25% or more, a dried fruit at 5% or more, and last the nut/no-nut split. The class letter is tied to
  named evidence — `'B'` if and only if the id is in `_CAPPED` or `_BADENERGY` — and **a cap must bind**: a row
  the numbers already hold at B is measured, not capped, and listing it would overstate what the names cost.
  **1064 mutations, all 1064 caught.**

### Other sweets: the residue shelf, where the question is whether it is a confection at all (`lt/31`, SH2-24-134)

The same file's third shelf, `/sweets/other-sweets/c/SH2-24-134`, holds 42 products and contributes **39 rows
over 10 archetypes, 18 A / 13 B / 8 C** — bringing `lt/31` to 151 rows over 27 archetypes, 38 A / 103 B / 10 C.
**The three the shelf holds and the file does not are its chewing gums**, the first products in the corpus to be
dropped rather than mapped; the mechanism and the reason are under *Dropping a product* above. A leaf, every card
declaring SH2-24-134 (the seventh shelf running where the gtm code and the URL slug agree product for product),
42 on page one and nothing after, 42 distinct ids and URLs, none shared with the 112 rows already in the file or
with any peer. The Lithuanian slug is *kiti-saldumynai* and the Russian *drugie-sladosti*, both saying what the
English one says.

**The shelf code carries no hierarchy at all — this is the stronger form of the SH-12-4-14 warning above.** The
breadcrumb reads Sweets and snacks / Sweets / Other sweets, and the three codes behind those three words are
**SH-23, SH-12 and SH2-24-134**: not one is a prefix of the code below it, and the leaf is not even in the same
namespace (`SH2`, not `SH`). A Rimi shelf code is an opaque identifier; the breadcrumb and the URL path are the
only statements of where a shelf sits.

**Three shelves under Sweets each turned on one layer — the shape of the block, the coating, the filling — and
this one has no layer to turn on, because it is the residue.** It is what was left once the bars, the dragées
and the boxes had been shelved, so the question that decides the row is the plainest one SAFAD asks: what is the
thing made of, and is it a confection at all. **Twelve of the 39 answer no.** Seven are a ground oilseed paste
and five are dried fruit with nothing added, and both families leave the confectionery branch or sit on a
surrogate inside it. The other twenty-seven are seventeen boiled sugar sweets, three jellies, a marshmallow,
two crisp caramels and four eggs with a toy in them.

- **Five names sell a sweet and the card declares fruit.** The BOB SNAIL packs are the only products here whose
  ingredient list is a fruit and a percentage and then stops: *mango 100 %*, or *obuoliai 70 %, kriaušės 30 %*.
  Three of the five are called *saldainiai*, which is the word that would put a model on `Jelly candy`; the right
  row is a dried fruit, with the head fruit deciding as it does on the dried-fruit shelf. The apple-based four
  take `Apple, dried` — `lt/15` files an all-apple pressed bar there already — and the mango pack has nowhere
  dried to go, so it is graded `'x'` on `Mango, pulp, raw` at 302 kJ against its 1407, as `lt/27`'s four
  dried-mango packs are. **The test that finds them is that the declared shares close to 100 with no second word
  in any entry**: anything with a syrup, an acid or a flavouring in the list fails it, and so does a list of
  foods whose shares do not add up, because then something unnamed makes up the rest.
- **Rimi's own product description settles the KINDER JOY row.** Both JOY cards declare wheat flour, wheat starch
  and raising agents beside the creams, and the page says *traškus vaflis, padengtas pienišku ir riešutiniu –
  kakaviniu kremu* — a crunchy wafer coated in a milky and nut-cocoa cream. So they take
  `Chocolate confectionery, filled with wafer` rather than the dairy-filling row, which fits the numbers as well
  or better and loses the wafer. **The letters are the same either way, which is why the description gets to
  decide**: where two rows are both defensible and both cost the same, the retailer's own prose is evidence and
  a coin toss is not.
- **A caramel with a fat phase is the soft-caramel row; a caramel without one is boiled sugar.** RŪTA's crisp
  caramel cubes carry palm-kernel, palm and shea fat and cocoa butter at 5,1 g and take `Soft caramel candy` at
  B, the same row and the same reason as the dragée shelf's boiled caramels. BAZOOKA's PUSH POP is called
  *karamelė* too and declares no fat at all, so it is a boiled sweet — *karamelė* in Lithuanian reaches both
  rows, which `lt/31`'s first half already showed from the other side.
- **Gelatine sets a jelly only where there is no fat phase to set instead.** That is what separates VIDAL's
  gummies, which name no food at all and are capped at B, from the caramel cubes, which also declare gelatine.
- **Seven of the 39 names cannot reach their row and six cost a letter.** Four are the eggs — *šokoladinis
  kiaušinis* says chocolate and never which chocolate or what is inside it, which is the MILKA cap of the box
  shelf again. Three are a generic *saldainiai* over a food the card does not name: PEZ's pressed tablet, BRAIN
  BLASTERZ's boiled sugar, VIDAL's gummies. **The seventh is already held at B by its own numbers and no cap is
  recorded for it** — a cap that does not bind would overstate what the names cost.
- **Two panels contradict themselves and neither changes a letter.** CHUPA CHUPS STRANGER THINGS prints 1643 kJ
  beside 198 kcal; 198 kcal is 828 kJ and the macros give 1626, so it is the kcal that is mistyped and the kJ
  the grading uses is sound. BRAIN BLASTERZ is the other way round — 1664 kJ beside 370 kcal, which is 1548,
  against macros giving 1574 — but that row is capped at B by its name in any case and the corrected energy
  would still grade A. Unlike `lt/31`'s LAIMA panel, neither is overridden; both are recorded in the Comment
  and asserted in `check()`.
- **Name drift: 23 of 39 cards (59%) match the `h1` exactly, 26 (67%) once spacing and case are ignored** — the
  best of any confectionery shelf — and **two drops change the archetype**, both BOB SNAIL packs that lose
  *Vaisiniai* and leave a bare *Saldainiai*, pointing at confectionery where the card is fruit. Per-shelf severe
  counts now read 2, 2, 2, 2, 0, 4, 6, 4, 6, 4, 1, 4, **2**.
- **`check()` re-derives instead of reading back.** `family()` derives all 39 keys again from the product name,
  the ingredient list and the fat cell, in this order: the word *chalva*, a list that is foods and shares
  closing at 100, the word *zefyr*, a fat cell at 20 g or more (then wheat for the wafer,
  a declared chocolate for the filled bar, otherwise the plain bar), *žel* or gelatine under 2 g of fat, and
  *karamel* with a fat phase; everything left is boiled sugar. The class letter is tied to named evidence —
  `'B'` if and only if the id is in `_CAPPED`, `'x'` if and only if it is in `_RAWONLY` — every cap must bind,
  both contradictory panels are asserted still to print the figures quoted above, and any card whose name says
  *kramt* must be in `DROP`, so a fourth chewing gum on the shelf fails the build instead of being filed as a
  boiled sweet. **663 mutations, all 663 caught.**

### Gummy candy: the first shelf a row was actually written for (`lt/31`, SH-12-43)

The same file's fourth shelf, `/sweets/gummy-candy/c/SH-12-43`, carries **118 rows over 2 archetypes,
81 A / 24 B / 13 C** — bringing `lt/31` to 269 rows over 27 archetypes, 119 A / 127 B / 23 C, the largest file in
the corpus. A leaf, every card declaring SH-12-43 (the eighth shelf running where the gtm code and the URL slug
agree product for product), 100 on page one and 18 on page two with nothing after, 118 distinct ids and URLs, none
shared with the 151 rows already in the file or with any peer. The Lithuanian slug is *guminukai* and the Russian
*marmeladnye-konfety*.

**Every other confectionery shelf here has had to borrow, and this one borrows nothing.** The boxed chocolates
took the mint row for want of a truffle or a liqueur row, the halva took `Nougat`, the chewing gums had no row at
all and were dropped. `Confectionery (non-chocolate) / Jelly candies / Jelly candy` is a sugar-and-glucose gel set
with gelatine, pectin or starch, which is exactly what 103 of these 118 products are; the other 15 are chews and
land on the one row SAFAD gives a chewed sugar-and-fat sweet. **Two rows carry a 118-product shelf with no
surrogates and no gaps to register** — the first time that has happened.

- **The line between the two rows is the fat phase, not the name.** *Kramtomieji saldainiai*, chewy sweets, sits
  on both sides of it. A **gummy** is a gel: gelatine, pectin or starch is the structure and any fat is a surface
  polish or rides in a wheat-flour body. A **chew** is sugar and glucose syrup cooked with a real fat phase and no
  cereal — palm, palm kernel or coconut, 3 to 9,6 g of it, third or fourth in the list. So the test is *fat at 3 g
  or more with no wheat flour declared*. It is not a numeric convenience: the wheat-flour belts carry as much fat
  as the lighter chews and are not chews, because their flour and starch do the work the fat does in a MAMBA.
- **`Soft caramel candy` is the branch's only chewed-sweet-with-fat row, and it is calibrated on a butter
  caramel.** MAMBA, CHEWITS, FRITT, CAPRI SUN and the RIMI own-brand chew sit 3 to 7 g under its 9,98 g of fat and
  grade B — the same price `lt/31`'s first half already paid for WERTHER'S. Fourteen of the fifteen are held there
  by their numbers; only FRITT, at 9,6 g, would have taken an A and is capped, because no chew is sold as a caramel.
- **The wheat-flour belts pay for their row.** Twenty-two cards — belts, pencils, sticks, tubes, PICO BALLA — are
  built on wheat flour and wheat or corn starch, nineteen of them carrying 1 to 4,9 g of palm or shea fat with it,
  and they are gummies by every other test: the shelf, the name, the gelatine further down the list. On a row
  calibrated at 0,53 g of fat, anything over 2,6 g grades C, so **twelve of the thirteen C's are that one
  recipe**. Routing them to the chew row
  would grade PICO BALLA B and would be wrong: a filled gummy ball is not a caramel.
- **Fruit with a gelling agent is a sweet; fruit without one is a dried fruit.** The three KOSSA FRUIT packs
  declare no sugar and no glucose syrup at all — 60 % apple puree, 32 % concentrated apple juice, 5 % of a second
  juice, pectin and an acid. That is the shape of the BOB SNAIL rolls two shelves up, which are fruit and nothing
  else and take a dried fruit of their head species. The pectin is the whole difference, and the rule the other
  shelf already uses — *a list that is foods and shares closing to 100, with no other entry* — separates them with
  no new test invented.
- **One card declares a fat it does not name.** ZOZOLE STICKS prints 4 g of fat and its list names no fat and no
  cereal, while its four siblings of the same range, weight and format all declare wheat flour and palm fat. The
  card is abridged, not a different recipe, so it is filed with its siblings and named in `_ABRIDGED`; `check()`
  asserts it is the only card on the shelf that declares fat while naming none.
- **Reachability is the best of any confectionery shelf**: 96 of 118 names carry *guminukai* or *želė* and reach
  the row directly. Fifteen of the 22 that do not are the chews, which no name could get to a caramel row; six
  rows are capped, and only one of those six is capped by an abbreviation of the card — RED BAND SWEDISH FISH,
  whose `h1` says *žėlė saldainiai* and whose card says only *saldainiai*.
- **The panels are clean, with one flag**: both CAPRI SUN packs report 93% of their fat as saturated, above palm
  kernel's own 82% and not impossible for a palm-kernel and coconut blend. Grading uses energy and fat, so it
  moves no letter; it is recorded and asserted rather than corrected.
- **Name drift: 63 of 118 cards (53%) match the `h1` exactly, 77 (65%) once spacing and case are ignored**, and
  **two drops change the archetype** — SWEDISH FISH and TUTTI FRUTTI TWISTS both lose *želė* and are left saying
  only *saldainiai* or *kramtomieji saldainiai*. Per-shelf severe counts now read
  2, 2, 2, 2, 0, 4, 6, 4, 6, 4, 1, 4, 2, **2**.
- **`check()` re-derives instead of reading back.** `family()` derives all 118 keys from the fat cell, the wheat
  the card names and the one named exception. Beyond that it asserts the abridged card is unique, that the
  fruit-and-pectin cards really declare no sugar, that both saturates flags still print what is quoted above, and
  — new here — **that no row graded A has a name that cannot reach its row**, which is the reachability rule
  checked mechanically rather than by eye. **1062 mutations, all 1062 caught.**

### The VIKIS farm store: a vendor shelf with a category tree of its own (`lt/32`, SH-18)

`/-vikis-farm-store/c/SH-18` is a new file, **113 rows over 59 archetypes, 30 A / 55 B / 28 C**, and the thinnest
spread in the corpus: one archetype for every 1,9 rows, where `lt/31` carries one for every ten. Eight children
summing to the parent exactly — frozen food 20, prepared meat 28 and fish 2, groceries 20, sweets 9, snacks 22,
cheese and eggs 11, bread 1, dairy 0 — over two listing pages with a third that returns nothing, 113 distinct ids
and URLs.

- **A vendor shelf can be a real category tree rather than a label laid across the shop, and which of the two it
  is has to be measured rather than assumed.** `vegan-products/SH-17` gathers 174 products that are shelved
  somewhere else and 11 of them carry a code from another tree; here every one of the 113 has its primary URL path
  under `-vikis-farm-store/`, and the diff against all 57 existing ground-truth files finds **0 shared Source URLs
  and 0 shared ids**. A farm store inside a supermarket keeps its own stock, so nothing it sells collides with a
  shelf already built — which is the opposite of what the diet-label shelf taught, and the same URL diff is what
  decides it either way.
- **A child can carry a code whose root does not exist at all.** Three of the eight — `SH-48-3` dairy-products,
  `SH-48-30` cheese-eggs, `SH-48-36` bread-products-and-confectionery — are numbered off `SH-48`, and
  `.../zzz/c/SH-48` answers **404**: not an empty category, not a foreign parent, nothing. Earlier shelves found
  codes numbered off a parent that existed but was not theirs (`SH-9-8-24` under fresh-poultry, `SH-9-16-49` under
  marinated fish); this is the stronger form. The redirect sweep settles it, because it returns the canonical slug
  and all three resolve into this tree. **A Rimi shelf code carries no hierarchy at all** — only the breadcrumb
  and the URL path do.
- **The worst panel fault rate yet recorded: 18 of 113, 15,9%**, against 6,1% on frozen gastronomy and 4,2% on the
  meat shelf. The producer field says why: these are farms and one-line manufacturers typing their own labels.
  `extract_det.py` flags thirteen; five more need a pairing or an ingredient list — a placeholder panel on a pack
  of eggs (4 kJ) and another on a pack of dried dandelion root (1 kJ), both all-zero and so invisible to the
  placeholder check, which requires a repeated *non-zero* figure; a beetroot salad declaring 10 g of fat over a
  list that names no fat and 0 g of sugars over one that names sugar; and two biscuits declaring 0 g of saturates
  over butter and oil.
- **One panel belongs to another product, and only the pairing can see it.** A pork roll of tongues and ears
  (4008784) prints 37,4 g of fat, 15,6 g of saturates, 0,9 g of carbohydrate, 0,6 g of sugars, 13,8 g of protein
  and 2,0 g of salt — the same six figures to the digit as its own producer's KAZIO sausage (4008798) — under an
  energy of 658 kJ that contradicts every one of them. Nothing in it can be used, so that row takes an explicit
  letter on its name alone. Same shape as the RESTERA curd pair on `lt/14` and the ice-cream record carrying
  another product's ingredient block: **internal consistency is not evidence of a good panel, and a sibling
  comparison is the only check that sees this class of fault.**
- **Where the energy cell is provably wrong and the rest of the panel is sound, the letter is the one the sound
  cells earn.** The fish shelf established that a broken panel inherits its *sibling's* letter; this shelf needed
  the general form, because four of these packs have no sibling and the recovery is unambiguous — the macros and
  the kcal cell agree with each other and only the kJ cell is out. A retailer's typo is not evidence about the
  mapping, which is what the letter measures. The recovered figure is still stated in Comments and never fed to
  the grader: the explicit letter says what the sound half of the panel buys, `_RECOVERED` records the arithmetic
  and `check()` recomputes it. Six rows take an explicit letter this way (a hot-smoked sausage at 414 kJ over
  macros worth 1336, venison dumplings at 110 over 1087, three marmalade sticks at 2481-2712 over 1423-1577, and a
  garlic bread whose macros sum to 127,8 g in 100 g); one has no usable panel at all; **the other eleven are graded
  exactly as printed, because there the printed figure and the recovered one buy the same letter.**
- **The pressed-cheese rule of `lt/01` reproduced without exception.** The fresh-cheese L3 stops at 9,9 g of fat
  for its plain rows, so a Lithuanian pressed cheese takes the row from the ripened branch that its own fat
  matches, and the type word does not choose: 13 to 17,5 g → `Mozzarella cheese, from cow's milk` (17,7), 22,8 →
  `Feta-type cheese from cow's milk` (22,6), 27 → `Firm cheese, around 27% fat, Maasdam-type cheese` (28,4).
  Eleven packs, every one landing where `lt/01`'s eleven did, and *saldaus pieno sūris* (rennet-set) and *varškės
  sūris* (acid-set) mix freely across the three rows. The one thing the Feta row asserts and these packs deny is
  brine — 0,9 g of salt where it stores 2,51 — and smoking, baking, drying and caraway are axes no cheese row
  carries.
- **The meat vocabulary of `lt/07` reproduced, with two products it has no rule for.** Twenty-six of the 28 meat
  packs resolve on the processing words alone. The two that do not are a **baked** belly roll, where `lt/07` has
  only smoked ones (`Lardoons, cooked` is the cooked belly row that does not also assert smoke), and the
  ear-and-tongue roll, which is brawn under a name that says neither.
- **What this shelf has no row for**, all of it the farm end of the range: **roasted acorn, dandelion-root and
  burdock coffee** (the coffee-imitates branch is chicory and nothing else, and both of its rows carry the same
  figures for the powder and for the drink), **sea buckthorn** (absent from the database, berry and preserve
  alike), **a caramelised-onion condiment** (no chutney, no confit, no vegetable jam anywhere), **an unfried
  potato snack** (both potato-snack rows are fried, at 23 and 28,8 g of fat), **a šakotis** and every other
  spit-baked cake, and **a pickled beetroot salad**. It also re-confirms two gaps already registered: the **curd
  fritter** (`lt/14`) and **dried meat** (`lt/07`).
- **Name drift: 50 of 113 cards (44%) match the `h1` exactly** — the lowest rate of any shelf, because this
  retailer abbreviates a farm's long product names hard — and **three drops change the archetype**. All three are
  rows this file had already capped to C for exactly that reason: a chicken roll whose card says only *Virtas
  vyniotinis su spanguolėmis* where the `h1` says *vištienos*; the ear-and-tongue roll, whose `h1` names both; and
  a beer snack whose `h1` says *Keptos duonos užkandėlė* and names the fried bread. **The h1 would rescue every
  row the card's name loses on this shelf**, which is the strongest evidence yet for the open question about
  recording `h1#product_name` instead. Per-shelf severe counts now read 2, 2, 2, 2, 0, 4, 6, 4, 6, 4, 1, 4, 2, 2,
  **3**.
- **What `check()` can prove on a heterogeneous shelf, and where it stops.** A single-food shelf can re-derive
  every key from the card (`lt/31`'s gummy shelf catches 1062 mutations out of 1062); 59 archetypes over eleven
  SAFAD L1s cannot. What the card *can* settle is the branch, and that is what is asserted: no meat word on the
  card, no meat row; the fish rows are exactly the cards naming fish; the cheese rows are exactly the milk-and-salt
  cards; a meat-led card stays in the meat or the animal-fat branch, and back fat leading it is the fat row; a
  one-entry card is the food its one word names; a fruit-and-sugar card is the jam of its fruit; a gel with no
  flour in it is a jelly candy; fried bread with garlic is a crouton; a flour or a groat extruded with no fat is a
  grain cake; the seventeen dumplings key on the filling and the ten cheeses on the fat cell. Add the class rules
  (declared only where one entry names the food, forced only for the groats, the cut class only for the belly and
  the back fat) and the reachability rule (no row graded A whose name cannot reach it), and **6286 of 7458
  mutations are caught, 84,3%**. The 1172 that slip are all within-branch — which sausage row, which biscuit row —
  and no rule written from an ingredient list can settle those.

### The Honest Bite store: the vendor-shelf test survives its second use (`lt/33`, SH-30)

`/honest-bite-prekiu-krautuvele/c/SH-30` is a new file, **87 rows over 34 archetypes, 12 A / 45 B / 28 C and 2
blank**. Four children — `SH-3011` 6, `SH-3212` 32, `SH-3213` 10, `SH-32015` 35 — pairwise disjoint and summing
to 83, with four DEAR BABY packs sitting on the parent and appearing in no child at all; the brand facet on the
parent listing sums independently to the same 87.

- **The URL test that settled `lt/32` gives the same answer here, which is what makes it a test.** All 87 primary
  URLs run through `honest-bite-prekiu-krautuvele/`, and against the 58 files built before it the intersection is
  **0 shared URLs and 0 shared ids** — the opposite of `vegan-products/SH-17`, which gathers 174 products shelved
  elsewhere. Two vendor stores, two real trees; the diet-label shelves remain the overlays.
- **A shelf code is an opaque key, and this shelf says so more plainly than `lt/32` did.** The children are
  `SH-3011`, `SH-3212`, `SH-3213`, `SH-32015` — they do not even have the shape of children of `SH-30` — and
  `SH-32`, `SH-320` and `SH-30-1` are all 404. Only `SH-3011` has a slug of its own; the other three redirect to
  the bare parent path with the code appended. Probe the code, read the canonical slug out of the redirect, and
  never infer a parent from a prefix.
- **On a polyol-and-fibre shelf the automatic macro check is not evidence of a fault, and must be re-run with the
  legal factors before anything is called broken.** An Atwater check costs carbohydrate at 17 kJ/g; Annex XIV of
  1169/2011 costs polyols at 10, fibre at 8 and erythritol at 0. Nineteen of these 87 packs trip the check and
  **seventeen are reading correctly**: 99,8 g of xylitol at 10 is the 1000 kJ printed to the kilojoule, 99,8 g of
  erythritol at 0 is the 1 kJ printed, and the psyllium, inulin and coconut-flour packs carry their bulk as fibre
  outside the carbohydrate line. `lt/15` wrote this as a per-row note; it is a shelf-wide rule.
- **One panel survives the re-run, and one is the cleanest rate in the corpus.** `1019267`, the matcha protein
  bar, prints 1788 kJ beside 426 kcal — the two agree with each other — over macros that buy 1370, with no polyol
  and no fibre-bearing ingredient on the label that could close 418 kJ inside 72,4 g of declared macros. **1 fault
  in 87 panels (1,1%)**, against `lt/32`'s 18 in 113 (15,9%). The letter does not move (+11,8% printed, −14,4%
  recovered, both inside A on a row its class caps anyway), so it is graded as printed, per the rule `lt/32` wrote.
- **Pairing finds an identical panel across two products and composition says it does not matter.** The almond oil
  and the hazelnut oil carry byte-identical cells: 3700 kJ, 900 kcal, 100 g fat, 8 g saturates. On `lt/32` that
  pattern was a fault, because the two foods were a sausage and a pork roll. Here both are 100% cold-pressed nut
  oil, real almond oil runs about 8,2 g of saturates and hazelnut about 7,4, and a rounded 8 is defensible for
  both. **The pairing test finds candidates; only composition can promote one to a fault.**
- **A card fault the ingredient list catches and nothing else would: the three LOCCO stevia drops have their
  flavourings rotated by one.** The caramel card lists coconut flavouring, the coconut card lists vanilla and the
  vanilla card lists caramel — a closed three-cycle. Nothing about the mapping turns on it (all three are the same
  5,5% steviol glycosides in glycerol), which is exactly why it would never have been noticed from the archetypes.
- **Name drift is the worst measured anywhere: 13 of 87 cards match the `h1` (15%),** against 44% on `lt/32` and
  a corpus norm well above that. Rimi abbreviates almost every card here. **Four of the drops change the
  archetype's reachability and therefore the letter**, and all four are rows this file had to cap: three pastas
  (`DINOZAURAI`, `DITALI`, the tomato-carrot-spinach one) whose cards name neither a grain nor the gluten-free
  claim while every one of their `h1`s names both, and the BIOPONT millet balls whose card omits the peanuts the
  `h1` names and the row turns on. Per-shelf severe counts now read
  2, 2, 2, 2, 0, 4, 6, 4, 6, 4, 1, 4, 2, 2, 3, **4**.
- **`check()` catches every mutation this file admits: 3136 of 3136.** That is the first 100% on a heterogeneous
  shelf, and it is worth saying what made it possible. 34 archetypes is small enough that every family has a card
  word or an ingredient rule, and both directions of each are asserted: *krekeriai* is a cracker and only a
  cracker row may be a *krekeriai* card; *juost.* or *lazdelė* is a fruit paste and must be set with pectin and
  carry no flour and no oil; a *košė* card is one whole cereal and the row is the grain the name states; a
  *miltai* card is the nut it names. The extruded family — five rows that no card word separates — is settled by
  the list instead: sea salt marks the salty puffs, peanuts pick between the two maize-puff rows, agave (a syrup,
  which adds mass) marks the crunchy-muesli reading where steviol glycosides (dosed in milligrams) do not, and the
  one list that is nothing but flour is the fat-free textured bread. A bar is the chocolate row when its list
  *leads* on caramel **and** its glaze names cocoa mass — which is what puts the SALTED CARAMEL bar, caramel-led
  with a cocoa-butter-and-erythritol glaze, on the nut row beside its siblings. Six rows that only one product can
  take are pinned by id in both directions. One class rule covers every row (`x` for the three absent forms, an
  explicit `B` for the reachability caps and the sweetener gaps, `s` for the bars carrying a protein isolate, `n`
  otherwise), and a blank row must carry no class at all.
- **`lt/32`'s reachability rule did most of the capping.** Seven rows whose numbers earn A are graded B because
  the display name does not name the food the row does: three gluten-free pastas named only by shape, two puffed
  snacks whose row says maize where the card says lentil or millet, a crunchy-muesli reading under a *rutuliukai*
  card, and the DINOZAURAI pasta. `check()` asserts it mechanically — an A is allowed only on the seven keys whose
  names can reach them, and a gluten-free-pasta A additionally needs a grain word or the claim in the name.

### Ice cream and frozen desserts

**SAFAD keys its ice cream on FORMAT, and the shelf names flavour.** The whole vocabulary
is `Snacks, desserts, and other foods / Ices and desserts`, whose two relevant L3s are
`Ice cream, milk-based` (16 rows) and `Sorbet` (5 rows). The 16 are not flavours: they are
`in box`, `luxury, in box`, `in individual cup`, `luxury, in cup`, `in cup for children`,
`cone (normal size)`, `cone, mini`, `chocolate coated`, `on stick with chocolate coating`,
`Ice lolly`, plus six composed restaurant desserts (baked Alaska, Sundae, Belle Helene,
vacherin, a goblet with whipped cream, an ice cream log). There is **no vanilla row, no
chocolate row and no strawberry row anywhere** — flavour is not an axis in this branch, so
stop looking for one and read the pack's shape instead.

That inverts the usual problem. On `lt/13` (228 rows) **164 of the 242 names on the shelf
carry no format word at all**: they are flavour, brand and volume, like
`Valg. ledai MAGNUM LA PISTACHE, 90 ml / 70 g`. What makes the file possible is that the
**volume is in the name**, so the box-versus-portion split — which is exactly the split
SAFAD's two commonest rows encode — is name-reachable without a format word.

**Read a multipack's UNIT size, not its total.** `Valg. ledų rinkinys HAAGEN DAZS,
4x95ml/ 326g` is four individual cups: on `Ice cream, in individual cup` it is +6% on
energy and inside the fat floor, i.e. A, where its 380 ml total would have put it on
`Ice cream, in box` 29% away. Same for `6 x 50ml`, `10x40ml`, `12x17ml`. `800 + 200 ml`
is the opposite shape and adds — one 1 l tub sold with a bonus. And read the volume off
the **raw** name: folding the decimal comma turns `0,5 l` into `0 5 l`, which a litre
pattern reads as five litres.

**A format is not a qualifier, so the pack may choose between plain format rows; a grade
or a coating is a qualifier, so it may not.** This is the governing rule's own licence
("tie-breaking between equally plain alternatives") applied to a branch that is nothing but
format rows. Concretely:

- Under **600 kJ per 100 g** nothing in this branch is an ice cream, so the pack is a water
  ice and takes `Ice lolly` whatever the name calls it. That is a choice between two plain
  named foods, not an attribute the name withheld. It routes 10 further packs that say only
  `valgomieji ledai` plus a fruit flavour, and the group grades well: 13 of the 23 lolly
  rows are A, median 364 kJ against the row's 356.
- **`luxury` needs a word, and on this shelf exactly one word supplies it: *plombyras***
  (*plombir*), the Lithuanian/Soviet term for the high-fat premium grade. 30 rows reach
  `Ice cream, luxury, in cup` or `luxury, in box` that way, 9 of them A. The premium
  *brands* cannot: Ben & Jerry's, Häagen-Dazs, Magnum and Barattolino name no grade, so
  their tubs sit on plain `Ice cream, in box` (800 kJ, 8,4 g fat) at +32% to +59% and grade
  C — ten of them. That is the mayonnaise rule (a name that declares no fat content takes
  the family's plain row, and the C records the pack) reappearing on a different axis.
- **`chocolate coated` needs the glaze in the name.** 11 names give it — *glaistytas*,
  *gl.*, *glais.*, *kakav. gl.*, *šok. gl.*, ESKIMO — and they fit `Ice cream, chocolate
  coated` well (median 1322 kJ against 1480, 4 A and 7 B). But **52 packs on this shelf
  carry 18 g of fat or more, and only 13 of their names mention a coating**; the other 39
  are coated Magnums, Milkas, Oreos and Snickers whose names say nothing, so they take the
  plain portion row at +38% to +75% and grade C. **31 of the file's 57 C rows are that one
  cause**, and another 10 are the missing `luxury`: 41 of 57 C rows are a name that
  withholds what the pack is.

**A glaze word does not imply chocolate — check the pack.** Three of the sixteen
glaze-bearing names name a *fruit* glaze (`su mangų gl.`, `su pers. glaistu`), and a
fourth, `Citrin. valg. ledai su glais. POLS`, names none at all and turns out to carry a
**lemon** glaze: 23% of coconut, rapeseed and shea oil with sugar, lemon powder and lutein,
and no cocoa anywhere. So the coated row is denied by the pack as well as unnamed, and all
four take the plain portion row with their 5-20 g of coating fat showing up as the letter.
The abbreviation matters too: `glaist` does not match `glais.`, and the stem to match is
**`glais`**.

**Three Lithuanian words for a frozen dessert, and each resolves differently:**

- ***plombyras*** is a grade, not a format — it appears on tubs, portions, waffle cups,
  cones and biscuit sandwiches — so it must be read *after* the format words. Order the
  table cone-before-plombyras and sandwich-before-plombyras or
  `Vanil. plombyras vaflin. kūg. POLS` lands on a cup row.
- ***šerbetas*** is a sherbet, and on this shelf it is usually **dairy**: cream 6%, milk
  powder, or milk protein plus coconut oil, on top of fruit juice. It still belongs in the
  `Sorbet` L3 because that is the family the name names, but it runs 1,9-5,2 g of fat
  against `Sorbet, in box`'s 0,56 and 572-693 kJ against its 519 — five of the ten sorbet
  rows are C for that reason alone.
- ***sorbetas*** is the real thing, and the two ends of it are both C. `Mangų sorbetas
  KLASIKA` (water, 25% mango purée, sugar) is A at 447 kJ against 519. The two SUN365
  packs are **unsweetened 100%-fruit purée and juice** at 285 and 234 kJ — 45% and 55%
  *below* the row, because SAFAD's sorbet is a sugared one. A 100%-fruit frozen pack is
  the one place in this branch where the miss is downward.

**`Ice lolly` is a low-sugar row and the shelf's water ices sit above it.** 19,4 g of
sugar and 356 kJ; the Lithuanian juice and soft-drink ices run 326-534 kJ. Nineteen of
twenty-three are within 30% and fine, but a pear water ice with a 6% wafer (534 kJ) and an
iced-tea multipack (515 kJ) are C. Note also that **anything with a wafer cannot be A on
this row**: `FAT_BASE_G` is 2,0 g, so 2 g of wafer fat over the row's 0,17 g is already a
100% fat penalty.

**Frozen yogurt exists, and not in the ices branch.** `Frozen yogurt` sits under
`Milk and dairy products / Fermented milk products / Yoghurt, cow milk, plain` at 533 kJ
and 2,2 g fat. Two KRI KRI packs that name Greek yoghurt take it at B. But a
**yoghurt or kefir *flavour* is not a yoghurt claim**: `Kefyro sk. ledai ICETOPIA` is
whole milk at 12,3 g fat and belongs on an ice cream row, 78% above the frozen-yogurt one.

**Gaps in this branch, all taken as the nearest format row rather than blank:** no ice
cream **sandwich** row (three *tarp sausainių* packs and the sandwich-shaped multipack take
`Ice cream, cone (normal size)`, capped at an explicit B — it is the only row that models
ice cream inside a baked wheat carrier, and it fits the numbers almost exactly at 1272 kJ
and 14,0 g fat against 1220 and 13,5); no **mochi** row (the glutinous-rice shell is about
a third of a WAO MOCHI pack and has no counterpart anywhere, so the row stands for the
filling alone); no **reduced-sugar or sweetener-sweetened** ice cream row; and no **frozen
plant-based dessert** row at all — see the vegan rules, where both markets put them on the
sorbet rows.

**A diet-label shelf gets there first: 14 of the 242 products are already in `lt/11`.**
`vegan-products/SH-77` gathered every plant-based frozen dessert on this shelf, so `lt/13`
maps 228 and `helperscripts/rimi/split_ic.py` drops the other 14 **by matching lt/11's own
Source URLs**, not by a hand-typed id list. This is the first time the overlap CLAUDE.md
predicted for a diet-label category has actually fired, and the URL-diff assertion in
`build_gt.py` is what found it. Worth knowing that the two files disagree in *method* and
that the earlier one is right to stand: `lt/11` (and `et/11` before it) put vegan frozen
desserts on `Sorbet, in box` / `Sorbet, on stick` at C rather than blanking them, because
sorbet is the one family in the ices branch that asserts no dairy, and a dairy-free row
at C beats no row at all.

**No cross-market control is available for this category.** `rimi/et` has no ice-cream
shelf, and the 228 rows share no product id with any Estonian file. The one adjacent
question does agree across the two markets: `et/11` puts 15 vegan frozen desserts on the
same two sorbet rows that `lt/11` uses.

**Retailer data errors, fourteen flags on 242 panels (5,8%), of which ten are faults.**
Five are saturates cells repeating the total fat (see the saturates note in the
working method — on this shelf the check's yield is 5 real against 4 honest coconut, midway
between the dairy shelves' near-100% and the plant-based shelf's near-10%). Four are a
**kcal cell that disagrees with a sound kJ cell** — 1395 kJ beside 361 kcal, 1211 beside
319, 1198 beside 312, 435 beside 308 — and in all four the macros confirm the kJ, which is
the half `grade.py` uses. One is the pair-consistent fault the macro check exists for:
`Van. plombyras vafl. p. NYKŠTUKAS` prints **4105 kJ / 981 kcal**, which agree with each
other, over macros worth 979 kJ — the kJ figure was typed into the kcal cell and the kJ
cell recomputed from it. That is the whole of the automated yield: 5 + 4 + 1.

**Two more faults on this shelf no check fires on**, which puts the real fault rate at 12
of 242 (5,0%) against the automated 10. The first is a **record carrying another product's
ingredient block**: `Obuol. ir vyšnių šerbetas DADU` declares 3,0 g fat and 693 kJ, a
sherbet, above an ingredient list of milk 24%, cream 16%, Belgian chocolate 15% and a
waffle cone 14%, which would be nearer 1300 kJ. The numbers are the sherbet's; the
ingredient list is not.

The second is a fault shape no check in the file *could* see. `Valg. ledai M&MS ICE CONE`
declares 685 kJ / 164 kcal, 8,2 g fat and 20 g carbohydrate. The kJ/kcal pair agrees, the
macros agree with the pair, nothing is over 100 g and the saturates ratio is honest coconut
— and it is **half** of what the same maker's `SNICKERS ICE CONE` declares (1405 kJ, 19 g
fat, 35 g carbohydrate) on an ingredient list whose only difference is peanuts where this
one has cocoa. It is recorded on its own row and graded as printed. This is the case the
working method already names: the automated checks narrow the field, and reading a row
against its own siblings is still what finds the rest.

### Frozen gastronomy and confectionery

The Lithuanian `frozen-foods/frozen-gastronomic-and-confectionery-products` shelf (`lt/14`, 147 products across
all eight of its children: dumplings and ravioli 35, ready-to-eat 32, pizzas and snacks 27, vegan and vegetarian
14, frozen bakery 12, frozen dough 10, pancakes 10, potato dumplings 7).

**This shelf is the ice cream shelf's mirror image, and the pair is the clearest statement of what makes a
category mappable.** There SAFAD keyed the food on FORMAT (box, cup, cone, stick) while the retailer named the
FLAVOUR, so 164 of 242 names could not reach the row that fitted them and 41 of 57 C rows were a name withholding
what the pack was. Here SAFAD keys pizza on its TOPPING, ravioli on its FILLING, crepes on their FILLING and
frozen dough on its FAT — and the Lithuanian names state exactly those things, because Lithuanian shelf names put
the ingredient in the title: `Pica su pepperoni, sūriu`, `Koldūnai su kiauliena`, `Liet. blynai su kump.-sūr.
įd.`, `Sviest. mielinė tešla`. The result is measurable: **the 27 pizzas grade 16 A, 11 B and not one C**, and
the 10 frozen doughs 4 A and 6 B with no C either, against the ice cream shelf's 57 C rows in 228. SAFAD's pizza
L3 carries 18 topping rows and this shelf lands on 11 of them, which is the widest spread of one L3 anywhere in
the tree. The lesson is not that one shelf was written better than the other; it is that
**the retailer's naming convention and SAFAD's keying axis either agree or they do not, and how well a category
can be mapped is largely decided before any judgement is made.**

**A brand line written in capitals is not a food descriptor.** rimi.lt prints brands in capitals, and VIČI sells
its koldūnai as TRADITIONAL, GYOZA and KHINKALI lines. Those words are the line, not the dish: every pack so
labelled is a Lithuanian koldūnas by composition (848 to 1105 kJ, pork-and-beef or chicken fillings, boiled wheat
dough), while `Chinese specialty or dumplings` sits at 630 kJ over 1,4 g of fat — a far leaner food that no pack
on the shelf comes near. So all 31 meat koldūnai take
`Fresh pasta, stuffed with meat (e.g. bolognese-style ravioli), raw`, keyed on the filling the name states, and
one exception confirms the rule rather than breaking it: `Korėj. stil. koldūnai su BBQ mėsa VICI` carries its
Asian word in running lower-case text and is still a beef-and-cabbage koldūnas, with the Asian row further off on
both energy and fat.

**Name-reachability is per-language, so two language files MUST sometimes disagree about the same product.**
Product 297078 is `Koldūnai su daržovėmis VIČI` in Lithuanian and `Pelmeenid köögiviljadega Gyoza Viči` in
Estonian. The Estonian name carries the word *Gyoza* and `rimi/et/11` puts it on `Chinese specialty or dumplings`
at A, correctly. The Lithuanian name carries no Asian word at all, so the same row would be ground truth the
Lithuanian name cannot reach, and it takes `Fresh pasta, stuffed with vegetables (e.g. ravioli), raw`. **This is
the first proven case of the divergence the per-language directory structure exists for**, and it is worth
stating plainly: a cross-market disagreement is not automatically an error to reconcile — check the two names
first. The control also works the other way. Of the five ids this shelf shares with `rimi/et/11`, three agree
outright, one is this Gyoza divergence, and one is an Estonian row worth revisiting: `Cordon bleu vegan 220g`
sits on `Schnitzel, soybean and wheat-based (vegan)` where SAFAD holds
`Schnitzel, soybean, wheat and cheese-based, cordon bleu-style` and both names say cordon bleu.

**Where a name gives only a marketing line, the plainest row in the family is the answer and the letter records
the pack — but the letter must be set by hand.** Five pizzas name no topping at all (AMERICANA PASSIONATA,
AMERICANA MAFIA, FELICIANA, FELICIANA SPECIALE, RISTORANTE SPECIALE) and take
`Pizza, cheese and tomato or Margherita pizza`, which is both the plainest pizza row and, at 944 kJ against the
shelf's 989 kJ median, very near its centre. One of them — FELICIANA SPECIALE, +6% on energy and inside the fat
floor — would have graded A on the numbers alone, and that would be wrong: **a good numeric fit cannot confirm a
type the name never stated**, so all five carry an explicit B. `Speciale` is the trap worth
naming — it is a recognised pizza style (ham, mushroom and salami) and a well-informed annotator knows what is on
a Ristorante Speciale, but the word names no ingredient, and B is where domain knowledge that the name does not
supply belongs.

**`Potato dumpling, plain` is an L3 whose only product row is a filo fritter.** The L3 name is exactly what a
*cepelinas* is; `Fritter, filled with potatoes (filo pastry)` is exactly what one is not. This is the
L3-name-as-mapping-key rule (see the cross-cutting rules) in its purest form, and the numbers back it: across the
six cepelinai and didžkukuliai the protein agrees to within a gram (3,25 g on the row against 3,3 to 4,8 g on the
packs, potato-dominant on both sides) while the fat does not, because the row is deep-fried and these are boiled.
One pack — `Šald. šilkiniai bulvių kukuliai DAGERA`, a potato dumpling with no filling at all — is the L3's own
name to the letter.

**SAFAD's `Spring roll` is a FRESH roll.** 437 kJ over 1,82 g of fat is rice paper, not fried wheat pastry. Both
Lithuanian packs name themselves SPRING ROLL and sit 66% and 78% above it, and there is no second row to move to,
so they are graded `'f'` — which is exactly the class's purpose — and land at C. Do not go looking for a fried
spring roll row.

Three Lithuanian words this shelf turns on:

- ***koldūnai*** — boiled wheat-dough dumplings with a meat filling. The stuffed-pasta rows carry them; the fat
  is where they part company, because SAFAD has no axis for the *lašiniai* (pork back fat) that most Lithuanian
  fillings carry at 9 to 22% of the filling. Thirteen of the 31 grade C, and **ten of those thirteen are fat
  alone** — the pack sits within 30% on energy and two to three times the row on fat. The other three are a lean
  chicken filling at -33%, a chicken-and-cheese one at -36%, and the broken panel.
- ***virtiniai*** — the same dough with a potato or curd filling, which routes to the vegetable and cheese
  ravioli rows respectively. *Varškė* is a fresh cheese, so the cheese row is name-reachable; a potato filling is
  not reachable at all, since potato is a starchy root in this taxonomy and the ravioli L3 has no starch row.
- ***Žemaičių blynai*** — Samogitian pancakes, which are mashed-potato cakes with a meat filling and not
  pancakes in the batter sense. They take `Rostis or Potatoes cake` with the filling unmodelled, at an explicit B.

**The bread file's readings were reused wholesale and none needed revisiting** —
ciabatta to `Panini bread`, cinnamon buns to `Raisin puff pastry (Viennese pastries)`, butter *rageliai* and
*kruasanai* to `Croissant, butter, from bakery`, a garlic-filled French loaf to
`Bread, French bread (baguette or ball), with yeast` at C, poppy-seed filling to `Brioche, filled with fruits`.
Reusing them cost nothing and keeps 12 frozen-bakery rows consistent with the 527 in `lt/12`.

**Retailer data errors: 9 of 147 (6,1%), of which only 3 are automated flags.** The three are one units-squared
energy and two kJ cells contradicting a sound kcal cell. The six the checks cannot see are all zeroed cells and
they cluster on one family: four meat-dumpling packs declaring 0,0 g of saturates in 5 to 10,5 g of pork, beef
and lard fat, four declaring 0 g of salt in a dough whose own ingredients name *valgomoji druska*, and one
declaring 0 g of fat over a filling of smoked pork belly. Two further faults on this shelf are the
sibling-comparison shape the ice cream file first met, and both are worth knowing:

- `Ryžių pat. su kario pad. ir krev. RIMI` declares **5,2 g of carbohydrate** over an ingredient list with 34%
  cooked yellow rice. Its panel passes every check — kJ/kcal agree, the macros agree with the pair, nothing is
  impossible — and cooked rice cannot go below about 25 g of carbohydrate per 100 g, so 34% of it is 8,5 g before
  anything else is counted. The nine sibling rice dishes on the same shelf declare 11 to 17 g at 20 to 47%
  starch. **A panel can be arithmetically perfect and still contradict its own ingredient list**, and only the
  ingredient list can say so.
- RESTERA prints **one panel on two different products**: its curd *virtiniai* and its curd *lietiniai* both
  declare 966 kJ, 2,0 g fat, 42,5 g carbohydrate, 10,0 g protein and 0,5 g salt to the digit, although one dough
  is 70% and egg-free and the other 75% with egg. Both panels are internally consistent; only the pair is
  evidence. Neither can be preferred over the other, so both are graded as printed and both rows say so.

**Scrape notes.** The filtered listing (eight `categoryNamePath` facets) returned exactly the same 147 ids as the
unfiltered `SH-13-4` parent — identical sets, not just equal counts. The children are `-01` and `-11` to `-17`
with `-02` to `-10` and `-18` upward all 404, they are pairwise DISJOINT (14 + 10 + 32 + 10 + 12 + 35 + 7 + 27 =
147 with a 147-id union), and the facet counts agree. No URL on this shelf is held by any other file: the 14
vegan products here were NOT taken by `vegan-products/SH-77`, unlike the 14 vegan frozen desserts the ice cream
shelf lost to it. So the diet-label shelf is not exhaustive either, and an empty URL overlap on one category says
nothing about the next.

## The eval test sets (`test_data_*_filled.csv`)

`assessment-eval.js` scores one row per HTTP round trip, so the 6728-row Lithuanian tree is too slow to run
routinely. `test_data_<retailer>_<lang>_filled.csv` is the fixed subset it is pointed at instead (`-i`), and
**`filled` means every row carries an archetype** — a blank-archetype row has nothing to score against, which is
the only difference between `kritis/test_data_kritis.csv` and its `_filled` twin.

- `kritis/test_data_kritis_filled.csv` — 617 rows, hand-assembled and still carrying its Comments column: the
  whole Greek corpus bar the parked `11_kritis_cereal_archetypes-XXX.csv`, minus the 42 rows with no archetype.
- `rimi/lt/test_data_rimi_lt_filled.csv` — **1000 rows drawn from the 6615 eligible Lithuanian ones**, written by
  `helperscripts/make_testdata.py`. Same eight columns as the ground truth, with **Comments deliberately empty**:
  a comment annotates the ground truth and nothing downstream of the eval reads it.

**The sample is built to ESTIMATE a full-corpus run, not to cover the taxonomy**, and those are different goals.
The headline strict-match number is only comparable with a full run if the sample carries the corpus's own mix, so
rows are ordered by (file, L1, L2, L3, archetype, reliability, product name) — every category a contiguous block,
every archetype a run inside it, every grade a run inside that — and then taken **systematically** over that order,
the i-th of n being the row at `floor((2i+1)·N/2n)`. A systematic draw over a stratified order reproduces every
stratum's proportions at once, to within one row, with no RNG, so the file regenerates byte for byte. Measured:
worst category share drift **0,09 pp** over 32 files, worst reliability drift **1,00 pp**. Two kinds of row are
dropped first — the 99 with no archetype, and the 14 repeating a product name already taken, since the eval is
given the name and nothing else and a repeated name is a repeated question.

**The cost is archetype coverage: 490 of the corpus's 904 archetypes are represented**, because a 15% sample
cannot reach an archetype that only one product uses. That is the right trade for a headline estimate and the wrong
one for per-archetype diagnostics; if the question ever becomes "which archetypes does the model miss", draw a
second file weighted by archetype instead of replacing this one, so the two numbers stay comparable across runs.

Regenerate with:

```bash
cd ~/Sources/ICCS/TealHelix/helperscripts
./make_testdata.py <repo>/tealhelix-architecture/src/data rimi/lt \
    <repo>/tealhelix-architecture/src/data/rimi/lt/test_data_rimi_lt_filled.csv -n 1000
```

It asserts the header of every source file, that no two rows sharing a name disagree on the archetype, and that
what it wrote re-reads as 1000 filled rows with 1000 distinct names; it prints the per-stratum drift above so a
regeneration that skews the mix is visible rather than silent. **Regenerating after the ground truth changes
changes which products are in the test set**, so a score measured across that boundary is not a like-for-like
comparison — say so when reporting one.

## Building the derived `*_foodterm.csv`

A per-file glossary of the retailer-language food terms the archetype mapping turned on (piim, kohupiim, või,
majonees, kaelakarbonaad, šašlõkk, …), feeding `TH_FOOD_TERM`. Build it from the same product set once the
`*_archetypes.csv` is settled — the terms worth adding are the ones whose Comments had to explain what the word
means.

```
id,lang,term,canonical_en,description,category_hint_l1,category_hint_l2,category_hint_l3
```

Written by `helperscripts/gen_foodterms.py` from a spec module
(`rimi/lt_terms.py`, `rimi/et_terms.py`) holding one `SPEC[stem]` list of `Term(term, en, desc, l1, l2, l3)` per
file; `verify_foodterms.py` re-checks the written CSVs from the CSVs alone, and `selftest_foodterms.py` exercises
every check on a fixture that trips it. The id is
`uuid5(NAMESPACE_URL, "tealhelix:foodterm:<lang>:" + term)` — the scheme the Greek rows already in production use.

### The matching rule is the application's, not a substring test

`FoodTermMatcher` + `DefaultTextNormalizer` strip diacritics (NFD), lower-case, cut on non-alphanumerics and then
require the term's **tokens to appear contiguously** among the name's. So a row the matcher cannot fire on is
worse than no row — it looks like coverage and enriches nothing — and the generator validates with exactly that
rule. Three consequences:

- **Surface forms, not lemmas.** *pienas* does not reach `Šokoladinis pieno gėrimas`, so a case form the shelf
  actually writes earns its own row (`Pienas` and `Pieno`, `Sūris` and `Sūrio`), the way the Greek file gave
  `Κριτσίνι` and `Κριτσίνια` one each. Rimi's truncations (*griet.*, *brašk.*, *kiauš.*) are typography, not word
  forms, and get none — they are most of what the glossary cannot reach.
- **Estonian closes its compounds**, so *hapukoor*, *kohupiim*, *seakaelakarbonaad* and *broilerikintsuliha* are
  single tokens and each needs a row of its own; the matcher cannot see *piim* inside *kohupiim*. This is why the
  Estonian glossary is wider per product and still reaches less (75,0% against Lithuanian's 92,7%), and why the
  bakery shelf, where every item is a fresh compound, stops at 46%. A stemming or compound-splitting stage in the
  matcher would buy more there than any number of extra rows.
- **The existing Greek rows were validated with a substring test and an `alias` field**, which passes *Πατάτα*
  against *Πατάτες*. Under the runtime rule **22 of the 185 Greek terms can never fire** — Πατάτα, Τομάτα,
  Αγγούρι, Μελιτζάνα, Καρότο, Μανιτάρι, Ακτινίδιο, Νεκταρίνι, Μπισκότο, Φρυγανιά, Σφακιανή πίτα and eleven more,
  all of them a singular where the shelf writes the plural. Adding the plural fixes each one; `Κριτσίνια` and
  `Μπισκότα` were already fixed that way. **14 of the 185 also carry ids that the documented scheme does not
  reproduce** (36 of the 207 rows in `food_term_gr.csv`), so regenerating them would produce different ids for
  rows that are already seeded.

### What earns a row

- **The word, not the product.** A term names what the product *is*, never what is stirred into it: `Varškė` yes,
  the caraway in `Varškės sūris su kmynais` no. A flavouring would have to borrow its matched product's category,
  which is exactly the misleading hint the hint rule exists to prevent.
- **Nouns and the adjectives that name an identity.** Cut and format nouns count (*sprandinė*, *filė*, *dešrelės*,
  *batonėlis*), as they did in Greek (Σπάλα, Μηρός). An adjective earns a row when it says what the food *is* —
  *žalioji* arbata, *juodasis* šokoladas, *kvietiniai* miltai, *kreeka* jogurt — and not when it says what was
  *done* to it. **Processing words are deliberately absent**: rūkyta/soolatud (smoked, salted),
  marinuota/marinaadis, vytinta, virta/keedetud, šaldyta. They decide archetypes all over the fish and meat
  shelves, so carrying them would help — but they are not food nouns, which is Nikos's rule, so they wait for his
  call rather than being added quietly.
- **A word is declared once per language**, in the file where it first earns its place; the runtime glossary is one
  list per language, so the other files still get the benefit and coverage is counted language-wide.

### How deep the hint goes

As deep as the word is honest on its own, which extends the binomial-honesty test the Greek vegetables settled:

- **L3 only when SAFAD's leaf names the thing itself.** `Gouda` → `Cheese, Gouda`, `Lašiša` →
  `Salmon and trout (Salmo spp.)`, `Bertoletijos` → `Brazil nuts (Bertholletia excelsa)`.
- **Stop at L2 where the ground truth had to borrow a neighbouring leaf**, because a borrowed binomial is exactly
  the wrong thing to teach: *tilsit* and *halloumi* (no row of their own), *ešerys* on a monkfish row, *eršketas*
  on a carp row, *šamas* on the wolf-fish row, quail eggs on the goose row, *karšis* on a sea-bream genus.
- **Stop at L1, or leave the hint empty, where the word spans branches.** *Kumpis* and *šoninė* are a fresh cut on
  one shelf and a cured product on another; *sūrelis* is a processed cheese here and a glazed curd bar (filed
  under bakery) there; *kukurūzai* is a vegetable on the cob and a cereal in the flake; *gėrimas*, *desertas*,
  *batonėlis*, *kremas*, *mišinys* and *filė* name a form and nothing else. An empty hint still carries the
  meaning, which is most of the value.
- The generator refuses a hint that is not a SAFAD path, one with a hole in it, and one that no product carrying
  the word is actually placed under **anywhere in the language** — per-file would be too strict, since every
  *kiauliena* on the fresh-meat shelf happens to be in a mix or a meat loaf, which is no reason to take the pork
  branch off the word for pork.

### What was built

| Corpus | Files | Terms | Hint depth (none/L1/L2/L3) | Products reached |
|---|---|---|---|---|
| `rimi/lt` | 32 | 642 | 52 / 75 / 200 / 315 | 6237 of 6728 (**92,7%**) |
| `rimi/et` | 12 | 407 | 31 / 41 / 124 / 211 | 2090 of 2786 (**75,0%**) |
| `kritis` | 15 | 185 | — | 560 of 659 (85,0%) |

The Lithuanian residue is almost entirely Rimi's abbreviations, so 92,7% is close to the ceiling a token-exact
matcher can reach on that shelf. The Estonian residue is compounds, which is a different problem and has a
different fix.

### Seeding the database

The per-file glossaries are the curated source; the database loads one merged CSV per language, written by
`helperscripts/merge_foodterms.py`:

```
./merge_foodterms.py -d .../src/data -s rimi/lt -l lt -o .../db/glossary/food_term_lt.csv
```

The rows are the per-file rows unchanged, in file order. The merge checks exactly what `TH_FOOD_TERM` constrains —
one row per `(lang, term)`, a canonical name and a description on every row, contiguous hint levels, every value
inside its column — so a merge that passes cannot fail the import, and refuses to write anything otherwise.

`food_term_lt.csv` is loaded by the `food_terms_data_lt` changeset beside `food_terms_data_gr`
(`changelogs/v1.0.0/20260712_food_terms.xml`, context `appdata`), and `FoodTermDataIntegrityTest` asserts the
Lithuanian rows against a real Postgres: the hint depths, the genitive forms Lithuanian compounds are written in,
and that the two languages stay apart. Estonian has no seed file — the `rimi/et` glossary is still being built.

Do not regenerate `food_term_gr.csv` this way: 36 of its rows carry ids the id scheme above does not reproduce, so
a merge would mint new ids for rows that are already seeded.
