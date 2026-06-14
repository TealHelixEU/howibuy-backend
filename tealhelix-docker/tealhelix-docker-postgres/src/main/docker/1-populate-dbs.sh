#!/usr/bin/env bash
set -e

# Run the schema creation scripts as the appropriate user for each schema. If we add the N-dbname.sql files directly to
# /docker-entrypoint-initdb.d/ they run as the postgres user and this user owns the resulting tables.
psql -f /docker-entrypoint-initdb.d/sql-scripts/1-howibuy.sql -U th_howibuy tealhelix
