package com.facturxapi.web;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.facturxapi.validator.ExtractionException;
import com.facturxapi.validator.FacturxValidator;
import com.facturxapi.validator.Issue;
import com.facturxapi.validator.ValidationResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

/**
 * 免费验证工具页 /validateur-factur-x（SEO 钩子，法语）。
 *
 * <p>交互：上传（htmx）→ 同步校验 → 三色结果 fragment；无 JS 时退化为普通 form POST。
 * 异常契约（spike-decision.md）：校验失败 = 200 正常渲染；ExtractionException = 422；
 * 格式不支持 / 超限 / 空文件 = 400；引擎故障 = 500 + trace id + 法语兜底。
 * 只记元数据日志（大小/类型/结论/耗时），文件不落盘。
 */
@Controller
public class ValidateurController {

    private static final Logger log = LoggerFactory.getLogger(ValidateurController.class);

    private final FacturxValidator validator;

    public ValidateurController(FacturxValidator validator) {
        this.validator = validator;
    }

    @GetMapping("/validateur-factur-x")
    public String page(Model model) {
        model.addAttribute("result", null);
        return "validateur";
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/validateur-factur-x";
    }

    @PostMapping("/validateur-factur-x")
    public Object valider(@RequestPart("file") MultipartFile file,
                          Model model,
                          jakarta.servlet.http.HttpServletRequest request,
                          jakarta.servlet.http.HttpServletResponse response) {
        if (file == null || file.isEmpty()) {
            model.addAttribute("erreur", "Veuillez sélectionner un fichier.");
            return erreurVue(request, response, HttpStatus.BAD_REQUEST);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes(); // 读入内存后 multipart 临时缓冲即释放；不落盘
        } catch (Exception e) {
            throw new IllegalStateException("Lecture du fichier impossible", e);
        }

        String type = detectType(bytes);
        if (type == null) {
            model.addAttribute("erreur", "Format non supporté : seuls les fichiers PDF et XML sont acceptés.");
            return erreurVue(request, response, HttpStatus.BAD_REQUEST);
        }

        long start = System.currentTimeMillis();
        ValidationResult result;
        try {
            result = validator.validate(bytes);
        } catch (ExtractionException e) {
            log.info("validate: type={} size={}B outcome=extraction-failed ({} ms)", type, bytes.length,
                    System.currentTimeMillis() - start);
            model.addAttribute("erreur", "Ceci n'est pas une facture Factur-X (XML intégré introuvable).");
            return erreurVue(request, response, HttpStatus.UNPROCESSABLE_ENTITY);
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("validate: type={} size={}B valid={} errors={} warnings={} ({} ms)",
                type, bytes.length, result.valid(), result.errors().size(), result.warnings().size(), elapsed);

        model.addAttribute("result", toViewModel(result));
        return isHtmx(request) ? "fragments/resultat :: resultat" : "validateur";
    }

    // ---- 异常映射 ----

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object tropVolumineux(Model model,
                                 jakarta.servlet.http.HttpServletRequest request,
                                 jakarta.servlet.http.HttpServletResponse response) {
        model.addAttribute("erreur", "Fichier trop volumineux (max 10 Mo).");
        return erreurVue(request, response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({IllegalStateException.class, Exception.class})
    public Object defaillance(Exception e, Model model,
                              jakarta.servlet.http.HttpServletRequest request,
                              jakarta.servlet.http.HttpServletResponse response) {
        String traceId = UUID.randomUUID().toString();
        log.error("internal error traceId={}", traceId, e);
        model.addAttribute("erreur", "Une erreur interne est survenue (réf : " + traceId + ").");
        return erreurVue(request, response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ---- helpers ----

    /**
     * 错误响应：htmx 请求返回 erreur fragment，否则整页错误视图。
     * 正常返回路径的 @ResponseStatus 不生效，须显式 setStatus。
     */
    private String erreurVue(jakarta.servlet.http.HttpServletRequest request,
                             jakarta.servlet.http.HttpServletResponse response,
                             HttpStatus status) {
        response.setStatus(status.value());
        return isHtmx(request) ? "fragments/resultat :: erreur" : "validateur";
    }

    private static boolean isHtmx(jakarta.servlet.http.HttpServletRequest request) {
        return "true".equals(request.getHeader("HX-Request"));
    }

    /** 前 5 字节 %PDF- 或去 BOM 后首个非空白字符为 '<' → pdf/xml；否则 null。 */
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

    /** 填充每条 issue 的法语说明（frMessage），未命中 fallback 用原始 message。 */
    private static Map<String, Object> toViewModel(ValidationResult result) {
        List<Map<String, Object>> errors = toIssues(result.errors());
        Map<String, Object> view = new HashMap<>();
        view.put("valid", result.valid());
        view.put("errors", errors);
        view.put("warnings", toIssues(result.warnings()));
        return view;
    }

    private static List<Map<String, Object>> toIssues(List<Issue> issues) {
        return issues.stream().map(i -> {
            Map<String, Object> m = new HashMap<>();
            m.put("ruleId", i.ruleId());
            m.put("xpath", i.xpath());
            m.put("fr", RuleMessages.get(i.ruleId()));
            m.put("raw", i.message());
            return m;
        }).toList();
    }
}
