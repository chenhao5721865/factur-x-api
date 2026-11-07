package com.facturxapi.api.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 同邮箱重发冷却，防有人用公开端点给他人邮箱灌信。
 * 内存实现与单实例部署匹配（同 IpRateLimiter）。
 */
@Component
public class KeyResendCooldown {

    private final ConcurrentHashMap<String, Long> lastResent = new ConcurrentHashMap<>();
    private final long cooldownMillis;

    public KeyResendCooldown(@Value("${app.keys.resend-cooldown-seconds:300}") long cooldownSeconds) {
        this.cooldownMillis = cooldownSeconds * 1000L;
    }

    /** 冷却期外返回 true 并记下本次发送时间；冷却期内返回 false 且不消耗窗口 */
    public boolean tryAcquire(String email) {
        long now = System.currentTimeMillis();
        AtomicBoolean allowed = new AtomicBoolean(false);
        lastResent.compute(email, (k, last) -> {
            if (last != null && now - last < cooldownMillis) {
                return last;
            }
            allowed.set(true);
            return now;
        });
        return allowed.get();
    }
}
