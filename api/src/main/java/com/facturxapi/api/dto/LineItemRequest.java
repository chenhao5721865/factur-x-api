package com.facturxapi.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.Locale;

/**
 * 发票行。unitPrice 为净单价（HT），LineID 按下标自动生成（BT-126）。
 */
public record LineItemRequest(
    @NotBlank String description,          // BT-153
    @NotNull @Positive BigDecimal quantity,        // BT-129
    @NotNull @PositiveOrZero BigDecimal unitPrice, // BT-146（净额）
    @NotNull @PositiveOrZero BigDecimal vatRate,   // BT-152
    String unitCode,                       // BT-130，默认 C62（one）
    String vatCategoryCode                 // BT-151，默认 S
) {
    public LineItemRequest {
        if (unitCode == null || unitCode.isBlank()) {
            unitCode = "C62";
        }
        if (vatCategoryCode == null || vatCategoryCode.isBlank()) {
            vatCategoryCode = "S";
        } else {
            // 归一化：生成器直接输出该值到 CategoryCode，EN 16931 码表为大写
            vatCategoryCode = vatCategoryCode.trim().toUpperCase(Locale.ROOT);
        }
    }
}
