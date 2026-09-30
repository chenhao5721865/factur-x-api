# API Validate Endpoint Implementation Plan

> **Goal:** Implement `api` module as a standalone Spring Boot REST app with `POST /api/validate`, API key auth auto-generated on subscription, OpenAPI docs, and full test coverage.
>
> **Architecture:** Independent Spring Boot app on port 8081, shares PostgreSQL with `web` module, depends on `validator-core`, uses Flyway for its own migration, H2 for tests.
>
> **Tech Stack:** Java 17, Spring Boot 3.5.5, Maven, PostgreSQL (prod), H2 (test), springdoc-openapi, validator-core

---

## Task 1: Scaffold api module pom.xml

**Files:**
- Modify: `api/pom.xml`

Add dependencies: spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-validation, postgresql, flyway-core, flyway-database-postgresql, validator-core, springdoc-openapi-starter-webmvc (2.8.9), spring-boot-starter-test, h2. Add maven-resources-plugin for schema assets + spring-boot-maven-plugin.

- [ ] **Step 1: Write api/pom.xml**

Read the current empty `api/pom.xml`, replace with full Maven POM including all dependencies. The `maven-resources-plugin` `copy-schema-assets` execution must be included (same pattern as web module).

- [ ] **Step 2: Verify Maven compile**

Run: `mvn -pl api -am compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add api/pom.xml
git commit -m "feat(api): scaffold api module with dependencies"
```

---

## Task 2: Create ApiApplication + application.yml

**Files:**
- Create: `api/src/main/java/com/facturxapi/api/ApiApplication.java`
- Create: `api/src/main/resources/application.yml`

`ApiApplication.java`:
```java
package com.facturxapi.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
```

`application.yml`:
```yaml
server:
  port: 8081

spring:
  application:
    name: api
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 11MB
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:facturx}
    username: ${DB_USER:facturx}
    password: ${DB_PASSWORD:facturx}
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration
```

- [ ] **Step 1: Create ApiApplication.java**

Create `api/src/main/java/com/facturxapi/api/ApiApplication.java` with `@SpringBootApplication` and main method.

- [ ] **Step 2: Create application.yml**

Create `api/src/main/resources/application.yml` with port 8081, multipart config, PostgreSQL datasource (env vars same pattern as web module), Flyway config.

- [ ] **Step 3: Verify compile**

Run: `mvn -pl api -am compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add api/src/main/java/com/facturxapi/api/ApiApplication.java api/src/main/resources/application.yml
git commit -m "feat(api): add main application class and configuration"
```

---

## Task 3: Flyway migration for api_keys table

**Files:**
- Create: `api/src/main/resources/db/migration/V1__create_api_keys.sql`

```sql
CREATE TABLE api_keys (
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    token      VARCHAR(128) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ
);

CREATE INDEX idx_api_keys_email ON api_keys(email);
CREATE INDEX idx_api_keys_token ON api_keys(token);
```

- [ ] **Step 1: Create migration file**

Create `api/src/main/resources/db/migration/V1__create_api_keys.sql` with the SQL above. Token is 128 chars to accommodate `facturx_` prefix + UUID (32 chars) + `_` + 8 hex chars.

- [ ] **Step 2: Verify migration syntax**

Run: `mvn -pl api -am package -DskipTests -q` then start app against local PostgreSQL (port 5433) and check Flyway migration succeeds.
Expected: App starts, `api_keys` table created.

- [ ] **Step 3: Commit**

```bash
git add api/src/main/resources/db/migration/V1__create_api_keys.sql
git commit -m "feat(api): add Flyway migration for api_keys table"
```

---

## Task 4: ApiKey entity + repository

**Files:**
- Create: `api/src/main/java/com/facturxapi/api/entity/ApiKey.java`
- Create: `api/src/main/java/com/facturxapi/api/repository/ApiKeyRepository.java`

`ApiKey.java` — JPA entity mapping to `api_keys` table. Fields: id (IDENTITY), email, token (unique, length 128), createdAt (OffsetDateTime), revokedAt (nullable). Methods: `isRevoked()`, `revoke()` (sets revokedAt to now). Follows same pattern as `Subscriber` entity in web module.

