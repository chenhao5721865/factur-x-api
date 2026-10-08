package com.facturxapi.api.dto;

import java.time.LocalDate;

/** 支付信息，整体可选。 */
public record PaymentRequest(
    String iban,                           // BT-91
    String paymentReference,               // BT-83
    LocalDate dueDate                      // BT-9
) {}
