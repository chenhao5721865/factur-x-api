package com.facturxapi.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EmbeddedXmlExtractorTest {

    private byte[] samplePdf() throws Exception {
        return Files.readAllBytes(Path.of("../samples/factur-x-basic.pdf"));
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
}
