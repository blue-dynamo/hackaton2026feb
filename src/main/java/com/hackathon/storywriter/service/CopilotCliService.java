package com.hackathon.storywriter.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Thin wrapper around the {@code copilot} CLI.
 *
 * <p>Each agent call combines the system persona and the user prompt into a
 * single text block and invokes:
 * <pre>copilot --model &lt;model&gt; -s -p "&lt;combined prompt&gt;" --yolo</pre>
 * Flags used:
 * <ul>
 *   <li>{@code --model} — LLM model passed by each individual agent</li>
 *   <li>{@code -s} — silent / suppress interactive UI</li>
 *   <li>{@code -p} — non-interactive prompt</li>
 *   <li>{@code --yolo} — skip confirmation prompts, run non-interactively</li>
 * </ul>
 */
@Service
public class CopilotCliService {

    private static final Logger log = LoggerFactory.getLogger(CopilotCliService.class);

    @Value("${copilot.cli.timeout-seconds:60}")
    private int timeoutSeconds;

    /**
     * Sends {@code systemMsg} + {@code userPrompt} to the Copilot CLI and returns the
     * raw text response.
     *
     * @param agentRole  Short label used in logs (e.g. "TechnicalAnalyzer")
     * @param model      Model identifier passed to {@code copilot --model} (e.g. "gpt-4.1")
     * @param systemMsg  System message that configures the agent's persona
     * @param userPrompt Constructed user prompt with failure context
     * @return AI-generated text response
     */
    public String ask(String agentRole, String model, String systemMsg, String userPrompt) {
        log.info("[{}] Invoking copilot CLI (model={})", agentRole, model);
        return askViaCopilotCli(agentRole, model, systemMsg, userPrompt);
    }

    // -------------------------------------------------------------------------
    // copilot CLI
    // -------------------------------------------------------------------------

    private String askViaCopilotCli(String agentRole, String model, String systemMsg, String userPrompt) {
        // Combine system persona and user request into a single prompt.
        String combinedPrompt = systemMsg.strip() + "\n\n" + userPrompt.strip();

        log.debug("[{}] ── INPUT PROMPT ─────────────────────────────────────\n{}\n──────────────────────────────────────────────────────",
                agentRole, combinedPrompt);

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "copilot", "--model", model, "-s", "-p", combinedPrompt, "--yolo"
            );
            pb.redirectErrorStream(false);

            Process process = pb.start();
            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("[" + agentRole + "] copilot timed out after " + timeoutSeconds + "s");
            }

            if (process.exitValue() != 0) {
                log.error("[{}] copilot exited {}: {}", agentRole, process.exitValue(), stderr);
                throw new RuntimeException("[" + agentRole + "] copilot failed: " + stderr);
            }

            log.debug("[{}] ── OUTPUT ({} chars) ────────────────────────────────\n{}\n──────────────────────────────────────────────────────",
                    agentRole, stdout.length(), stdout);
            return stdout;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("[" + agentRole + "] copilot CLI failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String readStream(InputStream is) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
