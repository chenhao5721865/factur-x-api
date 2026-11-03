package com.facturxapi.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FaviconTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void favicon_isServed() throws Exception {
        mockMvc.perform(get("/favicon.ico"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("image/")));
    }

    @Test
    void pages_declareFavicon() throws Exception {
        mockMvc.perform(get("/docs"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("href=\"/favicon.ico\"")));
    }
}
