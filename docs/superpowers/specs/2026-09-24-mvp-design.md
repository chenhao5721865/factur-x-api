# MVP 设计文档：Factur-X 免费验证工具 + 验证 API 雏形

> 日期：2026-09-24 ｜ 状态：已获批准 ｜ 域名：factur-x-api.com（Namecheap）｜ 收款：LemonSqueezy（Test mode 已建店）

## 0. 背景与目标

- 关键词验证结论：法语市场窗口期（法国 2026-09-01 生效），技术词簇 KD 全绿，全速立项
- 差异化定位：**Java/Spring Boot 生态最友好的 Factur-X 验证/生成 API**（竞品均为 PHP/Node 背景）
- MVP 目标：免费法语 Factur-X 验证工具页做 SEO 钩子 → 收集邮件 → 内部同能力暴露为 `POST /validate`（API key 占位，为付费铺路）
- 非目标（本期不做）：生成 API（/generate）、付费计费闭环、XRechnung/Peppol 格式、德语内容、多用户自助注册

## 1. 架构总览

单体 Spring Boot（Java 21 + Spring Boot 3.x），一个可执行 JAR + Postgres 16，Docker Compose 编排（开发与生产同一套文件）。

```
factur-x-api/
├── validator-core/    # 验证引擎（纯 Java 库，无 Web 依赖，独立可测）
├── web/               # 免费工具页 + SEO 页面（Thymeleaf 服务端渲染）
└── api/               # POST /validate + API key 鉴权 + 限流
```

Maven 多模块构建。部署：上线周购买 VPS（欧洲机房优先），本机开发全程 Docker。

## 2. 核心组件

### 2.1 validator-core

- 输入：PDF 或 XML 字节流（含内容类型探测）
- PDF 路径：PDFBox 检查 PDF/A-3 → 提取内嵌 `factur-x.xml` 附件（AFRelationship=Data）
- XML 路径：直接进入校验
- 校验两段：XSD（语法层）→ KoSIT validator 引擎跑 Schematron（业务规则层），Factur-X profile 支持 MINIMUM / BASIC / EN 16931
- 输出统一结果模型：

```java
record ValidationResult(
    boolean valid,
    String profile,          // MINIMUM | BASIC | EN16931
    List<Issue> errors,      // ruleId, severity, xpath, message
    List<Issue> warnings)
```

### 2.2 web 免费工具页（法语为主）

- 页面清单：首页、`/validateur-factur-x`（工具页）、`/api-docs`、2-3 篇法规解读文（落地 Easy 词：obligation facture électronique / facture électronique auto-entrepreneur）、`/en` 极简英文镜像
- 文案：AI 生成法语文案 + 用户人工抽查
- 工具页交互：拖拽上传 → 同步验证 → 结果页（红/黄/绿列表：规则号 + 法语说明）
- **隐私策略（产品卖点）**：文件即传即删，不落盘、不存储，全程内存处理；日志只记元数据（大小、格式、结论、耗时）。GDPR 友好

### 2.3 api

- `POST /validate`：multipart 或 raw body（Content-Type 区分 PDF/XML），响应 JSON `ValidationResult`
- 鉴权：`X-API-Key` 请求头；key 存 Postgres；MVP 只发 test key（自己 + 邮件订阅者）
- 限流：Bucket4j 令牌桶，按 key + IP 双维度，超限 429

### 2.4 邮件捕获

- 工具页底部订阅表单 → Postgres 单表（email + created_at）
- 双确认（double opt-in）后补，MVP 先单向收集

## 3. 数据流

浏览器/API 客户端 → `POST /validate` → web 与 api 共用同一 service 层 → validator-core → 结果返回。文件字节全程内存，请求结束即释放。无消息队列、无缓存；验证耗时可观测后再优化。

## 4. 错误处理

| 场景 | HTTP | 表现 |
|------|------|------|
| 非 PDF/XML、>10MB | 400 | 法语友好提示 |
| PDF 无内嵌 XML / 非 PDF/A-3 | 422 | 「这不是 Factur-X 发票（未找到内嵌 XML）」 |
| XSD/Schematron 校验不通过 | 200 | 正常业务结果，逐条列出（产品核心输出） |
| 引擎内部异常 | 500 | 唯一 trace id + 法语兜底文案 |

## 5. 测试策略

- **Golden tests（回归命根子）**：从 FNFE-MPE / KoSIT / easybill 开源仓库收集官方样例（有效+无效各 10+），逐样本断言 `valid` 与具体 ruleId；引擎或规则升级时全量回归
- validator-core 单测：边界 XML 构造
- api：MockMvc 契约测试；web：烟囱测试
- **第 0 周 spike（技术风险前置）**：KoSIT 引擎 + Factur-X profile 本机跑通 3 个官方样例（1 有效 2 无效）。**验收不过 → 降级方案 2**（Python factur-x 库 + FastAPI 微服务，Java 主应用内部调用）

## 6. 技术栈

| 层 | 选型 |
|----|------|
| 语言/框架 | Java 21、Spring Boot 3.5、Maven 多模块 |
| PDF | PDFBox 3 |
| 验证引擎 | KoSIT validator（jar 依赖引入） |
| 页面 | Thymeleaf + Tailwind CDN + htmx（结果局部刷新，不引前端框架） |
| 数据库 | Postgres 16 + Flyway（2-3 个迁移） |
| TLS/反代 | Caddy 或 Nginx 容器（上线时定） |
| 开发/部署 | Docker Compose |

## 7. 上线里程碑（建议序）

1. **第 0 周**：spike 验证 KoSIT + Factur-X 可行性（决策门）
2. **第 1-2 周**：validator-core + golden tests
3. **第 3 周**：web 工具页（法语）+ 邮件捕获
4. **第 4 周**：api + 限流 + 文档页；买 VPS 部署上线；Search Console 提交
5. 之后：SEO 文章按词簇滚动产出 → 观察 `factur-x validator` 等词排名 → 有真实流量后接 LS 付费

## 8. 参考来源

- [InvoiceXML（竞品教材）](https://www.invoicexml.com)
- [KoSIT validator（德国官方 Java 验证器）](https://github.com/itplr-kosit/validator)
- [easybill/e-invoice-validator（开源样例来源之一）](https://github.com/easybill/e-invoice-validator)
- [关键词验证.md（立项依据）](../../关键词验证.md)
