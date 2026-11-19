package com.facturxapi.api.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** 服务端调用 Resend 发事务邮件；未配置 key 时静默跳过，绝不阻塞主流程。 */
@Service
public class ResendMailClient {

    private static final Logger log = LoggerFactory.getLogger(ResendMailClient.class);
    private static final long WINDOW_MILLIS = 3600_000L;

    private final RestClient restClient;
    private final String apiKey;
    private final String from;
    private final int hourlyLimit;
    private long windowStart = System.currentTimeMillis();
    private int sentInWindow;

    @Autowired
    public ResendMailClient(
            RestClient.Builder builder,
            @Value("${app.resend.api-base:https://api.resend.com}") String apiBase,
            @Value("${app.resend.api-key:}") String apiKey,
            @Value("${app.resend.from:Factur-X API <bonjour@factur-x-api.com>}") String from,
            @Value("${app.mail.hourly-limit:5}") int hourlyLimit) {
        this(buildClient(builder, apiBase), apiKey, from, hourlyLimit);
    }

    ResendMailClient(RestClient restClient, String apiKey, String from) {
        this(restClient, apiKey, from, 5);
    }

    ResendMailClient(RestClient restClient, String apiKey, String from, int hourlyLimit) {
        this.restClient = restClient;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.from = from == null ? "" : from.trim();
        this.hourlyLimit = hourlyLimit;
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

    /** 发送含 key 与首条 curl 的欢迎邮件；返回是否真的发出，调用方负责兜住异常。 */
    public boolean sendWelcome(String email, String apiKeyValue) {
        if (!isConfigured()) {
            return false;
        }
        if (!acquireSendSlot()) {
            log.warn("resend: hourly send cap reached, skipping welcome email");
            return false;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", from);
        payload.put("to", List.of(email));
        payload.put("reply_to", "543843499@qq.com");
        payload.put("subject", "Votre clé API Factur-X");
        payload.put("text", welcomeText(apiKeyValue));

        restClient.post()
                .uri("/emails")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
        return true;
    }

    /** 全局小时闸：单实例内存窗口，防止注册脚本把欢迎邮件当放大器。 */
    private synchronized boolean acquireSendSlot() {
        long now = System.currentTimeMillis();
        if (now - windowStart >= WINDOW_MILLIS) {
            windowStart = now;
            sentInWindow = 0;
        }
        sentInWindow++;
        return sentInWindow <= hourlyLimit;
    }

    static String welcomeText(String apiKeyValue) {
        return """
            Bonjour,

            Votre clé API Factur-X est prête :

              %s

            Testez-la tout de suite — la validation est gratuite et illimitée :

              curl -X POST https://factur-x-api.com/api/validate \\
                -H "X-API-Key: %s" \\
                -F "file=@facture.xml"

            Pas de fichier sous la main ? Téléchargez cet exemple :
            https://factur-x-api.com/downloads/valid-en16931-cii-basic.xml

            Générer une facture à partir de JSON (5 générations offertes par mois) :
            https://factur-x-api.com/docs

            Conservez cet e-mail : votre clé ne sera plus affichée.
            Une question ? Répondez à cet e-mail ou écrivez à 543843499@qq.com.

            L'équipe Factur-X API
            """.formatted(apiKeyValue, apiKeyValue);
    }

    private static String stripTrailingSlash(String value) {
        String v = value == null ? "" : value.trim();
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }
}
