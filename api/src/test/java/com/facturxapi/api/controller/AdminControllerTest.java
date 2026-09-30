package com.facturxapi.api.controller;

import com.facturxapi.api.entity.ApiKey;
import com.facturxapi.api.repository.ApiKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @BeforeEach
    void setUp() {
        apiKeyRepository.deleteAll();
        ApiKey testKey = new ApiKey();
        testKey.setEmail("existing@example.com");
        testKey.setToken("facturx_test12345678");
        testKey.setCreatedAt(OffsetDateTime.now());
        apiKeyRepository.save(testKey);
    }

    @Test
    void provision_createsNewKey() throws Exception {
        mockMvc.perform(post("/api/admin/provision-key")
                .param("email", "new@example.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("new@example.com"))
            .andExpect(jsonPath("$.apiKey").isString());
    }

    @Test
    void provision_returns409IfExists() throws Exception {
        mockMvc.perform(post("/api/admin/provision-key")
                .param("email", "existing@example.com"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("Active key already exists for: existing@example.com"));
    }
}
