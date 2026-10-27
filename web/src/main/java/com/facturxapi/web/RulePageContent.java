package com.facturxapi.web;

import java.util.List;

/** 一条 EN 16931 业务规则的解释页内容（/regles/{slug}）。 */
public record RulePageContent(
        String ruleId,
        String title,
        String summary,
        String explanation,
        String brokenXml,
        String fixedXml,
        List<String> related) {
}
