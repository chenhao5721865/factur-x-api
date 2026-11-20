package com.facturxapi.web.service;

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

    @Test
    void validToken_postsForm_andAccepts() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TurnstileVerifier verifier = new TurnstileVerifier(builder.build(), "s3cr3t");

        server.expect(requestTo(BASE + "/turnstile/v0/siteverify"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().string(containsString("secret=s3cr3t")))
            .andExpect(content().string(containsString("response=tok123")))
            .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        assertThat(verifier.verify("tok123", "1.2.3.4")).isTrue();
        server.verify();
    }

    @Test
    void rejectedToken_returnsFalse() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TurnstileVerifier verifier = new TurnstileVerifier(builder.build(), "s3cr3t");

        server.expect(requestTo(BASE + "/turnstile/v0/siteverify"))
            .andRespond(withSuccess("{\"success\":false}", MediaType.APPLICATION_JSON));

        assertThat(verifier.verify("used-token", "1.2.3.4")).isFalse();
        server.verify();
    }

    @Test
    void blankTokenOrUnconfigured_isHandledLocally() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TurnstileVerifier configured = new TurnstileVerifier(builder.build(), "s3cr3t");
        TurnstileVerifier unconfigured = new TurnstileVerifier(builder.build(), "");

        assertThat(configured.verify(null, "1.2.3.4")).isFalse();
        assertThat(unconfigured.verify(null, null)).isTrue();
        server.verify();
    }

    @Test
    void providerError_failsClosed() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TurnstileVerifier verifier = new TurnstileVerifier(builder.build(), "s3cr3t");

        server.expect(requestTo(BASE + "/turnstile/v0/siteverify"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(verifier.verify("tok123", "1.2.3.4")).isFalse();
        server.verify();
    }
}
