package com.facturxapi.api.service;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 内存固定窗口限流（公开端点防滥用）。单实例部署够用；
 * 多实例或需持久化时再换 Redis（backlog）。
 */
@Component
public class IpRateLimiter {

    private static record Window(long windowStart, int count) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int limitPerHour;
    private final long windowMillis;

    public IpRateLimiter(@Value("${app.keys.ip-limit-per-hour:5}") int limitPerHour) {
        this.limitPerHour = limitPerHour;
        this.windowMillis = 3600_000L;
    }

    /** 取 X-Forwarded-For 首段（Caddy 反代注入的真实客户端 IP），无则回退 remoteAddr */
    public static String clientIp(String xForwardedFor, String remoteAddr) {
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return remoteAddr;
    }

    public boolean allow(String ip) {
        long now = System.currentTimeMillis();
        windows.compute(ip, (k, w) -> {
            if (w == null || now - w.windowStart() >= windowMillis) {
                return new Window(now, 1);
            }
            return new Window(w.windowStart(), w.count() + 1);
        });
        Window w = windows.get(ip);
        return w.count() <= limitPerHour;
    }
}
