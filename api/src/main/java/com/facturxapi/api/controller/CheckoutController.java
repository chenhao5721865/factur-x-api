package com.facturxapi.api.controller;

import com.facturxapi.api.entity.ApiKey;
import com.facturxapi.api.service.ApiKeyService;
import com.facturxapi.api.service.CreemCheckoutClient;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** 为当前 API key 创建 Creem checkout；email 与 api_key 都由服务端注入。 */
@RestController
@RequestMapping("/api")
public class CheckoutController {

    private static final Logger log = LoggerFactory.getLogger(CheckoutController.class);

    private final ApiKeyService apiKeyService;
    private final CreemCheckoutClient creemClient;

    public CheckoutController(ApiKeyService apiKeyService, CreemCheckoutClient creemClient) {
        this.apiKeyService = apiKeyService;
        this.creemClient = creemClient;
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Missing API key. Provide X-API-Key header."));
        }
        ApiKey key = apiKeyService.resolveActive(apiKey).orElse(null);
        if (key == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid or missing API key"));
        }
        if (!creemClient.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Checkout not configured"));
        }

        try {
            String checkoutUrl = creemClient.createCheckout(key.getEmail(), key.getToken());
            return ResponseEntity.ok(Map.of("checkout_url", checkoutUrl));
        } catch (RestClientResponseException e) {
            log.error("checkout: provider returned {} for {}",
                e.getStatusCode().value(), mask(key.getToken()));
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "Payment provider unavailable"));
        } catch (RestClientException | IllegalStateException e) {
            log.error("checkout: provider call failed for {}: {}",
                mask(key.getToken()), e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "Payment provider unavailable"));
        } catch (Exception e) {
            log.error("checkout: unexpected failure for {}: {}",
                mask(key.getToken()), e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Checkout failed"));
        }
    }

    private static String mask(String token) {
        return token.length() <= 12 ? token : token.substring(0, 12) + "...";
    }
}
