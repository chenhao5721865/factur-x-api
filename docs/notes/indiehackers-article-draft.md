# Indie Hackers 发布用稿

> 标题：I built a Factur-X e-invoicing API for the EU 2026 mandate — $0 revenue so far, open-sourcing the whole thing
> 发帖版块：找一个 "Show" / "Side Project" 类版块，或直接发在主页 feed。发完把链接给我。

---

France will make B2B e-invoicing mandatory in September 2026 (reception first), and Germany follows with ZUGFeRD/XRechnung in January 2027. Every French company will have to send and receive structured electronic invoices — and most small SaaS owners I looked at were either paying enterprise prices for compliance tools or doing nothing and hoping for the best.

So I built the tool I'd want as a developer: **Factur-X API** (https://factur-x-api.com).

## What it does

Two endpoints:

- **Free web validator** (unlimited, no signup): drop a Factur-X/ZUGFeRD PDF or raw CII XML, get the full EN 16931 error list with rule IDs and locations. This is the SEO hook.
- **Generation API**: POST plain JSON (seller, buyer, line items with net prices), get back a fully auto-validated EN 16931 XML. Totals are computed server-side, and every document is round-tripped through XSD + Schematron validation before it's returned — so the output can't be structurally broken.

Pricing: free tier is 5 invoices/month, Pro is $9/month for 500. Billing via LemonSqueezy as merchant of record (I'm a solo dev in China — MoR handles EU VAT for me, which is the whole reason I chose that route).

## The honest numbers (4 days after launch)

- ~650 unique visitors (mostly starting to trickle in from Google, which began indexing the site 3 days ago)
- 7 free API keys created, 18 email subscribers
- **0 paying customers**
- 1 Reddit post about it got auto-removed by Reddit's spam filter because my account was 2 hours old (lesson learned: warm up accounts before posting)

## Stack (boring on purpose)

- Spring Boot (Java 17), multi-module Maven: a validator core, an API service, a small Thymeleaf marketing site
- PostgreSQL on one €6 Hetzner VPS, Caddy for TLS
- Validation = PDFBox 3 (extract embedded XML) + XSD D16B + ph-schematron-pure for the ~200 EN 16931 business rules
- ~90 tests, deployed via docker compose + scp (yes, scp — CI is on the list)

The whole codebase is **open source (AGPL-3.0)**: https://github.com/chenhao5721865/factur-x-api

I open-sourced it partly because of a French subreddit's self-promotion rule (open-source posts are exempt), but mostly because for a solo product like this, trust is the product. If I'm asking developers to send me their invoice data, "just read the code" is the strongest answer I can give. Nobody is going to self-host a validator to avoid paying $9/month.

## What I've learned so far

1. **The XSD is the easy part.** Any XML editor can validate structure. The business rules are where invoices actually fail — e.g. a 0% VAT line with the default tax category "S" is structurally perfect and 100% non-compliant (it must be category "Z"). Writing a generator that can't produce broken output is harder than it sounds.
2. **SEO is a waiting game.** The free validator targets "validateur factur-x" type French searches. Google indexed it 3 days ago; too early to tell.
3. **Distribution is the whole job.** The product was done in a month. Getting the first 10 users is already taking longer than building it.

## Ask

If you're dealing with EN 16931 / Factur-X / ZUGFeRD in your product, I'd love your feedback — especially on the API design (docs: https://factur-x-api.com/docs). And if you spot a bug or a missing business rule in the repo, that's a genuine gift.

Roast the landing page too: https://factur-x-api.com — I wrote it myself in French, which is maybe ambitious for someone who doesn't speak French.
