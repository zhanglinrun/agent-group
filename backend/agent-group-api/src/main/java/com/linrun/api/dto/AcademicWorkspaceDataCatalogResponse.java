package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class AcademicWorkspaceDataCatalogResponse implements Serializable {

    private List<String> defaultModelCodeList = new ArrayList<>();
    private List<Model> models = new ArrayList<>();
    private List<String> sampleQuestions = new ArrayList<>();

    @Data
    public static class Model implements Serializable {
        private String modelCode;
        private String displayName;
        private String tableName;
        private String description;
        private List<Column> columns = new ArrayList<>();
        private List<String> defaultRecallFields = new ArrayList<>();
    }

    @Data
    public static class Column implements Serializable {
        private String name;
        private String type;
        private String description;
        private boolean metric;
    }
}
