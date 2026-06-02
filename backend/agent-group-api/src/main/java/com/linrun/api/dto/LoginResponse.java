package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LoginResponse implements Serializable {

    private String token;
    private LocalDateTime expireTime;
    private String userId;
    private String username;
    private String nickname;
    private String role;
    private BigDecimal quotaBalance;
}
