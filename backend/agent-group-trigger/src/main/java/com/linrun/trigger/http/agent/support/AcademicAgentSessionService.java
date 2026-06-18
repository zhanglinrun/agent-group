package com.linrun.trigger.http.agent.support;

import com.linrun.domain.account.model.UserAccount;
import com.linrun.trigger.agent.entity.AiSession;
import com.linrun.trigger.agent.entity.record.FileInfo;
import com.linrun.trigger.agent.entity.record.pptx.AiPptInst;
import com.linrun.trigger.agent.entity.vo.SaveQuestionRequest;
import com.linrun.trigger.agent.entity.vo.UpdateAnswerRequest;
import com.linrun.trigger.agent.mapper.AiSessionMapper;
import com.linrun.trigger.agent.service.AgentTaskManager;
import com.linrun.trigger.agent.service.AiPptInstService;
import com.linrun.trigger.agent.service.AiSessionService;
import com.linrun.trigger.agent.service.FileInfoService;
import com.linrun.trigger.agent.service.FileManageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学术 Agent 的会话管理服务，从 AcademicAgentNativeService 抽出。
 * 负责会话查询、消息回放、删除、按消息回滚、停止运行，以及确定性提问落库和 agentType 回填。
 */
@Service
public class AcademicAgentSessionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcademicAgentSessionService.class);

    private final AiSessionService sessionService;
    private final AgentTaskManager taskManager;
    private final AiSessionMapper aiSessionMapper;
    private final AiPptInstService aiPptInstService;
    private final FileInfoService fileInfoService;
    private final FileManageService fileManageService;
    private final AgentContextResolver agentContextResolver;

    public AcademicAgentSessionService(AiSessionService sessionService,
                                       AgentTaskManager taskManager,
                                       AiSessionMapper aiSessionMapper,
                                       AiPptInstService aiPptInstService,
                                       FileInfoService fileInfoService,
                                       FileManageService fileManageService,
                                       AgentContextResolver agentContextResolver) {
        this.sessionService = sessionService;
        this.taskManager = taskManager;
        this.aiSessionMapper = aiSessionMapper;
        this.aiPptInstService = aiPptInstService;
        this.fileInfoService = fileInfoService;
        this.fileManageService = fileManageService;
        this.agentContextResolver = agentContextResolver;
    }

    public void saveDeterministicTurn(String token,
                                      String agentType,
                                      String query,
                                      String conversationId,
                                      String fileId,
                                      String answer,
                                      long latencyMillis) {
        UserAccount user = agentContextResolver.user(token);
        String safeAgentType = AgentContextResolver.normalizeAgentType(agentType);
        String safeConversationId = StringUtils.hasText(conversationId) ? conversationId.trim() : "S" + System.currentTimeMillis();
        String internalConversationId = agentContextResolver.internalConversationId(user.getUserId(), safeConversationId);
        AiSession session = sessionService.saveQuestion(SaveQuestionRequest.builder()
                .sessionId(internalConversationId)
                .question(agentContextResolver.blank(query))
                .fileid(agentContextResolver.blank(fileId))
                .tools("")
                .firstResponseTime(Math.max(0L, latencyMillis))
                .build());
        sessionService.updateAnswer(UpdateAnswerRequest.builder()
                .id(session.getId())
                .answer(agentContextResolver.blank(answer))
                .thinking("平台身份问题使用确定性规则回答，避免底层模型自称模型本体。")
                .tools("")
                .firstResponseTime(Math.max(0L, latencyMillis))
                .totalResponseTime(Math.max(0L, latencyMillis))
                .build());
        fillAgentType(internalConversationId, safeAgentType);
    }

    public boolean stop(String token, String conversationId) {
        UserAccount user = agentContextResolver.user(token);
        return taskManager.stopTask(agentContextResolver.internalConversationId(user.getUserId(), conversationId));
    }

    public List<AiSession> querySessions(String token, int pageNum, int pageSize) {
        UserAccount user = agentContextResolver.user(token);
        String prefix = user.getUserId() + ":";
        int safePageNum = Math.max(1, pageNum);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        int offset = (safePageNum - 1) * safePageSize;
        return aiSessionMapper.selectSessionListWithFirstRecordByPrefix(prefix, offset, safePageSize);
    }

    public long countSessions(String token) {
        UserAccount user = agentContextResolver.user(token);
        return aiSessionMapper.countSessionByPrefix(user.getUserId() + ":");
    }

    public List<AiSession> querySessionMessages(String token, String conversationId) {
        UserAccount user = agentContextResolver.user(token);
        String internalConversationId = agentContextResolver.internalConversationId(user.getUserId(), conversationId);
        LambdaQueryWrapper<AiSession> wrapper = new LambdaQueryWrapper<AiSession>()
                .eq(AiSession::getSessionId, internalConversationId)
                .orderByAsc(AiSession::getCreateTime);
        return sessionService.list(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(String token, String conversationId) {
        UserAccount user = agentContextResolver.user(token);
        String internalConversationId = agentContextResolver.internalConversationId(user.getUserId(), conversationId);
        List<FileInfo> relatedFiles = fileInfoService.getAllFiles().stream()
                .filter(file -> internalConversationId.equals(file.getConversationId()))
                .toList();
        for (FileInfo file : relatedFiles) {
            try {
                fileManageService.deleteFileForSessionCleanup(file.getFileId());
            } catch (Exception e) {
                LOGGER.warn("academic-agent session file cleanup degraded, fileId={}, reason={}", file.getFileId(), e.getClass().getSimpleName());
                fileInfoService.deleteFileInfo(file.getFileId());
            }
        }
        aiPptInstService.remove(new LambdaQueryWrapper<AiPptInst>()
                .eq(AiPptInst::getConversationId, internalConversationId));
        sessionService.remove(new LambdaQueryWrapper<AiSession>().eq(AiSession::getSessionId, internalConversationId));
    }

    @Transactional(rollbackFor = Exception.class)
    public LocalDateTime rollbackSessionFromMessage(String token, String conversationId, String messageId) {
        UserAccount user = agentContextResolver.user(token);
        String internalConversationId = agentContextResolver.internalConversationId(user.getUserId(), conversationId);
        Long recordId = agentContextResolver.parseRecordId(messageId);
        if (recordId == null) {
            return null;
        }
        AiSession anchor = sessionService.getOne(new LambdaQueryWrapper<AiSession>()
                .eq(AiSession::getSessionId, internalConversationId)
                .eq(AiSession::getId, recordId)
                .last("LIMIT 1"));
        if (anchor == null || anchor.getCreateTime() == null) {
            return null;
        }
        sessionService.remove(new LambdaQueryWrapper<AiSession>()
                .eq(AiSession::getSessionId, internalConversationId)
                .ge(AiSession::getCreateTime, anchor.getCreateTime()));
        return anchor.getCreateTime();
    }

    public void fillAgentType(String internalConversationId, String agentType) {
        LambdaQueryWrapper<AiSession> wrapper = new LambdaQueryWrapper<AiSession>()
                .eq(AiSession::getSessionId, internalConversationId)
                .isNull(AiSession::getAgentType);
        AiSession update = new AiSession();
        update.setAgentType(agentType);
        sessionService.update(update, wrapper);
    }
}
