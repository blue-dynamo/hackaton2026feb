package com.hackathon.storywriter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Thin wrapper around the GitHub Copilot CLI / GitHub Models API.
 *
 * <p>Strategy {@code github-models}: calls
 * <pre>gh api POST https://models.inference.ai.azure.com/chat/completions</pre>
 * using the currently authenticated GitHub token.
 *
 * <p>Strategy {@code explain}: calls {@code gh copilot explain "<prompt>"}
 * for a lightweight demo without GitHub Models access.
 */
@Service
public class CopilotCliService {

    private static final Logger log = LoggerFactory.getLogger(CopilotCliService.class);

    @Value("${copilot.cli.strategy:github-models}")
    private String strategy;

    @Value("${copilot.cli.models-endpoint:https://models.inference.ai.azure.com/chat/completions}")
    private String modelsEndpoint;

    @Value("${copilot.cli.model:gpt-4o-mini}")
    private String model;

    @Value("${copilot.cli.timeout-seconds:60}")
    private int timeoutSeconds;

    private final ObjectMapper objectMapper;

    public CopilotCliService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Sends {@code prompt} to GitHub Copilot (via CLI) and returns the raw text response.
     *
     * @param agentRole  Short label used in logs (e.g. "TechnicalAnalyzer")
     * @param systemMsg  System message that configures the agent's persona
     * @param userPrompt Constructed user prompt with failure context
     * @return AI-generated text response
     */
    public String ask(String agentRole, String systemMsg, String userPrompt) {
        log.info("[{}] Invoking Copilot CLI (strategy={})", agentRole, strategy);

        return switch (strategy) {
            case "github-models" -> askViaGithubModels(agentRole, systemMsg, userPrompt);
            case "explain" -> askViaCopilotExplain(agentRole, userPrompt);
            default -> throw new IllegalStateException("Unknown strategy: " + strategy);
        };
    }

    // -------------------------------------------------------------------------
    // GitHub Models strategy
    // -------------------------------------------------------------------------

    private String askViaGithubModels(String agentRole, String systemMsg, String userPrompt) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);

            ArrayNode messages = body.putArray("messages");
            ObjectNode systemNode = messages.addObject();
            systemNode.put("role", "system");
            systemNode.put("content", systemMsg);

            ObjectNode userNode = messages.addObject();
            userNode.put("role", "user");
            userNode.put("content", userPrompt);

            String jsonBody = objectMapper.writeValueAsString(body);

            ProcessBuilder pb = new ProcessBuilder(
                    "gh", "api",
                    "--method", "POST",
                    modelsEndpoint,
                    "--header", "Content-Type: application/json",
                    "--input", "-"
            );
            pb.redirectErrorStream(false);

            Process process = pb.start();

            // Write JSON body to stdin
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("[" + agentRole + "] gh api timed out after " + timeoutSeconds + "s");
            }

            if (process.exitValue() != 0) {
                log.error("[{}] gh api exited with code {}: {}", agentRole, process.exitValue(), stderr);
                throw new RuntimeException("[" + agentRole + "] gh api failed: " + stderr);
            }

            // Parse OpenAI-compatible response
            JsonNode root = objectMapper.readTree(stdout);
            return root.path("choices").get(0).path("message").path("content").asText();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("[" + agentRole + "] GitHub Models call failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // gh copilot explain strategy (fallback / demo)
    // -------------------------------------------------------------------------

    private String askViaCopilotExplain(String agentRole, String userPrompt) {
        try {
            // gh copilot explain takes the target text as a positional argument
            ProcessBuilder pb = new ProcessBuilder("gh", "copilot", "explain", userPrompt);
            pb.redirectErrorStream(false);

            Process process = pb.start();
            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("[" + agentRole + "] gh copilot explain timed out");
            }

            if (process.exitValue() != 0) {
                log.error("[{}] gh copilot explain exited {}: {}", agentRole, process.exitValue(), stderr);
                throw new RuntimeException("[" + agentRole + "] gh copilot explain failed: " + stderr);
            }

            return stdout;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("[" + agentRole + "] gh copilot explain failed", e);
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
