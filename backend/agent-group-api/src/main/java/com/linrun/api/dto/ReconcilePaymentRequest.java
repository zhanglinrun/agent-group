package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

@Data
public class ReconcilePaymentRequest implements Serializable {

    private String orderId;
    private LocalDate billDate;
}
