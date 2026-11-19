package com.facturxapi.web.service;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 订阅端点限流：每 IP + 全局小时窗口，挡住分布式表单灌注（内存实现，单实例部署）。
 */
@Component
public class SubscribeRateLimiter {

    private static final long WINDOW_MILLIS = 3600_000L;

    private record Window(long windowStart, int count) {}

    private final ConcurrentHashMap<String, Window> ipWindows = new ConcurrentHashMap<>();
    private final int ipLimit;
    private final int globalLimit;
    private long globalWindowStart = System.currentTimeMillis();
    private int globalCount;

    public SubscribeRateLimiter(
            @Value("${app.subscribe.ip-limit-per-hour:5}") int ipLimit,
            @Value("${app.subscribe.global-limit-per-hour:20}") int globalLimit) {
        this.ipLimit = ipLimit;
        this.globalLimit = globalLimit;
    }

    /** 取 X-Forwarded-For 首段（Caddy 反代注入的真实客户端 IP），无则回退 remoteAddr */
    public static String clientIp(String xForwardedFor, String remoteAddr) {
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return remoteAddr;
    }

    public synchronized boolean allow(String ip) {
        long now = System.currentTimeMillis();
        if (now - globalWindowStart >= WINDOW_MILLIS) {
            globalWindowStart = now;
            globalCount = 0;
        }
        globalCount++;
        ipWindows.compute(ip, (k, w) ->
            (w == null || now - w.windowStart() >= WINDOW_MILLIS)
                ? new Window(now, 1)
                : new Window(w.windowStart(), w.count() + 1));
        return ipWindows.get(ip).count() <= ipLimit && globalCount <= globalLimit;
    }
}
