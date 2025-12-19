![Build](https://github.com/embabel/embabel-agent/actions/workflows/maven.yml/badge.svg)

![Java](https://img.shields.io/badge/java-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring](https://img.shields.io/badge/spring-%236DB33F.svg?style=for-the-badge&logo=spring&logoColor=white)
![Apache Tomcat](https://img.shields.io/badge/apache%20tomcat-%23F8DC75.svg?style=for-the-badge&logo=apache-tomcat&logoColor=black)
![Apache Maven](https://img.shields.io/badge/Apache%20Maven-C71A36?style=for-the-badge&logo=Apache%20Maven&logoColor=white)
![ChatGPT](https://img.shields.io/badge/chatGPT-74aa9c?style=for-the-badge&logo=openai&logoColor=white)
![JSON](https://img.shields.io/badge/JSON-000?logo=json&logoColor=fff)
![GitHub Actions](https://img.shields.io/badge/github%20actions-%232671E5.svg?style=for-the-badge&logo=githubactions&logoColor=white)
![Docker](https://img.shields.io/badge/docker-%230db7ed.svg?style=for-the-badge&logo=docker&logoColor=white)
![IntelliJ IDEA](https://img.shields.io/badge/IntelliJIDEA-000000.svg?style=for-the-badge&logo=intellij-idea&logoColor=white)

# Embabel Hub Backend: Chat and MCP Server

<img src="https://github.com/embabel/embabel-agent/blob/main/embabel-agent-api/images/315px-Meister_der_Weltenchronik_001.jpg?raw=true" width="180">

Embabel Hub exposes resources relating to the Embabel Agent Framework, such
as documentation, relevant blogs and other content, and up-to-the-minute API information.

This is exposed in two ways:

- Via a chatbot for the Embabel Hub front end
- Via an MCP server for integration with Claude Desktop, Claude Code and
  other MCP clients

## Loading data

```bash
curl -X POST http://localhost:1337/api/v1/data/load-references
```

To see stats on data, make a GET request or browse to http://localhost:1337/api/v1/data/stats

## Viewing and Deleting Data

Go to the Neo Browser at http://localhost:7474/browser/

Log in with username `neo4j` and password `brahmsian` (or your custom password if you set one).

To delete all data run the following query:

```cypher

MATCH (n:ContentElement)
DETACH DELETE n
```

## Exposing MCP Tools

Starting the server will expose MCP tools on `http://localhost:1337/sse`.

### Verifying With MCP Inspector (Optional)

An easy way to verify the tools are exposed and experiment with calling them is by running the MCP inspector:

```bash
npx @modelcontextprotocol/inspector
```

Within the inspector UI, connect to `http://localhost:1337/sse`.

### Consuming MCP Tools With Claude Desktop

Add this stanza to `claude_desktop_config.json`:

```yml
{
  "mcpServers": {

    "embabel-dev": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote",
        "http://localhost:1337/sse",
        "--transport",
        "sse-only"
      ]
    },
                  ...
  }
```

See [Connect Local Servers](https://modelcontextprotocol.io/docs/develop/connect-local-servers) for detailed
documentation.

### Consuming MCP Tools With Claude Code

If you're using Claude Code, adding the Embabel MCP server will
powerfully augment its capabilities for working on Embabel applications
and helping you learn Embabel.

```bash
claude mcp add embabel --transport sse http://localhost:1337/sse
```

Within the Claude Code shell, type `/mcp` to test the connection. Choose the number of the `embabel` server to check its
status.

Start via `claude --debug` to see more logging.

See [Claude Code MCP documentation](https://code.claude.com/docs/en/mcp) for further information.

#### Auto-Approving Embabel MCP Tools

By default, Claude Code asks for confirmation before running MCP tools. When you accept a tool with "Yes, don't ask
again", Claude Code saves that permission to your local `.claude/settings.local.json` file (which is auto-ignored by
git).

**Note:** Wildcards do not work for MCP tool permissions. Each tool must be approved individually or listed explicitly
in your settings.

**Tool naming:** By default, `guide.toolPrefix` is empty, so MCP tools are exposed with their original names (e.g.,
`mcp__embabel__docs_vectorSearch`). You can set a custom prefix in your application configuration to namespace your
tools.

See [Claude Code Permission Modes](https://code.claude.com/docs/en/iam#permission-modes) for detailed documentation on
how permissions work.

## Writing a Client

The backend supports any client via WebSocket (for real-time chat) and REST (for authentication).

### WebSocket Chat API

**Endpoint:** `ws://localhost:1337/ws`

Uses STOMP protocol over WebSocket with SockJS fallback. Any STOMP client library works (e.g., `@stomp/stompjs` for
JavaScript, `stomp.py` for Python).

**Authentication:** Pass an optional JWT token as a query parameter:

```
ws://localhost:1337/ws?token=<JWT>
```

If no token is provided, an anonymous user is created automatically.

#### STOMP Channels

| Direction | Destination             | Purpose                       |
|-----------|-------------------------|-------------------------------|
| Subscribe | `/user/queue/messages`  | Receive chat responses        |
| Subscribe | `/user/queue/status`    | Receive typing/status updates |
| Publish   | `/app/chat.sendToJesse` | Send message to AI bot        |
| Publish   | `/app/presence.ping`    | Keep-alive (send every 30s)   |

#### Message Formats

**Sending a message:**

```json
{
  "body": "your message here"
}
```

**Receiving a message:**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "content": "response text",
  "userId": "bot:jesse",
  "userName": "Jesse",
  "timestamp": "2025-12-16T10:30:00Z"
}
```

**Receiving a status update:**

```json
{
  "fromUserId": "bot:jesse",
  "status": "typing"
}
```

### REST API

CORS is open (`*`), no special headers required beyond `Content-Type: application/json`.

#### Authentication Endpoints

**Register:**

```
POST /api/hub/register
{
  "userDisplayName": "Jane Doe",
  "username": "jane",
  "userEmail": "jane@example.com",
  "password": "secret",
  "passwordConfirmation": "secret"
}
```

**Login:**

```
POST /api/hub/login
{ "username": "jane", "password": "secret" }

Response:
{ "token": "eyJhbG...", "userId": "...", "username": "jane", ... }
```

**List Personas:**

```
GET /api/hub/personas
```

**Update Persona** (requires auth):

```
PUT /api/hub/persona/mine
Authorization: Bearer <JWT>
{ "persona": "persona_name" }
```

### Example: Minimal JavaScript Client

```javascript
import {Client} from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const client = new Client({
    webSocketFactory: () => new SockJS('http://localhost:1337/ws'),
    onConnect: () => {
        // Subscribe to responses
        client.subscribe('/user/queue/messages', (frame) => {
            const message = JSON.parse(frame.body);
            console.log('Received:', message.content);
        });

        // Send a message
        client.publish({
            destination: '/app/chat.sendToJesse',
            body: JSON.stringify({body: 'Hello!'})
        });
    }
});

client.activate();
```

## Docker

Run with Docker Compose:

```bash
docker compose up
```

### Environment Variables

| Variable         | Default                        | Description            |
|------------------|--------------------------------|------------------------|
| `NEO4J_VERSION`  | `2025.10.1-community-bullseye` | Neo4j Docker image tag |
| `NEO4J_USERNAME` | `neo4j`                        | Neo4j username         |
| `NEO4J_PASSWORD` | `brahmsian`                    | Neo4j password         |
| `OPENAI_API_KEY` | (required)                     | OpenAI API key         |
| `DISCORD_TOKEN`  | (optional)                     | Discord bot token      |

Example:

```bash
NEO4J_PASSWORD=mysecretpassword OPENAI_API_KEY=sk-... docker compose up
```

## Miscellaneous

Sometimes (for example if your IDE crashes) you will be left with an orphaned server process and won't be able to
restart.
To kill the server:

```aiignore
lsof -ti:1337 | xargs kill -9
```


