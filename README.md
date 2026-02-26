# AI-Empowered Event-Based Story & Bug Writer

## Project Structure

```
src/main/java/com/hackathon/storywriter/
	controller/EventController.java          ← POST /api/events
	controller/SystemController.java         ← GET /_system/ping
	model/TestFailureEvent.java              ← input record (source, testName, errorMessage, stackTrace, context)
	model/ArtifactResponse.java              ← output record (technicalAnalysis, rootCause, bugReport, userStory, severity)
	service/CopilotCliService.java           ← gh CLI wrapper (github-models OR explain strategy)
	service/OrchestratorService.java         ← CompletableFuture DAG — no LLM
	service/agent/TechnicalAnalyzerAgent.java
	service/agent/RootCauseAgent.java
	service/agent/BugWriterAgent.java
	service/agent/StoryWriterAgent.java
	service/agent/SeverityAgent.java

.github/agents/                            Copilot IDE agent files with handoffs
	orchestrator.agent.md
	technical-analyzer.agent.md
	root-cause.agent.md
	bug-writer.agent.md
	story-writer.agent.md
	severity.agent.md
```

## Pipeline Execution

```
POST /api/events
			↓
Phase 1: TechnicalAnalyzer
			↓
Phase 2: RootCause
			↓ (parallel)
Phase 3: BugWriter + StoryWriter + Severity  →  merged ArtifactResponse JSON
```

## How to Run

1. Authenticate GitHub CLI:
	 ```bash
	 gh auth login
	 ```
2. Start the service:
	 ```bash
	 mvn spring-boot:run
	 # Service runs at http://localhost:8080
	 ```
3. Send a test event:
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
	 Or open `requests.http` in VS Code with the [REST Client](https://marketplace.visualstudio.com/items?itemName=humao.rest-client) extension and click **Send Request** on any example.

## API Documentation

Once the service is running, the interactive Swagger UI and the raw OpenAPI spec are available at:

| Resource | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON spec | http://localhost:8080/v3/api-docs |
| OpenAPI YAML spec | http://localhost:8080/v3/api-docs.yaml |

- **AI backbone:** All LLM calls use the `copilot` CLI — `copilot --model gpt-4.1 -s -p "<prompt>" --yolo`
- **Prerequisite:** `copilot` must be installed and authenticated before starting the service
- **Change model:** Edit `copilot.cli.model` in `src/main/resources/application.yml`
- **Test frameworks supported:** JUnit, Spring Boot Test/MockMvc, Concordion
- **All LLM calls** are routed through `CopilotCliService` (`copilot --model <model> -s -p "<prompt>" --yolo`)
- **No LLM in orchestrator:** OrchestratorService is deterministic, only routes/merges agent outputs
- **Tests:** Run `mvn test` (unit, integration, Concordion BDD)
- **Agent files:** See `.github/agents/*.agent.md` for Copilot agent definitions and handoff workflows
- **API endpoints:** `POST /api/events` — pipeline entry point; `GET /_system/ping` — liveness probe
# hackaton2026feb
Learning event to boost out productivity with AI
