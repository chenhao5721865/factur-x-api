# Spike B 决策记录：XSD + Schematron 纯 Java 校验链（M1-T4）

日期：2026-09-28　结论：**方案 1（纯 Java XSD + Schematron 链）可行**，Task 5 可直接转正。

## 资产与版本

- CII XSD：UN/CEFACT D16B SCRDM Subset（uncoupled clm 变体，4 文件闭包），取自
  ConnectingEurope/eInvoicing-EN16931 @ `b6c9e06a`（2026-04-14，validation-1.3.16 合并），
  与 CEF 官方校验器 `buildconfig-cii.xml` 的 inherit 配置同源。
- Schematron：同仓库 `cii/schematron/EN16931-CII-validation.sch`（queryBinding="xslt2"）
  及其 5 个 include（abstract/ CII/ codelist/）。
- 引擎：ph-schematron-pure 7.1.3 + 传递依赖 Saxon-HE 11.6（xslt2 必需），JDK 17。

## 实测结果（SpikeValidationTest，4/4 绿）

- `valid-en16931-cii-basic.xml`：XSD 通过；Schematron **0 条 failed assert**。
- `invalid-missing-buyer.xml`：XSD 通过（证明 XSD 层抓不到）；Schematron 抓到 **3 条**：
  BR-07（买方名称 BT-44 必填）、BR-10（买方地址 BG-8 必填）、BR-11（买方国家代码 BT-55）。
- `invalid-negative-amount.xml`：Schematron 抓到 **1 条**：BR-CO-16（BT-115 = BT-112 − BT-113 + BT-114）。

## 遇到的坑与解决

1. 任务模板的 ph-schematron API 与 7.1.3 实际不符：`applySchematronValidation` 返回
   `org.w3c.dom.Document` 而非 `SchematronOutputType`；改用
   `applySchematronValidationToSVRL(domNode, baseURI)`（需先解析成 namespace-aware DOM），
   文本/规则 id 用 `SVRLHelper.getAllFailedAssertions(...)` → `getID()/getText()`。
2. 规则 id 编号体系：CEF schematron 是两位零填充且与标准正文俗称错位——"买方名称必填"
   实际 id 是 **BR-07**（BR-05 是发票币种），不是标准正文里的 BR-5。samples/README.md 已同步更正。
   Task 5/6 做错误码映射时要以 schematron 实际 id 为准。
3. Windows 文件系统大小写不敏感：schematron 的 `CII/` 子目录与 XSD 目录 `cii/` 冲突，
   已把 schematron 树整体放在 `schema/schematron/` 下避让。
4. CEF 原始文件为 CRLF，已统一为 LF（内容零改动）。
5. uncoupled clm 变体只需 4 个 XSD（代码表约束由 Schematron 负责），无需 coupled 变体的
   50 个代码表 XSD——这也是 CEF 官方校验器自己的选择。

## 对 Task 5 的输入

- 校验入口固定为：`CrossIndustryInvoice_100pD16B.xsd`（XSD）+
  `EN16931-CII-validation.sch`（Schematron，全 phase，无 phase 限定即可覆盖 model/codelist/syntax）。
- 错误模型：SVRL `FailedAssert`（id + text + flag），可直接映射为 API 的业务错误项。

## 异常契约（M1-T7 成文，web 层按此映射 HTTP 状态码）

| 输入情况 | 行为 | web 层映射 |
|---|---|---|
| 文档可解析（含 XSD/Schematron 不通过） | 正常返回 `ValidationResult`，`valid=false` 是校验结论不是异常 | 200，语义由 errors 决定 |
| PDF 无法提取（非 PDF / 无内嵌 XML） | 抛 `ExtractionException` | 422 |
| 规则引擎自身故障 | 抛 `IllegalStateException` | 500（不应由用户输入触发） |

同一口径已写入 `FacturxValidator` 与 `XmlRuleValidator` 的类 javadoc。

## 里程碑收尾实测（M1-T7，2026-09-28）

- 规则资产：XSD = CEF D16B uncoupled clm（主入口 `samples/schema/cii/CrossIndustryInvoice_100pD16B.xsd`）；
  Schematron = `EN16931-CII-validation.sch`（`samples/schema/schematron/`，CEF @ b6c9e06a）；
  引擎 ph-schematron-pure 7.1.3 + Saxon-HE 11.6。
- 测试：golden 样例 3 个（1 valid / 2 invalid）+ Mustang PDF 端到端 valid=true；
  全量 16 个测试全绿（原 15 + XSD 多错误收集新增 1）。
- XSD 多错误收集实测：输入 `<rsm:foo></bar>`（非法子元素 + 未匹配闭合标签，模板建议输入即可产生多条），
  旧实现报 1 条即停；改用收集型 ErrorHandler（error + fatalError 均收集，按 message 去重——
  实测同一错误会被 error 和 fatalError 各回调一次）后报 2 条。
- 已完成加固：XXE 防护、XSD 多错误收集、/Kids 防环、cause 链、Locale.ROOT。
- 已知限制（后续里程碑处理）：
  1. profile（MINIMUM/BASIC/EN16931）检测未做（`ValidationResult.profile` 恒为 null）；
  2. 严格 PDF/A-3 合规检查未做（需 veraPDF）；
  3. SVRL `SuccessfulReport` warning 未接入（当前全按 ERROR）；
  4. ~~`SchematronResourcePure` 并发安全与 bind 开销未实测~~——已闭环（M2 骨架并发测试 8×25 全绿，
     无需 synchronized，见 docs/notes/m2-skeleton-decisions.md）；
  5. golden 尚未含 PDF 样例（当前 PDF 端到端用例依赖运行时生成的 Mustang PDF）；
  6. golden 样例距 spec §5「有效+无效各 10+」目标尚有缺口（当前 3 个 XML）——M2 验收标准之一；
  7. `validateXsd` 的 `catch (Exception)` 兜底把引擎故障包装为 XSD ERROR Issue（按异常契约应抛
     IllegalStateException 走 500）——M2 排期改为抛 ISE 或收窄 catch（XmlRuleValidator.java:88-90）；
  8. 提取器以文件名匹配（factur-x.xml 精确优先 + .xml 后缀兜底）替代 AFRelationship=Data 检查——
     工程上更稳，但与 spec §2.1 措辞有出入，属有意决策。

