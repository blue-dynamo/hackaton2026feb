package com.hackathon.storywriter.service.agent;

import com.hackathon.storywriter.model.ArtifactResponse.BugReport;
import com.hackathon.storywriter.model.TestFailureEvent;
import com.hackathon.storywriter.service.CopilotCliService;
import com.hackathon.storywriter.util.Strings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Bug Writer Agent.
 *
 * <p>Responsibility: produce a well-formatted bug report from the failure details,
 * including title, description, reproduction steps, expected vs actual behaviour.
 */
@Service
public class BugWriterAgent {

    private static final Logger log = LoggerFactory.getLogger(BugWriterAgent.class);
    private final CopilotCliService copilot;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String systemPrompt;
    private final String userTemplate;

    public BugWriterAgent(
            CopilotCliService copilot,
            ObjectMapper objectMapper,
            @Value("${copilot.cli.agents.bug-writer.model:${copilot.cli.model:gpt-4.1}}") String model,
            @Value("${copilot.cli.agents.bug-writer.system}") String systemPrompt,
            @Value("${copilot.cli.agents.bug-writer.user-template}") String userTemplate) {
        this.copilot = copilot;
        this.objectMapper = objectMapper;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.userTemplate = userTemplate;
    }

    public BugReport write(TestFailureEvent event, String technicalAnalysis, String rootCause) {
        String user = userTemplate.formatted(
                event.errorMessage(),
                event.source(),
                Strings.nvl(event.testName()),
                Strings.nvl(event.context()),
                technicalAnalysis,
                rootCause
        );

        String raw = copilot.ask("BugWriter", model, systemPrompt, user);
        return parseOrFallback(raw, event);
    }

    private BugReport parseOrFallback(String raw, TestFailureEvent event) {
        try {
            return objectMapper.readValue(Strings.stripCodeFence(raw), BugReport.class);
        } catch (Exception e) {
            log.warn("BugWriter agent response is not valid JSON, using raw text as description: {}", e.getMessage());
            return new BugReport(
                    "Bug: " + event.errorMessage(),
                    raw,
                    Strings.nvl(event.testName()),
                    "(not parsed)",
                    event.errorMessage(),
                    null,
                    0L
            );
        }
    }
}
