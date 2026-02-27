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
    private final String systemPrompt;
    private final String userTemplate;

    public TechnicalAnalyzerAgent(
            CopilotCliService copilot,
            @Value("${copilot.cli.agents.technical-analyzer.model:${copilot.cli.model:gpt-4.1}}") String model,
            @Value("${copilot.cli.max-stacktrace-chars:3000}") int maxStacktraceChars,
            @Value("${copilot.cli.agents.technical-analyzer.system}") String systemPrompt,
            @Value("${copilot.cli.agents.technical-analyzer.user-template}") String userTemplate) {
        this.copilot = copilot;
        this.model = model;
        this.maxStacktraceChars = maxStacktraceChars;
        this.systemPrompt = systemPrompt;
        this.userTemplate = userTemplate;
    }

    public String analyze(TestFailureEvent event) {
        log.debug("Analyzing failure: source={}, test={}", event.source(), event.testName());
        String stackTrace = Strings.truncate(event.stackTrace(), maxStacktraceChars);

        String user = userTemplate.formatted(
                event.source(),
                Strings.nvl(event.testName()),
                event.errorMessage(),
                Strings.nvl(stackTrace),
                Strings.nvl(event.context())
        );

        return copilot.ask("TechnicalAnalyzer", model, systemPrompt, user);
    }


}
