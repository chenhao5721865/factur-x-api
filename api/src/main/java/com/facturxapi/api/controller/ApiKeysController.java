package com.facturxapi.api.controller;

import com.facturxapi.api.service.ApiKeyService;
import com.facturxapi.api.service.IpRateLimiter;
import com.facturxapi.api.service.KeyResendCooldown;
import com.facturxapi.api.service.ResendMailClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开领 key 端点（定价页自助流程）。已有活跃 key 时把该 key 重发到同一邮箱
 * （收到信即证明邮箱归属），响应仍为 409 且绝不回显 token。
 */
@RestController
@RequestMapping("/api")
public class ApiKeysController {

    private static final Logger log = LoggerFactory.getLogger(ApiKeysController.class);

    public record KeyRequest(@NotBlank @Email String email) {}

    private final ApiKeyService apiKeyService;
    private final IpRateLimiter ipRateLimiter;
    private final ResendMailClient mailClient;
    private final KeyResendCooldown resendCooldown;

    public ApiKeysController(ApiKeyService apiKeyService, IpRateLimiter ipRateLimiter,
                             ResendMailClient mailClient, KeyResendCooldown resendCooldown) {
        this.apiKeyService = apiKeyService;
        this.ipRateLimiter = ipRateLimiter;
        this.mailClient = mailClient;
        this.resendCooldown = resendCooldown;
    }

    @PostMapping(value = "/keys", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@Valid @RequestBody KeyRequest request,
                                    HttpServletRequest httpRequest) {
        String ip = IpRateLimiter.clientIp(
            httpRequest.getHeader("X-Forwarded-For"), httpRequest.getRemoteAddr());
        if (!ipRateLimiter.allow(ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "Too many requests. Try again later."));
        }

        Optional<String> token = apiKeyService.generateFor(request.email());
        if (token.isEmpty()) {
            return duplicateResponse(request.email());
        }
        String key = token.get();
        sendQuietly(request.email(), key);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of("email", request.email(), "apiKey", key));
    }

    /** 丢 key 找回：重发到同一邮箱；受邮箱冷却与发信配置约束，响应不回显 token */
    private ResponseEntity<?> duplicateResponse(String email) {
        Optional<String> existing = apiKeyService.findActiveTokenByEmail(email);
        boolean resent = existing.isPresent()
            && mailClient.isConfigured()
            && resendCooldown.tryAcquire(email)
            && sendQuietly(email, existing.get());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", resent
            ? "An active key already exists for this email. We just re-sent it — check your inbox."
            : "An active key already exists for this email. Check your inbox, "
                + "or retry in a few minutes to receive it by email.");
        body.put("resent", resent);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /** 邮件是激活引导，不是发 key 的前提：失败只记类型，不回滚、不影响主响应 */
    private boolean sendQuietly(String email, String key) {
        try {
            mailClient.sendWelcome(email, key);
            return true;
        } catch (RuntimeException e) {
            log.warn("keys: welcome email failed for {}: {}",
                email, e.getClass().getSimpleName());
            return false;
        }
    }
}
