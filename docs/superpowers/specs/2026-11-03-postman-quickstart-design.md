# Postman Collection + 15-Minute Quickstart — Design

**Date:** 2026-11-03
**Status:** Approved for implementation
**Trigger:** IH launch comment (Haseeb Shahid): conversion bottleneck is time-to-first-valid-invoice, not pricing.

## Problem

/docs explains both endpoints but gives no importable artifact. A developer must hand-build requests, guess the JSON shape, and find a real Factur-X file before seeing a successful call. That is the gap between "interesting API" and a working integration.

## Scope

1. **Postman collection** (`facturx-api.postman_collection.json`) — four requests:
   1. Generate invoice (JSON → XML), asserts 200, XML body, quota headers.
   2. Validate the bundled valid Factur-X PDF, asserts `valid: true`.
   3. Validate the bundled non-compliant XML, asserts `valid: false` and rule `BR-07`.
   4. Validate the XML produced by request 1, asserts `valid: true`.
2. **Postman environment** (`facturx-api.postman_environment.json`) — `baseUrl`, `apiKey` (secret).
3. **Downloadable fixtures**: `factur-x-basic.pdf`, `valid-en16931-cii-basic.xml`, `invalid-missing-buyer.xml`.
4. **/docs section** “Démarrage en 15 minutes”, placed above the endpoint reference: key → import → run, plus a two-command curl loop.

## Decisions

- **Reuse `samples/` as the single source of truth.** Fixtures are copied into `static/downloads` at build time by `maven-resources-plugin` (same pattern as schema assets). No duplicate files in the repo.
- **No new page.** The quickstart lives on `/docs`; the URL is already indexed and linked from the nav.
- **French copy**, matching the site; Postman request names/descriptions are French, code and field names stay as-is.
- **Postman file-upload limitation is explicit.** Postman cannot feed a generated response into a multipart file field. Request 4 therefore asks the user to save the response first (*Save Response → Save to a file*); the curl section provides the automated equivalent.
- **No published “Run in Postman” button.** Requires publishing to a public Postman workspace; out of scope for this iteration.
- **Fix the contract example found during implementation.** `/docs` previously showed `rule` / `location`; the API actually returns `ruleId` / `severity` / `xpath` (+ `profile`). The quickstart example is corrected as part of this change.

## Acceptance

- `GET /docs` renders the quickstart with links to all five assets.
- All five assets return 200 with the right content type; the collection parses as valid JSON and every request targets `{{baseUrl}}`.
- The collection's assertions are correct against the current API contract (200 / `valid` / `ruleId` = `BR-07`).
- The request 1 payload is accepted by `/api/generate` and its output re-validates (regression test).
- All existing tests stay green.

## Non-goals

SDK, English translation, Postman cloud publication, new endpoint, quota changes.
