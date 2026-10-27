package com.facturxapi.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SitemapControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void sitemap_containsStaticAndRuleUrls() throws Exception {
        mvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("https://factur-x-api.com/regles")))
                .andExpect(content().string(containsString("https://factur-x-api.com/regles/br-co-10")))
                .andExpect(content().string(containsString("https://factur-x-api.com/regles/br-07")))
                .andExpect(content().string(containsString("https://factur-x-api.com/validateur-factur-x")))
                .andExpect(content().string(containsString("https://factur-x-api.com/tarifs")))
                .andExpect(content().string(containsString("https://factur-x-api.com/docs")))
                .andExpect(content().string(containsString("<priority>0.8</priority>")))
                .andExpect(content().string(containsString("<priority>0.7</priority>")));
    }
}
