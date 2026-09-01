#!/usr/bin/env python3
"""Reference re-implementation of the WP3 R algorithm (TH_Algorithm_Implementation_v2026-05-20.Rmd),
run over the archetype data as seeded into the backend.

This is an analysis aid, not production code. It exists to
  - quantify the outcome distribution before the Java port is written (the figures quoted in
    questions-ku-leuven.md), and
  - act as an independent cross-check while the Java engine is built.

It is NOT a substitute for a golden fixture produced by KU Leuven's own R run.

Usage:  python3 reference_algorithm.py [--level small|medium|large]
"""
import argparse
import collections
import csv
import os

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
ARCHETYPE_DIR = os.path.join(
    REPO, "howibuy-container", "howibuy-dao-hibernate-reactive",
    "src", "main", "resources", "db", "archetype")
ENCODING_CSV = os.path.abspath(os.path.join(
    REPO, "..", "R-algorithm", "R version algorithm", "Step1_encoding_v2026-05-28.csv"))

# --- Weights and normalization factors, transcribed from the .Rmd ------------------------------

E_KEYS = ("climate_change ozone_depletion ionizing_radiation ozone_formation "
          "particulate_matter non_carcinogenic_toxicity carcinogenic_toxicity land_water_acidification "
          "freshwater_eutrophication marine_eutrophication terrestrial_eutrophication freshwater_ecotoxicity "
          "land_use water_use energy_use mineral_use").split()
E_NORMALIZATION = dict(zip(E_KEYS, [
    7553.08316285117, 0.0523483833840181, 4220.16339014993, 40.8591977347772,
    0.000595366821125478, 0.000128735735008072, 1.72528976538705e-05, 55.5695412306019,
    1.60685212828813, 19.5451815519191, 176.754999788942, 56716.5863370596,
    819498.182923031, 11468.7086407597, 65004.2596640165, 0.0636226152369547]))
E_SCIENTIFIC = dict(zip(E_KEYS, [
    0.2106, 0.0631, 0.0501, 0.0478, 0.0896, 0.0184, 0.0213, 0.062,
    0.028, 0.0296, 0.0371, 0.0192, 0.0794, 0.0851, 0.0832, 0.0755]))

AW_SCIENTIFIC = {"index": 0.5, "antibio_index": 0.5}

S_KEYS = ("child_labour forced_labour fair_salary working_time discrimination "
          "health_safety_workers social_benefits_legal_issues workers_rights fair_competition "
          "corruption contribution_econ_dev illiteracy health_safety_society "
          "indigenous_rights").split()
S_SCIENTIFIC = {k: 0.071429 for k in S_KEYS}

NUTRISCORE = {"Nutriscore_A": 1.0, "Nutriscore_B": 0.75, "Nutriscore_C": 0.5,
              "Nutriscore_D": 0.25, "Nutriscore_E": 0.0}

# Absolute cut-offs on the *unnormalized* single score; see question 2.3.
E_UPPER_THRESHOLD = 4
AW_UPPER_THRESHOLD = 100

OVERALL_SCIENTIFIC = {"E": .25, "AW": .25, "S": .25, "H": .25}
OVERALL_PERSONAL = {"E": .1, "AW": .1, "S": .1, "H": .7}
WEIGHT_PERSONAL, WEIGHT_SCIENTIFIC = 0.6, 0.4

# Provisional, pending KU Leuven's answer to question 1.1.
LEVEL_THRESHOLDS = {"small": 4, "medium": 3, "large": 1}


def load():
    categories = {r[0]: (r[1], int(r[2]), r[3])
                  for r in csv.reader(open(os.path.join(ARCHETYPE_DIR, "archetype_category.csv"),
                                           encoding="utf8")) if r and r[0] != "id"}
    products = list(csv.DictReader(open(os.path.join(ARCHETYPE_DIR, "archetype_product.csv"),
                                        encoding="utf8")))
    rows = list(csv.reader(open(ENCODING_CSV, encoding="latin1")))
    header = rows[0][1:]
    matrix = {r[0]: dict(zip(header, [int(v) for v in r[1:]])) for r in rows[1:] if r}
    return categories, products, matrix, header


def aggregate(product, prefix, weights, normalization=None):
    """The PEF aggregation: sum of indicator x (1000 x weight / normalization factor)."""
    total = 0.0
    for key, weight in weights.items():
        factor = 1000 * weight / normalization[key] if normalization else weight
        total += float(product[f"{prefix}_{key}"]) * factor
    return total


