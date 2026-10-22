package com.facturxapi.api.service;

import com.facturxapi.api.dto.InvoiceRequest;
import com.facturxapi.api.dto.LineItemRequest;
import com.facturxapi.api.dto.PartyRequest;
import com.facturxapi.api.dto.PaymentRequest;
import com.facturxapi.api.service.InvoiceAmountCalculator.TaxGroup;
import com.facturxapi.api.service.InvoiceAmountCalculator.Totals;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.springframework.stereotype.Service;

/**
 * 用 JDK 内置 StAX 生成 EN16931 profile 的 CII XML，镜像
 * samples/valid-en16931-cii-basic.xml 的元素顺序。不输出 xsi:schemaLocation
 *（自校验不读它）。样例中重复两次的 PaymentMeans 是冗余，只输出一次。
 */
@Service
public class XmlInvoiceGenerator {

    private static final String NS_RSM = "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100";
    private static final String NS_RAM = "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100";
    private static final String NS_UDT = "urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100";
    private static final String NS_QDT = "urn:un:unece:uncefact:data:standard:QualifiedDataType:100";

    private static final DateTimeFormatter DATE_102 = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** EN 16931 允许的行级别 VAT 类别（BT-151）。 */
    private static final java.util.Set<String> VALID_CATEGORIES =
        java.util.Set.of("S", "Z", "E", "AE", "K", "G");
    /** 需要豁免理由（BT-120）的类别：BR-E-10 / BR-AE-10 / BR-IC-10 / BR-G-10。 */
    private static final java.util.Set<String> EXEMPT_REASON_CATEGORIES =
        java.util.Set.of("E", "AE", "K", "G");

    /**
     * 类别感知的输入校验：不一致的输入在入口拒绝（400 + rule ID + 字段路径 + 修复建议），
     * 而不是拖到自校验才失败。规则依据 EN 16931：
     * S 要求税率>0（BR-S-05）；Z/E/AE/K/G 要求税率=0（BR-Z-05 等）；
     * E/AE/K/G 要求豁免理由（BR-E-10 等）；AE/K 要求买方 VAT 号（BR-AE-02 / BR-IC-02）；
     * K 要求实际交付日期（BR-IC-11）。
     */
    private static void validateCategories(InvoiceRequest req) {
        boolean needsExemptionReason = false;
        boolean needsBuyerVatId = false;
        boolean needsDeliveryDate = false;
        for (int i = 0; i < req.lines().size(); i++) {
            LineItemRequest line = req.lines().get(i);
            String cat = line.vatCategoryCode();
            String path = "lines[" + i + "]";
            if (!VALID_CATEGORIES.contains(cat)) {
                throw new IllegalArgumentException(path + ".vatCategoryCode \"" + cat
                    + "\" is not a valid EN 16931 category. Allowed: S, Z, E, AE, K, G.");
            }
            int rateSign = line.vatRate().compareTo(BigDecimal.ZERO);
            switch (cat) {
                case "S" -> {
                    if (rateSign <= 0) {
                        throw new IllegalArgumentException(path
                            + ": vatRate 0 with category \"S\" violates BR-S-05 (standard-rated lines require a VAT rate > 0). "
                            + "Either provide a non-zero vatRate, or set " + path
                            + ".vatCategoryCode to \"Z\" (zero-rated), \"E\" (exempt from VAT), "
                            + "\"AE\" (reverse charge), \"K\" (intra-community supply) or \"G\" (export outside the EU).");
                    }
                }
                case "Z" -> {
                    if (rateSign != 0) {
                        throw new IllegalArgumentException(path
                            + ": category \"Z\" (zero-rated) requires vatRate 0 (BR-Z-05). "
                            + "If this line carries VAT at " + line.vatRate().toPlainString()
                            + "%, use category \"S\" instead.");
                    }
                }
                case "E", "AE", "K", "G" -> {
                    if (rateSign != 0) {
                        throw new IllegalArgumentException(path
                            + ": category \"" + cat + "\" requires vatRate 0. "
                            + "If this line carries VAT at " + line.vatRate().toPlainString()
                            + "%, use category \"S\" instead.");
                    }
                    needsExemptionReason = true;
                    if ("AE".equals(cat) || "K".equals(cat)) {
                        needsBuyerVatId = true;
                    }
                    if ("K".equals(cat)) {
                        needsDeliveryDate = true;
                    }
                }
                default -> throw new IllegalStateException("unreachable category: " + cat);
            }
        }
        if (needsExemptionReason && isBlank(req.vatExemptionReason())) {
            throw new IllegalArgumentException(
                "vatExemptionReason is required when lines use category E, AE, K or G "
                    + "(BR-E-10 / BR-AE-10 / BR-IC-10 / BR-G-10). "
                    + "Example: \"Reverse charge — Article 196 of Council Directive 2006/112/EC\".");
        }
        if (needsBuyerVatId && isBlank(req.buyer().vatId())) {
            throw new IllegalArgumentException(
                "buyer.vatId is required when lines use category AE or K "
                    + "(BR-AE-02 / BR-IC-02: reverse charge and intra-community supply "
                    + "require both seller and buyer VAT identifiers).");
        }
        if (needsDeliveryDate && req.deliveryDate() == null) {
            throw new IllegalArgumentException(
                "deliveryDate is required when lines use category K "
                    + "(BR-IC-11: intra-community supply requires the actual delivery date, BT-72).");
        }
    }

