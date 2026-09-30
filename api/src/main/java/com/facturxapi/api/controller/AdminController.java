package com.facturxapi.api.controller;

import com.facturxapi.api.service.ApiKeyService;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final ApiKeyService apiKeyService;

    public AdminController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping("/provision-key")
    public ResponseEntity<?> provision(@RequestParam String email) {
        Optional<String> token = apiKeyService.generateFor(email);
        if (token.isPresent()) {
            return ResponseEntity.ok(Map.of("email", email, "apiKey", token.get()));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", "Active key already exists for: " + email));
    }
}