def min_max_normalize(products, key, upper_threshold=None):
    """Rescale to [0,1] and invert so higher = more sustainable, floored at 0.

    Matches the .Rmd: the max excludes outliers above the threshold, the min does not."""
    values = [p[key] for p in products]
    low = min(values)
    high = max(v for v in values if upper_threshold is None or v <= upper_threshold)
    for p in products:
        p[key + "_norm"] = max(1 - (p[key] - low) / (high - low), 0.0)


def score(products, categories):
    l2_of = lambda cid: categories[categories[cid][0]][2]
    l1_of = lambda cid: categories[categories[categories[cid][0]][0]][2]
    for p in products:
        p["E"] = aggregate(p, "e", E_SCIENTIFIC, E_NORMALIZATION)
        p["AW"] = aggregate(p, "aw", AW_SCIENTIFIC)
        p["S"] = aggregate(p, "s", S_SCIENTIFIC)
        p["H_norm"] = NUTRISCORE.get(p["nutri_score"])
        p["l2"] = l2_of(p["category_id"])
        p["l1"] = l1_of(p["category_id"])
    min_max_normalize(products, "E", E_UPPER_THRESHOLD)
    min_max_normalize(products, "AW", AW_UPPER_THRESHOLD)
    min_max_normalize(products, "S")
    for p in products:
        # A missing Nutri-Score makes the overall score NA, as in R; those products are dropped.
        if p["H_norm"] is None:
            p["scientific"] = p["personal"] = None
        else:
            p["scientific"] = sum(p[d + "_norm"] * w for d, w in OVERALL_SCIENTIFIC.items())
            p["personal"] = sum(p[d + "_norm"] * w for d, w in OVERALL_PERSONAL.items())


def recommend(reference, by_l2, compatible):
    """Steps 1-4: eligible categories, no-regression filter, combined ranking, argmax.

    Ties break on agb_code; R's which.max takes the first row in TH_code order, which is an
    implementation detail rather than a decision (question 3.3)."""
    candidates = [p for category in compatible.get(reference["l2"], [])
                  for p in by_l2.get(category, [])
                  if p["scientific"] >= reference["scientific"]]
    if not candidates:
        return None, None, None
    best = lambda keyfn: max(candidates, key=lambda p: (keyfn(p), p["agb_code"]))
    return (best(lambda p: p["personal"]),
            best(lambda p: p["scientific"]),
            best(lambda p: WEIGHT_PERSONAL * p["personal"] + WEIGHT_SCIENTIFIC * p["scientific"]))


def compatible_categories(matrix, header, threshold):
    """Categories that may substitute for each category, read column-wise as the .Rmd does."""
    return {column: [row for row in matrix if matrix[row].get(column, 0) >= threshold]
            for column in header}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--level", choices=list(LEVEL_THRESHOLDS), default=None,
                        help="report a single level instead of all three")
    args = parser.parse_args()

    categories, products, matrix, header = load()
    score(products, categories)
    scoreable = [p for p in products if p["scientific"] is not None]
    by_l2 = collections.defaultdict(list)
    for p in scoreable:
        by_l2[p["l2"]].append(p)

    print(f"corpus {len(products)}  scoreable {len(scoreable)}  "
          f"dropped (no Nutri-Score) {len(products) - len(scoreable)}")
    print(f"L2 categories with scoreable products {len(by_l2)}  in matrix {len(matrix)}")
    print()

    levels = [args.level] if args.level else list(LEVEL_THRESHOLDS)
    for level in levels:
        threshold = LEVEL_THRESHOLDS[level]
        compatible = compatible_categories(matrix, header, threshold)
        outcome = collections.Counter()
        recommended = collections.Counter()
        crossing_l1 = 0
        for reference in scoreable:
            _, _, combined = recommend(reference, by_l2, compatible)
            if combined is None:
                outcome["no candidate"] += 1
                continue
            if combined is reference:
                outcome["recommends itself (GOOD_ENOUGH)"] += 1
            else:
                outcome["real suggestion"] += 1
            recommended[combined["name"]] += 1
            crossing_l1 += combined["l1"] != reference["l1"]

        total = len(scoreable)
        print(f"--- level {level} (matrix value >= {threshold}) ---")
        for label, count in outcome.most_common():
            print(f"  {count:5d} ({100 * count / total:5.1f}%)  {label}")
        print(f"  distinct products ever recommended: {len(recommended)}")
        print(f"  recommendations crossing L1: {crossing_l1} ({100 * crossing_l1 / total:.1f}%)")
        for name, count in recommended.most_common(7):
            print(f"      {100 * count / total:5.1f}%  {name}")
        print()


if __name__ == "__main__":
    main()
