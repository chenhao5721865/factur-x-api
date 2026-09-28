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
}
