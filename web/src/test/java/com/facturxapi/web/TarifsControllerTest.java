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
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Étape 2 — Testez en 30 secondes")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/validate")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "/downloads/valid-en16931-cii-basic.xml")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/docs\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Étape 3 — Passer en Pro")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("9 €")))
            // checkout 走服务端 POST /api/checkout；Souscrire 在拿到 key 前保持 disabled
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "id=\"subscribe-btn\" disabled")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("fetch('/api/checkout'")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("https://www.creem.io")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("LemonSqueezy"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("CHECKOUT_URL"))));
    }

    @Test
    void merciParam_showsThankYouBanner() throws Exception {
        mockMvc.perform(get("/tarifs").param("merci", "1"))
            .andExpect(status().isOk())
            .andExpect(content().string(
                org.hamcrest.Matchers.containsString("Merci pour votre abonnement")));
    }
}
