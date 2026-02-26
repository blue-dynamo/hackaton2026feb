package com.hackathon.storywriter.controller;

import com.hackathon.storywriter.model.ArtifactResponse;
import com.hackathon.storywriter.model.TestFailureEvent;
import com.hackathon.storywriter.service.OrchestratorService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST entry point for the story-writer pipeline.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/events} — submit a test failure event and receive a full artifact JSON</li>
 *   <li>{@code GET  /_system/ping} — simple liveness check (see {@link SystemController})</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);

    private final OrchestratorService orchestratorService;

    public EventController(OrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    /**
     * Accepts a test-failure event, runs it through the multi-agent pipeline,
     * and returns the aggregated JSON artifact.
     *
     * <p>Example request body:
     * <pre>
     * {
     *   "source": "JUNIT",
     *   "testName": "com.example.PaymentServiceTest#shouldProcessPayment",
     *   "errorMessage": "Expected status 200 but was 500",
     *   "stackTrace": "java.lang.AssertionError: Expected 200...\n  at ...",
     *   "context": "Payment service, checkout module"
     * }
     * </pre>
     *
     * @param event validated test failure event payload
     * @return 200 OK with {@link ArtifactResponse} body, or 400 if validation fails
     */
    @PostMapping("/events")
    public ResponseEntity<ArtifactResponse> processEvent(@Valid @RequestBody TestFailureEvent event) {
        log.info("POST /api/events received: source={}, test={}", event.source(), event.testName());
        ArtifactResponse response = orchestratorService.process(event);
        return ResponseEntity.ok(response);
    }
}
