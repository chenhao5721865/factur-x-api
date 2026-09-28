package com.facturxapi.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FacturxValidatorTest {

    static FacturxValidator validator;

    @BeforeAll
    static void init() {
        validator = new FacturxValidator(
            Path.of("../samples/schema/cii/CrossIndustryInvoice_100pD16B.xsd"),
            Path.of("../samples/schema/schematron/EN16931-CII-validation.sch"));
    }

    @Test
    void validatesFacturxPdfEndToEnd() throws Exception {
        byte[] pdf = Files.readAllBytes(Path.of("../samples/factur-x-basic.pdf"));
        ValidationResult r = validator.validate(pdf);
        // Mustang 样例是合规发票，期望整体通过（若实测有 failed assert，如实改断言并说明该样例的实测规则命中）
        assertThat(r.valid()).as("Mustang EN16931 样例应通过").isTrue();
    }

    @Test
    void validatesXmlDirectly() throws Exception {
        byte[] xml = Files.readAllBytes(Path.of("../samples/invalid-missing-buyer.xml"));
        ValidationResult r = validator.validate(xml);
        assertThat(r.valid()).isFalse();
        assertThat(r.errors()).anyMatch(i -> i.ruleId().equals("BR-07"));
    }

    @Test
    void pdfMagicBytesRouteToPdfExtraction() {
        // %PDF- 前缀走 PDF 提取路径；junk 内容提取失败抛 ExtractionException，证明确实进入了提取分支
        assertThatThrownBy(() -> validator.validate("%PDF-junk".getBytes()))
                .isInstanceOf(ExtractionException.class);
    }
}
