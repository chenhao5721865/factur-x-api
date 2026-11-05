package com.facturxapi.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** 服务端调用 Creem 创建 checkout；secret 只留在服务端，不发给浏览器。 */
@Service
public class CreemCheckoutClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String productId;
    private final String successUrl;

    @Autowired
    public CreemCheckoutClient(
            RestClient.Builder builder,
            @Value("${app.creem.api-base:https://api.creem.io/v1}") String apiBase,
            @Value("${app.creem.api-key:}") String apiKey,
            @Value("${app.creem.product-id:}") String productId,
            @Value("${app.creem.success-url:https://factur-x-api.com/tarifs?merci=1}")
            String successUrl) {
        this(buildClient(builder, apiBase), apiKey, productId, successUrl);
    }

    CreemCheckoutClient(RestClient restClient, String apiKey, String productId, String successUrl) {
        this.restClient = restClient;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.productId = productId == null ? "" : productId.trim();
        this.successUrl = successUrl;
    }

    private static RestClient buildClient(RestClient.Builder builder, String apiBase) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return builder
                .requestFactory(factory)
                .baseUrl(stripTrailingSlash(apiBase))
                .build();
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty() && !productId.isEmpty();
    }

    public String createCheckout(String email, String apiKeyValue) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("product_id", productId);
        payload.put("request_id", UUID.randomUUID().toString());
        payload.put("success_url", successUrl);
        payload.put("customer", Map.of("email", email));
        payload.put("metadata", Map.of("api_key", apiKeyValue));

        JsonNode response = restClient.post()
                .uri("/checkouts")
                .header("x-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(JsonNode.class);

        String url = response == null ? null : response.path("checkout_url").asText(null);
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Creem response missing checkout_url");
        }
        return url;
    }

    private static String stripTrailingSlash(String value) {
        String v = value == null ? "" : value.trim();
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }
}
