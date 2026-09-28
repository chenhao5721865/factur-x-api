package com.facturxapi.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.junit.jupiter.api.Test;

class EmbeddedXmlExtractorTest {

    private byte[] samplePdf() throws Exception {
        return Files.readAllBytes(Path.of("../samples/factur-x-basic.pdf"));
    }

    /** PDFBox 现场生成最小 PDF（默认无内嵌文件名树），用于分支覆盖。 */
    private byte[] emptyPdf() throws Exception {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** PDFBox 现场生成仅带一个非 .xml 附件（attach.txt）的 PDF。 */
    private byte[] pdfWithTxtAttachment() throws Exception {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDComplexFileSpecification fs = new PDComplexFileSpecification();
            fs.setFile("attach.txt");
            PDEmbeddedFile ef = new PDEmbeddedFile(doc, new ByteArrayInputStream("hello".getBytes()));
            fs.setEmbeddedFile(ef);
            PDEmbeddedFilesNameTreeNode efTree = new PDEmbeddedFilesNameTreeNode();
            efTree.setNames(Map.of("attach.txt", fs));
            PDDocumentNameDictionary names = new PDDocumentNameDictionary(doc.getDocumentCatalog());
            names.setEmbeddedFiles(efTree);
            doc.getDocumentCatalog().setNames(names);
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** PDFBox 现场生成 /F 与 /UF 不一致的 PDF：/F=attach.txt，/UF=factur-x.xml。 */
    private byte[] pdfWithDivergentFAndUf() throws Exception {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDComplexFileSpecification fs = new PDComplexFileSpecification();
            fs.setFile("attach.txt");          // /F 指向 .txt 名
            fs.setFileUnicode("factur-x.xml"); // /UF 指向 factur-x.xml
            String cii = "<rsm:CrossIndustryInvoice xmlns:rsm=\"urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100\"/>";
            PDEmbeddedFile ef = new PDEmbeddedFile(doc, new ByteArrayInputStream(cii.getBytes()));
            fs.setEmbeddedFile(ef);
            PDEmbeddedFilesNameTreeNode efTree = new PDEmbeddedFilesNameTreeNode();
            efTree.setNames(Map.of("attach.txt", fs));
            PDDocumentNameDictionary names = new PDDocumentNameDictionary(doc.getDocumentCatalog());
            names.setEmbeddedFiles(efTree);
            doc.getDocumentCatalog().setNames(names);
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void ufTakesPrecedenceOverFDivergentName() throws Exception {
        // /F=attach.txt、/UF=factur-x.xml：附件名判定必须取 /UF，提取成功且内容是 CII XML；
        // 若错误地用 /F 判名，会因非 .xml 走"未找到"分支而失败
        byte[] xml = EmbeddedXmlExtractor.extractFacturXml(pdfWithDivergentFAndUf());
        assertThat(new String(xml)).contains("CrossIndustryInvoice");
    }

    @Test
    void extractsEmbeddedFacturXmlFromPdf() throws Exception {
        byte[] xml = EmbeddedXmlExtractor.extractFacturXml(samplePdf());
        assertThat(new String(xml)).contains("CrossIndustryInvoice");
    }

    @Test
    void rejectsPdfWithoutEmbeddedXml() throws Exception {
        // 最小化的"空内容 PDF"：%PDF-1.4 头 + EOF，足够让 PDFBox 报格式错误或无附件
        byte[] junk = "%PDF-1.4\n%%EOF\n".getBytes();
        assertThatThrownBy(() -> EmbeddedXmlExtractor.extractFacturXml(junk))
            .isInstanceOf(ExtractionException.class);
    }

    @Test
    void rejectsParseablePdfWithoutAttachment() throws Exception {
        // 能正常解析但没有内嵌文件名树 → 应走"无附件"分支而非解析失败分支
        assertThatThrownBy(() -> EmbeddedXmlExtractor.extractFacturXml(emptyPdf()))
            .isInstanceOf(ExtractionException.class)
            .hasMessageContaining("不含内嵌附件");
    }

    @Test
    void rejectsPdfWithOnlyNonXmlAttachment() throws Exception {
        // 有附件但只有 attach.txt（非 .xml）→ 应走"未找到 .xml"分支
        assertThatThrownBy(() -> EmbeddedXmlExtractor.extractFacturXml(pdfWithTxtAttachment()))
            .isInstanceOf(ExtractionException.class)
            .hasMessageContaining("未找到");
    }
}
