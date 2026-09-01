# Sustainability Scoring & Substitution — working directory

| File                               | Role                                                                                                       |
|------------------------------------|------------------------------------------------------------------------------------------------------------|
| `spec.md`                          | The specification. Start here.                                                                             |
| `issues/`                          | Implementation issues, in dependency order.                                                                |
| `questions-ku-leuven.md`           | Open methodology questions for WP3. Tier 1 blocks correctness.                                             |
| `reference_algorithm.py`           | Python re-implementation of the R algorithm over the seeded data.                                          |
| `generate_substitutability_csv.py` | Converts `Step1_encoding_v2026-05-28.csv` into the Liquibase seed CSV for `TH_ARCHETYPE_SUBSTITUTABILITY`. |

Both scripts are plain Python 3, no dependencies. They read the WP3 source data from
`../../../R-algorithm/R version algorithm/` and the seeded archetype CSVs from
`howibuy-dao-hibernate-reactive/src/main/resources/db/archetype/`.

`reference_algorithm.py` aids in analysis and cross-check; produces the figures quoted in the spec and the question list.

```bash
python3 reference_algorithm.py                    # all three substitutability levels
python3 reference_algorithm.py --level small
python3 generate_substitutability_csv.py --check  # inspect the matrix, write nothing
python3 generate_substitutability_csv.py          # write the seed CSV into main/resources
```
