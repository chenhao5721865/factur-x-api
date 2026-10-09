package com.facturxapi.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.facturxapi.api.entity.ApiKey;
import com.facturxapi.api.entity.ApiUsage;
import com.facturxapi.api.repository.ApiKeyRepository;
import com.facturxapi.api.repository.ApiUsageRepository;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class QuotaServiceTest {

    @Autowired
    private QuotaService quotaService;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiUsageRepository apiUsageRepository;

    @BeforeEach
    void setUp() {
        apiUsageRepository.deleteAll();
        apiKeyRepository.deleteAll();
    }

    private ApiKey newKey(String token, String plan, OffsetDateTime expiresAt) {
        ApiKey key = new ApiKey(token + "@example.com", token);
        key.setPlan(plan);
        key.setPlanExpiresAt(expiresAt);
        return apiKeyRepository.save(key);
    }

    @Test
    void freeKey_firstFiveAllowed_sixthDenied() {
        ApiKey key = newKey("free-key", "free", null);

        for (int i = 1; i <= 5; i++) {
            QuotaService.Decision d = quotaService.checkAndIncrement(key, "/generate");
            assertThat(d.allowed()).as("call #%d", i).isTrue();
            assertThat(d.limit()).isEqualTo(5);
        }
        QuotaService.Decision denied = quotaService.checkAndIncrement(key, "/generate");
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.limit()).isEqualTo(5);
        assertThat(denied.used()).isEqualTo(5);
        // resetAt = 下月1日 00:00 UTC
        OffsetDateTime expectedReset = YearMonth.now(ZoneOffset.UTC).plusMonths(1)
            .atDay(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        assertThat(denied.resetAt()).isEqualTo(expectedReset);
    }

    @Test
    void proKey_active_limit500() {
        ApiKey key = newKey("pro-key", "pro",
            OffsetDateTime.now().plusDays(20));

        QuotaService.Decision d = quotaService.checkAndIncrement(key, "/generate");
        assertThat(d.allowed()).isTrue();
        assertThat(d.limit()).isEqualTo(500);
    }

    @Test
    void proKey_expired_treatedAsFree() {
        ApiKey key = newKey("expired-key", "pro",
            OffsetDateTime.now().minusDays(1));

        QuotaService.Decision d = quotaService.checkAndIncrement(key, "/generate");
        assertThat(d.allowed()).isTrue();
        assertThat(d.limit()).isEqualTo(5);
    }

    @Test
    void proMissingExpiry_treatedAsFree() {
        ApiKey key = newKey("noexpiry-key", "pro", null);

        QuotaService.Decision d = quotaService.checkAndIncrement(key, "/generate");
        assertThat(d.limit()).isEqualTo(5);
    }

    @Test
    void lastMonthUsage_doesNotCountTowardThisMonth() {
        ApiKey key = newKey("hist-key", "free", null);

        // 上个月的 5 次用量，不应计入本月窗口
        YearMonth lastMonth = YearMonth.now(ZoneOffset.UTC).minusMonths(1);
        for (int day = 1; day <= 5; day++) {
            apiUsageRepository.save(new ApiUsage(key, "/generate",
                lastMonth.atDay(day).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime()));
        }

        QuotaService.Decision d = quotaService.checkAndIncrement(key, "/generate");
        assertThat(d.allowed()).isTrue();
        assertThat(d.used()).isEqualTo(0);
    }

    @Test
    void deniedCall_isAlsoLogged() {
        ApiKey key = newKey("denylog-key", "free", null);
        for (int i = 0; i < 6; i++) {
            quotaService.checkAndIncrement(key, "/generate");
        }
        QuotaService.Decision d = quotaService.checkAndIncrement(key, "/generate");
        assertThat(d.allowed()).isFalse();
        // used = 本次增量前的窗口计数；前 6 次调用各落一行（含第 6 次被拒调用）
        assertThat(d.used()).isEqualTo(6);
        assertThat(apiUsageRepository.count()).isEqualTo(7); // 被拒调用也落库
    }

    @Test
    void differentEndpoints_countSeparately() {
        ApiKey key = newKey("endpoints-key", "free", null);
        quotaService.checkAndIncrement(key, "/generate");
        quotaService.checkAndIncrement(key, "/generate");

        QuotaService.Decision d = quotaService.checkAndIncrement(key, "/validate");
        assertThat(d.used()).isEqualTo(0);
    }
}
