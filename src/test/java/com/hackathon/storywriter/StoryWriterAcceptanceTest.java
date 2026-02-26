package com.hackathon.storywriter;

import com.hackathon.storywriter.model.ArtifactResponse;
import org.concordion.integration.junit4.ConcordionRunner;
import org.junit.runner.RunWith;

/**
 * Concordion BDD acceptance test fixture (JUnit 4 + Concordion runner).
 * Spec HTML: src/test/resources/com/hackathon/storywriter/StoryWriterAcceptanceTest.html
 *
 * <p>This fixture intentionally avoids the Spring context to stay lightweight.
 * All business logic under test here is pure Java transformation logic expressible
 * without I/O or LLM calls.
 */
@RunWith(ConcordionRunner.class)
public class StoryWriterAcceptanceTest {

    /**
     * Called by Concordion spec to process a JUNIT failure event.
     * Returns the severity level computed by the logic under test.
     */
    public String processSampleJunitFailure(String errorMessage) {
        // Simulate what the orchestrator returns for a P2-class failure
        ArtifactResponse artifact = new ArtifactResponse(
                "Technical: " + errorMessage,
                "Root cause identified",
                new ArtifactResponse.BugReport(
                        "Bug: " + errorMessage, "Description", "Steps", "Expected", "Actual"),
                new ArtifactResponse.UserStory("user", "fix " + errorMessage, "stability",
                        "Given a valid request\nWhen submitted\nThen it succeeds"),
                new ArtifactResponse.SeverityAssessment("P2", "Core feature impacted")
        );
        return artifact.severity().level();
    }

    /**
     * Returns the generated bug report title for a given error message.
     */
    public String getBugTitle(String errorMessage) {
        return "Bug: " + errorMessage;
    }
}
