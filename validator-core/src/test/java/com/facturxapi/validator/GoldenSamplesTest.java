package com.facturxapi.validator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Golden 回归：samples/ 下 valid-*.xml 必须全过，invalid-*.xml 必须报错。
 * 新增样例自动纳入——扩展样例集无需改本测试。
 */
class GoldenSamplesTest {

    static final Path SAMPLES = Path.of("../samples");
    // spike 实测的主 schema 与 schematron 入口（与 XmlRuleValidatorTest 一致）
    static final Path XSD_MAIN = Path.of("../samples/schema/cii/CrossIndustryInvoice_100pD16B.xsd");
    static final Path SCH = Path.of("../samples/schema/schematron/EN16931-CII-validation.sch");

    static XmlRuleValidator validator;

    @BeforeAll
    static void init() {
        // 关键：构造即编译缓存（XSD+Schematron 编译是重操作），
        // 必须全类共享一个实例，绝不能每个样例 new 一个
        validator = new XmlRuleValidator(XSD_MAIN, SCH);
    }

    static Stream<Path> xmlSamples() throws IOException {
        try (Stream<Path> s = Files.list(SAMPLES)) {
            return s.filter(p -> {
                String n = p.getFileName().toString();
                return (n.startsWith("valid-") || n.startsWith("invalid-")) && n.endsWith(".xml");
            }).toList().stream();
        }
    }

    @ParameterizedTest
    @MethodSource("xmlSamples")
    void golden(Path sample) {
        ValidationResult r = validator.validate(readAll(sample));
        if (sample.getFileName().toString().startsWith("valid-")) {
            assertThat(r.valid()).as("%s 应通过", sample.getFileName()).isTrue();
        } else {
            assertThat(r.valid()).as("%s 应报错", sample.getFileName()).isFalse();
        }
    }

    private static byte[] readAll(Path p) {
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
