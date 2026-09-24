package com.facturxapi.validator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.PDNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;

/**
 * 从 PDF（Factur-X = PDF/A-3）中提取内嵌的 factur-x.xml。
 *
 * <p>不做严格 PDF/A-3 合规检查（那需要 veraPDF 级别的重资产）——
 * 「PDF 能解析 + 有内嵌 XML 附件」即视为 Factur-X 候选，交给后续 XML 校验链定论。
 */
public final class EmbeddedXmlExtractor {

    private EmbeddedXmlExtractor() {}

    public static byte[] extractFacturXml(byte[] pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDDocumentNameDictionary names = doc.getDocumentCatalog().getNames();
            PDEmbeddedFilesNameTreeNode embeddedFiles = names == null ? null : names.getEmbeddedFiles();
            if (embeddedFiles == null) {
                throw new ExtractionException("PDF 不含内嵌附件，不是 Factur-X 发票");
            }
            List<PDComplexFileSpecification> specs = collect(embeddedFiles);
            // Factur-X 规范规定附件名为 factur-x.xml；优先精确匹配，其次任意 .xml 附件
            byte[] exact = findXml(specs, "factur-x.xml");
            if (exact != null) {
                return exact;
            }
            byte[] anyXml = specs.stream()
                    .filter(fs -> attachmentName(fs).endsWith(".xml"))
                    .filter(fs -> fs.getEmbeddedFile() != null)
                    .findFirst()
                    .map(fs -> bytes(fs.getEmbeddedFile()))
                    .orElse(null);
            if (anyXml != null) {
                return anyXml;
            }
            throw new ExtractionException("未找到内嵌 factur-x.xml");
        } catch (IOException e) {
            throw new ExtractionException("PDF 解析失败: " + e.getMessage());
        }
    }

    /** 收集名字树（含 /Kids 子树）中的全部附件描述。 */
    private static List<PDComplexFileSpecification> collect(PDNameTreeNode<PDComplexFileSpecification> node)
            throws IOException {
        List<PDComplexFileSpecification> result = new ArrayList<>();
        Map<String, PDComplexFileSpecification> nodeNames = node.getNames();
        if (nodeNames != null) {
            result.addAll(nodeNames.values());
        }
        if (node.getKids() != null) {
            for (PDNameTreeNode<PDComplexFileSpecification> kid : node.getKids()) {
                result.addAll(collect(kid));
            }
        }
        return result;
    }

    private static byte[] findXml(List<PDComplexFileSpecification> specs, String targetName) {
        return specs.stream()
                .filter(fs -> attachmentName(fs).equals(targetName))
                .filter(fs -> fs.getEmbeddedFile() != null)
                .findFirst()
                .map(fs -> bytes(fs.getEmbeddedFile()))
                .orElse(null);
    }

    private static byte[] bytes(PDEmbeddedFile embeddedFile) {
        try {
            return embeddedFile.toByteArray();
        } catch (IOException e) {
            throw new ExtractionException("读取内嵌附件失败: " + e.getMessage());
        }
    }

    /** 附件名：优先 /UF（Unicode），其次 /F，兜底名字树中的 key。 */
    private static String attachmentName(PDComplexFileSpecification fs) {
        String name = fs.getFileUnicode();
        if (name == null || name.isEmpty()) {
            name = fs.getFilename();
        }
        return (name == null ? "" : name).toLowerCase();
    }
}
