package com.hackathon.storywriter.service.agent;

import com.hackathon.storywriter.model.ArtifactResponse.BugReport;
import com.hackathon.storywriter.model.TestFailureEvent;
import com.hackathon.storywriter.service.CopilotCliService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public BugWriterAgent(CopilotCliService copilot, ObjectMapper objectMapper) {
        this.copilot = copilot;
        this.objectMapper = objectMapper;
    }

    public BugReport write(TestFailureEvent event, String technicalAnalysis, String rootCause) {
        String system = """
                You are a QA engineer expert in writing clear, actionable bug reports.
                You must respond with ONLY valid JSON matching this exact structure — no markdown, no explanation:
                {
                  "title": "<short title ≤ 80 chars>",
                  "description": "<detailed description>",
                  "stepsToReproduce": "<numbered steps or test name>",
                  "expectedBehavior": "<what should happen>",
                  "actualBehavior": "<what actually happened>"
                }
                """;

        String user = """
                ## Bug Report Generation Request

                **Error:** %s
                **Source:** %s
                **Test:** %s
                **Context:** %s

                **Technical Analysis:**
                %s

                **Root Cause:**
                %s

                Generate the bug report JSON now.
                """.formatted(
                event.errorMessage(),
                event.source(),
                nvl(event.testName()),
                nvl(event.context()),
                technicalAnalysis,
                rootCause
        );

        String raw = copilot.ask("BugWriter", system, user);
        return parseOrFallback(raw, event);
    }

    private BugReport parseOrFallback(String raw, TestFailureEvent event) {
        try {
            // Strip markdown code fences if present
            String json = raw.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
            }
            return objectMapper.readValue(json, BugReport.class);
        } catch (Exception e) {
            log.warn("BugWriter agent response is not valid JSON, using raw text as description: {}", e.getMessage());
            return new BugReport(
                    "Bug: " + event.errorMessage(),
                    raw,
                    nvl(event.testName()),
                    "(not parsed)",
                    event.errorMessage()
            );
        }
    }

    private String nvl(String s) {
        return s == null ? "(not provided)" : s;
    }
}
