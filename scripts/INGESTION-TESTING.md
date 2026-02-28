# Testing Guide

## Run all tests

```bash
./mvnw test
```

Runs all 97 tests (unit + integration). Integration tests use Testcontainers to spin up Neo4j automatically — no local Neo4j needed.

## Run specific test classes

```bash
# Single class
./mvnw test -Dtest=IngestionResultTest

# Multiple classes
./mvnw test -Dtest="IngestionResultTest,IngestionRunnerTest,DataManagerControllerTest"

# Single method
./mvnw test -Dtest="IngestionRunnerTest#summary banner contains URL results"
```

## Test coverage by area

### Ingestion pipeline (new)

| Test class | Type | What it covers |
|---|---|---|
| `IngestionResultTest` | Unit | `IngestionResult` record: totals, `hasFailures()`, duration |
| `IngestionRunnerTest` | Unit | `IngestionRunner`: calls `loadReferences`, prints banner with URLs/dirs/stats/port, `formatDuration` |
| `DataManagerControllerTest` | Unit | REST endpoints: `GET /stats`, `POST /load-references` returns `IngestionResult` |
| `DataManagerLoadReferencesIntegrationTest` | Integration | Full pipeline: DataManager → Neo4j. Ingests sample directory, verifies structured result + documents/chunks in store |

Run just these:

```bash
./mvnw test -Dtest="IngestionResultTest,IngestionRunnerTest,DataManagerControllerTest,DataManagerLoadReferencesIntegrationTest"
```

### Other test areas

| Test class | Type | What it covers |
|---|---|---|
| `GuidePropertiesPathResolutionTest` | Unit | Path resolution (`~/`, absolute, relative) |
| `HubApiControllerTest` | Integration | Hub REST API (register, login, sessions, JWT) |
| `HubServiceTest` | Integration | User registration validation |
| `DrivineGuideUserRepositoryTest` | Integration | Neo4j user repository (Drivine) |
| `GuideUserRepositoryDefaultImplTest` | Integration | Neo4j user repository (GraphView) |
| `GuideUserServiceTest` | Integration | Anonymous web user service |
| `McpSecurityTest` | Integration | MCP endpoints are publicly accessible |

## Using local Neo4j (faster iteration)

By default, tests use Testcontainers (slower startup, fully isolated). For faster runs during development:

1. Start Neo4j:

```bash
docker compose up neo4j -d
```

2. Run tests with local Neo4j:

```bash
USE_LOCAL_NEO4J=true ./mvnw test
```

## Manual testing of fresh-ingest.sh

To test the full ingestion flow end-to-end:

1. Set up your `.env` and personal profile (see `scripts/README.md`)
2. Run:

```bash
./scripts/fresh-ingest.sh
```

3. Watch for the **INGESTION COMPLETE** banner with:
   - Time elapsed
   - Loaded/failed URLs
   - Ingested/failed directories
   - RAG store stats (documents, chunks, elements)
   - Port and MCP endpoint

4. Verify the REST API:

```bash
# Stats
curl http://localhost:1337/api/v1/data/stats

# Trigger ingestion manually (returns JSON IngestionResult)
curl -X POST http://localhost:1337/api/v1/data/load-references
```

5. Verify MCP:

```bash
curl -i --max-time 3 http://localhost:1337/sse
```

Should return `Content-Type: text/event-stream`.
