package com.facturxapi.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TurnstileVerifierTest {

    private static final String BASE = "https://challenges.cloudflare.com";

    private static MockRestServiceServer bind(RestClient.Builder builder) {
        return MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void validToken_postsForm_andAccepts() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = bind(builder);
        TurnstileVerifier verifier = new TurnstileVerifier(builder.build(), "s3cr3t");

        server.expect(requestTo(BASE + "/turnstile/v0/siteverify"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().string(containsString("secret=s3cr3t")))
            .andExpect(content().string(containsString("response=tok123")))
            .andExpect(content().string(containsString("remoteip=1.2.3.4")))
            .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        assertThat(verifier.verify("tok123", "1.2.3.4")).isTrue();
        server.verify();
    }

    @Test
    void rejectedToken_returnsFalse() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = bind(builder);
        TurnstileVerifier verifier = new TurnstileVerifier(builder.build(), "s3cr3t");

        server.expect(requestTo(BASE + "/turnstile/v0/siteverify"))
            .andRespond(withSuccess("{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}",
                MediaType.APPLICATION_JSON));

        assertThat(verifier.verify("used-token", "1.2.3.4")).isFalse();
        server.verify();
    }

    @Test
    void blankToken_rejectedWithoutCallingProvider() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = bind(builder);
        TurnstileVerifier verifier = new TurnstileVerifier(builder.build(), "s3cr3t");

        assertThat(verifier.verify(null, "1.2.3.4")).isFalse();
        assertThat(verifier.verify("  ", "1.2.3.4")).isFalse();
        server.verify();
    }

    @Test
    void notConfigured_passesWithoutCallingProvider() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = bind(builder);
        TurnstileVerifier verifier = new TurnstileVerifier(builder.build(), "");

        assertThat(verifier.isConfigured()).isFalse();
        assertThat(verifier.verify(null, null)).isTrue();
        server.verify();
    }

    @Test
    void providerError_failsClosed() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = bind(builder);
        TurnstileVerifier verifier = new TurnstileVerifier(builder.build(), "s3cr3t");

        server.expect(requestTo(BASE + "/turnstile/v0/siteverify"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(verifier.verify("tok123", "1.2.3.4")).isFalse();
        server.verify();
    }
}
