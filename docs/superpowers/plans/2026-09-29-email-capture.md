# 邮件捕获 (Email Capture) 实现计划

> **Goal:** 在法语验证工具页底部添加可工作的邮件订阅表单，用户提交后邮箱存入 Postgres，htmx 局部刷新显示感谢消息。
>
> **Architecture:** Spring Boot web 模块添加 JPA + Flyway，使用已有 Postgres 容器。单表 `subscribers`，`POST /subscribe` 端点返回 htmx HTML fragment。
>
> **Tech Stack:** Java 17, Spring Boot 3.5, Spring Data JPA, Flyway, PostgreSQL 16, Thymeleaf, htmx

---

## 文件清单

| 操作 | 文件 | 职责 |
|------|------|------|
| 修改 | `web/pom.xml` | 添加 JPA、Flyway、PostgreSQL 驱动依赖 |
| 修改 | `web/src/main/resources/application.yml` | 添加 datasource + Flyway 配置 |
| 创建 | `web/src/main/resources/db/migration/V1__create_subscribers.sql` | Flyway 建表迁移 |
| 创建 | `web/src/main/java/com/facturxapi/web/entity/Subscriber.java` | Subscriber JPA 实体 |
| 创建 | `web/src/main/java/com/facturxapi/web/repository/SubscriberRepository.java` | Subscriber JPA Repository |
| 创建 | `web/src/main/java/com/facturxapi/web/service/SubscriberService.java` | 订阅业务逻辑（含重复处理） |
| 创建 | `web/src/main/java/com/facturxapi/web/controller/SubscribeController.java` | `POST /subscribe` 端点 |
| 修改 | `web/src/main/resources/templates/validateur.html` | 激活订阅表单 + CSRF |
| 创建 | `web/src/test/java/com/facturxapi/web/service/SubscriberServiceTest.java` | Service 单元测试 |
| 创建 | `web/src/test/java/com/facturxapi/web/controller/SubscribeControllerTest.java` | Controller 集成测试 |

---

## Task 1: 添加 Maven 依赖

**文件:**
- 修改: `web/pom.xml`

添加以下依赖到 `web/pom.xml` 的 `<dependencies>` 块中（放在 `spring-boot-starter-thymeleaf` 之后）：

```xml
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-database-postgresql</artifactId>
    </dependency>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>runtime</scope>
    </dependency>
```

> 注意：`postgresql` 驱动版本由 Spring Boot parent 管理，`flyway-core` 版本也由 parent 管理，无需指定 version。

---

## Task 2: 配置 datasource 和 Flyway

**文件:**
- 修改: `web/src/main/resources/application.yml`

在现有内容末尾添加：

```yaml|CODE_EDIT_BLOCK|C:\Project Exploration\03-api-service\web\src\main\resources\application.yml
server:
  port: 8080

spring:
  application:
    name: web
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 11MB
  datasource:
    url: jdbc:postgresql://db:5432/facturx
    username: facturx
    password: facturx
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration
```

> `ddl-auto: validate` 确保 schema 只由 Flyway 管理，Hibernate 不自动建表。

---

## Task 3: 创建 Flyway 迁移

**文件:**
- 创建: `web/src/main/resources/db/migration/V1__create_subscribers.sql`

```sql
-- Flyway migration: V1__create_subscribers.sql
CREATE TABLE subscribers (
    email      VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

> 邮箱作主键，因为每个邮箱只需订阅一次，不需要额外 ID 列。

---

## Task 4: 创建 Subscriber 实体

**文件:**
- 创建: `web/src/main/java/com/facturxapi/web/entity/Subscriber.java`

```java
package com.facturxapi.web.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscribers")
public class Subscriber {

    @Id
    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Subscriber() {}

