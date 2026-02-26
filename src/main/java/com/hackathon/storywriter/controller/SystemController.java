package com.hackathon.storywriter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * System-level endpoints (liveness, readiness, etc.) served outside the {@code /api} namespace.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /_system/ping} — simple liveness / smoke-test probe</li>
 * </ul>
 */
@Tag(name = "System", description = "Health and liveness probes")
@RestController
public class SystemController {

    private static final Logger log = LoggerFactory.getLogger(SystemController.class);

    /**
     * Liveness probe.
     *
     * @return 200 OK with a short status string
     */
    @Operation(summary = "Liveness probe", description = "Returns a short status string when the service is up.")
    @ApiResponse(responseCode = "200", description = "Service is running")
    @GetMapping("/_system/ping")
    public ResponseEntity<String> ping() {
        log.debug("GET /_system/ping");
        return ResponseEntity.ok("story-writer is running");
    }
}
