package com.linrun.trigger.http.agent.support;

import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.trigger.agent.entity.record.FileInfo;
import com.linrun.trigger.agent.service.FileManageService;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 链路里跨组复用的身份与会话上下文、文件归属解析逻辑，从 AcademicAgentNativeService 抽出。
 * 后续拆分出的会话、文件、能力等 Service 都注入这里复用同一套口径，避免每组各写一份。
 */
@Component
public class AgentContextResolver {

    private final UserAccountService userAccountService;
    private final FileManageService fileManageService;

    public AgentContextResolver(UserAccountService userAccountService, FileManageService fileManageService) {
        this.userAccountService = userAccountService;
        this.fileManageService = fileManageService;
    }

    public UserAccount user(String token) {
        return userAccountService.requireUserByToken(token);
    }

    public String internalConversationId(String userId, String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            throw new AppException("0001", "会话编号不能为空");
        }
        return userId + ":" + conversationId.trim();
    }

    public Long parseRecordId(String messageId) {
        if (!StringUtils.hasText(messageId)) {
            return null;
        }
        try {
            return Long.parseLong(messageId.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public List<String> splitFileIds(String fileIds) {
        if (!StringUtils.hasText(fileIds)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String fileId : fileIds.split("[,，\\s]+")) {
            String trimmed = fileId == null ? "" : fileId.trim();
            if (StringUtils.hasText(trimmed) && !result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    public void assertOwnedFile(String userId, FileInfo fileInfo) {
        String prefix = userId + ":";
        if (fileInfo == null || !StringUtils.hasText(fileInfo.getConversationId())
                || !fileInfo.getConversationId().startsWith(prefix)) {
            throw new AppException("FILE_0001", "文件不存在或无权访问");
        }
    }

    public void validateFileAccess(String userId, String internalConversationId, String fileId) {
        if (!StringUtils.hasText(fileId)) {
            return;
        }
        for (String currentFileId : splitFileIds(fileId)) {
            FileInfo fileInfo = fileManageService.getFileInfo(currentFileId);
            assertOwnedFile(userId, fileInfo);
        }
    }

    public String blank(String value) {
        return value == null ? "" : value;
    }

    public static String normalizeAgentType(String agentType) {
        String type = StringUtils.hasText(agentType) ? agentType.trim().toLowerCase() : "chat";
        return switch (type) {
            case "file", "paper" -> "file";
            case "ppt", "pptx" -> "ppt";
            case "deep", "deep-research" -> "deep";
            case "image", "image-generation", "workspace-image" -> "image";
            case "trade-diagnosis", "diagnose-trade", "order-diagnosis",
                 "workspace-trade-diagnosis", "workspace-trade", "trade", "trade-flow", "group-trade" ->
                    "trade-diagnosis";
            case "data", "data-qa", "workspace-data", "nl2sql", "table-rag" -> "data";
            case "mrag", "multi-modal-rag", "multimodal-rag", "workspace-mrag" -> "mrag";
            case "skills" -> "skills";
            case "manual", "manual-skills", "skills-manual" -> "manual-skills";
            default -> "chat";
        };
    }
}
