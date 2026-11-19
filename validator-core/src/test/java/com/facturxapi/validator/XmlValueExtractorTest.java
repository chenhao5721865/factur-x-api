package com.facturxapi.validator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class XmlValueExtractorTest {

    private static final String INVOICE =
        "<rsm:CrossIndustryInvoice"
            + " xmlns:rsm=\"urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100\""
            + " xmlns:ram=\"urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100\">"
            + "<rsm:ExchangedDocument><ram:ID>  INV\n          001  </ram:ID></rsm:ExchangedDocument>"
            + "<rsm:SupplyChainTradeTransaction>"
            + "<ram:IncludedSupplyChainTradeLineItem>"
            + "<ram:AssociatedDocumentLineDocument><ram:LineID>7</ram:LineID></ram:AssociatedDocumentLineDocument>"
            + "<ram:SpecifiedTradeProduct><ram:Name>Widget</ram:Name></ram:SpecifiedTradeProduct>"
            + "<ram:SpecifiedLineTradeDelivery><ram:BilledQuantity unitCode=\"H87\">2</ram:BilledQuantity></ram:SpecifiedLineTradeDelivery>"
            + "</ram:IncludedSupplyChainTradeLineItem>"
            + "<ram:ApplicableHeaderTradeSettlement>"
            + "<ram:SpecifiedTradeSettlementHeaderMonetarySummation>"
            + "<ram:GrandTotalAmount>250.33</ram:GrandTotalAmount>"
            + "<ram:DuePayableAmount>-250.33</ram:DuePayableAmount>"
            + "</ram:SpecifiedTradeSettlementHeaderMonetarySummation>"
            + "</ram:ApplicableHeaderTradeSettlement>"
            + "</rsm:SupplyChainTradeTransaction>"
            + "</rsm:CrossIndustryInvoice>";

    private static final String ROOT = "/rsm:CrossIndustryInvoice";
    private static final String TOTALS = ROOT
        + "/rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeSettlement"
        + "/ram:SpecifiedTradeSettlementHeaderMonetarySummation";
    private static final String LINE = ROOT
        + "/rsm:SupplyChainTradeTransaction/ram:IncludedSupplyChainTradeLineItem";

    private static Document doc(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder()
            .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void leafElement_returnsWhitespaceNormalizedValue() throws Exception {
        Document d = doc(INVOICE);
        assertThat(XmlValueExtractor.extract(d, ROOT + "/rsm:ExchangedDocument/ram:ID", null))
            .isEqualTo("INV 001");
    }

    @Test
    void attribute_returnsValue() throws Exception {
        Document d = doc(INVOICE);
        assertThat(XmlValueExtractor.extract(d, LINE + "/ram:SpecifiedLineTradeDelivery/ram:BilledQuantity/@unitCode", null))
            .isEqualTo("H87");
    }

    @Test
    void aggregateWithoutRuleMapping_returnsNull() throws Exception {
        Document d = doc(INVOICE);
        assertThat(XmlValueExtractor.extract(d, ROOT, null)).isNull();
    }

    @Test
    void aggregateWithRuleMapping_resolvesLeaf() throws Exception {
        Document d = doc(INVOICE);
        assertThat(XmlValueExtractor.extract(d, TOTALS, "BR-CO-16")).isEqualTo("-250.33");
    }

    @Test
    void invoiceLevelRuleMapping_resolvesLeaf() throws Exception {
        Document d = doc(INVOICE);
        assertThat(XmlValueExtractor.extract(d, ROOT, "BR-02")).isEqualTo("INV 001");
    }

    @Test
    void lineLevelRuleMapping_resolvesLeafOfFailingLine() throws Exception {
        Document d = doc(INVOICE);
        assertThat(XmlValueExtractor.extract(d, LINE, "BR-21")).isEqualTo("7");
        assertThat(XmlValueExtractor.extract(d, LINE, "BR-22")).isEqualTo("2");
        assertThat(XmlValueExtractor.extract(d, LINE, "BR-23")).isEqualTo("H87");
        assertThat(XmlValueExtractor.extract(d, LINE, "BR-25")).isEqualTo("Widget");
    }

    @Test
    void missingField_returnsNull() throws Exception {
        Document d = doc(INVOICE);
        assertThat(XmlValueExtractor.extract(d, ROOT, "BR-07")).isNull();
    }

    @Test
    void multipleMatches_returnsNull() throws Exception {
        Document d = doc(INVOICE);
        assertThat(XmlValueExtractor.extract(d, ROOT + "/*", null)).isNull();
    }

    @Test
    void nonXpathLocation_returnsNull() throws Exception {
        Document d = doc(INVOICE);
        assertThat(XmlValueExtractor.extract(d, "3:14", null)).isNull();
    }

    @Test
    void longValue_isTruncated() throws Exception {
        String longName = "a".repeat(130);
        Document d = doc(INVOICE.replace("Widget", longName));
        String value = XmlValueExtractor.extract(d, LINE, "BR-25");
        assertThat(value).hasSize(121).endsWith("…");
        assertThat(value).startsWith(longName.substring(0, 120));
    }
}
