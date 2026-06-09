package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class OperationalRuleResponse implements Serializable {

    private String ruleKey;
    private String ruleValue;
    private String ruleGroup;
    private String description;
    private LocalDateTime updateTime;
}















