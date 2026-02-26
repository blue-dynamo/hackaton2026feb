# AI-Empowered Event-Based Story & Bug Writer

## Project Structure

```
src/main/java/com/hackathon/storywriter/
	controller/EventController.java          ← POST /api/events
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

## Additional Information

- **Switch AI strategy:** Edit `src/main/resources/application.yml` to use `github-models` (default) or `explain` (demo fallback)
- **Test frameworks supported:** JUnit, Spring Boot Test/MockMvc, Concordion
- **All LLM calls** are routed through `CopilotCliService` (calls `gh api` or `gh copilot explain`)
- **No LLM in orchestrator:** OrchestratorService is deterministic, only routes/merges agent outputs
- **Tests:** Run `mvn test` (unit, integration, Concordion BDD)
- **Agent files:** See `.github/agents/*.agent.md` for Copilot agent definitions and handoff workflows
# hackaton2026feb
Learning event to boost out productivity with AI
