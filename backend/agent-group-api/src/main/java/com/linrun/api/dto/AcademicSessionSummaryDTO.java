package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class AcademicSessionSummaryDTO implements Serializable {

    private String sessionId;
    private String taskType;
    private String title;
    private String lastMessage;
    private LocalDateTime updateTime;
}
