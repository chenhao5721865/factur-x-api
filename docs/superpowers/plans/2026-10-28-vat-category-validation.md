# /generate vatCategoryCode 一致性校验 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** /generate 的 vatRate↔vatCategoryCode 冲突从自校验 500 兜底改为前置 400 + 规则 ID，category 仅允许 S/Z，且 400 不扣配额。

**Architecture:** LineItemRequest 紧凑构造器加归一化（trim + toUpperCase）；GenerateController 在 401 之后、配额检查之前调用新私有静态方法 validateVatConsistency，违规抛 IllegalArgumentException 走 ApiExceptionHandler 现有 400 通道。

**Tech Stack:** Spring Boot 3 / Java 17 / JUnit 5 MockMvc（H2 profile）

**Spec:** docs/superpowers/specs/2026-10-28-vat-category-validation-design.md

**环境（Windows Git Bash，每个跑测试的步骤都适用）：**
```bash
cd "C:/Project Exploration/03-api-service"
export JAVA_HOME="C:\\Program Files\\Java\\jdk-17"
MVN=/c/MyWork/tools/apache-maven-3.9.16/bin/mvn
```

---

### Task 1: LineItemRequest 归一化 + 三个 400 路径测试（红灯）

**Files:**
- Modify: `api/src/main/java/com/facturxapi/api/dto/LineItemRequest.java`
- Test: `api/src/test/java/com/facturxapi/api/controller/GenerateControllerTest.java`

- [ ] **Step 1: 写失败测试（4 个新测试方法，追加到 GenerateControllerTest 类末尾 `}` 之前）**

```java
    @Test
    void zeroVatRate_withDefaultCategoryS_returns400_brS05() throws Exception {
        String json = """
            {"invoiceNumber": "BRS05-1", "issueDate": "2026-10-08", "currency": "EUR",
             "seller": {"name": "S", "countryCode": "FR", "vatId": "FR1"},
             "buyer": {"name": "B", "countryCode": "FR"},
             "lines": [{"description": "x", "quantity": 1, "unitPrice": 50, "vatRate": 0}]}
            """;
        mockMvc.perform(gen(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(
                "lines[0].vatRate: BR-S-05 — category S requires vatRate > 0. For 0% use vatCategoryCode: \"Z\""));
    }

    @Test
    void nonzeroVatRate_withCategoryZ_returns400_brZ05() throws Exception {
        String json = """
            {"invoiceNumber": "BRZ05-1", "issueDate": "2026-10-08", "currency": "EUR",
             "seller": {"name": "S", "countryCode": "FR", "vatId": "FR1"},
             "buyer": {"name": "B", "countryCode": "FR"},
             "lines": [{"description": "x", "quantity": 1, "unitPrice": 50, "vatRate": 20,
                        "vatCategoryCode": "Z"}]}
            """;
        mockMvc.perform(gen(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(
                "lines[0].vatRate: BR-Z-05 — category Z requires vatRate = 0"));
    }

    @Test
    void unsupportedCategory_returns400() throws Exception {
        String json = """
            {"invoiceNumber": "AE-1", "issueDate": "2026-10-08", "currency": "EUR",
             "seller": {"name": "S", "countryCode": "FR", "vatId": "FR1"},
             "buyer": {"name": "B", "countryCode": "FR"},
             "lines": [{"description": "x", "quantity": 1, "unitPrice": 50, "vatRate": 0,
                        "vatCategoryCode": "AE"}]}
            """;
        mockMvc.perform(gen(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(
                "lines[0].vatCategoryCode: unsupported value 'AE'. Supported: S, Z (others require VATEX reason codes, not yet supported)"));
    }

    @Test
    void secondLineViolation_reportsLineIndex() throws Exception {
        String json = """
            {"invoiceNumber": "IDX-1", "issueDate": "2026-10-08", "currency": "EUR",
             "seller": {"name": "S", "countryCode": "FR", "vatId": "FR1"},
             "buyer": {"name": "B", "countryCode": "FR"},
             "lines": [
               {"description": "a", "quantity": 1, "unitPrice": 100, "vatRate": 20},
               {"description": "b", "quantity": 1, "unitPrice": 50, "vatRate": 0}
             ]}
            """;
        mockMvc.perform(gen(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(
                "lines[1].vatRate: BR-S-05 — category S requires vatRate > 0. For 0% use vatCategoryCode: \"Z\""));
    }
```

