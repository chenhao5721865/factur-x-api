# POST /api/generate 设计（JSON → EN16931 CII XML）

日期：2026-10-08 状态：已实现（38 测试绿 + 本地双端点冒烟通过）

## 范围

MVP 的另一半（第一个是 /api/validate）：输入 JSON 发票数据 → 输出 Factur-X 合规
CII XML。**v1 只做 XML，不做 PDF/A-3**（用户已确认）。沿用 X-API-Key 鉴权，
无配额概念（配额检查留 backlog，配合 LemonSqueezy 上架时做）。

核心卖点：**服务端计算金额链**——用户只传净单价/数量/税率，LineTotal/TaxTotal/
GrandTotal 全部由服务端算出并保证一致（BR-CO-10/13/14/15/16）。
**生成即验证**：产出 XML 用项目自有 FacturxValidator 自校验，valid=false = 服务端 bug（500）。

## 关键决策

| 议题 | 决策 |
|---|---|
| 输出 | CII XML，EN16931 profile（`urn:cen.eu:en16931:2017`），镜像 samples/valid-en16931-cii-basic.xml 结构 |
| 金额语义 | 单价/数量为**净额（HT 不含 VAT）** |
| 总金额 | 服务端计算，请求体不接受总金额字段 |
| XML 生成 | JDK 内置 StAX XMLStreamWriter（零新依赖；拒绝 JAXB/Jackson XML/StringBuilder） |
| 输入校验失败 | 400，经 ApiExceptionHandler 统一 `{"error":...}`，assignableTypes 限定 GenerateController |
| 自校验失败 | 500 通用错误体，明细只进 ERROR 日志 |
| 鉴权 | 复制 ValidateController 两步（header 缺失 401 / resolveEmail 空 401），MVP 不抽公共组件 |
| xsi:schemaLocation | 不输出（自校验不读它） |
| BR-CO-26 | 卖方须至少一个标识符（vatId/legalOrgId），服务层显式检查 → 400 |

## DTO 字段 ↔ BT 编号映射

**InvoiceRequest**

| 字段 | BT | 约束 |
|---|---|---|
| invoiceNumber | BT-1 | @NotBlank |
| issueDate | BT-2 | @NotNull LocalDate |
| currency | BT-5 | @NotBlank |
| typeCode | BT-3 | 默认 "380" |
| seller / buyer | BG-4 / BG-7 | @NotNull @Valid PartyRequest |
| lines | BG-25 | @NotEmpty @Valid |
| payment | BG-16 等 | 整体可选 |
| note | BT-22 | 可选 |

**PartyRequest**：name（BT-27/44 @NotBlank）、countryCode（BT-40/55 @NotBlank）、
vatId（BT-31/48）、legalOrgId（BT-30）、postcode/lineOne/city（可选，地址只要求 CountryID）。

**LineItemRequest**：description（BT-153 @NotBlank）、quantity（BT-129 @Positive）、
unitPrice（BT-146 净额 @PositiveOrZero）、vatRate（BT-152 @PositiveOrZero）、
unitCode（BT-130 默认 C62）、vatCategoryCode（BT-151 默认 S）。
LineID（BT-126）按下标自动生成。

**PaymentRequest**：iban（BT-91）、paymentReference（BT-83）、dueDate（BT-9）。

## 金额计算规则（InvoiceAmountCalculator，纯函数）

- 行：`lineTotal = quantity × unitPrice`，scale=2 HALF_UP
- 分组：按 `(vatCategoryCode, vatRate)`（rate 按 stripTrailingZeros 归一）→
  `basis = Σ行（已舍入）`、`tax = basis × rate / 100` HALF_UP scale=2
- 五金额链：`lineTotal = taxBasis = Σ行`；`taxTotal = Σ分组tax`；
  `grand = basis + tax`；`duePayable = grand`
- 0% 税率分组照样输出

## 错误码表

| 场景 | 状态 | 体 |
|---|---|---|
| 无 X-API-Key | 401 | `{"error":"Missing API key..."}` |
| 无效 key | 401 | `{"error":"Invalid or missing API key"}` |
| 坏 JSON | 400 | `{"error":"Malformed JSON request body"}` |
| @Valid 校验失败 | 400 | `{"error":"<field> <message>; ..."}` |
| 卖方无标识符（BR-CO-26） | 400 | `{"error":"Seller must provide at least one of..."}` |
| 生成 XML 自校验失败 | 500 | `{"error":"Generated XML failed internal validation"}` |
| 成功 | 200 | application/xml + Content-Disposition attachment |

成功响应：`Content-Type: application/xml`、
`Content-Disposition: attachment; filename="invoice-<sanitized>.xml"`
（invoiceNumber 去掉非 `[A-Za-z0-9._-]` 字符防 header 注入）。

## 实施中偏离计划的点

1. **去掉 `produces=application/xml`**：声明 produces 后，401/400 的 Map 错误体
   找不到 XML HttpMessageConverter → 500。改为映射不声明 produces，成功时
   ResponseEntity 显式 contentType(APPLICATION_XML)。行为不变。
2. **0% 税率必须配 category Z**：category S + rate 0 违反 BR-S-05 会被自校验
   拦截（返回 500）。这是自校验兜底设计的一部分：codelist/业务规则组合不强校验，
   由自校验拦。集成测试用 (Z, 0%) 验证 0% 分组照常输出。

## 已知限制

- EN16931-only（无 BASIC/EXTENDED profile 输出）
- 无折让/费用（BG-20/21 不支持）
- TypeCode 仅支持发票语义（默认 380；不强校验用户传入值）
- 负行不支持（quantity @Positive）
- codelist（unitCode、countryCode、currency、vatCategoryCode）不强校验，自校验兜底
- 无配额/计费钩子（商业化 backlog）

## 商业化备注

该端点是付费墙触发点。配额检查、用量计数、LemonSqueezy 订阅校验留 backlog；
当前任何有效 API key 均可无限调用。
