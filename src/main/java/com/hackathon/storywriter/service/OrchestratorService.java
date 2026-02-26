package com.hackathon.storywriter.service;

import com.hackathon.storywriter.model.ArtifactResponse;
import com.hackathon.storywriter.model.ArtifactResponse.BugReport;
import com.hackathon.storywriter.model.ArtifactResponse.RootCause;
import com.hackathon.storywriter.model.ArtifactResponse.SeverityAssessment;
import com.hackathon.storywriter.model.ArtifactResponse.TechnicalAnalysis;
import com.hackathon.storywriter.model.ArtifactResponse.UserStory;
import com.hackathon.storywriter.model.TestFailureEvent;
import com.hackathon.storywriter.service.agent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

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
     * Virtual-thread executor: each agent task runs on its own lightweight
     * virtual thread (Java 21+), eliminating fixed-pool sizing concerns.
     */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @PreDestroy
    public void shutdownExecutor() {
        executor.shutdown();
    }

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

        long pipelineStart = System.currentTimeMillis();
        AtomicLong techMs     = new AtomicLong();
        AtomicLong rootMs     = new AtomicLong();
        AtomicLong bugMs      = new AtomicLong();
        AtomicLong storyMs    = new AtomicLong();
        AtomicLong severityMs = new AtomicLong();

        // ── Phase 1: Technical Analyzer (no dependencies) ─────────────────────
        CompletableFuture<String> techFuture = CompletableFuture.supplyAsync(
                () -> {
                    long start = System.currentTimeMillis();
                    log.debug("TechnicalAnalyzer starting");
                    String result = technicalAnalyzerAgent.analyze(event);
                    techMs.set(System.currentTimeMillis() - start);
                    log.debug("TechnicalAnalyzer completed in {}ms", techMs.get());
                    return result;
                }, executor);

        // ── Phase 2: Root Cause (depends on technical analysis) ───────────────
        CompletableFuture<String> rootFuture = techFuture.thenApplyAsync(
                tech -> {
                    long start = System.currentTimeMillis();
                    log.debug("RootCause starting");
                    String result = rootCauseAgent.analyze(event, tech);
                    rootMs.set(System.currentTimeMillis() - start);
                    log.debug("RootCause completed in {}ms", rootMs.get());
                    return result;
                }, executor);

        // ── Phase 3a: Bug Writer (depends on tech + root) ─────────────────────
        CompletableFuture<BugReport> bugFuture = techFuture.thenCombineAsync(
                rootFuture,
                (tech, root) -> {
                    long start = System.currentTimeMillis();
                    log.debug("BugWriter starting");
                    BugReport result = bugWriterAgent.write(event, tech, root);
                    bugMs.set(System.currentTimeMillis() - start);
                    log.debug("BugWriter completed in {}ms", bugMs.get());
                    return result;
                }, executor);

        // ── Phase 3b: Story Writer (depends on root cause) ────────────────────
        CompletableFuture<UserStory> storyFuture = rootFuture.thenApplyAsync(
                root -> {
                    long start = System.currentTimeMillis();
                    log.debug("StoryWriter starting");
                    UserStory result = storyWriterAgent.write(event, root);
                    storyMs.set(System.currentTimeMillis() - start);
                    log.debug("StoryWriter completed in {}ms", storyMs.get());
                    return result;
                }, executor);

        // ── Phase 3c: Severity (depends on tech + root) ───────────────────────
        CompletableFuture<SeverityAssessment> severityFuture = techFuture.thenCombineAsync(
                rootFuture,
                (tech, root) -> {
                    long start = System.currentTimeMillis();
                    log.debug("Severity starting");
                    SeverityAssessment result = severityAgent.assess(event, tech, root);
                    severityMs.set(System.currentTimeMillis() - start);
                    log.debug("Severity completed in {}ms", severityMs.get());
                    return result;
                }, executor);

        // ── Wait for all phase-3 agents to finish ─────────────────────────────
        try {
            CompletableFuture.allOf(bugFuture, storyFuture, severityFuture).join();

            long totalMs = System.currentTimeMillis() - pipelineStart;

            // Wrap plain-text results in their record types with timing
            TechnicalAnalysis technicalAnalysis = new TechnicalAnalysis(techFuture.get(), techMs.get());
            RootCause rootCause = new RootCause(rootFuture.get(), rootMs.get());

            // Reconstruct JSON-parsed records with orchestrator-measured durationMs
            BugReport rawBug = bugFuture.get();
            BugReport bugReport = new BugReport(
                    rawBug.title(), rawBug.description(), rawBug.stepsToReproduce(),
                    rawBug.expectedBehavior(), rawBug.actualBehavior(), rawBug.confidence(),
                    bugMs.get());

            UserStory rawStory = storyFuture.get();
            UserStory userStory = new UserStory(
                    rawStory.asA(), rawStory.iWant(), rawStory.soThat(),
                    rawStory.acceptanceCriteria(), rawStory.confidence(),
                    storyMs.get());

            SeverityAssessment rawSeverity = severityFuture.get();
            SeverityAssessment severity = new SeverityAssessment(
                    rawSeverity.level(), rawSeverity.rationale(), rawSeverity.confidence(),
                    severityMs.get());

            ArtifactResponse artifact = new ArtifactResponse(
                    technicalAnalysis, rootCause, bugReport, userStory, severity, totalMs);

            log.info("Orchestrator pipeline completed in {}ms "
                             + "(tech={}ms root={}ms bug={}ms story={}ms severity={}ms). Severity={}",
                    totalMs, techMs.get(), rootMs.get(), bugMs.get(), storyMs.get(), severityMs.get(),
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
