package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DynamicConfigResponse implements Serializable {

    private String configKey;
    private String configValue;
    private String remark;
    private LocalDateTime updateTime;
}
