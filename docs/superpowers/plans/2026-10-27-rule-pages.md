# 规则解释页（/regles/*，法语 10 页 + 索引页 + sitemap 动态化）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 上线 10 个法语 rule-ID 解释页（/regles/br-co-10 等）+ 索引页 /regles/，验证器结果页 ruleId 徽章 linkify 闭环，sitemap 改动态生成。

**Architecture:** 内容为 Java record 常量清单（`RulePages`，照抄 `RuleMessages` 模式，零新依赖）；一个控制器渲染唯一模板 `regle.html` + 索引模板 `regles.html`；`SitemapController` 动态输出替换静态 sitemap.xml。spec 见 `docs/superpowers/specs/2026-10-27-rule-pages-design.md`。

**Tech Stack:** Spring Boot 3 / Thymeleaf / JUnit 5 MockMvc（H2 测试 profile）。无 Spring Security（所有路由默认公开；模板里 `_csrf?.` 是 null-safe 写法）。

**环境备忘（重要）：**
- Maven：`/c/MyWork/tools/apache-maven-3.9.16/bin/mvn`；每条 mvn 命令前缀 `JAVA_HOME="C:\Program Files\Java\jdk-17"`
- 测试模板页断言用 `content().string(containsString(...))`（勿用 xpath()——HTML 含 `<meta>` 空元素会炸）
- Thymeleaf 把撇号转义 `&#39;`，断言片段不得含撇号
- Thymeleaf(SpEL) 支持直接访问 record 组件（`${c.title}` 调 `title()`）；万一模板解析报错，退路是把 record 换成带 getter 的小 DTO——测试会立刻暴露

**文件结构总览：**
- Create: `web/src/main/java/com/facturxapi/web/RulePageContent.java`（record）
- Create: `web/src/main/java/com/facturxapi/web/RulePages.java`（10 条内容 + 查询方法）
- Create: `web/src/main/java/com/facturxapi/web/RulePagesController.java`（/regles/ + /regles/{slug}）
- Create: `web/src/main/java/com/facturxapi/web/SitemapController.java`（/sitemap.xml 动态）
- Create: `web/src/main/resources/templates/regle.html`、`templates/regles.html`
- Delete: `web/src/main/resources/static/sitemap.xml`
- Modify: `templates/fragments/resultat.html`（ruleId 徽章 linkify）、`templates/validateur.html`/`tarifs.html`/`docs.html`（nav 加 Règles）
- Test: `web/src/test/java/com/facturxapi/web/RulePagesTest.java`、`RulePagesControllerTest.java`、`SitemapControllerTest.java`；Modify `ValidateurControllerTest.java`

---

### Task 1: RulePageContent record + RulePages 内容清单（TDD）

**Files:**
- Create: `web/src/main/java/com/facturxapi/web/RulePageContent.java`
- Create: `web/src/main/java/com/facturxapi/web/RulePages.java`
- Test: `web/src/test/java/com/facturxapi/web/RulePagesTest.java`

- [ ] **Step 1: 写守恒测试（先红）**

```java
package com.facturxapi.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RulePages 内容守恒：防手滑写出半截页面或断链内链。 */
class RulePagesTest {

    @Test
    void everyEntryIsComplete() {
        assertThat(RulePages.all()).hasSize(10);
        for (RulePageContent c : RulePages.all()) {
            assertThat(c.ruleId())..isNotBlank();
            assertThat(c.title()).isNotBlank();
            assertThat(c.summary()).isNotBlank();
            assertThat(c.explanation()).isNotBlank();
            assertThat(c.brokenXml()).isNotBlank();
            assertThat(c.fixedXml()).isNotBlank();
        }
    }

    @Test
    void slugsAreUniqueAndLowercaseOfRuleId() {
        List<String> slugs = RulePages.all().stream().map(c -> RulePages.slugOf(c.ruleId())).toList();
        assertThat(slugs).doesNotHaveDuplicates();
        for (RulePageContent c : RulePages.all()) {
            assertThat(RulePages.slugOf(c.ruleId())).isEqualTo(c.ruleId().toLowerCase());
        }
    }

    @Test
    void relatedLinksStayInsideTheList() {
        for (RulePageContent c : RulePages.all()) {
            for (String rel : c.related()) {
                assertThat(RulePages.containsRule(rel))
                    .as("%s references unknown related rule %s", c.ruleId(), rel)
                    .isTrue();
            }
        }
    }

    @Test
    void getReturnsEntryBySlug() {
        assertThat(RulePages.get("br-co-10").ruleId()).isEqualTo("BR-CO-10");
        assertThat(RulePages.get("BR-CO-10").ruleId()).isEqualTo("BR-CO-10"); // 容忍大写
        assertThat(RulePages.get("br-99")).isNull();
    }
}
```

- [ ] **Step 2: 跑测试确认编译失败（类不存在即红）**

Run: `cd "/c/Project Exploration/03-api-service" && JAVA_HOME="C:\Program Files\Java\jdk-17" /c/MyWork/tools/apache-maven-3.9.16/bin/mvn -pl web test -Dtest=RulePagesTest`
Expected: 编译错误 `cannot find symbol: RulePages`。

- [ ] **Step 3: 实现 record + RulePages（10 条法语成稿）**

`RulePageContent.java`：

