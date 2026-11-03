# Postman Collection + 15-Minute Quickstart — Implementation Plan

**Spec:** `docs/superpowers/specs/2026-11-03-postman-quickstart-design.md`

## Task 1 — Add collection and environment

Files:
- `web/src/main/resources/static/downloads/facturx-api.postman_collection.json`
- `web/src/main/resources/static/downloads/facturx-api.postman_environment.json`

Collection v2.1, variables `baseUrl` + `apiKey`, four requests in run order:
generate → validate bundled PDF → validate bundled invalid XML → validate generated XML.
Test scripts assert status/headers/`valid`/`BR-07`.

## Task 2 — Serve fixtures from `samples/`

`web/pom.xml`: add a `copy-quickstart-fixtures` execution under `maven-resources-plugin`:
- source `${maven.multiModuleProjectDirectory}/samples`
- includes `factur-x-basic.pdf`, `valid-en16931-cii-basic.xml`, `invalid-missing-buyer.xml`
- target `${project.build.outputDirectory}/static/downloads`

## Task 3 — Add the quickstart to /docs

`web/src/main/resources/templates/docs.html`: new section after the intro paragraph:
- three numbered steps (key, import, run) with download links
- four-request summary with expected results
- two-command curl loop
- fixture list + license attribution
- fix the existing `/api/validate` response example: actual fields are
  `ruleId` / `severity` / `xpath` / `profile` (the old example said `rule` / `location`)

## Task 4 — Tests

New `web/src/test/java/com/facturxapi/web/QuickstartAssetsTest.java`:
- `/docs` contains the section heading and all download URLs
- collection/environment parse as JSON; collection has 4 items, each URL starts with `{{baseUrl}}`; environment defines `baseUrl` and `apiKey`
- PDF starts with `%PDF-`, XML fixtures start with `<`, content types correct

New `api/src/test/java/com/facturxapi/api/controller/QuickstartCollectionPayloadTest.java`:
- extracts the raw body of request 1 from the collection and posts it to `/api/generate`
- expects 200 and re-validates the generated XML (`valid: true`), so the quickstart cannot rot silently

## Verification

```
JAVA_HOME="C:\Program Files\Java\jdk-17" \
C:\MyWork\tools\apache-maven-3.9.16\bin\mvn -pl web -am test
```

Then full reactor test. No commit, no deploy until user confirms.
