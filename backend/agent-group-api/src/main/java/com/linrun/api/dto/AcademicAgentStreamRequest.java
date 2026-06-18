package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class AcademicAgentStreamRequest implements Serializable {

    private String sessionId;
    private String projectId;
    private String threadId;
    private String userId;
    private String taskType;
    private String taskMode;
    private String question;
    private String fileId;
    private List<String> selectedFileIds = new ArrayList<>();
    private String imageUrl;
    private String imageName;
    private Boolean webSearchEnabled;
    private String outputStyle;
    private String llmBaseUrl;
    private String llmApiKey;
    private String llmModel;
    /** 断点续跑凭证：首次执行无值（服务端生成并下发 checkpoint 事件），中断后续跑时回传。 */
    private String continueTraceId;
}















