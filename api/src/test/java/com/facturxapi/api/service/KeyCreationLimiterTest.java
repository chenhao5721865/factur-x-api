package com.facturxapi.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KeyCreationLimiterTest {

    @Test
    void allowsUpToLimit_thenBlocks() {
        KeyCreationLimiter limiter = new KeyCreationLimiter(2);

        assertThat(limiter.allow()).isTrue();
        assertThat(limiter.allow()).isTrue();
        assertThat(limiter.allow()).isFalse();
    }
}
