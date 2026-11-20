package com.facturxapi.web.controller;

import com.facturxapi.web.service.SubscribeRateLimiter;
import com.facturxapi.web.service.SubscriberService;
import com.facturxapi.web.service.SubscriberService.SubscribeResult;
import com.facturxapi.web.service.TurnstileVerifier;

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
    private final SubscribeRateLimiter rateLimiter;
    private final TurnstileVerifier turnstileVerifier;

    public SubscribeController(SubscriberService subscriberService, SubscribeRateLimiter rateLimiter,
                               TurnstileVerifier turnstileVerifier) {
        this.subscriberService = subscriberService;
        this.rateLimiter = rateLimiter;
        this.turnstileVerifier = turnstileVerifier;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<String> subscribe(
            @RequestParam("email") String email,
            @RequestParam(value = "cf-turnstile-response", required = false) String turnstileToken,
            jakarta.servlet.http.HttpServletRequest request) {

        if (!isHtmx(request)) {
            return ResponseEntity.badRequest().body("Invalid request");
        }

        String ip = SubscribeRateLimiter.clientIp(
                request.getHeader("X-Forwarded-For"), request.getRemoteAddr());
        // 422 而不是 429：htmx 默认只替换 2xx/422，429 用户看不到提示
        if (!rateLimiter.allow(ip)) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body("<div class=\"bg-red-50 border border-red-300 text-red-800 rounded-lg p-4 text-sm\">"
                        + "Trop de tentatives. Réessayez dans une heure.</div>");
        }

        if (!turnstileVerifier.verify(turnstileToken, ip)) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body("<div class=\"bg-red-50 border border-red-300 text-red-800 rounded-lg p-4 text-sm\">"
                        + "Vérification anti-robot échouée. Rechargez la page et réessayez.</div>");
        }

        // Email format validation (regex, no extra dependency needed)
        // 422 (not 400): htmx swaps 422 responses into the DOM by default; 4xx others are ignored.
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
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
