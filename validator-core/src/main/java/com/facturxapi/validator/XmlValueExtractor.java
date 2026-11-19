package com.facturxapi.validator;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 从已解析的发票 DOM 中取「出错字段的实际值」。
 *
 * <p>只回显叶子元素或属性：Schematron 的 location 大多落在聚合/根节点上，
 * 直接取 textContent 会把整张发票拼出来。聚合节点再按 ruleId 查相对路径取一次。
 * 字段缺失、多节点命中、表达式非法（XSD 的行:列）一律返回 null。
 */
final class XmlValueExtractor {

    private static final int MAX_LENGTH = 120;

    private static final NamespaceContext NS = new NamespaceContext() {
        private final Map<String, String> prefixes = Map.of(
            "rsm", "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100",
            "ram", "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100",
            "qdt", "urn:un:unece:uncefact:data:standard:QualifiedDataType:100",
            "udt", "urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100",
            "xsi", "http://www.w3.org/2001/XMLSchema-instance");

        @Override
        public String getNamespaceURI(String prefix) {
            String uri = prefixes.get(prefix);
            return uri != null ? uri : XMLConstants.NULL_NS_URI;
        }

        @Override
        public String getPrefix(String namespaceURI) {
            return null;
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceURI) {
            return Collections.emptyIterator();
        }
    };

    private XmlValueExtractor() {}

    static String extract(Document doc, String location, String ruleId) {
        Node context = evalSingle(doc, location);
        if (context == null) {
            return null;
        }
        String direct = leafValue(context);
        if (direct != null) {
            return direct;
        }
        return leafValue(evalSingle(context, RuleValuePaths.get(ruleId)));
    }

    /** 只接受恰好一个节点命中，避免把多行/多组的首条误当成该规则的字段值。 */
    private static Node evalSingle(Object contextNode, String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            XPath xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(NS);
            NodeList nodes = (NodeList) xpath.evaluate(expression, contextNode, XPathConstants.NODESET);
            return nodes.getLength() == 1 ? nodes.item(0) : null;
        } catch (Exception e) {
            // location 不一定是合法 XPath（XSD 的 行:列、空串）；取不到值不影响校验结论
            return null;
        }
    }

    private static String leafValue(Node node) {
        if (node == null) {
            return null;
        }
        String raw;
        if (node.getNodeType() == Node.ATTRIBUTE_NODE || node.getNodeType() == Node.TEXT_NODE) {
            raw = node.getNodeValue();
        } else if (node.getNodeType() == Node.ELEMENT_NODE && !hasElementChild(node)) {
            raw = node.getTextContent();
        } else {
            return null;
        }
        return clean(raw);
    }

    private static boolean hasElementChild(Node node) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                return true;
            }
        }
        return false;
    }

    private static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.replaceAll("[\\p{Cntrl}&&[^\\t\\n\\r]]", "")
            .replaceAll("\\s+", " ")
            .trim();
        if (value.isEmpty()) {
            return null;
        }
        return value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) + "…" : value;
    }
}
