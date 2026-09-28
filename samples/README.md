# Golden Samples（EN 16931 / Factur-X — CII 语法）

供 spike 校验链测试（Task 4）与 golden 回归测试（Task 6）使用。
命名约定：`valid-*` 必须校验通过；`invalid-*` 必须校验失败（错误为 EN 16931 业务规则层面，
非 XML 格式损坏）。测试按 `valid-*/invalid-*` 前缀自动扫描，勿改名。

所有样例均为 CII 语法（根元素 `rsm:CrossIndustryInvoice`，指南 `urn:cen.eu:en16931:2017`）。

| 文件 | 来源 | 状态 | 说明（预期触发的业务规则） |
|---|---|---|---|
| `valid-en16931-cii-basic.xml` | https://github.com/ConnectingEurope/eInvoicing-EN16931 → `cii/examples/CII_example1.xml`（原件未改动内容；本地副本统一为 LF 行尾） | valid | 官方 EN 16931 示例 1（多行项目发票，NL）。金额链已人工核算一致：行净额合计 229.60 = BT-109；VAT 分解 183.23@6%→10.99 + 46.37@21%→9.74 = BT-110 20.73；BT-112 = 250.33 = BT-115。EUPL-1.2 许可，许可头注释保留 |
| `invalid-missing-buyer.xml` | 上例派生：删除 `<ram:ApplicableHeaderTradeAgreement>` 内整个 `<ram:BuyerTradeParty>` 节点 | invalid | 违反 **BR-07**（CEF schematron 规则 id，"发票必须包含买方名称 BT-44"；EN 16931 标准正文中俗称 BR-5，编号体系差一位，见 docs/notes/spike-decision.md）。买方节点在 CII XSD 中 minOccurs=0，故 XSD 可过、Schematron 应报错——正是校验链要抓的规则层缺陷（实测另连带 BR-10/BR-11 买方地址规则） |
| `invalid-negative-amount.xml` | 上例派生：`<ram:DuePayableAmount>` 由 `250.33` 改为 `-250.33` | invalid | 违反 **BR-CO-16**（BT-115 应付金额 = BT-112 − BT-113 + BT-114，即 250.33 ≠ −250.33）。金额为负且与总计链不一致 |
| `factur-x-basic.pdf` | https://github.com/ZUGFeRD/mustangproject → `library/src/test/resources/EN16931_Einfach.pdf`（原件未改动，Apache-2.0 项目自产样例） | valid | PDF/A-3 发票，内嵌附件名 `factur-x.xml`（/F 与 /UF 均为该名），附件根元素 `rsm:CrossIndustryInvoice`，指南 `urn:cen.eu:en16931:2017`。供 `EmbeddedXmlExtractor` 提取测试（M1-T3）使用 |

## 派生样例的溯源说明

两个 invalid 样例的基底是上表中的 valid 原件（可溯源到 CEF 仓库），改动方式已写入
各文件开头的注释块（XML 声明之后）。改动均为最小确定性 diff，便于回归对比。

未直接选用仓库内现成 invalid 实例的原因：

- CEF 仓库 `test/cii/BR-CO-15-2.xml`、`test/cii/BR-CO-17.xml` 虽为故意违规样例，
  但文件内拼接了 4~5 个 `CrossIndustryInvoice` 根元素（CEF 测试框架自行拆分），
  作为单个 XML 不 well-formed，不符合"可独立校验的完整实例"要求。
- easybill/e-invoice-validator `src/test/resources/Invalid/` 的样例缺陷多为
  profile 未知、BOM/序言字符、废弃币种（HRK）等基础设施层问题，而非缺节点/金额不一致类业务规则。

## 规则资产（XSD + Schematron）

来源：https://github.com/ConnectingEurope/eInvoicing-EN16931 ，
shallow clone @ commit `b6c9e06a59812fb1a83585da40923b3678a649ad`（2026-04-14，validation-1.3.16 分支合并）。
许可证均为 EUPL-1.2。行尾已由 CRLF 统一为 LF（内容未改动）。

### CII XSD（UN/CEFACT D16B SCRDM Subset，uncoupled clm 变体）

- 主 schema（include/import 闭包入口，`SchemaFactory` 编译入口）：
  `samples/schema/cii/CrossIndustryInvoice_100pD16B.xsd`
- 依赖闭包共 **4 个文件**（互相 import、无外部引用，已复制全）：
  - `CrossIndustryInvoice_100pD16B.xsd`（RSM 根元素 `CrossIndustryInvoice`）
  - `CrossIndustryInvoice_QualifiedDataType_100pD16B.xsd`（QDT）
  - `CrossIndustryInvoice_ReusableAggregateBusinessInformationEntity_100pD16B.xsd`（RAM）
  - `CrossIndustryInvoice_UnqualifiedDataType_100pD16B.xsd`（UDT）
- 选 uncoupled（而非 coupled clm）的原因：CEF 官方校验器配置 `buildconfig-cii.xml` 的
  `<inherit>uncefact-d16b-uncoupled-crossindustryinvoice</inherit>` 即用此变体——代码取值
  约束不在 XSD 层，由 Schematron 的 codelist 规则负责，避免多复制 50 个代码表 XSD。

### EN 16931 CII Schematron

- 入口：`samples/schema/schematron/EN16931-CII-validation.sch`（`queryBinding="xslt2"`）
- include 闭包（相对入口文件，共 5 个，已复制全）：
  - `abstract/EN16931-CII-model.sch`、`abstract/EN16931-CII-syntax.sch`（抽象规则）
  - `CII/EN16931-CII-model.sch`、`CII/EN16931-CII-syntax.sch`（CII 语法绑定）
  - `codelist/EN16931-CII-codes.sch`（代码表规则）
- 注意：`schematron/CII/`（schematron 的 CII 子目录）与 `schema/cii/`（XSD 目录）大小写
  仅差 Windows 文件系统不区分，故把 schematron 树整体放在 `schema/schematron/` 之下避让。
- 运行时依赖：ph-schematron-pure 7.1.3（传递引入 Saxon-HE 11.6，xslt2 binding 必需）。
