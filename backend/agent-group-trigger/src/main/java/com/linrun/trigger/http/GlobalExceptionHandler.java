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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<Response<Void>> handleAppException(AppException e) {
        log.warn("业务异常，code={}，message={}", e.getCode(), e.getMessage());
        return ResponseEntity.ok(Response.fail(e.getCode(), e.getMessage(), RequestTraceContext.getRequestId()));
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
                "系统繁忙，请稍后再试",
                RequestTraceContext.getRequestId()
        ));
    }
}
