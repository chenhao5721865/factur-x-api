package com.facturxapi.api.controller;

import com.facturxapi.api.service.LsWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LemonSqueezy webhook 入口。Caddy 白名单放行 /api/webhook/lemonsqueezy。
 * 签名 = HMAC-SHA256(rawBody, signing secret)，hex，头 X-Signature；
 * 比较用 MessageDigest.isEqual 防时序攻击。secret 未配置 → 503 fail closed。
 */
@RestController
@RequestMapping("/api/webhook")
public class LsWebhookController {

    private static final Logger log = LoggerFactory.getLogger(LsWebhookController.class);

    private final LsWebhookService service;
    private final ObjectMapper objectMapper;
    private final String secret;

    public LsWebhookController(LsWebhookService service, ObjectMapper objectMapper,
                               @Value("${app.lemonsqueezy.webhook-secret:}") String secret) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.secret = secret;
    }

    @PostMapping(value = "/lemonsqueezy", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> handle(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Signature", required = false) String signature) {

        if (secret == null || secret.isBlank()) {
            log.error("webhook: LS_WEBHOOK_SECRET not configured — rejecting (fail closed)");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Webhook not configured"));
        }
        if (signature == null || !verifySignature(rawBody, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid signature"));
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Malformed JSON payload"));
        }
        String eventName = root.path("meta").path("event_name").asText(null);
        if (eventName == null || eventName.isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Missing meta.event_name"));
        }

        service.handle(eventName, root.path("meta"), root.path("data"));
        return ResponseEntity.ok(Map.of("received", true));
    }

    private boolean verifySignature(String rawBody, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            byte[] provided = hexToBytes(signature.trim());
            return provided.length > 0 && MessageDigest.isEqual(expected, provided);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] hexToBytes(String hex) {
        if (hex.length() % 2 != 0 || !hex.matches("[0-9a-fA-F]+")) {
            return new byte[0];
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
