package com.facturxapi.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.facturxapi.api.entity.ApiKey;
import com.facturxapi.api.repository.ApiKeyRepository;
import com.facturxapi.api.repository.ApiUsageRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * samples/api-fixtures 是六类 VAT 样例的单一事实源：-valid.json 必须生成 200 + XML，
 * -invalid.json 必须以 400 + 对应规则 ID 拒绝。新增样例自动纳入。
 */
@SpringBootTest
@AutoConfigureMockMvc
class CategoryFixturesTest {

    static final Path FIXTURES = Path.of("../samples/api-fixtures");
    private static final String API_KEY = "facturx_test_category_fixtures";

    private static final Map<String, String> INVALID_RULES = Map.of(
        "S", "BR-S-05",
        "Z", "BR-Z-05",
        "E", "BR-E-10",
        "AE", "BR-AE-02",
        "K", "BR-IC-11",
        "G", "BR-G-10");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiUsageRepository apiUsageRepository;

    @BeforeEach
    void setUp() {
        apiUsageRepository.deleteAll();
        apiKeyRepository.deleteAll();
        ApiKey key = new ApiKey();
        key.setEmail("categories@example.com");
        key.setToken(API_KEY);
        key.setCreatedAt(OffsetDateTime.now());
        key.setPlan("pro");
        key.setPlanExpiresAt(OffsetDateTime.now().plusDays(30));
        apiKeyRepository.save(key);
    }

    static Stream<Path> fixtures(String suffix) throws IOException {
        try (Stream<Path> s = Files.list(FIXTURES)) {
            return s.filter(p -> p.getFileName().toString().endsWith("-" + suffix + ".json"))
                .toList().stream();
        }
    }

    static Stream<Path> validFixtures() throws IOException {
        return fixtures("valid");
    }

    static Stream<Path> invalidFixtures() throws IOException {
        return fixtures("invalid");
    }

    @ParameterizedTest
    @MethodSource("validFixtures")
    void validFixture_generatesCategoryXml(Path fixture) throws Exception {
        String category = categoryOf(fixture);
        String xml = mockMvc.perform(post("/api/generate")
                .header("X-API-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(read(fixture)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(xml).as("%s: code catégorie BT-151", fixture.getFileName())
            .contains("<ram:CategoryCode>" + category + "</ram:CategoryCode>");
    }

    @ParameterizedTest
    @MethodSource("invalidFixtures")
    void invalidFixture_returns400WithRuleId(Path fixture) throws Exception {
        String category = categoryOf(fixture);
        String rule = INVALID_RULES.get(category);
        assertThat(rule).as("règle attendue pour %s", fixture.getFileName()).isNotNull();

        mockMvc.perform(post("/api/generate")
                .header("X-API-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(read(fixture)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString(rule)));
    }

    private static String categoryOf(Path fixture) {
        return fixture.getFileName().toString().split("-")[0];
    }

    private static String read(Path fixture) {
        try {
            return Files.readString(fixture, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
