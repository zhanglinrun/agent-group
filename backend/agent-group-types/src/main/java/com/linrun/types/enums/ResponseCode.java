package com.linrun.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResponseCode {

    SUCCESS("0000", "成功"),
    PARAM_ERROR("0001", "参数错误"),
    BIZ_ERROR("0002", "业务处理失败"),
    SYSTEM_ERROR("9999", "系统异常");

    private final String code;
    private final String info;
}
