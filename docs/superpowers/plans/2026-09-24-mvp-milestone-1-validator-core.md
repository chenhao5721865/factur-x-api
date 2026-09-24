# Milestone 1 实施计划：工程脚手架 + Spike 决策门 + validator-core

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立 Maven 多模块工程，用官方样例验证「PDFBox 提取 + XSD/Schematron 校验」技术路线可行（决策门），并在 golden tests 保护下交付 validator-core 验证库。

**Architecture:** 单体多模块 Spring Boot 工程的骨架；validator-core 是无 Web 依赖的纯 Java 库：PDF 输入经 PDFBox 提取内嵌 `factur-x.xml`，XML 经 XSD（UN/CEFACT CII）+ Schematron（EN 16931 CII 规则）两段校验，输出统一 `ValidationResult`。

**Tech Stack:** Java 17、Maven、Spring Boot 3.5（仅脚手架级）、PDFBox 3、ph-schematron-pure（helger）、JUnit 5。

**环境约定（所有 mvn 命令必须这样调用，系统 PATH 里的 mvn 3.6.1 跑在 JDK8 上不可用）：**

```bash
export JAVA_HOME="C:\Program Files\Java\jdk-17"
MVN=/c/MyWork/tools/apache-maven-3.9.16/bin/mvn
$MVN -pl validator-core test   # 示例
```

（Java 从 21 降为 17：本机 JDK 17 可用，MVP 无 21 特性依赖，后续需要时再升级。）

**Spec:** `docs/superpowers/specs/2026-09-24-mvp-design.md` §2.1 §5。profile（MINIMUM/BASIC/EN16931）检测移至 Milestone 2——需研读 FNFE 规范细节，阻塞不了本里程碑。

**决策门（Task 4）:** spike 通过 → 按 Task 5-6 正式化；不通过 → 记录原因到 `docs/notes/spike-decision.md`，返回用户重新决策（方案 2：Python factur-x 微服务）。

---

## 文件结构

```
pom.xml                          # Maven 父 POM（聚合 + 依赖管理）
.gitignore
samples/                         # 测试样例资产（含期望结果命名约定）
validator-core/
├── pom.xml
└── src/
    ├── main/java/com/facturxapi/validator/
    │   ├── Severity.java
    │   ├── Issue.java
    │   ├── ValidationResult.java
    │   ├── ExtractionException.java
    │   ├── EmbeddedXmlExtractor.java
    │   ├── XmlRuleValidator.java      # XSD + Schematron 两段校验
    │   └── FacturxValidator.java      # 门面：探测 PDF/XML → 提取 → 校验
    └── test/java/com/facturxapi/validator/
        ├── EmbeddedXmlExtractorTest.java
        ├── XmlRuleValidatorTest.java
        └── GoldenSamplesTest.java
web/pom.xml                      # Milestone 2 填充，先空模块占位
api/pom.xml                      # Milestone 2 填充，先空模块占位
```

样例命名约定（golden 测试靠它驱动）：`samples/valid-<描述>.xml` 期望通过；`samples/invalid-<描述>.xml` 期望有 ERROR。

---

### Task 1: Maven 多模块脚手架

**Files:**
- Create: `pom.xml`、`.gitignore`、`validator-core/pom.xml`、`web/pom.xml`、`api/pom.xml`

- [ ] **Step 1: 写父 POM**

`pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.5</version>
    <relativePath/>
  </parent>
  <groupId>com.facturxapi</groupId>
  <artifactId>factur-x-api</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <packaging>pom</packaging>

  <properties>
    <java.version>17</java.version>
    <pdfbox.version>3.0.5</pdfbox.version>
    <ph-schematron.version>7.1.3</ph-schematron.version>
  </properties>

  <modules>
    <module>validator-core</module>
    <module>web</module>
    <module>api</module>
  </modules>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.apache.pdfbox</groupId>
        <artifactId>pdfbox</artifactId>
        <version>${pdfbox.version}</version>
      </dependency>
      <dependency>
        <groupId>com.helger.schematron</groupId>
        <artifactId>ph-schematron-pure</artifactId>
        <version>${ph-schematron.version}</version>
      </dependency>
    </dependencies>
  </dependencyManagement>
</project>
```

注：`ph-schematron.version` 已确认为 7.1.3（7.x 最新实存版本，7.1.5 不存在）。

- [ ] **Step 2: 写 .gitignore**

