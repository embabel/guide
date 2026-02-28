# Personal config

Each developer can have their own Spring profile with personal settings (URLs, directories, paths, etc.).

## Quick start

```bash
cp scripts/user-config/application-user.yml.example scripts/user-config/application-myname.yml
# Edit to taste, then:
echo 'GUIDE_PROFILE=myname' >> .env
./scripts/fresh-ingest.sh
```

## How it works

- The scripts (`fresh-ingest.sh`, `append-ingest.sh`) read `GUIDE_PROFILE` from `.env` (default: `user`)
- Spring profiles become `local,<GUIDE_PROFILE>` → loads `application-<GUIDE_PROFILE>.yml`
- The scripts pass `--spring.config.additional-location=file:./scripts/user-config/` so Spring picks up profiles from this directory
- Personal profiles in `scripts/user-config/` are gitignored (only the `.example` is checked in)

## Ingestion on startup

The `IngestionRunner` only activates when `guide.reload-content-on-startup` is `true`. The default in `application.yml` is `false`, so normal builds (`./mvnw test`, `./mvnw spring-boot:run`) never trigger ingestion. Only the scripts set this flag -- `fresh-ingest.sh` exports `GUIDE_RELOADCONTENTONSTARTUP=true` before launching the app.

## Failure recovery

Ingestion is resilient at every level -- a single failure never prevents the remaining items from being processed:

- **URLs**: each URL is ingested independently. If one times out or returns an error, the rest continue.
- **Directories**: each configured directory is ingested independently. A missing or unreadable directory doesn't block others.
- **Documents within a directory**: each file is written to the store individually. A single unparseable file (e.g. corrupt encoding) doesn't skip the remaining files in that directory.

All failures are collected with their source and reason into the `IngestionResult`, which is:
- Printed in the **INGESTION COMPLETE** banner (so you can see what failed and why at a glance)
- Returned as JSON from `POST /api/v1/data/load-references` for programmatic inspection

## MCP tools

All ingested content -- both URLs and local directories -- is immediately available through the MCP tools (`docs_vectorSearch`, `docs_textSearch`, etc.). The MCP tools and the ingestion pipeline share the same Neo4j store, so there is no separate sync step. Once ingestion completes, MCP clients (Cursor, Claude Desktop, etc.) can search the content right away.
