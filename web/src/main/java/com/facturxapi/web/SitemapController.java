package com.facturxapi.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/** sitemap.xml 动态生成：既有 4 URL + 规则索引 + 全部规则页。扩页自动跟进。 */
@RestController
public class SitemapController {

    private static final String BASE = "https://factur-x-api.com";

    @GetMapping(value = "/sitemap.xml", produces = "application/xml;charset=UTF-8")
    @ResponseBody
    public String sitemap() {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
          .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        url(sb, "/validateur-factur-x", "1.0");
        url(sb, "/tarifs", "0.9");
        url(sb, "/regles", "0.8");
        url(sb, "/docs", "0.8");
        url(sb, "/", "0.5");
        for (RulePageContent c : RulePages.all()) {
            url(sb, "/regles/" + RulePages.slugOf(c.ruleId()), "0.7");
        }
        sb.append("</urlset>\n");
        return sb.toString();
    }

    private static void url(StringBuilder sb, String path, String priority) {
        sb.append("  <url>\n")
          .append("    <loc>").append(BASE).append(path).append("</loc>\n")
          .append("    <changefreq>monthly</changefreq>\n")
          .append("    <priority>").append(priority).append("</priority>\n")
          .append("  </url>\n");
    }
}
