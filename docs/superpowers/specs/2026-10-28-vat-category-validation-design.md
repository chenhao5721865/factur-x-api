# /generate vatCategoryCode 一致性校验（400 替代 500 兜底）— 设计

日期：2026-10-28
来源：IH launch 帖评论（fvazquez8，2026-10-20）：税类别决定下游必填字段，0% 无类别应 400 + 规则 ID，而非自校验步 500 兜底。

## 问题

`LineItemRequest.vatCategoryCode` 缺省时紧凑构造器自动填 `"S"`（LineItemRequest.java:24-26）。`vatRate: 0` + 默认 S → 生成 XML 违反 BR-S-05 → 生成后自校验挂 → **500 "Generated XML failed internal validation"**（GenerateController.java:86-92），且因已过配额检查**白扣一次配额**。非法 category（如 `"AE"`、`"X"`）同样走 500 兜底。500 语义是"服务端 bug"，但这两类都是客户端输入问题，应 400。

## 已定决策（与用户逐项确认过）

1. **口径：保留默认 S + 一致性校验**——不传 category 仍默认 S；仅 vatRate 与 category 冲突时报 400。存量合法调用零影响；现在 500 的 0% 调用变 400 纯改善。**不做** category 真必填（@NotBlank）。
2. **类别范围：只允许 S 和 Z**——生成器不输出 BT-120/121（VATEX 免税原因），E/AE/K/G/O 类别必挂 BR-E-10 等规则，放行=明知必败仍 500。其余类别 400 明确拒绝，等真实需求再扩。
3. **错误格式：沿用 `{"error": "..."}` Map**——规则 ID 嵌入消息文本。不做结构化 ruleId/field 字段（现有 @Valid 错误无此结构，避免两套格式共存）。
4. **实现：GenerateController 前置校验**（方案 A）——在配额检查之前，抛 IllegalArgumentException 走 ApiExceptionHandler 现有 400 通道。排除方案 B（类级 Bean Validation 约束产生 ObjectError，现有 handler 只遍历 fieldErrors 会丢消息，需改 handler，改动面大）与方案 C（compact constructor 抛异常被 Jackson 包成 "Malformed JSON"，误导）。

## 校验规则

新私有静态方法 `validateVatConsistency(InvoiceRequest)` 置于 GenerateController，调用位置：**401 鉴权之后、配额检查之前**（方法体前失败不扣额的语义延伸到该校验）。

category 比较前归一化：`trim().toUpperCase(Locale.ROOT)`（`"z"` 接受，生成器输出归一化后的大写值——注意：归一化发生在校验用副本，若用户传小写且合法，生成器须用归一化值而非原值）。

| 条件（按行顺序，命中即抛） | error 消息 |
|---|---|
| 归一化 category ∉ {S, Z} | `lines[i].vatCategoryCode: unsupported value 'X'. Supported: S, Z (others require VATEX reason codes, not yet supported)` |
| S 且 vatRate == 0 | `lines[i].vatRate: BR-S-05 — category S requires vatRate > 0. For 0% use vatCategoryCode: "Z"` |
| Z 且 vatRate != 0 | `lines[i].vatRate: BR-Z-05 — category Z requires vatRate = 0` |

- vatRate 比较用 `BigDecimal.compareTo(BigDecimal.ZERO)`（不用 equals，避免 `0` vs `0.00` scale 陷阱）。
- 错误消息英文（API 现有错误全英文，如 "Missing API key"）。
- 多行发票只报第一处违规（消息含行索引 `lines[i]`，用户修完重发即可，与 @Valid 单消息风格一致）。

**不改** LineItemRequest 的默认 S 行为（向后兼容）。

## 归一化传导

若用户传小写 `"z"` 且合法，XmlInvoiceGenerator 输出的 CategoryCode 必须是 `"Z"`（EN 16931 码表大写），否则自校验挂。实现选择：校验通过后在 controller 侧构建归一化副本传入生成器，或在 LineItemRequest 紧凑构造器里归一化（改动 DTO 但一处解决，含 trim）。**采用后者**：紧凑构造器在填默认 S 的同时做 `trim().toUpperCase(Locale.ROOT)` 归一化——校验逻辑随后面对的总是规范值，生成器零改动。

## 测试（GenerateControllerTest 追加，MockMvc）

1. 0% + 默认 S → 400，error 含 `BR-S-05` 与 `vatRate`
2. 0% + 显式 Z → 200（回归：合法路径不破）
3. 20% + Z → 400 含 `BR-Z-05`
4. category `"AE"` → 400 含 `unsupported`
5. 多行发票第 2 行违规 → 消息含 `lines[1]`
6. 400 路径不扣配额（当月用量计数不变——配额语义守恒）
7. 小写 `"z"` + 0% → 200 且响应 XML 含 `<ram:CategoryCode>Z</ram:CategoryCode>`（归一化传导）

现有 27 测试保持全绿（含既有 402 配额语义测试）。

## /docs 同步（docs.html）

- 字段表 `vatCategoryCode` 行：改为 "défaut `S`；seules `S` et `Z` acceptées；taux 0 % → `Z` obligatoire"
- 现有 ⚠️ 段落（docs.html:143-144）更新：从"会违反 BR-S-05"（软警告）改为"API 直接返回 400 并带规则编号"（硬约束描述）

## 明确不做

- E/AE/K/G/O 类别支持（需 VATEX 原因码字段 BT-121 + 生成器输出，无真实需求驱动）
- 结构化 ruleId/field 错误字段
- web 验证器侧改动（不涉及）
- /api/validate 侧改动（只读验证，无生成语义）

## 部署与冒烟

标准流程：`mvn package`（api 模块）→ scp api JAR 到 `/root/factur-x-api/api/target/` → `docker compose -f docker-compose.prod.yml up -d --build api` → 冒烟：

1. `vatRate: 0` 无 category → 400 含 BR-S-05
2. `vatRate: 0` + `"Z"` → 200 XML
3. `vatRate: 20` 无 category → 200（存量兼容回归）

## 风险

- 存量调用若有 0% 无 category 且依赖 500 做重试逻辑——400 后重试风暴停止，纯改善。
- 归一化改动 LineItemRequest 紧凑构造器，影响面=api 模块全链路，由测试 7 兜底。