```java
package com.facturxapi.web;

import java.util.List;

/** 一条 EN 16931 业务规则的解释页内容（/regles/{slug}）。 */
public record RulePageContent(
        String ruleId,
        String title,
        String summary,
        String explanation,
        String brokenXml,
        String fixedXml,
        List<String> related) {
}
```

`RulePages.java`（内容成稿，实现时可微调措辞但结构/字段语义不变）：

```java
package com.facturxapi.web;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** ruleId → 解释页内容。首批 10 条：必填项 + 金额链高频规则。扩页 = 往 PAGES 加一条。 */
public final class RulePages {

    private static final List<RulePageContent> PAGES = List.of(
        new RulePageContent("BR-01",
            "BR-01 : spécification de facture électronique manquante (BT-24) — causes et correction",
            "La facture doit déclarer la norme qu'elle suit (BT-24). Sans cet identifiant, le fichier est rejeté dès la première vérification.",
            "BT-24 est l'identifiant de spécification : il indique la norme que la facture respecte, par exemple urn:cen.eu:en16931:2017 pour le profil EN 16931 (Factur-X). Les plateformes (PDP, portails publics) s'en servent pour choisir le bon moteur de validation.\n\n"
            + "La règle échoue quand ExchangedDocumentContext ne contient pas de GuidelineSpecifiedDocumentContextParameter, ou quand l'ID porte une valeur inconnue du référentiel. C'est fréquent dans les XML générés à la main ou par des outils qui confondent les profils (EN 16931, EXTENDED, XRechnung).",
            """
            <rsm:ExchangedDocumentContext>
              <!-- manque : GuidelineSpecifiedDocumentContextParameter (BT-24) -->
              <ram:BusinessProcessSpecifiedDocumentContextParameter>
                <ram:ID>A1</ram:ID>
              </ram:BusinessProcessSpecifiedDocumentContextParameter>
            </rsm:ExchangedDocumentContext>""",
            """
            <rsm:ExchangedDocumentContext>
              <ram:GuidelineSpecifiedDocumentContextParameter>
                <ram:ID>urn:cen.eu:en16931:2017</ram:ID>
              </ram:GuidelineSpecifiedDocumentContextParameter>
            </rsm:ExchangedDocumentContext>""",
            List.of("BR-02", "BR-05")),

        new RulePageContent("BR-02",
            "BR-02 : numéro de facture obligatoire (BT-1) — causes et correction",
            "Chaque facture doit porter un numéro unique (BT-1), dans l'élément ID du document.",
            "BT-1 identifie la facture de façon unique chez le vendeur ; c'est la référence que votre client utilisera pour le paiement et la comptabilité. En syntaxe CII, il s'agit de l'élément ram:ID directement sous rsm:ExchangedDocument.\n\n"
            + "L'erreur survient surtout dans les exports PDP mal paramétrés et les générateurs maison qui oublient l'élément, ou qui y mettent une valeur vide. Le numéro doit rester non vide : une chaîne d'espaces est rejetée aussi.",
            """
            <rsm:ExchangedDocument>
              <!-- manque : <ram:ID> (BT-1) -->
              <ram:IssueDateTime>2026-10-27</ram:IssueDateTime>
            </rsm:ExchangedDocument>""",
            """
            <rsm:ExchangedDocument>
              <ram:ID>FA-2026-0042</ram:ID>
              <ram:IssueDateTime>2026-10-27</ram:IssueDateTime>
            </rsm:ExchangedDocument>""",
            List.of("BR-03", "BR-07")),

        new RulePageContent("BR-03",
            "BR-03 : date d'émission obligatoire (BT-2) — causes et correction",
            "La facture doit porter sa date d'émission (BT-2), au format AAAA-MM-JJ.",
            "BT-2 est la date à laquelle la facture est émise ; elle déclenche les délais de paiement et les déclarations de TVA. En CII : ram:IssueDateTime sous rsm:ExchangedDocument, au format ISO AAAA-MM-JJ.\n\n"
            + "Deux causes typiques d'échec : l'élément absent, ou un format non ISO comme 27/10/2026. Le validateur XSD rejette ce dernier avant même les règles métier — si vous voyez BR-03, l'élément manque vraiment.",
            """
            <rsm:ExchangedDocument>
              <ram:ID>FA-2026-0042</ram:ID>
              <!-- manque : <ram:IssueDateTime> (BT-2) -->
            </rsm:ExchangedDocument>""",
            """
            <rsm:ExchangedDocument>
              <ram:ID>FA-2026-0042</ram:ID>
              <ram:IssueDateTime>2026-10-27</ram:IssueDateTime>
            </rsm:ExchangedDocument>""",
            List.of("BR-02", "BR-05")),

        new RulePageContent("BR-05",
            "BR-05 : devise de la facture obligatoire (BT-5) — causes et correction",
            "La facture doit déclarer sa devise (BT-5), par exemple EUR, en code ISO 4217.",
            "BT-5 donne la devise de tous les montants de la facture. Sans elle, aucun contrôle de cohérence n'est possible : le moteur ne sait pas si 1000 signifie mille euros ou mille dollars. En CII : ram:InvoiceCurrencyCode sous ApplicableHeaderTradeDocument.\n\n"
            + "L'échec vient d'un élément absent ou d'un code qui n'est pas dans la liste ISO 4217 (Euros au lieu de EUR, par exemple). Utilisez toujours le code alphabétique à trois lettres.",
            """
            <ram:ApplicableHeaderTradeDocument>
              <!-- manque : <ram:InvoiceCurrencyCode> (BT-5) -->
              <ram:SellerTradeParty>...</ram:SellerTradeParty>
            </ram:ApplicableHeaderTradeDocument>""",
            """
            <ram:ApplicableHeaderTradeDocument>
              <ram:InvoiceCurrencyCode>EUR</ram:InvoiceCurrencyCode>
              <ram:SellerTradeParty>...</ram:SellerTradeParty>
            </ram:ApplicableHeaderTradeDocument>""",
            List.of("BR-01", "BR-CO-15")),

        new RulePageContent("BR-06",
            "BR-06 : nom du vendeur obligatoire (BT-27) — causes et correction",
            "La facture doit contenir le nom légal du vendeur (BT-27).",
            "BT-27 est le nom légal de votre entreprise tel qu'il apparaît sur la facture. Les plateformes et l'administration fiscale l'utilisent pour identifier l'émetteur ; il est donc contrôlé même quand un SIREN/VAT number (BT-30/BT-31) est présent — les deux sont complémentaires.\n\n"
            + "L'échec typique : le bloc SellerTradeParty existe mais sans élément ram:Name, ou avec une chaîne vide. Ça arrive quand un outil copie un gabarit où le vendeur n'est renseigné que par son identifiant.",
            """
            <ram:SellerTradeParty>
              <ram:ID>SELLER-001</ram:ID>
              <!-- manque : <ram:Name> (BT-27) -->
            </ram:SellerTradeParty>""",
            """
            <ram:SellerTradeParty>
              <ram:ID>SELLER-001</ram:ID>
              <ram>Name>Atelier Duval SARL</ram:Name>
            </ram:SellerTradeParty>""",
            List.of("BR-07", "BR-16")),

        new RulePageContent("BR-07",
            "BR-07 : nom de l'acheteur obligatoire (BT-44) — causes et correction",
            "La facture doit contenir le nom de l'acheteur (BT-44).",
            "BT-44 est le nom légal de votre client. Comme pour le vendeur (BR-06), il est obligatoire en plus de tout identifiant : une facture adressée uniquement à un SIREN est rejetée. C'est l'une des erreurs les plus vues sur les factures générées par des PDP mal configurés.\n\n"
            + "En CII : ram:Name sous ram:BuyerTradeParty. Vérifiez aussi que le bloc acheteur n'a pas été confondu avec le bloc livraison (ShipToTradeParty), confusion classique dans les flux logistiques.",
            """
            <ram:BuyerTradeParty>
              <ram:ID>BUYER-042</ram:ID>
              <!-- manque : <ram:Name> (BT-44) -->
            </ram:BuyerTradeParty>""",
            """
            <ram:BuyerTradeParty>
              <ram:ID>BUYER-042</ram:ID>
              <ram:Name>Client Final SAS</ram:Name>
            </ram:BuyerTradeParty>""",
            List.of("BR-02", "BR-06")),

        new RulePageContent("BR-16",
            "BR-16 : au moins une ligne de facture est requise — causes et correction",
            "Une facture doit contenir au moins une ligne de facture (IncludedSupplyChainTradeLineItem).",
            "Une facture électronique décrit ce qui a été vendu, ligne par ligne. Une facture sans ligne ne transporte aucune prestation et ne peut pas être comptabilisée chez le client — le profil EN 16931 l'interdit donc explicitement.\n\n"
            + "L'échec vient d'un export qui ne contient que les totaux (bloc SpecifiedTradeSettlementHeaderMonetarySummation rempli) sans aucun ram:IncludedSupplyChainTradeLineItem. Si votre système vend un abonnement ou un forfait, créez une ligne unique à 1 unité plutôt qu'une facture sans ligne.",
            """
            <rsm:SupplyChainTradeTransaction>
              <!-- aucune ligne : manque IncludedSupplyChainTradeLineItem -->
              <ram:ApplicableHeaderTradeAgreement>...</ram:ApplicableHeaderTradeAgreement>
            </rsm:SupplyChainTradeTransaction>""",
            """
            <rsm:SupplyChainTradeTransaction>
              <ram:IncludedSupplyChainTradeLineItem>
                <ram:AssociatedDocumentLineDocument>
                  <ram:LineID>1</ram:LineID>
                </ram:AssociatedDocumentLineDocument>
                <!-- ... prix, quantité, article ... -->
              </ram:IncludedSupplyChainTradeLineItem>
              <ram:ApplicableHeaderTradeAgreement>...</ram:ApplicableHeaderTradeAgreement>
            </rsm:SupplyChainTradeTransaction>""",
            List.of("BR-06", "BR-CO-10")),

        new RulePageContent("BR-CO-10",
            "BR-CO-10 : la somme des lignes ne correspond pas au total HT — causes et correction",
            "Le total HT (BT-106) doit être égal à la somme des montants HT de chaque ligne.",
            "BT-106 (LineTotalAmount) est la somme des montants nets de toutes les lignes. Le contrôle arithmétique échoue dès qu'il y a un écart, même d'un centime.\n\n"
            + "Cause n°1 : des totaux saisis ou recalculés à la main alors que les lignes ont changé. Cause n°2 : l'arrondi — trois lignes à 33,33 € donnent 99,99 €, pas 100,00 € : le total doit être la somme réelle des lignes, pas un chiffre « rond » choisi au moment de la facturation. En CII, le total se trouve dans SpecifiedTradeSettlementHeaderMonetarySummation/ram:LineTotalAmount et chaque ligne déclare son propre ram:LineTotalAmount.",
            """
            <ram:SpecifiedTradeSettlementHeaderMonetarySummation>
              <ram:LineTotalAmount>100.00</ram:LineTotalAmount> <!-- devrait être 99.99 -->
            </ram:SpecifiedTradeSettlementHeaderMonetarySummation>
            <!-- lignes : 33.33 + 33.33 + 33.33 = 99.99 -->""",
            """
            <ram:SpecifiedTradeSettlementHeaderMonetarySummation>
              <ram:LineTotalAmount>99.99</ram:LineTotalAmount>
            </ram:SpecifiedTradeSettlementHeaderMonetarySummation>
            <!-- lignes : 33.33 + 33.33 + 33.33 = 99.99 -->""",
            List.of("BR-CO-13", "BR-CO-15")),

        new RulePageContent("BR-CO-13",
            "BR-CO-13 : le total HT ne correspond pas à la somme des bases TVA — causes et correction",
            "Le total HT (BT-109) doit être égal à la somme des bases taxables (BT-116) de la synthèse TVA.",
            "La synthèse TVA décompose le total HT par catégorie de taxe : pour chaque taux, une base taxable (BT-116, ram:BasisAmount). BT-109 (TaxBasisTotalAmount) doit être exactement la somme de ces bases.\n\n"
            + "L'erreur apparaît quand la synthèse TVA est recalculée d'un côté et les totaux de l'autre — par exemple une remise appliquée aux lignes mais pas dans les bases par taux, ou une ligne classée dans une mauvaise catégorie. Vérifiez que chaque catégorie présente dans les lignes a bien sa ligne dans TradeTax, et que les BasisAmount s'additionnent au total HT.",
            """
            <ram:SpecifiedTradeSettlementHeaderMonetarySummation>
              <ram:TaxBasisTotalAmount>1000.00</ram:TaxBasisTotalAmount> <!-- devrait être 1100.00 -->
            </ram:SpecifiedTradeSettlementHeaderMonetarySummation>
            <!-- synthèse TVA : base 20 % = 1100.00 -->""",
            """
            <ram:SpecifiedTradeSettlementHeaderMonetarySummation>
              <ram:TaxBasisTotalAmount>1100.00</ram:TaxBasisTotalAmount>
            </ram:SpecifiedTradeSettlementHeaderMonetarySummation>
            <!-- synthèse TVA : base 20 % = 1100.00 -->""",
            List.of("BR-CO-10", "BR-CO-15")),

        new RulePageContent("BR-CO-15",
            "BR-CO-15 : le total TTC ne correspond pas au total HT plus la TVA — causes et correction",
            "Le total TTC (BT-112) doit être égal au total HT (BT-109) plus le total TVA (BT-110).",
            "C'est l'équation de base de toute facture : TTC = HT + TVA. BT-112 (GrandTotalAmount) doit être exactement la somme de TaxBasisTotalAmount (BT-109) et TaxTotalAmount (BT-110).\n\n"
            + "Cause classique : la TVA est calculée taux par taux avec arrondi, puis le TTC est calculé depuis un taux global — les arrondis divergent d'un centime. Règle d'or : calculez la TVA par catégorie (TaxTotalAmount = Σ pour chaque taux de basis × taux, arrondi au centime), puis additionnez. Notre API /api/generate applique cet arrondi par catégorie nativement.",
            """
            <ram:SpecifiedTradeSettlementHeaderMonetarySummation>
              <ram:TaxBasisTotalAmount>100.00</ram:TaxBasisTotalAmount>
              <ram:TaxTotalAmount>20.00</ram:TaxTotalAmount>
              <ram:GrandTotalAmount>120.01</ram:GrandTotalAmount> <!-- devrait être 120.00 -->
            </ram:SpecifiedTradeSettlementHeaderMonetarySummation>""",
            """
            <ram:SpecifiedTradeSettlementHeaderMonetarySummation>
              <ram:TaxBasisTotalAmount>100.00</ram:TaxBasisTotalAmount>
              <ram:TaxTotalAmount>20.00</ram:TaxTotalAmount>
              <ram:GrandTotalAmount>120.00</ram:GrandTotalAmount>
            </ram:SpecifiedTradeSettlementHeaderMonetarySummation>""",
            List.of("BR-CO-10", "BR-CO-13")));

    private static final Map<String, RulePageContent> BY_SLUG =
        PAGES.stream().collect(Collectors.toUnmodifiableMap(c -> slugOf(c.ruleId()), Function.identity()));

    private RulePages() {}

    public static List<RulePageContent> all() { return PAGES; }

    public static RulePageContent get(String slug) {
        return slug == null ? null : BY_SLUG.get(slug.toLowerCase());
    }

    public static boolean containsRule(String ruleId) {
        return ruleId != null && BY_SLUG.containsKey(ruleId.toLowerCase());
    }

    public static String slugOf(String ruleId) {
        return ruleId.toLowerCase();
    }
}
```

