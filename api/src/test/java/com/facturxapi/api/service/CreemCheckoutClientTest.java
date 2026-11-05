package com.facturxapi.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class CreemCheckoutClientTest {

    private static final String BASE = "https://api.creem.io/v1";
    private static final String SUCCESS_URL = "https://factur-x-api.com/tarifs?merci=1";

    @Test
    void createCheckout_postsServerSidePayload_andReturnsUrl() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CreemCheckoutClient client = new CreemCheckoutClient(
            builder.build(), "creem_secret_key", "prod_abc", SUCCESS_URL);

        server.expect(requestTo(BASE + "/checkouts"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("x-api-key", "creem_secret_key"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.product_id").value("prod_abc"))
            .andExpect(jsonPath("$.success_url").value(SUCCESS_URL))
            .andExpect(jsonPath("$.customer.email").value("buyer@example.com"))
            .andExpect(jsonPath("$.metadata.api_key").value("facturx_abc123"))
            .andExpect(jsonPath("$.request_id").isNotEmpty())
            .andRespond(withSuccess(
                "{\"checkout_url\":\"https://checkout.creem.io/abc\",\"id\":\"ch_1\"}",
                MediaType.APPLICATION_JSON));

        String url = client.createCheckout("buyer@example.com", "facturx_abc123");

        assertThat(url).isEqualTo("https://checkout.creem.io/abc");
        server.verify();
    }

    @Test
    void createCheckout_missingUrl_throwsIllegalState() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CreemCheckoutClient client = new CreemCheckoutClient(
            builder.build(), "creem_secret_key", "prod_abc", SUCCESS_URL);

        server.expect(requestTo(BASE + "/checkouts"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.createCheckout("buyer@example.com", "facturx_abc123"))
            .isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @Test
    void createCheckout_providerError_throwsRestClientException() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CreemCheckoutClient client = new CreemCheckoutClient(
            builder.build(), "creem_secret_key", "prod_abc", SUCCESS_URL);

        server.expect(requestTo(BASE + "/checkouts")).andRespond(withServerError());

        assertThatThrownBy(() -> client.createCheckout("buyer@example.com", "facturx_abc123"))
            .isInstanceOf(RestClientException.class);
        server.verify();
    }

    @Test
    void isConfigured_requiresApiKeyAndProductId() {
        RestClient rest = RestClient.builder().baseUrl(BASE).build();

        assertThat(new CreemCheckoutClient(rest, "key", "prod", SUCCESS_URL).isConfigured())
            .isTrue();
        assertThat(new CreemCheckoutClient(rest, "", "prod", SUCCESS_URL).isConfigured())
            .isFalse();
        assertThat(new CreemCheckoutClient(rest, "key", " ", SUCCESS_URL).isConfigured())
            .isFalse();
    }
}
