package com.facturxapi.web;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/** 规则解释页：/regles/（索引）与 /regles/{slug}（每条 EN 16931 规则一页）。 */
@Controller
public class RulePagesController {

    @GetMapping({"/regles", "/regles/"})
    public String index(Model model) {
        model.addAttribute("pages", RulePages.all());
        return "regles";
    }

    @GetMapping("/regles/{slug}")
    public String page(@PathVariable String slug, Model model) {
        RulePageContent c = RulePages.get(slug);
        if (c == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("c", c);
        return "regle";
    }
}
