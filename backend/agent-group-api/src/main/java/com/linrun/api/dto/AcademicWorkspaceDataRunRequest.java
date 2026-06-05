package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class AcademicWorkspaceDataRunRequest implements Serializable {

    private String sessionId;
    private String question;
    private List<Map<String, Object>> rows = new ArrayList<>();
    private List<String> columns = new ArrayList<>();
    private List<String> modelCodeList = new ArrayList<>();
    private List<Map<String, Object>> schemaInfo = new ArrayList<>();
    private String businessKnowledge;
    private String dbType = "mysql";
    private Boolean useVector = true;
    private Boolean useElastic = false;
    private Integer topK = 5;
    private Integer maxSteps = 10;
    private Boolean includeTableRag = true;
    private Boolean includeNl2Sql = true;
    private Boolean includeAnalysis = true;
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
