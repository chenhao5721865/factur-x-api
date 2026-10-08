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

    public byte[] generate(InvoiceRequest req) {
        // BR-CO-26：卖方须至少一个标识符（vatId 或 legalOrgId）
        if (isBlank(req.seller().vatId()) && isBlank(req.seller().legalOrgId())) {
            throw new IllegalArgumentException(
                "Seller must provide at least one of vatId or legalOrgId (BR-CO-26)");
        }
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
        w.writeEmptyElement("ram", "ApplicableHeaderTradeDelivery", NS_RAM);
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
