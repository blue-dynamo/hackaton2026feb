package com.hackathon.storywriter.service.agent;

import com.hackathon.storywriter.model.ArtifactResponse.SeverityAssessment;
import com.hackathon.storywriter.model.TestFailureEvent;
import com.hackathon.storywriter.service.CopilotCliService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public SeverityAgent(CopilotCliService copilot, ObjectMapper objectMapper) {
        this.copilot = copilot;
        this.objectMapper = objectMapper;
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
                  "rationale": "<2-3 sentence justification>"
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
                nvl(event.testName()),
                nvl(event.context()),
                technicalAnalysis,
                rootCause
        );

        String raw = copilot.ask("Severity", system, user);
        return parseOrFallback(raw);
    }

    private SeverityAssessment parseOrFallback(String raw) {
        try {
            String json = raw.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
            }
            return objectMapper.readValue(json, SeverityAssessment.class);
        } catch (Exception e) {
            log.warn("Severity agent response is not valid JSON: {}", e.getMessage());
            return new SeverityAssessment("P3", raw);
        }
    }

    private String nvl(String s) {
        return s == null ? "(not provided)" : s;
    }
}