⚠️ 注意上面 BR-06 的 fixedXml 里有一处刻意的笔误（`<ram>Name>` 应为 `<ram:Name>`）——实现时请写成正确的 `<ram:Name>Atelier Duval SARL</ram:Name>`。除此之外内容逐字采用。

- [ ] **Step 4: 跑测试确认绿**

Run: `cd "/c/Project Exploration/03-api-service" && JAVA_HOME="C:\Program Files\Java\jdk-17" /c/MyWork/tools/apache-maven-3.9.16/bin/mvn -pl web test -Dtest=RulePagesTest`
Expected: 4 tests PASS。

- [ ] **Step 5: Commit**

```bash
cd "/c/Project Exploration/03-api-service" && git add web/src/main/java/com/facturxapi/web/RulePageContent.java web/src/main/java/com/facturxapi/web/RulePages.java web/src/test/java/com/facturxapi/web/RulePagesTest.java && git commit -m "feat(web): RulePages content list (10 FR rule pages) with integrity tests"
```

---

### Task 2: RulePagesController + regle.html + 索引页 /regles/（TDD）

**Files:**
- Create: `web/src/main/java/com/facturxapi/web/RulePagesController.java`
- Create: `web/src/main/resources/templates/regle.html`
- Create: `web/src/main/resources/templates/regles.html`
- Modify: `templates/validateur.html`、`templates/tarifs.html`、`templates/docs.html`（nav）
- Test: `web/src/test/java/com/facturxapi/web/RulePagesControllerTest.java`