- [ ] **Step 2: 跑测试确认红灯**

```bash
$MVN -pl api test -Dtest=GenerateControllerTest
```

预期：4 个新测试 FAIL（0% + 默认 S 现在是 500 自校验兜底；Z+20% 现在是 500 或 200 取决于自校验；AE 是 500）。注意现有测试 `zeroVatRate_withCategoryZ_groupEmitted_andValid`（0%+Z → 200）必须保持绿。

- [ ] **Step 3: 归一化 LineItemRequest 紧凑构造器**

改 `api/src/main/java/com/facturxapi/api/dto/LineItemRequest.java` 紧凑构造器（第 20-28 行）为：

```java
    public LineItemRequest {
        if (unitCode == null || unitCode.isBlank()) {
            unitCode = "C62";
        }
        if (vatCategoryCode == null || vatCategoryCode.isBlank()) {
            vatCategoryCode = "S";
        } else {
            // 归一化：生成器直接输出该值到 CategoryCode，EN 16931 码表为大写
            vatCategoryCode = vatCategoryCode.trim().toUpperCase(java.util.Locale.ROOT);
        }
    }
```

文件头部 import 区加 `import java.util.Locale;` 并把上面 `java.util.Locale.ROOT` 改为 `Locale.ROOT`（二选一，推荐加 import）。

- [ ] **Step 4: GenerateController 加前置校验**

改 `api/src/main/java/com/facturxapi/api/controller/GenerateController.java`：

在配额检查（第 66-67 行注释与 `quotaService.checkAndIncrement` 调用）**之前**插入调用：

```java
        // 一致性前置校验：在配额检查之前，客户端输入错误不扣额
        validateVatConsistency(request);

```

（位置：第 64 行 `}` 之后、第 66 行注释之前。）

类末尾（`sanitizeFilename` 方法之后、类的 closing `}` 之前）加：

```java
    /**
     * vatRate 与 vatCategoryCode 一致性校验（BR-S-05 / BR-Z-05），命中即抛 400。
     * category 仅支持 S/Z——其余类别需 VATEX 原因码（BT-120/121），生成器不支持，
     * 放行必然自校验 500，故前置拒绝。只报第一处违规。
     */
    private static void validateVatConsistency(InvoiceRequest request) {
        var lines = request.lines();
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            String cat = line.vatCategoryCode(); // 已经紧凑构造器归一化
            boolean zeroRate = line.vatRate().compareTo(java.math.BigDecimal.ZERO) == 0;
            if (!"S".equals(cat) && !"Z".equals(cat)) {
                throw new IllegalArgumentException(
                    "lines[" + i + "].vatCategoryCode: unsupported value '" + cat
                    + "'. Supported: S, Z (others require VATEX reason codes, not yet supported)");
            }
            if ("S".equals(cat) && zeroRate) {
                throw new IllegalArgumentException(
                    "lines[" + i + "].vatRate: BR-S-05 — category S requires vatRate > 0. "
                    + "For 0% use vatCategoryCode: \"Z\"");
            }
            if ("Z".equals(cat) && !zeroRate) {
                throw new IllegalArgumentException(
                    "lines[" + i + "].vatRate: BR-Z-05 — category Z requires vatRate = 0");
            }
        }
    }
```

（`java.math.BigDecimal` 可改为文件头 import；GenerateController 当前无 BigDecimal import。）

- [ ] **Step 5: 跑测试确认全绿**

```bash
$MVN -pl api test -Dtest=GenerateControllerTest
```

预期：GenerateControllerTest 全绿（含 4 个新测试 + 所有既有测试）。

- [ ] **Step 6: 跑 api 模块全量测试**

```bash
$MVN -pl api test
```

预期：api 模块全部测试绿。

- [ ] **Step 7: Commit**

```bash
git add api/src/main/java/com/facturxapi/api/dto/LineItemRequest.java \
        api/src/main/java/com/facturxapi/api/controller/GenerateController.java \
        api/src/test/java/com/facturxapi/api/controller/GenerateControllerTest.java
git commit -m "feat(api): vatRate/category consistency check returns 400 with rule ID"
```

---

### Task 2: 配额语义 + 小写归一化端到端测试

**Files:**
- Test: `api/src/test/java/com/facturxapi/api/controller/GenerateControllerTest.java`

