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
@Tag(name = "熊博士 Agent Compatibility")
public class BearDoctorAgentCompatibilityController {

    private static final int SUCCESS = 200;
    private static final int ERROR = 500;

    private final UserAccountService userAccountService;
    private final ObjectMapper objectMapper;
    private final BearDoctorNativeAgentService bearDoctorNativeAgentService;

    public BearDoctorAgentCompatibilityController(UserAccountService userAccountService,
                                            ObjectMapper objectMapper,
                                            BearDoctorNativeAgentService bearDoctorNativeAgentService) {
        this.userAccountService = userAccountService;
        this.objectMapper = objectMapper;
        this.bearDoctorNativeAgentService = bearDoctorNativeAgentService;
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
    public BearDoctorResult<Map<String, Object>> capabilities() {
        return BearDoctorResult.success(bearDoctorNativeAgentService.capabilities());
    }

    @GetMapping("/agent/stop")
    public BearDoctorResult<Map<String, Object>> stop(@RequestHeader(value = "Authorization", required = false) String token,
                                                @RequestParam String conversationId) {
        boolean stopped = StringUtils.hasText(conversationId) && bearDoctorNativeAgentService.stop(token, conversationId);
        return BearDoctorResult.success(Map.of("success", stopped, "message", stopped ? "已停止执行" : "没有找到正在执行的任务"));
    }

    @PostMapping(value = "/file/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BearDoctorResult<BearDoctorFileInfo> upload(@RequestHeader(value = "Authorization", required = false) String token,
                                           @RequestParam("file") MultipartFile file,
                                           @RequestParam(required = false) String conversationId) {
        try {
            return BearDoctorResult.success(toFileInfo(bearDoctorNativeAgentService.upload(token, file, conversationId)));
        } catch (Exception e) {
            return BearDoctorResult.error(message(e));
        }
    }

    @GetMapping("/file/info/{fileId}")
    public BearDoctorResult<BearDoctorFileInfo> fileInfo(@RequestHeader(value = "Authorization", required = false) String token,
                                             @PathVariable String fileId) {
        try {
            return BearDoctorResult.success(toFileInfo(bearDoctorNativeAgentService.getFileInfo(token, fileId)));
        } catch (Exception e) {
            return BearDoctorResult.error(message(e));
        }
    }

    @GetMapping("/file/content/{fileId}")
    public BearDoctorResult<Map<String, Object>> fileContent(@RequestHeader(value = "Authorization", required = false) String token,
                                                       @PathVariable String fileId) {
        try {
            String content = bearDoctorNativeAgentService.getFileContent(token, fileId);
            return BearDoctorResult.success(Map.of("content", content, "length", content.length()));
        } catch (Exception e) {
            return BearDoctorResult.error(message(e));
        }
    }

    @DeleteMapping("/file/{fileId}")
    public BearDoctorResult<String> deleteFile(@RequestHeader(value = "Authorization", required = false) String token,
                                         @PathVariable String fileId) {
        try {
            bearDoctorNativeAgentService.deleteFile(token, fileId);
            return BearDoctorResult.success("文件删除成功");
        } catch (Exception e) {
            return BearDoctorResult.error(message(e));
        }
    }

    @GetMapping("/file/list")
    public BearDoctorResult<Map<String, Object>> fileList(@RequestHeader(value = "Authorization", required = false) String token) {
        try {
            List<BearDoctorFileInfo> files = bearDoctorNativeAgentService.listFiles(token)
                    .stream()
                    .map(this::toFileInfo)
                    .toList();
            return BearDoctorResult.success(Map.of("count", files.size(), "files", files));
        } catch (Exception e) {
            return BearDoctorResult.error(message(e));
        }
    }

    @GetMapping("/file/exists/{fileId}")
    public BearDoctorResult<Boolean> fileExists(@RequestHeader(value = "Authorization", required = false) String token,
                                          @PathVariable String fileId) {
        try {
            return BearDoctorResult.success(bearDoctorNativeAgentService.fileExists(token, fileId));
        } catch (Exception e) {
            return BearDoctorResult.error(message(e));
        }
    }

    @GetMapping("/session/{conversationId}")
    public BearDoctorResult<BearDoctorSessionDetail> sessionDetail(@RequestHeader(value = "Authorization", required = false) String token,
                                                       @PathVariable String conversationId) {
        try {
            List<AiSession> messages = bearDoctorNativeAgentService.querySessionMessages(token, conversationId);
            if (messages.isEmpty()) {
                throw new AppException("SESSION_0001", "会话不存在");
            }
            AiSession session = messages.get(0);
            BearDoctorSessionDetail detail = new BearDoctorSessionDetail(
                    conversationId,
                    toBearDoctorAgentType(session.getAgentType()),
                    session.getFileid() == null ? "" : session.getFileid(),
                    toBearDoctorMessages(messages));
            return BearDoctorResult.success(detail);
        } catch (Exception e) {
            return BearDoctorResult.error(message(e));
        }
    }

    @GetMapping("/session/list")
    public BearDoctorResult<BearDoctorPage<BearDoctorSessionListItem>> sessionList(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        try {
            int safePageSize = Math.max(1, Math.min(pageSize, 100));
            int safePageNum = Math.max(1, pageNum);
            UserAccount user = user(token);
            List<AiSession> sessions = bearDoctorNativeAgentService.querySessions(token, safePageNum, safePageSize);
            long total = bearDoctorNativeAgentService.countSessions(token);
            List<BearDoctorSessionListItem> records = sessions.stream()
                    .map(session -> new BearDoctorSessionListItem(
                            bearDoctorNativeAgentService.externalConversationId(user.getUserId(), session.getSessionId()),
                            session.getQuestion(),
                            toBearDoctorAgentType(session.getAgentType()),
                            session.getFileid() == null ? "" : session.getFileid(),
                            session.getUpdateTime()))
                    .toList();
            return BearDoctorResult.success(new BearDoctorPage<>(safePageNum, safePageSize, total, records));
        } catch (Exception e) {
            return BearDoctorResult.error(message(e));
        }
    }

    @DeleteMapping("/session/{conversationId}")
    public BearDoctorResult<String> deleteSession(@RequestHeader(value = "Authorization", required = false) String token,
                                            @PathVariable String conversationId) {
        try {
            bearDoctorNativeAgentService.deleteSession(token, conversationId);
            return BearDoctorResult.success("会话删除成功");
        } catch (Exception e) {
            return BearDoctorResult.error(message(e));
        }
    }

    private Flux<String> stream(String token, String agentType, String query, String conversationId, String fileId) {
        if (!StringUtils.hasText(query)) {
            return Flux.just(bearDoctorEvent("error", Map.of("message", "查询参数不能为空")), bearDoctorEvent("complete", null), "[DONE]");
        }
        return bearDoctorNativeAgentService.stream(token, agentType, query, conversationId, fileId, "", "", "")
                .concatWithValues(bearDoctorEvent("complete", null), "[DONE]")
                .onErrorResume(error -> Flux.just(
                        bearDoctorEvent("error", Map.of("message", message(error))),
                        bearDoctorEvent("complete", null),
                        "[DONE]"));
    }

    private String bearDoctorEvent(String type, Object content) {
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

    private List<BearDoctorMessage> toBearDoctorMessages(List<AiSession> sessions) {
        return sessions.stream()
                .map(session -> new BearDoctorMessage(
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

    private BearDoctorFileInfo toFileInfo(FileInfo file) {
        return new BearDoctorFileInfo(
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

    private String toBearDoctorAgentType(String taskType) {
        return switch (taskType == null ? "" : taskType.toLowerCase()) {
            case "paper", "file" -> "file";
            case "deep-research" -> "deep";
            default -> taskType;
        };
    }

    private String message(Throwable error) {
        if (error == null || !StringUtils.hasText(error.getMessage())) {
            return "处理失败";
        }
        String message = error.getMessage();
        String lower = message.toLowerCase();
        if ((lower.contains("duplicate entry") || lower.contains("sqlintegrityconstraintviolationexception"))
                && (lower.contains("uk_user_biz_flow") || lower.contains("user_quota_flow"))) {
            return "本次请求已处理，请勿重复提交或刷新后重试";
        }
        return message;
    }

    public record BearDoctorResult<T>(int code, String message, T data) {
        static <T> BearDoctorResult<T> success(T data) {
            return new BearDoctorResult<>(SUCCESS, "", data);
        }

        static <T> BearDoctorResult<T> error(String message) {
            return new BearDoctorResult<>(ERROR, message, null);
        }
    }

    public record BearDoctorPage<T>(int pageNum, int pageSize, long total, List<T> records) {
    }

    public record BearDoctorSessionListItem(String conversationId,
                                       String question,
                                       String agentType,
                                       String fileid,
                                       LocalDateTime createTime) {
    }

    public record BearDoctorSessionDetail(String conversationId,
                                     String agentType,
                                     String fileid,
                                     List<BearDoctorMessage> messages) {
    }

    public record BearDoctorMessage(Long id,
                               String question,
                               String answer,
                               String thinking,
                               String tools,
                               String reference,
                               LocalDateTime createTime,
                               String fileid,
                               String recommend) {
    }

    public record BearDoctorFileInfo(String fileId,
                                String fileName,
                                String fileType,
                                Long fileSize,
                                String minioPath,
                                String extractedText,
                                String status,
                                String conversationId,
                                LocalDateTime createdAt) {
    }

    private static class BearDoctorMessageBuilder {
        private Long id;
        private String question = "";
        private String answer = "";
        private String thinking = "";
        private String tools = "";
        private String reference = "[]";
        private LocalDateTime createTime;
        private String fileid = "";
        private String recommend = "[]";

        private BearDoctorMessage build() {
            return new BearDoctorMessage(id, question, answer, thinking, tools, reference, createTime, fileid, recommend);
        }
    }
}
