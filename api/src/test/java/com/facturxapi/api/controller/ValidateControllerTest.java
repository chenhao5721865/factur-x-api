package com.facturxapi.api.controller;

import com.facturxapi.api.entity.ApiKey;
import com.facturxapi.api.repository.ApiKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ValidateControllerTest {

    private static final Path SAMPLES = Path.of("..", "samples");
    private static final String VALID_API_KEY = "facturx_test_valid_key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @BeforeEach
    void setUp() {
        apiKeyRepository.findByToken(VALID_API_KEY).ifPresent(apiKeyRepository::delete);
        ApiKey key = new ApiKey();
        key.setEmail("test@example.com");
        key.setToken(VALID_API_KEY);
        key.setCreatedAt(OffsetDateTime.now());
        apiKeyRepository.save(key);
    }

    private MockMultipartHttpServletRequestBuilder prepare(String filename, byte[] content) {
        MockMultipartFile multipartFile =
            new MockMultipartFile("file", filename, "application/octet-stream", content);
        return ((MockMultipartHttpServletRequestBuilder) multipart("/api/validate")
                .header("X-API-Key", VALID_API_KEY))
            .file(multipartFile);
    }

    private MockMultipartHttpServletRequestBuilder prepareNoKey(String filename, byte[] content) {
        MockMultipartFile multipartFile =
            new MockMultipartFile("file", filename, "application/octet-stream", content);
        return ((MockMultipartHttpServletRequestBuilder) multipart("/api/validate"))
            .file(multipartFile);
    }

    private MockMultipartHttpServletRequestBuilder prepareWithBadKey(String filename, byte[] content) {
        MockMultipartFile multipartFile =
            new MockMultipartFile("file", filename, "application/octet-stream", content);
        return ((MockMultipartHttpServletRequestBuilder) multipart("/api/validate")
                .header("X-API-Key", "facturx_invalid_key"))
            .file(multipartFile);
    }

    @Test
    void validXml_withValidKey_returns200AndValidTrue() throws Exception {
        byte[] data = Files.readAllBytes(SAMPLES.resolve("valid-en16931-cii-basic.xml"));
        mockMvc.perform(prepare("valid.xml", data))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void invalidXml_withValidKey_returns200AndValidFalse() throws Exception {
        byte[] data = Files.readAllBytes(SAMPLES.resolve("invalid-missing-buyer.xml"));
        mockMvc.perform(prepare("invalid.xml", data))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.errors").isArray())
            .andExpect(jsonPath("$.errors.length()").value(org.hamcrest.Matchers.greaterThan(0)))
            .andExpect(jsonPath("$.errors[0].ruleId").value("BR-07"));
    }

    @Test
    void validPdf_withValidKey_returns200() throws Exception {
        byte[] data = Files.readAllBytes(SAMPLES.resolve("factur-x-basic.pdf"));
        mockMvc.perform(prepare("facture.pdf", data))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void fakePdf_returns422() throws Exception {
        byte[] data = ("%PDF-1.4\nthis is a fake pdf with no embedded xml\n%%EOF\n")
            .getBytes(StandardCharsets.US_ASCII);
        MvcResult result = mockMvc.perform(prepare("fake.pdf", data))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("error");
    }

    @Test
    void unsupportedFormat_returns400() throws Exception {
        byte[] data = "hello world, not a pdf or xml".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(prepare("junk.txt", data))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Unsupported file format. Only PDF and XML are accepted."));
    }

    @Test
    void emptyFile_returns400() throws Exception {
        mockMvc.perform(prepare("empty.xml", new byte[0]))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Empty file"));
    }

    @Test
    void missingApiKey_returns401() throws Exception {
        byte[] data = Files.readAllBytes(SAMPLES.resolve("valid-en16931-cii-basic.xml"));
        mockMvc.perform(prepareNoKey("valid.xml", data))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Missing API key. Provide X-API-Key header."));
    }

    @Test
    void invalidApiKey_returns401() throws Exception {
        byte[] data = Files.readAllBytes(SAMPLES.resolve("valid-en16931-cii-basic.xml"));
        mockMvc.perform(prepareWithBadKey("valid.xml", data))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Invalid or missing API key"));
    }
}
