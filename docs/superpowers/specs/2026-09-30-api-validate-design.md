# API 模块设计 — POST /validate

> 2026-09-30 | MVP 付费功能 | 状态：已批准

## 概述

在 `api` 模块（当前为空 Maven 模块）中实现独立的 Spring Boot REST API 应用，提供 `POST /validate` 端点，供已订阅用户通过 API Key 验证 Factur-X 文件。

## 架构决策

| 决策 | 选择 |
|------|------|
| API Key 来源 | 订阅确认后自动生成，绑定 subscriber |
| 速率限制 | MVP 无限制 |
| 文件上传 | 单一 multipart endpoint，PDF 或 XML |
| 响应格式 | 200 直接返回 validationResult，无包装 |
| 错误处理 | 422 处理解析失败和验证不通过 |
| Key 存储 | UUID + BCrypt 哈希 |
| 部署 | 独立 Spring Boot 应用，端口 8081，共享 PostgreSQL |
| API 文档 | SpringDoc OpenAPI |

## 数据模型

### api_keys 表

```sql
CREATE TABLE api_keys (
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(255) NOT NULL REFERENCES subscribers(email),
    key_hash   VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMP
);
```

- `email` 外键关联 subscribers 表（同一 PostgreSQL 实例，跨 schema 外键在 MVP 阶段暂不强制）
- `key_hash` 存储 UUID 的 BCrypt 哈希
- `revoked_at` 为 NULL 表示有效，非 NULL 表示已撤销

### 实体类

```java
@Entity
@Table(name = "api_keys")
public class ApiKey {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "key_hash", nullable = false, unique = true)
    private String keyHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
```

## API 端点

### POST /api/validate

- **认证**：`X-API-Key: <uuid>` header
- **请求**：multipart/form-data，`file` 参数（PDF 或 XML，最大 10MB）
- **200** — 验证通过或不通过，返回 validationResult
- **401** — API Key 无效或未提供
- **403** — API Key 已撤销
- **422** — 文件解析失败或验证有 errors

**成功响应（200）**：
```json
{
  "valid": true,
  "profile": "EN16931",
  "errors": [],
  "warnings": []
}
```

**验证不通过（200，valid=false）**：
```json
{
  "valid": false,
  "profile": null,
  "errors": [
    { "ruleId": "BR-07", "severity": "ERROR", "xpath": null, "message": "..." }
  ],
  "warnings": []
}
```

**解析失败（422）**：
```json
{
  "valid": false,
  "profile": null,
  "errors": [
    { "ruleId": "EXTRACTION_FAILED", "severity": "ERROR", "xpath": null, "message": "PDF 中未找到 Factur-X XML" }
  ],
  "warnings": []
}
```

### GET /api/health

- **200** — `{"status": "UP"}`

## 模块结构

```
api/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/facturxapi/api/
│   │   │   ├── ApiApplication.java          # @SpringBootApplication
│   │   │   ├── config/
│   │   │   │   ├── ValidatorConfig.java     # 复用 web 的 bean 提取逻辑
│   │   │   │   └── JpaConfig.java
│   │   │   ├── controller/
│   │   │   │   ├── ValidateController.java  # POST /api/validate
│   │   │   │   └── HealthController.java    # GET /api/health
│   │   │   ├── service/
│   │   │   │   ├── ApiKeyService.java       # key 生成/验证
│   │   │   │   └── ValidationService.java   # 委托 validator-core
│   │   │   ├── entity/
│   │   │   │   └── ApiKey.java
│   │   │   ├── repository/
│   │   │   │   └── ApiKeyRepository.java
│   │   │   └── security/
│   │   │       └── ApiKeyInterceptor.java   # HandlerInterceptor
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/
│   │           └── V1__create_api_keys.sql
│   └── test/
│       └── java/com/facturxapi/api/
│           ├── ValidateControllerTest.java
│           └── ApiKeyServiceTest.java
└── Dockerfile
```

## 与 validator-core 的关系

- `api` 模块依赖 `validator-core`（和 `web` 模块一样）
- `ValidatorConfig` 从 `validator-core` 的 classpath 资源中提取规则文件（XSD/SCH）到临时目录
- `FacturxValidator` 接收字节数组，返回 `ValidationResult`
- `api` 模块不修改 `validator-core`，只调用其公共 API

## 与 web 模块的关系

- 共享同一个 PostgreSQL 实例（`db` 服务，端口 5432）
- `api` 模块读取 `subscribers` 表（web 模块写入）和写入 `api_keys` 表
- `api` 模块不依赖 `web` 模块的 JAR
- `ValidatorConfig` 逻辑在两个模块中独立配置（可考虑提取为 shared config，MVP 阶段不 refactor）

## 部署

### docker-compose.yml 新增

```yaml
api:
  build:
    context: .
    dockerfile: api/Dockerfile
  ports:
    - "8081:8081"
  environment:
    - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/facturx_api
    - SPRING_DATASOURCE_USERNAME=${POSTGRES_USER:-facturx}
    - SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}
  depends_on:
    db:
      condition: service_healthy
```

### docker-compose.prod.yml 新增

Caddy 路由：
```
api.factur-x-api.com {
    reverse_proxy api:8081
}
```

或通过同一域名路径区分：
```
factur-x-api.com {
    route /api/* {
        reverse_proxy api:8081
    }
    # 其他路由...
}
```

**MVP 阶段**：先跑在 8081 port，通过 `curl` 直接测试。生产部署策略后续确定。

## 订阅确认邮件集成

当前 `SubscriberService.subscribe()` 返回 `SUCCESS` 时，需要：
1. 生成 UUID
2. BCrypt 哈希后存入 `api_keys` 表
3. 将 key 写入确认邮件

**实现方式**：在 `SubscriberService.subscribe()` 成功后，调用 `ApiKeyService.generateFor(email)`。MVP 阶段邮件模板暂不修改（已有订阅成功通知），key 通过后续邮件手动发送或数据库查询。

## 安全考虑

- API Key 以 BCrypt 哈希存储，数据库泄露无法直接获取可用 key
- 文件上传限制 10MB，与 web 模块一致
- XXE 防护由 `validator-core` 的 `XmlRuleValidator` 处理
- PDF 解析使用 PDFBox 3，无额外风险
- MVP 阶段无 rate limiting，后续可通过 Redis 或 Spring RateLimiter 添加
