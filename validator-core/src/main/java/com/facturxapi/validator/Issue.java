package com.facturxapi.validator;

/**
 * 单条校验结论。
 *
 * @param ruleId   规则编号（EN 16931 如 BR-07/BR-CO-16；语法层统一为 XSD）
 * @param severity 严重级别
 * @param xpath    出错位置（Schematron 为 location 表达式；XSD 层为 行:列）
 * @param message  人读描述
 */
public record Issue(String ruleId, Severity severity, String xpath, String message) {}
