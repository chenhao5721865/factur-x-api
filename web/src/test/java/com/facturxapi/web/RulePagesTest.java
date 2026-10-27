package com.facturxapi.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RulePages 内容守恒：防手滑写出半截页面或断链内链。 */
class RulePagesTest {

    @Test
    void everyEntryIsComplete() {
        assertThat(RulePages.all()).hasSize(10);
        for (RulePageContent c : RulePages.all()) {
            assertThat(c.ruleId()).isNotBlank();
            assertThat(c.title()).isNotBlank();
            assertThat(c.summary()).isNotBlank();
            assertThat(c.explanation()).isNotBlank();
            assertThat(c.brokenXml()).isNotBlank();
            assertThat(c.fixedXml()).isNotBlank();
        }
    }

    @Test
    void slugsAreUniqueAndLowercaseOfRuleId() {
        List<String> slugs = RulePages.all().stream().map(c -> RulePages.slugOf(c.ruleId())).toList();
        assertThat(slugs).doesNotHaveDuplicates();
        for (RulePageContent c : RulePages.all()) {
            assertThat(RulePages.slugOf(c.ruleId())).isEqualTo(c.ruleId().toLowerCase());
        }
    }

    @Test
    void relatedLinksStayInsideTheList() {
        for (RulePageContent c : RulePages.all()) {
            for (String rel : c.related()) {
                assertThat(RulePages.containsRule(rel))
                    .as("%s references unknown related rule %s", c.ruleId(), rel)
                    .isTrue();
            }
        }
    }

    @Test
    void getReturnsEntryBySlug() {
        assertThat(RulePages.get("br-co-10").ruleId()).isEqualTo("BR-CO-10");
        assertThat(RulePages.get("BR-CO-10").ruleId()).isEqualTo("BR-CO-10");
        assertThat(RulePages.get("br-99")).isNull();
    }
}
