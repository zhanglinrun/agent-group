package com.linrun.types.response;

import com.linrun.types.enums.ResponseCode;
import lombok.Data;

import java.io.Serializable;

@Data
public class Response<T> implements Serializable {

    private String code;
    private String info;
    private T data;

    public static <T> Response<T> success(T data) {
        Response<T> response = new Response<>();
        response.setCode(ResponseCode.SUCCESS.getCode());
        response.setInfo(ResponseCode.SUCCESS.getInfo());
        response.setData(data);
        return response;
    }

    public static <T> Response<T> fail(String code, String info) {
        Response<T> response = new Response<>();
        response.setCode(code);
        response.setInfo(info);
        return response;
    }
}