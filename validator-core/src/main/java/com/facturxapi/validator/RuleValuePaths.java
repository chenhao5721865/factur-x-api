package com.facturxapi.validator;

import java.util.Map;

/**
 * 聚合定位上的失败规则 → 相对定位节点的字段路径。
 *
 * <p>路径取自 EN16931-CII-model.sch 的 param（上下文由 SVRL location 提供），
 * 只收录值本身可回显的规则；未收录的规则退回 null。
 */
final class RuleValuePaths {

    private static final Map<String, String> PATHS = Map.ofEntries(
        // Invoice 上下文（/rsm:CrossIndustryInvoice）
        Map.entry("BR-02", "rsm:ExchangedDocument/ram:ID"),
        Map.entry("BR-03", "rsm:ExchangedDocument/ram:IssueDateTime/udt:DateTimeString"),
        Map.entry("BR-04", "rsm:ExchangedDocument/ram:TypeCode"),
        Map.entry("BR-05", "rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeSettlement/ram:InvoiceCurrencyCode"),
        Map.entry("BR-06", "rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty/ram:Name"),
        Map.entry("BR-07", "rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:BuyerTradeParty/ram:Name"),
        // Document_totals 上下文（SpecifiedTradeSettlementHeaderMonetarySummation）
        Map.entry("BR-12", "ram:LineTotalAmount"),
        Map.entry("BR-13", "ram:TaxBasisTotalAmount"),
        Map.entry("BR-14", "ram:GrandTotalAmount"),
        Map.entry("BR-15", "ram:DuePayableAmount"),
        Map.entry("BR-CO-10", "ram:LineTotalAmount"),
        Map.entry("BR-CO-13", "ram:TaxBasisTotalAmount"),
        Map.entry("BR-CO-15", "ram:GrandTotalAmount"),
        Map.entry("BR-CO-16", "ram:DuePayableAmount"),
        // Invoice_Line 上下文（IncludedSupplyChainTradeLineItem）
        Map.entry("BR-21", "ram:AssociatedDocumentLineDocument/ram:LineID"),
        Map.entry("BR-22", "ram:SpecifiedLineTradeDelivery/ram:BilledQuantity"),
        Map.entry("BR-23", "ram:SpecifiedLineTradeDelivery/ram:BilledQuantity/@unitCode"),
        Map.entry("BR-24", "ram:SpecifiedLineTradeSettlement/ram:SpecifiedTradeSettlementLineMonetarySummation/ram:LineTotalAmount"),
        Map.entry("BR-25", "ram:SpecifiedTradeProduct/ram:Name"),
        Map.entry("BR-26", "ram:SpecifiedLineTradeAgreement/ram:NetPriceProductTradePrice/ram:ChargeAmount")
    );

    private RuleValuePaths() {}

    static String get(String ruleId) {
        return ruleId == null ? null : PATHS.get(ruleId);
    }
}
