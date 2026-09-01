#!/usr/bin/env python3
"""Convert the WP3 substitutability matrix into the Liquibase loadData CSV for
TH_ARCHETYPE_SUBSTITUTABILITY.

Input   Step1_encoding_v2026-05-28.csv  — a 124x124 matrix of SAFAD L2 category names, values 0-5.
Output  archetype_substitutability.csv  — one row per non-zero cell:
            from_category_id, to_category_id, degree
        where from_category_id may substitute for to_category_id with the given degree.

Cell (row R, column C) is read as "R may substitute for C", matching the .Rmd's column-wise lookup
(`matrix_A[, subcategory_A]`). The current matrix is perfectly symmetric, so the orientation is
not observable today; it is preserved so a future asymmetric revision cannot silently invert.

Zero cells are omitted: 2,634 of 15,376 cells are non-zero, including the all-5s diagonal (a
category is always substitutable for itself, which is what makes a self-recommendation possible).

Category UUIDs are resolved by name against the already-seeded archetype_category.csv. L2 names are
globally unique today; the script fails loudly if that stops being true.

Usage:  python3 generate_substitutability_csv.py [--out PATH] [--check]
"""
import argparse
import collections
import csv
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
ARCHETYPE_DIR = os.path.join(
    REPO, "howibuy-container", "howibuy-dao-hibernate-reactive",
    "src", "main", "resources", "db", "archetype")
ENCODING_CSV = os.path.abspath(os.path.join(
    REPO, "..", "R-algorithm", "R version algorithm", "Step1_encoding_v2026-05-28.csv"))
DEFAULT_OUT = os.path.join(ARCHETYPE_DIR, "archetype_substitutability.csv")


def l2_ids_by_name():
    path = os.path.join(ARCHETYPE_DIR, "archetype_category.csv")
    rows = [r for r in csv.reader(open(path, encoding="utf8")) if r and r[0] != "id"]
    l2 = [r for r in rows if r[2] == "2"]
    duplicates = [n for n, c in collections.Counter(r[3] for r in l2).items() if c > 1]
    if duplicates:
        sys.exit(f"L2 category names are no longer globally unique, cannot resolve by name: {duplicates}")
    return {r[3]: r[0] for r in l2}


def read_matrix():
    rows = list(csv.reader(open(ENCODING_CSV, encoding="latin1")))
    header = rows[0][1:]
    matrix = {r[0]: dict(zip(header, [int(v) for v in r[1:]])) for r in rows[1:] if r}
    return header, matrix


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=DEFAULT_OUT)
    parser.add_argument("--check", action="store_true",
                        help="report on the matrix without writing anything")
    args = parser.parse_args()

    ids = l2_ids_by_name()
    header, matrix = read_matrix()

    unknown = sorted((set(header) | set(matrix)) - set(ids))
    if unknown:
        sys.exit(f"Matrix categories absent from the seeded taxonomy: {unknown}")
    missing = sorted(set(ids) - set(header))
    if missing:
        sys.exit(f"Seeded L2 categories absent from the matrix: {missing}")

    asymmetric = [(row, col) for row in header for col in header
                  if matrix[row][col] != matrix[col][row]]
    values = collections.Counter(matrix[r][c] for r in matrix for c in header)

    print(f"categories {len(header)}  cells {len(header) ** 2}")
    print(f"value distribution: {dict(sorted(values.items()))}")
    print(f"non-zero cells: {sum(v for k, v in values.items() if k)}")
    print(f"symmetric: {not asymmetric}")
    print(f"diagonal values: {sorted({matrix[c][c] for c in header})}")
    if args.check:
        return

    written = 0
    with open(args.out, "w", newline="", encoding="utf8") as fh:
        writer = csv.writer(fh, lineterminator="\n")
        writer.writerow(["from_category_id", "to_category_id", "degree"])
        for source in header:            # stable, matrix order
            for target in header:
                degree = matrix[source][target]
                if degree:
                    writer.writerow([ids[source], ids[target], degree])
                    written += 1
    print(f"wrote {written} rows to {args.out}")


if __name__ == "__main__":
    main()
