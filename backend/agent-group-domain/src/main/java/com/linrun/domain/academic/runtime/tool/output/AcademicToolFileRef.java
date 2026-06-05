package com.linrun.domain.academic.runtime.tool.output;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public class AcademicToolFileRef {

    private final String artifactId;
    private final String fileName;
    private final String downloadUrl;
    private final String previewUrl;
    private final String contentType;
    private final long fileSize;

    private AcademicToolFileRef(Builder builder) {
        this.artifactId = safe(builder.artifactId);
        this.fileName = safe(builder.fileName);
        this.downloadUrl = safe(builder.downloadUrl);
        this.previewUrl = safe(builder.previewUrl);
        this.contentType = safe(builder.contentType);
        this.fileSize = Math.max(0L, builder.fileSize);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AcademicToolFileRef fromMap(Map<String, Object> map) {
        Map<String, Object> values = map == null ? Map.of() : map;
        return builder()
                .artifactId(firstText(values.get("artifactId"), values.get("fileId"), values.get("resourceKey")))
                .fileName(firstText(values.get("fileName"), values.get("filename"), values.get("primaryFileName"),
                        values.get("displayName"), values.get("name"), values.get("resourceKey"), values.get("title")))
                .downloadUrl(firstText(values.get("downloadUrl"), values.get("ossUrl"), values.get("domainUrl"),
                        values.get("url"), values.get("previewUrl")))
                .previewUrl(firstText(values.get("previewUrl"), values.get("domainUrl"), values.get("url"),
                        values.get("ossUrl"), values.get("downloadUrl")))
                .contentType(firstText(values.get("contentType"), values.get("mimeType"), values.get("artifactType")))
                .fileSize(longValue(firstText(values.get("fileSize"), values.get("size"))))
                .build();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        putIfPresent(map, "artifactId", artifactId);
        putIfPresent(map, "fileName", fileName);
        putIfPresent(map, "downloadUrl", downloadUrl);
        putIfPresent(map, "previewUrl", previewUrl);
        putIfPresent(map, "contentType", contentType);
        if (fileSize > 0L) {
            map.put("fileSize", fileSize);
        }
        return map;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (StringUtils.hasText(value)) {
            map.put(key, value);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(text(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static final class Builder {

        private String artifactId = "";
        private String fileName = "";
        private String downloadUrl = "";
        private String previewUrl = "";
        private String contentType = "";
        private long fileSize;

        private Builder() {
        }

        public Builder artifactId(String artifactId) {
            this.artifactId = artifactId;
            return this;
        }

        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder downloadUrl(String downloadUrl) {
            this.downloadUrl = downloadUrl;
            return this;
        }

        public Builder previewUrl(String previewUrl) {
            this.previewUrl = previewUrl;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder fileSize(long fileSize) {
            this.fileSize = fileSize;
            return this;
        }

        public AcademicToolFileRef build() {
            return new AcademicToolFileRef(this);
        }
    }
}
