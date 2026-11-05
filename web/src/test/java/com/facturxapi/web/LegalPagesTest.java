package com.facturxapi.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LegalPagesTest {

    private static final String SUPPORT_EMAIL = "543843499@qq.com";

    @Autowired
    private MockMvc mvc;

    @Test
    void legalPages_areServed_withSupportEmailAndFooterLinks() throws Exception {
        for (String path : new String[]{"/mentions-legales", "/confidentialite", "/cgv"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(SUPPORT_EMAIL)))
                    .andExpect(content().string(containsString("href=\"/mentions-legales\"")))
                    .andExpect(content().string(containsString("href=\"/confidentialite\"")))
                    .andExpect(content().string(containsString("href=\"/cgv\"")));
        }
    }

    @Test
    void englishAliases_serveTheSameContent() throws Exception {
        mvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Politique de confidentialité")));

        mvc.perform(get("/terms"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Conditions générales de vente")));
    }

    @Test
    void existingPages_linkToLegalPagesAndShowSupportEmail() throws Exception {
        for (String path : new String[]{"/docs", "/tarifs", "/regles", "/validateur-factur-x"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("href=\"/mentions-legales\"")))
                    .andExpect(content().string(containsString(SUPPORT_EMAIL)));
        }
    }
}
