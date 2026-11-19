package com.facturxapi.web.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SubscribeRateLimiterTest {

    @Test
    void blocksIpAfterLimit() {
        SubscribeRateLimiter limiter = new SubscribeRateLimiter(2, 100);

        assertThat(limiter.allow("1.1.1.1")).isTrue();
        assertThat(limiter.allow("1.1.1.1")).isTrue();
        assertThat(limiter.allow("1.1.1.1")).isFalse();
        assertThat(limiter.allow("2.2.2.2")).isTrue();
    }

    @Test
    void globalLimit_catchesDistributedIps() {
        SubscribeRateLimiter limiter = new SubscribeRateLimiter(5, 2);

        assertThat(limiter.allow("1.1.1.1")).isTrue();
        assertThat(limiter.allow("2.2.2.2")).isTrue();
        assertThat(limiter.allow("3.3.3.3")).isFalse();
    }

    @Test
    void clientIp_usesFirstForwardedSegment() {
        assertThat(SubscribeRateLimiter.clientIp("203.0.113.5, 10.0.0.1", "10.0.0.2"))
            .isEqualTo("203.0.113.5");
        assertThat(SubscribeRateLimiter.clientIp(null, "10.0.0.2")).isEqualTo("10.0.0.2");
    }
}
