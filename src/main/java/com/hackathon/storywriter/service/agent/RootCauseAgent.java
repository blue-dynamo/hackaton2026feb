package com.hackathon.storywriter.service.agent;

import com.hackathon.storywriter.model.TestFailureEvent;
import com.hackathon.storywriter.service.CopilotCliService;
import com.hackathon.storywriter.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Root Cause Agent.
 *
 * <p>Responsibility: given the technical analysis and the raw event, deduce
 * the most probable root cause of the failure and suggest fix directions.
 * This agent receives the output of the Technical Analyzer as additional input.
 */
@Service
public class RootCauseAgent {

    private static final Logger log = LoggerFactory.getLogger(RootCauseAgent.class);

    private final CopilotCliService copilot;
    private final String model;
    private final String systemPrompt;
    private final String userTemplate;

    public RootCauseAgent(
            CopilotCliService copilot,
            @Value("${copilot.cli.agents.root-cause.model:${copilot.cli.model:gpt-4.1}}") String model,
            @Value("${copilot.cli.agents.root-cause.system}") String systemPrompt,
            @Value("${copilot.cli.agents.root-cause.user-template}") String userTemplate) {
        this.copilot = copilot;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.userTemplate = userTemplate;
    }

    public String analyze(TestFailureEvent event, String technicalAnalysis) {
        log.debug("Analyzing root cause for: {}", event.errorMessage());
        String user = userTemplate.formatted(
                event.errorMessage(),
                event.source(),
                Strings.nvl(event.testName()),
                Strings.nvl(event.context()),
                technicalAnalysis
        );

        return copilot.ask("RootCause", model, systemPrompt, user);
    }


}
