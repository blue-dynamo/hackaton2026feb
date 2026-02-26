package com.hackathon.storywriter.service.agent;

import com.hackathon.storywriter.model.TestFailureEvent;
import com.hackathon.storywriter.service.CopilotCliService;
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

    private final CopilotCliService copilot;

    public RootCauseAgent(CopilotCliService copilot) {
        this.copilot = copilot;
    }

    public String analyze(TestFailureEvent event, String technicalAnalysis) {
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
                nvl(event.testName()),
                nvl(event.context()),
                technicalAnalysis
        );

        return copilot.ask("RootCause", system, user);
    }

    private String nvl(String s) {
        return s == null ? "(not provided)" : s;
    }
}
