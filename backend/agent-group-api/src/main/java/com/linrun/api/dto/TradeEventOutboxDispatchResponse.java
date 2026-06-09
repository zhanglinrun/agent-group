package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class TradeEventOutboxDispatchResponse implements Serializable {

    private int waitCount;
    private int successCount;
    private int retryCount;
    private int deadLetterCount;
}















