package com.facturxapi.api.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.facturxapi.validator.FacturxValidator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 规则资产以文件 Path 构造 validator；fat JAR 里 classpath 资源不是文件，
 * 启动时解压到临时目录（保留目录结构）再构造。本地 spring-boot:run 与
 * Docker fat JAR 走同一条路径。
 */
@Configuration
public class ValidatorConfig {

    private static final String XSD_MAIN = "schema/cii/CrossIndustryInvoice_100pD16B.xsd";
    private static final String SCH_MAIN = "schema/schematron/EN16931-CII-validation.sch";

    /** 4 XSD + 6 SCH；规则资产增删需同步此清单。 */
    private static final List<String> RULE_FILES = List.of(
        "schema/cii/CrossIndustryInvoice_100pD16B.xsd",
        "schema/cii/CrossIndustryInvoice_QualifiedDataType_100pD16B.xsd",
        "schema/cii/CrossIndustryInvoice_ReusableAggregateBusinessInformationEntity_100pD16B.xsd",
        "schema/cii/CrossIndustryInvoice_UnqualifiedDataType_100pD16B.xsd",
        "schema/schematron/EN16931-CII-validation.sch",
        "schema/schematron/CII/EN16931-CII-model.sch",
        "schema/schematron/CII/EN16931-CII-syntax.sch",
        "schema/schematron/abstract/EN16931-CII-model.sch",
        "schema/schematron/abstract/EN16931-CII-syntax.sch",
        "schema/schematron/codelist/EN16931-CII-codes.sch"
    );

    @Bean
    public FacturxValidator facturxValidator() {
        try {
            Path schemaRoot = Files.createTempDirectory("facturx-schema");
            for (String relPath : RULE_FILES) {
                Path target = schemaRoot.resolve(relPath);
                Files.createDirectories(target.getParent());
                try (InputStream in = ValidatorConfig.class
                        .getClassLoader().getResourceAsStream(relPath)) {
                    if (in == null) {
                        throw new IllegalStateException("classpath 规则资产缺失: " + relPath);
                    }
                    Files.copy(in, target);
                }
            }
            return new FacturxValidator(schemaRoot.resolve(XSD_MAIN), schemaRoot.resolve(SCH_MAIN));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("FacturxValidator 初始化失败", e);
        }
    }
}
