package com.facturxapi.web.controller;

import com.facturxapi.web.service.SubscribeRateLimiter;
import com.facturxapi.web.service.SubscriberService;
import com.facturxapi.web.service.SubscriberService.SubscribeResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubscribeController.class)
class SubscribeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubscriberService subscriberService;

    @MockBean
    private SubscribeRateLimiter rateLimiter;

    @BeforeEach
    void allowByDefault() {
        when(rateLimiter.allow(anyString())).thenReturn(true);
    }

    @Test
    void subscribe_success_returns_200() throws Exception {
        when(subscriberService.subscribe("new@example.com")).thenReturn(SubscribeResult.SUCCESS);

        mockMvc.perform(post("/subscribe")
                        .header("HX-Request", "true")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "new@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Merci")));
    }

    @Test
    void subscribe_duplicate_returns_200() throws Exception {
        when(subscriberService.subscribe("existing@example.com")).thenReturn(SubscribeResult.DUPLICATE);

        mockMvc.perform(post("/subscribe")
                        .header("HX-Request", "true")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "existing@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("déjà inscrit")));
    }

    @Test
    void subscribe_invalid_email_returns_422() throws Exception {
        mockMvc.perform(post("/subscribe")
                        .header("HX-Request", "true")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "not-an-email"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("invalide")));
    }

    @Test
    void rateLimited_returns_422_with_french_message() throws Exception {
        when(rateLimiter.allow(anyString())).thenReturn(false);

        mockMvc.perform(post("/subscribe")
                        .header("HX-Request", "true")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "new@example.com"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("Trop de tentatives")));

        verify(subscriberService, never()).subscribe(anyString());
    }

    @Test
    void subscribe_without_htmx_returns_400() throws Exception {
        mockMvc.perform(post("/subscribe")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "test@example.com"))
                .andExpect(status().isBadRequest());
    }
}
