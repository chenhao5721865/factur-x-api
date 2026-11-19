package com.facturxapi.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 全局每小时建 key 上限：分布式出口（Tor/VPN）每个 IP 只打几次，
 * 能绕过 IpRateLimiter，必须再加一道全局闸。
 */
@Component
public class KeyCreationLimiter {

    private static final long WINDOW_MILLIS = 3600_000L;

    private final int limitPerHour;
    private long windowStart = System.currentTimeMillis();
    private int count;

    public KeyCreationLimiter(@Value("${app.keys.global-limit-per-hour:10}") int limitPerHour) {
        this.limitPerHour = limitPerHour;
    }

    public synchronized boolean allow() {
        long now = System.currentTimeMillis();
        if (now - windowStart >= WINDOW_MILLIS) {
            windowStart = now;
            count = 0;
        }
        count++;
        return count <= limitPerHour;
    }
}