- [ ] **Step 1: 写控制器测试（先红）**

```java
package com.facturxapi.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class RulePagesControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void knownSlug_200_containsRuleIdAndTitle() throws Exception {
        mvc.perform(get("/regles/br-co-10"))
                .andExpect(status().isOk())
                .andExpect(view().name("regle"))
                .andExpect(content().string(containsString("BR-CO-10")))
                .andExpect(content().string(containsString("la somme des lignes")));
    }

    @Test
    void unknownSlug_404() throws Exception {
        mvc.perform(get("/regles/br-99")).andExpect(status().isNotFound());
    }

    @Test
    void index_200_listsAllRules() throws Exception {
        mvc.perform(get("/regles"))
                .andExpect(status().isOk())
                .andExpect(view().name("regles"))
                .andExpect(content().string(containsString("R&#232;gles m&#233;tier")))
                .andExpect(content().string(containsString("BR-CO-15")));
    }
}
```

（注：`index_200_listsAllRules` 的 `R&#232;gles` 断言假定模板里 H1 写 `Règles métier EN 16931 expliquées` 且 Thymeleaf 对 è 转义；若实测 Thymeleaf 不转义 è，断言改成 `"Règles m"` 即可——以测试实际输出为准，二选一必须稳定绿。）

- [ ] **Step 2: 跑测试确认红**

