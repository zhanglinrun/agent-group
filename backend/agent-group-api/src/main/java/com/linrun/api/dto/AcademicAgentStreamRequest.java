package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AcademicAgentStreamRequest implements Serializable {

    private String sessionId;
    private String userId;
    private String taskType;
    private String question;
    private String fileId;
    private String imageUrl;
    private String imageName;
    private Boolean webSearchEnabled;
    private String outputStyle;
    private String llmBaseUrl;
    private String llmApiKey;
    private String llmModel;
}
