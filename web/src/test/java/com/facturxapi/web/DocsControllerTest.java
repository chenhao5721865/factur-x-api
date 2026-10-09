package com.facturxapi.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DocsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void docsPage_renders200_inFrench() throws Exception {
        mockMvc.perform(get("/docs"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("lang=\"fr\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Documentation API")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("POST /api/generate")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("POST /api/validate")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("X-API-Key")));
    }
}
