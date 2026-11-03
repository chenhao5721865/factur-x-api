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
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;


/**
 * XSD（语法层）+ Schematron（业务规则层）两段校验。
 *
 * <p>规则资产在构造时编译缓存（XSD 编译与 Schematron 解析都很重，
 * 不允许每次 validate 重建）；每线程通过 {@code newValidator()} 拿独立 Validator。
 *
 * <p>异常契约（分层）：文档内容问题（语法/规则不通过）一律以 {@link ValidationResult}
 * 返回（valid=false，不是异常）；规则引擎自身故障才抛 {@link IllegalStateException}。
 * 即：异常只留给"引擎坏了"，不留给"文档坏了"。
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
        Document doc = parseSecurely(xml);
        validateSchematron(doc, issues);
        checkRemittanceReference(doc, issues);
        return ValidationResult.of(issues);
    }

    /** @return true = XSD 通过 */
    private boolean validateXsd(byte[] xml, List<Issue> issues) {
        // 收集型 ErrorHandler：聚齐全部 XSD 错误（而非遇第一个就抛）
        ErrorHandler collector = new ErrorHandler() {
            @Override
            public void warning(SAXParseException e) {
                // warning 不阻断，也不计入 errors（当前无 warning 接入需求）
            }

            @Override
            public void error(SAXParseException e) {
                addXsdIssueIfNew(issues, e);
            }

            @Override
            public void fatalError(SAXParseException e) {
                // 良构性错误：解析器通常会停，但仍可能多次回调（实测同一错误会经 error+fatalError 各上报一次）
                addXsdIssueIfNew(issues, e);
            }
        };
        try {
            Validator v = xsdSchema.newValidator();
            // XXE 防护：禁止外部 DTD / schema 解析（用户提交的 XML 走此路径）
            v.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            v.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            v.setErrorHandler(collector);
            v.validate(new StreamSource(new ByteArrayInputStream(xml)));
        } catch (SAXParseException e) {
            // 兜底：fatalError 回调后 Xerces 仍会重抛同一异常，此处去重避免重复条目
            addXsdIssueIfNew(issues, e);
        } catch (Exception e) {
            // 意外异常（非文档问题）兜底
            issues.add(new Issue(XSD_RULE_ID, Severity.ERROR, null, String.valueOf(e.getMessage())));
        }
        return issues.stream().noneMatch(i -> i.ruleId().equals(XSD_RULE_ID) && i.severity() == Severity.ERROR);
    }

    /** 同一条 SAXParseException 会被多次上报（error/fatalError/重抛），按 message 去重 */
    private static void addXsdIssueIfNew(List<Issue> issues, SAXParseException e) {
        String msg = String.valueOf(e.getMessage());
        if (issues.stream().noneMatch(i -> i.message().equals(msg))) {
            issues.add(new Issue(XSD_RULE_ID, Severity.ERROR,
                    e.getLineNumber() + ":" + e.getColumnNumber(), msg));
        }
    }

    private void validateSchematron(Document doc, List<Issue> issues) {
        try {
            // applySchematronValidationToSVRL 入参须是 DOM 节点（spike 实测，7.1.3）
            SchematronOutputType svrl = schematron.applySchematronValidationToSVRL(doc, null);
            for (SVRLFailedAssert fa : SVRLHelper.getAllFailedAssertions(svrl)) {
                issues.add(new Issue(fa.getID(), Severity.ERROR, fa.getLocation(), fa.getText().trim()));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Schematron 校验执行失败", e);
        }
    }

    /**
     * 建议级检查（非 EN 16931 规则）：发票带付款信息（IBAN）但没有 PaymentReference (BT-83)
     * 时，买方无法把付款与发票对应。不构成不合规（valid 不受影响），只提示。
     */
    private static void checkRemittanceReference(Document doc, List<Issue> issues) {
        if (doc.getElementsByTagNameNS(RAM_NS, "SpecifiedTradeSettlementPaymentMeans")
                .getLength() == 0) {
            return; // 没有付款信息就无从谈起
        }
        if (doc.getElementsByTagNameNS(RAM_NS, "PaymentReference").getLength() > 0) {
            return; // 已有汇款参考
        }
        issues.add(new Issue("FX-W01", Severity.WARNING,
            "/rsm:CrossIndustryInvoice/rsm:SupplyChainTradeTransaction"
                + "/ram:ApplicableHeaderTradeSettlement/ram:SpecifiedTradeSettlementPaymentMeans",
            "Invoice carries payment details (IBAN) but no PaymentReference (BT-83): "
                + "the buyer cannot match the payment back to this invoice."));
    }

    private static Document parseSecurely(byte[] xml) throws IllegalStateException {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            // XXE 防护：安全处理 + 禁 DOCTYPE（外部实体随之失效），用户提交的 XML 走此路径
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new IllegalStateException("XML 解析失败", e);
        }
    }

    private static final String RAM_NS =
        "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100";
}
