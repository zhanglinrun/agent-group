package com.linrun.trigger.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.types.exception.AppException;
import cn.hollis.llm.mentor.agent.entity.AiSession;
import cn.hollis.llm.mentor.agent.entity.record.FileInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@Tag(name = "Dodo Agent Compatibility")
public class DodoAgentCompatibilityController {

    private static final int SUCCESS = 200;
    private static final int ERROR = 500;

    private final UserAccountService userAccountService;
    private final ObjectMapper objectMapper;
    private final DodoNativeAgentService dodoNativeAgentService;

    public DodoAgentCompatibilityController(UserAccountService userAccountService,
                                            ObjectMapper objectMapper,
                                            DodoNativeAgentService dodoNativeAgentService) {
        this.userAccountService = userAccountService;
        this.objectMapper = objectMapper;
        this.dodoNativeAgentService = dodoNativeAgentService;
    }

    @GetMapping(value = "/agent/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> chatStream(@RequestHeader(value = "Authorization", required = false) String token,
                                   @RequestParam String query,
                                   @RequestParam String conversationId) {
        return stream(token, "chat", query, conversationId, "");
    }

    @GetMapping(value = "/agent/file/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> fileStream(@RequestHeader(value = "Authorization", required = false) String token,
                                   @RequestParam String query,
                                   @RequestParam String conversationId,
                                   @RequestParam String fileId) {
        return stream(token, "file", query, conversationId, fileId);
    }

    @GetMapping(value = "/agent/pptx/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> pptStream(@RequestHeader(value = "Authorization", required = false) String token,
                                  @RequestParam String query,
                                  @RequestParam String conversationId) {
        return stream(token, "ppt", query, conversationId, "");
    }

    @GetMapping(value = "/agent/deep/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> deepStream(@RequestHeader(value = "Authorization", required = false) String token,
                                   @RequestParam String query,
                                   @RequestParam String conversationId) {
        return stream(token, "deep", query, conversationId, "");
    }

