package com.facturxapi.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.facturxapi.api.entity.ApiKey;
import com.facturxapi.api.service.ApiKeyService;
import com.facturxapi.api.service.CreemCheckoutClient;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class CheckoutControllerTest {

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private CreemCheckoutClient creemClient;

    private CheckoutController controller;

    @BeforeEach
    void setUp() {
        controller = new CheckoutController(apiKeyService, creemClient);
    }

    private static ApiKey key(String token, String email) {
        return new ApiKey(email, token);
    }

    @Test
    void missingHeader_returns401() {
        ResponseEntity<?> response = controller.checkout(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void blankHeader_returns401() {
        ResponseEntity<?> response = controller.checkout("   ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidOrRevokedKey_returns401() {
        when(apiKeyService.resolveActive("facturx_dead")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.checkout("facturx_dead");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(body(response)).containsEntry("error", "Invalid or missing API key");
    }

    @Test
    void creemNotConfigured_returns503() {
        when(apiKeyService.resolveActive("facturx_ok"))
            .thenReturn(Optional.of(key("facturx_ok", "a@b.com")));
        when(creemClient.isConfigured()).thenReturn(false);

        ResponseEntity<?> response = controller.checkout("facturx_ok");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(body(response)).containsEntry("error", "Checkout not configured");
    }

    @Test
    void success_returnsCheckoutUrl() {
        when(apiKeyService.resolveActive("facturx_ok"))
            .thenReturn(Optional.of(key("facturx_ok", "a@b.com")));
        when(creemClient.isConfigured()).thenReturn(true);
        when(creemClient.createCheckout("a@b.com", "facturx_ok"))
            .thenReturn("https://checkout.creem.io/xyz");

        ResponseEntity<?> response = controller.checkout("facturx_ok");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(response)).containsEntry("checkout_url", "https://checkout.creem.io/xyz");
    }

    @Test
    void providerFailure_returns502_withoutProviderDetail() {
        when(apiKeyService.resolveActive("facturx_ok"))
            .thenReturn(Optional.of(key("facturx_ok", "a@b.com")));
        when(creemClient.isConfigured()).thenReturn(true);
        when(creemClient.createCheckout("a@b.com", "facturx_ok"))
            .thenThrow(new RestClientException("creem said: internal detail"));

        ResponseEntity<?> response = controller.checkout("facturx_ok");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(body(response)).containsEntry("error", "Payment provider unavailable");
        assertThat(body(response).toString()).doesNotContain("internal detail");
    }

    @Test
    void unexpectedFailure_returns500() {
        when(apiKeyService.resolveActive("facturx_ok"))
            .thenReturn(Optional.of(key("facturx_ok", "a@b.com")));
        when(creemClient.isConfigured()).thenReturn(true);
        when(creemClient.createCheckout("a@b.com", "facturx_ok"))
            .thenThrow(new RuntimeException("boom"));

        ResponseEntity<?> response = controller.checkout("facturx_ok");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body(response)).containsEntry("error", "Checkout failed");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(ResponseEntity<?> response) {
        return (Map<String, Object>) response.getBody();
    }
}
