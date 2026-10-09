package com.facturxapi.api.service;

import com.facturxapi.api.entity.ApiKey;
import com.facturxapi.api.repository.ApiKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ApiKeyService {

    private static final String PREFIX = "facturx_";
    private final ApiKeyRepository repository;

    public ApiKeyService(ApiKeyRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Optional<String> generateFor(String email) {
        // If an active (non-revoked) key already exists for this email, return empty
        Optional<ApiKey> existing = repository.findByEmailAndRevokedAtIsNull(email);
        if (existing.isPresent()) {
            return Optional.empty();
        }
        // If a revoked key exists, delete it before generating a new one
        repository.findByEmail(email)
            .filter(ApiKey::isRevoked)
            .ifPresent(repository::delete);

        String token = PREFIX
            + UUID.randomUUID().toString().replace("-", "")
            + "_" + randomHex(8);
        ApiKey entity = new ApiKey(email, token);
        repository.save(entity);
        return Optional.of(token);
    }

    public Optional<String> resolveEmail(String token) {
        return repository.findByToken(token)
            .filter(k -> !k.isRevoked())
            .map(ApiKey::getEmail);
    }

    /** 返回实体本身，供需要 plan/配额字段的调用方（/generate）。resolveEmail 保留给 /validate 零改动 */
    public Optional<ApiKey> resolveActive(String token) {
        return repository.findByToken(token)
            .filter(k -> !k.isRevoked());
    }

    @Transactional
    public boolean revokeByToken(String token) {
        return repository.findByToken(token)
            .filter(k -> !k.isRevoked())
            .map(k -> {
                k.revoke();
                repository.save(k);
                return true;
            })
            .orElse(false);
    }

    private static String randomHex(int hexLength) {
        StringBuilder sb = new StringBuilder(hexLength);
        byte[] rand = new byte[(hexLength + 1) / 2];
        ThreadLocalRandom.current().nextBytes(rand);
        for (int i = 0; i < hexLength && i < rand.length * 2; i++) {
            int nibble = (i % 2 == 0) ? (rand[i / 2] >> 4) & 0xF : rand[i / 2] & 0xF;
            sb.append(Integer.toHexString(nibble));
        }
        return sb.toString();
    }
}
