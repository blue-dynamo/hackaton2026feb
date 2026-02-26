# AI-Empowered Event-Based Story & Bug Writer

> A Spring Boot 3 / Java 21 service that automatically generates bug reports, user stories, and severity assessments from test failures and application logs — powered by the GitHub Copilot CLI and a multi-agent pipeline.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [How to Run](#how-to-run)
- [Sending Events](#sending-events)
- [API Documentation](#api-documentation)
- [Pipeline Architecture](#pipeline-architecture)
- [Agent Descriptions](#agent-descriptions)
- [Severity Levels](#severity-levels)
- [Running Tests](#running-tests)
- [Project Structure](#project-structure)
- [Configuration](#configuration)

---

## Prerequisites

| Tool | Purpose |
|------|---------|
| Java 21 | Runtime |
| Maven 3.9+ | Build tool |
| `copilot` CLI | LLM backbone — must be installed and authenticated |
| GitHub CLI (`gh`) | Used by `copilot` for authentication |

Authenticate before starting the service:

```bash
gh auth login
```

---

## How to Run

**Build:**
```bash
mvn clean package
```

**Start the service:**
```bash
mvn spring-boot:run
# Service starts at http://localhost:8080
```

**Liveness check:**
```bash
curl http://localhost:8080/_system/ping
# → {"status":"UP"}
```

---

## Sending Events

POST a `TestFailureEvent` JSON payload to `/api/events`. The service runs it through the full agent pipeline and returns an `ArtifactResponse`.

### Request fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `source` | enum | ✅ | `JUNIT`, `MOCK_MVC`, `CONCORDION`, `LOGS` |
| `errorMessage` | string | ✅ | The error or exception message |
| `testName` | string | — | Fully-qualified test method or origin |
| `stackTrace` | string | — | Full stack trace text |
| `context` | string | — | Free-text context (module, component, etc.) |

### Example — JUnit failure

```bash
curl -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -d '{
    "source": "JUNIT",
    "testName": "com.example.PaymentServiceTest#shouldProcessPayment",
    "errorMessage": "Expected status 200 but was 500",
    "stackTrace": "java.lang.AssertionError: Expected 200...\n  at ...",
    "context": "Payment service, checkout module"
  }'
```

### Example — Application log failure

```bash
curl -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -d '{
    "source": "LOGS",
    "testName": "net.nfon.portal.api.customer.group.service.GroupServiceCollectionEndpoint.post(GroupServiceCollectionEndpoint.java:103)",
    "errorMessage": "com.mysql.jdbc.jdbc2.optional.MysqlXAException: XA_RBDEADLOCK: Transaction branch was rolled back: deadlock was detected",
    "stackTrace": "com.mysql.jdbc.jdbc2.optional.MysqlXAException: XA_RBDEADLOCK: Transaction branch was rolled back: deadlock was detected\n\tat com.mysql.jdbc.jdbc2.optional.MysqlXAConnection.mapXAExceptionFromSQLException(MysqlXAConnection.java:583)\n\tat com.mysql.jdbc.jdbc2.optional.MysqlXAConnection.dispatchCommand(MysqlXAConnection.java:568)\n\tat com.mysql.jdbc.jdbc2.optional.MysqlXAConnection.end(MysqlXAConnection.java:464)",
    "context": "logs source; exception observed in GroupServiceCollectionEndpoint.post at line 103"
  }'
```

Or open `requests.http` in VS Code with the [REST Client](https://marketplace.visualstudio.com/items?itemName=humao.rest-client) extension and click **Send Request** on any example.

### Response shape (`ArtifactResponse`)

```json
{
  "technicalAnalysis": { "content": "...", "durationMs": 0 },
  "rootCause":         { "content": "...", "durationMs": 0 },
  "bugReport": {
    "title": "...", "description": "...", "stepsToReproduce": "...",
    "expectedBehavior": "...", "actualBehavior": "...",
    "confidence": 0.95, "durationMs": 0
  },
  "userStory": {
    "description": "...",
    "whatToDo": "...",
    "acceptanceCriteria": "Given ...\nWhen ...\nThen ...",
    "additionalInformation": "...",
    "confidence": 0.95, "durationMs": 0
  },
  "severity": { "level": "Blocker|Critical|Major|Minor", "rationale": "...", "confidence": 0.95, "durationMs": 0 },
  "totalMs": 0
}
```

---

## API Documentation

Once the service is running:

| Resource | URL |
|----------|-----|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| OpenAPI YAML | http://localhost:8080/v3/api-docs.yaml |

---

## Pipeline Architecture

```
POST /api/events  (TestFailureEvent JSON)
        │
OrchestratorService  (deterministic — no LLM)
        │
        ├── Phase 1:  TechnicalAnalyzerAgent
        │                       ↓
        ├── Phase 2:  RootCauseAgent
        │                       ↓ (parallel)
        └── Phase 3:  BugWriterAgent  ┐
                      StoryWriterAgent├──→  ArtifactResponse JSON
                      SeverityAgent   ┘
```

All LLM calls go through `CopilotCliService`, which invokes:
```
copilot --model <model> -s -p "<system_context + user_prompt>" --yolo
```

The orchestrator is purely deterministic Java (`CompletableFuture` DAG) — it only routes and merges results.

---

## Agent Descriptions

| Agent | Role |
|-------|------|
| **TechnicalAnalyzer** | Extracts technical facts: exception type, affected component, failure category |
| **RootCause** | Identifies the root cause from the technical analysis |
| **BugWriter** | Produces a structured bug report (title, description, steps, expected vs actual) |
| **StoryWriter** | Generates a 4-section user story (description, whatToDo, acceptanceCriteria, additionalInformation) |
| **Severity** | Assigns a severity level with rationale and confidence |

Agent definitions (with Copilot IDE handoff support) live in `.github/agents/*.agent.md`.

---

## Severity Levels

| Level | Definition |
|-------|-----------|
| **Blocker** | Production blocker, data loss, or security vulnerability — must fix immediately |
| **Critical** | Major feature completely broken, no workaround available |
| **Major** | Feature partially impacted, workaround exists |
| **Minor** | Cosmetic issue, edge case, or minimal business impact |

---

## Running Tests

Run the full test suite:

```bash
mvn test
```

The project uses three test types:

### 1. Unit tests — `OrchestratorServiceTest`

Located in `src/test/java/.../service/OrchestratorServiceTest.java`.

Tests the `OrchestratorService` in isolation with Mockito mocks for all five agents. Verifies the `CompletableFuture` DAG wiring, correct agent invocation order, result aggregation, and duration tracking. Also covers error handling when an agent throws.

```bash
mvn test -Dtest=OrchestratorServiceTest
```

### 2. MockMvc slice tests — `EventControllerTest`, `SystemControllerTest`

Located in `src/test/java/.../controller/`.

`EventControllerTest` uses `@WebMvcTest` to test the REST layer in isolation. The `OrchestratorService` is mocked; tests verify HTTP status codes, response JSON structure (`$.userStory.description`, `$.severity.level`, etc.), and Bean Validation for missing/invalid fields.

`SystemControllerTest` verifies that `GET /_system/ping` returns `200 OK` with `{"status":"UP"}`.

```bash
mvn test -Dtest=EventControllerTest,SystemControllerTest
```

### 3. Integration test — `StoryWriterIntegrationTest`

Located in `src/test/java/.../StoryWriterIntegrationTest.java`.

Loads the full Spring application context (`@SpringBootTest` + `MockMvc`). The orchestrator is mocked to return a pre-built `ArtifactResponse`. Verifies the end-to-end HTTP flow from request deserialization through controller to JSON serialization.

```bash
mvn test -Dtest=StoryWriterIntegrationTest
```

### 4. Concordion BDD acceptance test — `StoryWriterAcceptanceTest`

Located in `src/test/java/.../StoryWriterAcceptanceTest.java` with its spec in `src/test/resources/.../StoryWriterAcceptance.html`.

Uses [Concordion](https://concordion.org/) to execute a human-readable HTML specification as a test. The spec describes business scenarios ("Given a JUnit test failure… then the severity level returned should be Critical") and binds them to Java fixture methods. Concordion generates an annotated HTML report in `target/concordion/`.

```bash
mvn test -Dtest=StoryWriterAcceptanceTest
# Report: target/concordion/com/hackathon/storywriter/StoryWriterAcceptance.html
```

---

## Project Structure

```
src/main/java/com/hackathon/storywriter/
    controller/EventController.java          ← POST /api/events
    controller/SystemController.java         ← GET /_system/ping
    model/TestFailureEvent.java              ← input record
    model/ArtifactResponse.java              ← output record (with nested records)
    service/CopilotCliService.java           ← copilot CLI wrapper
    service/OrchestratorService.java         ← CompletableFuture DAG (no LLM)
    service/agent/TechnicalAnalyzerAgent.java
    service/agent/RootCauseAgent.java
    service/agent/BugWriterAgent.java
    service/agent/StoryWriterAgent.java
    service/agent/SeverityAgent.java

src/test/java/com/hackathon/storywriter/
    controller/EventControllerTest.java      ← @WebMvcTest slice
    controller/SystemControllerTest.java     ← @WebMvcTest slice
    service/OrchestratorServiceTest.java     ← unit tests (Mockito)
    StoryWriterIntegrationTest.java          ← full-context integration test
    StoryWriterAcceptanceTest.java           ← Concordion BDD fixture

.github/agents/
    orchestrator.agent.md
    technical-analyzer.agent.md
    root-cause.agent.md
    bug-writer.agent.md
    story-writer.agent.md
    severity.agent.md
```

---

## Configuration

Key settings in `src/main/resources/application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `copilot.cli.model` | `gpt-4.1` | Default model for all agents |
| `copilot.cli.agents.<name>.model` | inherits | Per-agent model override |
| `copilot.cli.timeout-seconds` | `120` | Timeout for each CLI call |

To change the model globally:
```yaml
copilot:
  cli:
    model: gpt-4.1-mini
```

To override per agent:
```yaml
copilot:
  cli:
    agents:
      severity:
        model: gpt-4.1-mini
```
