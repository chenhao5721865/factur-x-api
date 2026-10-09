package com.facturxapi.api.repository;

import com.facturxapi.api.entity.ApiKey;
import com.facturxapi.api.entity.ApiUsage;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiUsageRepository extends JpaRepository<ApiUsage, Long> {

    /** 本月窗口内 count：旧行自然滚出窗口即自动重置 */
    long countByApiKeyAndEndpointAndCreatedAtGreaterThanEqual(
        ApiKey apiKey, String endpoint, OffsetDateTime windowStart);
}
