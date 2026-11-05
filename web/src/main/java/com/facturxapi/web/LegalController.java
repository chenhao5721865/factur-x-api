package com.facturxapi.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Pages légales (français) + alias anglais /privacy et /terms. */
@Controller
public class LegalController {

    @GetMapping("/mentions-legales")
    public String mentionsLegales() {
        return "mentions-legales";
    }

    @GetMapping("/confidentialite")
    public String confidentialite() {
        return "confidentialite";
    }

    @GetMapping("/cgv")
    public String cgv() {
        return "cgv";
    }

    @GetMapping("/privacy")
    public String privacyAlias() {
        return "confidentialite";
    }

    @GetMapping("/terms")
    public String termsAlias() {
        return "cgv";
    }
}
