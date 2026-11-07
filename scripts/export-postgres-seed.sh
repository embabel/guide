#!/usr/bin/env bash
# Dumps the content of a Guide database on Postgres, just ingested, as the seed seed/postgres/apply.sql
# loads: nodes.tsv and edges.tsv in <out-dir>, with apply.sql beside them.
#
# The content is the ContentElement nodes (documents, sections and chunks), each tagged managedBy = 'embabel'
# so the next seed replaces it, and the edges between them. Nothing else is: not the personas or the bot user
# Guide makes on startup, which the app makes again wherever it runs.
#
# Usage: scripts/export-postgres-seed.sh <out-dir>
# psql connects as the PG* environment says (PGHOST, PGPORT, PGUSER, PGPASSWORD, PGDATABASE); set PSQL to run
# psql some other way, such as `docker exec -i <container> psql -U guide -d guide`.
set -euo pipefail

out=${1:?usage: $0 <out-dir>}
psql=${PSQL:-psql}
here=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$out"

content="labels @> '[\"ContentElement\"]'"

$psql -v ON_ERROR_STOP=1 -X -q -c "\copy (
    SELECT nid, labels, props || '{\"managedBy\": \"embabel\"}', stamp FROM drivine_node WHERE $content ORDER BY nid
) TO STDOUT" > "$out/nodes.tsv"

$psql -v ON_ERROR_STOP=1 -X -q -c "\copy (
    SELECT e.src, e.dst, e.type, e.props FROM drivine_edge e
    JOIN drivine_node s ON s.nid = e.src AND s.$content
    JOIN drivine_node d ON d.nid = e.dst AND d.$content
    ORDER BY e.src, e.dst, e.type
) TO STDOUT" > "$out/edges.tsv"

cp "$here/../seed/postgres/apply.sql" "$out/"

echo "Nodes: $(wc -l < "$out/nodes.tsv"), edges: $(wc -l < "$out/edges.tsv"), $(du -h "$out/nodes.tsv" | cut -f1) of nodes"