`.gitignore`：

```
target/
*.class
.idea/
*.iml
.vscode/
*.log
```

- [ ] **Step 3: 写 validator-core 模块 POM**

`validator-core/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.facturxapi</groupId>
    <artifactId>factur-x-api</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </parent>
  <artifactId>validator-core</artifactId>

  <dependencies>
    <dependency>
      <groupId>org.apache.pdfbox</groupId>
      <artifactId>pdfbox</artifactId>
    </dependency>
    <dependency>
      <groupId>com.helger.schematron</groupId>
      <artifactId>ph-schematron-pure</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 4: 写 web 与 api 占位模块 POM**

`web/pom.xml`（`api/pom.xml` 仅 artifactId 不同，其余逐字相同）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.facturxapi</groupId>
    <artifactId>factur-x-api</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </parent>
  <artifactId>web</artifactId>
</project>
```

- [ ] **Step 5: 验证构建**

Run: `mvn -q validate`
Expected: 无输出退出码 0（BUILD SUCCESS）

- [ ] **Step 6: Commit**

```bash
git add pom.xml .gitignore validator-core/pom.xml web/pom.xml api/pom.xml
git commit -m "chore: Maven 多模块脚手架"
```

---

### Task 2: 收集测试样例资产

**Files:**
- Create: `samples/`（至少 3 个 XML：1 valid 2 invalid，供 spike；golden 全集后续扩充）

- [ ] **Step 1: 克隆样例来源仓库到临时目录**

```bash
git clone --depth 1 https://github.com/ConnectingEurope/eInvoicing-EN16931 /tmp/en16931
git clone --depth 1 https://github.com/easybill/e-invoice-validator /tmp/easybill
```

若 /tmp 不可用（Windows），用 `$TEMP` 目录替代。

- [ ] **Step 2: 挑样例**

在两个仓库中找 CII 语法（CrossIndustryInvoice 根元素）的测试实例：
- EN 16931 仓库常见路径：`test instances/`（注意空格）或 `instances/` 下 CII 子目录
- easybill 仓库：`tests/` 下 fixture XML

挑选标准：根元素为 `CrossIndustryInvoice`（注意 Factur-X/XRechnung 同属 CII 语法）；挑 1 个规范声明 EN 16931 的完整实例 + 2 个明显有业务错误的实例（如缺买方、金额为负）。

- [ ] **Step 3: 按命名约定放入 samples/ 并记录来源**

```bash
mkdir -p samples
cp <选中的valid样例> samples/valid-en16931-cii-basic.xml
cp <选中的invalid样例1> samples/invalid-missing-buyer.xml
cp <选中的invalid样例2> samples/invalid-negative-amount.xml
```

并在 `samples/README.md` 记录每个文件的来源仓库与原始路径（黄金样本必须可溯源）。

- [ ] **Step 4: Commit**

```bash
git add samples/
git commit -m "test: 引入 EN16931 CII 样例（1 valid 2 invalid）"
```

---

### Task 3: Spike A — PDFBox 提取内嵌 factur-x.xml（TDD）

**Files:**
- Create: `validator-core/src/main/java/com/facturxapi/validator/EmbeddedXmlExtractor.java`
- Create: `validator-core/src/main/java/com/facturxapi/validator/ExtractionException.java`
- Test: `validator-core/src/test/java/com/facturxapi/validator/EmbeddedXmlExtractorTest.java`
- Test asset: `samples/factur-x-basic.pdf`

**前置说明**：需要一个真实的 Factur-X PDF（内嵌 XML 的 PDF/A-3）。easybill 仓库 `tests/` 下通常有 Factur-X PDF fixture；没有的话用 FNFE-MPE 官方样例包 https://fnfe-mpe.org/factur-x/factur-x_en/ 下载的样例。放入 `samples/factur-x-basic.pdf` 并在 `samples/README.md` 补来源。

- [ ] **Step 1: 写失败测试**

