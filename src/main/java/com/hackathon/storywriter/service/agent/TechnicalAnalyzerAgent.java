package com.hackathon.storywriter.service.agent;

import com.hackathon.storywriter.model.TestFailureEvent;
import com.hackathon.storywriter.service.CopilotCliService;
import com.hackathon.storywriter.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(TechnicalAnalyzerAgent.class);

    private final CopilotCliService copilot;
    private final int maxStacktraceChars;
    private final String model;

    public TechnicalAnalyzerAgent(
            CopilotCliService copilot,
            @Value("${copilot.cli.agents.technical-analyzer.model:${copilot.cli.model:gpt-4.1}}") String model,
            @Value("${copilot.cli.max-stacktrace-chars:3000}") int maxStacktraceChars) {
        this.copilot = copilot;
        this.model = model;
        this.maxStacktraceChars = maxStacktraceChars;
    }

    public String analyze(TestFailureEvent event) {
        log.debug("Analyzing failure: source={}, test={}", event.source(), event.testName());
        String stackTrace = Strings.truncate(event.stackTrace(), maxStacktraceChars);

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
                Strings.nvl(event.testName()),
                event.errorMessage(),
                Strings.nvl(stackTrace),
                Strings.nvl(event.context())
        );

        return copilot.ask("TechnicalAnalyzer", model, system, user);
    }


}
