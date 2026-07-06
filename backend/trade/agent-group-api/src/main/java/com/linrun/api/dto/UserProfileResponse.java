package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class UserProfileResponse implements Serializable {

    private String userId;
    private String username;
    private String nickname;
    private String email;
    private String role;
    private String status;
    private BigDecimal quotaBalance;
    private BigDecimal frozenQuota;
    private BigDecimal usedQuota;
}