```java
package com.facturxapi.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EmbeddedXmlExtractorTest {

    private byte[] samplePdf() throws Exception {
        return Files.readAllBytes(Path.of("samples/factur-x-basic.pdf"));
    }

    @Test
    void extractsEmbeddedFacturXmlFromPdf() throws Exception {
        byte[] xml = EmbeddedXmlExtractor.extractFacturXml(samplePdf());
        assertThat(new String(xml)).contains("CrossIndustryInvoice");
    }

    @Test
    void rejectsPdfWithoutEmbeddedXml() throws Exception {
        // 最小化的"空内容 PDF"：%PDF-1.4 头 + EOF，足够让 PDFBox 报格式错误或无附件
        byte[] junk = "%PDF-1.4\n%%EOF\n".getBytes();
        assertThatThrownBy(() -> EmbeddedXmlExtractor.extractFacturXml(junk))
            .isInstanceOf(ExtractionException.class);
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -pl validator-core test -Dtest=EmbeddedXmlExtractorTest`
Expected: 编译失败（`EmbeddedXmlExtractor`/`ExtractionException` 不存在）

- [ ] **Step 3: 最小实现**

`ExtractionException.java`：

```java
package com.facturxapi.validator;

public class ExtractionException extends RuntimeException {
    public ExtractionException(String message) {
        super(message);
    }
}
```

`EmbeddedXmlExtractor.java`：

```java
package com.facturxapi.validator;

import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameTree;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;

/** 从 PDF（Factur-X = PDF/A-3）中提取内嵌的 factur-x.xml。 */
public final class EmbeddedXmlExtractor {

    private EmbeddedXmlExtractor() {}

    public static byte[] extractFacturXml(byte[] pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDDocumentNameTree names = doc.getNames();
            if (names == null || names.getEmbeddedFiles() == null) {
                throw new ExtractionException("PDF 不含内嵌附件，不是 Factur-X 发票");
            }
            for (PDComplexFileSpecification fs : names.getEmbeddedFiles().getEmbeddedFiles().values()) {
                String name = fs.getFilename() == null ? "" : fs.getFilename().toLowerCase();
                PDEmbeddedFile embedded = fs.getEmbeddedFile();
                if (embedded != null && name.endsWith(".xml")) {
                    return embedded.toByteArray();
                }
            }
            throw new ExtractionException("未找到内嵌 factur-x.xml");
        } catch (IOException e) {
            throw new ExtractionException("PDF 解析失败: " + e.getMessage());
        }
    }
}
```

注：MVP 不做严格 PDF/A-3 合规性检查（那需要 veraPDF，重资产）——「有内嵌 XML 且 XML 校验通过」已满足免费工具的验证价值；PDF/A 合规检测列入 Milestone 2 备选。

- [ ] **Step 4: 运行确认通过**

Run: `mvn -pl validator-core test -Dtest=EmbeddedXmlExtractorTest`
Expected: 2 个测试 PASS

- [ ] **Step 5: Commit**

```bash
git add validator-core samples/factur-x-basic.pdf samples/README.md
git commit -m "feat(validator-core): PDFBox 提取内嵌 factur-x.xml"
```

---

### Task 4: Spike B — XSD + Schematron 校验链跑通（决策门）

**Files:**
- Create: `samples/schema/`（XSD + .sch 规则文件，来源记录进 `samples/README.md`）
- Create: `validator-core/src/test/java/com/facturxapi/validator/spike/SpikeValidationTest.java`（临时探针，决策后删除或转正）

- [ ] **Step 1: 找到并放置规则资产**

从 Task 2 克隆的仓库中定位并复制：
- CII XSD（根 schema 为 `CrossIndustryInvoice` 所在的 D16B schema 集合）→ `samples/schema/cii/`
- EN 16931 CII Schematron（`.sch` 文件，通常名 `EN16931-CII-validation.sch`）→ `samples/schema/EN16931-CII-validation.sch`

优先来源：ConnectingEurope/eInvoicing-EN16931 仓库（schematron 与 xsd 目录）；备选：itplr-kosit/validator-configuration-xrechnung 的 release 包。把实际取用路径记入 `samples/README.md`。

- [ ] **Step 2: 写 spike 探针测试**

