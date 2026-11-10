package com.facturxapi.web.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FailureReportTest {

    @Test
    void rendersKnownRulesWithExplanationPages() {
        String text = FailureReport.render(List.of("BR-07", "BR-CO-10")).orElseThrow();

        assertThat(text).contains("2 points à corriger");
        assertThat(text).contains("BR-07 : ").contains("nom de l'acheteur");
        assertThat(text).contains("https://factur-x-api.com/regles/br-07");
        assertThat(text).contains("https://factur-x-api.com/regles/br-co-10");
    }

    @Test
    void singleRule_usesSingularWording() {
        String text = FailureReport.render(List.of("BR-07")).orElseThrow();

        assertThat(text).contains("1 point à corriger");
    }

    @Test
    void ignoresUnknownDuplicatesAndMarkup() {
        String text = FailureReport.render(List.of("BR-07", "br-07", "FAKE-99", "<script>"))
            .orElseThrow();

        assertThat(text).contains("1 point à corriger");
        assertThat(text).doesNotContain("FAKE-99").doesNotContain("<script>");
    }

    @Test
    void ruleWithoutPage_hasNoLink() {
        String text = FailureReport.render(List.of("BR-04")).orElseThrow();

        assertThat(text).contains("BR-04 : ");
        assertThat(text).doesNotContain("/regles/br-04");
    }

    @Test
    void emptyWhenNoKnownRule() {
        assertThat(FailureReport.render(List.of("FAKE-99"))).isEmpty();
        assertThat(FailureReport.render(List.of())).isEmpty();
    }
}
