package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class AcademicWorkspaceImageGenerateRequest implements Serializable {

    private String sessionId;
    private String prompt;
    private String mode = "generate";
    private String size = "1024x1024";
    private Integer batchCount = 1;
    private List<String> sourceFileIds = new ArrayList<>();
    private List<String> sourceImageUrls = new ArrayList<>();
    private List<String> maskImageUrls = new ArrayList<>();
}
