package com.facturxapi.web;

import java.util.Map;

/**
 * ruleId → 法语说明静态映射。覆盖 golden 实测触发的 id + EN 16931 常见规则。
 * 未命中时 fallback 返回引擎原始 message（英文），由页面标注 ruleId 兜底展示。
 */
public final class RuleMessages {

    private static final Map<String, String> FR = Map.ofEntries(
        // golden 实测触发
        Map.entry("BR-07", "La facture doit contenir le nom de l'acheteur (BT-44)."),
        Map.entry("BR-10", "Une ligne de facture doit contenir le nom de l'article (BT-153)."),
        Map.entry("BR-11", "Une ligne de facture doit contenir la quantité facturée (BT-129)."),
        Map.entry("BR-CO-16", "Le montant total de la TVA (BT-110) doit correspondre à la somme des bases taxables par taux."),
        // 常见 EN 16931 业务规则
        Map.entry("BR-01", "Une facture émise en électronique doit porter une spécification (BT-24)."),
        Map.entry("BR-02", "Un numéro de facture est obligatoire (BT-1)."),
        Map.entry("BR-03", "Une facture doit contenir une date d'émission (BT-2)."),
        Map.entry("BR-04", "Une facture doit contenir un code de type de document (BT-3)."),
        Map.entry("BR-05", "Une facture doit contenir une devise (BT-5)."),
        Map.entry("BR-06", "La facture doit contenir le nom du vendeur (BT-27)."),
        Map.entry("BR-08", "L'identifiant du vendeur est obligatoire (BT-30/BT-31) selon le profil."),
        Map.entry("BR-09", "L'identifiant de l'acheteur est obligatoire (BT-47/BT-48) selon le profil."),
        Map.entry("BR-12", "Une ligne de facture doit porter un identifiant (BT-126)."),
        Map.entry("BR-13", "Une ligne de facture doit être référencée dans la synthèse TVA (BT-131)."),
        Map.entry("BR-14", "Un taux de TVA par catégorie doit être présent dans la synthèse TVA (BT-119)."),
        Map.entry("BR-15", "Une catégorie de TVA taxable doit avoir une base taxable (BT-116)."),
        Map.entry("BR-16", "Une facture doit contenir au moins une ligne."),
        Map.entry("BR-17", "Le pays du vendeur est obligatoire (BT-40)."),
        Map.entry("BR-18", "Le pays de l'acheteur est obligatoire (BT-55)."),
        Map.entry("BR-19", "La date limite ou date d'échéance de paiement est obligatoire."),
        Map.entry("BR-20", "Le montant total HT est obligatoire (BT-109)."),
        Map.entry("BR-21", "Le montant total TTC est obligatoire (BT-112)."),
        Map.entry("BR-22", "Le montant total à payer est obligatoire (BT-115)."),
        Map.entry("BR-45", "Chaque TVA par catégorie doit porter un motif d'exonération si le taux est 0 (BT-120)."),
        Map.entry("BR-49", "Le motif de paiement doit être présent si les données de paiement sont fournies."),
        Map.entry("BR-52", "Chaque acompte doit avoir une description."),
        Map.entry("BR-61", "Si la TVA n'est pas applicable, le motif d'exonération est obligatoire (BT-120)."),
        Map.entry("BR-62", "Un attribut de spécification de facture est attendu (BT-24)."),
        Map.entry("BR-CO-10", "La somme des lignes (BT-106) doit correspondre au total HT."),
        Map.entry("BR-CO-12", "Le montant à payer (BT-115) doit correspondre au TTC moins les acomptes."),
        Map.entry("BR-CO-13", "Le total HT (BT-109) doit correspondre à la somme des bases taxables par taux de TVA."),
        Map.entry("BR-CO-15", "Le total TTC (BT-112) doit correspondre au total HT plus le total TVA."),
        Map.entry("BR-CO-26", "L'acheteur doit être un fournisseur ou un prestataire électronique."),
        // 语法层
        Map.entry("XSD", "Le document n'est pas conforme au schéma XML CII (erreur de syntaxe ou de structure).")
    );

    private RuleMessages() {}

    /** 法语说明；未命中返回 null（调用方决定 fallback 展示）。 */
    public static String get(String ruleId) {
        return ruleId == null ? null : FR.get(ruleId);
    }
}
