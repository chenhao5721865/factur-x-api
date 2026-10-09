package com.facturxapi.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.facturxapi.api.repository.ApiKeyRepository;
import com.facturxapi.api.repository.ApiUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class ApiKeysControllerTest {

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
    }

    /** 发领 key 请求，X-Forwarded-For 用例间隔离 IP 限流窗口 */
    private MvcResult requestKey(String email, String ip) throws Exception {
        return mockMvc.perform(post("/api/keys")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\"}"))
            .andReturn();
    }

    private static int statusOf(MvcResult r) {
        return r.getResponse().getStatus();
    }

    @Test
    void newEmail_returns201_withFacturxPrefixedKey() throws Exception {
        MvcResult result = requestKey("new@example.com", "10.1.1.1");

        assertThat(statusOf(result)).isEqualTo(201);
        String content = result.getResponse().getContentAsString();
        assertThat(content).contains("\"apiKey\":\"facturx_");
        assertThat(content).contains("new@example.com");
    }

    @Test
    void duplicateActiveEmail_returns409_withoutEchoingKey() throws Exception {
        requestKey("dup@example.com", "10.2.2.1");
        MvcResult second = requestKey("dup@example.com", "10.2.2.2");

        assertThat(statusOf(second)).isEqualTo(409);
        String content = second.getResponse().getContentAsString();
        assertThat(content).doesNotContain("facturx_");
        assertThat(content).contains("error");
    }

    @Test
    void invalidEmail_returns400() throws Exception {
        assertThat(statusOf(requestKey("not-an-email", "10.3.3.1"))).isEqualTo(400);
    }

    @Test
    void blankEmail_returns400() throws Exception {
        assertThat(statusOf(requestKey("", "10.4.4.1"))).isEqualTo(400);
    }

    @Test
    void sixthRequestFromSameIp_returns429() throws Exception {
        String ip = "10.5.5.1";
        for (int i = 1; i <= 5; i++) {
            requestKey("user" + i + "@example.com", ip); // 前 5 次无论 201/409 都计数
        }
        MvcResult sixth = requestKey("user6@example.com", ip);

        assertThat(statusOf(sixth)).isEqualTo(429);
        assertThat(sixth.getResponse().getContentAsString()).doesNotContain("facturx_");
    }

    @Test
    void rateLimit_isPerIp_otherIpUnaffected() throws Exception {
        String ip = "10.6.6.1";
        for (int i = 1; i <= 5; i++) {
            requestKey("rl" + i + "@example.com", ip);
        }
        assertThat(statusOf(requestKey("rl6@example.com", ip))).isEqualTo(429);
        assertThat(statusOf(requestKey("fresh@example.com", "10.6.6.2"))).isEqualTo(201);
    }
}
