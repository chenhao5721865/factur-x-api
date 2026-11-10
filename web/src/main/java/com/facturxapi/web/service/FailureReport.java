package com.facturxapi.web.service;

import com.facturxapi.web.RuleMessages;
import com.facturxapi.web.RulePages;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** 把结果页的 ruleId 列表渲染成法语报告正文；未知/伪造 id 被忽略，一条不剩则不发信。 */
public final class FailureReport {

    private static final int MAX_ITEMS = 20;

    private FailureReport() {}

    public static Optional<String> render(List<String> ruleIds) {
        Set<String> seen = new LinkedHashSet<>();
        StringBuilder items = new StringBuilder();
        for (String raw : ruleIds) {
            if (raw == null) {
                continue;
            }
            String id = raw.trim().toUpperCase(Locale.ROOT);
            if (id.isEmpty() || id.length() > 20 || !id.matches("[A-Z0-9-]+")) {
                continue;
            }
            String fr = RuleMessages.get(id);
            if (fr == null || !seen.add(id)) {
                continue;
            }
            if (seen.size() > MAX_ITEMS) {
                break;
            }
            items.append("- ").append(id).append(" : ").append(fr).append('\n');
            if (RulePages.containsRule(id)) {
                items.append("  https://factur-x-api.com/regles/")
                    .append(RulePages.slugOf(id)).append('\n');
            }
        }
        if (seen.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(text(seen.size(), items.toString()));
    }

    static String text(int count, String items) {
        String points = count == 1 ? "1 point à corriger" : count + " points à corriger";
        return """
            Bonjour,

            Voici le rapport de conformité demandé sur factur-x-api.com.

            Votre facture n'est pas conforme à la norme EN 16931 : %s.

            %s
            Rappel : votre fichier n'a pas été conservé sur nos serveurs.

            Pour corriger ces points automatiquement ou valider vos factures par API :
            https://factur-x-api.com/docs

            Une question ? Répondez à cet e-mail ou écrivez à 543843499@qq.com.

            L'équipe Factur-X API
            """.formatted(points, items);
    }
}
