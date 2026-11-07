package com.facturxapi.api.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** 服务端调用 Resend 发事务邮件；未配置 key 时静默跳过，绝不阻塞主流程。 */
@Service
public class ResendMailClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String from;

    @Autowired
    public ResendMailClient(
            RestClient.Builder builder,
            @Value("${app.resend.api-base:https://api.resend.com}") String apiBase,
            @Value("${app.resend.api-key:}") String apiKey,
            @Value("${app.resend.from:Factur-X API <bonjour@factur-x-api.com>}") String from) {
        this(buildClient(builder, apiBase), apiKey, from);
    }

    ResendMailClient(RestClient restClient, String apiKey, String from) {
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

    /** 发送含 key 与首条 curl 的欢迎邮件；调用方负责兜住异常。 */
    public void sendWelcome(String email, String apiKeyValue) {
        if (!isConfigured()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", from);
        payload.put("to", List.of(email));
        payload.put("subject", "Votre clé API Factur-X");
        payload.put("text", welcomeText(apiKeyValue));

        restClient.post()
                .uri("/emails")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
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
