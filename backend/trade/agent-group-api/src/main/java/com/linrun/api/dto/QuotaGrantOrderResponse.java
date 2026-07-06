package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class QuotaGrantOrderResponse implements Serializable {

    private int requestedCount;
    private int processedCount;
    private List<String> processedOrderIds = new ArrayList<>();
    private String message;
}