    public byte[] generate(InvoiceRequest req) {
        // BR-CO-26：卖方须至少一个标识符（vatId 或 legalOrgId）
        if (isBlank(req.seller().vatId()) && isBlank(req.seller().legalOrgId())) {
            throw new IllegalArgumentException(
                "Seller must provide at least one of vatId or legalOrgId (BR-CO-26)");
        }
        validateCategories(req);
        Totals totals = InvoiceAmountCalculator.compute(req.lines());

        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        try {
            XMLStreamWriter w = XMLOutputFactory.newFactory().createXMLStreamWriter(out, "UTF-8");
            w.writeStartDocument("UTF-8", "1.0");

            w.writeStartElement("rsm", "CrossIndustryInvoice", NS_RSM);
            w.writeNamespace("rsm", NS_RSM);
            w.writeNamespace("ram", NS_RAM);
            w.writeNamespace("udt", NS_UDT);
            w.writeNamespace("qdt", NS_QDT);

            writeDocumentContext(w);
            writeExchangedDocument(w, req);
            writeTransaction(w, req, totals);

            w.writeEndElement(); // rsm:CrossIndustryInvoice
            w.writeEndDocument();
            w.close();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("XML generation failed", e);
        }
        return out.toByteArray();
    }

    private void writeDocumentContext(XMLStreamWriter w) throws XMLStreamException {
        w.writeStartElement("rsm", "ExchangedDocumentContext", NS_RSM);
        w.writeStartElement("ram", "GuidelineSpecifiedDocumentContextParameter", NS_RAM);
        el(w, "ID", "urn:cen.eu:en16931:2017");
        w.writeEndElement();
        w.writeEndElement();
    }

    private void writeExchangedDocument(XMLStreamWriter w, InvoiceRequest req)
            throws XMLStreamException {
        w.writeStartElement("rsm", "ExchangedDocument", NS_RSM);
        el(w, "ID", req.invoiceNumber());
        el(w, "TypeCode", req.typeCode());
        w.writeStartElement("ram", "IssueDateTime", NS_RAM);
        dateTimeString(w, req.issueDate().format(DATE_102));
        w.writeEndElement();
        if (!isBlank(req.note())) {
            w.writeStartElement("ram", "IncludedNote", NS_RAM);
            el(w, "Content", req.note());
            w.writeEndElement();
        }
        w.writeEndElement();
    }

    private void writeTransaction(XMLStreamWriter w, InvoiceRequest req, Totals totals)
            throws XMLStreamException {
        w.writeStartElement("rsm", "SupplyChainTradeTransaction", NS_RSM);
        List<LineItemRequest> lines = req.lines();
        for (int i = 0; i < lines.size(); i++) {
            writeLineItem(w, i + 1, lines.get(i), totals.lineTotals().get(i));
        }
        writeHeaderAgreement(w, req);
        boolean hasIntraCommunitySupply = lines.stream()
            .anyMatch(l -> "K".equals(l.vatCategoryCode()));
        writeHeaderDelivery(w, req, hasIntraCommunitySupply);
        writeHeaderSettlement(w, req, totals);
        w.writeEndElement();
    }

