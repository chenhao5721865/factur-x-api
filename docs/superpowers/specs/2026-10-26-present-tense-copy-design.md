# 站内文案改现在时 + 接收方钩子 — 设计

日期：2026-10-26
来源：IH launch 帖评论（ska1296，2026-10-20）：法国电子发票**接收**义务已于 2026-09 生效，站内"义务将至（devient obligatoire en 2026）"文案过时；痛点应改为现在时、覆盖接收方。

## 背景与目标

- 法国 B2B 电子发票接收义务 2026-09 已生效，买家当下真实痛点是"检查收到的发票是否合规"。
- 全站唯一遗留的未来时文案在落地页 footer（首页 `/` 302 到 `/validateur-factur-x`，验证器页即落地页）。
- 目标：① 文案改现在时；② hero 与 meta 加"开票或收票"双视角钩子，接住接收方搜索意图；③ 不破坏现有"验证我开的发票"定位与已有 SEO。

## 改动内容

**只动一个文件**：`web/src/main/resources/templates/validateur.html`

### 1. Footer（93-94 行）— 核心修正

旧：
> Factur-X API — validation EN 16931 / Factur-X. La norme de facturation électronique devient obligatoire en France en 2026. Préparez vos flux dès maintenant.

新：
> Factur-X API — validation EN 16931 / Factur-X. La facturation électronique est obligatoire en France depuis septembre 2026. Vérifiez dès aujourd'hui la conformité de vos factures, émises ou reçues.

### 2. Hero 副标题（26 行）— 接收方钩子

旧：
> Téléversez votre facture électronique (PDF Factur-X ou XML CII / EN 16931) et vérifiez instantanément sa conformité à la norme européenne.

新：
> Téléversez votre facture électronique — émise ou reçue — (PDF Factur-X ou XML CII / EN 16931) et vérifiez instantanément sa conformité à la norme européenne.

### 3. Meta description（7 行）— SEO 同步

旧：
> Vérifiez gratuitement la conformité de votre facture électronique Factur-X (PDF ou XML) à la norme EN 16931. Résultat immédiat, fichier supprimé après analyse.

新：
> Vérifiez gratuitement la conformité de vos factures électroniques, émises ou reçues (PDF Factur-X ou XML) à la norme EN 16931. Résultat immédiat, fichier supprimé après analyse.

## 明确不动的

- H1（既有 SEO 排名对应此句，不动）
- tarifs.html / docs.html / fragments/resultat.html（无未来时问题，且 tarifs/docs footer 为 `© 2026` 正常版权行）
- validateur.html:71 "profils ZUGFeRD / XRechnung (Allemagne 2027)"（德国 2027 强制，事实正确）

## 测试与部署

- 现有测试不依赖上述文案（已 grep 验证），`mvn -pl web test` 应保持全绿。
- 本地冒烟：渲染验证器页确认新文案。
- 部署走标准流程：`mvn package` → scp JAR 到服务器 `web/target/` → `docker compose -f docker-compose.prod.yml up -d --build app`（web 服务名是 `app`）→ 线上冒烟（首页 302、footer 新文案）。
- 部署后 Search Console 对 `/validateur-factur-x` 重新"请求编入索引"（footer 是页面可见文本，会进索引）。

## 风险

极低。纯静态文案改动，无逻辑、无 schema、无 API 变更。