    @GetMapping(value = "/agent/skills/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    @Operation(summary = "Skills stream")
    public Flux<String> skillsStream(@RequestHeader(value = "Authorization", required = false) String token,
                                     @RequestParam String query,
                                     @RequestParam String conversationId,
                                     @RequestParam(required = false) String fileId) {
        return stream(token, "skills", query, conversationId, fileId);
    }

    @GetMapping(value = "/agent/skills/manual/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    @Operation(summary = "Manual skills stream")
    public Flux<String> manualSkillsStream(@RequestHeader(value = "Authorization", required = false) String token,
                                           @RequestParam String query,
                                           @RequestParam String conversationId,
                                           @RequestParam(required = false) String fileId) {
        return stream(token, "manual-skills", query, conversationId, fileId);
    }

    @GetMapping("/agent/capabilities")
    @Operation(summary = "Agent capability status")
    public DodoResult<Map<String, Object>> capabilities() {
        return DodoResult.success(dodoNativeAgentService.capabilities());
    }

    @GetMapping("/agent/stop")
    public DodoResult<Map<String, Object>> stop(@RequestHeader(value = "Authorization", required = false) String token,
                                                @RequestParam String conversationId) {
        boolean stopped = StringUtils.hasText(conversationId) && dodoNativeAgentService.stop(token, conversationId);
        return DodoResult.success(Map.of("success", stopped, "message", stopped ? "已停止执行" : "没有找到正在执行的任务"));
    }

    @PostMapping(value = "/file/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DodoResult<DodoFileInfo> upload(@RequestHeader(value = "Authorization", required = false) String token,
                                           @RequestParam("file") MultipartFile file,
                                           @RequestParam(required = false) String conversationId) {
        try {
            return DodoResult.success(toFileInfo(dodoNativeAgentService.upload(token, file, conversationId)));
        } catch (Exception e) {
            return DodoResult.error(message(e));
        }
    }

    @GetMapping("/file/info/{fileId}")
    public DodoResult<DodoFileInfo> fileInfo(@RequestHeader(value = "Authorization", required = false) String token,
                                             @PathVariable String fileId) {
        try {
            return DodoResult.success(toFileInfo(dodoNativeAgentService.getFileInfo(token, fileId)));
        } catch (Exception e) {
            return DodoResult.error(message(e));
        }
    }

    @GetMapping("/file/content/{fileId}")
    public DodoResult<Map<String, Object>> fileContent(@RequestHeader(value = "Authorization", required = false) String token,
                                                       @PathVariable String fileId) {
        try {
            String content = dodoNativeAgentService.getFileContent(token, fileId);
            return DodoResult.success(Map.of("content", content, "length", content.length()));
        } catch (Exception e) {
            return DodoResult.error(message(e));
        }
    }

    @DeleteMapping("/file/{fileId}")
    public DodoResult<String> deleteFile(@RequestHeader(value = "Authorization", required = false) String token,
                                         @PathVariable String fileId) {
        try {
            dodoNativeAgentService.deleteFile(token, fileId);
            return DodoResult.success("文件删除成功");
        } catch (Exception e) {
            return DodoResult.error(message(e));
        }
    }

    @GetMapping("/file/list")
    public DodoResult<Map<String, Object>> fileList(@RequestHeader(value = "Authorization", required = false) String token) {
        try {
            List<DodoFileInfo> files = dodoNativeAgentService.listFiles(token)
                    .stream()
                    .map(this::toFileInfo)
                    .toList();
            return DodoResult.success(Map.of("count", files.size(), "files", files));
        } catch (Exception e) {
            return DodoResult.error(message(e));
        }
    }

    @GetMapping("/file/exists/{fileId}")
    public DodoResult<Boolean> fileExists(@RequestHeader(value = "Authorization", required = false) String token,
                                          @PathVariable String fileId) {
        try {
            return DodoResult.success(dodoNativeAgentService.fileExists(token, fileId));
        } catch (Exception e) {
            return DodoResult.error(message(e));
        }
    }

    @GetMapping("/session/{conversationId}")
    public DodoResult<DodoSessionDetail> sessionDetail(@RequestHeader(value = "Authorization", required = false) String token,
                                                       @PathVariable String conversationId) {
        try {
            List<AiSession> messages = dodoNativeAgentService.querySessionMessages(token, conversationId);
            if (messages.isEmpty()) {
                throw new AppException("SESSION_0001", "会话不存在");
            }
            AiSession session = messages.get(0);
            DodoSessionDetail detail = new DodoSessionDetail(
                    conversationId,
                    toDodoAgentType(session.getAgentType()),
                    session.getFileid() == null ? "" : session.getFileid(),
                    toDodoMessages(messages));
            return DodoResult.success(detail);
        } catch (Exception e) {
            return DodoResult.error(message(e));
        }
    }

    @GetMapping("/session/list")
    public DodoResult<DodoPage<DodoSessionListItem>> sessionList(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        try {
            int safePageSize = Math.max(1, Math.min(pageSize, 100));
            int safePageNum = Math.max(1, pageNum);
            UserAccount user = user(token);
            List<AiSession> sessions = dodoNativeAgentService.querySessions(token, safePageNum, safePageSize);
            long total = dodoNativeAgentService.countSessions(token);
            List<DodoSessionListItem> records = sessions.stream()
                    .map(session -> new DodoSessionListItem(
                            dodoNativeAgentService.externalConversationId(user.getUserId(), session.getSessionId()),
                            session.getQuestion(),
                            toDodoAgentType(session.getAgentType()),
                            session.getFileid() == null ? "" : session.getFileid(),
                            session.getUpdateTime()))
                    .toList();
            return DodoResult.success(new DodoPage<>(safePageNum, safePageSize, total, records));
        } catch (Exception e) {
            return DodoResult.error(message(e));
        }
    }

    @DeleteMapping("/session/{conversationId}")
    public DodoResult<String> deleteSession(@RequestHeader(value = "Authorization", required = false) String token,
                                            @PathVariable String conversationId) {
        try {
            dodoNativeAgentService.deleteSession(token, conversationId);
            return DodoResult.success("会话删除成功");
        } catch (Exception e) {
            return DodoResult.error(message(e));
        }
    }

    private Flux<String> stream(String token, String agentType, String query, String conversationId, String fileId) {
        if (!StringUtils.hasText(query)) {
            return Flux.just(dodoEvent("error", Map.of("message", "查询参数不能为空")), dodoEvent("complete", null), "[DONE]");
        }
        return dodoNativeAgentService.stream(token, agentType, query, conversationId, fileId)
                .concatWithValues(dodoEvent("complete", null), "[DONE]")
                .onErrorResume(error -> Flux.just(
                        dodoEvent("error", Map.of("message", message(error))),
                        dodoEvent("complete", null),
                        "[DONE]"));
    }

    private String dodoEvent(String type, Object content) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", type);
        if (content != null) {
            data.put("content", content);
        }
        if (content instanceof List<?> list) {
            data.put("count", list.size());
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return "{\"type\":\"error\",\"content\":\"事件序列化失败\"}";
        }
    }

    private List<DodoMessage> toDodoMessages(List<AiSession> sessions) {
        return sessions.stream()
                .map(session -> new DodoMessage(
                        session.getId(),
                        session.getQuestion() == null ? "" : session.getQuestion(),
                        session.getAnswer() == null ? "" : session.getAnswer(),
                        session.getThinking() == null ? "" : session.getThinking(),
                        session.getTools() == null ? "" : session.getTools(),
                        session.getReference() == null ? "" : session.getReference(),
                        session.getCreateTime(),
                        session.getFileid() == null ? "" : session.getFileid(),
                        session.getRecommend() == null ? "" : session.getRecommend()))
                .toList();
    }

    private DodoFileInfo toFileInfo(FileInfo file) {
        return new DodoFileInfo(
                file.getFileId(),
                file.getFileName(),
                file.getFileType(),
                file.getFileSize(),
                file.getMinioPath(),
                file.getExtractedText(),
                file.getStatus() == null ? "" : file.getStatus().name(),
                file.getConversationId(),
                file.getCreatedAt());
    }

    private UserAccount user(String token) {
        return userAccountService.requireUserByToken(token);
    }

    private String toDodoAgentType(String taskType) {
        return switch (taskType == null ? "" : taskType.toLowerCase()) {
            case "paper", "file" -> "file";
            case "deep-research" -> "deep";
            default -> taskType;
        };
    }

    private String message(Throwable error) {
        return error == null || !StringUtils.hasText(error.getMessage()) ? "处理失败" : error.getMessage();
    }

    public record DodoResult<T>(int code, String message, T data) {
        static <T> DodoResult<T> success(T data) {
            return new DodoResult<>(SUCCESS, "", data);
        }

        static <T> DodoResult<T> error(String message) {
            return new DodoResult<>(ERROR, message, null);
        }
    }

    public record DodoPage<T>(int pageNum, int pageSize, long total, List<T> records) {
    }

    public record DodoSessionListItem(String conversationId,
                                       String question,
                                       String agentType,
                                       String fileid,
                                       LocalDateTime createTime) {
    }

    public record DodoSessionDetail(String conversationId,
                                     String agentType,
                                     String fileid,
                                     List<DodoMessage> messages) {
    }

    public record DodoMessage(Long id,
                               String question,
                               String answer,
                               String thinking,
                               String tools,
                               String reference,
                               LocalDateTime createTime,
                               String fileid,
                               String recommend) {
    }

    public record DodoFileInfo(String fileId,
                                String fileName,
                                String fileType,
                                Long fileSize,
                                String minioPath,
                                String extractedText,
                                String status,
                                String conversationId,
                                LocalDateTime createdAt) {
    }

    private static class DodoMessageBuilder {
        private Long id;
        private String question = "";
        private String answer = "";
        private String thinking = "";
        private String tools = "";
        private String reference = "[]";
        private LocalDateTime createTime;
        private String fileid = "";
        private String recommend = "[]";

        private DodoMessage build() {
            return new DodoMessage(id, question, answer, thinking, tools, reference, createTime, fileid, recommend);
        }
    }
}
