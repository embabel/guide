# PR: Cursor MCP Integration & Docker Improvements

## Summary

This PR enables **Cursor IDE** to connect to the Embabel MCP server by fixing Spring Security configuration issues that were blocking MCP endpoints. It also improves the Docker workflow so `docker compose up --build` works from a fresh clone without requiring a pre-built JAR.

### Key Changes

1. **Fix HTTP 403 errors** blocking Cursor from connecting to `/sse` and `/mcp` endpoints
2. **Multi-stage Docker build** so the image builds from source
3. **Flexible Docker Compose** with port override and optional services
4. **Documentation** for Cursor setup and testing
5. **Regression tests** to prevent future MCP security issues

---

## File-by-File Changes

### 1. `src/main/kotlin/com/embabel/guide/chat/security/SecurityConfig.kt`

**Type:** Modified  
**Lines Changed:** +37

#### Problem

Cursor was receiving `HTTP 403 Forbidden` when connecting to `/sse` and `/mcp` endpoints. Despite these paths being listed in `permittedPatterns`, Spring Security was still blocking them.

#### Root Cause

Spring Boot auto-configuration can contribute additional `SecurityFilterChain` beans that take precedence over custom configurations. When multiple filter chains exist, the first one that matches a request handles it—and if that chain doesn't explicitly permit the path, it defaults to denying access.

#### Solution

Three-layer defense to ensure MCP endpoints are never blocked:

**Layer 1: `WebSecurityCustomizer` (strongest)**

```kotlin
@Bean
fun webSecurityCustomizer(): WebSecurityCustomizer = WebSecurityCustomizer { web ->
    web.ignoring().requestMatchers(*mcpMatchers)
}
```

This completely bypasses the Spring Security filter chain for MCP paths. Requests to `/sse/**` and `/mcp/**` never touch security filters at all.

**Layer 2: Dedicated high-priority filter chain**

```kotlin
@Bean
@Order(0)
fun mcpFilterChain(http: HttpSecurity): SecurityFilterChain {
    http.securityMatcher(mcpMatcher)
        .csrf { it.disable() }
        .cors { }
        .authorizeHttpRequests { it.anyRequest().permitAll() }
    return http.build()
}
```

If `web.ignoring()` is ever removed, this `@Order(0)` chain catches MCP requests before any other chain.

**Layer 3: Explicit matchers in main filter chain**

```kotlin
it.requestMatchers(*mcpMatchers).permitAll()
```

Belt-and-suspenders addition to the existing `filterChain`.

#### Why `AntPathRequestMatcher`?

Spring's default `MvcRequestMatcher` only matches paths registered with Spring MVC. The `/sse` endpoint is registered by the MCP library directly with the servlet container, so `MvcRequestMatcher` doesn't see it. `AntPathRequestMatcher` matches any request path regardless of how it's registered.

#### Compiler Warnings

There are 4 deprecation warnings about `AntPathRequestMatcher` constructor. Spring recommends using `AntPathRequestMatcher.antMatcher("/path")` instead. The current code works correctly; this is cosmetic.

---

### 2. `Dockerfile`

**Type:** Modified  
**Lines Changed:** +8 / -7

#### Problem

The original Dockerfile expected a pre-built JAR in `target/`:

```dockerfile
COPY target/*.jar app.jar
```

This meant developers had to:

1. Install Java 21 and Maven locally
2. Run `mvn clean package -DskipTests`
3. Then run `docker compose up`

For a fresh clone, step 2 would fail if Java wasn't installed.

#### Solution

Convert to a **multi-stage build** that compiles inside Docker:

```dockerfile
# Stage 1: Build
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -q -DskipTests package

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
COPY --from=build /workspace/target/*.jar /app/app.jar
EXPOSE 1337
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Now `docker compose up --build` works from a fresh clone with only Docker installed.

#### Trade-offs

| Aspect        | Before                | After                      |
| ------------- | --------------------- | -------------------------- |
| Build time    | ~5 seconds (copy JAR) | ~2-3 minutes (Maven build) |
| Prerequisites | Java 21 + Maven       | Docker only                |
| Fresh clone   | ❌ Fails              | ✅ Works                   |
| CI/CD         | May need adjustment   | Works as-is                |

---

### 3. `.dockerignore`

**Type:** Modified  
**Lines Changed:** +8 / -9

#### Problem

The original `.dockerignore` excluded source files because the old Dockerfile only needed the pre-built JAR:

```
src/
pom.xml
mvnw
```

With the multi-stage build, we need source files in the Docker context.

#### Solution

Invert the logic—include source files, exclude build artifacts:

```
# Keep build context lean
target/

