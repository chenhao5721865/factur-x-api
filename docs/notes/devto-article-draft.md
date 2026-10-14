# Validating Factur-X / ZUGFeRD e-invoices in practice: XSD + Schematron

> dev.to 发布用稿。Tags: java, api, einvoicing, webdev。Canonical URL 留空。

If you build software for European businesses, the clock is ticking: **France makes B2B e-invoicing mandatory from September 2026** (reception first, then issuance), and **Germany follows in January 2027**. Both mandates are built on the same foundation — the European standard **EN 16931** — and both use hybrid formats (Factur-X in France, ZUGFeRD in Germany) that embed a structured XML file inside a human-readable PDF.

I recently built an e-invoicing API around this, and learned the hard way that "the file is valid XML" is nowhere near enough. This post is what I wish someone had told me before I started.

## What a Factur-X invoice actually is

A Factur-X file is a regular PDF with an XML attachment (usually `factur-x.xml`) embedded via the PDF/A-3 association mechanism. The PDF is for humans; the XML is for machines. The XML follows the **UN/CEFACT CII** syntax (Cross Industry Invoice) — the other allowed EN 16931 syntax is UBL, used by XRechnung.

So validating one involves three distinct steps:

1. Extract the embedded XML from the PDF
2. Check the XML against the XSD (structure, data types)
3. Check it against the Schematron business rules (the BR-* rules from EN 16931)

Skip step 3 and you'll accept invoices that any compliant receiver will reject.

## Step 1: Extracting the XML with PDFBox 3

```java
try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
    PDDocumentNameDictionary names = doc.getDocumentCatalog().getNames();
    PDEmbeddedFilesNameTreeNode efTree = names.getEmbeddedFiles();
    Map<String, PDComplexFileSpecification> files = efTree.getNames();
    // Factur-X attaches "factur-x.xml"; ZUGFeRD uses "zugferd-invoice.xml"
    for (var entry : files.entrySet()) {
        PDEmbeddedFile ef = entry.getValue().getEmbeddedFile();
        byte[] xml = ef.toByteArray();
        // ...
    }
}
```

Two gotchas here:

- **Not every PDF has attachments.** A plain PDF invoice (what most French suppliers still send today) will come back with an empty map — that's a `422`, not a validation failure.
- **Don't trust the file extension or Content-Type header.** Check magic bytes (`%PDF-` for PDF, `<` for raw XML) — clients upload mislabeled files constantly.

## Step 2: XSD validation

The EN 16931 CII schema is based on the UN/CEFACT D16B schemas. You load the XSD once and reuse the `Schema` (it's thread-safe; `Validator` instances are not, so create one per call):

```java
SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
Schema schema = factory.newSchema(new File("Factur-X_BASICWL.xsd"));

Validator v = schema.newValidator();
v.setErrorHandler(handler); // collect, don't throw on first error
v.validate(new StreamSource(new ByteArrayInputStream(xml)));
```

Collect all errors instead of failing fast — users fixing invoices want the full list in one pass.

## Step 3: Schematron business rules

This is the layer everyone forgets. The XSD checks *structure*; Schematron checks *meaning*. EN 16931 ships ~200 business rules like:

- **BR-CO-26**: the seller must have a VAT identifier or a legal registration identifier
- **BR-07**: the buyer name must be present
- **BR-S-05**: an invoice line in VAT category "S" (standard rate) must have a VAT rate > 0

That last one is my favorite trap: `vatRate: 0` with the default category `S` is structurally perfect XML — and completely non-compliant. Zero-rated lines must use category `Z`. No XSD will ever tell you that.

In Java, the practical choice is [ph-schematron](https://github.com/phax/ph-schematron) in "pure" mode (Schematron interpreted directly, no XSLT compilation step):

```java
SchematronResourcePure schematron = SchematronResourcePure.fromFile("EN16931-CII-validation.sch");
if (!schematron.isValidSchematron()) throw new IllegalStateException("bad rules file");

// validate
SchematronOutputType result = schematron
    .applySchematronValidation(new StreamSource(new ByteArrayInputStream(xml)));
```

One caveat I hit: the `applySchematronValidation*` family throws or swallows exceptions depending on which overload you pick — test with malformed input, not just valid samples, or your validator will 500 on exactly the files users most need help with.

The pure implementation is thread-safe for concurrent validation (I load-tested 8 threads × 25 iterations with no failures), which matters if you're putting this behind an API.

## The tooling gap

When I looked for a place to *test* all this, the options were surprisingly bad: the official validators are enterprise-priced or require uploading invoices to someone's portal with a signup form. So I built the thing I wanted:

**[Factur-X Validator](https://factur-x-api.com/validateur-factur-x)** — free, unlimited, no signup. Drop a Factur-X/ZUGFeRD PDF or raw XML, get the full XSD + Schematron error list with rule IDs (BR-07, etc.) and locations.

And for anyone who needs to *generate* compliant invoices from a SaaS rather than validate them, there's a companion API — `POST /api/generate` takes plain JSON (seller, buyer, line items with net prices) and returns an auto-validated EN 16931 XML. All totals (net, VAT, gross) are computed server-side so the output can't be internally inconsistent, and every generated document is round-tripped through the same validator before it's returned. 5 invoices/month free, docs at [factur-x-api.com/docs](https://factur-x-api.com/docs).

## If you're preparing for the mandate

- **Receiving invoices** (mandatory Sept 2026 in France): you need to *read* Factur-X — at minimum, extract the embedded XML reliably.
- **Issuing invoices**: get your totals chain right (net → VAT per rate → gross), pick the right VAT category codes, and validate against Schematron before sending anything. A rejected invoice in the new system isn't a bounced email — it's an unpaid invoice.

Curious what others are doing: building EN 16931 support in-house, or going through a PDP (Plateforme de Dématérialisation Partenaire)? And if you try the validator on a real invoice and hit a false positive/negative, I'd genuinely love to hear about it.