Run: `... mvn -pl web test -Dtest=RulePagesControllerTest`
Expected: 编译失败（RulePagesController 不存在）。

- [ ] **Step 3: 实现控制器**

```java
package com.facturxapi.web;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/** 规则解释页：/regles/（索引）与 /regles/{slug}（每条 EN 16931 规则一页）。 */
@Controller
public class RulePagesController {

    @GetMapping({"/regles", "/regles/"})
    public String index(Model model) {
        model.addAttribute("pages", RulePages.all());
        return "regles";
    }

    @GetMapping("/regles/{slug}")
    public String page(@PathVariable String slug, Model model) {
        RulePageContent c = RulePages.get(slug);
        if (c == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("c", c);
        return "regle";
    }
}
```

- [ ] **Step 4: 写模板 regle.html**

```html
<!DOCTYPE html>
<html lang="fr" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title th:text="|${c.ruleId} — Factur-X / EN 16931 | Factur-X API|">Règle</title>
  <meta name="description" th:content="${c.summary}">
  <script src="https://cdn.tailwindcss.com"></script>
</head>
<body class="bg-slate-50 text-slate-800 min-h-screen flex flex-col">
  <header class="bg-white border-b border-slate-200">
    <div class="max-w-3xl mx-auto px-4 py-4 flex items-center justify-between">
      <span class="font-bold text-lg">Factur-X API</span>
      <nav class="text-sm space-x-4">
        <a href="/validateur-factur-x" class="text-slate-500 hover:text-slate-800">Validateur gratuit</a>
        <a href="/tarifs" class="text-slate-500 hover:text-slate-800">Tarifs</a>
        <a href="/regles" class="font-medium text-blue-600">Règles</a>
        <a href="/docs" class="text-slate-500 hover:text-slate-800">Docs</a>
      </nav>
    </div>
  </header>

  <main class="max-w-3xl mx-auto px-4 py-10 flex-1 w-full">
    <nav class="text-sm text-slate-400 mb-6">
      <a href="/" class="hover:text-slate-600">Accueil</a>
      <span class="mx-1">/</span>
      <a href="/regles" class="hover:text-slate-600">Règles</a>
      <span class="mx-1">/</span>
      <span th:text="${c.ruleId}"></span>
    </nav>

    <h1 class="text-3xl font-bold mb-4" th:text="${c.title}"></h1>
    <p class="text-lg text-slate-700 bg-blue-50 border border-blue-200 rounded-lg p-4 mb-8"
       th:text="${c.summary}"></p>

    <section class="mb-10 whitespace-pre-line text-slate-700 leading-relaxed"
             th:text="${c.explanation}"></section>

    <section class="mb-10">
      <h2 class="text-xl font-semibold mb-3">Exemple — non conforme puis corrigé</h2>
      <p class="text-sm text-slate-500 mb-2">Rejeté :</p>
      <pre class="bg-red-50 border border-red-200 text-sm rounded-lg p-4 overflow-x-auto mb-4"
           th:text="${c.brokenXml}"></pre>
      <p class="text-sm text-slate-500 mb-2">Corrigé :</p>
      <pre class="bg-emerald-50 border border-emerald-200 text-sm rounded-lg p-4 overflow-x-auto"
           th:text="${c.fixedXml}"></pre>
    </section>

    <!-- CTA -->
    <section class="bg-white border border-slate-200 rounded-xl p-6 mb-10">
      <p class="font-semibold mb-1">Vérifiez votre facture gratuitement</p>
      <p class="text-sm text-slate-500 mb-4">
        Téléversez votre facture (PDF Factur-X ou XML) et voyez immédiatement si
        <span th:text="${c.ruleId}"></span> — ou une autre règle — est en défaut.
      </p>
      <div class="flex flex-wrap gap-3">
        <a href="/validateur-factur-x"
           class="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700">
          Ouvrir le validateur
        </a>
        <a href="/docs"
           class="px-4 py-2 border border-slate-300 text-slate-700 rounded-lg text-sm font-medium hover:bg-slate-50">
          Générer des factures conformes via API
        </a>
        <a href="/tarifs"
           class="px-4 py-2 border border-slate-300 text-slate-700 rounded-lg text-sm font-medium hover:bg-slate-50">
          Tarifs
        </a>
      </div>
    </section>

    <!-- Règles liées -->
    <section class="mb-10" th:if="${!#lists.isEmpty(c.related)}">
      <h2 class="text-xl font-semibold mb-3">Règles liées</h2>
      <ul class="list-disc list-inside text-sm">
        <li th:each="rel : ${c.related}">
          <a th:href="|/regles/${#strings.toLowerCase(rel)}|" class="text-blue-600 underline"
             th:text="${rel}"></a>
        </li>
      </ul>
    </section>
  </main>

  <footer class="bg-white border-t border-slate-200">
    <div class="max-w-3xl mx-auto px-4 py-6 text-sm text-slate-500">
      Factur-X API — validation EN 16931 / Factur-X. La facturation électronique est obligatoire
      en France depuis septembre 2026.
    </div>
  </footer>
</body>
</html>
```

