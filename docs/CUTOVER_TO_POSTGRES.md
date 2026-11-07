# Cutover from Neo4j Aura to Postgres

Guide in production runs on Neo4j Aura. It moves to Cloud SQL for Postgres, through Drivine, in one cutover. Two kinds
of data move, by different paths:

| Data | Nodes in prod | How it gets to Postgres |
|---|---|---|
| Content: documents, sections, chunks (`managedBy = embabel`) | ~3,000 | The Postgres seed: made each night by the Export Seed Database workflow (`seed-postgres`), loaded by `seed/postgres/apply.sql`. Rebuilt from the sources, never copied. |
| App data: users, web and OAuth logins, chat sessions, messages, personas, feedback | ~2,050 (Nov 2026) | Copied once, at cutover, by `Neo4jToPostgresMigration`. |

No app data points into content (feedback names its page by URL), so the two never need to move together, and a seed
refresh never touches users or chats.

## Before

- Cloud SQL exists: embabel/cloud-infrastructure#6, applied. Guide's password is the `POSTGRES_PASSWORD` secret.
- The Postgres seed is published: embabel/guide#70, and a run of Export Seed Database since.
- A rehearsal from prod into a local Postgres has passed (see [Rehearsal](#rehearsal)), on the Drivine version
  the cutover ships.
- An Aura snapshot is taken.

## Steps

1. **Make the tables.** Start Guide once against Cloud SQL, through the Auth Proxy, with the `postgres` profile and no
   traffic: a Cloud Run revision with no traffic, or a local run through `cloud-sql-proxy`. Drivine makes its tables,
   indexes and extensions; Guide makes the bot, the anonymous user and the system personas, which the copy replaces.
2. **Load the content.** Apply the latest `seed-postgres` artifact with `psql -f apply.sql` from its directory.
3. **Freeze writes** on the Neo4j service: logins and chats made after the copy would be lost.
4. **Copy the app data.** Run the migration from Aura to Cloud SQL, then the read-back check with the counts the
   migration printed. The copy takes seconds.
5. **Switch.** Guide's Cloud Run service moves to the `postgres` profile, with the Auth Proxy as a sidecar on
   `localhost:5432`, and takes the traffic. The hub's `apply-seed` moves to the Postgres seed. End the freeze.
6. **Afterwards.** Change the default profile in `application.yml` to `postgres` and drop `spring-boot.run.profiles`
   from the pom. Keep Aura, read only, until Guide has run on Postgres for a while.

**Rollback**, up to and after the switch: move the Cloud Run service back to the Neo4j revision. The migration only
reads Aura, so it is as it was at the freeze. Chats made on Postgres since the switch would not be carried back.

## Running the migration

It is a test class, enabled only when its target is set, so it never runs in the ordinary build. It reads Neo4j in
read mode only. Before it touches Postgres it refuses a source with edges between app data and content, or edges
with properties. On Postgres it deletes every app node, then copies the source's: so it refuses a target holding
anything beyond what Guide makes on its first start (the bot, the anonymous user, the system personas), unless
`MIGRATE_REPLACE=true` (a rehearsal rerun, or a rerun after a failed copy).

```bash
MIGRATE_NEO4J_URI=neo4j+s://… MIGRATE_NEO4J_USERNAME=… MIGRATE_NEO4J_PASSWORD=… \
MIGRATE_POSTGRES_URL=jdbc:postgresql://localhost:5432/guide MIGRATE_POSTGRES_USERNAME=guide MIGRATE_POSTGRES_PASSWORD=… \
./mvnw -q test -Dtest=Neo4jToPostgresMigration -Dsurefire.failIfNoSpecifiedTests=false
```

It checks the counts of nodes by labels and of edges by type against the source before it passes. Then read every
user back through Guide's services:

```bash
USE_LOCAL_POSTGRES=true POSTGRES_HOST=localhost POSTGRES_DATABASE=guide POSTGRES_PASSWORD=… \
MIGRATE_EXPECT_SESSIONS=<ChatSession> MIGRATE_EXPECT_MESSAGES=<StoredMessage> \
MIGRATE_EXPECT_PERSONAS=<Persona> MIGRATE_EXPECT_FEEDBACK=<Feedback> \
./mvnw -q test -Dtest=MigratedDataReadBackTest -Dsurefire.failIfNoSpecifiedTests=false
```

The `MIGRATE_` prefix keeps the source apart from the `NEO4J_*` variables Guide itself reads, so a shell holding both
never points Guide at production.

Where it runs at cutover is still to settle: a manual (`workflow_dispatch`) job in the hub, which already reads the
`NEO4J_*` secrets through workload identity and whose deploy account gets `roles/cloudsql.client` in #6, leaves no
production credentials on a laptop and keeps a log. A laptop through `cloud-sql-proxy` works too.

### How it copies

- Nodes go in through Drivine's Cypher, `UNWIND $rows AS row CREATE (n:…) SET n += row.props`, with the properties
  bound as parameters, so Drivine encodes them as it does for any save: zoned datetimes as ISO text, the stamp
  (`__drivine.stamp`) into its column, embeddings as arrays the vector index reads.
- Edges go in by one SQL join on a mark each node carries while copying, removed after. A Cypher `MATCH` on a row's
  value scans every node for each row (drivine4j#617): 263 s for the edges, against 170 ms. That holds only while no
  edge has properties, which Guide's have none of; the migration refuses to run if one does.
- APOC's `export.cypher` replayed through Drivine was tried first and needs drivine4j 0.6.0 (`end` as a variable,
  #617, Neo4j schema statements), with a file to move about. This needs neither.

## Rehearsal

On 2026-11-06, from prod Aura (Neo4j 5.27) into a local Postgres 17 holding the content seed, on Drivine 0.5.0:

- 2,052 nodes and 3,128 edges read; counts by labels and by type equal on Postgres.
- Nodes in 2.9 s, edges in 170 ms. A second run replaced the first.
- Read back through Guide's services: 220 users with their personas (the 221st is the bot, which uses none), 104
  sessions, 1,161 messages, 93 personas, 48 feedback. Guide found the copied bot and personas rather than making them.
- Prod held 91 system personas, 13 copies of each of 7, 84 of them ownerless and in use: each seed apply on Aura
  deleted `bot:jesse`, cutting its `OWNED_BY` edges, and Guide then seeded 7 more. All were copied. Fixed by
  embabel/guide#72 and embabel/embabel-hub#84, and prod cleaned on 2026-11-06 (users moved to the owned copy of
  the same persona, orphans deleted): the next rehearsal expects 9 personas.
- Guide started on the copy, and a user logged in through OAuth found their sessions.

A rehearsal copies users' email addresses, password hashes and chats onto the machine it runs on: drop the database
after.

## The cutover, 2026-11-07

Run from a laptop, through `cloud-sql-proxy`, on Drivine 0.6.0:

1. Tables: Guide started once against Cloud SQL (embabel/cloud-infrastructure#6, `db-g1-small`).
2. Content: the `seed-postgres` artifact applied with `psql -f apply.sql` in 28 s.
3. Freeze, 22:41:56 UTC: `gcloud run services update embabel-guide --ingress=internal`.
4. Copy: 1,984 nodes and 3,151 edges in 54 s, counts equal. Read back: 222 users, 105 sessions, 1,168 messages,
   9 personas, 50 feedback.
5. Switch, 22:44:41 UTC: `gcloud run services replace` with the hub's `.github/cloud-run/guide-postgres.yaml` and an
   image already built from `main`, which reopened ingress. Deploy to GCP uses that file since embabel/embabel-hub#89.
   Chat was down about 3 minutes.

Rollback is the last Aura revision, `embabel-guide-00048-sw6`:
`gcloud run services update-traffic embabel-guide --to-revisions=embabel-guide-00048-sw6=100`.

Learned on the way:

- Set the pool size. Unset, Drivine's pool held 40 connections; `db-g1-small` allows 47 to the `guide` user, and a
  deploy runs the new revision beside the old one, so the next deploy could not have started. `poolMax` is
  `POSTGRES_POOL_MAX`, 5 by default (#74).
- Text search is the slow part: about 1 s slower than on Aura (timed from a laptop, both services in us-east1), growing with the common
  words in the query (the same shape on a laptop, about 3 times faster). Vector search is within 40 ms of Aura.
