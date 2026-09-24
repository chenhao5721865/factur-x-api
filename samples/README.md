# Golden Samples（EN 16931 / Factur-X — CII 语法）

供 spike 校验链测试（Task 4）与 golden 回归测试（Task 6）使用。
命名约定：`valid-*` 必须校验通过；`invalid-*` 必须校验失败（错误为 EN 16931 业务规则层面，
非 XML 格式损坏）。测试按 `valid-*/invalid-*` 前缀自动扫描，勿改名。

所有样例均为 CII 语法（根元素 `rsm:CrossIndustryInvoice`，指南 `urn:cen.eu:en16931:2017`）。

| 文件 | 来源 | 状态 | 说明（预期触发的业务规则） |
|---|---|---|---|
| `valid-en16931-cii-basic.xml` | https://github.com/ConnectingEurope/eInvoicing-EN16931 → `cii/examples/CII_example1.xml`（原件未改动） | valid | 官方 EN 16931 示例 1（多行项目发票，NL）。金额链已人工核算一致：行净额合计 229.60 = BT-109；VAT 分解 183.23@6%→10.99 + 46.37@21%→9.74 = BT-110 20.73；BT-112 = 250.33 = BT-115。EUPL-1.2 许可，许可头注释保留 |
| `invalid-missing-buyer.xml` | 上例派生：删除 `<ram:ApplicableHeaderTradeAgreement>` 内整个 `<ram:BuyerTradeParty>` 节点 | invalid | 违反 **BR-5**（发票必须包含买方名称 BT-44）。买方节点在 CII XSD 中 minOccurs=0，故 XSD 可过、Schematron 应报错——正是校验链要抓的规则层缺陷 |
| `invalid-negative-amount.xml` | 上例派生：`<ram:DuePayableAmount>` 由 `250.33` 改为 `-250.33` | invalid | 违反 **BR-CO-16**（BT-115 应付金额 = BT-112 + BT-113 − BT-114，即 250.33 ≠ −250.33）。金额为负且与总计链不一致 |

## 派生样例的溯源说明

两个 invalid 样例的基底是上表中的 valid 原件（可溯源到 CEF 仓库），改动方式已写入
各文件开头的注释块（XML 声明之后）。改动均为最小确定性 diff，便于回归对比。

未直接选用仓库内现成 invalid 实例的原因：

- CEF 仓库 `test/cii/BR-CO-15-2.xml`、`test/cii/BR-CO-17.xml` 虽为故意违规样例，
  但文件内拼接了 4~5 个 `CrossIndustryInvoice` 根元素（CEF 测试框架自行拆分），
  作为单个 XML 不 well-formed，不符合"可独立校验的完整实例"要求。
- easybill/e-invoice-validator `src/test/resources/Invalid/` 的样例缺陷多为
  profile 未知、BOM/序言字符、废弃币种（HRK）等基础设施层问题，而非缺节点/金额不一致类业务规则。