- [ ] **Step 5: 写索引页 regles.html**

```html
<!DOCTYPE html>
<html lang="fr" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Règles métier EN 16931 expliquées (BR-01 à BR-CO-15) | Factur-X API</title>
  <meta name="description" content="Chaque règle métier EN 16931 expliquée simplement : ce qu'elle contrôle, pourquoi elle échoue et comment corriger votre facture électronique.">
  <script src="https://cdn.tailwindcss.com"></script>
</head>
<body class="bg-slate-50 text-slate-800 min-h-screen flex flex-col">
  <header class="bg-white border-b border-slate-200">
    <div class="max-w-3xl mx-auto px-4 py-4 flex items-center justify-between">
      <span class="font-bold text-lg">Factur-X API</span>
      <nav class="text-sm space-x-4">
        <a href="/validateur-factur-x" class="text-slate-500 hover:text-slate-800">Validateur gratuit</a>
        <a href="/tarifs" class="text-slate-500 hover:text-slate-800">Tarifs</a>
        <a href="/regles" class="font-medium text-blue-600">Règles</a>
        <a href="/docs" class="text-slate-500 hover:text-slate-800">Docs</a>
      </nav>
    </div>
  </header>

  <main class="max-w-3xl mx-auto px-4 py-10 flex-1 w-full">
    <h1 class="text-3xl font-bold mb-2">Règles métier EN 16931 expliquées</h1>
    <p class="text-slate-600 mb-8">
      Chaque règle de validation (BR-xx) expliquée simplement : ce qu'elle contrôle, pourquoi votre
      facture échoue et comment corriger. Retrouvez ces identifiants dans les résultats du
      <a href="/validateur-factur-x" class="text-blue-600 underline">validateur gratuit</a>.
    </p>

    <ul class="space-y-3 mb-10">
      <li th:each="p : ${pages}" class="bg-white border border-slate-200 rounded-lg p-4">
        <a th:href="|/regles/${#strings.toLowerCase(p.ruleId)}|" class="font-medium text-blue-600 underline"
           th:text="${p.title}"></a>
        <p class="text-sm text-slate-500 mt-1" th:text="${p.summary}"></p>
      </li>
    </ul>

    <section class="bg-white border border-slate-200 rounded-xl p-6">
      <p class="font-semibold mb-1">Besoin de générer des factures conformes ?</p>
      <p class="text-sm text-slate-500 mb-4">
        L'API crée des factures EN 16931 auto-validées (XSD + Schematron). Gratuit jusqu'à 5 factures par mois.
      </p>
      <div class="flex flex-wrap gap-3">
        <a href="/docs" class="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700">Documentation API</a>
        <a href="/tarifs" class="px-4 py-2 border border-slate-300 text-slate-700 rounded-lg text-sm font-medium hover:bg-slate-50">Obtenir une clé</a>
      </div>
    </section>
  </main>

  <footer class="bg-white border-t border-slate-200">
    <div class="max-w-3xl mx-auto px-4 py-6 text-sm text-slate-500">
      Factur-X API — validation EN 16931 / Factur-X. La facturation électronique est obligatoire
      en France depuis septembre 2026.
    </div>
  </footer>
</body>
</html>
```

