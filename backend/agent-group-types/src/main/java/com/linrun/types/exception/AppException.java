package com.linrun.types.exception;

import lombok.Getter;

/**
 * @author linrun.com
 * @description
 * @create 2026-05-13 上午9:11
 */
@Getter
public class AppException extends RuntimeException {

    private final String code;

    public AppException(String code, String message) {
        super(message);
        this.code = code;
    }
}