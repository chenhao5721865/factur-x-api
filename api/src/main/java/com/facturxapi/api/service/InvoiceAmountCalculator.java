package com.facturxapi.api.service;

import com.facturxapi.api.dto.LineItemRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯函数金额计算器：行净额 → 按 (category, rate) 分组 → 五金额链。
 * 全部金额 scale=2 HALF_UP。单价/数量语义为净额（HT 不含 VAT）。
 */
public final class InvoiceAmountCalculator {

    private static final int SCALE = 2;

    /** 头级税额分组（BT-116/117/118/119/120 一组）。 */
    public record TaxGroup(String categoryCode, BigDecimal rate,
                           BigDecimal basisAmount, BigDecimal calculatedAmount) {}

    public record Totals(List<BigDecimal> lineTotals, List<TaxGroup> taxGroups,
                         BigDecimal lineTotalAmount, BigDecimal taxBasisTotalAmount,
                         BigDecimal taxTotalAmount, BigDecimal grandTotalAmount,
                         BigDecimal duePayableAmount) {}

    private record GroupKey(String categoryCode, BigDecimal rate) {}

    private InvoiceAmountCalculator() {}

    public static Totals compute(List<LineItemRequest> lines) {
        List<BigDecimal> lineTotals = new ArrayList<>(lines.size());
        Map<GroupKey, BigDecimal> basisByGroup = new LinkedHashMap<>();

        for (LineItemRequest line : lines) {
            BigDecimal lineTotal = line.quantity().multiply(line.unitPrice())
                .setScale(SCALE, RoundingMode.HALF_UP);
            lineTotals.add(lineTotal);
            GroupKey key = new GroupKey(line.vatCategoryCode(), line.vatRate().stripTrailingZeros());
            basisByGroup.merge(key, lineTotal, BigDecimal::add);
        }

        List<TaxGroup> taxGroups = new ArrayList<>(basisByGroup.size());
        for (Map.Entry<GroupKey, BigDecimal> e : basisByGroup.entrySet()) {
            BigDecimal basis = e.getValue().setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal tax = basis.multiply(e.getKey().rate())
                .movePointLeft(2)
                .setScale(SCALE, RoundingMode.HALF_UP);
            taxGroups.add(new TaxGroup(e.getKey().categoryCode(), e.getKey().rate(), basis, tax));
        }

        BigDecimal lineTotalAmount = sum(lineTotals);
        BigDecimal taxBasisTotalAmount = lineTotalAmount;
        BigDecimal taxTotalAmount = taxGroups.stream()
            .map(TaxGroup::calculatedAmount)
            .reduce(BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP), BigDecimal::add);
        BigDecimal grandTotalAmount = taxBasisTotalAmount.add(taxTotalAmount);
        BigDecimal duePayableAmount = grandTotalAmount;

        return new Totals(lineTotals, taxGroups, lineTotalAmount, taxBasisTotalAmount,
            taxTotalAmount, grandTotalAmount, duePayableAmount);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream()
            .reduce(BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP), BigDecimal::add);
    }
}
