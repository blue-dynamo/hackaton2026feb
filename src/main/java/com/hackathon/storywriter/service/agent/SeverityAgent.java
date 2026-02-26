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
                Determine the priority severity of the reported failure using this scale:
                  Blocker  = production blocker, data loss, or security vulnerability
                  Critical = major feature completely broken, no workaround available
                  Major    = feature partially impacted, workaround exists
                  Minor    = cosmetic or edge case with minimal business impact
                You must respond with ONLY valid JSON matching this exact structure — no markdown, no explanation:
                {
                  "level": "<Blocker|Critical|Major|Minor>",
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
            return new SeverityAssessment("Major", raw, null, 0L);
        }
    }
}