- [ ] **Step 1: 写失败测试（2 个新测试方法，追加到 GenerateControllerTest 类末尾 `}` 之前）**

```java
    @Test
    void vatConsistencyError_beforeQuotaCheck_doesNotConsumeQuota() throws Exception {
        String badJson = """
            {"invoiceNumber": "Q-1", "issueDate": "2026-10-08", "currency": "EUR",
             "seller": {"name": "S", "countryCode": "FR", "vatId": "FR1"},
             "buyer": {"name": "B", "countryCode": "FR"},
             "lines": [{"description": "x", "quantity": 1, "unitPrice": 50, "vatRate": 0}]}
            """;
        mockMvc.perform(gen(badJson)).andExpect(status().isBadRequest());
        // 400 不扣额：随后成功调用的 remaining 应为 4（与 validationError_beforeQuotaCheck 同语义）
        mockMvc.perform(gen(VALID_JSON))
            .andExpect(status().isOk())
            .andExpect(header().string("X-RateLimit-Remaining", "4"));
    }

    @Test
    void lowercaseCategoryZ_isNormalized_andValid() throws Exception {
        String json = """
            {"invoiceNumber": "LC-1", "issueDate": "2026-10-08", "currency": "EUR",
             "seller": {"name": "S", "countryCode": "FR", "vatId": "FR1"},
             "buyer": {"name": "B", "countryCode": "FR"},
             "lines": [{"description": "x", "quantity": 1, "unitPrice": 50, "vatRate": 0,
                        "vatCategoryCode": "z"}]}
            """;
        byte[] xml = generateValidXml(json);
        String text = new String(xml, StandardCharsets.UTF_8);

        assertThat(text).contains("<ram:CategoryCode>Z</ram:CategoryCode>");
        assertThat(validator.validate(xml).valid()).isTrue();
    }
```

- [ ] **Step 2: 跑测试**

```bash
$MVN -pl api test -Dtest=GenerateControllerTest
```

预期：全绿（Task 1 的实现已覆盖这两个测试——Task 1 Step 4 的校验在配额之前、Step 3 的归一化让小写 z 生效。若红，检查 Task 1 实现是否完全落实）。

- [ ] **Step 3: Commit**

```bash
git add api/src/test/java/com/facturxapi/api/controller/GenerateControllerTest.java
git commit -m "test(api): vat consistency 400 does not consume quota; lowercase category normalized"
```

---

### Task 3: /docs 文档同步

**Files:**
- Modify: `web/src/main/resources/templates/docs.html:138,141-145`

- [ ] **Step 1: 改字段表 vatCategoryCode 行（docs.html 第 138 行）**

old:

```html
            <tr><td class="px-3 py-2 font-mono">vatCategoryCode</td><td class="px-3 py-2">non</td><td class="px-3 py-2">Catégorie TVA, défaut <code>S</code>. Taux 0 % → <code>Z</code> (exonéré) (BT-151)</td></tr>
```

new:

```html
            <tr><td class="px-3 py-2 font-mono">vatCategoryCode</td><td class="px-3 py-2">non</td><td class="px-3 py-2">Catégorie TVA, défaut <code>S</code>. Seules <code>S</code> et <code>Z</code> acceptées ; taux 0 % → <code>Z</code> obligatoire (BT-151)</td></tr>
```

- [ ] **Step 2: 改 ⚠️ 段落（docs.html 第 141-145 行）**

old:

```html
        <p class="text-xs text-slate-500 mt-1">
          Les montants totaux (HT, TVA, TTC) sont calculés côté serveur : il n'y a rien à fournir,
          et aucune incohérence n'est possible. ⚠️ Une ligne <code>vatRate: 0</code> avec la catégorie
          par défaut <code>S</code> viole la règle BR-S-05 : utilisez <code>vatCategoryCode: "Z"</code>.
        </p>
```

new:

```html
        <p class="text-xs text-slate-500 mt-1">
          Les montants totaux (HT, TVA, TTC) sont calculés côté serveur : il n'y a rien à fournir,
          et aucune incohérence n'est possible. ⚠️ Une ligne <code>vatRate: 0</code> avec la catégorie
          par défaut <code>S</code> est rejetée (<code>400</code>, règle BR-S-05) : utilisez
          <code>vatCategoryCode: "Z"</code>. Toute autre catégorie que <code>S</code>/<code>Z</code>
          est également rejetée (<code>400</code>).
        </p>
```