`ApiKeyRepository.java` — extends `JpaRepository<ApiKey, Long>`. Methods:
- `Optional<ApiKey> findByToken(String token)` — for API key verification
- `Optional<ApiKey> findByEmailAndRevokedAtIsNull(String email)` — for key generation (check existing)

- [ ] **Step 1: Create ApiKey entity**

Create `api/src/main/java/com/facturxapi/api/entity/ApiKey.java` with all fields, constructors, getters/setters, and `isRevoked()`/`revoke()` methods.

- [ ] **Step 2: Create ApiKeyRepository**

Create `api/src/main/java/com/facturxapi/api/repository/ApiKeyRepository.java` with the two query methods.

- [ ] **Step 3: Verify compile**

Run: `mvn -pl api -am compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add api/src/main/java/com/facturxapi/api/entity/ApiKey.java api/src/main/java/com/facturxapi/api/repository/ApiKeyRepository.java
git commit -m "feat(api): add ApiKey entity and repository"
```

---

## Task 5: ApiKeyService

**Files:**
- Create: `api/src/main/java/com/facturxapi/api/service/ApiKeyService.java`
- Create: `api/src/test/java/com/facturxapi/api/service/ApiKeyServiceTest.java`

Token format: `facturx_` + UUID (no dashes) + `_` + 8 random hex chars. Example: `facturx_a1b2c3d4e5f6..._a1b2c3d4`. Total ~48 chars. Entropy: 128 + 32 = 160 bits.

Methods:
- `generateFor(String email)`: Check if active key exists for email via `findByEmailAndRevokedAtIsNull`. If yes, return empty. If no, generate token, save entity, return token.
- `resolveEmail(String token)`: Lookup by token via `findByToken`, filter non-revoked, return email or empty.
- `revokeByToken(String token)`: Find by token, set revokedAt, save. Return true if found and not already revoked.

- [ ] **Step 1: Create ApiKeyService**

Create `api/src/main/java/com/facturxapi/api/service/ApiKeyService.java` with the three methods. Use `@Service` + `@Transactional` on write methods.

- [ ] **Step 2: Write unit test**

Create `api/src/test/java/com/facturxapi/api/service/ApiKeyServiceTest.java` with `@ExtendWith(MockitoExtension.class)`. Test:
- `generateFor` creates token matching prefix pattern
- `generateFor` returns empty if active key exists
- `generateFor` returns empty if revoked key exists (new key regenerated)
- `resolveEmail` returns email for valid token
- `resolveEmail` returns empty for invalid token
- `revokeByToken` sets revokedAt and returns true
- `resolveEmail` returns empty for revoked token

- [ ] **Step 3: Run tests**

Run: `mvn -pl api test -Dtest=ApiKeyServiceTest`
Expected: All tests pass.

- [ ] **Step 4: Commit**

```bash
git add api/src/main/java/com/facturxapi/api/service/ApiKeyService.java api/src/test/java/com/facturxapi/api/service/ApiKeyServiceTest.java
git commit -m "feat(api): add ApiKeyService with generate, resolve, revoke"
```

---

## Task 6: ValidatorConfig for api module

**Files:**
- Create: `api/src/main/java/com/facturxapi/api/config/ValidatorConfig.java`

Same pattern as `web/ValidatorConfig.java`: extract 10 rule files (4 XSD + 6 SCH) from classpath to temp directory, then create `FacturxValidator` bean. Package changes to `com.facturxapi.api.config`.

- [ ] **Step 1: Create ValidatorConfig**

Create `api/src/main/java/com/facturxapi/api/config/ValidatorConfig.java`. Copy exact RULE_FILES list and extraction logic from `web/ValidatorConfig.java:25-59`.

- [ ] **Step 2: Verify compile**

Run: `mvn -pl api -am compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add api/src/main/java/com/facturxapi/api/config/ValidatorConfig.java
git commit -m "feat(api): add ValidatorConfig for factur-x validation"
```

---

## Task 7: ValidateController + HealthController

**Files:**
- Create: `api/src/main/java/com/facturxapi/api/controller/HealthController.java`
- Create: `api/src/main/java/com/facturxapi/api/controller/ValidateController.java`
- Create: `api/src/test/java/com/facturxapi/api/controller/ValidateControllerTest.java`

