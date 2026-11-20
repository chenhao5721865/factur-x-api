package com.facturxapi.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.turnstile.site-key=1x00000000000000000000AA")
@AutoConfigureMockMvc
class TurnstilePageTest {

    private static final String SITE_KEY = "1x00000000000000000000AA";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void tarifs_rendersTurnstileWidget() throws Exception {
        mockMvc.perform(get("/tarifs"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(
                "https://challenges.cloudflare.com/turnstile/v0/api.js")))
            .andExpect(content().string(containsString("class=\"cf-turnstile")))
            .andExpect(content().string(containsString("data-sitekey=\"" + SITE_KEY + "\"")));
    }

    @Test
    void validateur_rendersTurnstileWidget_onSubscribeForm() throws Exception {
        mockMvc.perform(get("/validateur-factur-x"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(
                "https://challenges.cloudflare.com/turnstile/v0/api.js")))
            .andExpect(content().string(containsString("id=\"subscribe-form\"")))
            .andExpect(content().string(containsString("data-sitekey=\"" + SITE_KEY + "\"")));
    }
}
