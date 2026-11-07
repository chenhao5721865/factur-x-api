package com.facturxapi.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
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

class ResendMailClientTest {

    private static final String BASE = "https://api.resend.com";
    private static final String FROM = "Factur-X API <bonjour@factur-x-api.com>";

    @Test
    void sendWelcome_postsBearerAuth_andKeyInBody() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendMailClient client = new ResendMailClient(builder.build(), "re_test_key", FROM);

        server.expect(requestTo(BASE + "/emails"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer re_test_key"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.from").value(FROM))
            .andExpect(jsonPath("$.to[0]").value("buyer@example.com"))
            .andExpect(jsonPath("$.reply_to").value("543843499@qq.com"))
            .andExpect(jsonPath("$.subject").value("Votre clé API Factur-X"))
            .andExpect(jsonPath("$.text").value(containsString("facturx_abc123")))
            .andExpect(jsonPath("$.text").value(containsString(
                "https://factur-x-api.com/api/validate")))
            .andExpect(jsonPath("$.text").value(containsString(
                "https://factur-x-api.com/downloads/valid-en16931-cii-basic.xml")))
            .andRespond(withSuccess("{\"id\":\"em_1\"}", MediaType.APPLICATION_JSON));

        client.sendWelcome("buyer@example.com", "facturx_abc123");

        server.verify();
    }

    @Test
    void notConfigured_skipsProviderCall() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendMailClient client = new ResendMailClient(builder.build(), "", FROM);

        assertThatNoException()
            .isThrownBy(() -> client.sendWelcome("buyer@example.com", "facturx_abc123"));

        server.verify();
    }

    @Test
    void isConfigured_requiresApiKeyAndFrom() {
        RestClient rest = RestClient.builder().baseUrl(BASE).build();

        assertThat(new ResendMailClient(rest, "key", FROM).isConfigured()).isTrue();
        assertThat(new ResendMailClient(rest, "", FROM).isConfigured()).isFalse();
        assertThat(new ResendMailClient(rest, "key", " ").isConfigured()).isFalse();
    }
}
