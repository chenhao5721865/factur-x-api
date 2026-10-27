# 站内文案改现在时 + 接收方钩子 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地页（validateur.html）文案从"义务将至（2026）"改为现在时（2026-09 已生效），并在 hero/meta 加"开票或收票"双视角钩子。

**Architecture:** 纯静态 Thymeleaf 模板文案改动，一个模板文件 + 一个现有测试加断言。无逻辑、无 schema、无 API 变更。设计见 `docs/superpowers/specs/2026-10-26-present-tense-copy-design.md`。

**Tech Stack:** Spring Boot 3 / Thymeleaf / JUnit 5 MockMvc（H2 测试 profile）。

**环境备忘（重要）：**
- JDK 17：`JAVA_HOME` 必须显式指向 `C:\Program Files\Java\jdk-17`，否则 surefire fork 出 Java 8 报 class file 52.0
- Maven 3.9.16：`C:\MyWork\tools\apache-maven-3.9.16\bin\mvn`（系统 mvn 3.6.1 不可用）
- Thymeleaf 把撇号转义为 `&#39;`，测试断言必须用**不含撇号**的文案片段

---

### Task 1: 先写失败断言（TDD）

**Files:**
- Modify: `web/src/test/java/com/facturxapi/web/ValidateurControllerTest.java:50-56`（`page_200_french_with_keyword` 方法）

- [ ] **Step 1: 在现有测试里加新文案断言**

把方法改为（新增两行断言，其余不动）：

```java
    @Test
    void page_200_french_with_keyword() throws Exception {
        mvc.perform(get("/validateur-factur-x"))
                .andExpect(status().isOk())
                .andExpect(view().name("validateur"))
                .andExpect(content().string(containsString("lang=\"fr\"")))
                .andExpect(content().string(containsString("Valider un fichier Factur-X en ligne")))
                // 文案已改现在时：法国接收义务 2026-09 已生效（勿再断言旧句 "devient obligatoire"）
                .andExpect(content().string(containsString("obligatoire en France depuis septembre 2026")))
                .andExpect(content().string(containsString("émises ou reçues")));
    }
```

- [ ] **Step 2: 跑测试确认失败**

```bash
cd "/c/Project Exploration/03-api-service" && JAVA_HOME="C:\Program Files\Java\jdk-17" /c/MyWork/tools/apache-maven-3.9.16/bin/mvn -pl web test -Dtest=ValidateurControllerTest
```

预期：`page_200_french_with_keyword` **FAIL**（模板里还没有新文案），其余测试 PASS。

---

### Task 2: 改模板文案

**Files:**
- Modify: `web/src/main/resources/templates/validateur.html`（第 7、26、93-94 行）

- [ ] **Step 1: meta description（第 7 行）**

旧：
```html
  <meta name="description" content="Vérifiez gratuitement la conformité de votre facture électronique Factur-X (PDF ou XML) à la norme EN 16931. Résultat immédiat, fichier supprimé après analyse.">
```
新：
```html
  <meta name="description" content="Vérifiez gratuitement la conformité de vos factures électroniques, émises ou reçues (PDF Factur-X ou XML) à la norme EN 16931. Résultat immédiat, fichier supprimé après analyse.">
```

- [ ] **Step 2: hero 副标题（第 26 行）**

旧：
```html
      Téléversez votre facture électronique (PDF Factur-X ou XML CII / EN 16931) et vérifiez
      instantanément sa conformité à la norme européenne.
```
新：
```html
      Téléversez votre facture électronique — émise ou reçue — (PDF Factur-X ou XML CII / EN 16931)
      et vérifiez instantanément sa conformité à la norme européenne.
```

- [ ] **Step 3: footer（第 93-94 行）**

旧：
```html
      Factur-X API — validation EN 16931 / Factur-X. La norme de facturation électronique devient
      obligatoire en France en 2026. Préparez vos flux dès maintenant.
```
新：
```html
      Factur-X API — validation EN 16931 / Factur-X. La facturation électronique est obligatoire
      en France depuis septembre 2026. Vérifiez dès aujourd'hui la conformité de vos factures,
      émises ou reçues.
```

- [ ] **Step 4: 跑全部 web 测试确认绿**

```bash
cd "/c/Project Exploration/03-api-service" && JAVA_HOME="C:\Program Files\Java\jdk-17" /c/MyWork/tools/apache-maven-3.9.16/bin/mvn -pl web test
```

预期：BUILD SUCCESS，全绿（含 Task 1 的断言转 PASS）。

- [ ] **Step 5: 提交**

```bash
cd "/c/Project Exploration/03-api-service" && git add web/src/main/resources/templates/validateur.html web/src/test/java/com/facturxapi/web/ValidateurControllerTest.java && git commit -m "feat(web): present-tense e-invoicing copy (FR obligation effective since 2026-09) + receiver hook"
```

---

### Task 3: 部署 + 线上冒烟

**Files:** 无新改动（构建产物部署）。

- [ ] **Step 1: 打包**

```bash
cd "/c/Project Exploration/03-api-service" && JAVA_HOME="C:\Program Files\Java\jdk-17" /c/MyWork/tools/apache-maven-3.9.16/bin/mvn -pl web -am package -DskipTests
```

⚠️ 必须先 `mvn install` 过父 POM；若报找不到 validator-core，先跑根目录 `mvn install -DskipTests`。

- [ ] **Step 2: scp 到服务器（路径坑：web JAR 必须放 web/target/）**

```bash
scp "/c/Project Exploration/03-api-service/web/target/web-*.jar" root@116.203.186.254:/root/factur-x-api/web/target/
```

⚠️ 74MB 偶发 Connection reset，重试即可。

- [ ] **Step 3: 重建 web 容器（服务名叫 app，不叫 web）**

```bash
ssh root@116.203.186.254 "cd /root/factur-x-api && docker compose -f docker-compose.prod.yml up -d --build app"
```

- [ ] **Step 4: 线上冒烟**

```bash
curl -s https://factur-x-api.com/validateur-factur-x | grep -o "obligatoire en France depuis septembre 2026"
curl -s -o /dev/null -w "%{http_code}" https://factur-x-api.com/validateur-factur-x
```

预期：第一条输出新 footer 文案片段，第二条 `200`。

- [ ] **Step 5: Search Console 重新请求编入索引**

人工操作：Search Console → 网址检查 → `https://factur-x-api.com/validateur-factur-x` → "请求编入索引"。（footer 是页面可见文本，会进索引）

---

## Self-Review 记录

- **Spec 覆盖**：spec 三处改动（meta/hero/footer）↔ Task 2 Step 1-3；测试 ↔ Task 1；部署+Search Console ↔ Task 3。无缺口。
- **占位符**：无 TBD/TODO；所有步骤含完整代码或命令。
- **一致性**：断言片段 `obligatoire en France depuis septembre 2026` / `émises ou reçues` 与 Task 2 模板文案逐字一致，且不含撇号（避开 Thymeleaf `&#39;` 转义）。
