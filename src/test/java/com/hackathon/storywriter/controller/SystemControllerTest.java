package com.hackathon.storywriter.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SystemController.class)
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /_system/ping returns 200 and liveness message")
    void pingReturns200() throws Exception {
        mockMvc.perform(get("/_system/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("story-writer is running"));
    }
}
