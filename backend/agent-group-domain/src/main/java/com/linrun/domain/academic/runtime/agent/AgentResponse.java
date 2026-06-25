package com.linrun.domain.academic.runtime.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 统一流式响应格式工具类
 *
 * 所有 Agent 流式输出必须使用本类方法，确保前端能统一解析
 */
public class AgentResponse {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 创建文本响应（最终答案）
     *
     * @param content 文本内容
     * @return JSON 格式响应 {"type":"text","content":"..."}
     */
    public static String text(String content) {
        return createResponse("text", content);
    }

    /**
     * 创建思考过程响应（推理链）
     *
     * @param content 思考内容
     * @return JSON 格式响应 {"type":"thinking","content":"..."}
     */
    public static String thinking(String content) {
        return createResponse("thinking", content);
    }

    /**
     * 创建引用响应（参考资料、知识库检索结果）
     *
     * @param references 引用列表的 JSON 字符串
     * @param count 引用数量
     * @return JSON 格式响应 {"type":"reference","content":"...","count":3}
     */
    public static String reference(String references, int count) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "reference");
        data.put("content", references);
        data.put("count", count);
        return toJson(data);
    }

    /**
     * 创建工具调用响应
     *
     * @param toolName 工具名称
     * @param toolInput 工具输入参数
     * @return JSON 格式响应 {"type":"tool","toolName":"...","toolInput":"..."}
     */
    public static String tool(String toolName, String toolInput) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "tool");
        data.put("toolName", toolName);
        data.put("toolInput", toolInput);
        return toJson(data);
    }

    /**
     * 创建工具调用结果响应
     *
     * @param toolName 工具名称
     * @param toolOutput 工具输出结果
     * @return JSON 格式响应 {"type":"toolResult","toolName":"...","toolOutput":"..."}
     */
    public static String toolResult(String toolName, String toolOutput) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "toolResult");
        data.put("toolName", toolName);
        data.put("toolOutput", toolOutput);
        return toJson(data);
    }

    /**
     * 创建计划响应（Plan-Execute 模式的计划）
     *
     * @param planTitle 计划标题
     * @param planSteps 计划步骤列表 JSON
     * @return JSON 格式响应 {"type":"plan","title":"...","steps":"..."}
     */
    public static String plan(String planTitle, String planSteps) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "plan");
        data.put("title", planTitle);
        data.put("steps", planSteps);
        return toJson(data);
    }

    /**
     * 创建重规划响应
     *
     * @param reason 重规划原因
     * @param newPlan 新计划 JSON
     * @return JSON 格式响应 {"type":"replan","reason":"...","newPlan":"..."}
     */
    public static String replan(String reason, String newPlan) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "replan");
        data.put("reason", reason);
        data.put("newPlan", newPlan);
        return toJson(data);
    }

    /**
     * 创建反思响应
     *
     * @param summary 反思总结
     * @param quality 质量评分
     * @return JSON 格式响应 {"type":"reflection","summary":"...","quality":78}
     */
    public static String reflection(String summary, int quality) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "reflection");
        data.put("summary", summary);
        data.put("quality", quality);
        return toJson(data);
    }

    /**
     * 创建错误响应
     *
     * @param error 错误信息
     * @return JSON 格式响应 {"type":"error","content":"..."}
     */
    public static String error(String error) {
        return createResponse("error", error);
    }

    /**
     * 创建元数据响应（耗时、工具使用统计等）
     *
     * @param metadata 元数据 JSON
     * @return JSON 格式响应 {"type":"metadata","content":"..."}
     */
    public static String metadata(String metadata) {
        return createResponse("metadata", metadata);
    }

    /**
     * 创建诊断报告响应
     *
     * @param level 诊断等级
     * @param summary 诊断摘要
     * @return JSON 格式响应 {"type":"diagnosis","level":"...","summary":"..."}
     */
    public static String diagnosis(String level, String summary) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "diagnosis");
        data.put("level", level);
        data.put("summary", summary);
        return toJson(data);
    }

    /**
     * 创建通用响应
     *
     * @param type 响应类型
     * @param content 响应内容
     * @return JSON 格式响应 {"type":"...","content":"..."}
     */
    private static String createResponse(String type, String content) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", type);
        data.put("content", content == null ? "" : content);
        return toJson(data);
    }

    private static String toJson(Map<String, Object> data) {
        try {
            return MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return "{\"type\":\"error\",\"content\":\"JSON serialization failed\"}";
        }
    }
}
