# 邮件捕获 (Email Capture) 设计文档

> 日期：2026-09-29 ｜ 状态：已批准 ｜ 来源：MVP spec §2.4 + §7

## 1. 目标

在已上线的法语验证工具页 (`/validateur-factur-x`) 底部添加可工作的邮件订阅表单。
MVP 阶段单向收集（双确认后续实现），为后续 API test key 分发提供邮箱列表。

## 2. 数据模型

```
表: subscribers
  email      VARCHAR(255) PRIMARY KEY
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

## 3. 技术实现

### 3.1 依赖 (web/pom.xml)

- `spring-boot-starter-data-jpa`
- `org.flywaydb:flyway-core`
- `org.postgresql:postgresql`

### 3.2 配置 (application.yml)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://db:5432/facturx
    username: facturx
    password: facturx
  jpa:
    hibernate:
      ddl-auto: validate  # 仅 Flyway 管 schema
    show-sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration
```

### 3.3 Flyway 迁移

`src/main/resources/db/migration/V1__create_subscribers.sql`

### 3.4 代码结构

```
web/src/main/java/com/facturxapi/web/
  ├── entity/Subscriber.java          # @Entity
  ├── repository/SubscriberRepository.java  # JpaRepository
  ├── service/SubscriberService.java  # subscribe(email), 处理重复/异常
  └── controller/SubscribeController.java  # POST /subscribe (htmx)
```

### 3.7 重复邮箱处理

SubscriberService.subscribe() 捕获 `DataIntegrityViolationException`（PostgreSQL unique constraint violation），
返回 HTTP 200 + 黄色 "您已订阅" 提示 fragment。

### 3.5 前端 (validateur.html)

- 表单添加 `hx-post="/subscribe" hx-target="closest section" hx-swap="outerHTML"`
- 添加 CSRF hidden input: `<input type="hidden" th:name="${_csrf?.parameterName}" th:value="${_csrf?.token}"/>`
- 按钮改为 `type="submit"`，移除 disabled
- 成功/重复/错误 → 不同样式 fragment 返回
- 提交成功后禁用按钮并显示 "Merci! 已订阅"

### 3.6 响应契约

| 场景 | HTTP | 返回 fragment |
|------|------|---------------|
| 成功订阅 | 200 | 绿色 "Merci! 已订阅" 消息 |
| 重复邮箱 | 200 | 黄色 "您已订阅" 提示（捕获 PostgreSQL unique violation） |
| 无效邮箱 | 400 | 红色 "邮箱格式错误" |
| 服务端错误 | 500 | 红色 + traceId |

## 4. 生产部署

- compose 中 db 服务已存在，无需变更
- Flyway 在应用启动时自动执行迁移
- 无需额外环境变量或配置

## 5. 后续（不在本期）

- 双确认 (double opt-in)：发送确认邮件 + 验证 token
- 退订链接
- 邮件发送服务集成 (SMTP)