```java
package com.facturxapi.validator.spike;

import static org.assertj.core.api.Assertions.assertThat;

import com.helger.schematron.pure.SchematronResourcePure;
import com.helger.schematron.svrl.jaxb.SchematronOutputType;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXParseException;

/** 决策门探针：验证 XSD+Schematron 纯 Java 链路可行。决策后删除或转正。 */
class SpikeValidationTest {

    @Test
    void xsdAcceptsValidSample() throws Exception {
        var factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        var schema = factory.newSchema(Path.of("samples/schema/cii/D16B SCRDM (Subset)/uncoupled clm/CII/uncefact/data/standard/CrossIndustryInvoice_100pD16B.xsd").toFile());
        schema.newValidator().validate(new StreamSource(Files.newInputStream(Path.of("samples/valid-en16931-cii-basic.xml"))));
        // 不抛异常 = 通过
    }

    @Test
    void schematronFlagsInvalidSampleAndAcceptsValidOne() throws Exception {
        var sch = SchematronResourcePure.fromFile(Path.of("samples/schema/EN16931-CII-validation.sch").toFile());

        SchematronOutputType outValid = sch.applySchematronValidation(new StreamSource(Files.newInputStream(Path.of("samples/valid-en16931-cii-basic.xml"))));
        assertThat(failedAssertCount(outValid)).isZero();

        SchematronOutputType outInvalid = sch.applySchematronValidation(new StreamSource(Files.newInputStream(Path.of("samples/invalid-missing-buyer.xml"))));
        assertThat(failedAssertCount(outInvalid)).isPositive();
    }

    private long failedAssertCount(SchematronOutputType out) {
        return out.getActivePatternAndRuleAndAssertAndReport().stream()
            .filter(o -> o instanceof com.helger.schematron.svrl.jaxb.FailedAssert)
            .count();
    }
}
```

注 1：XSD 路径以实际下载到的 schema 目录结构为准（D16B 包目录名含空格/括号），`SchemaFactory` 需要指向 `CrossIndustryInvoice` 主 schema 文件。
注 2：若 `SchematronResourcePure.fromFile` 处理大 `.sch` 报内存/兼容错误，改用 `SchematronResourcePure.fromFile(path, null)` 重载或升级 ph-schematron 版本——这正是 spike 要暴露的问题。

- [ ] **Step 3: 运行探针**

Run: `mvn -pl validator-core test -Dtest=SpikeValidationTest`
Expected: 2 个测试 PASS

- [ ] **Step 4: 记录决策（决策门）**

跑通 → 在 `docs/notes/spike-decision.md` 写 5 行以内结论（用了哪个 XSD/sch 版本、遇到什么坑、结论：方案 1 可行），继续 Task 5。
跑不通（规则文件 incompatible / 引擎缺陷 / 工作量失控 > 4h）→ 写明失败原因与证据，**停下来向用户汇报**，触发方案 2 重决策。

- [ ] **Step 5: Commit**

```bash
git add samples/schema samples/README.md validator-core/src docs/notes/spike-decision.md
git commit -m "test(spike): XSD+Schematron 纯 Java 校验链决策门通过"
```

---

### Task 5: validator-core 正式化 — 结果模型与门面（TDD）

> **T3 审查带入的加固项（本任务必须一并完成）**：
> 1. `ExtractionException` 增加 `(String, Throwable)` 构造器，EmbeddedXmlExtractor 两处包装传入 cause（当前丢堆栈）
> 2. 补分支覆盖测试：合成"可解析但无附件"PDF、仅带非 .xml 附件的 PDF、/UF 与 /F 不一致的 PDF（当前两个分支零覆盖）
> 3. `collect()` 递归加 visited 集合防 /Kids 环（畸形 PDF StackOverflow）
> 4. `toLowerCase()` 补 `Locale.ROOT`
> 5. 静态工具类是否转实例化，届时结合门面需求定（现在改动成本最低）

**Files:**
- Create: `validator-core/src/main/java/com/facturxapi/validator/Severity.java`
- Create: `validator-core/src/main/java/com/facturxapi/validator/Issue.java`
- Create: `validator-core/src/main/java/com/facturxapi/validator/ValidationResult.java`
- Create: `validator-core/src/main/java/com/facturxapi/validator/XmlRuleValidator.java`
- Create: `validator-core/src/main/java/com/facturxapi/validator/FacturxValidator.java`
- Modify: `validator-core/src/test/java/com/facturxapi/validator/XmlRuleValidatorTest.java`

- [ ] **Step 1: 写失败测试**

`XmlRuleValidatorTest.java`：

