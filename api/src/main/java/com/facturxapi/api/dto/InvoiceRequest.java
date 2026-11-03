package com.facturxapi.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * POST /api/generate 请求体。金额语义：单价/数量为净额（HT 不含 VAT）；
 * 总金额由服务端计算，不接受总金额字段。
 */
public record InvoiceRequest(
    @NotBlank String invoiceNumber,        // BT-1
    @NotNull LocalDate issueDate,          // BT-2
    @NotBlank String currency,             // BT-5
    String typeCode,                       // BT-3，默认 380（发票）
    @NotNull @Valid PartyRequest seller,
    @NotNull @Valid PartyRequest buyer,
    @NotEmpty @Valid List<LineItemRequest> lines,
    @Valid PaymentRequest payment,
    String note,                           // BT-22
    String vatExemptionReason,             // BT-120：E/AE/K/G 类别必填（BR-E-10 等）
    LocalDate deliveryDate                 // BT-72：K 类别必填（BR-IC-11，实际交付日期）
) {
    public InvoiceRequest {
        if (typeCode == null || typeCode.isBlank()) {
            typeCode = "380";
        }
    }
}
