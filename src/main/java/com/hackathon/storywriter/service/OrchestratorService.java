package com.hackathon.storywriter.service;

import com.hackathon.storywriter.model.ArtifactResponse;
import com.hackathon.storywriter.model.ArtifactResponse.BugReport;
import com.hackathon.storywriter.model.ArtifactResponse.SeverityAssessment;
import com.hackathon.storywriter.model.ArtifactResponse.UserStory;
import com.hackathon.storywriter.model.TestFailureEvent;
import com.hackathon.storywriter.service.agent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

/**
 * Deterministic, non-LLM orchestrator that drives the multi-agent pipeline.
 *
 * <p><b>Execution pipeline (optimal parallel DAG):</b>
 * <pre>
 *  Phase 1:  TechnicalAnalyzer ──────────────────────────────────────┐
 *                                                                     │
 *  Phase 2:  (after Phase 1) RootCause ──────────────────────────────┤
 *                                                                     │
 *  Phase 3:  (after Phase 2, parallel):                              │
 *            BugWriter (needs tech + root)                           │
 *            StoryWriter (needs root)              ◄─────────────────┘
 *            Severity (needs tech + root)
 * </pre>
 *
 * <p>BugWriter, StoryWriter, and Severity are all launched concurrently once
 * their upstream dependencies are available.
 */
@Service
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);

    private final TechnicalAnalyzerAgent technicalAnalyzerAgent;
    private final RootCauseAgent rootCauseAgent;
    private final BugWriterAgent bugWriterAgent;
    private final StoryWriterAgent storyWriterAgent;
    private final SeverityAgent severityAgent;

    /**
     * Dedicated thread pool: one thread per agent branch to avoid blocking
     * the common ForkJoinPool used by the rest of the application.
     */
    private final ExecutorService executor = Executors.newFixedThreadPool(5,
            Thread.ofVirtual().name("agent-", 0).factory());

    public OrchestratorService(
            TechnicalAnalyzerAgent technicalAnalyzerAgent,
            RootCauseAgent rootCauseAgent,
            BugWriterAgent bugWriterAgent,
            StoryWriterAgent storyWriterAgent,
            SeverityAgent severityAgent) {
        this.technicalAnalyzerAgent = technicalAnalyzerAgent;
        this.rootCauseAgent = rootCauseAgent;
        this.bugWriterAgent = bugWriterAgent;
        this.storyWriterAgent = storyWriterAgent;
        this.severityAgent = severityAgent;
    }

    /**
     * Processes a test failure event through the full agent pipeline and
     * returns the aggregated {@link ArtifactResponse}.
     *
     * @param event the incoming test failure event
     * @return the validated, merged artifact
     */
    public ArtifactResponse process(TestFailureEvent event) {
        log.info("Orchestrator starting pipeline for event: source={}, test={}",
                event.source(), event.testName());

        // ── Phase 1: Technical Analyzer (no dependencies) ─────────────────────
        CompletableFuture<String> techFuture = CompletableFuture.supplyAsync(
                () -> {
                    log.debug("TechnicalAnalyzer starting");
                    String result = technicalAnalyzerAgent.analyze(event);
                    log.debug("TechnicalAnalyzer completed");
                    return result;
                }, executor);

        // ── Phase 2: Root Cause (depends on technical analysis) ───────────────
        CompletableFuture<String> rootFuture = techFuture.thenApplyAsync(
                tech -> {
                    log.debug("RootCause starting");
                    String result = rootCauseAgent.analyze(event, tech);
                    log.debug("RootCause completed");
                    return result;
                }, executor);

        // ── Phase 3a: Bug Writer (depends on tech + root) ─────────────────────
        CompletableFuture<BugReport> bugFuture = techFuture.thenCombineAsync(
                rootFuture,
                (tech, root) -> {
                    log.debug("BugWriter starting");
                    BugReport result = bugWriterAgent.write(event, tech, root);
                    log.debug("BugWriter completed");
                    return result;
                }, executor);

        // ── Phase 3b: Story Writer (depends on root cause) ────────────────────
        CompletableFuture<UserStory> storyFuture = rootFuture.thenApplyAsync(
                root -> {
                    log.debug("StoryWriter starting");
                    UserStory result = storyWriterAgent.write(event, root);
                    log.debug("StoryWriter completed");
                    return result;
                }, executor);

        // ── Phase 3c: Severity (depends on tech + root) ───────────────────────
        CompletableFuture<SeverityAssessment> severityFuture = techFuture.thenCombineAsync(
                rootFuture,
                (tech, root) -> {
                    log.debug("Severity starting");
                    SeverityAssessment result = severityAgent.assess(event, tech, root);
                    log.debug("Severity completed");
                    return result;
                }, executor);

        // ── Wait for all phase-3 agents to finish ─────────────────────────────
        try {
            CompletableFuture.allOf(bugFuture, storyFuture, severityFuture).join();

            ArtifactResponse artifact = new ArtifactResponse(
                    techFuture.get(),
                    rootFuture.get(),
                    bugFuture.get(),
                    storyFuture.get(),
                    severityFuture.get()
            );

            log.info("Orchestrator pipeline completed. Severity={}",
                    artifact.severity() != null ? artifact.severity().level() : "N/A");

            return artifact;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Agent pipeline interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Agent pipeline failed: " + e.getCause().getMessage(), e.getCause());
        }
    }
}
