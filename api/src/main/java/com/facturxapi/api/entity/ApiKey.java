package com.facturxapi.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, unique = true, length = 128)
    private String token;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(nullable = false)
    private String plan = "free";

    @Column(name = "plan_expires_at")
    private OffsetDateTime planExpiresAt;

    @Column(name = "ls_subscription_id", length = 64)
    private String lsSubscriptionId;

    public ApiKey() {}

    public ApiKey(String email, String token) {
        this.email = email;
        this.token = token;
        this.createdAt = OffsetDateTime.now();
        this.plan = "free";
    }

    /** 全参构造器：测试注入历史时间戳用 */
    public ApiKey(String email, String token, OffsetDateTime createdAt, OffsetDateTime revokedAt,
                  String plan, OffsetDateTime planExpiresAt, String lsSubscriptionId) {
        this.email = email;
        this.token = token;
        this.createdAt = createdAt;
        this.revokedAt = revokedAt;
        this.plan = plan;
        this.planExpiresAt = planExpiresAt;
        this.lsSubscriptionId = lsSubscriptionId;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** pro 且未过期（时间自愈：过期后自然回落到 free 计费） */
    public boolean isProActive() {
        return "pro".equals(plan)
            && planExpiresAt != null
            && planExpiresAt.isAfter(OffsetDateTime.now());
    }

    public void revoke() {
        this.revokedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(OffsetDateTime revokedAt) { this.revokedAt = revokedAt; }

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public OffsetDateTime getPlanExpiresAt() { return planExpiresAt; }
    public void setPlanExpiresAt(OffsetDateTime planExpiresAt) { this.planExpiresAt = planExpiresAt; }

    public String getLsSubscriptionId() { return lsSubscriptionId; }
    public void setLsSubscriptionId(String lsSubscriptionId) { this.lsSubscriptionId = lsSubscriptionId; }
}
