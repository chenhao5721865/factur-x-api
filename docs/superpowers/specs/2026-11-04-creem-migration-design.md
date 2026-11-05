# Creem 迁移设计（替换 LemonSqueezy 付费墙）

> 2026-11-04 ｜ 状态：Draft（待用户确认后进入 plan）
> 前置：LS Live 激活长期无回复，48h 通牒截至 2026-11-05；到期无实质回复即按本设计切换 Creem。

## 1. 背景与目标

LemonSqueezy 因 Stripe 出金限制无法完成 Live 激活，付费墙事实上不可用。Creem 支持中国大陆个人主体（Alipay 出金）、作为 Merchant of Record 处理税务，且已有可用 checkout / webhook API。

目标：

- 新的 `/tarifs` 订阅走 Creem，9 €/月，订阅成功后自动把对应 API key 升为 `pro`（500 次/月）。
- 保留现有 `plan` / `plan_expires_at` 计费模型与配额逻辑，`/api/generate`、`/api/validate` 零改动。
- 订阅取消、到期、暂停、续费都能通过 webhook 正确驱动；事件重复或乱序不破坏状态。
- 保留 LS webhook 端点作为存量兼容，直到现有 pro 订阅自然到期；不迁移、不退订 LS。

非目标：

- 不做 Creem 客户门户（取消订阅先引导用户走 Creem 邮件/后台链接；后续单独做）。
- 不做多档套餐、年付、优惠码、退款自动化。
- 不删除 LS 代码/表字段（单独清理任务）。
- 不改 `/api/keys` 领 key 流程。

## 2. 关键决策

### D1：Creem 与 LS 双轨

新增 `/api/webhook/creem`，保留 `/api/webhook/lemonsqueezy`。两者写同一套 `plan` 字段。
`/tarifs` 的「Souscrire」只跳 Creem；LS checkout URL 配置与前端拼接逻辑删除。
存量 LS pro key 不受影响，按 `plan_expires_at` 自然到期。

### D2：checkout 由服务端创建

Creem 的 `POST /v1/checkouts` 需要 `x-api-key`（secret），不能放前端。新增 `POST /api/checkout`：

- 鉴权：`X-API-Key`，必须是未撤销的 key。
- 服务端用该 key 的 email 作为 `customer.email`，把 token 写入 `metadata.api_key`。
- 返回 `{"checkout_url": "..."}`，前端跳转。
- 未配置 Creem 参数 → `503`，前端提示「Souscription bientôt disponible」。

这样不需要把 email/token 交给浏览器拼 query，也避免伪造归属。

### D3：归属优先级

webhook 归属顺序（与 LS 一致并扩展）：

1. `object.metadata.api_key` → 未撤销 key（主路径）。
2. `creem_subscription_id` 匹配（续费事件可能不带 metadata）。
3. `object.customer.email` → 该 email 的未撤销 key；没有则自动 provision。
4. 无 email：provision 占位 key `unknown+<subscriptionId>@creem.local` + `log.error` 人工跟进。

### D4：绝对赋值 + 单调保护

沿用 LS 的「事件写绝对值」保证重放幂等，但增加乱序保护：

- 激活/续费：仅当新 `current_period_end_date` 晚于当前 `plan_expires_at` 才延长；新到期时间已过去则忽略并记日志（防旧事件复活）。
- 降级事件：若库里 `plan_expires_at` 仍晚于 now，保留 pro 到该时间（防 `expired` 早于 `paid` 到达）；否则立即 free。
- 所有事件只按 `eventType` + `object.status` 更新，不累加、不依赖顺序。

### D5：数据模型最小改动

新增列 `creem_subscription_id`，保留 `ls_subscription_id`。不复用 LS 列，避免语义混淆；两个 provider 的 ID 互不覆盖。

## 3. Creem 外部契约（实现前以 test mode 实际 payload 复核）

### 3.1 API

- 生产 base：`https://api.creem.io/v1`；测试 base：`https://test-api.creem.io/v1`。
- 鉴权头：`x-api-key: creem_...`。
- 创建 checkout：`POST /v1/checkouts`

```json
{
  "product_id": "prod_...",
  "request_id": "<uuid>",
  "success_url": "https://factur-x-api.com/tarifs?merci=1",
  "customer": { "email": "buyer@example.com" },
  "metadata": { "api_key": "facturx_..." }
}
```

响应关键字段：`checkout_url`、`id`、`status`。

### 3.2 Webhook

- 入口：`POST /api/webhook/creem`，`Content-Type: application/json`。
- 签名头：`creem-signature`，值为 **hex** 的 `HMAC-SHA256(rawBody, webhookSecret)`（无 `t=`/`v1=` 前缀）。
- 顶层字段：`eventType`、`object`。
- 订阅对象关键路径：`object.id`、`object.status`、`object.customer.email`、`object.product.id`、`object.current_period_end_date`、`object.metadata`。
- Creem 失败重试最多 5 次、24h 内退避；同一事件可能重复投递。
- 未配置 secret → `503`（fail closed）；签名错误 → `401`；JSON 错误 → `400`；缺 `eventType` → `400`；未知事件 → `200` 并记 debug。

