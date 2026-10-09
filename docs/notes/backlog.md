# Backlog

## 产品/质量
- [ ] **API 错误信息 i18n**（2026-10-08 dogfood 发现）：`/api/validate` 的错误信息是中文（如 "PDF 不含内嵌附件"），面向法语/英语开发者不合适。至少统一英文，或按 Accept-Language 切换。
- [ ] **真实 Factur-X 样例 dogfood**（2026-10-08）：Hetzner 真实发票是纯 PDF（无内嵌 XML），422 拒绝正确。下一步用 mustangproject/ZUGFeRD 官方公开样例测正向路径，检验规则覆盖对真实发票的命中率。
- [ ] golden 样例扩充至 10+（validator-core 健壮性）
- [ ] validator-core catch 坑（见 m2-validateur-limitations.md）
- [ ] CDN 本地化（Tailwind/htmx 目前走 CDN）
- [ ] warnings 恒空问题

## 商业化
- [ ] LemonSqueezy 产品上架（配合 /generate 做付费墙）
- [ ] Search Console 重新请求编入索引（此前因网络抖动失败）
- [ ] **webhook ls_subscription_id 数据瑕疵**（2026-10-09 付费墙闭环发现）：`subscription_payment_success` 事件的 `data.id` 是订阅发票（subscription-invoice）id，不是订阅 id——当前实现把它写进 `ls_subscription_id` 覆盖了 created 事件存的订阅 id（如 2593398 被覆盖为 8677274）。功能无碍（归属/plan 正确），但语义不纯。修法：payment_* 事件取 `data.attributes.subscription_id`。
- [ ] **api_usage 无界增长**：追加式日志表按月累积行数，无清理任务。流量上来后加归档/聚合（如每月汇总行 + 删明细）。
- [ ] 丢 key 无法自助找回（409 不回显是防盗设计；当前只能 SSH 重发）。有用户量后考虑邮箱找回流程。

