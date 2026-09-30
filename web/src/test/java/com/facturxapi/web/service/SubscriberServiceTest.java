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
