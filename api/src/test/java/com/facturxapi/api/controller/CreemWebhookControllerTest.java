package com.facturxapi.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.facturxapi.api.entity.ApiKey;
import com.facturxapi.api.repository.ApiKeyRepository;
import com.facturxapi.api.repository.ApiUsageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CreemWebhookControllerTest {

    /** 与 api/src/test/resources/application.yml 的 app.creem.webhook-secret 一致 */
    private static final String SECRET = "test-creem-secret";
    private static final String FUTURE = "2030-01-15T00:00:00Z";
    private static final String FUTURE_LATER = "2030-02-15T00:00:00Z";
    private static final String PAST = "2020-01-15T00:00:00Z";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiUsageRepository apiUsageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        apiUsageRepository.deleteAll();
        apiKeyRepository.deleteAll();
    }

    private ApiKey newKey(String token, String email) {
        return apiKeyRepository.save(new ApiKey(email, token));
    }

    private ApiKey newProKey(String token, String email, OffsetDateTime expires) {
        return apiKeyRepository.save(new ApiKey(email, token, OffsetDateTime.now(), null,
            "pro", expires, null));
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

    private int postSigned(String payload) throws Exception {
        return mockMvc.perform(post("/api/webhook/creem")
                .header("creem-signature", sign(payload))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andReturn().getResponse().getStatus();
    }

    private String payload(String eventType, String subId, String status, String email,
                           String metadataKey, String periodEnd) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("eventType", eventType);
        ObjectNode object = root.putObject("object");
        object.put("id", subId);
        if (status != null) {
            object.put("status", status);
        }
        if (email != null) {
            object.putObject("customer").put("email", email);
        }
        if (metadataKey != null) {
            object.putObject("metadata").put("api_key", metadataKey);
        }
        if (periodEnd != null) {
            object.put("current_period_end_date", periodEnd);
        }
        return objectMapper.writeValueAsString(root);
    }

    // ---------- 签名 / 协议错误 ----------

    @Test
    void blankSecret_returns503_failClosed() {
        CreemWebhookController controller =
            new CreemWebhookController(null, objectMapper, "");
        ResponseEntity<?> response = controller.handle("{}", "deadbeef");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void missingSignature_returns401() throws Exception {
        String body = payload("subscription.active", "sub_x", "active", "a@b.com", null, FUTURE);
        mockMvc.perform(post("/api/webhook/creem")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongSignature_returns401() throws Exception {
        String body = payload("subscription.active", "sub_x", "active", "a@b.com", null, FUTURE);
        mockMvc.perform(post("/api/webhook/creem")
                .header("creem-signature", "deadbeef")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedJson_returns400() throws Exception {
        int status = postSigned("{not-json");

        assertThat(status).isEqualTo(400);
    }

    @Test
    void missingEventType_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("object", Map.of("id", "sub_x")));

        assertThat(postSigned(body)).isEqualTo(400);
    }

    // ---------- 授权 / 续费 / 乱序 ----------

    @Test
    void active_grantsPro_byMetadata_andStoresSubscriptionId() throws Exception {
        newKey("facturx_act_1", "owner@example.com");
        String body = payload("subscription.active", "sub_creem_1", "active",
            "buyer@other.com", "facturx_act_1", FUTURE);

        assertThat(postSigned(body)).isEqualTo(200);

        ApiKey updated = apiKeyRepository.findByToken("facturx_act_1").orElseThrow();
        assertThat(updated.getPlan()).isEqualTo("pro");
        assertThat(updated.getPlanExpiresAt()).isEqualTo(OffsetDateTime.parse(FUTURE));
        assertThat(updated.getCreemSubscriptionId()).isEqualTo("sub_creem_1");
    }

    @Test
    void paidFirst_thenOlderActive_replayDoesNotShortenExpiry() throws Exception {
        newKey("facturx_act_2", "owner2@example.com");
        postSigned(payload("subscription.paid", "sub_creem_2", "paid",
            "owner2@example.com", "facturx_act_2", FUTURE_LATER));
        postSigned(payload("subscription.active", "sub_creem_2", "active",
            "owner2@example.com", "facturx_act_2", FUTURE));

        ApiKey updated = apiKeyRepository.findByToken("facturx_act_2").orElseThrow();
        assertThat(updated.getPlan()).isEqualTo("pro");
        assertThat(updated.getPlanExpiresAt()).isEqualTo(OffsetDateTime.parse(FUTURE_LATER));
    }

    @Test
    void paid_duplicateDelivery_isIdempotent() throws Exception {
        newKey("facturx_act_3", "owner3@example.com");
        String body = payload("subscription.paid", "sub_creem_3", "paid",
            "owner3@example.com", "facturx_act_3", FUTURE);

        postSigned(body);
        postSigned(body);

        ApiKey updated = apiKeyRepository.findByToken("facturx_act_3").orElseThrow();
        assertThat(updated.getPlan()).isEqualTo("pro");
        assertThat(updated.getPlanExpiresAt()).isEqualTo(OffsetDateTime.parse(FUTURE));
        assertThat(updated.getCreemSubscriptionId()).isEqualTo("sub_creem_3");
    }

    @Test
    void scheduledCancel_keepsProUntilPeriodEnd() throws Exception {
        newKey("facturx_sc_1", "sc@example.com");
        postSigned(payload("subscription.scheduled_cancel", "sub_sc_1", "scheduled_cancel",
            "sc@example.com", "facturx_sc_1", FUTURE));

        ApiKey updated = apiKeyRepository.findByToken("facturx_sc_1").orElseThrow();
        assertThat(updated.getPlan()).isEqualTo("pro");
        assertThat(updated.getPlanExpiresAt()).isEqualTo(OffsetDateTime.parse(FUTURE));
    }

    @Test
    void canceled_futurePeriodEnd_keepsPro() throws Exception {
        newProKey("facturx_ca_1", "ca@example.com", OffsetDateTime.now().plusDays(10));
        postSigned(payload("subscription.canceled", "sub_ca_1", "canceled",
            "ca@example.com", "facturx_ca_1", FUTURE));

        ApiKey updated = apiKeyRepository.findByToken("facturx_ca_1").orElseThrow();
        assertThat(updated.getPlan()).isEqualTo("pro");
        assertThat(updated.getPlanExpiresAt()).isEqualTo(OffsetDateTime.parse(FUTURE));
    }

    @Test
    void canceled_pastPeriodEnd_downgradesToFree() throws Exception {
        newProKey("facturx_ca_2", "ca2@example.com", OffsetDateTime.now().minusDays(1));
        postSigned(payload("subscription.canceled", "sub_ca_2", "canceled",
            "ca2@example.com", "facturx_ca_2", PAST));

        ApiKey updated = apiKeyRepository.findByToken("facturx_ca_2").orElseThrow();
        assertThat(updated.getPlan()).isEqualTo("free");
    }

    @Test
    void expired_outOfOrder_keepsProWhileDbExpiryIsInFuture() throws Exception {
        newProKey("facturx_ex_1", "ex@example.com", OffsetDateTime.now().plusDays(30));
        postSigned(payload("subscription.expired", "sub_ex_1", "expired",
            "ex@example.com", "facturx_ex_1", PAST));

        ApiKey updated = apiKeyRepository.findByToken("facturx_ex_1").orElseThrow();
        assertThat(updated.getPlan()).isEqualTo("pro");
    }

    @Test
    void expired_afterPeriodEnd_downgradesToFree() throws Exception {
        newProKey("facturx_ex_2", "ex2@example.com", OffsetDateTime.now().minusDays(2));
        postSigned(payload("subscription.expired", "sub_ex_2", "expired",
            "ex2@example.com", "facturx_ex_2", PAST));

        ApiKey updated = apiKeyRepository.findByToken("facturx_ex_2").orElseThrow();
        assertThat(updated.getPlan()).isEqualTo("free");
    }

    @Test
    void paused_dropsAccessImmediately_evenWithFutureExpiry() throws Exception {
        newProKey("facturx_pa_1", "pa@example.com", OffsetDateTime.now().plusDays(20));
        postSigned(payload("subscription.paused", "sub_pa_1", "paused",
            "pa@example.com", "facturx_pa_1", FUTURE));

        ApiKey updated = apiKeyRepository.findByToken("facturx_pa_1").orElseThrow();
        assertThat(updated.getPlan()).isEqualTo("free");
        assertThat(updated.isProActive()).isFalse();
    }

    @Test
    void pastDueAndUnpaid_keepProUntilExpiry() throws Exception {
        newProKey("facturx_pd_1", "pd@example.com", OffsetDateTime.now().plusDays(10));
        newProKey("facturx_up_1", "up@example.com", OffsetDateTime.now().plusDays(10));

        postSigned(payload("subscription.past_due", "sub_pd_1", "past_due",
            "pd@example.com", "facturx_pd_1", FUTURE));
        postSigned(payload("subscription.unpaid", "sub_up_1", "unpaid",
            "up@example.com", "facturx_up_1", FUTURE));

        assertThat(apiKeyRepository.findByToken("facturx_pd_1").orElseThrow().getPlan())
            .isEqualTo("pro");
        assertThat(apiKeyRepository.findByToken("facturx_up_1").orElseThrow().getPlan())
            .isEqualTo("pro");
    }

    @Test
    void subscriptionUpdate_active_grantsPro() throws Exception {
        newKey("facturx_upd_1", "upd@example.com");
        postSigned(payload("subscription.update", "sub_upd_1", "active",
            "upd@example.com", "facturx_upd_1", FUTURE));

        ApiKey updated = apiKeyRepository.findByToken("facturx_upd_1").orElseThrow();
        assertThat(updated.getPlan()).isEqualTo("pro");
        assertThat(updated.getPlanExpiresAt()).isEqualTo(OffsetDateTime.parse(FUTURE));
    }

    @Test
    void subscriptionUpdate_paused_downgrades() throws Exception {
        newProKey("facturx_upd_2", "upd2@example.com", OffsetDateTime.now().plusDays(20));
        postSigned(payload("subscription.update", "sub_upd_2", "paused",
            "upd2@example.com", "facturx_upd_2", FUTURE));

        assertThat(apiKeyRepository.findByToken("facturx_upd_2").orElseThrow().getPlan())
            .isEqualTo("free");
    }

    // ---------- 归属 ----------

    @Test
    void missingMetadata_attributesByCustomerEmail() throws Exception {
        newKey("facturx_em_1", "emailmatch@example.com");
        postSigned(payload("subscription.active", "sub_em_1", "active",
            "emailmatch@example.com", null, FUTURE));

        ApiKey updated = apiKeyRepository.findByToken("facturx_em_1").orElseThrow();
        assertThat(updated.getPlan()).isEqualTo("pro");
        assertThat(updated.getCreemSubscriptionId()).isEqualTo("sub_em_1");
    }

    @Test
    void missingMetadataAndEmail_attributesByStoredSubscriptionId() throws Exception {
        ApiKey key = newKey("facturx_sid_1", "sid@example.com");
        key.setCreemSubscriptionId("sub_sid_1");
        apiKeyRepository.save(key);

        postSigned(payload("subscription.paid", "sub_sid_1", "paid", null, null, FUTURE));

        ApiKey updated = apiKeyRepository.findByToken("facturx_sid_1").orElseThrow();
        assertThat(updated.getPlan()).isEqualTo("pro");
        assertThat(updated.getPlanExpiresAt()).isEqualTo(OffsetDateTime.parse(FUTURE));
    }

    @Test
    void noEmail_provisionsPlaceholderKey() throws Exception {
        postSigned(payload("subscription.active", "sub_ph_1", "active", null, null, FUTURE));

        Optional<ApiKey> provisioned =
            apiKeyRepository.findByEmailAndRevokedAtIsNull("unknown+sub_ph_1@creem.local");
        assertThat(provisioned).isPresent();
        assertThat(provisioned.get().getPlan()).isEqualTo("pro");
        assertThat(provisioned.get().getCreemSubscriptionId()).isEqualTo("sub_ph_1");
    }

    @Test
    void unknownEvent_returns200_noSideEffects() throws Exception {
        newKey("facturx_uk_1", "uk@example.com");
        int status = postSigned(payload("order.created", "sub_uk_1", null,
            "uk@example.com", "facturx_uk_1", FUTURE));

        assertThat(status).isEqualTo(200);
        assertThat(apiKeyRepository.findByToken("facturx_uk_1").orElseThrow().getPlan())
            .isEqualTo("free");
    }
}
