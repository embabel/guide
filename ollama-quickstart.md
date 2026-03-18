# Ollama Quickstart

Run the Embabel Guide locally with [Ollama](https://ollama.com) — no API key required.

## Prerequisites

- Java 21+
- Docker (for Neo4j)
- macOS (adjust brew commands for Linux)

## Setup

**1. Install Ollama and pull models:**

```bash
brew install ollama
brew services start ollama
ollama pull llama3.2
ollama pull nomic-embed-text
```

**2. Start Neo4j** (choose one):

*Option A — Docker:*
```bash
docker run -p 7474:7474 -p 7687:7687 -e NEO4J_AUTH=neo4j/brahmsian neo4j:latest
```

*Option B — Use your own Neo4j instance:*

Set environment variables to point at it before running:
```bash
export NEO4J_URI=bolt://your-host:7687
export NEO4J_USERNAME=neo4j
export NEO4J_PASSWORD=your-password
```

Or add them to a `.envrc` file (if you use [direnv](https://direnv.net/)):
```bash
export NEO4J_URI=bolt://your-host:7687
export NEO4J_USERNAME=neo4j
export NEO4J_PASSWORD=your-password
```

**3. Run the app:**

```bash
mvn spring-boot:run
```

The app starts on port 1337.

## Load Content

Once running, load the documentation into the RAG store:

```bash
curl -X POST http://localhost:1337/api/v1/data/load-references
```

## MCP

MCP is exposed via SSE at `http://localhost:1337/sse`. Configure your client:

**Claude Desktop** (`claude_desktop_config.json`):
```json
{
  "mcpServers": {
    "embabel-guide": {
      "url": "http://localhost:1337/sse"
    }
  }
}
```

**Claude Code:**
```bash
claude mcp add embabel-guide --transport sse http://localhost:1337/sse
```

**MCP Inspector** (to verify tools are working):
```bash
npx @modelcontextprotocol/inspector
```
Then connect to `http://localhost:1337/sse`.

## Switching Models

Edit `application.yml` and change the model names. Models with better tool-calling support:

```bash
ollama pull qwen2.5
ollama pull mistral-nemo
```

Then update `application.yml`:
```yaml
guide:
  chat-llm:
    model: qwen2.5:latest
```