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
 * <p>Responsibility: assess the priority / severity of the failure (P1–P4)
 * based on the error, its root cause, and the affected component.
 */
@Service
public class SeverityAgent {

    private static final Logger log = LoggerFactory.getLogger(SeverityAgent.class);
    private final CopilotCliService copilot;
    private final ObjectMapper objectMapper;
    private final String model;

    public SeverityAgent(
            CopilotCliService copilot,
            ObjectMapper objectMapper,
            @Value("${copilot.cli.agents.severity.model:${copilot.cli.model:gpt-4.1}}") String model) {
        this.copilot = copilot;
        this.objectMapper = objectMapper;
        this.model = model;
    }

    public SeverityAssessment assess(TestFailureEvent event, String technicalAnalysis, String rootCause) {
        String system = """
                You are a senior engineering manager expert in triaging software defects.
                Determine the priority severity of the reported failure using P1–P4 scale:
                  P1 = Critical / production blocker
                  P2 = High / major functionality broken
                  P3 = Medium / feature partially impacted
                  P4 = Low / minor issue or cosmetic
                You must respond with ONLY valid JSON matching this exact structure — no markdown, no explanation:
                {
                  "level": "<P1|P2|P3|P4>",
                  "rationale": "<2-3 sentence justification>",
                  "confidence": <float 0.0–1.0 — your confidence in this severity assessment>
                }
                """;

        String user = """
                ## Severity Assessment Request

                **Error:** %s
                **Source:** %s
                **Test:** %s
                **Context:** %s

                **Technical Analysis:**
                %s

                **Root Cause:**
                %s

                Assess the severity and return the JSON now.
                """.formatted(
                event.errorMessage(),
                event.source(),
                Strings.nvl(event.testName()),
                Strings.nvl(event.context()),
                technicalAnalysis,
                rootCause
        );

        String raw = copilot.ask("Severity", model, system, user);
        return parseOrFallback(raw);
    }

    private SeverityAssessment parseOrFallback(String raw) {
        try {
            return objectMapper.readValue(Strings.stripCodeFence(raw), SeverityAssessment.class);
        } catch (Exception e) {
            log.warn("Severity agent response is not valid JSON: {}", e.getMessage());
            return new SeverityAssessment("P3", raw, null, 0L);
        }
    }
}
