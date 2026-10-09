package com.facturxapi.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 定价页 /tarifs（法语）。领 key 走公开端点 POST /api/keys（纯 fetch），
 * Souscrire 跳 LemonSqueezy checkout 并透传 custom[api_key]/email，
 * webhook 侧凭 custom_data 归属付费。
 */
@Controller
public class TarifsController {

    private final String checkoutUrl;

    public TarifsController(
            @Value("${app.lemonsqueezy.checkout-url:}") String checkoutUrl) {
        this.checkoutUrl = checkoutUrl == null ? "" : checkoutUrl.trim();
    }

    @GetMapping("/tarifs")
    public String page(@RequestParam(value = "merci", required = false) Integer merci,
                       Model model) {
        model.addAttribute("checkoutUrl", checkoutUrl);
        model.addAttribute("merci", merci != null && merci == 1);
        return "tarifs";
    }
}