    private void writeLineItem(XMLStreamWriter w, int lineId, LineItemRequest line,
                               BigDecimal lineTotal) throws XMLStreamException {
        w.writeStartElement("ram", "IncludedSupplyChainTradeLineItem", NS_RAM);

        w.writeStartElement("ram", "AssociatedDocumentLineDocument", NS_RAM);
        el(w, "LineID", String.valueOf(lineId));
        w.writeEndElement();

        w.writeStartElement("ram", "SpecifiedTradeProduct", NS_RAM);
        el(w, "Name", line.description());
        w.writeEndElement();

        w.writeStartElement("ram", "SpecifiedLineTradeAgreement", NS_RAM);
        w.writeStartElement("ram", "NetPriceProductTradePrice", NS_RAM);
        el(w, "ChargeAmount", fmt(line.unitPrice()));
        w.writeEndElement();
        w.writeEndElement();

        w.writeStartElement("ram", "SpecifiedLineTradeDelivery", NS_RAM);
        w.writeStartElement("ram", "BilledQuantity", NS_RAM);
        w.writeAttribute("unitCode", line.unitCode());
        w.writeCharacters(fmt(line.quantity()));
        w.writeEndElement();
        w.writeEndElement();

        w.writeStartElement("ram", "SpecifiedLineTradeSettlement", NS_RAM);
        w.writeStartElement("ram", "ApplicableTradeTax", NS_RAM);
        el(w, "TypeCode", "VAT");
        el(w, "CategoryCode", line.vatCategoryCode());
        el(w, "RateApplicablePercent", fmt(line.vatRate()));
        w.writeEndElement();
        w.writeStartElement("ram", "SpecifiedTradeSettlementLineMonetarySummation", NS_RAM);
        el(w, "LineTotalAmount", fmt(lineTotal));
        w.writeEndElement();
        w.writeEndElement();

        w.writeEndElement();
    }

    private void writeHeaderAgreement(XMLStreamWriter w, InvoiceRequest req)
            throws XMLStreamException {
        w.writeStartElement("ram", "ApplicableHeaderTradeAgreement", NS_RAM);
        writeParty(w, "SellerTradeParty", req.seller());
        writeParty(w, "BuyerTradeParty", req.buyer());
        w.writeEndElement();
    }

    private void writeParty(XMLStreamWriter w, String tag, PartyRequest party)
            throws XMLStreamException {
        w.writeStartElement("ram", tag, NS_RAM);
        el(w, "Name", party.name());
        if (!isBlank(party.legalOrgId())) {
            w.writeStartElement("ram", "SpecifiedLegalOrganization", NS_RAM);
            el(w, "ID", party.legalOrgId());
            w.writeEndElement();
        }
        w.writeStartElement("ram", "PostalTradeAddress", NS_RAM);
        if (!isBlank(party.postcode())) {
            el(w, "PostcodeCode", party.postcode());
        }
        if (!isBlank(party.lineOne())) {
            el(w, "LineOne", party.lineOne());
        }
        if (!isBlank(party.city())) {
            el(w, "CityName", party.city());
        }
        el(w, "CountryID", party.countryCode());
        w.writeEndElement();
        if (!isBlank(party.vatId())) {
            w.writeStartElement("ram", "SpecifiedTaxRegistration", NS_RAM);
            w.writeStartElement("ram", "ID", NS_RAM);
            w.writeAttribute("schemeID", "VA");
            w.writeCharacters(party.vatId());
            w.writeEndElement();
            w.writeEndElement();
        }
        w.writeEndElement();
    }

