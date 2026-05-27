package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class NotifyRequest implements Serializable {

    private String teamId;
    private List<String> outTradeNoList;
}
