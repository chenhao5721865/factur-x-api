package com.facturxapi.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.facturxapi.validator.FacturxValidator;
import com.facturxapi.validator.ValidationResult;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1 遗留必测项：首次实测 SchematronResourcePure 并发安全。
 * 8 线程 × 25 次 XML 路径校验（Schematron 共享状态主战场）。
 */
@SpringBootTest
class ValidatorConcurrencyTest {

    private static final int THREADS = 8;
    private static final int ITERATIONS = 25;

    @Autowired
    private FacturxValidator validator;

    private byte[] sampleXml() throws IOException {
        return Files.readAllBytes(Path.of("../samples/valid-en16931-cii-basic.xml"));
    }

    @Test
    void concurrentValidationIsThreadSafe() throws Exception {
        byte[] xml = sampleXml();
        // 预热：触发懒加载，排除首次初始化的非并发因素
        assertTrue(validator.validate(xml).valid());

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?>[] futures = new Future<?>[THREADS];
            for (int t = 0; t < THREADS; t++) {
                futures[t] = pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < ITERATIONS; i++) {
                        ValidationResult result = validator.validate(xml);
                        assertTrue(result.valid(), "合法样例被误判: " + result);
                    }
                    return null;
                });
            }
            start.countDown();
            for (Future<?> f : futures) {
                assertDoesNotThrow(() -> f.get(2, TimeUnit.MINUTES));
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
