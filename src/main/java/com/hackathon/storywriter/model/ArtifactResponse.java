package com.hackathon.storywriter.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Final aggregated artifact produced by the orchestrator from all sub-agent outputs.
 */
@Schema(description = "Aggregated artifact produced by the multi-agent pipeline")
public record ArtifactResponse(

        @Schema(description = "Technical analysis of the failure produced by TechnicalAnalyzerAgent")
        TechnicalAnalysis technicalAnalysis,

        @Schema(description = "Root cause identified by RootCauseAgent")
        RootCause rootCause,

        @Schema(description = "Structured bug report produced by BugWriterAgent")
        BugReport bugReport,

        @Schema(description = "User story produced by StoryWriterAgent in Connextra format")
        UserStory userStory,

        @Schema(description = "Severity assessment produced by SeverityAgent")
        SeverityAssessment severity,

        @Schema(description = "Total wall-clock time for the full pipeline (ms)")
        long totalMs
) {

    /**
     * Technical analysis produced by TechnicalAnalyzerAgent.
     */
    @Schema(description = "Technical analysis output")
    public record TechnicalAnalysis(
            @Schema(description = "Analysis text") String content,
            @Schema(description = "Agent execution time (ms)") long durationMs
    ) {}

    /**
     * Root cause produced by RootCauseAgent.
     */
    @Schema(description = "Root cause output")
    public record RootCause(
            @Schema(description = "Root cause text") String content,
            @Schema(description = "Agent execution time (ms)") long durationMs
    ) {}

    /**
     * Structured bug report produced by the Bug Writer Agent.
     */
    @Schema(description = "Structured bug report")
    public record BugReport(
            @Schema(description = "Short, descriptive title") String title,
            @Schema(description = "Detailed description of the defect") String description,
            @Schema(description = "Steps to reproduce the failure") String stepsToReproduce,
            @Schema(description = "What should have happened") String expectedBehavior,
            @Schema(description = "What actually happened") String actualBehavior,
            @Schema(description = "Model confidence in this report (0.0\u20131.0)") Double confidence,
            @Schema(description = "Agent execution time (ms)") long durationMs
    ) {}

    /**
     * User story produced by the Story Writer Agent.
     * Structured in four sections: description, whatToDo, acceptanceCriteria, additionalInformation.
     */
    @Schema(description = "User story with four structured sections")
    public record UserStory(
            @Schema(description = "Context and description of the problem / feature") String description,
            @Schema(description = "What needs to be done to address the issue") String whatToDo,
            @Schema(description = "Gherkin-style acceptance criteria") String acceptanceCriteria,
            @Schema(description = "Additional information, links, or notes") String additionalInformation,
            @Schema(description = "Model confidence (0.0\u20131.0)") Double confidence,
            @Schema(description = "Agent execution time (ms)") long durationMs
    ) {}

    /**
     * Severity assessment produced by the Severity Agent.
     */
    @Schema(description = "Severity assessment")
    public record SeverityAssessment(
            @Schema(description = "Severity level: P1 | P2 | P3 | P4", example = "P2") String level,
            @Schema(description = "Explanation for the chosen severity") String rationale,
            @Schema(description = "Model confidence (0.0\u20131.0)") Double confidence,
            @Schema(description = "Agent execution time (ms)") long durationMs
    ) {}
}
