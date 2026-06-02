package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class AcademicSessionDetailResponse implements Serializable {

    private String sessionId;
    private List<Message> messages = new ArrayList<>();

    @Data
    public static class Message implements Serializable {
        private String role;
        private String content;
        private String imageUrl;
        private java.time.LocalDateTime createTime;
    }
}
