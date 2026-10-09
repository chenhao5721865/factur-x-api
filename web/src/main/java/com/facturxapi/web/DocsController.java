package com.facturxapi.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 文档页 /docs（法语）：/api/generate 与 /api/validate 的契约说明。 */
@Controller
public class DocsController {

    @GetMapping("/docs")
    public String page() {
        return "docs";
    }
}
