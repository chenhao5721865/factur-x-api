package com.facturxapi.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.facturxapi.api.entity.ApiKey;
import com.facturxapi.api.repository.ApiKeyRepository;
import com.facturxapi.api.repository.ApiUsageRepository;
import com.facturxapi.validator.FacturxValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 保证快速上手 collection 里的生成请求确实能跑通，而不是只在文档里好看。 */
@SpringBootTest
@AutoConfigureMockMvc
class QuickstartCollectionPayloadTest {

    private static final String API_KEY = "facturx_test_quickstart_key";
    private static final Path COLLECTION =
        Path.of("../web/src/main/resources/static/downloads/facturx-api.postman_collection.json");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiUsageRepository apiUsageRepository;

    @Autowired
    private FacturxValidator validator;

    @BeforeEach
    void setUp() {
        apiUsageRepository.deleteAll();
        apiKeyRepository.deleteAll();
        ApiKey key = new ApiKey();
        key.setEmail("quickstart@example.com");
        key.setToken(API_KEY);
        key.setCreatedAt(OffsetDateTime.now());
        apiKeyRepository.save(key);
    }

    @Test
    void generateRequestInCollection_isAcceptedAndSelfValidates() throws Exception {
        JsonNode root = new ObjectMapper().readTree(Files.readAllBytes(COLLECTION));
        String payload = root.path("item").get(0).path("request").path("body").path("raw").asText();

        MvcResult result = mockMvc.perform(post("/api/generate")
                .header("X-API-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andReturn();

        byte[] xml = result.getResponse().getContentAsByteArray();
        assertThat(xml).isNotEmpty();
        assertThat(validator.validate(xml).valid()).isTrue();
    }
}
