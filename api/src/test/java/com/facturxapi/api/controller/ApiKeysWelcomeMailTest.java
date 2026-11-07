package com.facturxapi.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.facturxapi.api.repository.ApiKeyRepository;
import com.facturxapi.api.repository.ApiUsageRepository;
import com.facturxapi.api.service.ResendMailClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ApiKeysWelcomeMailTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiUsageRepository apiUsageRepository;

    @MockitoBean
    private ResendMailClient mailClient;

    @BeforeEach
    void setUp() {
        apiUsageRepository.deleteAll();
        apiKeyRepository.deleteAll();
    }

    private MvcResult requestKey(String email, String ip) throws Exception {
        return mockMvc.perform(post("/api/keys")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\"}"))
            .andReturn();
    }

    @Test
    void newKey_sendsWelcomeEmailWithIssuedKey() throws Exception {
        MvcResult result = requestKey("welcome@example.com", "10.11.1.1");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(mailClient).sendWelcome(org.mockito.ArgumentMatchers.eq("welcome@example.com"),
            key.capture());
        assertThat(key.getValue()).startsWith("facturx_");
        assertThat(result.getResponse().getContentAsString()).contains(key.getValue());
    }

    @Test
    void mailFailure_stillReturns201WithKey() throws Exception {
        doThrow(new IllegalStateException("provider down"))
            .when(mailClient).sendWelcome(anyString(), anyString());

        MvcResult result = requestKey("resilient@example.com", "10.11.2.1");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getContentAsString()).contains("\"apiKey\":\"facturx_");
    }

    @Test
    void duplicateRequest_resendsExistingKeyWithoutEchoingIt() throws Exception {
        when(mailClient.isConfigured()).thenReturn(true);
        String email = "dup-mail@example.com";
        requestKey(email, "10.11.3.1");
        String key = apiKeyRepository.findByEmailAndRevokedAtIsNull(email).orElseThrow().getToken();
        clearInvocations(mailClient);

        MvcResult second = requestKey(email, "10.11.3.2");

        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        String body = second.getResponse().getContentAsString();
        assertThat(body).contains("\"resent\":true");
        assertThat(body).doesNotContain(key);
        verify(mailClient).sendWelcome(email, key);
    }

    @Test
    void duplicateRequest_withinCooldown_doesNotResend() throws Exception {
        when(mailClient.isConfigured()).thenReturn(true);
        String email = "dup-cooldown@example.com";
        requestKey(email, "10.11.4.1");
        clearInvocations(mailClient);

        requestKey(email, "10.11.4.2");
        MvcResult third = requestKey(email, "10.11.4.3");

        assertThat(third.getResponse().getStatus()).isEqualTo(409);
        assertThat(third.getResponse().getContentAsString()).contains("\"resent\":false");
        verify(mailClient, times(1)).sendWelcome(anyString(), anyString());
    }

    @Test
    void duplicateRequest_mailFailure_reportsNotResent() throws Exception {
        when(mailClient.isConfigured()).thenReturn(true);
        String email = "dup-fail@example.com";
        requestKey(email, "10.11.5.1");
        String key = apiKeyRepository.findByEmailAndRevokedAtIsNull(email).orElseThrow().getToken();
        clearInvocations(mailClient);
        doThrow(new IllegalStateException("provider down"))
            .when(mailClient).sendWelcome(anyString(), anyString());

        MvcResult second = requestKey(email, "10.11.5.2");

        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        String body = second.getResponse().getContentAsString();
        assertThat(body).contains("\"resent\":false");
        assertThat(body).doesNotContain(key);
    }

    @Test
    void duplicateRequest_unconfiguredMail_reportsNotResent() throws Exception {
        String email = "dup-nomail@example.com";
        requestKey(email, "10.11.6.1");
        clearInvocations(mailClient);

        MvcResult second = requestKey(email, "10.11.6.2");

        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(second.getResponse().getContentAsString()).contains("\"resent\":false");
        verify(mailClient, never()).sendWelcome(anyString(), anyString());
    }
}
