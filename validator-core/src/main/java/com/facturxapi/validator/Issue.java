package com.facturxapi.validator;

/**
 * 单条校验结论。
 *
 * @param ruleId   规则编号（EN 16931 如 BR-07/BR-CO-16；语法层统一为 XSD）
 * @param severity 严重级别
 * @param xpath    出错位置（Schematron 为 location 表达式；XSD 层为 行:列）
 * @param message  人读描述
 * @param value    出错字段在内嵌 XML 中的实际值；定位是聚合节点、字段缺失或 XSD 错误时为 null
 */
public record Issue(String ruleId, Severity severity, String xpath, String message, String value) {

    public Issue(String ruleId, Severity severity, String xpath, String message) {
        this(ruleId, severity, xpath, message, null);
    }
}
