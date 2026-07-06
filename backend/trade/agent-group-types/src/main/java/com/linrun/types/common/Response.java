package com.linrun.types.common;

import com.linrun.types.enums.ResponseCode;
import lombok.Data;

import java.io.Serializable;

@Data
public class Response<T> implements Serializable {

    private String code;
    private String info;
    private String requestId;
    private T data;

    public static <T> Response<T> success(T data) {
        return success(data, null);
    }

    public static <T> Response<T> success(T data, String requestId) {
        Response<T> response = new Response<>();
        response.setCode(ResponseCode.SUCCESS.getCode());
        response.setInfo(ResponseCode.SUCCESS.getInfo());
        response.setRequestId(requestId);
        response.setData(data);
        return response;
    }

    public static <T> Response<T> fail(String code, String info) {
        return fail(code, info, null);
    }

    public static <T> Response<T> fail(String code, String info, String requestId) {
        Response<T> response = new Response<>();
        response.setCode(code);
        response.setInfo(info);
        response.setRequestId(requestId);
        return response;
    }
}















