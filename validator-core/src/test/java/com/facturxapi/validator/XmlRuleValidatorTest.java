package com.facturxapi.validator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class XmlRuleValidatorTest {

    // spike 实测路径（surefire 工作目录 = validator-core/ 模块目录）
    private XmlRuleValidator validator() {
        return new XmlRuleValidator(
            Path.of("../samples/schema/cii/CrossIndustryInvoice_100pD16B.xsd"),
            Path.of("../samples/schema/schematron/EN16931-CII-validation.sch")
        );
    }

    @Test
    void validSampleReturnsNoErrors() throws Exception {
        byte[] xml = Files.readAllBytes(Path.of("../samples/valid-en16931-cii-basic.xml"));
        ValidationResult r = validator().validate(xml);
        assertThat(r.valid()).isTrue();
        assertThat(r.errors()).isEmpty();
    }

    @Test
    void missingBuyerIsCaughtBySchematronNotXsd() throws Exception {
        byte[] xml = Files.readAllBytes(Path.of("../samples/invalid-missing-buyer.xml"));
        ValidationResult r = validator().validate(xml);
        assertThat(r.valid()).isFalse();
        assertThat(r.errors()).isNotEmpty();
        assertThat(r.errors()).anyMatch(i -> i.ruleId().equals("BR-07"));
        assertThat(r.errors()).noneMatch(i -> i.ruleId().equals("XSD")); // 买方节点 minOccurs=0，XSD 应放行
    }

    @Test
    void negativeAmountIsCaughtAsBrCo16() throws Exception {
        byte[] xml = Files.readAllBytes(Path.of("../samples/invalid-negative-amount.xml"));
        ValidationResult r = validator().validate(xml);
        assertThat(r.errors()).anyMatch(i -> i.ruleId().equals("BR-CO-16"));
    }

    @Test
    void malformedXmlIsCaughtByXsdLayer() throws Exception {
        ValidationResult r = validator().validate("<not-a-cii".getBytes());
        assertThat(r.valid()).isFalse();
        assertThat(r.errors()).anyMatch(i -> i.ruleId().equals("XSD"));
    }

    @Test
    void xsdReportsAllSyntaxErrorsNotJustFirst() {
        // 两个语法错误：未闭合标签 + 非法字符位置，XSD 层应报 ≥2 条
        String bad = "<rsm:CrossIndustryInvoice xmlns:rsm=\"urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100\"><rsm:foo></bar></rsm:CrossIndustryInvoice>";
        ValidationResult r = validator().validate(bad.getBytes());
        assertThat(r.valid()).isFalse();
        long xsdErrors = r.errors().stream().filter(i -> i.ruleId().equals("XSD")).count();
        assertThat(xsdErrors).as("应收集多条 XSD 错误而非只报第一条").isGreaterThanOrEqualTo(2);
    }

    @Test
    void paymentMeansWithoutRemittanceReference_emitsWarning_butStaysValid() throws Exception {
        byte[] original = Files.readAllBytes(Path.of("../samples/valid-en16931-cii-basic.xml"));
        String stripped = new String(original, java.nio.charset.StandardCharsets.UTF_8)
            .replaceAll("(?m)^\\s*<ram:PaymentReference>.*</ram:PaymentReference>\\r?\\n", "");
        assertThat(stripped).contains("SpecifiedTradeSettlementPaymentMeans"); // 前提：样例带付款信息
        assertThat(stripped).doesNotContain("PaymentReference");

        ValidationResult r = validator().validate(stripped.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(r.valid()).isTrue(); // 建议级：不影响合规判定
        assertThat(r.warnings()).anyMatch(i -> i.ruleId().equals("FX-W01"));
    }

    @Test
    void paymentMeansWithRemittanceReference_noWarning() throws Exception {
        byte[] xml = Files.readAllBytes(Path.of("../samples/valid-en16931-cii-basic.xml"));
        ValidationResult r = validator().validate(xml); // 金样例自带 PaymentReference
        assertThat(r.warnings()).noneMatch(i -> i.ruleId().equals("FX-W01"));
    }
}
