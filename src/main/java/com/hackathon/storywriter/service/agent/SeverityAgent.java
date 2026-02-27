package com.hackathon.storywriter.service.agent;

import com.hackathon.storywriter.model.ArtifactResponse.SeverityAssessment;
import com.hackathon.storywriter.model.TestFailureEvent;
import com.hackathon.storywriter.service.CopilotCliService;
import com.hackathon.storywriter.util.Strings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Severity Agent.
 *
 * <p>Responsibility: assess the priority / severity of the failure
 * (Blocker, Critical, Major, Minor) based on the error, its root cause, and the affected component.
 */
@Service
public class SeverityAgent {

    private static final Logger log = LoggerFactory.getLogger(SeverityAgent.class);
    private final CopilotCliService copilot;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String systemPrompt;
    private final String userTemplate;

    public SeverityAgent(
            CopilotCliService copilot,
            ObjectMapper objectMapper,
            @Value("${copilot.cli.agents.severity.model:${copilot.cli.model:gpt-4.1}}") String model,
            @Value("${copilot.cli.agents.severity.system}") String systemPrompt,
            @Value("${copilot.cli.agents.severity.user-template}") String userTemplate) {
        this.copilot = copilot;
        this.objectMapper = objectMapper;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.userTemplate = userTemplate;
    }

    public SeverityAssessment assess(TestFailureEvent event, String technicalAnalysis, String rootCause) {
        String user = userTemplate.formatted(
                event.errorMessage(),
                event.source(),
                Strings.nvl(event.testName()),
                Strings.nvl(event.context()),
                technicalAnalysis,
                rootCause
        );

        String raw = copilot.ask("Severity", model, systemPrompt, user);
        return parseOrFallback(raw);
    }

    private SeverityAssessment parseOrFallback(String raw) {
        try {
            return objectMapper.readValue(Strings.stripCodeFence(raw), SeverityAssessment.class);
        } catch (Exception e) {
            log.warn("Severity agent response is not valid JSON: {}", e.getMessage());
            return new SeverityAssessment("Major", raw, null, 0L);
        }
    }
}