- [ ] **Step 6: 三个现有页面 nav 加 Règles 链接**

`validateur.html`、`tarifs.html`、`docs.html` 的 `<nav>` 里，在 Docs 链接前各加一行（注意：各页**自身**的高亮 class 保持不变，Règles 在这三页里用非高亮样式）：

```html
        <a href="/regles" class="text-slate-500 hover:text-slate-800">Règles</a>
```

- [ ] **Step 7: 跑测试确认绿（含全部既有测试）**

Run: `cd "/c/Project Exploration/03-api-service" && JAVA_HOME="C:\Program Files\Java\jdk-17" /c/MyWork/tools/apache-maven-3.9.16/bin/mvn -pl web test`
Expected: BUILD SUCCESS 全绿（若 index 断言 `R&#232;gles` 失败，按 Step 1 注释切换为 `"Règles m"` 再跑）。

- [ ] **Step 8: Commit**

```bash
cd "/c/Project Exploration/03-api-service" && git add web/src/main/java/com/facturxapi/web/RulePagesController.java web/src/main/resources/templates/regle.html web/src/main/resources/templates/regles.html web/src/main/resources/templates/validateur.html web/src/main/resources/templates/tarifs.html web/src/main/resources/templates/docs.html web/src/test/java/com/facturxapi/web/RulePagesControllerTest.java && git commit -m "feat(web): /regles rule pages (controller, templates, index) + nav links"
```

---

### Task 3: 验证器结果页 ruleId 徽章 linkify（TDD）

**Files:**
- Modify: `web/src/main/java/com/facturxapi/web/RulePages.java`（加 `containsRule` 已在 Task 1 提供——确认存在即可）
- Modify: `web/src/main/resources/templates/fragments/resultat.html:36-39`（错误项徽章）与 `:60-63`（警告项徽章）
- Test: `web/src/test/java/com/facturxapi/web/ValidateurControllerTest.java`

- [ ] **Step 1: 写失败断言**

在 `invalidXml_200_withBr07AndFrench` 测试（约 68 行）里追加一行断言（其余不动）：

```java
                .andExpect(content().string(containsString("href=\"/regles/br-07\"")));
```

- [ ] **Step 2: 跑测试确认红**

Run: `... mvn -pl web test -Dtest=ValidateurControllerTest#invalidXml_200_withBr07AndFrench`
Expected: FAIL（徽章还是纯文本，无链接）。

- [ ] **Step 3: 改 fragment**

错误项徽章（原 36-39 行）：

```html
          <th:block th:if="${RulePages.containsRule(i.ruleId)}">
            <a th:href="|/regles/${#strings.toLowerCase(i.ruleId)}|"
               class="shrink-0 px-2 py-0.5 rounded bg-red-100 text-red-800 text-xs font-mono font-semibold hover:bg-red-200"
               th:text="${i.ruleId}"></a>
          </th:block>
          <span th:unless="${RulePages.containsRule(i.ruleId)}"
                class="shrink-0 px-2 py-0.5 rounded bg-red-100 text-red-800 text-xs font-mono font-semibold"
                th:text="${i.ruleId}"></span>
```

警告项徽章（原 60-63 行）做同样替换（`bg-amber-*` 类名保持 amber）。

要让模板能调 `RulePages.containsRule(...)`，需在控制器把工具类放进模型。在 `ValidateurController` 的 GET 与 POST（htmx fragment 路径）两处 model 里加：

```java
model.addAttribute("RulePages", RulePages.class);
```

（静态方法经 SpEL 以 `${RulePages.containsRule(...)}` 调用；若该写法在当前 Thymeleaf 版本不可用，退路：给 fragment 暴露一个 `Set<String> rulePageSlugs` 属性，模板改用 `${rulePageSlugs.contains(#strings.toLowerCase(i.ruleId))}`——以测试绿为准。）

- [ ] **Step 4: 跑全部 web 测试确认绿**

Run: `... mvn -pl web test`
Expected: BUILD SUCCESS（`containsString("BR-07")` 在链接文本里仍命中）。

- [ ] **Step 5: Commit**

```bash
cd "/c/Project Exploration/03-api-service" && git add web/src/main/resources/templates/fragments/resultat.html web/src/main/java/com/facturxapi/web/ValidateurController.java web/src/test/java/com/facturxapi/web/ValidateurControllerTest.java && git commit -m "feat(web): linkify rule IDs in validator results to /regles pages"
```

---

### Task 4: sitemap 动态化（TDD）

**Files:**
- Create: `web/src/main/java/com/facturxapi/web/SitemapController.java`
- Delete: `web/src/main/resources/static/sitemap.xml`
- Test: `web/src/test/java/com/facturxapi/web/SitemapControllerTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.facturxapi.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SitemapControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void sitemap_containsStaticAndRuleUrls() throws Exception {
        mvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("https://factur-x-api.com/regles")))
                .andExpect(content().string(containsString("https://factur-x-api.com/regles/br-co-10")))
                .andExpect(content().string(containsString("https://factur-x-api.com/regles/br-07")))
                .andExpect(content().string(containsString("https://factur-x-api.com/validateur-factur-x")))
                .andExpect(content().string(containsString("https://factur-x-api.com/tarifs")))
                .andExpect(content().string(containsString("https://factur-x-api.com/docs")));
    }
}
```

