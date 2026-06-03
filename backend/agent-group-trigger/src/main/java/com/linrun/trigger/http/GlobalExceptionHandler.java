package com.linrun.trigger.http;

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
            return "系统繁忙，请稍后再试";
        }
        String normalized = normalizeMessage(message);
        return normalized.equals(message) ? "系统繁忙，请稍后再试" : normalized;
    }

    private String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "操作失败";
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("user group buy take limit reached")) {
            return "你已达到该拼团活动的参与次数上限";
        }
        if (lower.contains("group team slot is full") || lower.contains("group team quota is full")) {
            return "拼团队伍名额已满";
        }
        if (lower.contains("group team not found") || lower.contains("group lock not found") || lower.contains("group order lock not found")) {
            return "拼团队伍不存在或已失效";
        }
        if (lower.contains("idempotent key conflict")) {
            return "请勿重复提交不同的拼团订单";
        }
        if (lower.contains("request activity does not match market trial activity")) {
            return "当前拼团活动已变化，请刷新后重试";
        }
        if (lower.contains("user cannot join this group activity")) {
            return "当前账号暂不能参加这个拼团活动";
        }
        if (lower.contains("group buy market is downgraded")) {
            return "拼团活动暂时不可用";
        }
        if (lower.contains("user is outside market cut range")) {
            return "当前账号暂不在活动范围内";
        }
        if (lower.contains("source and channel are blocked")) {
            return "当前渠道暂不能参加活动";
        }
        if (lower.contains("product not found")) {
            return "额度包不存在或已下架";
        }
        if (lower.contains("pay order not found")) {
            return "支付单不存在";
        }
        if (lower.contains("refund order not found")) {
            return "退款单不存在";
        }
        if (lower.contains("order not found or user mismatch")) {
            return "订单不存在或不属于当前用户";
        }
        if (lower.contains("order not found")) {
            return "订单不存在";
        }
        if (lower.contains("cannot be blank") || lower.contains("cannot be empty") || lower.contains("is required")) {
            return "请补全必要信息";
        }
        if (lower.contains("request cannot be null")) {
            return "请求参数不能为空";
        }
        if (lower.contains("group buy timeout unformed")) {
            return "拼团超时未成团";
        }
        if (lower.contains("too many requests")) {
            return "操作过于频繁，请稍后再试";
        }
        if (lower.contains("human approval required")) {
            return "该操作需要人工确认";
        }
        if (lower.contains("human approval expired")) {
            return "人工确认已过期";
        }
        if (lower.contains("human approval user mismatch")) {
            return "人工确认用户不匹配";
        }
        if (lower.contains("human approval is not approved")) {
            return "人工确认未通过";
        }
        if (lower.contains("human approval action mismatch") || lower.contains("human approval biz mismatch")) {
            return "人工确认信息不匹配";
        }
        if (lower.contains("human approval not found")) {
            return "人工确认记录不存在";
        }
        if ((lower.contains("duplicate entry") || lower.contains("sqlintegrityconstraintviolationexception"))
                && (lower.contains("uk_user_biz_flow") || lower.contains("user_quota_flow"))) {
            return "本次请求已处理，请勿重复提交或刷新后重试";
        }
        return message;
    }
}
