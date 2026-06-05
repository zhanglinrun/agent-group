package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class AcademicWorkspaceMragRunRequest implements Serializable {

    private String sessionId;
    private String question;
    private String text;
    private List<String> imageUrls = new ArrayList<>();
    private List<String> fileUrls = new ArrayList<>();
    private List<String> modelCodeList = new ArrayList<>();
    private List<String> sourceTypes = new ArrayList<>();
    private Integer topK = 5;
    private Integer maxResults = 5;
    private Boolean includeMultimodal = true;
    private Boolean includeTableRag = true;
    private Boolean includeDeepSearch = true;
    private Boolean useVector = true;
    private Boolean useElastic = false;
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
