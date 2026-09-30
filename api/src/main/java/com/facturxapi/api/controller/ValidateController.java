package com.facturxapi.api.controller;

import com.facturxapi.api.service.ApiKeyService;
import com.facturxapi.validator.ExtractionException;
import com.facturxapi.validator.FacturxValidator;
import com.facturxapi.validator.ValidationResult;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ValidateController {

    private static final Logger log = LoggerFactory.getLogger(ValidateController.class);

    private final FacturxValidator validator;
    private final ApiKeyService apiKeyService;

    public ValidateController(FacturxValidator validator, ApiKeyService apiKeyService) {
        this.validator = validator;
        this.apiKeyService = apiKeyService;
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validate(
            @RequestPart("file") MultipartFile file,
            @RequestHeader("X-API-Key") String apiKey) {

        // 1. Resolve email from API key
        String email = apiKeyService.resolveEmail(apiKey).orElse(null);
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid or missing API key"));
        }

        // 2. Read file bytes
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to read file"));
        }

        // 3. Check empty
        if (bytes.length == 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Empty file"));
        }

        // 4. Detect type
        String type = detectType(bytes);
        if (type == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Unsupported file format. Only PDF and XML are accepted."));
        }

        // 5. Validate
        long start = System.currentTimeMillis();
        ValidationResult result;
        try {
            result = validator.validate(bytes);
        } catch (ExtractionException e) {
            log.info("validate: email={} type={} size={}B error=extraction-failed",
                email, type, bytes.length);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", e.getMessage()));
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("validate: email={} type={} size={}B valid={} errors={} warnings={} ({}ms)",
            email, type, bytes.length, result.valid(), result.errors().size(),
            result.warnings().size(), elapsed);

        // 6. Return ValidationResult directly (no wrapper)
        return ResponseEntity.ok(result);
    }

    private static String detectType(byte[] bytes) {
        if (bytes.length >= 5 && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            return "pdf";
        }
        int i = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            i = 3;
        }
        while (i < bytes.length && Character.isWhitespace((char) (bytes[i] & 0xFF))) {
            i++;
        }
        return (i < bytes.length && bytes[i] == '<') ? "xml" : null;
    }
}
