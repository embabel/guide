#!/usr/bin/env bash
# Wipe Neo4j RAG data and re-ingest everything from scratch.
# Starts Neo4j (Docker), clears all ContentElement nodes, then runs Guide
# with reload-content-on-startup=true. IngestionRunner prints the summary.
#
# Set GUIDE_PROFILE in .env to use your own profile (default: "user").
# e.g. GUIDE_PROFILE=menke → loads application-menke.yml
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GUIDE_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$GUIDE_ROOT"

if [ -f .env ]; then
  echo "Loading .env..."
  set -a
  source .env
  set +a
fi

GUIDE_PORT="${GUIDE_PORT:-1337}"
EXISTING_PID=$(lsof -ti :"$GUIDE_PORT" 2>/dev/null | head -1)
if [ -n "$EXISTING_PID" ]; then
  echo "Killing existing process on port $GUIDE_PORT (PID $EXISTING_PID)..."
  kill "$EXISTING_PID" 2>/dev/null || true
  sleep 1
  kill -9 "$EXISTING_PID" 2>/dev/null || true
  sleep 1
fi

echo "Ensuring Neo4j is up (Docker)..."
docker compose up neo4j -d

NEO4J_BOLT_PORT="${NEO4J_BOLT_PORT:-7687}"
echo "Waiting for Neo4j on port $NEO4J_BOLT_PORT..."
max_wait=60
elapsed=0
while [ $elapsed -lt $max_wait ]; do
  if docker exec embabel-neo4j cypher-shell -u "${NEO4J_USERNAME:-neo4j}" -p "${NEO4J_PASSWORD:-brahmsian}" "RETURN 1" >/dev/null 2>&1; then
    echo "Neo4j is ready."
    break
  fi
  sleep 3
  elapsed=$((elapsed + 3))
  echo "  ... ${elapsed}s"
done
if [ $elapsed -ge $max_wait ]; then
  echo "Neo4j did not become ready in time."
  exit 1
fi

echo "Clearing RAG content in Neo4j (ContentElement nodes)..."
docker exec embabel-neo4j cypher-shell -u "${NEO4J_USERNAME:-neo4j}" -p "${NEO4J_PASSWORD:-brahmsian}" "MATCH (c:ContentElement) DETACH DELETE c" 2>/dev/null || true
echo "RAG content cleared."

GUIDE_PROFILE="${GUIDE_PROFILE:-user}"
export SPRING_PROFILES_ACTIVE="local,${GUIDE_PROFILE}"
export NEO4J_URI="${NEO4J_URI:-bolt://localhost:${NEO4J_BOLT_PORT}}"
export NEO4J_HOST="${NEO4J_HOST:-localhost}"

# Force ingestion on startup (IngestionRunner prints the summary)
export GUIDE_RELOADCONTENTONSTARTUP=true

echo ""
echo "Starting Guide with profiles: $SPRING_PROFILES_ACTIVE"
echo "Neo4j: $NEO4J_URI"
echo ""
echo "Ingestion will run automatically on startup."
echo "Watch for the INGESTION COMPLETE banner."
echo "Press Ctrl+C to stop."
echo ""

# Run in foreground so Ctrl+C kills it directly
# Include scripts/user-config/ so Spring Boot finds personal profile files
./mvnw -DskipTests spring-boot:run -Dspring-boot.run.arguments="--spring.config.additional-location=file:./scripts/user-config/"
