package com.hackathon.storywriter.model;

/**
 * Final aggregated artifact produced by the orchestrator from all sub-agent outputs.
 */
public record ArtifactResponse(

        String technicalAnalysis,

        String rootCause,

        BugReport bugReport,

        UserStory userStory,

        SeverityAssessment severity
) {

    /**
     * Structured bug report produced by the Bug Writer Agent.
     *
     * @param title         Short, descriptive title
     * @param description   Detailed description of the defect
     * @param stepsToReproduce Steps to reproduce the failure
     * @param expectedBehavior What should have happened
     * @param actualBehavior   What actually happened
     */
    public record BugReport(
            String title,
            String description,
            String stepsToReproduce,
            String expectedBehavior,
            String actualBehavior
    ) {}

    /**
     * User story produced by the Story Writer Agent following Connextra format.
     *
     * @param asA               Role affected by the bug
     * @param iWant             Feature / behaviour desired
     * @param soThat           Business value
     * @param acceptanceCriteria Gherkin-style or plain-English acceptance criteria
     */
    public record UserStory(
            String asA,
            String iWant,
            String soThat,
            String acceptanceCriteria
    ) {}

    /**
     * Severity assessment produced by the Severity Agent.
     *
     * @param level     P1 | P2 | P3 | P4
     * @param rationale Explanation for the chosen severity
     */
    public record SeverityAssessment(
            String level,
            String rationale
    ) {}
}