    public Subscriber(String email) {
        this.email = email;
        this.createdAt = LocalDateTime.now();
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

> 注意：用 `LocalDateTime` 而非 `LocalDateTime` 的 `Instant`，因为 JPA 默认映射与 Postgres `TIMESTAMPTZ` 兼容。

---

## Task 5: 创建 SubscriberRepository

**文件:**
- 创建: `web/src/main/java/com/facturxapi/web/repository/SubscriberRepository.java`

```java
package com.facturxapi.web.repository;

import com.facturxapi.web.entity.Subscriber;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriberRepository extends JpaRepository<Subscriber, String> {
    boolean existsByEmail(String email);
}
```

> 继承 `JpaRepository<Subscriber, String>`，泛型参数是实体类和主键类型（String）。

---

## Task 6: 创建 SubscriberService

**文件:**
- 创建: `web/src/main/java/com/facturxapi/web/service/SubscriberService.java`

```java
package com.facturxapi.web.service;

import com.facturxapi.web.entity.Subscriber;
import com.facturxapi.web.repository.SubscriberRepository;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriberService {

    private final SubscriberRepository repository;

    public SubscriberService(SubscriberRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SubscribeResult subscribe(String email) {
        try {
            Subscriber subscriber = new Subscriber(email.trim().toLowerCase());
            repository.save(subscriber);
            return SubscribeResult.success();
        } catch (DataIntegrityViolationException e) {
            return SubscribeResult.duplicate();
        }
    }

    public Optional<Subscriber> findByEmail(String email) {
        return repository.findById(email.toLowerCase());
    }

    public enum SubscribeResult {
        SUCCESS,
        DUPLICATE;

        public boolean isSuccess() { return this == SUCCESS; }
        public boolean isDuplicate() { return this == DUPLICATE; }

        static SubscribeResult success() { return SUCCESS; }
        static SubscribeResult duplicate() { return DUPLICATE; }
    }
}
```

> 关键逻辑：`trim().toLowerCase()` 规范化邮箱，`DataIntegrityViolationException` 捕获 PostgreSQL 唯一约束冲突。

---

## Task 7: 创建 SubscribeController

**文件:**
- 创建: `web/src/main/java/com/facturxapi/web/controller/SubscribeController.java`

```java
package com.facturxapi.web.controller;

import com.facturxapi.web.service.SubscriberService;
import com.facturxapi.web.service.SubscriberService.SubscribeResult;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SubscribeController {

    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final SubscriberService subscriberService;

    public SubscribeController(SubscriberService subscriberService) {
        this.subscriberService = subscriberService;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<String> subscribe(
            @RequestParam("email") String email,
            jakarta.servlet.http.HttpServletRequest request) {

        if (!isHtmx(request)) {
            return ResponseEntity.badRequest().body("Invalid request");
        }

        // 邮箱格式校验 (正则，无需额外依赖)
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("<div class=\"bg-red-50 border border-red-300 text-red-800 rounded-lg p-4 text-sm\">"
                        + "Format d'email invalide.</div>");
        }

        SubscribeResult result = subscriberService.subscribe(email);

        return switch (result) {
            case SUCCESS -> ResponseEntity.ok()
                    .body("<div class=\"bg-emerald-50 border border-emerald-300 text-emerald-800 rounded-lg p-4\">"
                        + "<p class=\"font-semibold\">Merci !</p>"
                        + "<p class=\"text-sm\">Vous êtes inscrit(e). Nous vous tiendrons informé(e) de l'ouverture de l'API.</p>"
                        + "</div>");
            case DUPLICATE -> ResponseEntity.ok()
                    .body("<div class=\"bg-amber-50 border border-amber-300 text-amber-800 rounded-lg p-4 text-sm\">"
                        + "Vous êtes déjà inscrit(e). Merci !</div>");
        };
    }

    private static boolean isHtmx(jakarta.servlet.http.HttpServletRequest request) {
        return "true".equals(request.getHeader("HX-Request"));
    }
}
```

> htmx 通过 `HX-Request` 头区分普通 POST 和 htmx 请求，非 htmx 请求返回 400。

---

## Task 8: 更新 validateur.html 表单

**文件:**
- 修改: `web/src/main/resources/templates/validateur.html`

将第 62-74 行的订阅表单区域替换为：

```html|CODE_EDIT_BLOCK|C:\Project Exploration\03-api-service\web\src\main\resources\templates\validateur.html
    <!-- Inscription -->
    <section id="subscribe-section" class="mt-8 bg-white border border-slate-200 rounded-xl p-6">
      <h2 class="font-semibold mb-1">Restez informé</h2>
      <p class="text-sm text-slate-500 mb-4">API de génération et de validation en cours de préparation.</p>
      <form id="subscribe-form"
            hx-post="/subscribe"
            hx-target="#subscribe-section"
            hx-swap="outerHTML"
            class="flex gap-2 max-w-md">
        <input type="hidden" th:name="${_csrf?.parameterName}" th:value="${_csrf?.token}"/>
        <input type="email" name="email" placeholder="votre@exemple.fr" required
               class="flex-1 px-3 py-2 border border-slate-300 rounded-lg text-sm">
        <button type="submit"
                class="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm hover:bg-blue-700 font-medium">
          S'inscrire
        </button>
      </form>
    </section>
```

> 关键点：
> - `hx-post="/subscribe"`：htmx 提交到后端
> - `hx-target="#subscribe-section"`：替换整个 section
> - `hx-swap="outerHTML"`：用服务端返回的完整 HTML 替换
> - CSRF hidden input：Thymeleaf 渲染时注入 token
> - 按钮改为 `type="submit"`，移除 disabled

---

## Task 9: 编写 SubscriberService 单元测试

**文件:**
- 创建: `web/src/test/java/com/facturxapi/web/service/SubscriberServiceTest.java`

```java
package com.facturxapi.web.service;

import com.facturxapi.web.entity.Subscriber;
import com.facturxapi.web.repository.SubscriberRepository;
import com.facturxapi.web.service.SubscriberService.SubscribeResult;

import org.springframework.dao.DataIntegrityViolationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriberServiceTest {

    @Mock
    private SubscriberRepository repository;

    private SubscriberService service;

    @BeforeEach
    void setUp() {
        service = new SubscriberService(repository);
    }

    @Test
    void subscribe_success() {
        SubscribeResult result = service.subscribe("test@example.com");
        assertEquals(SubscribeResult.SUCCESS, result);
        verify(repository).save(argThat(s -> "test@example.com".equals(s.getEmail())));
    }

    @Test
    void subscribe_normalizes_email() {
        service.subscribe("  TEST@Example.COM  ");
        verify(repository).save(argThat(s -> "test@example.com".equals(s.getEmail())));
    }

    @Test
    void subscribe_duplicate_returns_duplicate() {
        when(repository.save(any(Subscriber.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        SubscribeResult result = service.subscribe("test@example.com");
        assertEquals(SubscribeResult.DUPLICATE, result);
    }
}
```

---

## Task 10: 编写 SubscribeController 集成测试

**文件:**
- 创建: `web/src/test/java/com/facturxapi/web/controller/SubscribeControllerTest.java`

```java
package com.facturxapi.web.controller;

import com.facturxapi.web.service.SubscriberService;
import com.facturxapi.web.service.SubscriberService.SubscribeResult;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubscribeController.class)
class SubscribeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubscriberService subscriberService;

    @Test
    void subscribe_success_returns_200() throws Exception {
        when(subscriberService.subscribe("new@example.com")).thenReturn(SubscribeResult.SUCCESS);

        mockMvc.perform(post("/subscribe")
                        .header("HX-Request", "true")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "new@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Merci")));
    }

    @Test
    void subscribe_duplicate_returns_200() throws Exception {
        when(subscriberService.subscribe("existing@example.com")).thenReturn(SubscribeResult.DUPLICATE);

        mockMvc.perform(post("/subscribe")
                        .header("HX-Request", "true")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "existing@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("déjà inscrit")));
    }

    @Test
    void subscribe_invalid_email_returns_400() throws Exception {
        mockMvc.perform(post("/subscribe")
                        .header("HX-Request", "true")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "not-an-email"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("invalide")));
    }

    @Test
    void subscribe_without_htmx_returns_400() throws Exception {
        mockMvc.perform(post("/subscribe")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "test@example.com"))
                .andExpect(status().isBadRequest());
    }
}
```

---

## Task 11: 本地冒烟测试

**命令:**

```bash
# 1. 构建项目（确保 validator-core 和 api 模块可用）
mvn install -DskipTests

# 2. 启动 Postgres（如果未运行）
docker compose up -d db

# 3. 启动 web 模块
mvn -pl web spring-boot:run

# 4. 验证 Flyway 迁移
# 查看日志中应该有 "Flyway: Migrating table 'public.subscribers'" 或类似输出

# 5. 测试 htmx 提交
curl -X POST http://localhost:8080/subscribe \
  -H "HX-Request: true" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "email=test@example.com&_csrf=placeholder" \
  -v

# 6. 验证重复提交
curl -X POST http://localhost:8080/subscribe \
  -H "HX-Request: true" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "email=test@example.com&_csrf=placeholder" \
  -v
```

> 注意：`_csrf` 参数在测试时需要替换为实际的 CSRF token。可以通过先访问 `/validateur-factur-x` 页面获取 token。

---

## Task 12: 提交代码

```bash
git add web/pom.xml web/src/main/resources/application.yml \
  web/src/main/resources/db/migration/V1__create_subscribers.sql \
  web/src/main/java/com/facturxapi/web/entity/Subscriber.java \
  web/src/main/java/com/facturxapi/web/repository/SubscriberRepository.java \
  web/src/main/java/com/facturxapi/web/service/SubscriberService.java \
  web/src/main/java/com/facturxapi/web/controller/SubscribeController.java \
  web/src/main/resources/templates/validateur.html \
  web/src/test/java/com/facturxapi/web/service/SubscriberServiceTest.java \
  web/src/test/java/com/facturxapi/web/controller/SubscribeControllerTest.java
git commit -m "feat(web): 添加邮件订阅表单 (htmx + JPA + Flyway)"
```

---

## 验证清单

- [ ] `mvn test` 全部通过
- [ ] `mvn spring-boot:run` 启动无报错
- [ ] Flyway 迁移成功执行
- [ ] 表单提交返回感谢消息 fragment
- [ ] 重复邮箱返回 "您已订阅" 消息
- [ ] 无效邮箱返回 400
- [ ] 非 htmx 请求返回 400
- [ ] Docker 构建成功 (`mvn package && docker compose up -d --build`)
