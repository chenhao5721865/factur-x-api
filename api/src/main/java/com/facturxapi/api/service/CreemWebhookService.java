package com.facturxapi.api.service;

import com.facturxapi.api.entity.ApiKey;
import com.facturxapi.api.repository.ApiKeyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creem webhook 状态机：事件写绝对值，授权只延长不缩短（乱序保护），降级在付费期未满时保留 pro。
 * 归属：metadata.api_key → creem_subscription_id → customer.email（可 provision）→ 占位邮箱。
 */
@Service
public class CreemWebhookService {

    private static final Logger log = LoggerFactory.getLogger(CreemWebhookService.class);

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyService apiKeyService;

    public CreemWebhookService(ApiKeyRepository apiKeyRepository, ApiKeyService apiKeyService) {
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyService = apiKeyService;
    }

    @Transactional
    public void handle(String eventType, JsonNode object) {
        String subscriptionId = text(object, "id");
        String status = text(object, "status");
        String email = text(object.path("customer"), "email");
        String metadataKey = text(object.path("metadata"), "api_key");
        OffsetDateTime periodEnd = parseDateTime(text(object, "current_period_end_date"));

        switch (eventType == null ? "" : eventType) {
            case "subscription.active", "subscription.paid", "subscription.trialing" ->
                grantPro(eventType, metadataKey, email, subscriptionId, periodEnd);
            case "subscription.scheduled_cancel" ->
                grantPro(eventType, metadataKey, email, subscriptionId, periodEnd);
            case "subscription.canceled" -> {
                if (isFuture(periodEnd)) {
                    grantPro(eventType, metadataKey, email, subscriptionId, periodEnd);
                } else {
                    downgrade(eventType, metadataKey, email, subscriptionId, true);
                }
            }
            case "subscription.expired" -> {
                if (isFuture(periodEnd)) {
                    log.warn("webhook: expired arrived before period end (sub={}, end={}) — keeping pro",
                        subscriptionId, periodEnd);
                } else {
                    downgrade(eventType, metadataKey, email, subscriptionId, true);
                }
            }
            case "subscription.paused" ->
                downgrade(eventType, metadataKey, email, subscriptionId, false);
            case "subscription.past_due", "subscription.unpaid" ->
                log.warn("webhook: {} sub={} email={} — keeping current plan until expiry",
                    eventType, subscriptionId, email);
            case "subscription.update" ->
                applyStatus(status, metadataKey, email, subscriptionId, periodEnd);
            case "checkout.completed" ->
                log.info("webhook: checkout completed sub={} email={}", subscriptionId, email);
            case "refund.created", "dispute.created" ->
                log.warn("webhook: {} sub={} email={} — manual follow-up required",
                    eventType, subscriptionId, email);
            default -> log.debug("webhook: ignored event {}", eventType);
        }
    }

    private void applyStatus(String status, String metadataKey, String email,
                             String subscriptionId, OffsetDateTime periodEnd) {
        switch (status == null ? "" : status) {
            case "active", "trialing", "paid" ->
                grantPro("subscription.update/" + status, metadataKey, email, subscriptionId, periodEnd);
            case "scheduled_cancel" ->
                grantPro("subscription.update/" + status, metadataKey, email, subscriptionId, periodEnd);
            case "canceled", "expired" -> {
                if (isFuture(periodEnd)) {
                    grantPro("subscription.update/" + status, metadataKey, email, subscriptionId, periodEnd);
                } else {
                    downgrade("subscription.update/" + status, metadataKey, email, subscriptionId, true);
                }
            }
            case "paused" ->
                downgrade("subscription.update/" + status, metadataKey, email, subscriptionId, false);
            case "unpaid" ->
                downgrade("subscription.update/" + status, metadataKey, email, subscriptionId, true);
            case "past_due", "incomplete" ->
                log.warn("webhook: subscription.update status={} sub={} — no change", status, subscriptionId);
            default -> log.debug("webhook: subscription.update ignored status={} sub={}",
                status, subscriptionId);
        }
    }

