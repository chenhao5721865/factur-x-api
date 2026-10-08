package com.facturxapi.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 交易方（卖方/买方）。卖方必须至少提供 vatId 或 legalOrgId 之一（BR-CO-26，
 * 由服务层显式检查）；地址除 countryCode 外均可选。
 */
public record PartyRequest(
    @NotBlank String name,                 // BT-27 / BT-44
    @NotBlank String countryCode,          // BT-40 / BT-55
    String vatId,                          // BT-31 / BT-48
    String legalOrgId,                     // BT-30
    String postcode,                       // BT-38 / BT-53
    String lineOne,                        // BT-35 / BT-50
    String city                            // BT-37 / BT-52
) {}
