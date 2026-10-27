# 规则解释页（programmatic SEO，法语首批 10 页）— 设计

日期：2026-10-27
来源：IH launch 帖评论（Resultris，2026-10-20）：验证器输出的 rule ID（BR-CO-10 等）就是用户下一步 Google 的字符串，每规则一页可接住该搜索意图并导流 API。

## 目标与成功标准

- **目标**：用户在 Google 搜 rule ID（如 "BR-CO-10"）时落到我们的解释页，页面提供通俗解释 + 修复示例，并以双 CTA 导流免费验证器与 API。
- **成功标准**：上线 4-8 周内 Search Console 出现规则 ID 查询的展示/点击；页面 CTA 被点击（traffic.sh/Caddy 日志可见 /regles/ 来源流量）。不达标的页面不追加投入。
- **假设验证定位**：首批 10 页是最小验证批次，跑通（有展示→点击→CTA）后再扩量到 RuleMessages 全量 ~30 条。

## 已定决策（与用户逐项确认过）

1. **语言：法语先行**——现有流量/外链全在法语语境，规则 ID 搜索行为法英一致；英文版等假设验证后再议。
2. **首批 10 条**：BR-01、BR-02、BR-03、BR-05、BR-06、BR-07、BR-16、BR-CO-10、BR-CO-13、BR-CO-15（必填项 + 金额链高频规则）。
3. **架构：内容清单 + 单一动态模板**（方案 2），Java record 沿用 RuleMessages 模式，零新依赖。

## URL 与路由

- `GET /regles/br-co-10`——slug = rule ID 全小写（`BR-CO-10` → `br-co-10`，`BR-07` → `br-07`）。
- 新控制器 `RulePagesController`（web 模块）：`/regles/{slug}` 查 `RulePages` 清单 → 渲染 `regle.html`；未命中返回 404。

## 内容模型

```java
public record RulePageContent(
    String ruleId,        // "BR-CO-10"
    String title,         // H1/SEO 标题，含 rule ID，形如 "BR-CO-10 : la somme des lignes ne correspond pas au total HT — causes et correction"
    String summary,       // 一句话通俗解释（meta description 素材）
    String explanation,   // 2-3 段：规则管什么 / 何时触发 / 涉及 BT 字段
    String brokenXml,     // 违规 XML 最小片段
    String fixedXml,      // 修复后 XML 最小片段
    List<String> related  // 相关规则 ID（内链，须都在清单内）
)
```

- 常量类 `RulePages`（`web/src/main/java/com/facturxapi/web/RulePages.java`）：`List<RulePageContent>` + `get(slug)` + `all()`；模式照抄 `RuleMessages`。
- 内容为静态法语文案，由实现时起草、用户过目后定稿；XML 片段用 Java text block。

## 规则索引页（/regles/，内链枢纽）

- `GET /regles/`：动态列出全部规则页（H1 "Règles métier EN 16931 expliquées" + 每条一行：rule ID 链接 + title），页面底部同款双 CTA。
- 作用：SEO 内链枢纽 + 面包屑落点；扩页时自动跟进，零手工。
- 导航加链接：三页现有导航（header nav）加 "Règles" 入口（排在 Docs 前）。

## 页面模板（regle.html）

沿用 docs.html 的页面骨架（header/nav/footer 一致，Tailwind CDN）：

1. 面包屑（首页 → /regles/ → 本页）
2. H1（title，含 rule ID）
3. summary（强调块）
4. explanation（正文段落）
5. brokenXml / fixedXml 对照（两个 `<pre><code>` 块，红/绿边框区分）
6. 双 CTA："Vérifiez votre facture gratuitement" → `/validateur-factur-x`；"Générez des factures conformes via API" → `/docs`（次按钮 → `/tarifs`）
7. related 规则内链（锚文本 = rule ID + 短语）

**明确不做**（本批次）：英文版、页面内搜索、FAQ/Article schema 结构化数据、评论。

## 验证器结果页 linkify（闭环）

`fragments/resultat.html`：错误项与警告项的 ruleId 徽章，若该规则在 `RulePages` 清单中则渲染为 `<a href="/regles/{slug}">`，否则维持纯文本。现有测试 `containsString("BR-07")` 在链接化后仍命中（文本不变）。

## sitemap 动态化

- 删除静态 `web/src/main/resources/static/sitemap.xml`。
- 新 `SitemapController`：`GET /sitemap.xml`，`produces=application/xml`，内容 = 现有 4 个 URL（首页/验证器/tarifs/docs，优先级沿用现值）+ `/regles/` 索引页（0.8）+ 全部 `/regles/*` 规则页（0.7），从 `RulePages.all()` 动态拼接。
- robots.txt 不动（仍指向 /sitemap.xml）。

## 测试

- `RulePagesControllerTest`：已知 slug → 200 且含 ruleId 与 title；未知 slug → 404；`/regles/` 索引 → 200 且列出全部规则链接。
- `RulePages` 内容守恒测试：每条字段非空白；slug 唯一；slug == ruleId 小写；`related` 引用的 ruleId 都在清单内。
- `SitemapControllerTest`：响应 content-type 为 XML，包含 `/regles/`、全部规则 URL 与 4 个既有 URL。
- 现有 `ValidateurControllerTest`（含 `containsString("BR-07")`）保持全绿。

## 部署与后续

- 标准部署流程（mvn package → scp → `up -d --build app`），线上冒烟：任一规则页 200、未知 slug 404、sitemap.xml 含规则 URL。
- Search Console 重索引等有代理时一并做（sitemap 动态更新会被 Google 自然重抓，不阻塞）。
- 数据驱动扩量：上线后用 Search Console 查询报告决定第二批规则与是否做英文版。

## 风险

- 内容质量是 SEO 成败关键（薄内容页可能不被收录）——每页保证 explanation 2-3 段 + 修复示例的实质内容，杜绝模板化空页。
- `related` 内链引用错误 ID → 由守恒测试拦截。
