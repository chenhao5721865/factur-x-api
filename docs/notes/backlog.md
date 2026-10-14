# Backlog

## 产品/质量
- [ ] **支持 EXTENDED-CTC-FR 法国 CTC schematron 档案**（2026-10-14，Reddit r/developpeurs 用户 u/Fun_Ad_2011 点名需求——正在接 PDP 的开发者，高信号）：只过 EN 16931 对法国改革不够，需加法国专规（BR-FR-05 票据备注、BR-FR-12 买家电子地址 BT-49/EM 方案、BR-FR-CO-09 已付款框架）+ Peppol 约束（R002 单备注、R008 禁空元素）。规则来源待他回复确认（官方为 FNFE-MPE 发布）。可做成分档：验证报告区分 "EN 16931" 与 "Réforme française (CTC)" 两节，差异化卖点。
- [ ] **调研竞品 SUPER PDP**（2026-10-14，同上评论）：免认证 API，`POST /v1.beta/validation_reports`（Factur-X/CII/UBL 自动识别+最新 schematron）与 `POST /v1.beta/invoices/convert`（JSON EN16931 → CII/UBL/Factur-X，multipart JSON+PDF → PDF/A-3），按用量计费。与我们 /generate 直接重叠，研究其定价/规则覆盖/差异点。
- [ ] **API 错误信息 i18n**（2026-10-08 dogfood 发现）：`/api/validate` 的错误信息是中文（如 "PDF 不含内嵌附件"），面向法语/英语开发者不合适。至少统一英文，或按 Accept-Language 切换。
- [ ] **真实 Factur-X 样例 dogfood**（2026-10-08）：Hetzner 真实发票是纯 PDF（无内嵌 XML），422 拒绝正确。下一步用 mustangproject/ZUGFeRD 官方公开样例测正向路径，检验规则覆盖对真实发票的命中率。
- [ ] golden 样例扩充至 10+（validator-core 健壮性）
- [ ] validator-core catch 坑（见 m2-validateur-limitations.md）
- [ ] CDN 本地化（Tailwind/htmx 目前走 CDN）
- [ ] warnings 恒空问题

## 商业化
- [x] ~~LemonSqueezy 产品上架（配合 /generate 做付费墙）~~（2026-10-09 已完成并全链路冒烟通过）
- [x] ~~Search Console 重新请求编入索引~~（2026-10-09 已完成，4 个 URL 均成功）
- [ ] **webhook ls_subscription_id 数据瑕疵**（2026-10-09 付费墙闭环发现）：`subscription_payment_success` 事件的 `data.id` 是订阅发票（subscription-invoice）id，不是订阅 id——当前实现把它写进 `ls_subscription_id` 覆盖了 created 事件存的订阅 id（如 2593398 被覆盖为 8677274）。功能无碍（归属/plan 正确），但语义不纯。修法：payment_* 事件取 `data.attributes.subscription_id`。
- [ ] **api_usage 无界增长**：追加式日志表按月累积行数，无清理任务。流量上来后加归档/聚合（如每月汇总行 + 删明细）。
- [ ] 丢 key 无法自助找回（409 不回显是防盗设计；当前只能 SSH 重发）。有用户量后考虑邮箱找回流程。

