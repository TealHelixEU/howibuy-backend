# A Recommendation is gated and floored on the scientific Overall Score, and personalised by a seeded random draw

The assessment answers every Reference Product with one of four Recommendations: Recommended, Good Enough, No
Alternative or No Score. Whether an Alternative is sought at all (the Gate: a scientific Overall Score of at least
0.80), and which products may be one (the Pools: one for each of the degrees of Substitutability 5, 4 and 3, holding
the stocked products substitutable at exactly that degree and strictly better on the scientific Overall Score), are
decided on the scientific Weighting Profile alone. The user's own weights decide only which product of each Pool is
offered, by a random draw that favours a higher personal Overall Score: each product with probability proportional to
`exp(15 × its personal Overall Score)`. Up to three Alternatives are offered, one from each Pool that is not empty, the
closest degree first.

**The random source is seeded from the user and the Reference Product, so the same user scanning the same product is offered the same Alternatives, and two users, or one user scanning two products, need not be.**

This is the method of WP3's notebook `paper_analysis_2026_07_31_v15.qmd`, apart from the seeding, which the notebook
leaves open and WP3 left to us, and the three Pools, which WP3 agreed to: the notebook draws one Alternative from
degrees 5 and 4 together.

## Considered Options

### What the user's weights decide

- **The choice within an objective floor (chosen).** The Gate and the Pools use the scientific profile, the draws the
  personal one. No weighting a user can express can lead to an objectively worse product, or talk a product into being
  good enough. The notebook's comparison of its pool modes shows the point: a pool open to every substitutable product
  lets the draw make a downgrade unlikely, not impossible, where the strict floor rules it out.
- **Three rankings of every product at least as good as the Reference Product.** The first method's best personal,
  best scientific and best combined (0.6 personal, 0.4 scientific). The three mostly agreed, and concentrated on a
  handful of products. Their floor was inclusive, so the Reference Product could win its own search, and "good enough"
  ended up meaning "nothing better is stocked".
- **Personal weights in the Gate as well.** The Gate's 0.80 is a statement about the product, not the user. A user
  indifferent to health would otherwise be told a product is good enough when it is not.

### How the Alternative is picked from the Pool

- **The best on the personal Overall Score (argmax).** Deterministic and simple to test, but users with similar weights
  are all sent to the same few products, and nothing the Pool holds besides the winner is ever seen. It is the
  notebook's validation mode, not its app mode. It is not kept: the picks are not compared with WP3's (ADR 0005), so
  nothing would use it. It comes back if WP3 asks for pick-by-pick output.
- **A fresh draw on every scan.** The variety the notebook's sharpness sweep measures across repeated visits, but the
  same scan answered twice with two products looks like a fault to the user. And no answer can be reproduced when a
  user or a Retailer asks about it.
- **A draw seeded per user and Reference Product (chosen).** The same user scanning the same product sees the same
  Alternative; different users, and the same user across the products of a category, still spread over the Pool.
  This gives up the per-visit variety the notebook measures, for a single user and a single product; across users
  and products the variety stays. The seed is a hash fixed by specification of the internal `UserId` and the
  Reference Product's `ArchetypeProductId`, fed to `java.util.Random`, whose algorithm the JDK specifies. Nothing
  identity- or run-dependent goes in, so the answer survives restarts and redeploys.

### How the three Pools share the random source

- **One number per degree, in turn, whatever its Pool holds (chosen).** Degrees 5, 4 and 3 each take one number, in
  that order, whether their Pool is empty, of one or of more. Each Alternative depends on its own Pool alone, and the
  seed stays one hash per assessment. A Pool that is empty or of one takes a number it has no use for.
- **A number only for a Pool with something to draw between.** The fewest numbers, but stocking a second product at
  degree 5 would move the user's Alternatives at degrees 4 and 3, though nothing changed there.
- **A seed per degree,** a hash of the user, the Reference Product and the degree. The same independence, for three
  hashes per assessment and a seed whose specification has to name the degree.

## Consequences

- The engine never creates a random source. It takes a `java.util.random.RandomGenerator`, so its tests stub the draw
  or seed it, and the service layer derives the seed.
- The draw samples the notebook's softmax by inversion over the Pool in the order of the AGB codes, where the notebook
  uses the Gumbel-max trick. The distribution is the same; the products picked from a given seed are not, and need not
  be.
- Each degree takes one number from the random source, in the order 5, 4, 3, whatever its Pool holds.
- Each Alternative says the degree of the Pool it was drawn from, since the order of the list cannot tell once a degree
  is skipped.
- A user's Alternative from a Pool changes when, and only when:
  - they retake the compass, since their weights move the probabilities;
  - that Pool changes: the Catalogue stocks or drops a product of it. Any such change can move the Alternative, even
    when the product stocked or dropped is not the one offered: the draw maps one random number onto the shares of the
    whole Pool, and every share moves with the total. A change to one Pool never moves the Alternative of another;
  - WP3's data or substitutability matrix change.

  It does not change with a restart, a redeploy, the order the corpus is loaded in, or the time of day.
- What was recommended to whom is not stored. Reconstructing an answer needs the user's weights at the time, the
  Catalogue's stock at the time and the data version.
