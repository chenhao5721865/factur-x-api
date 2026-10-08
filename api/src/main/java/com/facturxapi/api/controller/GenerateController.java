package com.facturxapi.api.controller;

import com.facturxapi.api.dto.InvoiceRequest;
import com.facturxapi.api.service.ApiKeyService;
import com.facturxapi.api.service.XmlInvoiceGenerator;
import com.facturxapi.validator.FacturxValidator;
import com.facturxapi.validator.ValidationResult;
import jakarta.validation.Valid;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GenerateController {

    private static final Logger log = LoggerFactory.getLogger(GenerateController.class);

    private final ApiKeyService apiKeyService;
    private final XmlInvoiceGenerator generator;
    private final FacturxValidator validator;

    public GenerateController(ApiKeyService apiKeyService, XmlInvoiceGenerator generator,
                              FacturxValidator validator) {
        this.apiKeyService = apiKeyService;
        this.generator = generator;
        this.validator = validator;
    }

    // 不声明 produces：成功时显式 application/xml，错误体 Map 仍需走 JSON 序列化
    @PostMapping(value = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> generate(
            @Valid @RequestBody InvoiceRequest request,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Missing API key. Provide X-API-Key header."));
        }
        String email = apiKeyService.resolveEmail(apiKey).orElse(null);
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid or missing API key"));
        }

        long start = System.currentTimeMillis();
        byte[] xml = generator.generate(request);

        // 生成即验证：valid=false 视为服务端 bug（500，明细只进日志）
        ValidationResult result = validator.validate(xml);
        if (!result.valid()) {
            log.error("generate: self-validation FAILED invoice={} errors={}",
                request.invoiceNumber(), result.errors());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Generated XML failed internal validation"));
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("generate: email={} invoice={} lines={} size={}B ({}ms)",
            email, request.invoiceNumber(), request.lines().size(), xml.length, elapsed);

        String filename = "invoice-" + sanitizeFilename(request.invoiceNumber()) + ".xml";
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_XML)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .body(xml);
    }

    /** 去掉非 [A-Za-z0-9._-] 字符，防 header 注入。 */
    private static String sanitizeFilename(String invoiceNumber) {
        String cleaned = invoiceNumber.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isEmpty() ? "invoice" : cleaned;
    }
}