# Docs/images aren't needed to build the backend image
*.md
images/
```

Files now **included** in Docker context:

- `pom.xml` ✅
- `src/` ✅
- `mvnw`, `mvnw.cmd` ✅ (not used, but harmless)

Files **excluded**:

- `target/` (local build artifacts)
- `*.md`, `images/` (documentation)
- `.git/`, `.idea/`, etc. (IDE/VCS)

---

### 4. `compose.yaml`

**Type:** Modified  
**Lines Changed:** +6

#### Change 1: Parameterized Port

**Problem:** Port 1337 may already be in use by another service. Running the guide app caused:

```
Error: bind: address already in use
```

**Solution:** Use environment variable with default:

```yaml
ports:
  - '${GUIDE_PORT:-1337}:1337'
```

Usage:

```bash
# Default (port 1337)
docker compose up --build -d

# Override (port 1338)
GUIDE_PORT=1338 docker compose up --build -d
```

#### Change 2: Profile-Gated Services

**Problem:** Two services caused failures on fresh clones:

1. `neo4j-init` — requires `neo4j-init/init.sh` which may not exist
2. `frontend` — requires `../embabel-hub` repo checkout

**Solution:** Move to Compose profiles (opt-in):

```yaml
neo4j-init:
  profiles:
    - init
  # ...

frontend:
  profiles:
    - frontend
  # ...
```

Usage:

```bash
# Default: only neo4j + guide
docker compose up --build -d

# With frontend (requires ../embabel-hub)
COMPOSE_PROFILES=frontend docker compose up --build -d

# With neo4j-init (requires neo4j-init/init.sh)
COMPOSE_PROFILES=init docker compose up --build -d
```

---

### 5. `README.md`

**Type:** Modified  
**Lines Changed:** +131

#### New Section: "Consuming MCP Tools With Cursor"

Step-by-step instructions for connecting Cursor to the MCP server:

1. **Verify server is running** — `curl` command to check `/sse`
2. **Configure `~/.cursor/mcp.json`** — Example using `mcp-remote` stdio bridge
3. **Reload Cursor** — Note about "Developer: Reload Window" command
4. **Visual confirmation** — Embedded SVG screenshot

#### Expanded Section: "Docker"

- Changed `docker compose up` → `docker compose up --build -d`
- Added port conflict handling (`GUIDE_PORT`)
- Added Compose override variables documentation
- Added `COMPOSE_PROFILES=frontend` example
- Added MCP verification command

#### New Section: "Testing"

- Prerequisites: `OPENAI_API_KEY`, Neo4j
- Expected test count (38 tests)
- MCP Security regression test mention

#### New Section: "Local vs CI Testing"

Documents how to run tests locally vs in CI:

| Mode                  | `USE_LOCAL_NEO4J` | How Neo4j is provided |
| --------------------- | ----------------- | --------------------- |
| **CI (default)**      | unset/`false`     | Testcontainers        |
| **Local development** | `true`            | Docker Compose        |

Local developers can run:

```bash
docker compose up neo4j -d
USE_LOCAL_NEO4J=true ./mvnw test
```

#### Minor Formatting

- Added blank lines between code blocks (markdown best practice)
- Aligned table columns
- Fixed trailing commas in JavaScript example

---

### 6. `src/test/kotlin/com/embabel/guide/chat/security/McpSecurityTest.kt`

**Type:** New File  
**Lines:** 69

#### Purpose

Regression test to ensure MCP endpoints are never blocked by Spring Security. If someone accidentally breaks the security configuration, these tests will fail.

#### Tests

```kotlin
@Test
fun `MCP SSE endpoint should be accessible without authentication`() {
    mockMvc.perform(get("/sse"))
        .andExpect(status().isOk)
}

@Test
fun `MCP endpoint should be accessible without authentication`() {
    val result = mockMvc.perform(get("/mcp")).andReturn()
    val httpStatus = result.response.status
    assert(httpStatus != 401 && httpStatus != 403)
}

@Test
fun `MCP tools list endpoint should be accessible without authentication`() {
    val result = mockMvc.perform(get("/mcp/tools/list")).andReturn()
    val httpStatus = result.response.status
    assert(httpStatus != 401 && httpStatus != 403)
}
```

#### Design Decisions

1. **Uses `@SpringBootTest`** — Full application context, tests real security config
2. **Checks for NOT 401/403** — The endpoint might return 404 if not registered, which is fine; we only care that Security isn't blocking it
3. **Includes license header** — Matches project convention

---

### 7. `images/cursor-mcp-installed-servers.svg`

**Type:** New File  
**Lines:** 41

#### Purpose

Visual documentation showing what a successful Cursor MCP connection looks like.

#### Why SVG?

- **Text-based** — Git can diff it, no binary blobs
- **Small** — 41 lines, ~2KB
- **Matches Cursor theme** — Dark background (#0f1115), proper typography
- **Self-contained** — No external dependencies

#### Content

Shows the "Installed MCP Servers" panel with:

- Server name: `embabel-dev`
- Status: `38 tools enabled`
- Toggle: On (green)

---

### 8. `src/test/kotlin/com/embabel/guide/Neo4jTestContainer.kt`

**Type:** Modified  
**Lines Changed:** +5 / -2

#### Problem

The `USE_LOCAL_NEO4J` flag was a compile-time constant, requiring developers to edit source code to switch between local Neo4j and Testcontainers.

#### Solution

Changed from `const val` to environment variable:

```kotlin
// Before (required code edit)
const val USE_LOCAL_NEO4J = false

