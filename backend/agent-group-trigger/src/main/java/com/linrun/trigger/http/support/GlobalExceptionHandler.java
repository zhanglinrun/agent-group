package com.linrun.trigger.http.support;

import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.enums.ResponseCode;
import com.linrun.types.exception.AppException;
import com.linrun.types.common.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Locale;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<Response<Void>> handleAppException(AppException e) {
        log.warn("业务异常，code={}，message={}", e.getCode(), e.getMessage());
        return ResponseEntity.ok(Response.fail(e.getCode(), normalizeMessage(e.getMessage()), RequestTraceContext.getRequestId()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<Response<Void>> handleParamException(Exception e) {
        log.warn("参数异常，message={}", e.getMessage());
        return ResponseEntity.badRequest().body(Response.fail(
                ResponseCode.PARAM_ERROR.getCode(),
                ResponseCode.PARAM_ERROR.getInfo(),
                RequestTraceContext.getRequestId()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Void>> handleException(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Response.fail(
                ResponseCode.SYSTEM_ERROR.getCode(),
                normalizeSystemMessage(e),
                RequestTraceContext.getRequestId()
        ));
    }

    private String normalizeSystemMessage(Exception e) {
        String message = e == null ? "" : e.getMessage();
        if (!StringUtils.hasText(message)) {
            return "system busy, please try again later";
        }
        String normalized = normalizeMessage(message);
        return normalized.equals(message) ? "system busy, please try again later" : normalized;
    }

    private String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "operation failed";
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("user group buy take limit reached")) {
            return "group buy participation limit reached";
        }
        if (lower.contains("group team slot is full") || lower.contains("group team quota is full")) {
            return "group team is full";
        }
        if (lower.contains("group team not found") || lower.contains("group lock not found") || lower.contains("group order lock not found")) {
            return "group team not found or expired";
        }
        if (lower.contains("idempotent key conflict")) {
            return "duplicate idempotent key";
        }
        if (lower.contains("request activity does not match market trial activity")) {
            return "group activity has changed, please retry";
        }
        if (lower.contains("user cannot join this group activity")) {
            return "current account cannot join this group activity";
        }
        if (lower.contains("group buy market is downgraded")) {
            return "group buy activity is temporarily unavailable";
        }
        if (lower.contains("user is outside market cut range")) {
            return "current account is outside activity range";
        }
        if (lower.contains("source and channel are blocked")) {
            return "current channel cannot join this activity";
        }
        if (lower.contains("product not found")) {
            return "quota product not found or offline";
        }
        if (lower.contains("pay order not found")) {
            return "pay order not found";
        }
        if (lower.contains("refund order not found")) {
            return "refund order not found";
        }
        if (lower.contains("order not found or user mismatch")) {
            return "order not found or user mismatch";
        }
        if (lower.contains("order not found")) {
            return "order not found";
        }
        if (lower.contains("cannot be blank") || lower.contains("cannot be empty") || lower.contains("is required")) {
            return "required information is missing";
        }
        if (lower.contains("request cannot be null")) {
            return "request cannot be null";
        }
        if (lower.contains("group buy timeout unformed")) {
            return "group buy timed out before forming";
        }
        if (lower.contains("too many requests")) {
            return "too many requests, please try again later";
        }
        if (lower.contains("human approval required")) {
            return "human approval required";
        }
        if (lower.contains("human approval expired")) {
            return "human approval expired";
        }
        if (lower.contains("human approval user mismatch")) {
            return "human approval user mismatch";
        }
        if (lower.contains("human approval is not approved")) {
            return "human approval is not approved";
        }
        if (lower.contains("human approval action mismatch") || lower.contains("human approval biz mismatch")) {
            return "human approval information mismatch";
        }
        if (lower.contains("human approval not found")) {
            return "human approval not found";
        }
        if ((lower.contains("duplicate entry") || lower.contains("sqlintegrityconstraintviolationexception"))
                && (lower.contains("uk_user_biz_flow") || lower.contains("user_quota_flow"))) {
            return "request already processed, please do not submit repeatedly";
        }
        return message;
    }
}