package com.hackathon.storywriter.service.agent;

import com.hackathon.storywriter.model.TestFailureEvent;
import com.hackathon.storywriter.service.CopilotCliService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Technical Analyzer Agent.
 *
 * <p>Responsibility: parse the raw error / stacktrace and produce a clear
 * technical summary of what failed, what component is involved, and what
 * the error type is.
 */
@Service
public class TechnicalAnalyzerAgent {

    private final CopilotCliService copilot;

    @Value("${copilot.cli.max-stacktrace-chars:3000}")
    private int maxStacktraceChars;

    public TechnicalAnalyzerAgent(CopilotCliService copilot) {
        this.copilot = copilot;
    }

    public String analyze(TestFailureEvent event) {
        String stackTrace = truncate(event.stackTrace());

        String system = """
                You are a senior Java engineer specializing in diagnosing test failures.
                Analyze the provided failure and return a structured technical summary.
                Be concise, precise, and focus only on factual observations.
                Format your response as plain text with clear sections.
                """;

        String user = """
                ## Test Failure Technical Analysis Request

                **Source:** %s
                **Test:** %s
                **Error:** %s

                **Stack Trace:**
                ```
                %s
                ```

                **Additional Context:** %s

                Provide a technical analysis covering:
                1. Error type and classification
                2. Component / layer where the failure originated
                3. Key observations from the stack trace
                4. Whether this is likely a unit-level or integration-level issue
                """.formatted(
                event.source(),
                nvl(event.testName()),
                event.errorMessage(),
                nvl(stackTrace),
                nvl(event.context())
        );

        return copilot.ask("TechnicalAnalyzer", system, user);
    }

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() > maxStacktraceChars ? s.substring(0, maxStacktraceChars) + "\n... [truncated]" : s;
    }

    private String nvl(String s) {
        return s == null ? "(not provided)" : s;
    }
}
