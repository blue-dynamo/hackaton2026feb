package com.hackathon.storywriter.model;

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
public record TestFailureEvent(

        @NotNull(message = "source must not be null")
        FailureSource source,

        String testName,

        @NotBlank(message = "errorMessage must not be blank")
        String errorMessage,

        String stackTrace,

        String context
) {
    public enum FailureSource {
        JUNIT,
        MOCK_MVC,
        CONCORDION,
        LOG
    }
}
