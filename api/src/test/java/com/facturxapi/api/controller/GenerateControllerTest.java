package com.facturxapi.api.controller;

import com.facturxapi.api.entity.ApiKey;
import com.facturxapi.api.repository.ApiKeyRepository;
import com.facturxapi.api.repository.ApiUsageRepository;
import com.facturxapi.validator.FacturxValidator;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GenerateControllerTest {

    private static final String VALID_API_KEY = "facturx_test_generate_key";

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
        apiUsageRepository.deleteAll(); // 先清用量（FK 指向 api_keys），再清 key
        apiKeyRepository.deleteAll();
        ApiKey key = new ApiKey();
        key.setEmail("gen@example.com");
        key.setToken(VALID_API_KEY);
        key.setCreatedAt(OffsetDateTime.now());
        apiKeyRepository.save(key);
    }

    private static final String VALID_JSON = """
        {
          "invoiceNumber": "INV-2026-001",
          "issueDate": "2026-10-08",
          "currency": "EUR",
          "seller": {"name": "Ma Société", "countryCode": "FR", "vatId": "FR12345678901"},
          "buyer": {"name": "Client SA", "countryCode": "FR"},
          "lines": [
            {"description": "Consulting services", "quantity": 2, "unitPrice": 100.00, "vatRate": 20}
          ]
        }
        """;

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder gen(String json) {
        return post("/api/generate")
            .header("X-API-Key", VALID_API_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json);
    }

    private byte[] generateValidXml(String json) throws Exception {
        MvcResult result = mockMvc.perform(gen(json))
            .andExpect(status().isOk())
            .andReturn();
        return result.getResponse().getContentAsByteArray();
    }

    @Test
    void missingApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/generate")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_JSON))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Missing API key. Provide X-API-Key header."));
    }

    @Test
    void invalidApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/generate")
                .header("X-API-Key", "facturx_invalid_key")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_JSON))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Invalid or missing API key"));
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(gen("{not json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Malformed JSON request body"));
    }

    @Test
    void missingRequiredField_returns400() throws Exception {
        String json = """
            {"issueDate": "2026-10-08", "currency": "EUR",
             "seller": {"name": "S", "countryCode": "FR", "vatId": "FR1"},
             "buyer": {"name": "B", "countryCode": "FR"},
             "lines": [{"description": "x", "quantity": 1, "unitPrice": 1, "vatRate": 20}]}
            """;
        mockMvc.perform(gen(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void emptyLines_returns400() throws Exception {
        String json = """
            {"invoiceNumber": "X", "issueDate": "2026-10-08", "currency": "EUR",
             "seller": {"name": "S", "countryCode": "FR", "vatId": "FR1"},
             "buyer": {"name": "B", "countryCode": "FR"}, "lines": []}
            """;
        mockMvc.perform(gen(json)).andExpect(status().isBadRequest());
    }

    @Test
    void sellerWithoutIdentifier_returns400_brCo26() throws Exception {
        String json = """
            {"invoiceNumber": "X", "issueDate": "2026-10-08", "currency": "EUR",
             "seller": {"name": "S", "countryCode": "FR"},
             "buyer": {"name": "B", "countryCode": "FR"},
             "lines": [{"description": "x", "quantity": 1, "unitPrice": 1, "vatRate": 20}]}
            """;
        mockMvc.perform(gen(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(
                "Seller must provide at least one of vatId or legalOrgId (BR-CO-26)"));
    }

    @Test
    void zeroQuantity_returns400() throws Exception {
        String json = """
            {"invoiceNumber": "X", "issueDate": "2026-10-08", "currency": "EUR",
             "seller": {"name": "S", "countryCode": "FR", "vatId": "FR1"},
             "buyer": {"name": "B", "countryCode": "FR"},
             "lines": [{"description": "x", "quantity": 0, "unitPrice": 1, "vatRate": 20}]}
            """;
        mockMvc.perform(gen(json)).andExpect(status().isBadRequest());
    }

    @Test
    void validRequest_returns200Xml_selfValidationPasses() throws Exception {
        MvcResult result = mockMvc.perform(gen(VALID_JSON))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/xml"))
            .andExpect(header().string("Content-Disposition",
                "attachment; filename=\"invoice-INV-2026-001.xml\""))
            .andReturn();

        byte[] xml = result.getResponse().getContentAsByteArray();
        String text = new String(xml, StandardCharsets.UTF_8);
        assertThat(text).startsWith("<?xml");
        assertThat(text).contains("rsm:CrossIndustryInvoice");
        assertThat(text).contains("urn:cen.eu:en16931:2017");

        assertThat(validator.validate(xml).valid()).isTrue();
    }

    @Test
    void invoiceNumberSanitizedInContentDisposition() throws Exception {
        String json = VALID_JSON.replace("INV-2026-001", "INV/2026 001");
        mockMvc.perform(gen(json))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",
                "attachment; filename=\"invoice-INV_2026_001.xml\""));
    }

    @Test
    void multipleVatRates_producesTwoHeaderTaxGroups_andValid() throws Exception {
        String json = """
            {"invoiceNumber": "MULTI-1", "issueDate": "2026-10-08", "currency": "EUR",
             "seller": {"name": "S", "countryCode": "FR", "vatId": "FR1"},
             "buyer": {"name": "B", "countryCode": "FR"},
             "lines": [
               {"description": "a", "quantity": 1, "unitPrice": 100.00, "vatRate": 20},
               {"description": "b", "quantity": 2, "unitPrice": 10.00, "vatRate": 5.5}
             ]}
            """;
        byte[] xml = generateValidXml(json);
        String text = new String(xml, StandardCharsets.UTF_8);

        assertThat(text).contains("<ram:RateApplicablePercent>20</ram:RateApplicablePercent>");
        assertThat(text).contains("<ram:RateApplicablePercent>5.5</ram:RateApplicablePercent>");
        assertThat(text).contains("<ram:BasisAmount>100</ram:BasisAmount>");
        assertThat(text).contains("<ram:BasisAmount>20</ram:BasisAmount>");
        assertThat(text).contains("<ram:TaxTotalAmount currencyID=\"EUR\">21.1</ram:TaxTotalAmount>");
        assertThat(text).contains("<ram:GrandTotalAmount>141.1</ram:GrandTotalAmount>");

        assertThat(validator.validate(xml).valid()).isTrue();
    }

    @Test
    void specialCharacters_areEscaped_andValid() throws Exception {
        String json = """
            {"invoiceNumber": "ESC-1", "issueDate": "2026-10-08", "currency": "EUR",
             "seller": {"name": "A & B <Café>", "countryCode": "FR", "vatId": "FR1"},
             "buyer": {"name": "B", "countryCode": "FR"},
             "lines": [{"description": "x", "quantity": 1, "unitPrice": 1, "vatRate": 20}]}
            """;
        byte[] xml = generateValidXml(json);
        String text = new String(xml, StandardCharsets.UTF_8);

        assertThat(text).contains("A &amp; B &lt;Café&gt;");
        assertThat(validator.validate(xml).valid()).isTrue();
    }

    @Test
    void noPayment_noPaymentMeans_andValid() throws Exception {
        byte[] xml = generateValidXml(VALID_JSON);
        String text = new String(xml, StandardCharsets.UTF_8);

        assertThat(text).doesNotContain("SpecifiedTradeSettlementPaymentMeans");
        assertThat(text).doesNotContain("PaymentReference");
        assertThat(validator.validate(xml).valid()).isTrue();
    }

    @Test
    void withPayment_includesIbanPaymentReferenceDueDate_andValid() throws Exception {
        String json = """
            {"invoiceNumber": "PAY-1", "issueDate": "2026-10-08", "currency": "EUR",
             "seller": {"name": "S", "countryCode": "FR", "vatId": "FR1"},
             "buyer": {"name": "B", "countryCode": "FR"},
             "lines": [{"description": "x", "quantity": 1, "unitPrice": 10, "vatRate": 20}],
             "payment": {"iban": "FR7630006000011234567890189",
                         "paymentReference": "INV-PAY-1", "dueDate": "2026-11-07"}}
            """;
        byte[] xml = generateValidXml(json);
        String text = new String(xml, StandardCharsets.UTF_8);

        assertThat(text).contains("<ram:IBANID>FR7630006000011234567890189</ram:IBANID>");
        assertThat(text).contains("<ram:PaymentReference>INV-PAY-1</ram:PaymentReference>");
        assertThat(text).contains("<udt:DateTimeString format=\"102\">20261107</udt:DateTimeString>");
        // 样例里重复两次的 PaymentMeans 是冗余：只输出一次
        assertThat(text.split("SpecifiedTradeSettlementPaymentMeans").length - 1)
            .isEqualTo(2); // 开闭标签各一次 = 一个元素
        assertThat(validator.validate(xml).valid()).isTrue();
    }

    @Test
    void zeroVatRate_withCategoryZ_groupEmitted_andValid() throws Exception {
        // 0% 必须配 category Z：category S + rate 0 违反 BR-S-05，会被自校验拦截
        String json = """
            {"invoiceNumber": "ZERO-1", "issueDate": "2026-10-08", "currency": "EUR",
             "seller": {"name": "S", "countryCode": "FR", "vatId": "FR1"},
             "buyer": {"name": "B", "countryCode": "FR"},
             "lines": [{"description": "x", "quantity": 1, "unitPrice": 50, "vatRate": 0,
                        "vatCategoryCode": "Z"}]}
            """;
        byte[] xml = generateValidXml(json);
        String text = new String(xml, StandardCharsets.UTF_8);

        assertThat(text).contains("<ram:RateApplicablePercent>0</ram:RateApplicablePercent>");
        assertThat(validator.validate(xml).valid()).isTrue();
    }

    @Test
    void successResponse_includesRateLimitHeaders() throws Exception {
        mockMvc.perform(gen(VALID_JSON))
            .andExpect(status().isOk())
            .andExpect(header().string("X-RateLimit-Limit", "5"))
            .andExpect(header().string("X-RateLimit-Remaining", "4"));
    }

    @Test
    void freeKey_sixthCallWithinMonth_returns402() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(gen(VALID_JSON)).andExpect(status().isOk());
        }
        mockMvc.perform(gen(VALID_JSON))
            .andExpect(status().isPaymentRequired())
            .andExpect(header().exists("Retry-After"))
            .andExpect(header().string("X-RateLimit-Remaining", "0"))
            .andExpect(jsonPath("$.error").exists())
            .andExpect(jsonPath("$.upgrade_url").value("https://factur-x-api.com/tarifs"));
    }

    @Test
    void validationError_beforeQuotaCheck_doesNotConsumeQuota() throws Exception {
        mockMvc.perform(gen("{not json")).andExpect(status().isBadRequest());
        mockMvc.perform(gen(VALID_JSON))
            .andExpect(status().isOk())
            .andExpect(header().string("X-RateLimit-Remaining", "4"));
    }

    @Test
    void failedGeneration_afterQuotaCheck_stillConsumesQuota() throws Exception {
        // BR-CO-26 在配额检查之后抛出：失败也扣额
        String noIdentifier = """
            {"invoiceNumber": "X", "issueDate": "2026-10-08", "currency": "EUR",
             "seller": {"name": "S", "countryCode": "FR"},
             "buyer": {"name": "B", "countryCode": "FR"},
             "lines": [{"description": "x", "quantity": 1, "unitPrice": 1, "vatRate": 20}]}
            """;
        mockMvc.perform(gen(noIdentifier)).andExpect(status().isBadRequest());
        // 已扣 1 额：成功调用后 remaining 应为 3
        mockMvc.perform(gen(VALID_JSON))
            .andExpect(status().isOk())
            .andExpect(header().string("X-RateLimit-Remaining", "3"));
    }
}