```java
package com.facturxapi.validator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class XmlRuleValidatorTest {

    private XmlRuleValidator validator() {
        return new XmlRuleValidator(
            Path.of("samples/schema/cii/CrossIndustryInvoice_100pD16B.xsd"), // 主 schema，实际路径以 Task 4 spike 确认为准
            Path.of("samples/schema/EN16931-CII-validation.sch")
        );
    }

    @Test
    void validSampleReturnsNoErrors() throws Exception {
        byte[] xml = Files.readAllBytes(Path.of("samples/valid-en16931-cii-basic.xml"));
        ValidationResult r = validator().validate(xml);
        assertThat(r.valid()).isTrue();
        assertThat(r.errors()).isEmpty();
    }

    @Test
    void invalidSampleReturnsErrorsWithRuleIds() throws Exception {
        byte[] xml = Files.readAllBytes(Path.of("samples/invalid-missing-buyer.xml"));
        ValidationResult r = validator().validate(xml);
        assertThat(r.valid()).isFalse();
        assertThat(r.errors()).isNotEmpty();
        assertThat(r.errors().get(0).ruleId()).isNotBlank();
        assertThat(r.errors().get(0).message()).isNotBlank();
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -pl validator-core test -Dtest=XmlRuleValidatorTest`
Expected: 编译失败（类不存在）

- [ ] **Step 3: 实现结果模型与校验器**

`Severity.java`：

```java
package com.facturxapi.validator;

public enum Severity { ERROR, WARNING }
```

`Issue.java`：

```java
package com.facturxapi.validator;

public record Issue(String ruleId, Severity severity, String xpath, String message) {}
```

`ValidationResult.java`：

```java
package com.facturxapi.validator;

import java.util.List;

public record ValidationResult(boolean valid, String profile, List<Issue> errors, List<Issue> warnings) {

    public static ValidationResult of(List<Issue> issues) {
        List<Issue> errors = issues.stream().filter(i -> i.severity() == Severity.ERROR).toList();
        List<Issue> warnings = issues.stream().filter(i -> i.severity() == Severity.WARNING).toList();
        return new ValidationResult(errors.isEmpty(), null, errors, warnings); // profile 检测 Milestone 2
    }
}
```

`XmlRuleValidator.java`：

```java
package com.facturxapi.validator;

import com.helger.schematron.pure.SchematronResourcePure;
import com.helger.schematron.svrl.jaxb.FailedAssert;
import com.helger.schematron.svrl.jaxb.SchematronOutputType;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.xml.sax.SAXParseException;

/** XSD（语法层）+ Schematron（业务规则层）两段校验。 */
public final class XmlRuleValidator {

    private final Schema xsdSchema;
    private final SchematronResourcePure schematron;

    public XmlRuleValidator(Path xsdMainSchema, Path schematronFile) {
        try {
            this.xsdSchema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(xsdMainSchema.toFile());
            this.schematron = SchematronResourcePure.fromFile(schematronFile.toFile());
        } catch (Exception e) {
            throw new IllegalStateException("规则资产初始化失败", e);
        }
    }

    public ValidationResult validate(byte[] xml) {
        List<Issue> issues = new ArrayList<>();
        validateXsd(xml, issues);
        if (issues.isEmpty()) { // XSD 不过时 Schematron 结果无意义，先报语法错
            validateSchematron(xml, issues);
        }
        return ValidationResult.of(issues);
    }

    private void validateXsd(byte[] xml, List<Issue> issues) {
        try {
            xsdSchema.newValidator().validate(new StreamSource(new ByteArrayInputStream(xml)));
        } catch (SAXParseException e) {
            issues.add(new Issue("XSD", Severity.ERROR,
                e.getLineNumber() + ":" + e.getColumnNumber(), e.getMessage()));
        } catch (Exception e) {
            issues.add(new Issue("XSD", Severity.ERROR, null, String.valueOf(e.getMessage())));
        }
    }

    private void validateSchematron(byte[] xml, List<Issue> issues) {
        try {
            SchematronOutputType out =
                schematron.applySchematronValidation(new StreamSource(new ByteArrayInputStream(xml)));
            for (Object o : out.getActivePatternAndRuleAndAssertAndReport()) {
                if (o instanceof FailedAssert fa) {
                    issues.add(new Issue(fa.getId(), Severity.ERROR, fa.getLocation(), fa.getText()));
                }
                // SuccessfulReport（warning 级）Milestone 2 再接入
            }
        } catch (Exception e) {
            throw new IllegalStateException("Schematron 校验执行失败", e);
        }
    }
}
```

注：构造函数接收**主 schema 文件路径**（Task 4 spike 验证通过的那个 `CrossIndustryInvoice_100pD16B.xsd` 的实际相对路径）。若 spike 确认的真实文件名/层级与此处示例不同，以 spike 实测为准，并同步改 XmlRuleValidatorTest 与 GoldenSamplesTest 两处。

