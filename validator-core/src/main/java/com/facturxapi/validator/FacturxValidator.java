package com.facturxapi.validator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * 门面：输入 PDF 或 XML 字节流，输出统一校验结果。web/api 共用。
 *
 * <p>%PDF- 魔数前缀 → 走 PDF 内嵌 XML 提取（{@link EmbeddedXmlExtractor}）；
 * 否则按 XML 直接进 XSD+Schematron 校验链（{@link XmlRuleValidator}）。
 */
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
