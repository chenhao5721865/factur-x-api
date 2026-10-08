package com.facturxapi.api.service;

import com.facturxapi.api.dto.LineItemRequest;
import com.facturxapi.api.service.InvoiceAmountCalculator.TaxGroup;
import com.facturxapi.api.service.InvoiceAmountCalculator.Totals;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceAmountCalculatorTest {

    private static LineItemRequest line(String qty, String price, String rate) {
        return new LineItemRequest("item", new BigDecimal(qty), new BigDecimal(price),
            new BigDecimal(rate), "C62", "S");
    }

    private static LineItemRequest line(String qty, String price, String rate, String category) {
        return new LineItemRequest("item", new BigDecimal(qty), new BigDecimal(price),
            new BigDecimal(rate), "C62", category);
    }

    @Test
    void singleLine_multipliesQuantityByPrice() {
        Totals t = InvoiceAmountCalculator.compute(List.of(line("2", "9.95", "20")));

        assertThat(t.lineTotals()).containsExactly(new BigDecimal("19.90"));
        assertThat(t.lineTotalAmount()).isEqualByComparingTo("19.90");
        assertThat(t.taxBasisTotalAmount()).isEqualByComparingTo("19.90");
        assertThat(t.taxTotalAmount()).isEqualByComparingTo("3.98");
        assertThat(t.grandTotalAmount()).isEqualByComparingTo("23.88");
        assertThat(t.duePayableAmount()).isEqualByComparingTo(t.grandTotalAmount());
    }

    @Test
    void lineTotal_roundsHalfUpToScale2() {
        // 1.005 * 1 = 1.005 → HALF_UP → 1.01
        Totals t = InvoiceAmountCalculator.compute(List.of(line("1", "1.005", "20")));
        assertThat(t.lineTotals()).containsExactly(new BigDecimal("1.01"));
    }

    @Test
    void multipleRates_groupsByCategoryAndRate_basisSumsToTaxBasis_brCo13() {
        Totals t = InvoiceAmountCalculator.compute(List.of(
            line("1", "100.00", "20"),
            line("1", "50.00", "20"),
            line("2", "10.00", "5.5")));

        assertThat(t.taxGroups()).hasSize(2);
        TaxGroup g20 = t.taxGroups().get(0);
        assertThat(g20.rate()).isEqualByComparingTo("20");
        assertThat(g20.basisAmount()).isEqualByComparingTo("150.00");
        assertThat(g20.calculatedAmount()).isEqualByComparingTo("30.00");
        TaxGroup g55 = t.taxGroups().get(1);
        assertThat(g55.rate()).isEqualByComparingTo("5.5");
        assertThat(g55.basisAmount()).isEqualByComparingTo("20.00");
        assertThat(g55.calculatedAmount()).isEqualByComparingTo("1.10");

        BigDecimal basisSum = t.taxGroups().stream()
            .map(TaxGroup::basisAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(basisSum).isEqualByComparingTo(t.taxBasisTotalAmount());

        assertThat(t.taxTotalAmount()).isEqualByComparingTo("31.10");
        assertThat(t.grandTotalAmount()).isEqualByComparingTo("201.10");
    }

    @Test
    void sameRateDifferentCategory_producesSeparateGroups() {
        Totals t = InvoiceAmountCalculator.compute(List.of(
            line("1", "100.00", "0", "S"),
            line("1", "50.00", "0", "Z")));
        assertThat(t.taxGroups()).hasSize(2);
    }

    @Test
    void zeroRate_groupStillEmittedWithZeroTax() {
        Totals t = InvoiceAmountCalculator.compute(List.of(line("3", "10.00", "0")));

        assertThat(t.taxGroups()).hasSize(1);
        TaxGroup g = t.taxGroups().get(0);
        assertThat(g.basisAmount()).isEqualByComparingTo("30.00");
        assertThat(g.calculatedAmount()).isEqualByComparingTo("0.00");
        assertThat(t.taxTotalAmount()).isEqualByComparingTo("0.00");
        assertThat(t.grandTotalAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void multiLine_sameGroup_sumsWithPrecision() {
        // 0.1 × 3 行 × 7 组内相加无浮点误差
        Totals t = InvoiceAmountCalculator.compute(List.of(
            line("1", "0.1", "20"), line("1", "0.1", "20"), line("1", "0.1", "20")));
        assertThat(t.lineTotalAmount()).isEqualByComparingTo("0.30");
        assertThat(t.taxGroups().get(0).basisAmount()).isEqualByComparingTo("0.30");
    }

    @Test
    void taxRounding_halfUpOnGroupBasis() {
        // basis 10.03 × 5.5% = 0.551650 → 0.55
        Totals t = InvoiceAmountCalculator.compute(List.of(line("1", "10.03", "5.5")));
        assertThat(t.taxGroups().get(0).calculatedAmount()).isEqualByComparingTo("0.55");
    }
}