    /**
     * ApplicableHeaderTradeDelivery：有 deliveryDate 才输出 ActualDeliverySupplyChainEvent
     * （BT-72）；K 类别必须输出 ShipToTradeParty 的国家码（BR-IC-12，BT-80），复用买方信息。
     */
    private void writeHeaderDelivery(XMLStreamWriter w, InvoiceRequest req, boolean intraCommunity)
            throws XMLStreamException {
        if (!intraCommunity && req.deliveryDate() == null) {
            w.writeEmptyElement("ram", "ApplicableHeaderTradeDelivery", NS_RAM);
            return;
        }
        w.writeStartElement("ram", "ApplicableHeaderTradeDelivery", NS_RAM);
        if (intraCommunity) {
            // CII-SR 禁止 ShipToTradeParty 携带法律组织/税号：只输出 Name + 国家
            w.writeStartElement("ram", "ShipToTradeParty", NS_RAM);
            el(w, "Name", req.buyer().name());
            w.writeStartElement("ram", "PostalTradeAddress", NS_RAM);
            el(w, "CountryID", req.buyer().countryCode());
            w.writeEndElement();
            w.writeEndElement();
        }
        if (req.deliveryDate() != null) {
            w.writeStartElement("ram", "ActualDeliverySupplyChainEvent", NS_RAM);
            w.writeStartElement("ram", "OccurrenceDateTime", NS_RAM);
            dateTimeString(w, req.deliveryDate().format(DATE_102));
            w.writeEndElement();
            w.writeEndElement();
        }
        w.writeEndElement();
    }

    private void writeHeaderSettlement(XMLStreamWriter w, InvoiceRequest req, Totals totals)
            throws XMLStreamException {
        PaymentRequest payment = req.payment();
        w.writeStartElement("ram", "ApplicableHeaderTradeSettlement", NS_RAM);

        if (payment != null && !isBlank(payment.paymentReference())) {
            el(w, "PaymentReference", payment.paymentReference());
        }
        el(w, "InvoiceCurrencyCode", req.currency());

        if (payment != null && !isBlank(payment.iban())) {
            w.writeStartElement("ram", "SpecifiedTradeSettlementPaymentMeans", NS_RAM);
            el(w, "TypeCode", "30");
            w.writeStartElement("ram", "PayeePartyCreditorFinancialAccount", NS_RAM);
            el(w, "IBANID", payment.iban());
            w.writeEndElement();
            w.writeEndElement();
        }

        for (TaxGroup g : totals.taxGroups()) {
            w.writeStartElement("ram", "ApplicableTradeTax", NS_RAM);
            el(w, "CalculatedAmount", fmt(g.calculatedAmount()));
            el(w, "TypeCode", "VAT");
            if (EXEMPT_REASON_CATEGORIES.contains(g.categoryCode())
                    && !isBlank(req.vatExemptionReason())) {
                el(w, "ExemptionReason", req.vatExemptionReason()); // BT-120
            }
            el(w, "BasisAmount", fmt(g.basisAmount()));
            el(w, "CategoryCode", g.categoryCode());
            el(w, "RateApplicablePercent", fmt(g.rate()));
            w.writeEndElement();
        }

        if (payment != null && payment.dueDate() != null) {
            w.writeStartElement("ram", "SpecifiedTradePaymentTerms", NS_RAM);
            w.writeStartElement("ram", "DueDateDateTime", NS_RAM);
            dateTimeString(w, payment.dueDate().format(DATE_102));
            w.writeEndElement();
            w.writeEndElement();
        }

        w.writeStartElement("ram", "SpecifiedTradeSettlementHeaderMonetarySummation", NS_RAM);
        el(w, "LineTotalAmount", fmt(totals.lineTotalAmount()));
        el(w, "TaxBasisTotalAmount", fmt(totals.taxBasisTotalAmount()));
        w.writeStartElement("ram", "TaxTotalAmount", NS_RAM);
        w.writeAttribute("currencyID", req.currency());
        w.writeCharacters(fmt(totals.taxTotalAmount()));
        w.writeEndElement();
        el(w, "GrandTotalAmount", fmt(totals.grandTotalAmount()));
        el(w, "DuePayableAmount", fmt(totals.duePayableAmount()));
        w.writeEndElement();

        w.writeEndElement();
    }

    private void dateTimeString(XMLStreamWriter w, String yyyyMMdd) throws XMLStreamException {
        w.writeStartElement("udt", "DateTimeString", NS_UDT);
        w.writeAttribute("format", "102");
        w.writeCharacters(yyyyMMdd);
        w.writeEndElement();
    }

    private void el(XMLStreamWriter w, String local, String text) throws XMLStreamException {
        w.writeStartElement("ram", local, NS_RAM);
        w.writeCharacters(text);
        w.writeEndElement();
    }

    private static String fmt(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
