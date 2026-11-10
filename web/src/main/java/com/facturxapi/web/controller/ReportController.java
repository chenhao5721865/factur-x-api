package com.facturxapi.web.controller;

import com.facturxapi.web.service.FailureReport;
import com.facturxapi.web.service.ReportMailClient;
import com.facturxapi.web.service.ReportRateLimiter;
import com.facturxapi.web.service.SubscriberService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;

/** 验证失败后「把报告发到邮箱」：只发规则清单，不保存发票文件；可选加入资讯列表。 */
@Controller
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int MAX_RULE_IDS = 50;

    private final ReportMailClient mailClient;
    private final ReportRateLimiter rateLimiter;
    private final SubscriberService subscriberService;

    public ReportController(ReportMailClient mailClient, ReportRateLimiter rateLimiter,
                            SubscriberService subscriberService) {
        this.mailClient = mailClient;
        this.rateLimiter = rateLimiter;
        this.subscriberService = subscriberService;
    }

    @PostMapping("/validateur-factur-x/rapport")
    public ResponseEntity<String> send(
            @RequestParam("email") String email,
            @RequestParam(name = "ruleIds", required = false) String ruleIds,
            @RequestParam(name = "subscribe", required = false) String subscribe,
            HttpServletRequest request) {

        if (!isHtmx(request)) {
            return ResponseEntity.badRequest().body("Invalid request");
        }
        if (!rateLimiter.allowIp(clientIp(request))) {
            return message(HttpStatus.UNPROCESSABLE_ENTITY,
                "Trop de demandes depuis cette connexion. Réessayez dans une heure.");
        }

        String to = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(to).matches()) {
            return message(HttpStatus.UNPROCESSABLE_ENTITY, "Format d'email invalide.");
        }

        Optional<String> report = FailureReport.render(splitRuleIds(ruleIds));
        if (report.isEmpty()) {
            return message(HttpStatus.UNPROCESSABLE_ENTITY,
                "Aucune erreur à envoyer : relancez d'abord la vérification de votre facture.");
        }
        if (!mailClient.isConfigured()) {
            return message(HttpStatus.UNPROCESSABLE_ENTITY,
                "L'envoi par e-mail est momentanément indisponible.");
        }
        if (!rateLimiter.tryAcquireEmail(to)) {
            return message(HttpStatus.UNPROCESSABLE_ENTITY,
                "Un rapport vient déjà d'être envoyé à cette adresse. Réessayez dans quelques minutes.");
        }

        try {
            mailClient.sendReport(to, report.get());
        } catch (RuntimeException e) {
            log.warn("report: send failed for {}: {}", to, e.getClass().getSimpleName());
            return message(HttpStatus.UNPROCESSABLE_ENTITY,
                "L'envoi a échoué. Réessayez dans un instant.");
        }

        boolean subscribed = isChecked(subscribe);
        if (subscribed) {
            subscriberService.subscribe(to);
        }
        return ok(to, subscribed);
    }

    private static ResponseEntity<String> ok(String email, boolean subscribed) {
        String extra = subscribed
            ? "<p class=\"text-xs mt-1\">Vous êtes aussi inscrit(e) aux conseils de correction.</p>"
            : "";
        return ResponseEntity.ok()
            .body("<div class=\"bg-emerald-50 border border-emerald-300 text-emerald-800 rounded-lg p-4 text-sm\">"
                + "<p class=\"font-semibold\">Rapport envoyé</p>"
                + "<p>Le rapport a été envoyé à <strong>" + HtmlUtils.htmlEscape(email) + "</strong>. "
                + "Pensez à vérifier vos spams.</p>"
                + extra
                + "</div>");
    }

    private static ResponseEntity<String> message(HttpStatus status, String text) {
        return ResponseEntity.status(status)
            .body("<div class=\"bg-red-50 border border-red-300 text-red-800 rounded-lg p-4 text-sm\">"
                + text + "</div>");
    }

    private static List<String> splitRuleIds(String ruleIds) {
        if (ruleIds == null || ruleIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(ruleIds.split(","))
            .limit(MAX_RULE_IDS)
            .toList();
    }

    private static boolean isChecked(String value) {
        return "on".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static boolean isHtmx(HttpServletRequest request) {
        return "true".equals(request.getHeader("HX-Request"));
    }
}
