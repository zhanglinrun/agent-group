package com.linrun.domain.academic.runtime.mode;

import java.util.List;
import java.util.Map;

/**
 * Agent 执行模式接口
 * 定义系统一的执行模式抽象，支持 ReAct、Plan-Execute、Flow、Skill-SOP 四种模式
 */
public interface AgentExecutionMode {

    /**
     * 模式名称
     */
    String modeName();

    /**
     * 模式描述
     */
    String description();

    /**
     * 判断是否能处理当前上下文
     */
    boolean canHandle(ExecutionContext context);

    /**
     * 执行任务
     */
    ExecutionResult execute(ExecutionContext context);

    /**
     * 需要的工具列表
     */
    List<String> requiredTools();

    /**
     * 模式优先级（数字越大优先级越高）
     */
    default int priority() {
        return 0;
    }

    /**
     * 执行上下文
     */
    class ExecutionContext {
        private final String userId;
        private final String conversationId;
        private final String userQuery;
        private final List<Object> attachments;
        private final Map<String, Object> metadata;

        public ExecutionContext(String userId, String conversationId, String userQuery,
                              List<Object> attachments, Map<String, Object> metadata) {
            this.userId = userId;
            this.conversationId = conversationId;
            this.userQuery = userQuery;
            this.attachments = attachments != null ? attachments : List.of();
            this.metadata = metadata != null ? metadata : Map.of();
        }

        public String getUserId() {
            return userId;
        }

        public String getConversationId() {
            return conversationId;
        }

        public String getUserQuery() {
            return userQuery;
        }

        public List<Object> getAttachments() {
            return attachments;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public boolean hasAttachments() {
            return !attachments.isEmpty();
        }

        public Object getMetadata(String key) {
            return metadata.get(key);
        }
    }

    /**
     * 执行结果
     */
    class ExecutionResult {
        private final boolean success;
        private final String content;
        private final String error;
        private final Map<String, Object> metadata;

        public ExecutionResult(boolean success, String content, String error, 
                              Map<String, Object> metadata) {
            this.success = success;
            this.content = content;
            this.error = error;
            this.metadata = metadata != null ? metadata : Map.of();
        }

        public static ExecutionResult success(String content) {
            return new ExecutionResult(true, content, null, Map.of());
        }

        public static ExecutionResult success(String content, Map<String, Object> metadata) {
            return new ExecutionResult(true, content, null, metadata);
        }

        public static ExecutionResult failure(String error) {
            return new ExecutionResult(false, null, error, Map.of());
        }

        public boolean isSuccess() {
            return success;
        }

        public String getContent() {
            return content;
        }

        public String getError() {
            return error;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }
    }
}















