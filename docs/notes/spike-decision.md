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