- [ ] **Step 3: 跑 web 模块测试确认无回归**

```bash
$MVN -pl web test
```

预期：web 模块全绿（docs 页无现有断言覆盖这两段文案，纯预防）。

- [ ] **Step 4: Commit**

```bash
git add web/src/main/resources/templates/docs.html
git commit -m "docs(web): /docs reflects vatCategoryCode 400 validation (S/Z only)"
```

---

### Task 4: 全量验证 + 部署 + 线上冒烟

**Files:** 无代码改动。

- [ ] **Step 1: 全量测试 + 打包**

```bash
$MVN clean package
```

预期：全模块 BUILD SUCCESS，27+ 测试绿（api 新增 6 个）。

- [ ] **Step 2: 部署 api + web（docs.html 在 web 模块）**

```bash
scp api/target/api-*.jar root@116.203.186.254:/root/factur-x-api/api/target/
scp web/target/web-*.jar root@116.203.186.254:/root/factur-x-api/web/target/
ssh root@116.203.186.254 "cd /root/factur-x-api && docker compose -f docker-compose.prod.yml up -d --build api app"
```

注意：web 服务名是 `app`；api 是 `api`。JAR 必须放对应 `模块/target/` 子目录（Dockerfile COPY 相对路径）。

- [ ] **Step 3: 线上冒烟**

先领一个冒烟 key（或复用已有 free key）。写 JSON 到临时文件再 `--data-binary`（Git Bash 内联非 ASCII 会编码错乱）：

```bash
# 冒烟 1：0% 无 category → 400 含 BR-S-05
cat > /tmp/smoke-brs05.json <<'EOF'
{"invoiceNumber":"SMK-1","issueDate":"2026-10-28","currency":"EUR",
 "seller":{"name":"S","countryCode":"FR","vatId":"FR12345678901"},
 "buyer":{"name":"B","countryCode":"FR"},
 "lines":[{"description":"x","quantity":1,"unitPrice":50,"vatRate":0}]}
EOF
curl -s -o /tmp/smoke1.out -w "%{http_code}\n" -X POST https://factur-x-api.com/api/generate \
  -H "X-API-Key: $SMOKE_KEY" -H "Content-Type: application/json" --data-binary @/tmp/smoke-brs05.json
grep -o "BR-S-05" /tmp/smoke1.out
# 预期：400 + 输出 BR-S-05

# 冒烟 2：0% + Z → 200 XML
sed 's/"vatRate":0}/"vatRate":0,"vatCategoryCode":"Z"}/' /tmp/smoke-brs05.json > /tmp/smoke-z.json
curl -s -o /tmp/smoke2.xml -w "%{http_code}\n" -X POST https://factur-x-api.com/api/generate \
  -H "X-API-Key: $SMOKE_KEY" -H "Content-Type: application/json" --data-binary @/tmp/smoke-z.json
grep -o "<ram:CategoryCode>Z</ram:CategoryCode>" /tmp/smoke2.xml
# 预期：200 + CategoryCode Z

# 冒烟 3：20% 无 category → 200（存量兼容回归）
sed 's/"vatRate":0}/"vatRate":20}/' /tmp/smoke-brs05.json > /tmp/smoke-s.json
curl -s -o /dev/null -w "%{http_code}\n" -X POST https://factur-x-api.com/api/generate \
  -H "X-API-Key: $SMOKE_KEY" -H "Content-Type: application/json" --data-binary @/tmp/smoke-s.json
# 预期：200

# 冒烟 4：/docs 页含新文案
curl -s https://factur-x-api.com/docs | grep -o "obligatoire (BT-151)"
# 预期：有输出
```

- [ ] **Step 4: 冒烟 key revoke + 清理**

内网 revoke 冒烟 key（如本次新建的话；把 `REPLACE_WITH_SMOKE_KEY` 换成实际 token）：

```bash
ssh root@116.203.186.254 "docker compose -f /root/factur-x-api/docker-compose.prod.yml exec db psql -U \$POSTGRES_USER -c \"UPDATE api_keys SET revoked=true WHERE token='REPLACE_WITH_SMOKE_KEY'\""
```

（postgres 镜像容器内 `$POSTGRES_USER` 恒存在；不带 `-d` 时 psql 默认连同名库。若连错库，补 `-d \$POSTGRES_DB`。）