`HealthController.java`:
```java
package com.facturxapi.api.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
```

`ValidateController.java`:
- `@RestController` + `@RequestMapping("/api")`
- Inject `FacturxValidator` + `ApiKeyService`
- `POST /validate`: accepts multipart `file` part + `X-API-Key` header
- Flow:
  1. Resolve email: `apiKeyService.resolveEmail(apiKey)` → 401 if null
  2. Read file bytes: `file.getBytes()` → 500 on failure
  3. Check empty → 400
  4. Detect type (PDF/XML) via `detectType(bytes)` → 400 if unknown
  5. Call `validator.validate(bytes)` → catch `ExtractionException` → 422
  6. Log: `info` level with email, type, size, valid, errors count, warnings count, elapsed ms
  7. Return 200 with `ValidationResult` directly (no wrapper)
- File type detection: same logic as `ValidateurController.detectType()` (check `%PDF-` prefix or `<` start-of-content with BOM skip)
- Error response: `{"error": "message"}` for 400/401/422/500

Response format: `ValidationResult` record serializes directly to JSON via Jackson:
```json
{"valid": true, "profile": null, "errors": [], "warnings": []}
```

- [ ] **Step 1: Create HealthController**

Create `api/src/main/java/com/facturxapi/api/controller/HealthController.java` with `GET /api/health` returning `{"status": "UP"}`.

- [ ] **Step 2: Create ValidateController**

Create `api/src/main/java/com/facturxapi/api/controller/ValidateController.java` with `POST /api/validate`. Include the `detectType()` helper method (same logic as web module).

- [ ] **Step 3: Write integration test**

Create `api/src/test/java/com/facturxapi/api/controller/ValidateControllerTest.java`. Use `@SpringBootTest` + `@AutoConfigureMockMvc`. Test cases:
- Valid XML with valid API key → 200, `valid=true`, `errors=[]`
- Invalid XML (missing buyer) with valid API key → 200, `valid=false`, errors contain BR-07
- Valid PDF with valid API key → 200, `valid=true`
- Fake PDF (no embedded XML) with valid API key → 422, error contains "extraction" or "embedded"
- Plain text garbage → 400, error contains "Unsupported"
- Empty file → 400, error contains "Empty"
- Valid API key header missing → 401
- Invalid API key → 401

Use sample files from `samples/` directory: `valid-en16931-cii-basic.xml`, `invalid-missing-buyer.xml`, `factur-x-basic.pdf`.

- [ ] **Step 4: Run tests**

Run: `mvn -pl api test`
Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/facturxapi/api/controller/HealthController.java \
       api/src/main/java/com/facturxapi/api/controller/ValidateController.java \
       api/src/test/java/com/facturxapi/api/controller/ValidateControllerTest.java
git commit -m "feat(api): add POST /validate endpoint with health check and tests"
```

---

## Task 8: Test configuration

**Files:**
- Create: `api/src/test/resources/application.yml`
- Create: `api/src/test/resources/db/migration/V1__create_api_keys.sql`

Test `application.yml` — same pattern as web module test config: H2 in-memory DB, Flyway disabled, DDL auto create-drop:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop
  flyway:
    enabled: false
```

Test Flyway migration — copy from main resources (same SQL, H2-compatible). Note: `BIGSERIAL` and `TIMESTAMPTZ` are H2-compatible.

- [ ] **Step 1: Create test application.yml**

Create `api/src/test/resources/application.yml` with H2 in-memory DB config.

- [ ] **Step 2: Create test Flyway migration**

Create `api/src/test/resources/db/migration/V1__create_api_keys.sql` (same SQL as main).

- [ ] **Step 3: Run full test suite**

Run: `mvn -pl api test`
Expected: All tests pass including integration tests (which need the api_keys table).

- [ ] **Step 4: Commit**

```bash
git add api/src/test/resources/
git commit -m "feat(api): add test configuration (H2 + Flyway migration)"
```

---

## Task 9: Admin provision endpoint

**Files:**
- Create: `api/src/main/java/com/facturxapi/api/controller/AdminController.java`

