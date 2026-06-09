package com.linrun.domain.academic.runtime.tool.output;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AcademicToolStructuredOutput {

    private final String toolName;
    private final String title;
    private final String summary;
    private final String content;
    private final Map<String, Object> metadata;
    private final List<AcademicToolFileRef> fileRefs;

    private AcademicToolStructuredOutput(Builder builder) {
        this.toolName = safe(builder.toolName);
        this.title = safe(builder.title);
        this.summary = safe(builder.summary);
        this.content = safe(builder.content);
        this.metadata = builder.metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(builder.metadata);
        this.fileRefs = builder.fileRefs == null ? List.of() : List.copyOf(builder.fileRefs);
        if (!StringUtils.hasText(toolName)) {
            throw new IllegalArgumentException("tool name cannot be blank");
        }
    }

    public static Builder builder(String toolName) {
        return new Builder(toolName);
    }

    public String getToolName() {
        return toolName;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getContent() {
        return content;
    }

    public Map<String, Object> getMetadata() {
        return new LinkedHashMap<>(metadata);
    }

    public List<AcademicToolFileRef> getFileRefs() {
        return fileRefs;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Builder {

        private final String toolName;
        private String title = "";
        private String summary = "";
        private String content = "";
        private Map<String, Object> metadata = new LinkedHashMap<>();
        private List<AcademicToolFileRef> fileRefs = new ArrayList<>();

        private Builder(String toolName) {
            this.toolName = toolName;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
            return this;
        }

        public Builder putMetadata(String key, Object value) {
            if (StringUtils.hasText(key) && value != null) {
                this.metadata.put(key.trim(), value);
            }
            return this;
        }

        public Builder fileRefs(List<AcademicToolFileRef> fileRefs) {
            this.fileRefs = fileRefs == null ? new ArrayList<>() : new ArrayList<>(fileRefs);
            return this;
        }

        public Builder addFileRef(AcademicToolFileRef fileRef) {
            if (fileRef != null) {
                this.fileRefs.add(fileRef);
            }
            return this;
        }

        public AcademicToolStructuredOutput build() {
            return new AcademicToolStructuredOutput(this);
        }
    }
}















