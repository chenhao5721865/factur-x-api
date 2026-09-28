package com.facturxapi.validator.spike;

import static org.assertj.core.api.Assertions.assertThat;

import com.helger.schematron.pure.SchematronResourcePure;
import com.helger.schematron.svrl.SVRLHelper;
import com.helger.schematron.svrl.SVRLFailedAssert;
import com.helger.schematron.svrl.jaxb.SchematronOutputType;
import java.io.File;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

/**
 * 决策门探针（M1-T4）：验证「纯 Java XSD + Schematron」校验链对 EN 16931 CII 可行。
 * 决策后本类将被 Task 5 转正或删除，不做长期维护。
 *
 * ph-schematron-pure 7.1.3 实际 API 说明（与任务模板的出入）：
 * - applySchematronValidation 返回 org.w3c.dom.Document；
 *   取 SVRL JAXB 对象要用 applySchematronValidationToSVRL(Node, baseURI)，入参须先解析成 DOM；
 * - 文本/规则 id 用 SVRLHelper.getAllFailedAssertions(...) → SVRLFailedAssert.getID()/getText()。
 */
class SpikeValidationTest {

    private static final Path XSD_MAIN = Path.of("../samples/schema/cii/CrossIndustryInvoice_100pD16B.xsd");
    private static final Path SCH = Path.of("../samples/schema/schematron/EN16931-CII-validation.sch");
    private static final Path VALID = Path.of("../samples/valid-en16931-cii-basic.xml");
    private static final Path INVALID_MISSING_BUYER = Path.of("../samples/invalid-missing-buyer.xml");
    private static final Path INVALID_NEGATIVE_AMOUNT = Path.of("../samples/invalid-negative-amount.xml");

    private static void validateXsd(Path xml) throws Exception {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        var schema = factory.newSchema(XSD_MAIN.toFile());
        // 显式加载我们的 XSD，样例中 xsi:schemaLocation 指向的无效相对路径会被忽略，无害
        schema.newValidator().validate(new StreamSource(xml.toFile()));
    }

    private static SchematronOutputType validateSchematron(Path xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder().parse(new File(xml.toString()));
        SchematronResourcePure sch = SchematronResourcePure.fromFile(SCH.toFile());
        return sch.applySchematronValidationToSVRL(doc, xml.toUri().toString());
    }

    private static void dumpFailedAsserts(String label, SchematronOutputType out) {
        System.out.println("[" + label + " failed-asserts] 共 " + SVRLHelper.getAllFailedAssertions(out).size() + " 条");
        for (SVRLFailedAssert a : SVRLHelper.getAllFailedAssertions(out)) {
            System.out.println("  " + a.getID() + " :: " + a.getText().trim());
        }
    }

    @Test
    void xsdAcceptsValidSample() throws Exception {
        // 不抛异常 = 通过
        validateXsd(VALID);
    }

    @Test
    void xsdAloneMissesBusinessRuleDefects() throws Exception {
        // 证明"只靠 XSD 不够"：两条 invalid 都应通过 XSD（规则层缺陷 XSD 管不着）
        validateXsd(INVALID_MISSING_BUYER);
        validateXsd(INVALID_NEGATIVE_AMOUNT);
    }

    @Test
    void schematronAcceptsValidSample() throws Exception {
        SchematronOutputType out = validateSchematron(VALID);
        dumpFailedAsserts("valid", out);
        assertThat(SVRLHelper.getAllFailedAssertions(out))
                .as("valid 样例应无 failed assert")
                .isEmpty();
    }

    @Test
    void schematronFlagsInvalidSamples() throws Exception {
        SchematronOutputType outBuyer = validateSchematron(INVALID_MISSING_BUYER);
        dumpFailedAsserts("missing-buyer", outBuyer);
        // 注：CEF schematron 的规则 id 用两位零填充编号，"买方名称必填"实际是 BR-07（BR-05 是发票币种），
        // 与 EN 16931 标准正文里俗称的 "BR-5" 不是同一编号体系，见 docs/notes/spike-decision.md
        assertThat(SVRLHelper.getAllFailedAssertions(outBuyer).stream().map(SVRLFailedAssert::getID))
                .as("invalid-missing-buyer 应被 Schematron 抓到，并命中 BR-07（买方名称 BT-44 必填）")
                .contains("BR-07");

        SchematronOutputType outAmount = validateSchematron(INVALID_NEGATIVE_AMOUNT);
        dumpFailedAsserts("negative-amount", outAmount);
        assertThat(SVRLHelper.getAllFailedAssertions(outAmount).stream().map(SVRLFailedAssert::getID))
                .as("invalid-negative-amount 应被 Schematron 抓到，并命中 BR-CO-16（BT-115 = BT-112 - BT-113 + BT-114）")
                .contains("BR-CO-16");
    }
}