`POST /api/admin/provision-key?email=xxx` — generates an API key for a subscriber. Only accessible internally (localhost-only in production). Returns `{"email": "...", "apiKey": "facturx_..."}` or 409 if key already exists.

- [ ] **Step 1: Create AdminController**

Create `api/src/main/java/com/facturxapi/api/controller/AdminController.java`:
```java
package com.facturxapi.api.controller;

import com.facturxapi.api.service.ApiKeyService;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final ApiKeyService apiKeyService;

    public AdminController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping("/provision-key")
    public ResponseEntity<?> provision(@RequestParam String email) {
        Optional<String> token = apiKeyService.generateFor(email);
        if (token.isPresent()) {
            return ResponseEntity.ok(Map.of("email", email, "apiKey", token.get()));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", "Active key already exists for: " + email));
    }
}
```

- [ ] **Step 2: Write unit test**

Create `api/src/test/java/com/facturxapi/api/controller/AdminControllerTest.java`:
- Provision creates new key → 200 with token
- Provision when key exists → 409

- [ ] **Step 3: Run tests**

Run: `mvn -pl api test`
Expected: All tests pass.

- [ ] **Step 4: Commit**

```bash
git add api/src/main/java/com/facturxapi/api/controller/AdminController.java \
       api/src/test/java/com/facturxapi/api/controller/AdminControllerTest.java
git commit -m "feat(api): add admin provision-key endpoint"
```

---

## Task 10: Docker support

**Files:**
- Create: `api/Dockerfile`
- Modify: `docker-compose.yml`

`api/Dockerfile`:
```dockerfile
FROM eclipse-temurin:17-jre-alpine
COPY api/target/api-*.jar /app/app.jar
EXPOSE 8081
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s \
  CMD wget -qO- http://localhost:8081/api/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

`docker-compose.yml` — add `api` service after `app`:
```yaml
  api:
    build:
      context: .
      dockerfile: api/Dockerfile
    ports:
      - "8081:8081"
    environment:
      - DB_HOST=db
      - DB_PORT=5432
      - DB_NAME=facturx
      - DB_USER=facturx
      - DB_PASSWORD=facturx
    depends_on:
      db:
        condition: service_healthy
```

- [ ] **Step 1: Create api/Dockerfile**

Create `api/Dockerfile` with eclipse-temurin:17-jre-alpine base, copy fat JAR, expose 8081, healthcheck via wget.

- [ ] **Step 2: Add api service to docker-compose.yml**

Modify `docker-compose.yml` to add the `api` service. Same pattern as `app` service but pointing to api Dockerfile.

- [ ] **Step 3: Verify compose up**

Run: `docker compose -f docker-compose.yml up -d --build api`
Expected: API service starts, health check passes. Curl `http://localhost:8081/api/health` → `{"status":"UP"}`.

- [ ] **Step 4: Commit**

```bash
git add api/Dockerfile docker-compose.yml
git commit -m "feat(api): add Dockerfile and compose service"
```

---

## Task 11: SpringDoc OpenAPI docs

**Files:**
- Create: `api/src/main/java/com/facturxapi/api/config/OpenApiConfig.java`

`OpenApiConfig.java`:
```java
package com.facturxapi.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String schemeName = "api_key";
        return new OpenAPI()
            .info(new Info()
                .title("Factur-X Validation API")
                .version("1.0.0")
                .description("Validate Factur-X PDF and XML invoices against EN 16931 rules.")
                .contact(new Contact()
                    .name("Factur-X API Support")
                    .url("https://factur-x-api.com")))
            .addSecurityItem(new SecurityRequirement().addList(schemeName))
            .components(new io.swagger.v3.oas.models.Components()
                .addSecurityScheme(schemeName,
                    new SecurityScheme()
                        .name("X-API-Key")
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .description("Generate via POST /api/admin/provision-key")));
    }
}
```

This enables `/swagger-ui.html` (interactive docs) and `/v3/api-docs` (OpenAPI spec JSON). No additional code changes needed — SpringDoc auto-scans `@RestController` classes.

- [ ] **Step 1: Create OpenApiConfig**

Create `api/src/main/java/com/facturxapi/api/config/OpenApiConfig.java` with the OpenAPI bean above.

- [ ] **Step 2: Verify Swagger UI loads**

