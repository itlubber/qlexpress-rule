package com.hengshucredit.rule.server.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuleGovernanceException.class)
    public ResponseEntity<R<Map<String, Object>>> handleRuleGovernance(
            RuleGovernanceException e) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("errorCode", e.getCode());
        data.put("issues", e.getIssues());
        R<Map<String, Object>> body = R.fail(e.getHttpStatus(), e.getMessage());
        body.setData(data);
        return ResponseEntity.status(e.getHttpStatus()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return Result.fail(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        return Result.fail(400, e.getMessage());
    }
}
