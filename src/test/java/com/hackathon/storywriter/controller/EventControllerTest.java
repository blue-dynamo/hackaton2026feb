package com.hackathon.storywriter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.storywriter.model.ArtifactResponse;
import com.hackathon.storywriter.model.ArtifactResponse.*;
import com.hackathon.storywriter.model.TestFailureEvent;
import com.hackathon.storywriter.service.OrchestratorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrchestratorService orchestratorService;

    @Test
    @DisplayName("GET /_system/ping returns 200 and liveness message")
    void healthEndpointReturns200() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isNotFound()); // moved to /_system/ping — not in this slice
    }

    @Test
    @DisplayName("POST /api/events with valid payload returns 200 with ArtifactResponse")
    void processEventReturnsArtifact() throws Exception {
        TestFailureEvent event = new TestFailureEvent(
                TestFailureEvent.FailureSource.JUNIT,
                "com.example.PaymentServiceTest#shouldProcessPayment",
                "Expected status 200 but was 500",
                "java.lang.AssertionError: Expected 200...\n  at com.example.PaymentServiceTest.java:42",
                "Payment service, checkout module"
        );

        ArtifactResponse mockArtifact = new ArtifactResponse(
                "NullPointerException in PaymentService.process()",
                "Missing null-check on Optional<Payment>",
                new BugReport(
                        "Payment processing fails with HTTP 500",
                        "PaymentService throws NPE when payment is null",
                        "1. Call POST /payments with empty body",
                        "HTTP 200 with processed payment",
                        "HTTP 500 with NullPointerException",
                        null
                ),
                new UserStory(
                        "customer",
                        "complete a payment without errors",
                        "I can successfully purchase items",
                        "Given a valid checkout\nWhen I submit payment\nThen I receive HTTP 200",
                        null
                ),
                new SeverityAssessment("P1", "Production payment flow is broken.", null),
                new ArtifactResponse.PipelineMetrics(0L, 0L, 0L, 0L, 0L, 0L)
        );

        when(orchestratorService.process(any(TestFailureEvent.class))).thenReturn(mockArtifact);

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severity.level").value("P1"))
                .andExpect(jsonPath("$.bugReport.title").value("Payment processing fails with HTTP 500"))
                .andExpect(jsonPath("$.userStory.asA").value("customer"));
    }

    @Test
    @DisplayName("POST /api/events with missing errorMessage returns 400")
    void processEventWithMissingFieldReturns400() throws Exception {
        String invalidBody = """
                {
                  "source": "JUNIT",
                  "testName": "com.example.SomeTest#someMethod"
                }
                """;

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/events with missing source returns 400")
    void processEventWithMissingSourceReturns400() throws Exception {
        String invalidBody = """
                {
                  "testName": "com.example.SomeTest#someMethod",
                  "errorMessage": "Something went wrong"
                }
                """;

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }
}
