-- Loads the content seed (nodes.tsv and edges.tsv, beside this file) into a Guide database on Postgres,
-- in place of the content it has: run it from this directory with `psql -f apply.sql`, on a database
-- Guide has started against at least once, so Drivine has made its tables and indexes.
--
-- The seed is the docs' content, every node tagged managedBy = 'embabel', as scripts/export-postgres-seed.sh
-- dumps it after an ingest. The content already here (the nodes tagged so) goes, and their edges with them;
-- the data the app keeps, users, chat sessions and personas among them, stays. The seed's nodes take new
-- ids, so they never meet the app's. It is all one transaction: Guide reads the old content or the new.

\set ON_ERROR_STOP on

DO $$
BEGIN
    IF to_regclass('drivine_node') IS NULL OR to_regclass('drivine_edge') IS NULL THEN
        RAISE EXCEPTION 'No Drivine tables here: start Guide against this database once, so Drivine makes them';
    END IF;
END
$$;

BEGIN;

CREATE TEMP TABLE seed_node (nid bigint PRIMARY KEY, labels jsonb NOT NULL, props jsonb NOT NULL, stamp varchar(33))
    ON COMMIT DROP;
CREATE TEMP TABLE seed_edge (src bigint NOT NULL, dst bigint NOT NULL, type varchar(128) NOT NULL, props jsonb NOT NULL)
    ON COMMIT DROP;

\copy seed_node FROM 'nodes.tsv'
\copy seed_edge FROM 'edges.tsv'

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM seed_node WHERE labels @> '["Chunk"]') THEN
        RAISE EXCEPTION 'The seed holds no chunks: refusing to replace the content with it';
    END IF;
    IF EXISTS (SELECT 1 FROM seed_node WHERE props->>'managedBy' IS DISTINCT FROM 'embabel') THEN
        RAISE EXCEPTION 'A seed node is not tagged managedBy = embabel, so the next seed would not replace it';
    END IF;
END
$$;

DELETE FROM drivine_node WHERE props->>'managedBy' = 'embabel';

ALTER TABLE seed_node ADD COLUMN new_nid bigint;
UPDATE seed_node SET new_nid = nextval(pg_get_serial_sequence('drivine_node', 'nid'));

INSERT INTO drivine_node (nid, labels, props, stamp) OVERRIDING SYSTEM VALUE
SELECT new_nid, labels, props, stamp FROM seed_node;

INSERT INTO drivine_edge (src, dst, type, props)
SELECT s.new_nid, d.new_nid, e.type, e.props
FROM seed_edge e
JOIN seed_node s ON s.nid = e.src
JOIN seed_node d ON d.nid = e.dst;

SELECT (SELECT count(*) FROM seed_node) AS nodes, (SELECT count(*) FROM seed_edge) AS edges,
       (SELECT count(*) FROM seed_node WHERE labels @> '["Chunk"]') AS chunks;

COMMIT;

ANALYZE drivine_node;
ANALYZE drivine_edge;
