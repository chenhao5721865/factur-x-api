# Backlog

## 产品/质量
- [ ] **支持 EXTENDED-CTC-FR 法国 CTC schematron 档案**（2026-10-14，Reddit r/developpeurs 用户 u/Fun_Ad_2011 点名需求——正在接 PDP 的开发者，高信号）：只过 EN 16931 对法国改革不够，需加法国专规（BR-FR-05 票据备注、BR-FR-12 买家电子地址 BT-49/EM 方案、BR-FR-CO-09 已付款框架）+ Peppol 约束（R002 单备注、R008 禁空元素）。规则来源待他回复确认（官方为 FNFE-MPE 发布）。**下一步（Tom Han 10-20 提醒）**：直接问他"什么才值得集成进你的工作流"——动机比技术问答更有信号。可做成分档：验证报告区分 "EN 16931" 与 "Réforme française (CTC)" 两节，差异化卖点。
- [ ] **调研竞品 SUPER PDP**（2026-10-14，同上评论）：免认证 API，`POST /v1.beta/validation_reports`（Factur-X/CII/UBL 自动识别+最新 schematron）与 `POST /v1.beta/invoices/convert`（JSON EN16931 → CII/UBL/Factur-X，multipart JSON+PDF → PDF/A-3），按用量计费。与我们 /generate 直接重叠，研究其定价/规则覆盖/差异点。
- [ ] **API 错误信息 i18n**（2026-10-08 dogfood 发现）：`/api/validate` 的错误信息是中文（如 "PDF 不含内嵌附件"），面向法语/英语开发者不合适。至少统一英文，或按 Accept-Language 切换。
- [ ] **真实 Factur-X 样例 dogfood**（2026-10-08）：Hetzner 真实发票是纯 PDF（无内嵌 XML），422 拒绝正确。下一步用 mustangproject/ZUGFeRD 官方公开样例测正向路径，检验规则覆盖对真实发票的命中率。
- [ ] **批量验证 endpoint**（2026-10-20，IH launch 帖评论 Luca 点名需求——EU 合规 API 同行，场景=会计软件月度对账）：`POST /api/validate/batch`，一次提交多张发票 → 每张 pass/fail + rule ID 汇总。**配额语义需先想清**（按发票数还是请求数扣——free 5/月则批量 10 张一张就爆；rule ID 返回可与增长/SEO 的规则解释页闭环）。
- [ ] **/generate category 必填化**（2026-10-20，IH 评论 fvazquez8——全帖最硬核技术评论）：税类别（S/Z/E/AE/K/G）决定下游必填字段——E 要免税理由（BR-E-10）、AE 要双方 VAT 号（BR-AE-02）。现状：0% 配错类别只在自校验步晚失败（500，报错不友好）。改为：category 必填，0% 无类别直接 400 + 规则 ID + 字段名。
- [ ] **按类别 fixtures：S/Z/E/AE/K/G 各一过一挂**（2026-10-20，同上评论）：一石三鸟——文档样例（开发者 day-one 可粘贴进验证器）+ 回归测试 + 与 golden 扩充项合并。另记：XRechnung BR-DE-15 买家参考号（公共买家=Leitweg-ID）是德国侧最常见首败点，补进德语页内容规划。
- [ ] golden 样例扩充至 10+（validator-core 健壮性）
- [ ] validator-core catch 坑（见 m2-validateur-limitations.md）
- [ ] CDN 本地化（Tailwind/htmx 目前走 CDN）
- [ ] warnings 恒空问题

## 增长/SEO
- [ ] **Rule ID programmatic SEO 页面**（2026-10-20，IH launch 帖评论点子，被评"最值钱"）：验证器输出的 BR-CO-10 等规则 ID 就是开发者下一步要 Google 的精确字符串，而站上目前无任何匹配页面（4 个页面全打 "validateur factur-x"）。做法：每条常见规则一页——rule ID 放标题、通俗解释+常见原因+修复后的 XML 片段，结尾一行指向 API（"Generate it right the first time"）。种子=按 api_usage 日志失败最多的 ~20 条规则；RuleMessages 已有 ~30 条法语映射可复用，模板/样式现成，落地成本低。**英文优先**（搜 rule ID 的是国际开发者）。
- [ ] **德语 ZUGFeRD/XRechnung 页面**（2026-10-20，同上评论）：德国 2027-01 强制生效，规则相同、换一批搜索人群，早做早吃 SEO 沙箱期。2027-01 前上线即可。
- [ ] **VIES 生成前 VAT 校验 + 接触 Luca**（2026-10-20，IH 评论）：同行做相邻 API（VIES VAT 验证/IBAN/意大利税号，不与 Factur-X 重叠），主动邀约集成，其 wrapper 区分 "VIES unavailable" vs "invalid"（VIES 常宕机）。合作/交叉推广候选，可 DM。另他提醒：RapidAPI 等 marketplace 版本升级后 endpoint 可能不在公开 Hub 显示（他踩过，两周无流量）——上 marketplace 时记得检查。**补充（10-20 晚）**：他的 VAT API 返回注册公司名+地址，可接入 /generate 做 BT-48→买家地址自动回填（必填字段易错点）；IH 私信对他不可用，公开回复留邮箱 hao [at] factur-x-api.com。
- [ ] **站内文案改"现在时"**（2026-10-20，IH 评论 ska1296）：法国**接收**义务 2026-09 已生效，买家痛点是现在时，而站内文案仍是"义务将至"（footer "devient obligatoire en 2026" 等）。钩子改为"检查你收件箱里的发票现在是否合规"，围绕"接收方"重写。
- [ ] **Postman collection + 15 分钟 quickstart**（2026-10-20，IH 评论 Haseeb Shahid）：转化点是"到第一张合规发票的时间"而非定价页。加 Postman collection + 真实 fixtures + "粘贴进验证器→再进 /generate" 的 15 分钟路径，挂 /docs。SDK 等有人问两次再做。

## 商业化
- [x] ~~LemonSqueezy 产品上架（配合 /generate 做付费墙）~~（2026-10-09 已完成并全链路冒烟通过）
- [x] ~~Search Console 重新请求编入索引~~（2026-10-09 已完成，4 个 URL 均成功）
- [ ] **webhook ls_subscription_id 数据瑕疵**（2026-10-09 付费墙闭环发现）：`subscription_payment_success` 事件的 `data.id` 是订阅发票（subscription-invoice）id，不是订阅 id——当前实现把它写进 `ls_subscription_id` 覆盖了 created 事件存的订阅 id（如 2593398 被覆盖为 8677274）。功能无碍（归属/plan 正确），但语义不纯。修法：payment_* 事件取 `data.attributes.subscription_id`。
- [ ] **api_usage 无界增长**：追加式日志表按月累积行数，无清理任务。流量上来后加归档/聚合（如每月汇总行 + 删明细）。
- [ ] 丢 key 无法自助找回（409 不回显是防盗设计；当前只能 SSH 重发）。有用户量后考虑邮箱找回流程。

