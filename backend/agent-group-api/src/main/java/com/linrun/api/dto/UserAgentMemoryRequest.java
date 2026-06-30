package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserAgentMemoryRequest implements Serializable {

    private String memoryType;
    private String content;
    private Boolean enabled;
}
