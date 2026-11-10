package com.facturxapi.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ReportMailClientTest {

    private static final String BASE = "https://api.resend.com";
    private static final String FROM = "Factur-X API <bonjour@factur-x-api.com>";

    @Test
    void sendReport_postsBearerAuthAndPayload() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ReportMailClient client = new ReportMailClient(builder.build(), "re_test_key", FROM);

        server.expect(requestTo(BASE + "/emails"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer re_test_key"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.from").value(FROM))
            .andExpect(jsonPath("$.to[0]").value("buyer@example.com"))
            .andExpect(jsonPath("$.reply_to").value("543843499@qq.com"))
            .andExpect(jsonPath("$.subject").value("Votre rapport de conformité Factur-X"))
            .andExpect(jsonPath("$.text").value(containsString("BR-07")))
            .andRespond(withSuccess("{\"id\":\"em_1\"}", MediaType.APPLICATION_JSON));

        client.sendReport("buyer@example.com", "Rapport BR-07");

        server.verify();
    }

    @Test
    void isConfigured_requiresApiKeyAndFrom() {
        RestClient rest = RestClient.builder().baseUrl(BASE).build();

        assertThat(new ReportMailClient(rest, "key", FROM).isConfigured()).isTrue();
        assertThat(new ReportMailClient(rest, "", FROM).isConfigured()).isFalse();
        assertThat(new ReportMailClient(rest, "key", " ").isConfigured()).isFalse();
    }
}
