package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AcademicWorkspaceMragHistoryResponse implements Serializable {

    private String sessionId;
    private Integer total = 0;
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item implements Serializable {
        private String runId;
        private String sessionId;
        private String question;
        private String summary;
        private String status;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private Long durationMillis;
    }
}
