package com.hackathon.storywriter.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Represents a test failure or error event that triggers the agent pipeline.
 *
 * @param source       Origin of the failure (junit | mockMvc | concordion | log)
 * @param testName     Fully-qualified test method name, if applicable
 * @param errorMessage Short error title / exception message
 * @param stackTrace   Full stack trace text
 * @param context      Additional context: class under test, module, feature area, etc.
 */
@Schema(description = "Test failure or error event that triggers the multi-agent pipeline")
public record TestFailureEvent(

        @Schema(description = "Origin of the failure", example = "JUNIT", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "source must not be null")
        FailureSource source,

        @Schema(description = "Fully-qualified test method name", example = "com.example.PaymentServiceTest#shouldProcessPayment")
        String testName,

        @Schema(description = "Short error title / exception message", example = "Expected status 200 but was 500", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "errorMessage must not be blank")
        String errorMessage,

        @Schema(description = "Full stack trace text")
        String stackTrace,

        @Schema(description = "Additional context: class under test, module, feature area, etc.", example = "Payment service, checkout module")
        String context
) {
    @Schema(description = "Origin / runner type of the test failure")
    public enum FailureSource {
        JUNIT,
        MOCK_MVC,
        CONCORDION,
        LOG
    }
}
