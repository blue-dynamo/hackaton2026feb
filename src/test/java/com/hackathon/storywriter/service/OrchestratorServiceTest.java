package com.hackathon.storywriter.service;

import com.hackathon.storywriter.model.ArtifactResponse;
import com.hackathon.storywriter.model.ArtifactResponse.*;
import com.hackathon.storywriter.model.TestFailureEvent;
import com.hackathon.storywriter.service.agent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrchestratorServiceTest {

    @Mock private TechnicalAnalyzerAgent technicalAnalyzerAgent;
    @Mock private RootCauseAgent rootCauseAgent;
    @Mock private BugWriterAgent bugWriterAgent;
    @Mock private StoryWriterAgent storyWriterAgent;
    @Mock private SeverityAgent severityAgent;

    private OrchestratorService orchestratorService;

    private static final TestFailureEvent SAMPLE_EVENT = new TestFailureEvent(
            TestFailureEvent.FailureSource.JUNIT,
            "com.example.OrderServiceTest#shouldCreateOrder",
            "AssertionError: expected:<200> but was:<500>",
            "java.lang.AssertionError: expected:<200> but was:<500>\n  at com.example.OrderServiceTest:55",
            "Order service module"
    );

    @BeforeEach
    void setUp() {
        orchestratorService = new OrchestratorService(
                technicalAnalyzerAgent,
                rootCauseAgent,
                bugWriterAgent,
                storyWriterAgent,
                severityAgent
        );
    }

    @Test
    @DisplayName("process() calls all agents and aggregates results into ArtifactResponse")
    void processCallsAllAgentsAndAggregates() {
        // given
        String techAnalysis = "NullPointerException in OrderService.createOrder()";
        String rootCause = "Missing validation on order payload";
        BugReport bugReport = new BugReport(
                "Order creation fails with 500", "NPE in service", "1. POST /orders", "200", "500", null, 0L);
        UserStory userStory = new UserStory(
                "customer", "create an order", "I can buy items",
                "Given valid order\nWhen submitted\nThen 200 returned", null, 0L);
        SeverityAssessment severity = new SeverityAssessment("P2", "Core order flow impacted", null, 0L);

        when(technicalAnalyzerAgent.analyze(SAMPLE_EVENT)).thenReturn(techAnalysis);
        when(rootCauseAgent.analyze(eq(SAMPLE_EVENT), eq(techAnalysis))).thenReturn(rootCause);
        when(bugWriterAgent.write(eq(SAMPLE_EVENT), eq(techAnalysis), eq(rootCause))).thenReturn(bugReport);
        when(storyWriterAgent.write(eq(SAMPLE_EVENT), eq(rootCause))).thenReturn(userStory);
        when(severityAgent.assess(eq(SAMPLE_EVENT), eq(techAnalysis), eq(rootCause))).thenReturn(severity);

        // when
        ArtifactResponse result = orchestratorService.process(SAMPLE_EVENT);

        // then
        assertThat(result.technicalAnalysis().content()).isEqualTo(techAnalysis);
        assertThat(result.rootCause().content()).isEqualTo(rootCause);
        assertThat(result.bugReport().title()).isEqualTo(bugReport.title());
        assertThat(result.userStory().asA()).isEqualTo(userStory.asA());
        assertThat(result.severity().level()).isEqualTo(severity.level());
        assertThat(result.totalMs()).isGreaterThanOrEqualTo(0L);
        assertThat(result.technicalAnalysis().durationMs()).isGreaterThanOrEqualTo(0L);
        assertThat(result.rootCause().durationMs()).isGreaterThanOrEqualTo(0L);
        assertThat(result.bugReport().durationMs()).isGreaterThanOrEqualTo(0L);
        assertThat(result.userStory().durationMs()).isGreaterThanOrEqualTo(0L);
        assertThat(result.severity().durationMs()).isGreaterThanOrEqualTo(0L);

        verify(technicalAnalyzerAgent).analyze(SAMPLE_EVENT);
        verify(rootCauseAgent).analyze(eq(SAMPLE_EVENT), eq(techAnalysis));
        verify(bugWriterAgent).write(eq(SAMPLE_EVENT), eq(techAnalysis), eq(rootCause));
        verify(storyWriterAgent).write(eq(SAMPLE_EVENT), eq(rootCause));
        verify(severityAgent).assess(eq(SAMPLE_EVENT), eq(techAnalysis), eq(rootCause));
    }

    @Test
    @DisplayName("process() propagates agent exceptions as RuntimeException")
    void processWrapsAgentExceptions() {
        when(technicalAnalyzerAgent.analyze(any())).thenThrow(new RuntimeException("CLI failed"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> orchestratorService.process(SAMPLE_EVENT));
    }
}
