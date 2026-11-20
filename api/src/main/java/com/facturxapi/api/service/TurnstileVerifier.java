package com.facturxapi.api.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Cloudflare Turnstile 服务端校验。未配置 secret（本地/测试）时放行；
 * 配置后令牌缺失或校验失败一律拒绝，调用方无法绕过。
 */
@Service
public class TurnstileVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileVerifier.class);

    private final RestClient restClient;
    private final String secret;

    @Autowired
    public TurnstileVerifier(
            RestClient.Builder builder,
            @Value("${app.turnstile.api-base:https://challenges.cloudflare.com}") String apiBase,
            @Value("${app.turnstile.secret:}") String secret) {
        this(buildClient(builder, apiBase), secret);
    }

    TurnstileVerifier(RestClient restClient, String secret) {
        this.restClient = restClient;
        this.secret = secret == null ? "" : secret.trim();
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
        return !secret.isEmpty();
    }

    /** 未配置时返回 true（测试/本地）；配置后 fail closed，网络故障也拒绝。 */
    public boolean verify(String token, String remoteIp) {
        if (!isConfigured()) {
            return true;
        }
        if (token == null || token.isBlank()) {
            return false;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret);
        form.add("response", token.trim());
        if (remoteIp != null && !remoteIp.isBlank()) {
            form.add("remoteip", remoteIp);
        }
        try {
            Response response = restClient.post()
                    .uri("/turnstile/v0/siteverify")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Response.class);
            if (response == null || !response.success()) {
                log.warn("turnstile: token rejected");
            }
            return response != null && response.success();
        } catch (RuntimeException e) {
            log.warn("turnstile: verification failed: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(boolean success) {}

    private static String stripTrailingSlash(String value) {
        String v = value == null ? "" : value.trim();
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }
}
