package com.facturxapi.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class QuickstartAssetsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void docsPage_containsQuickstartAndDownloadLinks() throws Exception {
        mockMvc.perform(get("/docs"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Démarrage en 15 minutes")))
            .andExpect(content().string(containsString("/downloads/facturx-api.postman_collection.json")))
            .andExpect(content().string(containsString("/downloads/facturx-api.postman_environment.json")))
            .andExpect(content().string(containsString("/downloads/factur-x-basic.pdf")))
            .andExpect(content().string(containsString("/downloads/valid-en16931-cii-basic.xml")))
            .andExpect(content().string(containsString("/downloads/invalid-missing-buyer.xml")));
    }

    @Test
    void collection_isValidJsonWithFourBaseUrlRequests() throws Exception {
        JsonNode root = readJson("/downloads/facturx-api.postman_collection.json");

        assertThat(root.path("info").path("name").asText()).contains("Démarrage");
        assertThat(root.path("item").size()).isEqualTo(4);

        for (JsonNode item : root.path("item")) {
            String rawUrl = item.path("request").path("url").path("raw").asText();
            assertThat(rawUrl).startsWith("{{baseUrl}}/api/");
        }
    }

    @Test
    void collection_wiresApiKeyAndFileUploads() throws Exception {
        JsonNode root = readJson("/downloads/facturx-api.postman_collection.json");

        JsonNode generate = root.path("item").get(0).path("request");
        assertThat(generate.path("method").asText()).isEqualTo("POST");
        assertThat(generate.path("header").toString()).contains("{{apiKey}}");
        assertThat(generate.path("body").path("raw").asText())
            .contains("invoiceNumber", "vatCategoryCode", "\"vatRate\"");

        for (int i = 1; i < 4; i++) {
            JsonNode body = root.path("item").get(i).path("request").path("body");
            assertThat(body.path("mode").asText()).isEqualTo("formdata");
            assertThat(body.path("formdata").get(0).path("key").asText()).isEqualTo("file");
            assertThat(body.path("formdata").get(0).path("type").asText()).isEqualTo("file");
        }
    }

    @Test
    void environment_definesBaseUrlAndSecretApiKey() throws Exception {
        JsonNode root = readJson("/downloads/facturx-api.postman_environment.json");

        JsonNode values = root.path("values");
        assertThat(values.size()).isEqualTo(2);
        assertThat(values.get(0).path("key").asText()).isEqualTo("baseUrl");
        assertThat(values.get(0).path("value").asText()).isEqualTo("https://factur-x-api.com");
        assertThat(values.get(1).path("key").asText()).isEqualTo("apiKey");
        assertThat(values.get(1).path("type").asText()).isEqualTo("secret");
    }

    @Test
    void fixtures_areServedWithExpectedFormat() throws Exception {
        byte[] pdf = mockMvc.perform(get("/downloads/factur-x-basic.pdf"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        for (String xml : new String[] {"valid-en16931-cii-basic.xml", "invalid-missing-buyer.xml"}) {
            MvcResult result = mockMvc.perform(get("/downloads/" + xml))
                .andExpect(status().isOk())
                .andReturn();
            String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
            assertThat(body).contains("CrossIndustryInvoice");
        }
    }

    private JsonNode readJson(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