- [ ] **Step 2: 跑测试确认红**

Run: `... mvn -pl web test -Dtest=SitemapControllerTest`
Expected: FAIL 或 404（静态文件存在但不含 /regles URL——静态资源优先于控制器时表现为旧内容，断言 br-co-10 不在 → FAIL）。

- [ ] **Step 3: 实现 SitemapController，删静态文件**

```java
package com.facturxapi.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/** sitemap.xml 动态生成：既有 4 URL + 规则索引 + 全部规则页。扩页自动跟进。 */
@RestController
public class SitemapController {

    private static final String BASE = "https://factur-x-api.com";

    @GetMapping(value = "/sitemap.xml", produces = "application/xml")
    @ResponseBody
    public String sitemap() {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
          .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        url(sb, "/validateur-factur-x", "1.0");
        url(sb, "/tarifs", "0.9");
        url(sb, "/regles", "0.8");
        url(sb, "/docs", "0.8");
        url(sb, "/", "0.5");
        for (RulePageContent c : RulePages.all()) {
            url(sb, "/regles/" + RulePages.slugOf(c.ruleId()), "0.7");
        }
        sb.append("</urlset>\n");
        return sb.toString();
    }

    private static void url(StringBuilder sb, String path, String priority) {
        sb.append("  <url>\n")
          .append("    <loc>").append(BASE).append(path).append("</loc>\n")
          .append("    <changefreq>monthly</changefreq>\n")
          .append("    <priority>").append(priority).append("</priority>\n")
          .append("  </url>\n");
    }
}
```

然后删除静态文件：

```bash
cd "/c/Project Exploration/03-api-service" && git rm web/src/main/resources/static/sitemap.xml
```

⚠️ 优先级顺序变化（/regles 0.8 插在 tarifs 与 docs 间、/ 从 0.5 保持）：spec 规定索引页 0.8、规则页 0.7，与静态版 4 URL 的优先级（1.0/0.9/0.8/0.5）一致兼容。

- [ ] **Step 4: 跑全部 web 测试确认绿**

Run: `... mvn -pl web test`
Expected: BUILD SUCCESS 全绿。

- [ ] **Step 5: Commit**

```bash
cd "/c/Project Exploration/03-api-service" && git add web/src/main/java/com/facturxapi/web/SitemapController.java web/src/test/java/com/facturxapi/web/SitemapControllerTest.java && git commit -m "feat(web): dynamic sitemap.xml including /regles pages (replaces static file)"
```

---

### Task 5: 本地冒烟 + 打包部署 + 线上冒烟

**Files:** 无新改动（构建产物部署）。

- [ ] **Step 1: 全量测试 + 打包**

```bash
cd "/c/Project Exploration/03-api-service" && JAVA_HOME="C:\Program Files\Java\jdk-17" /c/MyWork/tools/apache-maven-3.9.16/bin/mvn -pl web -am package -DskipTests
```

（若报找不到 validator-core，先在根目录 `mvn install -DskipTests`。）

- [ ] **Step 2: scp 到服务器（web JAR 必须放 web/target/）**

```bash
scp "/c/Project Exploration/03-api-service/web/target/web-0.1.0-SNAPSHOT.jar" root@116.203.186.254:/root/factur-x-api/web/target/web-0.1.0-SNAPSHOT.jar
```

（偶发 Connection reset，重试即可。）

- [ ] **Step 3: 重建容器（服务名叫 app）**

```bash
ssh root@116.203.186.254 "cd /root/factur-x-api && docker compose -f docker-compose.prod.yml up -d --build app"
```

- [ ] **Step 4: 线上冒烟**

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://factur-x-api.com/regles/br-co-10
curl -s https://factur-x-api.com/regles/br-co-10 | grep -o "la somme des lignes" | head -1
curl -s -o /dev/null -w "%{http_code}\n" https://factur-x-api.com/regles
curl -s -o /dev/null -w "%{http_code}\n" https://factur-x-api.com/regles/br-99
curl -s https://factur-x-api.com/sitemap.xml | grep -c "/regles/"
```

预期依次：`200`、`la somme des lignes`、`200`、`404`、`≥ 11`。

- [ ] **Step 5: Search Console**——等有代理时一并做：请求编入 `/regles`（索引页会带动子页发现）。

---

## Self-Review 记录

- **Spec 覆盖**：10 条内容+守恒测试（Task 1）；控制器/模板/索引页/nav（Task 2）；linkify（Task 3）；sitemap 动态化+删静态文件（Task 4）；部署冒烟（Task 5）。无缺口。
- **占位符**：无 TBD；10 条规则内容全量成稿在 Task 1 Step 3（唯一修正项：BR-06 fixedXml 的 `<ram>Name>` 笔误已标注）。Task 3 Step 3 提供了 SpEL 静态调用不可用时的退路，两版写法都完整给出。
- **类型一致性**：`RulePageContent` 组件名（ruleId/title/summary/explanation/brokenXml/fixedXml/related）在 RulePages、regle.html、regles.html、SitemapController 中一致；`RulePages.slugOf/get/containsRule/all` 签名各处一致；slug 全小写规则由测试锁定。
