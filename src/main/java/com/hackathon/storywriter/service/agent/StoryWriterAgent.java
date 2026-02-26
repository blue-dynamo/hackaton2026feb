package com.hackathon.storywriter.service.agent;

import com.hackathon.storywriter.model.ArtifactResponse.UserStory;
import com.hackathon.storywriter.model.TestFailureEvent;
import com.hackathon.storywriter.service.CopilotCliService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Story Writer Agent.
 *
 * <p>Responsibility: translate the bug / failure into a user story in Connextra
 * format (As a... I want... So that...) with Gherkin-style acceptance criteria.
 */
@Service
public class StoryWriterAgent {

    private static final Logger log = LoggerFactory.getLogger(StoryWriterAgent.class);
    private final CopilotCliService copilot;
    private final ObjectMapper objectMapper;

    public StoryWriterAgent(CopilotCliService copilot, ObjectMapper objectMapper) {
        this.copilot = copilot;
        this.objectMapper = objectMapper;
    }

    public UserStory write(TestFailureEvent event, String rootCause) {
        String system = """
                You are a product owner and agile coach expert in writing user stories.
                Translate a technical bug / failure into a clear user story following Connextra format.
                You must respond with ONLY valid JSON matching this exact structure — no markdown, no explanation:
                {
                  "asA": "<role affected by the bug>",
                  "iWant": "<desired behaviour>",
                  "soThat": "<business value>",
                  "acceptanceCriteria": "<Gherkin Given/When/Then or bullet points>"
                }
                """;

        String user = """
                ## User Story Generation Request

                **Error:** %s
                **Source:** %s
                **Test:** %s
                **Context:** %s

                **Root Cause:**
                %s

                Generate the user story JSON now. Be business-oriented, not technical.
                The acceptance criteria should be in Gherkin Given/When/Then format.
                """.formatted(
                event.errorMessage(),
                event.source(),
                nvl(event.testName()),
                nvl(event.context()),
                rootCause
        );

        String raw = copilot.ask("StoryWriter", system, user);
        return parseOrFallback(raw, event);
    }

    private UserStory parseOrFallback(String raw, TestFailureEvent event) {
        try {
            String json = raw.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
            }
            return objectMapper.readValue(json, UserStory.class);
        } catch (Exception e) {
            log.warn("StoryWriter agent response is not valid JSON, using raw text: {}", e.getMessage());
            return new UserStory(
                    "developer",
                    "fix: " + event.errorMessage(),
                    "the application behaves correctly",
                    raw
            );
        }
    }

    private String nvl(String s) {
        return s == null ? "(not provided)" : s;
    }
}
