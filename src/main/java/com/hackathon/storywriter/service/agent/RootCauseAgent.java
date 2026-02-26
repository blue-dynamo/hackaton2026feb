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

    public RootCauseAgent(
            CopilotCliService copilot,
            @Value("${copilot.cli.agents.root-cause.model:${copilot.cli.model:gpt-4.1}}") String model) {
        this.copilot = copilot;
        this.model = model;
    }

    public String analyze(TestFailureEvent event, String technicalAnalysis) {
        log.debug("Analyzing root cause for: {}", event.errorMessage());
        String system = """
                You are a root-cause analysis expert with deep knowledge of Java, Spring Boot,
                JUnit, MockMvc, and Concordion testing frameworks.
                Identify the most probable root cause of the failure and propose fix directions.
                Be precise, evidence-based, and actionable.
                """;

        String user = """
                ## Root Cause Analysis Request

                **Error:** %s
                **Source:** %s
                **Test:** %s
                **Context:** %s

                **Technical Analysis (from analyzer agent):**
                %s

                Based on the above, provide:
                1. Most probable root cause (1-2 sentences, specific)
                2. Contributing factors (if any)
                3. Suggested fix directions (2-3 actionable items)
                4. What additional information would confirm this root cause
                """.formatted(
                event.errorMessage(),
                event.source(),
                Strings.nvl(event.testName()),
                Strings.nvl(event.context()),
                technicalAnalysis
        );

        return copilot.ask("RootCause", model, system, user);
    }


}
