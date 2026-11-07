package com.facturxapi.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class RulePagesControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void knownSlug_200_containsRuleIdAndTitle() throws Exception {
        mvc.perform(get("/regles/br-co-10"))
                .andExpect(status().isOk())
                .andExpect(view().name("regle"))
                .andExpect(content().string(containsString("BR-CO-10")))
                .andExpect(content().string(containsString("la somme des lignes")));
    }

    @Test
    void unknownSlug_404() throws Exception {
        mvc.perform(get("/regles/br-99")).andExpect(status().isNotFound());
    }

    @Test
    void index_200_listsAllRules() throws Exception {
        mvc.perform(get("/regles"))
                .andExpect(status().isOk())
                .andExpect(view().name("regles"))
                .andExpect(content().string(containsString("href=\"/regles/br-co-10\"")));
    }

    @Test
    void legacyEnglishIndex_301_toFrenchIndex() throws Exception {
        mvc.perform(get("/rules"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/regles"));
    }

    @Test
    void legacyEnglishRule_301_toMatchingFrenchSlug() throws Exception {
        mvc.perform(get("/rules/BR-01"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/regles/br-01"));
        mvc.perform(get("/rules/br-co-10"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/regles/br-co-10"));
    }

    @Test
    void legacyEnglishUnknownRule_301_toFrenchIndex() throws Exception {
        mvc.perform(get("/rules/BR-99"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/regles"));
    }
}