// After (environment-driven)
private val USE_LOCAL_NEO4J: Boolean = System.getenv("USE_LOCAL_NEO4J")?.toBoolean() ?: false
```

Now developers just run:

```bash
USE_LOCAL_NEO4J=true ./mvnw test
```

No code changes needed, no risk of accidentally committing `true`.

---

### 9. `src/test/kotlin/com/embabel/guide/TestApplicationContext.kt`

**Type:** Modified  
**Lines Changed:** +2 / -2

#### Change

Updated to use the `useLocalNeo4j()` function instead of directly accessing the now-private constant:

```kotlin
// Before
val useLocalNeo4j = Neo4jTestContainer.USE_LOCAL_NEO4J

// After
val useLocalNeo4j = Neo4jTestContainer.useLocalNeo4j()
```

---

### 10. `src/test/resources/application-test.yml`

**Type:** Modified  
**Lines Changed:** +2 / -2

#### Change

Updated comment to reflect the new environment variable approach:

```yaml
# Before
# Edit Neo4jTestContainer.USE_LOCAL_NEO4J constant in Neo4jTestContainer.kt

# After
# Set environment variable USE_LOCAL_NEO4J=true before running tests
```

---

### 11. `.gitignore`

**Type:** Modified  
**Lines Changed:** +3

#### Addition

```gitignore
# Runtime artifacts
*.pid
```

#### Reason

Running the Spring Boot app locally creates `guide-local.pid`. This file should not be committed.

---

## Testing

All 38 tests pass:

```
[INFO] Tests run: 38, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Breakdown:

- 18 HubApiControllerTest
- 6 HubServiceTest
- 8 DrivineGuideUserRepositoryTest
- 3 GuideUserServiceTest
- **3 McpSecurityTest** (new)

### Test Prerequisites

#### For CI (GitHub Actions)

Tests use Testcontainers to automatically spin up Neo4j. Just needs `OPENAI_API_KEY` secret configured.

#### For Local Development

For faster test runs during development, use a local Neo4j instance:

```bash
# 1. Start Neo4j
docker compose up neo4j -d

# 2. Set OpenAI API key
export OPENAI_API_KEY=sk-your-key-here

# 3. Run tests with local Neo4j
USE_LOCAL_NEO4J=true ./mvnw test
```

---

## Verification Steps

### Cursor MCP Connection

1. Start the server:

   ```bash
   ./mvnw spring-boot:run
   ```

2. Configure `~/.cursor/mcp.json`:

   ```json
   {
     "mcpServers": {
       "embabel-dev": {
         "command": "npx",
         "args": ["-y", "mcp-remote", "http://localhost:1337/sse", "--transport", "sse-only"]
       }
     }
   }
   ```

3. In Cursor: **Command Palette** → **Developer: Reload Window**

4. Check MCP panel shows "embabel-dev" with "38 tools enabled"

### Docker Build

```bash
# Fresh clone simulation
rm -rf target/

# Should succeed without local Maven/Java
docker compose up --build -d

# Verify
curl -i --max-time 3 http://localhost:1337/sse
# Expected: HTTP 200, Content-Type: text/event-stream
```

---

## Breaking Changes

### For Existing Developers

1. **Docker build is slower** — First build takes ~2-3 minutes (Maven inside Docker)

### Migration

No code changes required. Just be aware:

- `docker compose up` → `docker compose up --build -d` (add `--build`)
- Tests need `OPENAI_API_KEY` exported

---

## Files Changed Summary

| File                               | Change         | Impact                                       |
| ---------------------------------- | -------------- | -------------------------------------------- |
| `SecurityConfig.kt`                | +37 lines      | Fixes Cursor 403 errors                      |
| `Dockerfile`                       | Rewrite        | Enables fresh-clone Docker builds            |
| `.dockerignore`                    | Rewrite        | Supports multi-stage build                   |
| `compose.yaml`                     | +6 lines       | Adds port flexibility, optional services     |
| `README.md`                        | +170 lines     | Documents Cursor setup, Local vs CI testing  |
| `McpSecurityTest.kt`               | New (69 lines) | Prevents security regressions                |
| `cursor-mcp-installed-servers.svg` | New (41 lines) | Visual documentation                         |
| `Neo4jTestContainer.kt`            | +5 / -2 lines  | `USE_LOCAL_NEO4J` now env var (not constant) |
| `TestApplicationContext.kt`        | +2 / -2 lines  | Uses `useLocalNeo4j()` function              |
| `application-test.yml`             | +2 / -2 lines  | Updated comment for env var approach         |
| `.gitignore`                       | +3 lines       | Ignores `*.pid` files                        |
