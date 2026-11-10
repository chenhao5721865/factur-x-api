package com.facturxapi.web.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** 发送验证失败报告（web 侧唯一的出站邮件）。未配置 key 时 isConfigured=false，由调用方提示。 */
@Service
public class ReportMailClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String from;

    @Autowired
    public ReportMailClient(
            RestClient.Builder builder,
            @Value("${app.resend.api-base:https://api.resend.com}") String apiBase,
            @Value("${app.resend.api-key:}") String apiKey,
            @Value("${app.resend.from:Factur-X API <bonjour@factur-x-api.com>}") String from) {
        this(buildClient(builder, apiBase), apiKey, from);
    }

    ReportMailClient(RestClient restClient, String apiKey, String from) {
        this.restClient = restClient;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.from = from == null ? "" : from.trim();
    }

    private static RestClient buildClient(RestClient.Builder builder, String apiBase) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        return builder
            .requestFactory(factory)
            .baseUrl(stripTrailingSlash(apiBase))
            .build();
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty() && !from.isEmpty();
    }

    public void sendReport(String email, String reportText) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", from);
        payload.put("to", List.of(email));
        payload.put("reply_to", "543843499@qq.com");
        payload.put("subject", "Votre rapport de conformité Factur-X");
        payload.put("text", reportText);

        restClient.post()
            .uri("/emails")
            .header("Authorization", "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .toBodilessEntity();
    }

    private static String stripTrailingSlash(String value) {
        String v = value == null ? "" : value.trim();
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }
}
