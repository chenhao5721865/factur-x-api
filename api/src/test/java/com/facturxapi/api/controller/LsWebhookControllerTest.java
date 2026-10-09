package com.facturxapi.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.facturxapi.api.entity.ApiKey;
import com.facturxapi.api.repository.ApiKeyRepository;
import com.facturxapi.api.repository.ApiUsageRepository;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LsWebhookControllerTest {

    /** 与 api/src/test/resources/application.yml 的 app.lemonsqueezy.webhook-secret 一致 */
    private static final String SECRET = "test-webhook-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiUsageRepository apiUsageRepository;

    @BeforeEach
    void setUp() {
        apiUsageRepository.deleteAll();
        apiKeyRepository.deleteAll();
    }

    private ApiKey newKey(String token, String email) {
        ApiKey key = new ApiKey(email, token);
        return apiKeyRepository.save(key);
    }

    private static String sign(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /** 发合法签名 webhook，返回响应状态 */
    private int postWebhook(String payload) throws Exception {
        return mockMvc.perform(post("/api/webhook/lemonsqueezy")
                .header("X-Signature", sign(payload))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andReturn().getResponse().getStatus();
    }

    private static String payload(String eventName, String customApiKey,
                                  String userEmail, String renewsAt, String endsAt) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"meta\": {\"event_name\": \"").append(eventName).append("\"");
        if (customApiKey != null) {
            sb.append(", \"custom_data\": {\"api_key\": \"").append(customApiKey).append("\"}");
        }
        sb.append("}, \"data\": {\"id\": \"sub-123\", \"attributes\": {");
        boolean first = true;
        if (userEmail != null) {
            sb.append("\"user_email\": \"").append(userEmail).append("\"");
            first = false;
        }
        if (renewsAt != null) {
            if (!first) sb.append(", ");
            sb.append("\"renews_at\": \"").append(renewsAt).append("\"");
            first = false;
        }
        if (endsAt != null) {
            if (!first) sb.append(", ");
            sb.append("\"ends_at\": \"").append(endsAt).append("\"");
        }
        sb.append("}}}");
        return sb.toString();
    }

    @Test
    void missingSignature_returns401() throws Exception {
        mockMvc.perform(post("/api/webhook/lemonsqueezy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("subscription_created", null, "a@b.com", null, null)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongSignature_returns401() throws Exception {
        String body = payload("subscription_created", null, "a@b.com", null, null);
        mockMvc.perform(post("/api/webhook/lemonsqueezy")
                .header("X-Signature", "deadbeef")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedPayload_returns401() throws Exception {
        String body = payload("subscription_created", null, "a@b.com", null, null);
        String sig = sign(body);
        String tampered = body.replace("a@b.com", "evil@attacker.com");
        mockMvc.perform(post("/api/webhook/lemonsqueezy")
                .header("X-Signature", sig)
                .contentType(MediaType.APPLICATION_JSON)
                .content(tampered))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void subscriptionCreated_activatesProViaCustomData() throws Exception {
        ApiKey key = newKey("facturx_ck_1", "ck@example.com");
        int status = postWebhook(payload("subscription_created", "facturx_ck_1",
            null, "2026-11-09T00:00:00Z", null));

        assertThat(status).isEqualTo(200);
        ApiKey updated = apiKeyRepository.findByToken("facturx_ck_1").orElseThrow();
        assertThat(updated.getPlan()).isEqualTo("pro");
        assertThat(updated.getPlanExpiresAt()).isEqualTo(OffsetDateTime.parse("2026-11-09T00:00:00Z"));
        assertThat(updated.getLsSubscriptionId()).isEqualTo("sub-123");
    }

    @Test
    void paymentSuccess_activatesPro_andIsIdempotent() throws Exception {
        ApiKey key = newKey("facturx_ps_1", "ps@example.com");
        String body = payload("subscription_payment_success", "facturx_ps_1",
            null, "2026-12-09T00:00:00Z", null);
        postWebhook(body);
        postWebhook(body); // 重放同事件：绝对值写入，幂等

        ApiKey updated = apiKeyRepository.findByToken("facturx_ps_1").orElseThrow();
        assertThat(updated.getPlan()).isEqualTo("pro");
        assertThat(updated.getPlanExpiresAt()).isEqualTo(OffsetDateTime.parse("2026-12-09T00:00:00Z"));
    }

    @Test
    void paymentFailed_leavesPlanUnchanged() throws Exception {
        ApiKey key = newKey("facturx_pf_1", "pf@example.com");
        key.setPlan("pro");
        key.setPlanExpiresAt(OffsetDateTime.now().plusDays(10));
        apiKeyRepository.save(key);

        int status = postWebhook(payload("subscription_payment_failed", "facturx_pf_1",
            null, null, null));

        assertThat(status).isEqualTo(200);
        ApiKey updated = apiKeyRepository.findByToken("facturx_pf_1").orElseThrow();
        assertThat(updated.getPlan()).isEqualTo("pro"); // 到期时间自愈，不做主动降级
    }

    @Test
    void expired_downgradesToFree() throws Exception {
        ApiKey key = newKey("facturx_ex_1", "ex@example.com");
        key.setPlan("pro");
        key.setPlanExpiresAt(OffsetDateTime.now().plusDays(3));
        apiKeyRepository.save(key);

        postWebhook(payload("subscription_expired", "facturx_ex_1", null, null, null));

        ApiKey updated = apiKeyRepository.findByToken("facturx_ex_1").orElseThrow();
        assertThat(updated.getPlan()).isEqualTo("free");
    }

    @Test
    void cancelled_withFutureEndsAt_keepsProUntilEndsAt() throws Exception {
        ApiKey key = newKey("facturx_ca_1", "ca@example.com");
        apiKeyRepository.save(key);
        String endsAt = OffsetDateTime.now().plusDays(15).withNano(0).toString();

        postWebhook(payload("subscription_cancelled", "facturx_ca_1", null, null, endsAt));

        ApiKey updated = apiKeyRepository.findByToken("facturx_ca_1").orElseThrow();
        assertThat(updated.getPlan()).isEqualTo("pro");
        assertThat(updated.getPlanExpiresAt()).isEqualTo(OffsetDateTime.parse(endsAt));
    }

    @Test
    void cancelled_withPastEndsAt_downgradesToFree() throws Exception {
        ApiKey key = newKey("facturx_ca_2", "ca2@example.com");
        key.setPlan("pro");
        apiKeyRepository.save(key);
        String endsAt = OffsetDateTime.now().minusDays(1).withNano(0).toString();

        postWebhook(payload("subscription_cancelled", "facturx_ca_2", null, null, endsAt));

        ApiKey updated = apiKeyRepository.findByToken("facturx_ca_2").orElseThrow();
        assertThat(updated.getPlan()).isEqualTo("free");
    }

    @Test
    void emailFallback_withoutCustomData_attachesByKeyEmail() throws Exception {
        newKey("facturx_em_1", "emailmatch@example.com");
        int status = postWebhook(payload("subscription_created", null,
            "emailmatch@example.com", "2026-11-15T00:00:00Z", null));

        assertThat(status).isEqualTo(200);
        ApiKey updated = apiKeyRepository.findByToken("facturx_em_1").orElseThrow();
        assertThat(updated.getPlan()).isEqualTo("pro");
        assertThat(updated.getLsSubscriptionId()).isEqualTo("sub-123");
    }

    @Test
    void unknownEmail_provisionsNewProKey() throws Exception {
        int status = postWebhook(payload("subscription_created", null,
            "brandnew@example.com", "2026-11-15T00:00:00Z", null));

        assertThat(status).isEqualTo(200);
        Optional<ApiKey> provisioned =
            apiKeyRepository.findByEmailAndRevokedAtIsNull("brandnew@example.com");
        assertThat(provisioned).isPresent();
        assertThat(provisioned.get().getPlan()).isEqualTo("pro");
        assertThat(provisioned.get().getPlanExpiresAt())
            .isEqualTo(OffsetDateTime.parse("2026-11-15T00:00:00Z"));
    }

    @Test
    void unknownEvent_returns200_noSideEffects() throws Exception {
        newKey("facturx_uk_1", "uk@example.com");
        int status = postWebhook(payload("order_created", "facturx_uk_1",
            null, null, null));

        assertThat(status).isEqualTo(200);
        ApiKey updated = apiKeyRepository.findByToken("facturx_uk_1").orElseThrow();
        assertThat(updated.getPlan()).isEqualTo("free");
    }
}
