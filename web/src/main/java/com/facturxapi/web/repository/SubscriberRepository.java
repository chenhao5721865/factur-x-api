package com.facturxapi.web.repository;

import com.facturxapi.web.entity.Subscriber;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriberRepository extends JpaRepository<Subscriber, String> {
    boolean existsByEmail(String email);
}
