package com.facturxapi.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** 追加式用量日志：计数=窗口内 count，无竞态、自然月重置免费获得 */
@Entity
@Table(name = "api_usage")
public class ApiUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "key_id", nullable = false)
    private ApiKey apiKey;

    @Column(nullable = false, length = 64)
    private String endpoint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public ApiUsage() {}

    public ApiUsage(ApiKey apiKey, String endpoint) {
        this(apiKey, endpoint, OffsetDateTime.now());
    }

    /** 全参构造器：测试注入历史时间戳用 */
    public ApiUsage(ApiKey apiKey, String endpoint, OffsetDateTime createdAt) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }

    public ApiKey getApiKey() { return apiKey; }

    public String getEndpoint() { return endpoint; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
