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
    private final String systemPrompt;
    private final String userTemplate;

    public StoryWriterAgent(
            CopilotCliService copilot,
            ObjectMapper objectMapper,
            @Value("${copilot.cli.agents.story-writer.model:${copilot.cli.model:gpt-4.1}}") String model,
            @Value("${copilot.cli.agents.story-writer.system}") String systemPrompt,
            @Value("${copilot.cli.agents.story-writer.user-template}") String userTemplate) {
        this.copilot = copilot;
        this.objectMapper = objectMapper;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.userTemplate = userTemplate;
    }

    public UserStory write(TestFailureEvent event, String rootCause) {
        String user = userTemplate.formatted(
                event.errorMessage(),
                event.source(),
                Strings.nvl(event.testName()),
                Strings.nvl(event.context()),
                rootCause
        );

        String raw = copilot.ask("StoryWriter", model, systemPrompt, user);
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