- [ ] **Step 4: 运行确认通过**

Run: `mvn -pl validator-core test -Dtest=XmlRuleValidatorTest`
Expected: 2 个测试 PASS

- [ ] **Step 5: Commit**

```bash
git add validator-core
git commit -m "feat(validator-core): XSD+Schematron 两段校验与统一结果模型"
```

---

### Task 6: 门面 + Golden 样例回归

**Files:**
- Create: `validator-core/src/main/java/com/facturxapi/validator/FacturxValidator.java`
- Test: `validator-core/src/test/java/com/facturxapi/validator/GoldenSamplesTest.java`

- [ ] **Step 1: 写 golden 测试（失败）**

```java
package com.facturxapi.validator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Golden 回归：samples/ 下 valid-*.xml 必须全过，invalid-*.xml 必须报错。
 * 新增样例自动纳入——扩展样例集无需改本测试。
 */
class GoldenSamplesTest {

    static final Path SAMPLES = Path.of("samples");
    // 主 schema 文件路径 = Task 4 spike 中验证通过的那个 XSD 文件，两处测试必须一致
    static final Path SCHEMA = Path.of("samples/schema/cii/CrossIndustryInvoice_100pD16B.xsd");

    static Stream<Path> xmlSamples() throws IOException {
        try (Stream<Path> s = Files.list(SAMPLES)) {
            return s.filter(p -> p.toString().endsWith(".xml")).toList().stream();
        }
    }

    @ParameterizedTest
    @MethodSource("xmlSamples")
    @EnabledIf("hasSamples")
    void golden(Path sample) {
        XmlRuleValidator v = new XmlRuleValidator(SCHEMA, Path.of("samples/schema/EN16931-CII-validation.sch"));
        ValidationResult r = v.validate(readAll(sample));
        if (sample.getFileName().toString().startsWith("valid-")) {
            assertThat(r.valid()).as("%s 应通过", sample).isTrue();
        } else {
            assertThat(r.valid()).as("%s 应报错", sample).isFalse();
        }
    }

    static boolean hasSamples() {
        return Files.isDirectory(SAMPLES);
    }

    private static byte[] readAll(Path p) {
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
```

注：`SCHEMA` 常量里的文件名在 Task 4 spike 后即为已知，填实际值——这是 spike 产出的一部分，不允许留 `<...>` 占位进主干。

- [ ] **Step 2: 实现 FacturxValidator 门面**

```java
package com.facturxapi.validator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** 门面：输入 PDF 或 XML 字节流，输出统一校验结果。web/api 共用。 */
public final class FacturxValidator {

    private final XmlRuleValidator ruleValidator;

    public FacturxValidator(Path xsdMainSchema, Path schematronFile) {
        this.ruleValidator = new XmlRuleValidator(xsdMainSchema, schematronFile);
    }

    public ValidationResult validate(byte[] input) {
        byte[] xml = looksLikePdf(input)
            ? EmbeddedXmlExtractor.extractFacturXml(input)
            : input;
        return ruleValidator.validate(xml);
    }

    private static boolean looksLikePdf(byte[] b) {
        return b.length > 4 && new String(b, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-");
    }
}
```

- [ ] **Step 3: 全模块回归**

Run: `mvn -pl validator-core test`
Expected: 全部 PASS（EmbeddedXmlExtractor 2 + XmlRuleValidator 2 + Golden N + spike 探针）

- [ ] **Step 4: Commit**

```bash
git add validator-core
git commit -m "feat(validator-core): FacturxValidator 门面与 golden 回归测试"
```

---

### Task 7: 里程碑收尾

- [ ] **Step 1: 更新 `docs/notes/spike-decision.md`**：补充正式化后的实测结论（规则版本、已知限制：profile 检测未做、PDF/A 严格合规未做、SuccessfulReport warning 未接入）
- [ ] **Step 2: 全量构建**：`mvn -q test`，Expected: BUILD SUCCESS
- [ ] **Step 3: Commit**

```bash
git add docs/notes/spike-decision.md
git commit -m "docs: Milestone 1 收尾——spike 结论与已知限制"
```

完成后向用户汇报 golden 样例数与已知限制，申请启动 Milestone 2（web 工具页）计划。
