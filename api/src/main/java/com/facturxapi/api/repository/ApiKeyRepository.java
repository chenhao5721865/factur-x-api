package com.facturxapi.api.repository;

import com.facturxapi.api.entity.ApiKey;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByToken(String token);
    Optional<ApiKey> findByEmail(String email);
    Optional<ApiKey> findByEmailAndRevokedAtIsNull(String email);
}
