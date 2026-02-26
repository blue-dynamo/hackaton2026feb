package com.hackathon.storywriter.model;

/**
 * Final aggregated artifact produced by the orchestrator from all sub-agent outputs.
 */
public record ArtifactResponse(

        String technicalAnalysis,

        String rootCause,

        BugReport bugReport,

        UserStory userStory,

        SeverityAssessment severity,

        PipelineMetrics metrics
) {

    /**
     * Structured bug report produced by the Bug Writer Agent.
     *
     * @param title         Short, descriptive title
     * @param description   Detailed description of the defect
     * @param stepsToReproduce Steps to reproduce the failure
     * @param expectedBehavior What should have happened
     * @param actualBehavior   What actually happened
     * @param confidence       Model confidence in this report (0.0–1.0), or null if unavailable
     */
    public record BugReport(
            String title,
            String description,
            String stepsToReproduce,
            String expectedBehavior,
            String actualBehavior,
            Double confidence
    ) {}

    /**
     * User story produced by the Story Writer Agent following Connextra format.
     *
     * @param asA               Role affected by the bug
     * @param iWant             Feature / behaviour desired
     * @param soThat           Business value
     * @param acceptanceCriteria Gherkin-style or plain-English acceptance criteria
     * @param confidence         Model confidence in this story (0.0–1.0), or null if unavailable
     */
    public record UserStory(
            String asA,
            String iWant,
            String soThat,
            String acceptanceCriteria,
            Double confidence
    ) {}

    /**
     * Severity assessment produced by the Severity Agent.
     *
     * @param level      P1 | P2 | P3 | P4
     * @param rationale  Explanation for the chosen severity
     * @param confidence Model confidence in this assessment (0.0–1.0), or null if unavailable
     */
    public record SeverityAssessment(
            String level,
            String rationale,
            Double confidence
    ) {}

    /**
     * Per-agent execution timing telemetry for the full pipeline.
     *
     * @param technicalAnalyzerMs Wall-clock time spent in TechnicalAnalyzerAgent (ms)
     * @param rootCauseMs         Wall-clock time spent in RootCauseAgent (ms)
     * @param bugWriterMs         Wall-clock time spent in BugWriterAgent (ms)
     * @param storyWriterMs       Wall-clock time spent in StoryWriterAgent (ms)
     * @param severityMs          Wall-clock time spent in SeverityAgent (ms)
     * @param totalMs             Total wall-clock time for the full pipeline (ms)
     */
    public record PipelineMetrics(
            long technicalAnalyzerMs,
            long rootCauseMs,
            long bugWriterMs,
            long storyWriterMs,
            long severityMs,
            long totalMs
    ) {}
}
