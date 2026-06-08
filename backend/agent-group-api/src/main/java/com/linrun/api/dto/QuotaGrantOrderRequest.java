package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class QuotaGrantOrderRequest implements Serializable {

    private List<String> orderIds = new ArrayList<>();
}
