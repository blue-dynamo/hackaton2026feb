# Copilot Agent Instructions for story-writer

## Project Summary

**story-writer** is a Spring Boot 3 / Java 21 service that uses GitHub Copilot CLI
(via `gh api` / GitHub Models) to automatically generate bug reports and user stories
from test failures (JUnit, MockMvc, Concordion) and application logs.

## Big Picture Architecture

```
POST /api/events  (TestFailureEvent JSON)
        │
OrchestratorService  (deterministic, non-LLM, Spring @Service)
        │
        ├── Phase 1:  TechnicalAnalyzerAgent  ──────────────────────────────────────┐
        │                                                                            │
        ├── Phase 2:  RootCauseAgent  (depends on Phase 1 output) ─────────────────┤
        │                                                                            │
        └── Phase 3 (parallel):                                                     │
             BugWriterAgent     (needs tech + root)    ◄──────────────────────────┘
             StoryWriterAgent   (needs root)
             SeverityAgent      (needs tech + root)
        │
ArtifactResponse  (JSON: technicalAnalysis, rootCause, bugReport, userStory, severity)
```

Each agent service calls `CopilotCliService`, which invokes the GitHub CLI:
- **github-models** strategy (default): `gh api POST https://models.inference.ai.azure.com/chat/completions`
- **explain** strategy (demo fallback): `gh copilot explain "<prompt>"`

- The project uses a modular, agent-driven architecture. Custom agents are defined in `.github/agents/` with clear roles (e.g., Orchestrator, Technical Analyzer, Root Cause, Bug Writer, Story Writer, Severity).
- Each agent file follows a strict YAML frontmatter and markdown structure, specifying description, tools, and model.
- Agents are orchestrated for multi-step workflows, with handoff support for sequential tasks.

## Critical Developer Workflows

- **Build:** `mvn clean package` — Java 21, Spring Boot 3.4.2
- **Run:** `mvn spring-boot:run` — starts on `http://localhost:8080`
- **Test:** `mvn test` — runs JUnit, MockMvc, and Concordion tests
- **Copilot CLI prerequisite:** `gh auth login` must be completed before running the service

### Switching AI Strategy

Edit `application.yml`:
```yaml
copilot:
  cli:
    strategy: github-models   # → gh api GitHub Models endpoint (default)
    # strategy: explain       # → gh copilot explain (demo fallback)
    model: gpt-4o-mini
```

### Sending a Test Event (curl)

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

## Project-Specific Conventions

- **Package root:** `com.hackathon.storywriter`
- **Models:** `TestFailureEvent` (input record), `ArtifactResponse` (output record with nested records)
- **Agent services:** all in `service/agent/` package, injected only with `CopilotCliService`
- **Orchestrator:** `OrchestratorService` — deterministic Java, no LLM. Uses `CompletableFuture` DAG.
- **No LLM in the orchestrator** — it only routes and merges. All LLM calls go through `CopilotCliService`
- **JSON parsing fallback:** all agents catch JSON parse errors and return a degraded but non-null result
- All agent `.agent.md` files follow the frontmatter checklist in `.github/instructions/agents.instructions.md`

## Source Tree

```
src/
  main/java/com/hackathon/storywriter/
    StoryWriterApplication.java
    controller/EventController.java          ← POST /api/events
    model/TestFailureEvent.java              ← input record
    model/ArtifactResponse.java              ← output record
    service/CopilotCliService.java           ← gh CLI wrapper
    service/OrchestratorService.java         ← deterministic pipeline
    service/agent/TechnicalAnalyzerAgent.java
    service/agent/RootCauseAgent.java
    service/agent/BugWriterAgent.java
    service/agent/StoryWriterAgent.java
    service/agent/SeverityAgent.java
  main/resources/application.yml
  test/java/com/hackathon/storywriter/
    controller/EventControllerTest.java      ← MockMvc slice tests
    service/OrchestratorServiceTest.java     ← unit tests
    StoryWriterIntegrationTest.java          ← full context integration test
    StoryWriterAcceptanceTest.java           ← Concordion BDD fixture
  test/resources/com/hackathon/storywriter/
    StoryWriterAcceptanceTest.html           ← Concordion spec

.github/agents/
  orchestrator.agent.md
  technical-analyzer.agent.md
  root-cause.agent.md
  bug-writer.agent.md
  story-writer.agent.md
  severity.agent.md
```

## Key Files

- `.github/agents/*.agent.md` — Custom Copilot agent definitions
- `.github/instructions/agents.instructions.md` — Agent creation guidelines
- `src/main/java/com/hackathon/storywriter/service/CopilotCliService.java` — GitHub CLI / Models integration
- `src/main/java/com/hackathon/storywriter/service/OrchestratorService.java` — agent DAG execution
- `src/main/resources/application.yml` — all configurable parameters
