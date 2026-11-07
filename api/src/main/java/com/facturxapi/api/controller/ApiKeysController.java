package com.facturxapi.api.controller;

import com.facturxapi.api.service.ApiKeyService;
import com.facturxapi.api.service.IpRateLimiter;
import com.facturxapi.api.service.ResendMailClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
 * 公开领 key 端点（定价页自助流程）。幂等语义：已有活跃 key 返回 409 且不回显
 * （公开端点防盗用，丢 key 走 SSH 重发）。
 */
@RestController
@RequestMapping("/api")
public class ApiKeysController {

    private static final Logger log = LoggerFactory.getLogger(ApiKeysController.class);

    public record KeyRequest(@NotBlank @Email String email) {}

    private final ApiKeyService apiKeyService;
    private final IpRateLimiter ipRateLimiter;
    private final ResendMailClient mailClient;

    public ApiKeysController(ApiKeyService apiKeyService, IpRateLimiter ipRateLimiter,
                             ResendMailClient mailClient) {
        this.apiKeyService = apiKeyService;
        this.ipRateLimiter = ipRateLimiter;
        this.mailClient = mailClient;
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
            // 已有活跃 key：不回显 token，提示用户找回头_key
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error",
                    "An active key already exists for this email. "
                        + "Keep it safe — if lost, contact support to reissue."));
        }
        String key = token.get();
        try {
            // 邮件是激活引导，不是发 key 的前提：失败只记类型，不回滚、不影响 201
            mailClient.sendWelcome(request.email(), key);
        } catch (RuntimeException e) {
            log.warn("keys: welcome email failed for {}: {}",
                request.email(), e.getClass().getSimpleName());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of("email", request.email(), "apiKey", key));
    }
}
