package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserMembershipDTO implements Serializable {

    private String userId;
    private String planCode;
    private String planName;
    private String status;
    private BigDecimal monthlyQuota;
    private BigDecimal monthlyUsedQuota;
    private BigDecimal remainingMonthlyQuota;
    private LocalDateTime cycleStartTime;
    private LocalDateTime cycleEndTime;
    private boolean active;
}