## 4. 内部接口

### 4.1 `POST /api/checkout`

请求：无 body；`X-API-Key` 必填。
响应：

- `200 {"checkout_url": "https://checkout.creem.io/..."}`
- `401 {"error": "Invalid or missing API key"}`
- `503 {"error": "Checkout not configured"}`（`CREEM_API_KEY` / `CREEM_PRODUCT_ID` 为空）
- `502 {"error": "Payment provider unavailable"}`（Creem 非 2xx / 超时）
- `500` 其他未预期错误（不返回 provider 原始 body）

实现：`RestClient`，连接超时 5s、读超时 10s，不自动重试（避免重复 checkout）；日志只记录 status 和脱敏后的 key。

### 4.2 `POST /api/webhook/creem`

返回 `200 {"received": true}`（包括忽略的事件）。错误码见 3.2。

## 5. 事件与状态机

| eventType | 动作 |
|---|---|
| `subscription.active` | `plan=pro`，`expires=current_period_end_date`，记录 `creem_subscription_id` |
| `subscription.paid` | 同上（续费）；新到期时间必须晚于 now |
| `subscription.trialing` | 同上（试用期也给 pro） |
| `subscription.scheduled_cancel` | 保留 pro 到 `current_period_end_date`（不立即降级） |
| `subscription.canceled` | 到期时间在未来 → pro 到该时间；否则 free |
| `subscription.expired` | 到期时间在未来（乱序保护）→ 保持 pro；否则 free |
| `subscription.paused` | 立即 free（暂停视为撤销访问） |
| `subscription.past_due` / `subscription.unpaid` | 仅日志，保留 pro 到到期时间（dunning 窗口，时间自愈） |
| `subscription.update` | 按 `object.status` 走通用映射（见下） |
| `checkout.completed` | 仅日志（订阅事件负责授权） |
| `refund.created` / `dispute.created` | 仅 `log.warn`，人工处理 |
| 其他 | debug 日志，200 |

通用状态映射（用于 `subscription.update`）：

- `active` / `trialing` / `paid` → 授予 pro 到 `current_period_end_date`
- `scheduled_cancel` / `canceled`（到期未来）→ pro 到到期
- `canceled` / `expired` / `paused` / `unpaid`（到期已过或无到期）→ free
- `past_due` / `incomplete` → 仅日志

`current_period_end_date` 缺失时：**不授予 pro**，`log.error` 并返回 200（避免 Creem 无意义重试），人工跟进。

## 6. 数据模型

新增 Flyway `api/src/main/resources/db/migration/V3__creem_subscription.sql`：

```sql
ALTER TABLE api_keys ADD COLUMN creem_subscription_id VARCHAR(64);
CREATE INDEX idx_api_keys_creem_sub ON api_keys(creem_subscription_id);
```

实体 `ApiKey`：

- 新字段 `creemSubscriptionId` + getter/setter。
- 全参构造器保留原 7 参签名（测试兼容），另加 8 参重载或改测试；plan 阶段确认。

`ApiKeyRepository` 新增：

```java
Optional<ApiKey> findByCreemSubscriptionId(String creemSubscriptionId);
```

## 7. 代码改动清单

**api**

- `controller/CreemWebhookController.java`（签名校验，结构仿 `LsWebhookController`）
- `service/CreemWebhookService.java`（状态机 + 归属）
- `service/CreemCheckoutClient.java`（`RestClient` 封装 `POST /v1/checkouts`）
- `controller/CheckoutController.java`（`POST /api/checkout`）
- `entity/ApiKey.java`、`repository/ApiKeyRepository.java`
- `db/migration/V3__creem_subscription.sql`
- `application.yml`：

```yaml
app:
  creem:
    api-base: ${CREEM_API_BASE:https://api.creem.io/v1}
    api-key: ${CREEM_API_KEY:}
    product-id: ${CREEM_PRODUCT_ID:}
    success-url: ${CREEM_SUCCESS_URL:https://factur-x-api.com/tarifs?merci=1}
    webhook-secret: ${CREEM_WEBHOOK_SECRET:}
```

**web**

- `templates/tarifs.html`：
  - 删除 `CHECKOUT_URL` 与 `checkout[custom][api_key]` 拼接；
  - 「Souscrire」点击 `POST /api/checkout`（带 `X-API-Key`）并跳转 `checkout_url`；
  - 处理 401 / 503 / 502 文案；
  - footer 改为「Paiements traités par Creem」+ `https://www.creem.io`。
- `TarifsController` 删除 `app.lemonsqueezy.checkout-url` 注入与 model 属性（页面不再需要）。
- `web/application.yml` 删除 LS checkout 配置。

**部署**

