package com.facturxapi.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 定价页 /tarifs（法语）。领 key 走公开端点 POST /api/keys（纯 fetch），
 * Souscrire 由浏览器带 X-API-Key 调 POST /api/checkout，服务端创建 Creem checkout。
 */
@Controller
public class TarifsController {

    @GetMapping("/tarifs")
    public String page(@RequestParam(value = "merci", required = false) Integer merci,
                       Model model) {
        model.addAttribute("merci", merci != null && merci == 1);
        return "tarifs";
    }
}
