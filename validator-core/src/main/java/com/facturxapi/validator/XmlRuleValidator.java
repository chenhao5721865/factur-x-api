package com.facturxapi.validator;

import com.helger.schematron.pure.SchematronResourcePure;
import com.helger.schematron.svrl.SVRLFailedAssert;
import com.helger.schematron.svrl.SVRLHelper;
import com.helger.schematron.svrl.jaxb.SchematronOutputType;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.w3c.dom.Document;
import org.xml.sax.SAXParseException;


/**
 * XSD（语法层）+ Schematron（业务规则层）两段校验。
 *
 * <p>规则资产在构造时编译缓存（XSD 编译与 Schematron 解析都很重，
 * 不允许每次 validate 重建）；每线程通过 {@code newValidator()} 拿独立 Validator。
 */
public final class XmlRuleValidator {

    private static final String XSD_RULE_ID = "XSD";

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
        if (!validateXsd(xml, issues)) {
            return ValidationResult.of(issues); // XSD 不过时 Schematron 结果无意义
        }
        validateSchematron(xml, issues);
        return ValidationResult.of(issues);
    }

    /** @return true = XSD 通过 */
    private boolean validateXsd(byte[] xml, List<Issue> issues) {
        try {
            Validator v = xsdSchema.newValidator();
            // XXE 防护：禁止外部 DTD / schema 解析（用户提交的 XML 走此路径）
            v.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            v.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            v.validate(new StreamSource(new ByteArrayInputStream(xml)));
            return true;
        } catch (SAXParseException e) {
            issues.add(new Issue(XSD_RULE_ID, Severity.ERROR,
                    e.getLineNumber() + ":" + e.getColumnNumber(), String.valueOf(e.getMessage())));
            return false;
        } catch (Exception e) {
            issues.add(new Issue(XSD_RULE_ID, Severity.ERROR, null, String.valueOf(e.getMessage())));
            return false;
        }
    }

    private void validateSchematron(byte[] xml, List<Issue> issues) {
        try {
            // applySchematronValidationToSVRL 入参须是 DOM 节点（spike 实测，7.1.3）
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            // XXE 防护：安全处理 + 禁 DOCTYPE（外部实体随之失效），用户提交的 XML 走此路径
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            SchematronOutputType svrl = schematron.applySchematronValidationToSVRL(doc, null);
            for (SVRLFailedAssert fa : SVRLHelper.getAllFailedAssertions(svrl)) {
                issues.add(new Issue(fa.getID(), Severity.ERROR, fa.getLocation(), fa.getText().trim()));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Schematron 校验执行失败", e);
        }
    }
}
