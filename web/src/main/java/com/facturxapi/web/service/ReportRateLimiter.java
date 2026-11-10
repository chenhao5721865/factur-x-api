package com.facturxapi.web.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/** 报告邮件限流：每 IP 每小时 5 次、每邮箱 5 分钟一次（内存实现，单实例部署）。 */
@Component
public class ReportRateLimiter {

    private static final int IP_LIMIT_PER_HOUR = 5;
    private static final long HOUR_MILLIS = 3600_000L;
    private static final long EMAIL_COOLDOWN_MILLIS = 300_000L;

    private record Window(long windowStart, int count) {}

    private final ConcurrentHashMap<String, Window> ipWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastEmailSent = new ConcurrentHashMap<>();

    public boolean allowIp(String ip) {
        long now = System.currentTimeMillis();
        ipWindows.compute(ip, (k, w) ->
            (w == null || now - w.windowStart() >= HOUR_MILLIS)
                ? new Window(now, 1)
                : new Window(w.windowStart(), w.count() + 1));
        return ipWindows.get(ip).count() <= IP_LIMIT_PER_HOUR;
    }

    /** 冷却期内返回 false；否则记下本次发送并返回 true。 */
    public boolean tryAcquireEmail(String email) {
        long now = System.currentTimeMillis();
        AtomicBoolean allowed = new AtomicBoolean(false);
        lastEmailSent.compute(email, (k, last) -> {
            if (last != null && now - last < EMAIL_COOLDOWN_MILLIS) {
                return last;
            }
            allowed.set(true);
            return now;
        });
        return allowed.get();
    }
}
