package com.facturxapi.api.service;

import com.facturxapi.api.entity.ApiKey;
import com.facturxapi.api.repository.ApiKeyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository repository;

    private ApiKeyService service;

    @BeforeEach
    void setUp() {
        service = new ApiKeyService(repository);
    }

    @Test
    void generateFor_creates_token_with_prefix() {
        when(repository.findByEmailAndRevokedAtIsNull("test@example.com"))
            .thenReturn(Optional.empty());

        Optional<String> result = service.generateFor("test@example.com");

        assertTrue(result.isPresent());
        assertTrue(result.get().startsWith("facturx_"));
        // token format: facturx_<uuid-no-dashes>_<8 hex chars>
        String suffix = result.get().substring("facturx_".length());
        assertTrue(suffix.matches("[a-f0-9]{32}_[a-f0-9]{8}"));
        verify(repository).save(argThat(k ->
            "test@example.com".equals(k.getEmail()) && k.getRevokedAt() == null
        ));
    }

    @Test
    void generateFor_returns_empty_if_active_key_exists() {
        ApiKey existing = new ApiKey();
        existing.setEmail("test@example.com");
        existing.setRevokedAt(null);
        when(repository.findByEmailAndRevokedAtIsNull("test@example.com"))
            .thenReturn(Optional.of(existing));

        Optional<String> result = service.generateFor("test@example.com");

        assertTrue(result.isEmpty());
        verify(repository, never()).save(any(ApiKey.class));
    }

    @Test
    void generateFor_regenerates_after_revoke() {
        ApiKey revoked = new ApiKey();
        revoked.setEmail("test@example.com");
        revoked.setRevokedAt(OffsetDateTime.now().minusDays(1));
        // First call finds active (empty), second call finds revoked (delete)
        when(repository.findByEmailAndRevokedAtIsNull("test@example.com"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(revoked));

        Optional<String> result = service.generateFor("test@example.com");

        assertTrue(result.isPresent());
        assertTrue(result.get().startsWith("facturx_"));
        verify(repository).delete(revoked);
        verify(repository).save(any(ApiKey.class));
    }

    @Test
    void resolveEmail_returns_email_for_valid_token() {
        ApiKey key = new ApiKey();
        key.setEmail("test@example.com");
        when(repository.findByToken("facturx_abc123")).thenReturn(Optional.of(key));

        Optional<String> result = service.resolveEmail("facturx_abc123");

        assertTrue(result.isPresent());
        assertEquals("test@example.com", result.get());
    }

    @Test
    void resolveEmail_returns_empty_for_invalid_token() {
        when(repository.findByToken("invalid_token")).thenReturn(Optional.empty());

        Optional<String> result = service.resolveEmail("invalid_token");

        assertTrue(result.isEmpty());
    }

    @Test
    void revokeByToken_sets_revoked_at_and_returns_true() {
        ApiKey key = new ApiKey();
        key.setEmail("test@example.com");
        key.setToken("facturx_valid");
        when(repository.findByToken("facturx_valid")).thenReturn(Optional.of(key));

        boolean result = service.revokeByToken("facturx_valid");

        assertTrue(result);
        assertNotNull(key.getRevokedAt());
        verify(repository).save(key);
    }

    @Test
    void resolveEmail_returns_empty_after_revoke() {
        ApiKey key = new ApiKey();
        key.setEmail("test@example.com");
        key.setToken("facturx_valid");
        key.setRevokedAt(OffsetDateTime.now());
        when(repository.findByToken("facturx_valid")).thenReturn(Optional.of(key));

        Optional<String> result = service.resolveEmail("facturx_valid");

        assertTrue(result.isEmpty());
    }
}
