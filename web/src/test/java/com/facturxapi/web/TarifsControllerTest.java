package com.facturxapi.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TarifsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void tarifsPage_renders200_inFrench() throws Exception {
        mockMvc.perform(get("/tarifs"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("lang=\"fr\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Étape 1 — Obtenir votre clé API")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("9 $")))
            // 无 checkout URL → Souscrire 按钮渲染为 disabled
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "id=\"subscribe-btn\" disabled")));
    }

    @Test
    void merciParam_showsThankYouBanner() throws Exception {
        mockMvc.perform(get("/tarifs").param("merci", "1"))
            .andExpect(status().isOk())
            .andExpect(content().string(
                org.hamcrest.Matchers.containsString("Merci pour votre abonnement")));
    }
}
