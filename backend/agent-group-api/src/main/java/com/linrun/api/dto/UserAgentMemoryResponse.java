package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserAgentMemoryResponse implements Serializable {

    private String memoryType;
    private String content;
    private Boolean enabled;
    private String source;
    private String scope;
    private LocalDateTime updateTime;
}
