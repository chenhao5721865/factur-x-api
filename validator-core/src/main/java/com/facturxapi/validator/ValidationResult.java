package com.facturxapi.validator;

import java.util.List;

/**
 * 一次校验的完整结论。valid = 无 ERROR 级问题（WARNING 不影响通过）。
 */
public record ValidationResult(boolean valid, String profile, List<Issue> errors, List<Issue> warnings) {

    public static ValidationResult of(List<Issue> issues) {
        List<Issue> errors = issues.stream().filter(i -> i.severity() == Severity.ERROR).toList();
        List<Issue> warnings = issues.stream().filter(i -> i.severity() == Severity.WARNING).toList();
        return new ValidationResult(errors.isEmpty(), null, errors, warnings); // profile 检测是后续里程碑任务
    }
}