Run: `mvn -pl api -am package -DskipTests -q && java -jar api/target/api-0.1.0-SNAPSHOT.jar &`
Curl: `curl -s http://localhost:8081/swagger-ui.html`
Expected: HTML with Swagger UI.
Curl: `curl -s http://localhost:8081/v3/api-docs`
Expected: JSON OpenAPI spec.

- [ ] **Step 3: Commit**

```bash
git add api/src/main/java/com/facturxapi/api/config/OpenApiConfig.java
git commit -m "docs(api): add SpringDoc OpenAPI configuration"
```

---

## Task 12: End-to-end smoke test

- [ ] **Step 1: Start full stack**

Run: `docker compose -f docker-compose.yml up -d`
Wait for both `app` and `api` services to be healthy.

- [ ] **Step 2: Provision an API key**

Run: `curl -X POST http://localhost:8081/api/admin/provision-key?email=test@example.com`
Expected: `{"email":"test@example.com","apiKey":"facturx_..."}`

- [ ] **Step 3: Validate valid XML**

Run: `curl -F "file=@samples/valid-en16931-cii-basic.xml" -H "X-API-Key: <token>" http://localhost:8081/api/validate`
Expected: `{"valid":true,"profile":null,"errors":[],"warnings":[],...}`

- [ ] **Step 4: Validate invalid XML**

Run: `curl -F "file=@samples/invalid-missing-buyer.xml" -H "X-API-Key: <token>" http://localhost:8081/api/validate`
Expected: `{"valid":false,"profile":null,"errors":[{"ruleId":"BR-07",...}],...}`

- [ ] **Step 5: Validate valid PDF**

Run: `curl -F "file=@samples/factur-x-basic.pdf" -H "X-API-Key: <token>" http://localhost:8081/api/validate`
Expected: `{"valid":true,...}`

- [ ] **Step 6: Test auth failure**

Run: `curl -F "file=@samples/valid-en16931-cii-basic.xml" -H "X-API-Key: invalid" http://localhost:8081/api/validate`
Expected: 401 with error message.

- [ ] **Step 7: Commit**

No code changes — this is a verification step. Just confirm all smoke tests pass.

---

## File Summary

### New files created:
| File | Purpose |
|------|---------|
| `api/src/main/java/com/facturxapi/api/ApiApplication.java` | Spring Boot entry point |
| `api/src/main/resources/application.yml` | Server port, datasource, Flyway config |
| `api/src/main/resources/db/migration/V1__create_api_keys.sql` | Flyway migration |
| `api/src/main/java/com/facturxapi/api/entity/ApiKey.java` | JPA entity |
| `api/src/main/java/com/facturxapi/api/repository/ApiKeyRepository.java` | JPA repository |
| `api/src/main/java/com/facturxapi/api/service/ApiKeyService.java` | Token generation/verification |
| `api/src/main/java/com/facturxapi/api/config/ValidatorConfig.java` | FacturxValidator bean |
| `api/src/main/java/com/facturxapi/api/config/OpenApiConfig.java` | SpringDoc OpenAPI bean |
| `api/src/main/java/com/facturxapi/api/controller/HealthController.java` | GET /api/health |
| `api/src/main/java/com/facturxapi/api/controller/ValidateController.java` | POST /api/validate |
| `api/src/main/java/com/facturxapi/api/controller/AdminController.java` | POST /api/admin/provision-key |
| `api/Dockerfile` | Docker image for api service |
| `api/src/test/resources/application.yml` | H2 test config |
| `api/src/test/resources/db/migration/V1__create_api_keys.sql` | Test migration |
| `api/src/test/java/com/facturxapi/api/service/ApiKeyServiceTest.java` | Unit tests |
| `api/src/test/java/com/facturxapi/api/controller/ValidateControllerTest.java` | Integration tests |
| `api/src/test/java/com/facturxapi/api/controller/AdminControllerTest.java` | Admin endpoint tests |

### Existing files modified:
| File | Change |
|------|--------|
| `api/pom.xml` | Add all dependencies + build plugins |
| `docker-compose.yml` | Add api service |

### Files NOT modified:
- `web/` module — no cross-module dependencies
- `validator-core/` module — untouched library
