package com.hackathon.storywriter.service.agent;

import com.hackathon.storywriter.model.ArtifactResponse.UserStory;
import com.hackathon.storywriter.model.TestFailureEvent;
import com.hackathon.storywriter.service.CopilotCliService;
import com.hackathon.storywriter.util.Strings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final String model;

    public StoryWriterAgent(
            CopilotCliService copilot,
            ObjectMapper objectMapper,
            @Value("${copilot.cli.agents.story-writer.model:${copilot.cli.model:gpt-4.1}}") String model) {
        this.copilot = copilot;
        this.objectMapper = objectMapper;
        this.model = model;
    }

    public UserStory write(TestFailureEvent event, String rootCause) {
        String system = """
                You are a product owner and agile coach expert in writing user stories.
                Translate a technical bug / failure into a structured user story with four sections.
                You must respond with ONLY valid JSON matching this exact structure — no markdown, no explanation:
                {
                  "description": "<context and description of the problem: what it is, who is affected, and why it matters>",
                  "whatToDo": "<clear action items describing what needs to be implemented or fixed>",
                  "acceptanceCriteria": "<Gherkin Given/When/Then scenario(s) defining done>",
                  "additionalInformation": "<any extra context, related components, links, or notes relevant to the story>",
                  "confidence": <float 0.0–1.0 — your confidence in the relevance of this user story>
                }
                """;

        String user = """
                ## User Story Generation Request

                **Error:** %s
                **Source:** %s
                **Test / Origin:** %s
                **Context:** %s

                **Root Cause:**
                %s

                Generate the user story JSON now. Be business-oriented, not technical.
                - `description`: explain the context and impact in plain language.
                - `whatToDo`: list concrete actions the team must take.
                - `acceptanceCriteria`: use Gherkin Given/When/Then format.
                - `additionalInformation`: include component names, related tickets, or mitigation hints.
                """.formatted(
                event.errorMessage(),
                event.source(),
                Strings.nvl(event.testName()),
                Strings.nvl(event.context()),
                rootCause
        );

        String raw = copilot.ask("StoryWriter", model, system, user);
        return parseOrFallback(raw, event);
    }

    private UserStory parseOrFallback(String raw, TestFailureEvent event) {
        try {
            return objectMapper.readValue(Strings.stripCodeFence(raw), UserStory.class);
        } catch (Exception e) {
            log.warn("StoryWriter agent response is not valid JSON, using raw text: {}", e.getMessage());
            return new UserStory(
                    "fix: " + event.errorMessage(),
                    "Investigate and resolve the reported failure.",
                    "Given the system is running\nWhen the scenario is triggered\nThen it completes without error",
                    raw,
                    null,
                    0L
            );
        }
    }
}
