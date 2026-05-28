package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class UpdateOperationalRuleRequest implements Serializable {

    private String ruleKey;
    private String ruleValue;
}