- `Caddyfile` 的 `@api path` 增加 `/api/checkout`、`/api/webhook/creem`。
- `docker-compose.prod.yml` 的 `api` 环境变量增加 `CREEM_API_KEY`、`CREEM_PRODUCT_ID`、`CREEM_SUCCESS_URL`、`CREEM_WEBHOOK_SECRET`、可选 `CREEM_API_BASE`。
- `.env`（服务器，不进仓库）填真实值；`LS_WEBHOOK_SECRET` 保留。

## 8. 测试

**webhook（MockMvc）**

- 无/错签名 → 401；secret 空 → 503；坏 JSON → 400；缺 eventType → 400。
- `subscription.active` / `paid` → plan=pro、expires 正确、creem id 落库。
- `scheduled_cancel` → 仍 pro 到期末；`canceled` 期末在未来 → 仍 pro；期末已过 → free。
- `expired` 乱序（库中 expiry 在未来）→ 保持 pro；正常过期 → free。
- `past_due` / `unpaid` → 不降级。
- `metadata.api_key` 归属；无 metadata 时 email 归属；无 email 时占位 provision + 日志。
- 同一事件重复投递 → 状态不重复变化（幂等）。
- 未知事件 → 200。

**checkout**

- 无 key / 已撤销 key → 401。
- 未配置 → 503。
- Creem 返回 200 → 响应 `checkout_url`；请求体含 `product_id`、`metadata.api_key`、`customer.email`、`success_url`、`request_id`。
- Creem 4xx/5xx/超时 → 502，响应不含 provider 原始错误。
- 日志不含完整 token。

**web**

- `/tarifs` 不再引用 LemonSqueezy/`CHECKOUT_URL`，包含 `creem.io` 与 `/api/checkout`。
- `/tarifs?merci=1` 感谢横幅保持。

**回归**

- LS webhook 既有测试全绿。
- 全模块 `mvn test` 绿；部署后线上冒烟。

## 9. 上线步骤

1. **用户侧（关键路径）**：注册 Creem → 完成个人 KYC → 创建 recurring 产品「Factur-X API Pro」9 €/月 → 取 test mode API key / product ID / webhook secret。
2. 本地实现 + 全量测试；用 Creem **test mode** 创建 checkout，确认返回 `checkout_url`。
3. 用临时公网隧道（cloudflared/ngrok）把 `/api/webhook/creem` 暴露给 Creem test webhook，**抓取真实 payload**，核对 `eventType` / `object.*` / `metadata` / 日期字段；有偏差先改代码与测试。
4. 生产部署：V3 迁移 → api JAR → web JAR → Caddyfile 白名单 → `.env` 写入 live `CREEM_*` → 重建 `api`、`app`，reload Caddy。
5. 线上小额真实购买（9 €，自己的卡）：验证 plan=pro、`plan_expires_at`、第 6 次调用仍 200、`X-RateLimit-Limit: 500`；再测试取消/到期路径（可用 test mode 模拟）。
6. 保留 LS 端点；待存量 pro 到期后另开任务删除 LS 代码、`ls_subscription_id` 与 `LS_*` 配置。

## 10. 风险与待确认

- **Creem 字段偏差**：本文档字段来自官方文档摘要，必须在 test mode 用真实 payload 复核；这是实现前唯一硬前置。
- **KYC/审核时长**：Creem 开户与审核只能用户完成，可能阻塞上线；代码可以先合入但 checkout 保持 503。
- **出金**：最低 $50、每月 1/15 日、单笔上限 50k CNY；属于现金流约束，不影响代码。
- **订阅状态语义**：`canceled` 是「已排期取消」还是「已终止」需在 test mode 确认；现有设计用 `current_period_end_date` 做兜底，两种语义都不会提前断供。
- **乱序事件**：单调保护只防降级/缩短；若 Creem 提供事件时间戳，plan 阶段可再加 `last_event_at` 做严格排序。
- **checkout 滥用**：有效 free key 可反复创建 checkout；先依赖 Creem 限流，若日志显示异常再加每 key 频率限制。

## 11. 验收标准

- `/tarifs` 点击 Souscrire 能跳到 Creem checkout，metadata 携带当前 API key。
- Creem 测试订阅成功后，对应 key 在 1 个 webhook 内变为 pro，到期时间与 `current_period_end_date` 一致。
- 取消排期不提前降级；到期后自动回落 free；重复事件不改变结果。
- 签名错误、secret 未配置、provider 故障都有明确状态码，且不会误激活。
- 既有 LS 订阅与全部 132+ 测试不回归。
- 线上 `/tarifs`、`/docs`、`/api/generate` 冒烟通过。

## 参考

- Creem Webhooks：https://docs.creem.io/code/webhooks
- Creem Create Checkout：https://docs.creem.io/api-reference/endpoint/create-checkout
- Creem Checkout API：https://docs.creem.io/features/checkout/checkout-api
- Creem Subscriptions：https://docs.creem.io/features/subscriptions/introduction
- Creem API Introduction：https://docs.creem.io/api-reference/introduction
