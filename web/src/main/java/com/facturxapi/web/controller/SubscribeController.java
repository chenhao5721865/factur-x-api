package com.facturxapi.web.controller;

import com.facturxapi.web.service.SubscriberService;
import com.facturxapi.web.service.SubscriberService.SubscribeResult;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SubscribeController {

    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final SubscriberService subscriberService;

    public SubscribeController(SubscriberService subscriberService) {
        this.subscriberService = subscriberService;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<String> subscribe(
            @RequestParam("email") String email,
            jakarta.servlet.http.HttpServletRequest request) {

        if (!isHtmx(request)) {
            return ResponseEntity.badRequest().body("Invalid request");
        }

        // Email format validation (regex, no extra dependency needed)
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("<div class=\"bg-red-50 border border-red-300 text-red-800 rounded-lg p-4 text-sm\">"
                        + "Format d'email invalide.</div>");
        }

        SubscribeResult result = subscriberService.subscribe(email);

        return switch (result) {
            case SUCCESS -> ResponseEntity.ok()
                    .body("<div class=\"bg-emerald-50 border border-emerald-300 text-emerald-800 rounded-lg p-4\">"
                        + "<p class=\"font-semibold\">Merci !</p>"
                        + "<p class=\"text-sm\">Vous êtes inscrit(e). Nous vous tiendrons informé(e) de l'ouverture de l'API.</p>"
                        + "</div>");
            case DUPLICATE -> ResponseEntity.ok()
                    .body("<div class=\"bg-amber-50 border border-amber-300 text-amber-800 rounded-lg p-4 text-sm\">"
                        + "Vous êtes déjà inscrit(e). Merci !</div>");
        };
    }

    private static boolean isHtmx(jakarta.servlet.http.HttpServletRequest request) {
        return "true".equals(request.getHeader("HX-Request"));
    }
}
