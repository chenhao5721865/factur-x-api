package com.facturxapi.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.facturxapi.web.repository.SubscriberRepository;
import com.facturxapi.web.service.ReportMailClient;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class ReportControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SubscriberRepository subscriberRepository;

    @MockitoBean
    private ReportMailClient mailClient;

    @BeforeEach
    void setUp() {
        subscriberRepository.deleteAll();
    }

    private MvcResult post(String email, String ruleIds, String subscribe, String ip) throws Exception {
        MockHttpServletRequestBuilder req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .post("/validateur-factur-x/rapport")
            .header("HX-Request", "true")
            .header("X-Forwarded-For", ip)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("email", email);
        if (ruleIds != null) {
            req.param("ruleIds", ruleIds);
        }
        if (subscribe != null) {
            req.param("subscribe", subscribe);
        }
        return mvc.perform(req).andReturn();
    }

    private static String body(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    void validRequest_sendsReport_200() throws Exception {
        when(mailClient.isConfigured()).thenReturn(true);

        MvcResult result = post("buyer@example.com", "BR-07,BR-CO-10", null, "10.30.1.1");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result)).contains("Rapport envoyé").contains("buyer@example.com");
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(mailClient).sendReport(eq("buyer@example.com"), text.capture());
        assertThat(text.getValue()).contains("BR-07").contains("BR-CO-10");
    }

    @Test
    void subscribeCheckbox_addsSubscriber() throws Exception {
        when(mailClient.isConfigured()).thenReturn(true);

        MvcResult result = post("buyer+news@example.com", "BR-07", "on", "10.30.2.1");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(subscriberRepository.existsByEmail("buyer+news@example.com")).isTrue();
    }

    @Test
    void invalidEmail_422_andNoMail() throws Exception {
        MvcResult result = post("not-an-email", "BR-07", null, "10.30.3.1");

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(result)).contains("Format d'email invalide");
        verify(mailClient, never()).sendReport(anyString(), anyString());
    }

    @Test
    void unknownRuleIds_422_andNoMail() throws Exception {
        MvcResult result = post("buyer@example.com", "FAKE-99,<script>", null, "10.30.4.1");

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(result)).contains("Aucune erreur à envoyer");
        verify(mailClient, never()).sendReport(anyString(), anyString());
    }

    @Test
    void nonHtmxRequest_400() throws Exception {
        MvcResult result = mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/validateur-factur-x/rapport")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("email", "buyer@example.com")
                    .param("ruleIds", "BR-07"))
            .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void sameEmailWithinCooldown_422() throws Exception {
        when(mailClient.isConfigured()).thenReturn(true);
        post("cooldown@example.com", "BR-07", null, "10.30.5.1");

        MvcResult second = post("cooldown@example.com", "BR-07", null, "10.30.5.1");

        assertThat(second.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(second)).contains("déjà");
    }

    @Test
    void mailFailure_422_frenchMessage() throws Exception {
        when(mailClient.isConfigured()).thenReturn(true);
        doThrow(new IllegalStateException("provider down"))
            .when(mailClient).sendReport(anyString(), anyString());

        MvcResult result = post("failing@example.com", "BR-07", null, "10.30.6.1");

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(result)).contains("L'envoi a échoué");
    }

    @Test
    void unconfiguredMail_422() throws Exception {
        MvcResult result = post("nomail@example.com", "BR-07", null, "10.30.7.1");

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(result)).contains("momentanément indisponible");
        verify(mailClient, never()).sendReport(anyString(), anyString());
    }
}
