package com.hackathon.storywriter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.storywriter.model.ArtifactResponse;
import com.hackathon.storywriter.model.TestFailureEvent;
import com.hackathon.storywriter.service.OrchestratorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test: loads the full Spring application context and exercises
 * the HTTP layer end-to-end (orchestrator is mocked to avoid CLI calls).
 */
@SpringBootTest
@AutoConfigureMockMvc
class StoryWriterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrchestratorService orchestratorService;

    @Test
    @DisplayName("Full context loads and POST /api/events returns JSON artifact")
    void contextLoadsAndEventEndpointWorks() throws Exception {
        ArtifactResponse mockArtifact = new ArtifactResponse(
                new ArtifactResponse.TechnicalAnalysis("Technical: DB connection timeout", 0L),
                new ArtifactResponse.RootCause("Root cause: misconfigured connection pool", 0L),
                new ArtifactResponse.BugReport(
                        "DB timeout on order lookup",
                        "Connection pool exhausted",
                        "1. Load test with 100 concurrent users",
                        "Orders returned within 200ms",
                        "Timeout after 30s",
                        null,
                        0L
                ),
                new ArtifactResponse.UserStory(
                        "customer",
                        "retrieve my orders quickly",
                        "I get a responsive shopping experience",
                        "Given authenticated user\nWhen viewing orders\nThen response within 500ms",
                        null,
                        0L
                ),
                new ArtifactResponse.SeverityAssessment("P2", "High-traffic core feature degraded", null, 0L),
                0L
        );

        when(orchestratorService.process(any(TestFailureEvent.class))).thenReturn(mockArtifact);

        TestFailureEvent event = new TestFailureEvent(
                TestFailureEvent.FailureSource.MOCK_MVC,
                "com.example.OrderControllerIT#shouldReturnOrders",
                "Connection timed out after 30000ms",
                "java.net.SocketTimeoutException: Connect timed out\n  at ...",
                "Order Controller - GET /orders"
        );

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.technicalAnalysis.content").value("Technical: DB connection timeout"))
                .andExpect(jsonPath("$.severity.level").value("P2"))
                .andExpect(jsonPath("$.bugReport.title").value("DB timeout on order lookup"))
                .andExpect(jsonPath("$.userStory.asA").value("customer"));
    }
}
