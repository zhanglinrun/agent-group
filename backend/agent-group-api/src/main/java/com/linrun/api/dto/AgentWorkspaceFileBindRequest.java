package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AgentWorkspaceFileBindRequest implements Serializable {

    private String fileId;
    private String fileName;
    private String fileType;
    private String folderType;
    private String summary;
    private String contentPreview;
}















