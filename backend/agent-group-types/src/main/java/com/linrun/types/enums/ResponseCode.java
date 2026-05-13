package com.linrun.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author linrun.com
 * @description
 * @create 2026-05-13 上午9:10
 */
@Getter
@AllArgsConstructor
public enum ResponseCode {

    SUCCESS("0000", "success"),
    PARAM_ERROR("0001", "param error"),
    BIZ_ERROR("0002", "business error"),
    SYSTEM_ERROR("9999", "system error");

    private final String code;
    private final String info;
}
