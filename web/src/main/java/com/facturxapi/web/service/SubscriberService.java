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
