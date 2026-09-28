package com.facturxapi.validator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * 门面：输入 PDF 或 XML 字节流，输出统一校验结果。web/api 共用。
 *
 * <p>%PDF- 魔数前缀 → 走 PDF 内嵌 XML 提取（{@link EmbeddedXmlExtractor}）；
 * 否则按 XML 直接进 XSD+Schematron 校验链（{@link XmlRuleValidator}）。
 *
 * <p>异常契约（分层，调用方按此映射 HTTP 状态码）：
 * <ul>
 *   <li>文档可解析但内容不合规 → 正常返回 {@link ValidationResult}（valid=false，
 *       是校验结论不是异常；web 层映射 200，由 errors 内容决定语义）；</li>
 *   <li>PDF 无法提取（非 PDF / 无内嵌 XML）→ {@link ExtractionException}
 *       （用户输入问题，web 层映射 422）；</li>
 *   <li>规则引擎自身故障 → {@link IllegalStateException}
 *       （不应由用户输入触发，web 层映射 500）。</li>
 * </ul>
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
