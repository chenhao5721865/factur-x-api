package com.facturxapi.web;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** ruleId → 解释页内容。首批 10 条：必填项 + 金额链高频规则。扩页 = 往 PAGES 加一条。 */
public final class RulePages {

    private static final List<RulePageContent> PAGES = List.of(
        new RulePageContent(
            "BR-01",
            "BR-01 : spécification de facture électronique manquante (BT-24) — causes et correction",
            "La facture doit déclarer la norme qu'elle suit (BT-24). Sans cet identifiant, le fichier est rejeté dès la première vérification.",
            "BT-24 est l'identifiant de spécification : il indique la norme que la facture respecte, par exemple urn:cen.eu:en16931:2017 pour le profil EN 16931 (Factur-X). Les plateformes (PDP, portails publics) s'en servent pour choisir le bon moteur de validation.\n\nLa règle échoue quand ExchangedDocumentContext ne contient pas de GuidelineSpecifiedDocumentContextParameter, ou quand l'ID porte une valeur inconnue du référentiel. C'est fréquent dans les XML générés à la main ou par des outils qui confondent les profils (EN 16931, EXTENDED, XRechnung).",
            """
            <rsm:ExchangedDocumentContext>
              <!-- manque : GuidelineSpecifiedDocumentContextParameter (BT-24) -->
              <ram:BusinessProcessSpecifiedDocumentContextParameter>
                <ram:ID>A1</ram:ID>
              </ram:BusinessProcessSpecifiedDocumentContextParameter>
            </rsm:ExchangedDocumentContext>
            """,
            """
            <rsm:ExchangedDocumentContext>
              <ram:GuidelineSpecifiedDocumentContextParameter>
                <ram:ID>urn:cen.eu:en16931:2017</ram:ID>
              </ram:GuidelineSpecifiedDocumentContextParameter>
            </rsm:ExchangedDocumentContext>
            """,
            List.of("BR-02", "BR-05")),
        new RulePageContent(
            "BR-02",
            "BR-02 : numéro de facture obligatoire (BT-1) — causes et correction",
            "Chaque facture doit porter un numéro unique (BT-1), dans l'élément ID du document.",
            "BT-1 identifie la facture de façon unique chez le vendeur ; c'est la référence que votre client utilisera pour le paiement et la comptabilité. En syntaxe CII, il s'agit de l'élément ram:ID directement sous rsm:ExchangedDocument.\n\nL'erreur survient surtout dans les exports PDP mal paramétrés et les générateurs maison qui oublient l'élément, ou qui y mettent une valeur vide. Le numéro doit rester non vide : une chaîne d'espaces est rejetée aussi.",
            """
            <rsm:ExchangedDocument>
              <!-- manque : <ram:ID> (BT-1) -->
              <ram:IssueDateTime>2026-10-27</ram:IssueDateTime>
            </rsm:ExchangedDocument>
            """,
            """
            <rsm:ExchangedDocument>
              <ram:ID>FA-2026-0042</ram:ID>
              <ram:IssueDateTime>2026-10-27</ram:IssueDateTime>
            </rsm:ExchangedDocument>
            """,
            List.of("BR-03", "BR-07")),
        new RulePageContent(
            "BR-03",
            "BR-03 : date d'émission obligatoire (BT-2) — causes et correction",
            "La facture doit porter sa date d'émission (BT-2), au format AAAA-MM-JJ.",
            "BT-2 est la date à laquelle la facture est émise ; elle déclenche les délais de paiement et les déclarations de TVA. En CII : ram:IssueDateTime sous rsm:ExchangedDocument, au format ISO AAAA-MM-JJ.\n\nDeux causes typiques d'échec : l'élément absent, ou un format non ISO comme 27/10/2026. Le validateur XSD rejette ce dernier avant même les règles métier — si vous voyez BR-03, l'élément manque vraiment.",
            """
            <rsm:ExchangedDocument>
              <ram:ID>FA-2026-0042</ram:ID>
              <!-- manque : <ram:IssueDateTime> (BT-2) -->
            </rsm:ExchangedDocument>
            """,
            """
            <rsm:ExchangedDocument>
              <ram:ID>FA-2026-0042</ram:ID>
              <ram:IssueDateTime>2026-10-27</ram:IssueDateTime>
            </rsm:ExchangedDocument>
            """,
            List.of("BR-02", "BR-05")),
        new RulePageContent(
            "BR-05",
            "BR-05 : devise de la facture obligatoire (BT-5) — causes et correction",
            "La facture doit déclarer sa devise (BT-5), par exemple EUR, en code ISO 4217.",
            "BT-5 donne la devise de tous les montants de la facture. Sans elle, aucun contrôle de cohérence n'est possible : le moteur ne sait pas si 1000 signifie mille euros ou mille dollars. En CII : ram:InvoiceCurrencyCode sous ApplicableHeaderTradeDocument.\n\nL'échec vient d'un élément absent ou d'un code qui n'est pas dans la liste ISO 4217 (Euros au lieu de EUR, par exemple). Utilisez toujours le code alphabétique à trois lettres.",
            """
            <ram:ApplicableHeaderTradeDocument>
              <!-- manque : <ram:InvoiceCurrencyCode> (BT-5) -->
              <ram:SellerTradeParty>...</ram:SellerTradeParty>
            </ram:ApplicableHeaderTradeDocument>
            """,
            """
            <ram:ApplicableHeaderTradeDocument>
              <ram:InvoiceCurrencyCode>EUR</ram:InvoiceCurrencyCode>
              <ram:SellerTradeParty>...</ram:SellerTradeParty>
            </ram:ApplicableHeaderTradeDocument>
            """,
            List.of("BR-01", "BR-CO-15")),
        new RulePageContent(
            "BR-06",
            "BR-06 : nom du vendeur obligatoire (BT-27) — causes et correction",
            "La facture doit contenir le nom légal du vendeur (BT-27).",
            "BT-27 est le nom légal de votre entreprise tel qu'il apparaît sur la facture. Les plateformes et l'administration fiscale l'utilisent pour identifier l'émetteur ; il est donc contrôlé même quand un SIREN/VAT number (BT-30/BT-31) est présent — les deux sont complémentaires.\n\nL'échec typique : le bloc SellerTradeParty existe mais sans élément ram:Name, ou avec une chaîne vide. Ça arrive quand un outil copie un gabarit où le vendeur n'est renseigné que par son identifiant.",
            """
            <ram:SellerTradeParty>
              <ram:ID>SELLER-001</ram:ID>
              <!-- manque : <ram:Name> (BT-27) -->
            </ram:SellerTradeParty>
            """,
            """
            <ram:SellerTradeParty>
              <ram:ID>SELLER-001</ram:ID>
              <ram:Name>Atelier Duval SARL</ram:Name>
            </ram:SellerTradeParty>
            """,
            List.of("BR-07", "BR-16")),
        new RulePageContent(
            "BR-07",
            "BR-07 : nom de l'acheteur obligatoire (BT-44) — causes et correction",
            "La facture doit contenir le nom de l'acheteur (BT-44).",
            "BT-44 est le nom légal de votre client. Comme pour le vendeur (BR-06), il est obligatoire en plus de tout identifiant : une facture adressée uniquement à un SIREN est rejetée. C'est l'une des erreurs les plus vues sur les factures générées par des PDP mal configurés.\n\nEn CII : ram:Name sous ram:BuyerTradeParty. Vérifiez aussi que le bloc acheteur n'a pas été confondu avec le bloc livraison (ShipToTradeParty), confusion classique dans les flux logistiques.",
            """
            <ram:BuyerTradeParty>
              <ram:ID>BUYER-042</ram:ID>
              <!-- manque : <ram:Name> (BT-44) -->
            </ram:BuyerTradeParty>
            """,
            """
            <ram:BuyerTradeParty>
              <ram:ID>BUYER-042</ram:ID>
              <ram:Name>Client Final SAS</ram:Name>
            </ram:BuyerTradeParty>
            """,
            List.of("BR-02", "BR-06")),
        new RulePageContent(
            "BR-16",
            "BR-16 : au moins une ligne de facture est requise — causes et correction",
            "Une facture doit contenir au moins une ligne de facture (IncludedSupplyChainTradeLineItem).",
            "Une facture électronique décrit ce qui a été vendu, ligne par ligne. Une facture sans ligne ne transporte aucune prestation et ne peut pas être comptabilisée chez le client — le profil EN 16931 l'interdit donc explicitement.\n\nL'échec vient d'un export qui ne contient que les totaux (bloc SpecifiedTradeSettlementHeaderMonetarySummation rempli) sans aucun ram:IncludedSupplyChainTradeLineItem. Si votre système vend un abonnement ou un forfait, créez une ligne unique à 1 unité plutôt qu'une facture sans ligne.",
            """
            <rsm:SupplyChainTradeTransaction>
              <!-- aucune ligne : manque IncludedSupplyChainTradeLineItem -->
              <ram:ApplicableHeaderTradeAgreement>...</ram:ApplicableHeaderTradeAgreement>
            </rsm:SupplyChainTradeTransaction>
            """,
            """
            <rsm:SupplyChainTradeTransaction>
              <ram:IncludedSupplyChainTradeLineItem>
                <ram:AssociatedDocumentLineDocument>
                  <ram:LineID>1</ram:LineID>
                </ram:AssociatedDocumentLineDocument>
                <!-- ... prix, quantité, article ... -->
              </ram:IncludedSupplyChainTradeLineItem>
              <ram:ApplicableHeaderTradeAgreement>...</ram:ApplicableHeaderTradeAgreement>
            </rsm:SupplyChainTradeTransaction>
            """,
            List.of("BR-06", "BR-CO-10")),
        new RulePageContent(
            "BR-CO-10",
            "BR-CO-10 : la somme des lignes ne correspond pas au total HT — causes et correction",
            "Le total HT (BT-106) doit être égal à la somme des montants HT de chaque ligne.",
            "BT-106 (LineTotalAmount) est la somme des montants nets de toutes les lignes. Le contrôle arithmétique échoue dès qu'il y a un écart, même d'un centime.\n\nCause n°1 : des totaux saisis ou recalculés à la main alors que les lignes ont changé. Cause n°2 : l'arrondi — trois lignes à 33,33 € donnent 99,99 €, pas 100,00 € : le total doit être la somme réelle des lignes, pas un chiffre « rond » choisi au moment de la facturation. En CII, le total se trouve dans SpecifiedTradeSettlementHeaderMonetarySummation/ram:LineTotalAmount et chaque ligne déclare son propre ram:LineTotalAmount.",
            """
            <ram:SpecifiedTradeSettlementHeaderMonetarySummation>
              <ram:LineTotalAmount>100.00</ram:LineTotalAmount> <!-- devrait être 99.99 -->
            </ram:SpecifiedTradeSettlementHeaderMonetarySummation>
            <!-- lignes : 33.33 + 33.33 + 33.33 = 99.99 -->
            """,
            """
            <ram:SpecifiedTradeSettlementHeaderMonetarySummation>
              <ram:LineTotalAmount>99.99</ram:LineTotalAmount>
            </ram:SpecifiedTradeSettlementHeaderMonetarySummation>
            <!-- lignes : 33.33 + 33.33 + 33.33 = 99.99 -->
            """,
            List.of("BR-CO-13", "BR-CO-15")),
        new RulePageContent(
            "BR-CO-13",
            "BR-CO-13 : le total HT ne correspond pas à la somme des bases TVA — causes et correction",
            "Le total HT (BT-109) doit être égal à la somme des bases taxables (BT-116) de la synthèse TVA.",
            "La synthèse TVA décompose le total HT par catégorie de taxe : pour chaque taux, une base taxable (BT-116, ram:BasisAmount). BT-109 (TaxBasisTotalAmount) doit être exactement la somme de ces bases.\n\nL'erreur apparaît quand la synthèse TVA est recalculée d'un côté et les totaux de l'autre — par exemple une remise appliquée aux lignes mais pas dans les bases par taux, ou une ligne classée dans une mauvaise catégorie. Vérifiez que chaque catégorie présente dans les lignes a bien sa ligne dans TradeTax, et que les BasisAmount s'additionnent au total HT.",
            """
            <ram:SpecifiedTradeSettlementHeaderMonetarySummation>
              <ram:TaxBasisTotalAmount>1000.00</ram:TaxBasisTotalAmount> <!-- devrait être 1100.00 -->
            </ram:SpecifiedTradeSettlementHeaderMonetarySummation>
            <!-- synthèse TVA : base 20 % = 1100.00 -->
            """,
            """
            <ram:SpecifiedTradeSettlementHeaderMonetarySummation>
              <ram:TaxBasisTotalAmount>1100.00</ram:TaxBasisTotalAmount>
            </ram:SpecifiedTradeSettlementHeaderMonetarySummation>
            <!-- synthèse TVA : base 20 % = 1100.00 -->
            """,
            List.of("BR-CO-10", "BR-CO-15")),
        new RulePageContent(
            "BR-CO-15",
            "BR-CO-15 : le total TTC ne correspond pas au total HT plus la TVA — causes et correction",
            "Le total TTC (BT-112) doit être égal au total HT (BT-109) plus le total TVA (BT-110).",
            "C'est l'équation de base de toute facture : TTC = HT + TVA. BT-112 (GrandTotalAmount) doit être exactement la somme de TaxBasisTotalAmount (BT-109) et TaxTotalAmount (BT-110).\n\nCause classique : la TVA est calculée taux par taux avec arrondi, puis le TTC est calculé depuis un taux global — les arrondis divergent d'un centime. Règle d'or : calculez la TVA par catégorie (TaxTotalAmount = Σ pour chaque taux de basis × taux, arrondi au centime), puis additionnez. Notre API /api/generate applique cet arrondi par catégorie nativement.",
            """
            <ram:SpecifiedTradeSettlementHeaderMonetarySummation>
              <ram:TaxBasisTotalAmount>100.00</ram:TaxBasisTotalAmount>
              <ram:TaxTotalAmount>20.00</ram:TaxTotalAmount>
              <ram:GrandTotalAmount>120.01</ram:GrandTotalAmount> <!-- devrait être 120.00 -->
            </ram:SpecifiedTradeSettlementHeaderMonetarySummation>
            """,
            """
            <ram:SpecifiedTradeSettlementHeaderMonetarySummation>
              <ram:TaxBasisTotalAmount>100.00</ram:TaxBasisTotalAmount>
              <ram:TaxTotalAmount>20.00</ram:TaxTotalAmount>
              <ram:GrandTotalAmount>120.00</ram:GrandTotalAmount>
            </ram:SpecifiedTradeSettlementHeaderMonetarySummation>
            """,
            List.of("BR-CO-10", "BR-CO-13"))
    );

    private static final Map<String, RulePageContent> BY_SLUG =
        PAGES.stream().collect(Collectors.toUnmodifiableMap(c -> slugOf(c.ruleId()), Function.identity()));

    private RulePages() {}

    public static List<RulePageContent> all() { return PAGES; }

    public static RulePageContent get(String slug) {
        return slug == null ? null : BY_SLUG.get(slug.toLowerCase(Locale.ROOT));
    }

    public static boolean containsRule(String ruleId) {
        return ruleId != null && BY_SLUG.containsKey(ruleId.toLowerCase(Locale.ROOT));
    }

    public static String slugOf(String ruleId) {
        return ruleId.toLowerCase(Locale.ROOT);
    }
}
