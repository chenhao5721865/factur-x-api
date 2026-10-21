package com.facturxapi.web;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 法语工具页烟囱测试：页面渲染、校验路径（合规/不合规/PDF/422/400）、重定向。
 * 样例取自 samples/ 目录（golden 测试同一事实源）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ValidateurControllerTest {

    private static final Path SAMPLES = Path.of("..", "samples");

    @Autowired
    private MockMvc mvc;

    private static byte[] readSample(String name) throws Exception {
        return Files.readAllBytes(SAMPLES.resolve(name));
    }

    /** htmx 请求构造器（带 HX-Request 头），file part 名固定为 file。 */
    private static MockMultipartHttpServletRequestBuilder htmxUpload(String filename, byte[] content) {
        // .header() 的返回类型是父类 MockHttpServletRequestBuilder，需强转回 multipart 构建器才能再调 .file()
        return ((MockMultipartHttpServletRequestBuilder) multipart("/validateur-factur-x")
                .header("HX-Request", "true"))
                .file(new MockMultipartFile("file", filename, "application/octet-stream", content));
    }

    @Test
    void page_200_french_with_keyword() throws Exception {
        mvc.perform(get("/validateur-factur-x"))
                .andExpect(status().isOk())
                .andExpect(view().name("validateur"))
                .andExpect(content().string(containsString("lang=\"fr\"")))
                .andExpect(content().string(containsString("Vos factures sont-elles conformes au format Factur-X")));
    }

    @Test
    void validXml_200_green() throws Exception {
        mvc.perform(htmxUpload("valid.xml", readSample("valid-en16931-cii-basic.xml")))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/resultat :: resultat"))
                .andExpect(content().string(containsString("Facture conforme")))
                .andExpect(model().attributeExists("result"));
    }

    @Test
    void invalidXml_200_withBr07AndFrench() throws Exception {
        mvc.perform(htmxUpload("invalid.xml", readSample("invalid-missing-buyer.xml")))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/resultat :: resultat"))
                .andExpect(content().string(containsString("Facture non conforme")))
                .andExpect(content().string(containsString("BR-07")))
                // Thymeleaf 会把撇号转义为 &#39;，断言用未被转义的片段
                .andExpect(content().string(containsString("le nom de l")))
                .andExpect(model().attributeExists("result"));
    }

    @Test
    void realPdf_200() throws Exception {
        mvc.perform(htmxUpload("facture.pdf", readSample("factur-x-basic.pdf")))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/resultat :: resultat"))
                .andExpect(content().string(containsString("Facture conforme")));
    }

    @Test
    void fakePdf_noEmbeddedXml_422_french() throws Exception {
        byte[] fake = ("%PDF-1.4\njunk body without embedded xml\n%%EOF\n")
                .getBytes(StandardCharsets.US_ASCII);
        mvc.perform(htmxUpload("fake.pdf", fake))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("fragments/resultat :: erreur"))
                .andExpect(content().string(containsString("facture Factur-X")));
    }

    @Test
    void plainTextGarbage_400() throws Exception {
        mvc.perform(htmxUpload("junk.txt", "hello".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("fragments/resultat :: erreur"))
                .andExpect(content().string(containsString("Format non support")));
    }

    @Test
    void emptyFile_400() throws Exception {
        mvc.perform(htmxUpload("empty.xml", new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("fragments/resultat :: erreur"));
    }

    @Test
    void home_redirectsToValidateur() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/validateur-factur-x"));
    }
}
