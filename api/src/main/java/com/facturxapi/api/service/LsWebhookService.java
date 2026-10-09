package com.facturxapi.api.service;

import com.facturxapi.api.entity.ApiKey;
import com.facturxapi.api.repository.ApiKeyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LemonSqueezy webhook 事件状态机。全部"设绝对值"，事件乱序/重放天然幂等：
 * - created / payment_success → 激活 pro，expires = renews_at
 * - payment_failed → 仅日志（renews_at 未推进时 isProActive() 时间自愈降级）
 * - expired → 降级 free
 * - cancelled → ends_at 在未来则 pro 到 ends_at，否则立即 free
 *
 * 归属三级 fallback：①meta.custom_data.api_key → ②user_email 匹配（无 key 则
 * 自动 provision）→ ③双无：占位 email provision + log.error 人工跟进。
 */
@Service
public class LsWebhookService {

    private static final Logger log = LoggerFactory.getLogger(LsWebhookService.class);

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyService apiKeyService;

    public LsWebhookService(ApiKeyRepository apiKeyRepository, ApiKeyService apiKeyService) {
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyService = apiKeyService;
    }

    @Transactional
    public void handle(String eventName, JsonNode meta, JsonNode data) {
        JsonNode attrs = data.path("attributes");
        String subscriptionId = data.path("id").asText(null);
        String customKey = meta.path("custom_data").path("api_key").asText(null);
        String userEmail = attrs.path("user_email").asText(null);
        OffsetDateTime renewsAt = parseDateTime(attrs.path("renews_at").asText(null));
        OffsetDateTime endsAt = parseDateTime(attrs.path("ends_at").asText(null));

        switch (eventName == null ? "" : eventName) {
            case "subscription_created", "subscription_payment_success" -> {
                ApiKey key = resolveTarget(customKey, userEmail, subscriptionId);
                key.setPlan("pro");
                if (renewsAt != null) {
                    key.setPlanExpiresAt(renewsAt);
                }
                key.setLsSubscriptionId(subscriptionId);
                apiKeyRepository.save(key);
                log.info("webhook: {} sub={} email={} expires={}",
                    eventName, subscriptionId, key.getEmail(), key.getPlanExpiresAt());
            }
            case "subscription_payment_failed" -> {
                // 不主动降级：renews_at 未推进，isProActive() 到期自愈
                log.warn("webhook: payment FAILED sub={} email={}",
                    subscriptionId, userEmail);
            }
            case "subscription_expired" -> {
                ApiKey key = resolveTarget(customKey, userEmail, subscriptionId);
                key.setPlan("free");
                key.setLsSubscriptionId(subscriptionId);
                apiKeyRepository.save(key);
                log.info("webhook: expired sub={} email={} → free",
                    subscriptionId, key.getEmail());
            }
            case "subscription_cancelled" -> {
                ApiKey key = resolveTarget(customKey, userEmail, subscriptionId);
                if (endsAt != null && endsAt.isAfter(OffsetDateTime.now())) {
                    // 已付费期未用完：保留 pro 到 ends_at
                    key.setPlan("pro");
                    key.setPlanExpiresAt(endsAt);
                } else {
                    key.setPlan("free");
                }
                key.setLsSubscriptionId(subscriptionId);
                apiKeyRepository.save(key);
                log.info("webhook: cancelled sub={} email={} plan={} expires={}",
                    subscriptionId, key.getEmail(), key.getPlan(), key.getPlanExpiresAt());
            }
            default -> log.debug("webhook: ignored event {}", eventName);
        }
    }

    /** 归属三级 fallback；email 缺失时用占位邮箱 provision（log.error 人工跟进） */
    private ApiKey resolveTarget(String customKey, String userEmail, String subscriptionId) {
        if (customKey != null && !customKey.isBlank()) {
            var byToken = apiKeyRepository.findByToken(customKey)
                .filter(k -> !k.isRevoked());
            if (byToken.isPresent()) {
                return byToken.get();
            }
            log.warn("webhook: custom_data.api_key not found ({}), falling back to email",
                mask(customKey));
        }

        String email;
        if (userEmail != null && !userEmail.isBlank()) {
            email = userEmail;
        } else {
            log.error("webhook: cannot attribute subscription {} — no custom_data.api_key "
                + "and no user_email; provisioning placeholder, follow up manually",
                subscriptionId);
            email = "unknown+" + subscriptionId + "@lemonsqueezy.local";
        }

        return apiKeyRepository.findByEmailAndRevokedAtIsNull(email)
            .orElseGet(() -> {
                String token = apiKeyService.generateFor(email).orElse(null);
                if (token == null) {
                    throw new IllegalStateException("provision failed for " + email);
                }
                log.info("webhook: auto-provisioned key for {}", email);
                return apiKeyRepository.findByToken(token).orElseThrow();
            });
    }

    /** LS 时间格式兼容：完整 ISO offset 或纯日期（按 UTC 0 点） */
    private static OffsetDateTime parseDateTime(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception e) {
            try {
                return LocalDate.parse(value.substring(0, 10))
                    .atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
            } catch (Exception ignored) {
                log.warn("webhook: unparseable datetime {}", value);
                return null;
            }
        }
    }

    private static String mask(String token) {
        return token.length() <= 12 ? token : token.substring(0, 12) + "...";
    }
}
