package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author linrun.com
 * @description
 * @create 2026-05-13 上午9:28
 */
@Data
public class OrderDeltaDTO implements Serializable {

    private String orderNo;
    private String tradeType;
    private String status;
    private String currentStatus;
    private String displayStatus;
    private String message;
}















