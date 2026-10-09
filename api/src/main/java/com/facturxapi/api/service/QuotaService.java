package com.facturxapi.api.service;

import com.facturxapi.api.entity.ApiKey;
import com.facturxapi.api.entity.ApiUsage;
import com.facturxapi.api.repository.ApiUsageRepository;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 追加式用量配额：INSERT 原子无竞态，窗口=本月1日00:00 UTC→now，
 * 旧行自然滚出窗口即自动重置（零定时任务）。
 */
@Service
public class QuotaService {

    public record Decision(boolean allowed, int limit, long used, OffsetDateTime resetAt) {}

    private final ApiUsageRepository usageRepository;
    private final int freeMonthly;
    private final int proMonthly;

    public QuotaService(ApiUsageRepository usageRepository,
                        @Value("${app.quota.free-monthly:5}") int freeMonthly,
                        @Value("${app.quota.pro-monthly:500}") int proMonthly) {
        this.usageRepository = usageRepository;
        this.freeMonthly = freeMonthly;
        this.proMonthly = proMonthly;
    }

    @Transactional
    public Decision checkAndIncrement(ApiKey key, String endpoint) {
        OffsetDateTime now = OffsetDateTime.now();
        int limit = key.isProActive() ? proMonthly : freeMonthly;

        OffsetDateTime windowStart = YearMonth.now(ZoneOffset.UTC)
            .atDay(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        long used = usageRepository
            .countByApiKeyAndEndpointAndCreatedAtGreaterThanEqual(key, endpoint, windowStart);

        // 无论放行与否都记账（失败调用也扣额）
        usageRepository.save(new ApiUsage(key, endpoint, now));

        OffsetDateTime resetAt = YearMonth.now(ZoneOffset.UTC).plusMonths(1)
            .atDay(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        return new Decision(used < limit, limit, used, resetAt);
    }
}