    private void grantPro(String eventType, String metadataKey, String email,
                          String subscriptionId, OffsetDateTime periodEnd) {
        if (periodEnd == null) {
            log.error("webhook: {} sub={} missing current_period_end_date — not granting pro",
                eventType, subscriptionId);
            return;
        }
        if (!periodEnd.isAfter(OffsetDateTime.now())) {
            log.warn("webhook: {} sub={} period end {} already past — ignoring (stale event)",
                eventType, subscriptionId, periodEnd);
            return;
        }
        ApiKey key = resolveTarget(metadataKey, email, subscriptionId, true);
        if (key == null) {
            return;
        }
        boolean keepLaterExpiry = "pro".equals(key.getPlan())
            && key.getPlanExpiresAt() != null
            && key.getPlanExpiresAt().isAfter(periodEnd);
        if (keepLaterExpiry) {
            log.debug("webhook: {} sub={} keeping later expiry {}",
                eventType, subscriptionId, key.getPlanExpiresAt());
        } else {
            key.setPlan("pro");
            key.setPlanExpiresAt(periodEnd);
        }
        if (subscriptionId != null && !subscriptionId.isBlank()) {
            key.setCreemSubscriptionId(subscriptionId);
        }
        apiKeyRepository.save(key);
        log.info("webhook: {} sub={} email={} plan={} expires={}",
            eventType, subscriptionId, key.getEmail(), key.getPlan(), key.getPlanExpiresAt());
    }

    private void downgrade(String eventType, String metadataKey, String email,
                           String subscriptionId, boolean keepUntilPaidPeriod) {
        ApiKey key = resolveTarget(metadataKey, email, subscriptionId, false);
        if (key == null) {
            log.error("webhook: {} sub={} no key found to downgrade", eventType, subscriptionId);
            return;
        }
        if (keepUntilPaidPeriod
                && key.getPlanExpiresAt() != null
                && key.getPlanExpiresAt().isAfter(OffsetDateTime.now())) {
            log.info("webhook: {} sub={} paid period runs until {} — keeping pro",
                eventType, subscriptionId, key.getPlanExpiresAt());
        } else {
            key.setPlan("free");
            log.info("webhook: {} sub={} email={} → free", eventType, subscriptionId, key.getEmail());
        }
        if (subscriptionId != null && !subscriptionId.isBlank()) {
            key.setCreemSubscriptionId(subscriptionId);
        }
        apiKeyRepository.save(key);
    }

    /** 归属 fallback；provision=false 时找不到就返回 null（降级事件不创建 key） */
    private ApiKey resolveTarget(String metadataKey, String email, String subscriptionId,
                                 boolean provision) {
        if (isPresent(metadataKey)) {
            Optional<ApiKey> byToken = apiKeyRepository.findByToken(metadataKey)
                .filter(k -> !k.isRevoked());
            if (byToken.isPresent()) {
                return byToken.get();
            }
            log.warn("webhook: metadata.api_key not found ({}), falling back",
                mask(metadataKey));
        }
        if (isPresent(subscriptionId)) {
            Optional<ApiKey> bySub = apiKeyRepository.findByCreemSubscriptionId(subscriptionId);
            if (bySub.isPresent()) {
                return bySub.get();
            }
        }
        if (isPresent(email)) {
            Optional<ApiKey> byEmail = apiKeyRepository.findByEmailAndRevokedAtIsNull(email);
            if (byEmail.isPresent() || !provision) {
                return byEmail.orElse(null);
            }
        } else if (!provision) {
            return null;
        }

        String targetEmail;
        if (isPresent(email)) {
            targetEmail = email;
        } else {
            log.error("webhook: cannot attribute subscription {} — no metadata.api_key and no "
                + "customer email; provisioning placeholder, follow up manually", subscriptionId);
            targetEmail = "unknown+"
                + (isPresent(subscriptionId) ? subscriptionId : UUID.randomUUID())
                + "@creem.local";
        }

        Optional<ApiKey> existing = apiKeyRepository.findByEmailAndRevokedAtIsNull(targetEmail);
        if (existing.isPresent()) {
            return existing.get();
        }
        String token = apiKeyService.generateFor(targetEmail).orElse(null);
        if (token == null) {
            return apiKeyRepository.findByEmailAndRevokedAtIsNull(targetEmail).orElse(null);
        }
        log.info("webhook: auto-provisioned key for {}", targetEmail);
        return apiKeyRepository.findByToken(token).orElse(null);
    }

    private static boolean isFuture(OffsetDateTime value) {
        return value != null && value.isAfter(OffsetDateTime.now());
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank() && !"null".equals(value);
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }

    /** Creem 日期可能是完整 ISO offset 或纯日期（按 UTC 0 点） */
    private static OffsetDateTime parseDateTime(String value) {
        if (!isPresent(value)) {
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
